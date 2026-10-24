package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SeedGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final int COUNT = 10_000;
    private static final List<EmployeeRow> ROWS = SeedGenerator.generate(COUNT, 42L, TODAY);

    @Test
    void producesTheRequestedNumberOfEmployees() {
        assertThat(ROWS).hasSize(COUNT);
        assertThat(SeedGenerator.generate(25, 42L, TODAY)).hasSize(25);
    }

    @Test
    void givesTheSameEmployeesForTheSameInputAndOtherEmployeesForAnotherSeed() {
        assertThat(SeedGenerator.generate(500, 42L, TODAY)).isEqualTo(SeedGenerator.generate(500, 42L, TODAY));
        assertThat(SeedGenerator.generate(500, 43L, TODAY)).isNotEqualTo(SeedGenerator.generate(500, 42L, TODAY));
    }

    @Test
    void givesEveryEmployeeAUniqueEmail() {
        assertThat(ROWS.stream().map(EmployeeRow::email).distinct().count()).isEqualTo(COUNT);
    }

    @Test
    void hiresInTheLastTenYearsAndNeverInTheFuture() {
        assertThat(ROWS).allSatisfy(row -> assertThat(row.hireDate())
                .isAfterOrEqualTo(TODAY.minusDays(3_650)).isBeforeOrEqualTo(TODAY));
        assertThat(ROWS.stream().map(EmployeeRow::hireDate).min(LocalDate::compareTo).orElseThrow())
                .isBefore(TODAY.minusYears(9));
        assertThat(ROWS.stream().map(EmployeeRow::hireDate).max(LocalDate::compareTo).orElseThrow())
                .isAfter(TODAY.minusYears(1));
    }

    @Test
    void datesTheSalaryAtTheMostRecentWorkAnniversaryBeforeTheEmploymentEnds() {
        assertThat(ROWS).allSatisfy(row -> {
            final LocalDate end = row.terminationDate() == null ? TODAY : row.terminationDate();
            assertThat(row.salaryEffectiveDate()).isAfterOrEqualTo(row.hireDate()).isBeforeOrEqualTo(end);
            assertThat(row.salaryEffectiveDate().plusYears(1)).isAfter(end);
        });
    }

    @Test
    void makesAboutEightPercentOfEmployeesLeavers() {
        final long leavers = ROWS.stream().filter(row -> row.terminationDate() != null).count();

        assertThat(leavers * 100.0 / COUNT).isBetween(6.0, 10.0);
    }

    @Test
    void letsEveryLeaverLeaveAfterBeingHiredAndBeforeToday() {
        assertThat(ROWS.stream().filter(row -> row.terminationDate() != null)).isNotEmpty().allSatisfy(row ->
                assertThat(row.terminationDate()).isAfterOrEqualTo(row.hireDate()).isBefore(TODAY));
    }

    @Test
    void paysEveryCountryInItsOwnCurrencyAndUsesAllOfThem() {
        final Map<String, String> currencyByCountry = PayModel.countries().stream()
                .collect(Collectors.toMap(CountryPay::code, CountryPay::currency));

        assertThat(ROWS).allSatisfy(row ->
                assertThat(row.currency()).isEqualTo(currencyByCountry.get(row.countryCode())));
        assertThat(ROWS.stream().map(EmployeeRow::countryCode).distinct().count())
                .isEqualTo(PayModel.countries().size());
    }

    @Test
    void putsEveryTitleInItsOwnDepartment() {
        final Map<String, String> departmentByTitle = PayModel.titles().stream()
                .collect(Collectors.toMap(TitlePay::title, TitlePay::department));

        assertThat(ROWS).allSatisfy(row ->
                assertThat(row.department()).isEqualTo(departmentByTitle.get(row.jobTitle())));
        assertThat(ROWS.stream().map(EmployeeRow::jobTitle).distinct().count()).isEqualTo(PayModel.titles().size());
    }

    @Test
    void writesSalariesAsWholeThousandsOfMajorUnits() {
        assertThat(ROWS).allSatisfy(row -> assertThat(Long.parseLong(row.salary()) % 1_000).isZero());
    }

    @Test
    void followsTheCountryMixWithinAFewPoints() {
        final Map<String, Long> counts = ROWS.stream()
                .collect(Collectors.groupingBy(EmployeeRow::countryCode, Collectors.counting()));

        for (final CountryPay country : PayModel.countries()) {
            assertThat(counts.get(country.code()) * 100.0 / COUNT)
                    .as(country.code()).isBetween(country.weightPercent() - 2.0, country.weightPercent() + 2.0);
        }
    }

    @Test
    void paysTenuredEmployeesMoreOnAverageForTheSameTitleAndCountry() {
        final double juniors = averageIndianEngineerSalary(row -> row.hireDate().isAfter(TODAY.minusYears(2)));
        final double seniors = averageIndianEngineerSalary(row -> row.hireDate().isBefore(TODAY.minusYears(7)));

        assertThat(seniors).isGreaterThan(juniors * 1.15);
    }

    private static double averageIndianEngineerSalary(final Predicate<EmployeeRow> tenure) {
        return ROWS.stream()
                .filter(row -> row.terminationDate() == null)
                .filter(row -> row.countryCode().equals("IN") && row.jobTitle().equals("Software Engineer"))
                .filter(tenure)
                .mapToLong(row -> Long.parseLong(row.salary()))
                .average()
                .orElseThrow();
    }
}
