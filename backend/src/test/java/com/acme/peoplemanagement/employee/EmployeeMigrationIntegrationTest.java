package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeMigrationIntegrationTest {

    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

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
    void rejectsADuplicateEmailEvenWhenOnlyTheCaseDiffers() {
        employees.insert("asha@acme.example", HIRE_DATE);

        assertThatThrownBy(() -> employees.insert("ASHA@acme.example", HIRE_DATE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("employee_email_unique");
    }

    @Test
    void rejectsABlankFullName() {
        assertThatThrownBy(() -> employees.insert("   ", "blank@acme.example", HIRE_DATE, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsATerminationDateBeforeTheHireDate() {
        assertThatThrownBy(() -> employees.insert("Left Early", "early@acme.example", HIRE_DATE,
                HIRE_DATE.minusDays(1))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsATerminationDateOnTheHireDate() {
        assertThatCode(() -> employees.insert("One Day", "oneday@acme.example", HIRE_DATE, HIRE_DATE))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsADepartmentThatDoesNotExist() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into employee (full_name, email, hire_date, department_id, job_title_id, country_code)
                values ('No Department', 'nodept@acme.example', ?, -1,
                        (select min(id) from job_title), 'IN')""", HIRE_DATE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void indexesTheColumnsTheDirectoryAndInsightsFilterOn() {
        final List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'employee'", String.class);

        assertThat(indexes).contains("employee_country_idx", "employee_department_idx", "employee_job_title_idx");
    }
}
