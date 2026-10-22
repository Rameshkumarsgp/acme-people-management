package com.acme.peoplemanagement.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.salary.errors.CurrencyRequiredException;
import com.acme.peoplemanagement.salary.rules.HistoryEntry;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.NewSalaryEntry;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SalaryServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

    @Autowired
    private SalaryService salaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("salary-service@acme.example", HIRE_DATE);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void recordsTheFirstEntryAndReportsItAsTheCurrentSalary() {
        final TimelineEntry first = entry(HIRE_DATE, 125_000_000L, "INR");

        salaryService.recordFirstEntry(employeeId, HIRE_DATE, first, TODAY);

        assertThat(salaryService.currentSalary(employeeId, TODAY)).contains(first);
    }

    @Test
    void reportsTheLatestEntryOnOrBeforeTheGivenDate() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2025, 4, 1), 120L, "INR");

        assertThat(salaryService.currentSalary(employeeId, LocalDate.of(2025, 3, 31)))
                .contains(entry(HIRE_DATE, 100L, "INR"));
        assertThat(salaryService.currentSalary(employeeId, LocalDate.of(2025, 4, 1)))
                .contains(entry(LocalDate.of(2025, 4, 1), 120L, "INR"));
    }

    @Test
    void ignoresAnEntryDatedInTheFuture() {
        employees.insertSalary(employeeId, TODAY.plusDays(1), 100L, "INR");

        assertThat(salaryService.currentSalary(employeeId, TODAY)).isEmpty();
    }

    @Test
    void keepsEachEntryInItsOwnCurrency() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2025, 4, 1), 5_000L, "USD");

        assertThat(salaryService.currentSalary(employeeId, TODAY))
                .contains(entry(LocalDate.of(2025, 4, 1), 5_000L, "USD"));
    }

    @Test
    void savesNothingWhenTheTimelineBreaksARule() {
        final TimelineEntry beforeHire = entry(HIRE_DATE.minusDays(1), 100L, "INR");

        assertThatThrownBy(() -> salaryService.recordFirstEntry(employeeId, HIRE_DATE, beforeHire, TODAY))
                .isInstanceOf(SalaryRuleViolation.class);

        assertThat(salaryService.currentSalary(employeeId, TODAY)).isEmpty();
    }

    @Test
    void addsAnEntryThatBecomesTheCurrentSalaryAndEndsTheEarlierOne() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        final HistoryEntry added = salaryService.addEntry(
                employeeId, HIRE_DATE, new NewSalaryEntry(LocalDate.of(2026, 4, 1), 150L, "INR"), TODAY);

        assertThat(added.current()).isTrue();
        assertThat(added.endDate()).isNull();
        assertThat(salaryService.history(employeeId, TODAY)).extracting(HistoryEntry::endDate)
                .containsExactly(null, LocalDate.of(2026, 3, 31));
    }

    @Test
    void addsAFutureEntryAsScheduledAndLeavesTheEarlierOneCurrent() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        final HistoryEntry added = salaryService.addEntry(
                employeeId, HIRE_DATE, new NewSalaryEntry(TODAY.plusDays(1), 150L, "INR"), TODAY);

        assertThat(added.scheduled()).isTrue();
        assertThat(salaryService.history(employeeId, TODAY)).extracting(HistoryEntry::current)
                .containsExactly(false, true);
    }

    @Test
    void defaultsTheCurrencyToTheEntryBeforeTheNewDate() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2025, 6, 1), 110L, "GBP");
        employees.insertSalary(employeeId, LocalDate.of(2027, 1, 1), 5_000L, "USD");

        final HistoryEntry added = salaryService.addEntry(
                employeeId, HIRE_DATE, new NewSalaryEntry(LocalDate.of(2026, 3, 1), 120L, null), TODAY);

        // The latest entry before the date (GBP): not the earliest (INR) and not one after it (USD).
        assertThat(added.entry().salary().currencyCode()).isEqualTo("GBP");
    }

    @Test
    void requiresACurrencyWhenNoEntryPrecedesTheNewDate() {
        employees.insertSalary(employeeId, LocalDate.of(2025, 1, 1), 100L, "INR");

        assertThatThrownBy(() -> salaryService.addEntry(employeeId, HIRE_DATE,
                new NewSalaryEntry(LocalDate.of(2024, 6, 1), 90L, null), TODAY))
                .isInstanceOf(CurrencyRequiredException.class);
    }

    @Test
    void rejectsADateThatIsAlreadyUsed() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> salaryService.addEntry(employeeId, HIRE_DATE,
                new NewSalaryEntry(HIRE_DATE, 150L, "INR"), TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.DUPLICATE_SALARY_DATE));
    }

    @Test
    void rejectsADateBeforeTheHireDateAndSavesNothing() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> salaryService.addEntry(employeeId, HIRE_DATE,
                new NewSalaryEntry(HIRE_DATE.minusDays(1), 90L, "INR"), TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE));

        assertThat(salaryService.history(employeeId, TODAY)).hasSize(1);
    }

    @Test
    void listsTheHistoryNewestFirstWithTheDerivedValues() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2025, 4, 1), 120L, "INR");

        final List<HistoryEntry> history = salaryService.history(employeeId, TODAY);

        assertThat(history).extracting(entry -> entry.entry().effectiveDate())
                .containsExactly(LocalDate.of(2025, 4, 1), HIRE_DATE);
        assertThat(history.getFirst().current()).isTrue();
        assertThat(history.getLast().endDate()).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    private static TimelineEntry entry(final LocalDate effectiveDate, final long amountMinor, final String currency) {
        return new TimelineEntry(effectiveDate, new Money(amountMinor, currency));
    }
}
