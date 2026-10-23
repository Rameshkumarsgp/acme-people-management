package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.dataload.rules.FileRejectedException;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeCsvLoaderIntegrationTest {

    private static final String HEADER = String.join(",", Column.headers());
    private static final String ROW =
            "Asha Rao,asha@acme.example,2024-04-01,Engineering,Software Engineer,IN,INR,1250000.00,,";

    @Autowired
    private EmployeeCsvLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;

    @BeforeEach
    void createHelper() {
        employees = new TestEmployees(jdbcTemplate);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void loadsARowAsAnEmployeeWithItsFirstSalaryOnTheHireDate() {
        final LoadReport report = load(ROW);

        assertThat(report).isEqualTo(new LoadReport(1, List.of()));
        final Map<String, Object> stored = jdbcTemplate.queryForMap("""
                select e.full_name, s.amount_minor, s.currency_code, s.effective_date
                from employee e join salary_entry s on s.employee_id = e.id
                where e.email = 'asha@acme.example'""");
        assertThat(stored).containsEntry("full_name", "Asha Rao")
                .containsEntry("amount_minor", 125_000_000L)
                .containsEntry("currency_code", "INR");
        assertThat(stored.get("effective_date").toString()).isEqualTo("2024-04-01");
    }

    @Test
    void convertsTheAmountWithTheCurrencysOwnDecimals() {
        load(ROW.replace("IN,INR,1250000.00", "JP,JPY,6500000"));

        assertThat(amountMinorOf("asha@acme.example")).isEqualTo(6_500_000L);
    }

    @Test
    void usesTheGivenSalaryEffectiveDate() {
        load(ROW.replace("1250000.00,,", "1250000.00,2024-10-01,"));

        assertThat(effectiveDateOf("asha@acme.example")).isEqualTo(LocalDate.of(2024, 10, 1));
    }

    @Test
    void matchesNamesIgnoringCaseAndSurroundingSpaces() {
        final LoadReport report = load(ROW.replace("Engineering", " engineering ")
                .replace("Software Engineer", "SOFTWARE engineer"));

        assertThat(report.loaded()).isEqualTo(1);
    }

    @Test
    void rejectsOneRowAndStillLoadsTheOthers() {
        final LoadReport report = load(
                row("first@acme.example"),
                row("second@acme.example").replace("2024-04-01", "not-a-date"),
                row("third@acme.example"));

        assertThat(report.loaded()).isEqualTo(2);
        assertThat(report.rejected()).containsExactly(new RejectedRow(
                3, "hireDate", "Dates must look like 2024-04-01 (year-month-day)."));
        assertThat(employeeCount()).isEqualTo(2);
    }

    @Test
    void rejectsAnUnknownDepartmentAgainstItsColumn() {
        final LoadReport report = load(ROW.replace("Engineering", "Astrology"));

        assertThat(report.rejected()).containsExactly(new RejectedRow(2, "department", "Unknown department."));
    }

    @Test
    void rejectsAnUnknownJobTitleAndAnUnknownCurrencyAndAnUnknownCountry() {
        assertThat(load(ROW.replace("Software Engineer", "Astrologer")).rejected())
                .containsExactly(new RejectedRow(2, "jobTitle", "Unknown job title."));
        assertThat(load(ROW.replace("INR", "ZZZ")).rejected())
                .containsExactly(new RejectedRow(2, "currency", "Unknown currency."));
        assertThat(load(ROW.replace(",IN,", ",ZZ,")).rejected())
                .containsExactly(new RejectedRow(2, "countryCode", "Unknown country."));
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfCuttingThemOff() {
        assertThat(load(ROW.replace("IN,INR,1250000.00", "JP,JPY,6500000.5")).rejected())
                .containsExactly(new RejectedRow(2, "salary", "This currency has no decimal places."));
        assertThat(load(ROW.replace("1250000.00", "1.234")).rejected())
                .containsExactly(new RejectedRow(2, "salary", "This currency allows at most 2 decimal places."));
        assertThat(employeeCount()).isZero();
    }

    @Test
    void rejectsARowWhoseSalaryStartsBeforeTheHireDate() {
        final LoadReport report = load(ROW.replace("1250000.00,,", "1250000.00,2024-03-01,"));

        assertThat(report.rejected()).hasSize(1);
        assertThat(report.rejected().getFirst().field()).isEqualTo("salaryEffectiveDate");
        assertThat(report.rejected().getFirst().reason()).startsWith("SALARY_BEFORE_HIRE_DATE");
    }

    @Test
    void rejectsARowThatFailsValidationAgainstTheFieldName() {
        final LoadReport report = load(ROW.replace("Asha Rao", "x".repeat(201)));

        assertThat(report.rejected()).hasSize(1);
        assertThat(report.rejected().getFirst().field()).isEqualTo("fullName");
    }

    @Test
    void rejectsATerminationDateUntilLeaversAreSupported() {
        final LoadReport report = load(ROW.replace("1250000.00,,", "1250000.00,,2025-12-31"));

        assertThat(report.rejected()).containsExactly(
                new RejectedRow(2, "terminationDate", "Termination dates are not supported yet."));
        assertThat(employeeCount()).isZero();
    }

    @Test
    void rejectsAnEmailThatAnEarlierRowOfTheSameFileAlreadyLoaded() {
        final LoadReport report = load(ROW, ROW);

        assertThat(report.loaded()).isEqualTo(1);
        assertThat(report.rejected().getFirst().rowNumber()).isEqualTo(3);
        assertThat(report.rejected().getFirst().field()).isEqualTo("email");
        assertThat(report.rejected().getFirst().reason()).startsWith("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void reportsEveryRowAsAlreadyLoadedWhenTheSameFileRunsAgain() {
        load(row("first@acme.example"), row("second@acme.example"));

        final LoadReport second = load(row("first@acme.example"), row("second@acme.example"));

        assertThat(second.loaded()).isZero();
        assertThat(second.rejected()).hasSize(2).allSatisfy(rejected ->
                assertThat(rejected.reason()).startsWith("EMAIL_ALREADY_EXISTS"));
        assertThat(second.hasRejections()).isTrue();
        assertThat(employeeCount()).isEqualTo(2);
    }

    @Test
    void failsTheWholeFileOnAWrongHeaderBeforeAnyRowIsCreated() {
        assertThatExceptionOfType(FileRejectedException.class)
                .isThrownBy(() -> loader.load(new StringReader("fullName,email\nAsha Rao,asha@acme.example")));
        assertThat(employeeCount()).isZero();
    }

    private LoadReport load(final String... rows) {
        return loader.load(new StringReader(HEADER + "\n" + String.join("\n", rows)));
    }

    private static String row(final String email) {
        return ROW.replace("asha@acme.example", email);
    }

    private int employeeCount() {
        final Integer count = jdbcTemplate.queryForObject("select count(*) from employee", Integer.class);
        return count == null ? -1 : count;
    }

    private long amountMinorOf(final String email) {
        final Long amount = jdbcTemplate.queryForObject("""
                select s.amount_minor from salary_entry s join employee e on e.id = s.employee_id
                where e.email = ?""", Long.class, email);
        return amount == null ? -1 : amount;
    }

    private LocalDate effectiveDateOf(final String email) {
        return jdbcTemplate.queryForObject("""
                select s.effective_date from salary_entry s join employee e on e.id = s.employee_id
                where e.email = ?""", LocalDate.class, email);
    }
}
