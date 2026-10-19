package com.acme.peoplemanagement.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
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

    private static TimelineEntry entry(final LocalDate effectiveDate, final long amountMinor, final String currency) {
        return new TimelineEntry(effectiveDate, new Money(amountMinor, currency));
    }
}
