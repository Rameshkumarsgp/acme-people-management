package com.acme.peoplemanagement.support;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts employee and salary rows directly, so database tests do not depend on the code under test. */
public final class TestEmployees {

    private final JdbcTemplate jdbcTemplate;

    public TestEmployees(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(final String email, final LocalDate hireDate) {
        return insert("Test Person", email, hireDate, null);
    }

    public long insert(
            final String fullName, final String email, final LocalDate hireDate, final LocalDate terminationDate) {
        final Long id = jdbcTemplate.queryForObject("""
                insert into employee (full_name, email, hire_date, termination_date,
                                      department_id, job_title_id, country_code)
                values (?, ?, ?, cast(? as date),
                        (select min(id) from department), (select min(id) from job_title), 'IN')
                returning id""", Long.class, fullName, email, hireDate, terminationDate);
        return Objects.requireNonNull(id);
    }

    public void insertSalary(
            final long employeeId, final LocalDate effectiveDate, final long amountMinor, final String currencyCode) {
        jdbcTemplate.update("""
                insert into salary_entry (employee_id, effective_date, amount_minor, currency_code)
                values (?, ?, ?, ?)""", employeeId, effectiveDate, amountMinor, currencyCode);
    }

    /** Salary entries go with their employees, through the delete cascade. */
    public void deleteAll() {
        jdbcTemplate.update("delete from employee");
    }
}
