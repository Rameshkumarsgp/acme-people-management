package com.acme.peoplemanagement.insights;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.insights.api.ExtremeEmployee;
import com.acme.peoplemanagement.insights.api.ExtremesResponse;
import com.acme.peoplemanagement.insights.api.InsightGroup;
import com.acme.peoplemanagement.insights.api.InsightsResponse;
import com.acme.peoplemanagement.insights.rules.ExtremesParams;
import com.acme.peoplemanagement.insights.rules.ExtremesQuery;
import com.acme.peoplemanagement.insights.rules.InsightsParams;
import com.acme.peoplemanagement.insights.rules.InsightsQuery;
import com.acme.peoplemanagement.support.FixedClockConfiguration;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class InsightsServiceIntegrationTest {

    private static final LocalDate TODAY = FixedClockConfiguration.TODAY;
    private static final LocalDate HIRED = LocalDate.of(2020, 1, 1);
    private static final long CAP = 9_007_199_254_740_991L;

    @Autowired
    private InsightsService insights;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private int emailCounter;

    private record Person(String country, String department, String jobTitle) {
    }

    @BeforeEach
    void createHelper() {
        employees = new TestEmployees(jdbcTemplate);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void answersNoGroupsWhenNobodyMatches() {
        assertThat(slice("COUNTRY").groups()).isEmpty();
        pay(india(), 100L, "INR");
        assertThat(insights.insights(InsightsQuery.from(
                new InsightsParams("COUNTRY", "GB", null, null, null, null))).groups()).isEmpty();
    }

    @Test
    void reportsASingleEmployeeAsEveryMeasureAtOnce() {
        pay(india(), 125_000_000L, "INR");

        final InsightGroup group = slice("COUNTRY").groups().getFirst();

        assertThat(group.key()).isEqualTo("IN");
        assertThat(group.label()).isEqualTo("India");
        assertThat(group.currency()).isEqualTo("INR");
        assertThat(group.headcount()).isEqualTo(1);
        assertThat(group.total()).isEqualTo("125000000");
        assertThat(group.min()).isEqualTo(125_000_000L);
        assertThat(group.median()).isEqualTo(125_000_000L);
        assertThat(group.mean()).isEqualTo(125_000_000L);
        assertThat(group.max()).isEqualTo(125_000_000L);
    }

    @Test
    void computesTheMeasuresOfAnOddNumberOfEmployees() {
        for (final long amount : new long[] {1L, 2L, 9L}) {
            pay(india(), amount, "INR");
        }

        final InsightGroup group = slice("COUNTRY").groups().getFirst();

        assertThat(group.headcount()).isEqualTo(3);
        assertThat(group.total()).isEqualTo("12");
        assertThat(group.min()).isEqualTo(1L);
        assertThat(group.median()).isEqualTo(2L);
        assertThat(group.mean()).isEqualTo(4L);
        assertThat(group.max()).isEqualTo(9L);
    }

    @Test
    void roundsAHalfUpForTheMedianOfAnEvenNumberAndForTheMean() {
        pay(india(), 100L, "INR");
        pay(india(), 101L, "INR");

        final InsightGroup group = slice("COUNTRY").groups().getFirst();

        assertThat(group.median()).isEqualTo(101L);
        assertThat(group.mean()).isEqualTo(101L);
    }

    @Test
    void roundsDownBelowAHalf() {
        pay(india(), 100L, "INR");
        pay(india(), 100L, "INR");
        pay(india(), 101L, "INR");

        assertThat(slice("COUNTRY").groups().getFirst().mean()).isEqualTo(100L);
    }

    @Test
    void neverMixesCurrenciesInOneGroup() {
        pay(india(), 100L, "INR");
        pay(india(), 7L, "USD");

        final List<InsightGroup> groups = slice("COUNTRY").groups();

        assertThat(groups).extracting(InsightGroup::currency).containsExactly("INR", "USD");
        assertThat(groups).extracting(InsightGroup::headcount).containsExactly(1L, 1L);
        assertThat(groups).extracting(InsightGroup::total).containsExactly("100", "7");
    }

    @Test
    void sumsEveryoneTogetherPerCurrencyForTheOverallView() {
        pay(india(), 100L, "INR");
        pay(new Person("US", "Sales", "Sales Manager"), 300L, "INR");

        final InsightsResponse response = slice("OVERALL");

        assertThat(response.dimension()).isEqualTo("OVERALL");
        assertThat(response.groups()).hasSize(1);
        assertThat(response.groups().getFirst().label()).isEqualTo("All employees");
        assertThat(response.groups().getFirst().headcount()).isEqualTo(2);
        assertThat(response.groups().getFirst().total()).isEqualTo("400");
    }

    @Test
    void groupsByDepartmentAndByJobTitleAndByTitleInACountry() {
        pay(new Person("IN", "Engineering", "Software Engineer"), 100L, "INR");
        pay(new Person("US", "Engineering", "Software Engineer"), 200L, "USD");
        pay(new Person("IN", "Sales", "Sales Manager"), 300L, "INR");

        assertThat(labels(slice("DEPARTMENT"))).containsExactly("Engineering", "Engineering", "Sales");
        assertThat(slice("DEPARTMENT").groups()).extracting(InsightGroup::currency)
                .containsExactly("INR", "USD", "INR");
        assertThat(labels(slice("JOB_TITLE"))).containsExactly("Sales Manager", "Software Engineer",
                "Software Engineer");
        assertThat(labels(slice("TITLE_COUNTRY"))).containsExactly("Sales Manager in India",
                "Software Engineer in India", "Software Engineer in United States");
    }

    @Test
    void ignoresARaiseThatIsNotYetInForce() {
        final long id = hire(india(), HIRED, null);
        employees.insertSalary(id, HIRED, 100L, "INR");
        employees.insertSalary(id, TODAY.plusDays(1), 900L, "INR");
        employees.insertSalary(id, TODAY, 150L, "INR");

        assertThat(slice("COUNTRY").groups().getFirst().max()).isEqualTo(150L);
    }

    @Test
    void countsOnlyActiveEmployeesByDefaultAndFormerOnesWhenAsked() {
        pay(india(), 100L, "INR");
        payFormer(india(), 900L, "INR", TODAY.minusDays(1));
        payFormer(india(), 500L, "INR", TODAY);

        assertThat(headcount(statusSlice(null))).isEqualTo(2);
        assertThat(headcount(statusSlice("FORMER"))).isEqualTo(1);
        assertThat(headcount(statusSlice("ALL"))).isEqualTo(3);
    }

    @Test
    void filtersByCountryDepartmentJobTitleAndCurrency() {
        pay(new Person("IN", "Engineering", "Software Engineer"), 100L, "INR");
        pay(new Person("US", "Sales", "Sales Manager"), 200L, "USD");

        assertThat(insights.insights(InsightsQuery.from(
                new InsightsParams("OVERALL", "US", null, null, null, null))).groups().getFirst().total())
                .isEqualTo("200");
        assertThat(insights.insights(InsightsQuery.from(
                new InsightsParams("OVERALL", null, idOf("department", "Engineering"), null, null, null)))
                .groups().getFirst().total()).isEqualTo("100");
        assertThat(insights.insights(InsightsQuery.from(
                new InsightsParams("OVERALL", null, null, idOf("job_title", "Sales Manager"), null, null)))
                .groups().getFirst().total()).isEqualTo("200");
        assertThat(insights.insights(InsightsQuery.from(
                new InsightsParams("OVERALL", null, null, null, "USD", null))).groups().getFirst().total())
                .isEqualTo("200");
    }

    @Test
    void bandsTenureByCompletedYearsOnAndEitherSideOfEachBoundary() {
        tenant(TODAY.minusYears(1).plusDays(1));
        tenant(TODAY.minusYears(1));
        tenant(TODAY.minusYears(3).plusDays(1));
        tenant(TODAY.minusYears(3));
        tenant(TODAY.minusYears(5).plusDays(1));
        tenant(TODAY.minusYears(5));
        tenant(TODAY.minusYears(10).plusDays(1));
        tenant(TODAY.minusYears(10));

        final List<InsightGroup> groups = slice("TENURE").groups();

        assertThat(groups).extracting(InsightGroup::label).containsExactly("Under 1 year", "1 to under 3 years",
                "3 to under 5 years", "5 to under 10 years", "10 or more years");
        assertThat(groups).extracting(InsightGroup::headcount).containsExactly(1L, 2L, 2L, 2L, 1L);
    }

    @Test
    void stopsTheTenureOfAFormerEmployeeAtTheirTerminationDate() {
        final long id = hire(india(), TODAY.minusYears(8), TODAY.minusYears(4));
        employees.insertSalary(id, TODAY.minusYears(8), 100L, "INR");

        final InsightGroup group = statusSlice("ALL", "TENURE").groups().getFirst();

        assertThat(group.label()).isEqualTo("3 to under 5 years");
    }

    @Test
    void sumsAboveTheLargestIntegerABrowserCanHoldWithoutLosingADigit() {
        pay(india(), CAP, "INR");
        pay(india(), CAP, "INR");

        final InsightGroup group = slice("COUNTRY").groups().getFirst();

        assertThat(group.total()).isEqualTo("18014398509481982");
        assertThat(group.max()).isEqualTo(CAP);
        assertThat(group.mean()).isEqualTo(CAP);
    }

    @Test
    void pinsTheMedianOfTwoAmountsAtTheCapWhichIsOneUnitLowBecausePercentileWorksInDoubles() {
        pay(india(), CAP - 1, "INR");
        pay(india(), CAP, "INR");

        assertThat(slice("COUNTRY").groups().getFirst().median()).isEqualTo(CAP - 1);
    }

    @Test
    void listsTheHighestPaidOfACurrencyWithTheirDetails() {
        final long top = pay(india(), 900L, "INR");
        pay(india(), 500L, "INR");
        pay(india(), 5_000L, "USD");

        final ExtremesResponse response = extremes("HIGHEST", "INR", 10);

        assertThat(response.order()).isEqualTo("HIGHEST");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.employees()).extracting(ExtremeEmployee::amountMinor).containsExactly(900L, 500L);
        final ExtremeEmployee first = response.employees().getFirst();
        assertThat(first.id()).isEqualTo(top);
        assertThat(first.fullName()).isEqualTo("Test Person");
        assertThat(first.department()).isEqualTo("Engineering");
        assertThat(first.jobTitle()).isEqualTo("Software Engineer");
        assertThat(first.country()).isEqualTo("India");
        assertThat(first.effectiveDate()).isEqualTo(HIRED);
    }

    @Test
    void listsTheLowestPaidFirstAndHonoursTheLimit() {
        pay(india(), 900L, "INR");
        pay(india(), 500L, "INR");
        pay(india(), 700L, "INR");

        assertThat(extremes("LOWEST", "INR", 2).employees()).extracting(ExtremeEmployee::amountMinor)
                .containsExactly(500L, 700L);
    }

    @Test
    void splitsATieByEmployeeIdWhenTheLimitCutsThroughIt() {
        final long first = pay(india(), 500L, "INR");
        pay(india(), 500L, "INR");
        final long third = pay(india(), 900L, "INR");

        assertThat(extremes("HIGHEST", "INR", 2).employees()).extracting(ExtremeEmployee::id)
                .containsExactly(third, first);
        assertThat(extremes("LOWEST", "INR", 1).employees()).extracting(ExtremeEmployee::id)
                .containsExactly(first);
    }

    @Test
    void ranksOnlyFormerEmployeesWhenAsked() {
        pay(india(), 100L, "INR");
        final long former = payFormer(india(), 900L, "INR", TODAY.minusDays(1));

        final ExtremesResponse response = insights.extremes(ExtremesQuery.from(
                new ExtremesParams("HIGHEST", "INR", null, null, null, null, "FORMER")));

        assertThat(response.employees()).extracting(ExtremeEmployee::id).containsExactly(former);
    }

    @Test
    void answersNoEmployeesForACurrencyNobodyIsPaidIn() {
        pay(india(), 100L, "INR");

        assertThat(extremes("HIGHEST", "GBP", 10).employees()).isEmpty();
    }

    private InsightsResponse slice(final String dimension) {
        return statusSlice(null, dimension);
    }

    private InsightsResponse statusSlice(final String status) {
        return statusSlice(status, "COUNTRY");
    }

    private InsightsResponse statusSlice(final String status, final String dimension) {
        return insights.insights(InsightsQuery.from(new InsightsParams(dimension, null, null, null, null, status)));
    }

    private ExtremesResponse extremes(final String order, final String currency, final int limit) {
        return insights.extremes(ExtremesQuery.from(
                new ExtremesParams(order, currency, limit, null, null, null, null)));
    }

    private static long headcount(final InsightsResponse response) {
        return response.groups().stream().mapToLong(InsightGroup::headcount).sum();
    }

    private static List<String> labels(final InsightsResponse response) {
        return response.groups().stream().map(InsightGroup::label).toList();
    }

    private static Person india() {
        return new Person("IN", "Engineering", "Software Engineer");
    }

    private long pay(final Person person, final long amountMinor, final String currency) {
        final long id = hire(person, HIRED, null);
        employees.insertSalary(id, HIRED, amountMinor, currency);
        return id;
    }

    private long payFormer(
            final Person person, final long amountMinor, final String currency, final LocalDate terminated) {
        final long id = hire(person, HIRED, terminated);
        employees.insertSalary(id, HIRED, amountMinor, currency);
        return id;
    }

    private void tenant(final LocalDate hireDate) {
        final long id = hire(india(), hireDate, null);
        employees.insertSalary(id, hireDate, 100L, "INR");
    }

    private long hire(final Person person, final LocalDate hireDate, final LocalDate terminationDate) {
        emailCounter++;
        final Long id = jdbcTemplate.queryForObject("""
                insert into employee (full_name, email, hire_date, termination_date,
                                      department_id, job_title_id, country_code)
                values ('Test Person', ?, ?, cast(? as date),
                        (select id from department where name = ?), (select id from job_title where name = ?), ?)
                returning id""", Long.class, "person" + emailCounter + "@acme.example", hireDate, terminationDate,
                person.department(), person.jobTitle(), person.country());
        return Objects.requireNonNull(id);
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }
}
