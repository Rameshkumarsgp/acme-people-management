package com.acme.peoplemanagement.employee;

import com.acme.peoplemanagement.employee.api.SalaryChangeResponse;
import com.acme.peoplemanagement.employee.api.SalaryEntryRequest;
import com.acme.peoplemanagement.employee.api.SalaryEntryResponse;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.reference.ReferenceLookup;
import com.acme.peoplemanagement.salary.SalaryService;
import com.acme.peoplemanagement.salary.rules.EmploymentPeriod;
import com.acme.peoplemanagement.salary.rules.HistoryEntry;
import com.acme.peoplemanagement.salary.rules.NewSalaryEntry;
import com.acme.peoplemanagement.salary.rules.SalaryChange;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The salary use cases of an employee. It owns what belongs to the employee (the row lock and the hire
 * date) and leaves the salary rules to the salary service, which never needs to know about employees.
 */
@Service
public class EmployeeSalaryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;
    private final ReferenceLookup referenceLookup;
    private final Clock clock;

    EmployeeSalaryService(
            final EmployeeRepository employeeRepository,
            final SalaryService salaryService,
            final ReferenceLookup referenceLookup,
            final Clock clock) {
        this.employeeRepository = employeeRepository;
        this.salaryService = salaryService;
        this.referenceLookup = referenceLookup;
        this.clock = clock;
    }

    /** Locks the employee first, so two adds for the same employee are checked one after the other. */
    @Transactional
    public SalaryEntryResponse add(final long employeeId, final SalaryEntryRequest request) {
        final Employee employee = lockedEmployee(employeeId);
        verifyCurrencyExists(request.currency());
        final NewSalaryEntry newEntry =
                new NewSalaryEntry(request.effectiveDate(), request.amountMinor(), request.currency());
        return toResponse(salaryService.addEntry(employeeId, periodOf(employee), newEntry, LocalDate.now(clock)));
    }

    /** Locks the employee first, so the timeline cannot change between the check and the change. */
    @Transactional
    public SalaryEntryResponse edit(final long employeeId, final long entryId, final SalaryEntryRequest request) {
        final Employee employee = lockedEmployee(employeeId);
        verifyCurrencyExists(request.currency());
        final NewSalaryEntry changed =
                new NewSalaryEntry(request.effectiveDate(), request.amountMinor(), request.currency());
        return toResponse(salaryService.editEntry(
                employeeId, periodOf(employee), entryId, changed, LocalDate.now(clock)));
    }

    @Transactional
    public void delete(final long employeeId, final long entryId) {
        final Employee employee = lockedEmployee(employeeId);
        salaryService.deleteEntry(employeeId, periodOf(employee), entryId, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<SalaryChangeResponse> changes(final long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return salaryService.changes(employeeId).stream().map(EmployeeSalaryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SalaryEntryResponse> list(final long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return salaryService.history(employeeId, LocalDate.now(clock)).stream().map(this::toResponse).toList();
    }

    private Employee lockedEmployee(final long employeeId) {
        return employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private static SalaryChangeResponse toResponse(final SalaryChange change) {
        return new SalaryChangeResponse(change.id(), change.entryId(), change.action(), change.changedAt(),
                valuesOf(change.before()), valuesOf(change.after()));
    }

    private static SalaryChangeResponse.Values valuesOf(final TimelineEntry entry) {
        return entry == null ? null : new SalaryChangeResponse.Values(
                entry.effectiveDate(), entry.salary().amountMinor(), entry.salary().currencyCode());
    }

    private static EmploymentPeriod periodOf(final Employee employee) {
        return new EmploymentPeriod(employee.hireDate(), employee.terminationDate());
    }

    private void verifyCurrencyExists(final String currency) {
        if (currency != null && referenceLookup.currency(currency).isEmpty()) {
            throw new UnknownReferenceException("currency", "Unknown currency.");
        }
    }

    private SalaryEntryResponse toResponse(final HistoryEntry history) {
        return new SalaryEntryResponse(history.id(), history.entry().effectiveDate(),
                history.entry().salary().amountMinor(), history.entry().salary().currencyCode(),
                history.endDate(), history.current(), history.scheduled());
    }
}
