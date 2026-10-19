package com.acme.peoplemanagement.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SalaryMigrationIntegrationTest {

    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);
    private static final long LARGEST_EXACT_INTEGER = 9_007_199_254_740_991L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("salary@acme.example", HIRE_DATE);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void rejectsASecondEntryOnTheSameDate() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, 200L, "INR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnAmountOfZero() {
        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, 0L, "INR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsTheLargestAmountAJavaScriptNumberHoldsExactly() {
        assertThatCode(() -> employees.insertSalary(employeeId, HIRE_DATE, LARGEST_EXACT_INTEGER, "INR"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOneMoreThanTheLargestExactAmount() {
        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, LARGEST_EXACT_INTEGER + 1, "INR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsACurrencyThatDoesNotExist() {
        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, 100L, "XXX"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removesTheEntriesOfADeletedEmployeeAndLeavesOtherEmployeesUntouched() {
        final long otherEmployeeId = employees.insert("other@acme.example", HIRE_DATE);
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(otherEmployeeId, HIRE_DATE, 200L, "USD");

        jdbcTemplate.update("delete from employee where id = ?", employeeId);

        assertThat(entryCountOf(employeeId)).isZero();
        assertThat(entryCountOf(otherEmployeeId)).isEqualTo(1);
    }

    private int entryCountOf(final long id) {
        final Integer count = jdbcTemplate.queryForObject(
                "select count(*) from salary_entry where employee_id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }
}
