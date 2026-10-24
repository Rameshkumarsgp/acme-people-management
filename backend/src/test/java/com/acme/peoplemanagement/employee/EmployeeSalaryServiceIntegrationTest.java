package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.employee.api.AddSalaryEntryRequest;
import com.acme.peoplemanagement.employee.api.SalaryEntryResponse;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
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
class EmployeeSalaryServiceIntegrationTest {

    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);
    private static final int RACE_ROUNDS = 25;

    @Autowired
    private EmployeeSalaryService employeeSalaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("employee-salary@acme.example", HIRE_DATE);
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void addsAnEntryAndReturnsItWithItsIdAndTheDerivedValues() {
        final SalaryEntryResponse added = employeeSalaryService.add(employeeId, request("2026-04-01", 150L, "INR"));

        assertThat(added.id()).isPositive();
        assertThat(added.current()).isTrue();
        assertThat(added.endDate()).isNull();
        assertThat(added.currency()).isEqualTo("INR");
    }

    @Test
    void refusesARaiseDatedAfterTheEmployeeWasTerminated() {
        final long leaver = employees.insert("Leaver", "leaver@acme.example", HIRE_DATE, LocalDate.of(2026, 10, 9));
        employees.insertSalary(leaver, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> employeeSalaryService.add(leaver, request("2026-10-10", 150L, "INR")))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_AFTER_TERMINATION));
        assertThat(employeeSalaryService.list(leaver)).hasSize(1);
    }

    @Test
    void acceptsARaiseDatedOnTheLastDayOfAnEmployeeWhoLeft() {
        final long leaver = employees.insert("Leaver", "lastday@acme.example", HIRE_DATE, LocalDate.of(2026, 10, 9));
        employees.insertSalary(leaver, HIRE_DATE, 100L, "INR");

        final SalaryEntryResponse added = employeeSalaryService.add(leaver, request("2026-10-09", 150L, "INR"));

        assertThat(added.effectiveDate()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void listsTheHistoryNewestFirst() {
        employeeSalaryService.add(employeeId, request("2026-04-01", 150L, "INR"));

        final List<SalaryEntryResponse> history = employeeSalaryService.list(employeeId);

        assertThat(history).extracting(SalaryEntryResponse::effectiveDate)
                .containsExactly(LocalDate.of(2026, 4, 1), HIRE_DATE);
    }

    @Test
    void reportsAnEmployeeThatDoesNotExistWhenAdding() {
        assertThatThrownBy(() -> employeeSalaryService.add(-1L, request("2026-04-01", 150L, "INR")))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void reportsAnEmployeeThatDoesNotExistWhenListing() {
        assertThatThrownBy(() -> employeeSalaryService.list(-1L)).isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void namesTheCurrencyFieldWhenTheCurrencyDoesNotExist() {
        assertThatThrownBy(() -> employeeSalaryService.add(employeeId, request("2026-04-01", 150L, "ZZZ")))
                .isInstanceOfSatisfying(UnknownReferenceException.class,
                        exception -> assertThat(exception.field()).isEqualTo("currency"));
    }

    /** One race is a coin flip without the lock, so it is repeated on fresh dates to make a missing lock show. */
    @Test
    void twoSimultaneousAddsOnTheSameDateLeaveExactlyOneEntryEveryTime() throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                final LocalDate date = LocalDate.of(2025, 1, 1).plusDays(round);
                final List<String> results = raceTwoAdds(pool, date);

                assertThat(results).as("round %d", round).containsOnlyOnce("added");
                assertThat(results).filteredOn(result -> !result.equals("added"))
                        .singleElement().asString().startsWith("SalaryRuleViolation");
                assertThat(entriesOn(date)).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Two threads add the same date at the same moment. Each result is "added" or the exception it hit. */
    private List<String> raceTwoAdds(final ExecutorService pool, final LocalDate date) throws Exception {
        final CyclicBarrier bothReady = new CyclicBarrier(2);
        final Callable<String> add = () -> {
            bothReady.await();
            try {
                employeeSalaryService.add(employeeId, new AddSalaryEntryRequest(date, 150L, "INR"));
                return "added";
            } catch (RuntimeException exception) {
                return exception.getClass().getSimpleName() + ":" + exception.getMessage();
            }
        };
        final List<Future<String>> outcomes = pool.invokeAll(List.of(add, add), 60, TimeUnit.SECONDS);
        return List.of(outcomes.get(0).get(), outcomes.get(1).get());
    }

    private int entriesOn(final LocalDate date) {
        final Integer count = jdbcTemplate.queryForObject(
                "select count(*) from salary_entry where employee_id = ? and effective_date = ?",
                Integer.class, employeeId, date);
        return count == null ? 0 : count;
    }

    private static AddSalaryEntryRequest request(final String date, final long amountMinor, final String currency) {
        return new AddSalaryEntryRequest(LocalDate.parse(date), amountMinor, currency);
    }
}
