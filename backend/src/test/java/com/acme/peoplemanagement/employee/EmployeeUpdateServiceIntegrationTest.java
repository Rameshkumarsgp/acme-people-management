package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.employee.api.CreateEmployeeRequest;
import com.acme.peoplemanagement.employee.api.EmployeeResponse;
import com.acme.peoplemanagement.employee.api.InitialSalaryRequest;
import com.acme.peoplemanagement.employee.api.UpdateEmployeeRequest;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.StaleVersionException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.employee.rules.EmployeeStatus;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import com.acme.peoplemanagement.support.FixedClockConfiguration;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeUpdateServiceIntegrationTest {

    private static final LocalDate TODAY = FixedClockConfiguration.TODAY;
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);
    private static final int RACE_ROUNDS = 10;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeSalaryService employeeSalaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private int departmentId;
    private int otherDepartmentId;
    private int jobTitleId;
    private EmployeeResponse asha;

    @BeforeEach
    void createAnEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        departmentId = idOf("department", "Engineering");
        otherDepartmentId = idOf("department", "Sales");
        jobTitleId = idOf("job_title", "Software Engineer");
        asha = employeeService.create(new CreateEmployeeRequest("Asha Rao", "asha@acme.example", HIRE_DATE, null,
                departmentId, jobTitleId, "IN", new InitialSalaryRequest(null, 100L, "INR")));
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void updatesTheProfileAndBumpsTheVersion() {
        final EmployeeResponse updated = employeeService.update(asha.id(), new UpdateEmployeeRequest(
                "  Asha R. Rao ", "asha.rao@acme.example", HIRE_DATE, null, otherDepartmentId, jobTitleId, "US", 0L));

        assertThat(updated.fullName()).isEqualTo("Asha R. Rao");
        assertThat(updated.email()).isEqualTo("asha.rao@acme.example");
        assertThat(updated.department().name()).isEqualTo("Sales");
        assertThat(updated.country().name()).isEqualTo("United States");
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(employeeService.get(asha.id())).isEqualTo(updated);
    }

    @Test
    void refusesAStaleVersionAndChangesNothing() {
        assertThatThrownBy(() -> employeeService.update(asha.id(), changeName("Someone Else", 5L)))
                .isInstanceOf(StaleVersionException.class);

        assertThat(employeeService.get(asha.id()).fullName()).isEqualTo("Asha Rao");
    }

    @Test
    void refusesASecondUpdateThatStillCarriesTheOldVersion() {
        employeeService.update(asha.id(), changeName("First", 0L));

        assertThatThrownBy(() -> employeeService.update(asha.id(), changeName("Second", 0L)))
                .isInstanceOf(StaleVersionException.class);
        assertThat(employeeService.get(asha.id()).fullName()).isEqualTo("First");
    }

    @Test
    void recordsThatSomeoneLeftAndCanTakeItBack() {
        final EmployeeResponse left = employeeService.update(asha.id(), withTermination(TODAY.minusDays(30), 0L));

        assertThat(left.status()).isEqualTo(EmployeeStatus.FORMER);
        assertThat(left.terminationDate()).isEqualTo(TODAY.minusDays(30));

        final EmployeeResponse back = employeeService.update(asha.id(), withTermination(null, left.version()));

        assertThat(back.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(back.terminationDate()).isNull();
    }

    @Test
    void refusesATerminationBeforeTheHireDateWithTheRuleAndNotADatabaseError() {
        assertThatThrownBy(() -> employeeService.update(asha.id(), withTermination(HIRE_DATE.minusDays(1), 0L)))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.TERMINATION_BEFORE_HIRE_DATE));
        assertThat(employeeService.get(asha.id()).terminationDate()).isNull();
    }

    @Test
    void refusesATerminationBeforeAnExistingSalaryEntryAndChangesNothing() {
        employees.insertSalary(asha.id(), LocalDate.of(2026, 4, 1), 150L, "INR");

        assertThatThrownBy(() -> employeeService.update(asha.id(), withTermination(LocalDate.of(2025, 1, 1), 0L)))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_AFTER_TERMINATION));
        final EmployeeResponse stored = employeeService.get(asha.id());
        assertThat(stored.terminationDate()).isNull();
        assertThat(stored.version()).isZero();
    }

    @Test
    void refusesAHireDateAfterAnExistingSalaryEntryAndKeepsTheOldProfile() {
        assertThatThrownBy(() -> employeeService.update(asha.id(), new UpdateEmployeeRequest("Renamed",
                "asha@acme.example", HIRE_DATE.plusMonths(1), null, departmentId, jobTitleId, "IN", 0L)))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE));

        final EmployeeResponse stored = employeeService.get(asha.id());
        assertThat(stored.fullName()).isEqualTo("Asha Rao");
        assertThat(stored.hireDate()).isEqualTo(HIRE_DATE);
    }

    @Test
    void acceptsAnEarlierHireDate() {
        final EmployeeResponse updated = employeeService.update(asha.id(), new UpdateEmployeeRequest("Asha Rao",
                "asha@acme.example", HIRE_DATE.minusMonths(1), null, departmentId, jobTitleId, "IN", 0L));

        assertThat(updated.hireDate()).isEqualTo(HIRE_DATE.minusMonths(1));
    }

    @Test
    void movesTheHireDateLaterAsLongAsNoSalaryEntryIsBeforeIt() {
        final EmployeeResponse paidFromJune = paidFromJune();

        final EmployeeResponse updated = employeeService.update(paidFromJune.id(),
                hireDateOf(paidFromJune, LocalDate.of(2024, 5, 1)));

        assertThat(updated.hireDate()).isEqualTo(LocalDate.of(2024, 5, 1));
    }

    @Test
    void acceptsAHireDateOnTheDateOfTheFirstSalaryEntry() {
        final EmployeeResponse paidFromJune = paidFromJune();

        final EmployeeResponse updated = employeeService.update(paidFromJune.id(),
                hireDateOf(paidFromJune, LocalDate.of(2024, 6, 1)));

        assertThat(updated.hireDate()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void refusesAHireDateOneDayAfterTheFirstSalaryEntry() {
        final EmployeeResponse paidFromJune = paidFromJune();

        assertThatThrownBy(() -> employeeService.update(paidFromJune.id(),
                hireDateOf(paidFromJune, LocalDate.of(2024, 6, 2))))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE));
        assertThat(employeeService.get(paidFromJune.id()).hireDate()).isEqualTo(HIRE_DATE);
    }

    @Test
    void refusesAHireDateAfterTheFirstSalaryEntryWhenThatEntryIsOnTheOldHireDate() {
        assertThatThrownBy(() -> employeeService.update(asha.id(), hireDateOf(asha, LocalDate.of(2024, 5, 1))))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE));
    }

    @Test
    void refusesAnEmailThatAnotherEmployeeUsesEvenInAnotherCase() {
        employeeService.create(new CreateEmployeeRequest("Ben Carter", "ben@acme.example", HIRE_DATE, null,
                departmentId, jobTitleId, "IN", new InitialSalaryRequest(null, 100L, "INR")));

        assertThatThrownBy(() -> employeeService.update(asha.id(), changeEmail("BEN@acme.example", 0L)))
                .isInstanceOf(EmailAlreadyExistsException.class);
        assertThat(employeeService.get(asha.id()).email()).isEqualTo("asha@acme.example");
    }

    @Test
    void allowsChangingOnlyTheCaseOfTheEmployeesOwnEmail() {
        final EmployeeResponse updated = employeeService.update(asha.id(), changeEmail("ASHA@acme.example", 0L));

        assertThat(updated.email()).isEqualTo("ASHA@acme.example");
    }

    @Test
    void namesTheFieldWhenAReferenceDoesNotExist() {
        assertThatThrownBy(() -> employeeService.update(asha.id(), new UpdateEmployeeRequest("Asha Rao",
                "asha@acme.example", HIRE_DATE, null, -1, jobTitleId, "IN", 0L)))
                .isInstanceOfSatisfying(UnknownReferenceException.class,
                        exception -> assertThat(exception.field()).isEqualTo("departmentId"));
    }

    @Test
    void reportsAnEmployeeThatDoesNotExist() {
        assertThatThrownBy(() -> employeeService.update(-1L, changeName("Nobody", 0L)))
                .isInstanceOf(EmployeeNotFoundException.class);
        assertThatThrownBy(() -> employeeService.delete(-1L, 0L)).isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void addingASalaryEntryDoesNotChangeTheVersion() {
        employeeSalaryService.add(asha.id(), new com.acme.peoplemanagement.employee.api.AddSalaryEntryRequest(
                LocalDate.of(2026, 4, 1), 150L, "INR"));

        assertThat(employeeService.get(asha.id()).version()).isZero();
    }

    @Test
    void acceptsAFormThatWasOpenedBeforeASalaryChange() {
        employeeSalaryService.add(asha.id(), new com.acme.peoplemanagement.employee.api.AddSalaryEntryRequest(
                LocalDate.of(2026, 4, 1), 150L, "INR"));

        final EmployeeResponse updated = employeeService.update(asha.id(), changeName("Renamed", 0L));

        assertThat(updated.fullName()).isEqualTo("Renamed");
    }

    @Test
    void revalidatesAnOldFormAgainstASalaryChangeMadeSinceItWasOpened() {
        employeeSalaryService.add(asha.id(), new com.acme.peoplemanagement.employee.api.AddSalaryEntryRequest(
                LocalDate.of(2026, 4, 1), 150L, "INR"));

        assertThatThrownBy(() -> employeeService.update(asha.id(), withTermination(LocalDate.of(2025, 1, 1), 0L)))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_AFTER_TERMINATION));
    }

    @Test
    void deletesTheEmployeeAndTheirSalaryEntries() {
        employees.insertSalary(asha.id(), LocalDate.of(2026, 4, 1), 150L, "INR");

        employeeService.delete(asha.id(), 0L);

        assertThat(count("employee where id = " + asha.id())).isZero();
        assertThat(count("salary_entry where employee_id = " + asha.id())).isZero();
    }

    @Test
    void refusesToDeleteWithAStaleVersion() {
        assertThatThrownBy(() -> employeeService.delete(asha.id(), 3L)).isInstanceOf(StaleVersionException.class);

        assertThat(count("employee where id = " + asha.id())).isEqualTo(1);
    }

    @Test
    void createsAnEmployeeWhoAlreadyLeft() {
        final EmployeeResponse leaver = employeeService.create(new CreateEmployeeRequest("Leo Leaver",
                "leo@acme.example", HIRE_DATE, TODAY.minusDays(100), departmentId, jobTitleId, "IN",
                new InitialSalaryRequest(null, 100L, "INR")));

        assertThat(leaver.status()).isEqualTo(EmployeeStatus.FORMER);
        assertThat(leaver.terminationDate()).isEqualTo(TODAY.minusDays(100));
    }

    @Test
    void refusesToCreateAnEmployeeWhoseTerminationIsBeforeTheHireDateAndLeavesNoRow() {
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest("Leo Leaver", "leo@acme.example",
                HIRE_DATE, HIRE_DATE.minusDays(1), departmentId, jobTitleId, "IN",
                new InitialSalaryRequest(null, 100L, "INR"))))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.TERMINATION_BEFORE_HIRE_DATE));
        assertThat(count("employee where email = 'leo@acme.example'")).isZero();
    }

    @Test
    void refusesToCreateAnEmployeeWhoseFirstSalaryIsAfterTheTermination() {
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest("Leo Leaver", "leo@acme.example",
                HIRE_DATE, TODAY.minusDays(100), departmentId, jobTitleId, "IN",
                new InitialSalaryRequest(TODAY.minusDays(50), 100L, "INR"))))
                .isInstanceOfSatisfying(SalaryRuleViolation.class, violation ->
                        assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_AFTER_TERMINATION));
        assertThat(count("employee where email = 'leo@acme.example'")).isZero();
    }

    @Test
    void leavesExactlyOneWinnerWhenTwoUpdatesCarryTheSameVersion() throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                final EmployeeResponse subject = employeeService.create(new CreateEmployeeRequest("Race " + round,
                        "race" + round + "@acme.example", HIRE_DATE, null, departmentId, jobTitleId, "IN",
                        new InitialSalaryRequest(null, 100L, "INR")));
                final CyclicBarrier together = new CyclicBarrier(2);

                final List<String> outcomes = raceTwoUpdates(pool, together, subject);

                assertThat(outcomes).containsExactlyInAnyOrder("updated", "stale");
                final EmployeeResponse stored = employeeService.get(subject.id());
                assertThat(stored.version()).isEqualTo(1L);
                assertThat(stored.fullName()).isIn("Winner A", "Winner B");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private List<String> raceTwoUpdates(
            final ExecutorService pool, final CyclicBarrier together, final EmployeeResponse subject)
            throws Exception {
        final List<Future<String>> results = new ArrayList<>();
        for (final String name : new String[] {"Winner A", "Winner B"}) {
            final Callable<String> update = () -> {
                together.await(10, TimeUnit.SECONDS);
                try {
                    employeeService.update(subject.id(), new UpdateEmployeeRequest(name, subject.email(), HIRE_DATE,
                            null, departmentId, jobTitleId, "IN", 0L));
                    return "updated";
                } catch (StaleVersionException exception) {
                    return "stale";
                }
            };
            results.add(pool.submit(update));
        }
        final List<String> outcomes = new ArrayList<>();
        for (final Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    /** Hired on 2024-04-01, but the first salary entry is dated 2024-06-01. */
    private EmployeeResponse paidFromJune() {
        return employeeService.create(new CreateEmployeeRequest("Paid From June", "june@acme.example", HIRE_DATE, null,
                departmentId, jobTitleId, "IN", new InitialSalaryRequest(LocalDate.of(2024, 6, 1), 100L, "INR")));
    }

    private UpdateEmployeeRequest hireDateOf(final EmployeeResponse employee, final LocalDate hireDate) {
        return new UpdateEmployeeRequest(employee.fullName(), employee.email(), hireDate, null, departmentId,
                jobTitleId, "IN", employee.version());
    }

    private UpdateEmployeeRequest changeName(final String name, final long version) {
        return new UpdateEmployeeRequest(name, "asha@acme.example", HIRE_DATE, null, departmentId, jobTitleId, "IN",
                version);
    }

    private UpdateEmployeeRequest changeEmail(final String email, final long version) {
        return new UpdateEmployeeRequest("Asha Rao", email, HIRE_DATE, null, departmentId, jobTitleId, "IN", version);
    }

    private UpdateEmployeeRequest withTermination(final LocalDate termination, final long version) {
        return new UpdateEmployeeRequest("Asha Rao", "asha@acme.example", HIRE_DATE, termination, departmentId,
                jobTitleId, "IN", version);
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }

    private int count(final String fromAndWhere) {
        final Integer count = jdbcTemplate.queryForObject("select count(*) from " + fromAndWhere, Integer.class);
        return count == null ? -1 : count;
    }
}
