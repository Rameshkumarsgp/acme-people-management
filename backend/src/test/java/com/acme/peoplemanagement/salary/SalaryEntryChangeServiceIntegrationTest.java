package com.acme.peoplemanagement.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.salary.errors.SalaryEntryNotFoundException;
import com.acme.peoplemanagement.salary.rules.ChangeAction;
import com.acme.peoplemanagement.salary.rules.EmploymentPeriod;
import com.acme.peoplemanagement.salary.rules.HistoryEntry;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.NewSalaryEntry;
import com.acme.peoplemanagement.salary.rules.SalaryChange;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SalaryEntryChangeServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Instant NOW = Instant.parse("2026-10-19T09:00:00Z");
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);
    private static final LocalDate RAISE_DATE = LocalDate.of(2025, 4, 1);
    private static final EmploymentPeriod PERIOD = new EmploymentPeriod(HIRE_DATE, null);

    @Autowired
    private SalaryService salaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createAnEmployeeWithTwoEntries() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("changes@acme.example", HIRE_DATE);
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, RAISE_DATE, 120L, "INR");
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void logsAnAddedChangeWhenAnEntryIsAdded() {
        final HistoryEntry added = salaryService.addEntry(
                employeeId, PERIOD, new NewSalaryEntry(LocalDate.of(2026, 4, 1), 150L, "INR"), TODAY);

        final List<SalaryChange> changes = salaryService.changes(employeeId);

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().action()).isEqualTo(ChangeAction.ADDED);
        assertThat(changes.getFirst().entryId()).isEqualTo(added.id());
        assertThat(changes.getFirst().before()).isNull();
        assertThat(changes.getFirst().after()).isEqualTo(entry(LocalDate.of(2026, 4, 1), 150L, "INR"));
        assertThat(changes.getFirst().changedAt()).isEqualTo(NOW);
    }

    @Test
    void logsNothingWhenAnAddIsRefused() {
        assertThatThrownBy(() -> salaryService.addEntry(
                employeeId, PERIOD, new NewSalaryEntry(RAISE_DATE, 150L, "INR"), TODAY))
                .isInstanceOf(SalaryRuleViolation.class);

        assertThat(salaryService.changes(employeeId)).isEmpty();
    }

    @Test
    void editsAnEntryKeepingItsIdAndLogsTheOldAndNewValues() {
        final long raiseId = entryId(RAISE_DATE);

        final HistoryEntry edited = salaryService.editEntry(employeeId, PERIOD, raiseId,
                new NewSalaryEntry(LocalDate.of(2025, 5, 1), 130L, "INR"), TODAY);

        assertThat(edited.id()).isEqualTo(raiseId);
        assertThat(edited.entry()).isEqualTo(entry(LocalDate.of(2025, 5, 1), 130L, "INR"));
        final SalaryChange change = salaryService.changes(employeeId).getFirst();
        assertThat(change.action()).isEqualTo(ChangeAction.EDITED);
        assertThat(change.entryId()).isEqualTo(raiseId);
        assertThat(change.before()).isEqualTo(entry(RAISE_DATE, 120L, "INR"));
        assertThat(change.after()).isEqualTo(entry(LocalDate.of(2025, 5, 1), 130L, "INR"));
        assertThat(storedEntry(raiseId)).isEqualTo(entry(LocalDate.of(2025, 5, 1), 130L, "INR"));
    }

    @Test
    void keepsTheEntrysOwnCurrencyWhenNoneIsGivenAndChangesItWhenOneIs() {
        final long raiseId = entryId(RAISE_DATE);

        salaryService.editEntry(employeeId, PERIOD, raiseId, new NewSalaryEntry(RAISE_DATE, 125L, null), TODAY);
        assertThat(storedEntry(raiseId).salary().currencyCode()).isEqualTo("INR");

        salaryService.editEntry(employeeId, PERIOD, raiseId, new NewSalaryEntry(RAISE_DATE, 125L, "GBP"), TODAY);
        assertThat(storedEntry(raiseId).salary().currencyCode()).isEqualTo("GBP");
    }

    @Test
    void logsNothingForAnEditThatChangesNothing() {
        salaryService.editEntry(employeeId, PERIOD, entryId(RAISE_DATE),
                new NewSalaryEntry(RAISE_DATE, 120L, "INR"), TODAY);

        assertThat(salaryService.changes(employeeId)).isEmpty();
    }

    @Test
    void refusesAnEditToADateAnotherEntryUsesAndLeavesTheEntryAndTheLogAlone() {
        final long raiseId = entryId(RAISE_DATE);

        assertRefusedWith(SalaryRuleCode.DUPLICATE_SALARY_DATE, () -> salaryService.editEntry(
                employeeId, PERIOD, raiseId, new NewSalaryEntry(HIRE_DATE, 130L, "INR"), TODAY));

        assertThat(storedEntry(raiseId)).isEqualTo(entry(RAISE_DATE, 120L, "INR"));
        assertThat(salaryService.changes(employeeId)).isEmpty();
    }

    @Test
    void refusesAnEditBeforeTheHireDate() {
        assertRefusedWith(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE, () -> salaryService.editEntry(
                employeeId, PERIOD, entryId(RAISE_DATE),
                new NewSalaryEntry(HIRE_DATE.minusDays(1), 130L, "INR"), TODAY));
    }

    @Test
    void refusesAnEditAfterTheTerminationDate() {
        final EmploymentPeriod left = new EmploymentPeriod(HIRE_DATE, LocalDate.of(2025, 6, 1));

        assertRefusedWith(SalaryRuleCode.SALARY_AFTER_TERMINATION, () -> salaryService.editEntry(
                employeeId, left, entryId(RAISE_DATE), new NewSalaryEntry(LocalDate.of(2025, 6, 2), 130L, "INR"),
                TODAY));
    }

    @Test
    void refusesAnEditThatLeavesNoCurrentSalary() {
        final long first = entryId(HIRE_DATE);
        jdbcTemplate.update("delete from salary_entry where id = ?", entryId(RAISE_DATE));

        assertRefusedWith(SalaryRuleCode.NO_CURRENT_SALARY, () -> salaryService.editEntry(
                employeeId, PERIOD, first, new NewSalaryEntry(TODAY.plusDays(1), 100L, "INR"), TODAY));
    }

    @Test
    void doesNotFindAnEntryThatBelongsToAnotherEmployee() {
        final long other = employees.insert("other-changes@acme.example", HIRE_DATE);
        employees.insertSalary(other, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> salaryService.editEntry(
                employeeId, PERIOD, entryId(other, HIRE_DATE), new NewSalaryEntry(RAISE_DATE, 1L, "INR"), TODAY))
                .isInstanceOf(SalaryEntryNotFoundException.class);
        assertThatThrownBy(() -> salaryService.deleteEntry(employeeId, PERIOD, entryId(other, HIRE_DATE), TODAY))
                .isInstanceOf(SalaryEntryNotFoundException.class);
        assertThatThrownBy(() -> salaryService.deleteEntry(employeeId, PERIOD, -1L, TODAY))
                .isInstanceOf(SalaryEntryNotFoundException.class);
    }

    @Test
    void deletesAnEntryAndLogsItsOldValues() {
        final long raiseId = entryId(RAISE_DATE);

        salaryService.deleteEntry(employeeId, PERIOD, raiseId, TODAY);

        assertThat(count("salary_entry where id = " + raiseId)).isZero();
        final SalaryChange change = salaryService.changes(employeeId).getFirst();
        assertThat(change.action()).isEqualTo(ChangeAction.DELETED);
        assertThat(change.before()).isEqualTo(entry(RAISE_DATE, 120L, "INR"));
        assertThat(change.after()).isNull();
    }

    @Test
    void makesTheEarlierEntryCurrentWhenTheLatestIsDeleted() {
        salaryService.deleteEntry(employeeId, PERIOD, entryId(RAISE_DATE), TODAY);

        assertThat(salaryService.currentSalary(employeeId, TODAY)).contains(entry(HIRE_DATE, 100L, "INR"));
    }

    @Test
    void refusesToDeleteTheOnlyEntryAndKeepsItWithNoChangeRow() {
        jdbcTemplate.update("delete from salary_entry where id = ?", entryId(RAISE_DATE));
        final long only = entryId(HIRE_DATE);

        assertRefusedWith(SalaryRuleCode.NO_CURRENT_SALARY,
                () -> salaryService.deleteEntry(employeeId, PERIOD, only, TODAY));

        assertThat(count("salary_entry where id = " + only)).isEqualTo(1);
        assertThat(salaryService.changes(employeeId)).isEmpty();
    }

    @Test
    void keepsTheChangeRowsOfAnEntryAfterItIsDeleted() {
        final HistoryEntry added = salaryService.addEntry(
                employeeId, PERIOD, new NewSalaryEntry(LocalDate.of(2026, 4, 1), 150L, "INR"), TODAY);

        salaryService.deleteEntry(employeeId, PERIOD, added.id(), TODAY);

        assertThat(salaryService.changes(employeeId)).extracting(SalaryChange::entryId)
                .containsExactly(added.id(), added.id());
    }

    @Test
    void listsTheChangesNewestFirst() {
        final HistoryEntry added = salaryService.addEntry(
                employeeId, PERIOD, new NewSalaryEntry(LocalDate.of(2026, 4, 1), 150L, "INR"), TODAY);
        salaryService.editEntry(employeeId, PERIOD, added.id(),
                new NewSalaryEntry(LocalDate.of(2026, 4, 1), 160L, "INR"), TODAY);
        salaryService.deleteEntry(employeeId, PERIOD, added.id(), TODAY);

        assertThat(salaryService.changes(employeeId)).extracting(SalaryChange::action)
                .containsExactly(ChangeAction.DELETED, ChangeAction.EDITED, ChangeAction.ADDED);
    }

    private void assertRefusedWith(final SalaryRuleCode code, final Runnable change) {
        assertThatThrownBy(change::run).isInstanceOfSatisfying(SalaryRuleViolation.class,
                violation -> assertThat(violation.code()).isEqualTo(code));
    }

    private long entryId(final LocalDate effectiveDate) {
        return entryId(employeeId, effectiveDate);
    }

    private long entryId(final long employee, final LocalDate effectiveDate) {
        final Long id = jdbcTemplate.queryForObject(
                "select id from salary_entry where employee_id = ? and effective_date = ?", Long.class,
                employee, effectiveDate);
        return id == null ? -1 : id;
    }

    private TimelineEntry storedEntry(final long id) {
        return jdbcTemplate.queryForObject(
                "select effective_date, amount_minor, currency_code from salary_entry where id = ?",
                (row, number) -> entry(row.getObject("effective_date", LocalDate.class), row.getLong("amount_minor"),
                        row.getString("currency_code")), id);
    }

    private int count(final String fromAndWhere) {
        final Integer count = jdbcTemplate.queryForObject("select count(*) from " + fromAndWhere, Integer.class);
        return count == null ? -1 : count;
    }

    private static TimelineEntry entry(final LocalDate effectiveDate, final long amountMinor, final String currency) {
        return new TimelineEntry(effectiveDate, new Money(amountMinor, currency));
    }
}
