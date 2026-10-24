package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.employee.api.CreateEmployeeRequest;
import com.acme.peoplemanagement.employee.api.CurrentSalaryResponse;
import com.acme.peoplemanagement.employee.api.EmployeeResponse;
import com.acme.peoplemanagement.employee.api.InitialSalaryRequest;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.employee.rules.EmployeeStatus;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import com.acme.peoplemanagement.support.FixedClockConfiguration;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeServiceIntegrationTest {

    private static final LocalDate TODAY = FixedClockConfiguration.TODAY;
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private int departmentId;
    private int jobTitleId;

    @BeforeEach
    void findReferenceIds() {
        employees = new TestEmployees(jdbcTemplate);
        departmentId = idOf("department", "Engineering");
        jobTitleId = idOf("job_title", "Software Engineer");
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void createsAnEmployeeWithTheFirstSalaryAndReadsItBack() {
        final EmployeeResponse created =
                employeeService.create(request("asha@acme.example", HIRE_DATE, null, 125_000_000L));

        assertThat(created.fullName()).isEqualTo("Asha Rao");
        assertThat(created.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(created.department().name()).isEqualTo("Engineering");
        assertThat(created.jobTitle().name()).isEqualTo("Software Engineer");
        assertThat(created.country().name()).isEqualTo("India");
        assertThat(created.currentSalary())
                .isEqualTo(new CurrentSalaryResponse(HIRE_DATE, 125_000_000L, "INR"));
        assertThat(created.version()).isZero();
        assertThat(employeeService.get(created.id())).isEqualTo(created);
    }

    @Test
    void usesTheGivenFirstSalaryDateInsteadOfTheHireDate() {
        final LocalDate later = LocalDate.of(2024, 9, 1);

        final EmployeeResponse created =
                employeeService.create(request("later@acme.example", HIRE_DATE, later, 100L));

        assertThat(created.currentSalary().effectiveDate()).isEqualTo(later);
    }

    @Test
    void acceptsAHireDateOfToday() {
        final EmployeeResponse created =
                employeeService.create(request("today@acme.example", TODAY, null, 100L));

        assertThat(created.hireDate()).isEqualTo(TODAY);
        assertThat(created.currentSalary().effectiveDate()).isEqualTo(TODAY);
    }

    @Test
    void acceptsTheLargestAmountTheDatabaseAllows() {
        final EmployeeResponse created = employeeService.create(
                request("cap@acme.example", HIRE_DATE, null, Money.MAX_AMOUNT_MINOR));

        assertThat(created.currentSalary().amountMinor()).isEqualTo(Money.MAX_AMOUNT_MINOR);
    }

    @Test
    void rejectsAnEmailThatDiffersOnlyInCaseFromAnExistingOne() {
        employeeService.create(request("dup@acme.example", HIRE_DATE, null, 100L));

        assertThatThrownBy(() -> employeeService.create(request("DUP@acme.example", HIRE_DATE, null, 100L)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void namesTheFieldWhenAReferenceDoesNotExist() {
        final CreateEmployeeRequest unknownDepartment = new CreateEmployeeRequest("Asha Rao", "unknown@acme.example",
                HIRE_DATE, null, -1, jobTitleId, "IN", new InitialSalaryRequest(null, 100L, "INR"));

        assertThatThrownBy(() -> employeeService.create(unknownDepartment))
                .isInstanceOfSatisfying(UnknownReferenceException.class,
                        exception -> assertThat(exception.field()).isEqualTo("departmentId"));
    }

    @Test
    void namesTheCurrencyFieldWhenTheCurrencyDoesNotExist() {
        final CreateEmployeeRequest unknownCurrency = new CreateEmployeeRequest("Asha Rao", "currency@acme.example",
                HIRE_DATE, null, departmentId, jobTitleId, "IN", new InitialSalaryRequest(null, 100L, "ZZZ"));

        assertThatThrownBy(() -> employeeService.create(unknownCurrency))
                .isInstanceOfSatisfying(UnknownReferenceException.class,
                        exception -> assertThat(exception.field()).isEqualTo("initialSalary.currency"));
    }

    @Test
    void leavesNoEmployeeBehindWhenTheFirstSalaryBreaksARule() {
        final LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> employeeService.create(request("future@acme.example", tomorrow, null, 100L)))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.NO_CURRENT_SALARY));

        assertThat(employeeCountWithEmail("future@acme.example")).isZero();
    }

    @Test
    void reportsAnEmployeeThatDoesNotExist() {
        assertThatThrownBy(() -> employeeService.get(-1L)).isInstanceOf(EmployeeNotFoundException.class);
    }

    private CreateEmployeeRequest request(
            final String email, final LocalDate hireDate, final LocalDate salaryDate, final long amountMinor) {
        return new CreateEmployeeRequest("  Asha Rao ", email, hireDate, null, departmentId, jobTitleId, "IN",
                new InitialSalaryRequest(salaryDate, amountMinor, "INR"));
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }

    private int employeeCountWithEmail(final String email) {
        final Integer count = jdbcTemplate.queryForObject(
                "select count(*) from employee where email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }
}
