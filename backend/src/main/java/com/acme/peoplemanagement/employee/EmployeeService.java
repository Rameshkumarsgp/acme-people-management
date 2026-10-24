package com.acme.peoplemanagement.employee;

import com.acme.peoplemanagement.employee.api.CreateEmployeeRequest;
import com.acme.peoplemanagement.employee.api.CurrentSalaryResponse;
import com.acme.peoplemanagement.employee.api.EmployeeResponse;
import com.acme.peoplemanagement.employee.api.InitialSalaryRequest;
import com.acme.peoplemanagement.employee.api.UpdateEmployeeRequest;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.StaleVersionException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.employee.rules.EmployeeStatus;
import com.acme.peoplemanagement.reference.ReferenceLookup;
import com.acme.peoplemanagement.salary.SalaryService;
import com.acme.peoplemanagement.salary.rules.EmploymentPeriod;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, reads, edits and deletes employees. The salary service applies the salary rules in the same transaction. */
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
        verifyReferencesExist(request.departmentId(), request.jobTitleId(), request.countryCode());
        requireFound(referenceLookup.currency(request.initialSalary().currency()),
                "initialSalary.currency", "Unknown currency.");
        final LocalDate today = LocalDate.now(clock);
        final EmploymentPeriod period = new EmploymentPeriod(request.hireDate(), request.terminationDate());
        final TimelineEntry firstEntry = firstEntryOf(request);
        salaryService.checkFirstEntry(period, firstEntry, today);
        final Employee employee = saveRejectingDuplicateEmail(new Employee(new EmployeeProfile(
                request.fullName().strip(), request.email(), request.hireDate(), request.terminationDate(),
                request.departmentId(), request.jobTitleId(), request.countryCode())));
        salaryService.recordFirstEntry(employee.id(), period, firstEntry, today);
        return toResponse(employee, today);
    }

    /**
     * Replaces an employee's profile. The rules run before anything is written, so a broken rule is reported as
     * that and not as a database error, and the row lock keeps the timeline from changing in between.
     */
    @Transactional
    public EmployeeResponse update(final long id, final UpdateEmployeeRequest request) {
        final Employee employee = lockedEmployee(id, request.version());
        verifyReferencesExist(request.departmentId(), request.jobTitleId(), request.countryCode());
        final LocalDate today = LocalDate.now(clock);
        salaryService.revalidate(id, new EmploymentPeriod(request.hireDate(), request.terminationDate()), today);
        employee.applyProfile(new EmployeeProfile(request.fullName().strip(), request.email(), request.hireDate(),
                request.terminationDate(), request.departmentId(), request.jobTitleId(), request.countryCode()));
        return toResponse(saveRejectingDuplicateEmail(employee), today);
    }

    /** Removes the employee and, through the database cascade, their salary entries. */
    @Transactional
    public void delete(final long id, final long version) {
        employeeRepository.delete(lockedEmployee(id, version));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(final long id) {
        final Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return toResponse(employee, LocalDate.now(clock));
    }

    private void verifyReferencesExist(final int departmentId, final int jobTitleId, final String countryCode) {
        requireFound(referenceLookup.department(departmentId), "departmentId", "Unknown department.");
        requireFound(referenceLookup.jobTitle(jobTitleId), "jobTitleId", "Unknown job title.");
        requireFound(referenceLookup.country(countryCode), "countryCode", "Unknown country.");
    }

    /** Reads the employee under the row lock, and refuses a caller who saw an older version. */
    private Employee lockedEmployee(final long id, final long version) {
        final Employee employee = employeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        if (employee.version() != version) {
            throw new StaleVersionException(id);
        }
        return employee;
    }

    private static void requireFound(final Optional<?> reference, final String field, final String message) {
        if (reference.isEmpty()) {
            throw new UnknownReferenceException(field, message);
        }
    }

    /** The unique index is the only judge of a duplicate email, so two requests at once cannot both succeed. */
    private Employee saveRejectingDuplicateEmail(final Employee employee) {
        try {
            return employeeRepository.saveAndFlush(employee);
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
