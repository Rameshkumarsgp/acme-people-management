package com.acme.peoplemanagement.directory;

import static com.acme.peoplemanagement.directory.DirectoryParamsBuilder.params;
import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.directory.api.DirectoryRow;
import com.acme.peoplemanagement.directory.api.PagedResponse;
import com.acme.peoplemanagement.directory.rules.DirectoryQuery;
import com.acme.peoplemanagement.employee.rules.EmployeeStatus;
import com.acme.peoplemanagement.support.FixedClockConfiguration;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DirectoryServiceIntegrationTest {

    private static final LocalDate TODAY = FixedClockConfiguration.TODAY;
    private static final LocalDate HIRED = LocalDate.of(2020, 1, 1);

    @Autowired
    private DirectoryService directory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;

    /** Who a test employee is: the reference names are looked up by name when the row is inserted. */
    private record Person(String name, String email, String country, String department, String jobTitle) {
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
    void listsAnActiveEmployeeWithTheNamesAndTheCurrentSalary() {
        final long id = hire(asha(), HIRED, null);
        salary(id, HIRED, 125_000_000L, "INR");

        final PagedResponse<DirectoryRow> page = list(params());

        assertThat(page.content()).hasSize(1);
        final DirectoryRow row = page.content().getFirst();
        assertThat(row.id()).isEqualTo(id);
        assertThat(row.fullName()).isEqualTo("Asha Rao");
        assertThat(row.email()).isEqualTo("asha@acme.example");
        assertThat(row.department()).isEqualTo("Engineering");
        assertThat(row.jobTitle()).isEqualTo("Software Engineer");
        assertThat(row.country()).isEqualTo("India");
        assertThat(row.status()).isEqualTo("ACTIVE");
        assertThat(row.hireDate()).isEqualTo(HIRED);
        assertThat(row.currentSalary()).isEqualTo(new DirectoryRow.Salary(125_000_000L, "INR"));
    }

    @Test
    void showsTheSameStatusAsTheEmployeeRuleOnTheBoundaryDays() {
        for (final LocalDate termination : new LocalDate[] {null, TODAY.minusDays(1), TODAY, TODAY.plusDays(1)}) {
            final long id = hire(person("T" + termination, "t" + termination + "@acme.example"), HIRED, termination);
            salary(id, HIRED, 100L, "INR");
        }

        final PagedResponse<DirectoryRow> page = list(params().status("ALL"));

        assertThat(page.content()).hasSize(4).allSatisfy(row -> assertThat(row.status())
                .isEqualTo(EmployeeStatus.on(TODAY, terminationOf(row.id())).name()));
        assertThat(page.content()).extracting(DirectoryRow::status).containsExactlyInAnyOrder(
                "ACTIVE", "FORMER", "ACTIVE", "ACTIVE");
    }

    @Test
    void filtersByStatus() {
        final long active = hire(person("Active", "active@acme.example"), HIRED, null);
        final long former = hire(person("Former", "former@acme.example"), HIRED, TODAY.minusDays(1));
        salary(active, HIRED, 100L, "INR");
        salary(former, HIRED, 100L, "INR");

        assertThat(ids(list(params()))).containsExactly(active);
        assertThat(ids(list(params().status("FORMER")))).containsExactly(former);
        assertThat(ids(list(params().status("ALL")))).containsExactlyInAnyOrder(active, former);
    }

    @Test
    void filtersByCountryDepartmentJobTitleAndCurrencyAlone() {
        final long asha = hireWithSalary(asha(), 100L, "INR");
        final long ben = hireWithSalary(ben(), 200L, "USD");

        assertThat(ids(list(params().country("IN")))).containsExactly(asha);
        assertThat(ids(list(params().department(idOf("department", "Sales"))))).containsExactly(ben);
        assertThat(ids(list(params().jobTitle(idOf("job_title", "Software Engineer"))))).containsExactly(asha);
        assertThat(ids(list(params().currency("USD")))).containsExactly(ben);
    }

    @Test
    void combinesFilters() {
        final long asha = hireWithSalary(asha(), 100L, "INR");
        hireWithSalary(ben(), 200L, "USD");

        assertThat(ids(list(params().country("IN").currency("INR")))).containsExactly(asha);
        assertThat(list(params().country("IN").currency("USD")).content()).isEmpty();
    }

    @Test
    void answersAnEmptyPageForAFilterThatMatchesNothing() {
        hireWithSalary(asha(), 100L, "INR");

        final PagedResponse<DirectoryRow> page = list(params().department(-1));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void searchesNameAndEmailIgnoringCase() {
        final long asha = hireWithSalary(asha(), 100L, "INR");
        final long ben = hireWithSalary(ben(), 100L, "USD");

        assertThat(ids(list(params().search("ASHA")))).containsExactly(asha);
        assertThat(ids(list(params().search("carter")))).containsExactly(ben);
        assertThat(ids(list(params().search("  BEN@acme  ")))).containsExactly(ben);
    }

    @Test
    void treatsAPercentOrUnderscoreInTheSearchAsTheCharacterItself() {
        final long underscore = hireWithSalary(person("Under Score", "john_doe@acme.example"), 100L, "INR");
        hireWithSalary(person("Other Person", "johnxdoe@acme.example"), 100L, "INR");

        assertThat(ids(list(params().search("john_doe")))).containsExactly(underscore);
        assertThat(list(params().search("%")).content()).isEmpty();
        assertThat(list(params().search("\\")).content()).isEmpty();
    }

    @Test
    void takesTheSalaryOfTheLatestEntryOnOrBeforeTodayAndIgnoresAFutureRaise() {
        final long id = hire(asha(), HIRED, null);
        salary(id, HIRED, 100L, "INR");
        salary(id, TODAY.plusDays(1), 900L, "INR");

        assertThat(list(params()).content().getFirst().currentSalary().amountMinor()).isEqualTo(100L);
    }

    @Test
    void takesARaiseDatedTodayAsTheCurrentSalary() {
        final long id = hire(asha(), HIRED, null);
        salary(id, HIRED, 100L, "INR");
        salary(id, TODAY, 500L, "INR");

        assertThat(list(params()).content().getFirst().currentSalary().amountMinor()).isEqualTo(500L);
    }

    @Test
    void filtersByTheCurrencyOfTheCurrentSalaryNotAnOldOne() {
        final long id = hire(asha(), HIRED, null);
        salary(id, HIRED, 100L, "INR");
        salary(id, TODAY, 5L, "GBP");

        assertThat(ids(list(params().currency("GBP")))).containsExactly(id);
        assertThat(list(params().currency("INR")).content()).isEmpty();
    }

    @Test
    void sortsByEachFieldWithADifferentOrderForEach() {
        hireWithSalary(new Person("alice", "a@acme.example", "IN", "Sales", "QA Engineer"), 200L, "USD");
        hireWithSalary(new Person("Bob", "b@acme.example", "US", "Engineering", "Sales Manager"), 300L, "USD");
        hireWithSalary(new Person("Charlie", "c@acme.example", "GB", "Finance", "Accountant"), 100L, "USD");

        assertThat(names(list(params().sort("fullName,asc")))).containsExactly("alice", "Bob", "Charlie");
        assertThat(names(list(params().sort("fullName,desc")))).containsExactly("Charlie", "Bob", "alice");
        assertThat(names(list(params().sort("department,asc")))).containsExactly("Bob", "Charlie", "alice");
        assertThat(names(list(params().sort("jobTitle,asc")))).containsExactly("Charlie", "alice", "Bob");
        assertThat(names(list(params().sort("country,asc")))).containsExactly("alice", "Charlie", "Bob");
        assertThat(names(list(params().currency("USD").sort("salary,desc")))).containsExactly(
                "Bob", "alice", "Charlie");
        assertThat(names(list(params().currency("USD").sort("salary,asc")))).containsExactly(
                "Charlie", "alice", "Bob");
    }

    @Test
    void sortsByHireDate() {
        hireWithSalaryOn(person("Late", "late@acme.example"), LocalDate.of(2024, 1, 1));
        hireWithSalaryOn(person("Early", "early@acme.example"), LocalDate.of(2019, 1, 1));

        assertThat(names(list(params().sort("hireDate,asc")))).containsExactly("Early", "Late");
    }

    @Test
    void sortsBySalaryOnlyAmongEmployeesPaidInTheChosenCurrency() {
        hireWithSalary(person("Rupees Low", "a@acme.example"), 100L, "INR");
        hireWithSalary(person("Rupees High", "b@acme.example"), 900L, "INR");
        hireWithSalary(person("Dollars", "c@acme.example"), 5_000L, "USD");

        assertThat(names(list(params().currency("INR").sort("salary,desc")))).containsExactly(
                "Rupees High", "Rupees Low");
    }

    @Test
    void givesStablePagesThatNeitherOverlapNorSkipWhenSortValuesAreEqual() {
        final List<Long> hired = new ArrayList<>();
        for (int number = 1; number <= 5; number++) {
            hired.add(hireWithSalaryOn(person("Same " + number, "same" + number + "@acme.example"), HIRED));
        }

        final List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            seen.addAll(ids(list(params().sort("hireDate,asc").page(page).size(2))));
        }

        assertThat(seen).containsExactlyElementsOf(hired);
    }

    @Test
    void countsThePagesAndTheTotal() {
        for (int number = 1; number <= 5; number++) {
            hireWithSalaryOn(person("P" + number, "p" + number + "@acme.example"), HIRED);
        }

        final PagedResponse<DirectoryRow> last = list(params().page(2).size(2));
        final PagedResponse<DirectoryRow> beyond = list(params().page(9).size(2));

        assertThat(last.content()).hasSize(1);
        assertThat(last.page()).isEqualTo(2);
        assertThat(last.size()).isEqualTo(2);
        assertThat(last.totalElements()).isEqualTo(5);
        assertThat(last.totalPages()).isEqualTo(3);
        assertThat(beyond.content()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(5);
    }

    @Test
    void listsEveryEmployeeWhenAllStatusesAreAsked() {
        hireWithSalary(person("One", "one@acme.example"), 100L, "INR");
        final long gone = hire(person("Two", "two@acme.example"), HIRED, TODAY.minusDays(30));
        salary(gone, HIRED, 100L, "INR");

        assertThat(list(params().status("ALL")).totalElements()).isEqualTo(employeeCount());
    }

    private PagedResponse<DirectoryRow> list(final DirectoryParamsBuilder builder) {
        return directory.list(DirectoryQuery.from(builder.build()));
    }

    private long hire(final Person person, final LocalDate hireDate, final LocalDate terminationDate) {
        final Long id = jdbcTemplate.queryForObject("""
                insert into employee (full_name, email, hire_date, termination_date,
                                      department_id, job_title_id, country_code)
                values (?, ?, ?, cast(? as date),
                        (select id from department where name = ?), (select id from job_title where name = ?), ?)
                returning id""", Long.class, person.name(), person.email(), hireDate, terminationDate,
                person.department(), person.jobTitle(), person.country());
        return Objects.requireNonNull(id);
    }

    private long hireWithSalary(final Person person, final long amountMinor, final String currency) {
        final long id = hire(person, HIRED, null);
        salary(id, HIRED, amountMinor, currency);
        return id;
    }

    private long hireWithSalaryOn(final Person person, final LocalDate hireDate) {
        final long id = hire(person, hireDate, null);
        salary(id, hireDate, 100L, "INR");
        return id;
    }

    private void salary(final long employeeId, final LocalDate date, final long amountMinor, final String currency) {
        employees.insertSalary(employeeId, date, amountMinor, currency);
    }

    private LocalDate terminationOf(final long id) {
        return jdbcTemplate.queryForObject(
                "select termination_date from employee where id = ?", LocalDate.class, id);
    }

    private int employeeCount() {
        final Integer count = jdbcTemplate.queryForObject("select count(*) from employee", Integer.class);
        return count == null ? -1 : count;
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }

    private static List<Long> ids(final PagedResponse<DirectoryRow> page) {
        return page.content().stream().map(DirectoryRow::id).toList();
    }

    private static List<String> names(final PagedResponse<DirectoryRow> page) {
        return page.content().stream().map(DirectoryRow::fullName).toList();
    }

    private static Person asha() {
        return new Person("Asha Rao", "asha@acme.example", "IN", "Engineering", "Software Engineer");
    }

    private static Person ben() {
        return new Person("Ben Carter", "ben@acme.example", "US", "Sales", "Sales Manager");
    }

    private static Person person(final String name, final String email) {
        return new Person(name, email, "IN", "Engineering", "Software Engineer");
    }
}
