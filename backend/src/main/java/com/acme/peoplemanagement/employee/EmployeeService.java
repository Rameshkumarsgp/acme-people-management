package com.acme.peoplemanagement.employee;

import com.acme.peoplemanagement.employee.api.CreateEmployeeRequest;
import com.acme.peoplemanagement.employee.api.CurrentSalaryResponse;
import com.acme.peoplemanagement.employee.api.EmployeeResponse;
import com.acme.peoplemanagement.employee.api.InitialSalaryRequest;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.employee.rules.EmployeeStatus;
import com.acme.peoplemanagement.reference.ReferenceLookup;
import com.acme.peoplemanagement.salary.SalaryService;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and reads employees. Salary rules are applied by the salary service, in the same transaction. */
@Service
public class EmployeeService {

    /** The unique index on lower(email) in migration 009. */
    private static final String EMAIL_UNIQUE_INDEX = "employee_email_unique";

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;
    private final ReferenceLookup referenceLookup;
    private final Clock clock;

    EmployeeService(
            final EmployeeRepository employeeRepository,
            final SalaryService salaryService,
            final ReferenceLookup referenceLookup,
            final Clock clock) {
        this.employeeRepository = employeeRepository;
        this.salaryService = salaryService;
        this.referenceLookup = referenceLookup;
        this.clock = clock;
    }

    @Transactional
    public EmployeeResponse create(final CreateEmployeeRequest request) {
        verifyReferencesExist(request);
        final LocalDate today = LocalDate.now(clock);
        final Employee employee = insertEmployee(request);
        salaryService.recordFirstEntry(employee.id(), request.hireDate(), firstEntryOf(request), today);
        return toResponse(employee, today);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(final long id) {
        final Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return toResponse(employee, LocalDate.now(clock));
    }

    private void verifyReferencesExist(final CreateEmployeeRequest request) {
        requireFound(referenceLookup.department(request.departmentId()), "departmentId", "Unknown department.");
        requireFound(referenceLookup.jobTitle(request.jobTitleId()), "jobTitleId", "Unknown job title.");
        requireFound(referenceLookup.country(request.countryCode()), "countryCode", "Unknown country.");
        requireFound(referenceLookup.currency(request.initialSalary().currency()),
                "initialSalary.currency", "Unknown currency.");
    }

    private static void requireFound(final Optional<?> reference, final String field, final String message) {
        if (reference.isEmpty()) {
            throw new UnknownReferenceException(field, message);
        }
    }

    /** The unique index is the only judge of a duplicate email, so two requests at once cannot both succeed. */
    private Employee insertEmployee(final CreateEmployeeRequest request) {
        try {
            return employeeRepository.saveAndFlush(new Employee(toProfile(request)));
        } catch (DataIntegrityViolationException exception) {
            if (violatesEmailIndex(exception)) {
                throw new EmailAlreadyExistsException();
            }
            throw exception;
        }
    }

    private static boolean violatesEmailIndex(final DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                && EMAIL_UNIQUE_INDEX.equals(violation.getConstraintName());
    }

    private static EmployeeProfile toProfile(final CreateEmployeeRequest request) {
        return new EmployeeProfile(request.fullName().strip(), request.email(), request.hireDate(),
                request.departmentId(), request.jobTitleId(), request.countryCode());
    }

    /** The first entry starts on the hire date unless the request names another date. */
    private static TimelineEntry firstEntryOf(final CreateEmployeeRequest request) {
        final InitialSalaryRequest salary = request.initialSalary();
        final LocalDate effectiveDate = salary.effectiveDate() == null ? request.hireDate() : salary.effectiveDate();
        return new TimelineEntry(effectiveDate, new Money(salary.amountMinor(), salary.currency()));
    }

    private EmployeeResponse toResponse(final Employee employee, final LocalDate today) {
        return new EmployeeResponse(
                employee.id(),
                employee.fullName(),
                employee.email(),
                employee.hireDate(),
                employee.terminationDate(),
                EmployeeStatus.on(today, employee.terminationDate()),
                stored(referenceLookup.department(employee.departmentId()), "department", employee),
                stored(referenceLookup.jobTitle(employee.jobTitleId()), "job title", employee),
                stored(referenceLookup.country(employee.countryCode()), "country", employee),
                currentSalaryOf(employee, today),
                employee.version());
    }

    private CurrentSalaryResponse currentSalaryOf(final Employee employee, final LocalDate today) {
        final TimelineEntry entry = stored(salaryService.currentSalary(employee.id(), today),
                "current salary", employee);
        return new CurrentSalaryResponse(
                entry.effectiveDate(), entry.salary().amountMinor(), entry.salary().currencyCode());
    }

    /** A foreign key or a salary rule guarantees these exist, so a miss means the data is broken. */
    private static <T> T stored(final Optional<T> value, final String kind, final Employee employee) {
        return value.orElseThrow(() ->
                new IllegalStateException("Employee " + employee.id() + " has no " + kind + "."));
    }
}
