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
class SalaryChangeMigrationIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2024, 4, 1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("change@acme.example", DATE);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void acceptsEachKindOfChangeWithTheValuesItShouldHave() {
        assertThatCode(() -> {
            insert("ADDED", null, DATE);
            insert("EDITED", DATE, DATE);
            insert("DELETED", DATE, null);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnActionThatIsNotAddedEditedOrDeleted() {
        assertThatThrownBy(() -> insert("RENAMED", DATE, DATE)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnAddedRowThatHasAnOldValue() {
        assertThatThrownBy(() -> insert("ADDED", DATE, DATE)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADeletedRowThatHasANewValue() {
        assertThatThrownBy(() -> insert("DELETED", DATE, DATE)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnEditedRowWithoutBothValues() {
        assertThatThrownBy(() -> insert("EDITED", null, DATE)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("EDITED", DATE, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAChangeForAnEmployeeThatDoesNotExist() {
        assertThatThrownBy(() -> insertFor(-1L, 5L, "ADDED", null, DATE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsAChangeAfterTheEntryItDescribesIsDeleted() {
        employees.insertSalary(employeeId, DATE, 100L, "INR");
        final long entryId = queryLong("select id from salary_entry where employee_id = " + employeeId);
        insertFor(employeeId, entryId, "ADDED", null, DATE);

        jdbcTemplate.update("delete from salary_entry where id = ?", entryId);

        assertThat(queryLong("select count(*) from salary_entry_change where entry_id = " + entryId)).isEqualTo(1L);
    }

    @Test
    void removesAnEmployeesChangesWhenTheEmployeeIsDeletedAndLeavesOthersAlone() {
        final long other = employees.insert("other-change@acme.example", DATE);
        insertFor(employeeId, 1L, "ADDED", null, DATE);
        insertFor(other, 2L, "ADDED", null, DATE);

        jdbcTemplate.update("delete from employee where id = ?", employeeId);

        assertThat(queryLong("select count(*) from salary_entry_change where employee_id = " + employeeId)).isZero();
        assertThat(queryLong("select count(*) from salary_entry_change where employee_id = " + other)).isEqualTo(1L);
    }

    @Test
    void indexesAnEmployeesChangesNewestFirst() {
        assertThat(queryLong("select count(*) from pg_indexes where tablename = 'salary_entry_change' "
                + "and indexdef like '%(employee_id, changed_at DESC)%'")).isEqualTo(1L);
    }

    private void insert(final String action, final LocalDate oldDate, final LocalDate newDate) {
        insertFor(employeeId, 5L, action, oldDate, newDate);
    }

    private void insertFor(
            final long employee, final long entryId, final String action, final LocalDate oldDate,
            final LocalDate newDate) {
        jdbcTemplate.update("""
                insert into salary_entry_change (employee_id, entry_id, action,
                    old_effective_date, old_amount_minor, old_currency_code,
                    new_effective_date, new_amount_minor, new_currency_code, changed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())""",
                employee, entryId, action,
                oldDate, oldDate == null ? null : 100L, oldDate == null ? null : "INR",
                newDate, newDate == null ? null : 200L, newDate == null ? null : "INR");
    }

    private long queryLong(final String sql) {
        final Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? -1 : value;
    }
}
