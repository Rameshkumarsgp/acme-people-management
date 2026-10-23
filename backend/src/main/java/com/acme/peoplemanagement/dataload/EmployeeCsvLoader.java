package com.acme.peoplemanagement.dataload;

import com.acme.peoplemanagement.dataload.rules.EmployeeCsv;
import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.rules.MajorUnits;
import com.acme.peoplemanagement.dataload.rules.RowRejectedException;
import com.acme.peoplemanagement.employee.EmployeeService;
import com.acme.peoplemanagement.employee.api.CreateEmployeeRequest;
import com.acme.peoplemanagement.employee.api.InitialSalaryRequest;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.reference.CurrencyResponse;
import com.acme.peoplemanagement.reference.DepartmentResponse;
import com.acme.peoplemanagement.reference.JobTitleResponse;
import com.acme.peoplemanagement.reference.ReferenceLookup;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates one employee per row through the service HR uses, so a loaded row obeys the same rules as a typed one.
 * The loader has no transaction of its own: each row commits or rolls back by itself.
 */
@Component
class EmployeeCsvLoader {

    private final EmployeeService employeeService;
    private final ReferenceLookup referenceLookup;
    private final Validator validator;

    EmployeeCsvLoader(
            final EmployeeService employeeService, final ReferenceLookup referenceLookup, final Validator validator) {
        this.employeeService = employeeService;
        this.referenceLookup = referenceLookup;
        this.validator = validator;
    }

    LoadReport load(final Reader source) {
        final List<RejectedRow> rejected = new ArrayList<>();
        int loaded = 0;
        for (final EmployeeCsv.Line line : EmployeeCsv.read(source)) {
            try {
                createFrom(line);
                loaded++;
            } catch (RowRejectedException exception) {
                rejected.add(new RejectedRow(line.rowNumber(), exception.field(), exception.getMessage()));
            }
        }
        return new LoadReport(loaded, rejected);
    }

    private void createFrom(final EmployeeCsv.Line line) {
        final CreateEmployeeRequest request = toRequest(EmployeeRow.parse(line.cells()));
        rejectInvalid(request);
        create(request);
    }

    private CreateEmployeeRequest toRequest(final EmployeeRow row) {
        final CurrencyResponse currency = referenceLookup.currency(row.currency())
                .orElseThrow(() -> new RowRejectedException("currency", "Unknown currency."));
        final long amountMinor = MajorUnits.toMinor(row.salary(), currency.minorUnitDigits());
        return new CreateEmployeeRequest(
                row.fullName(),
                row.email(),
                row.hireDate(),
                departmentId(row.department()),
                jobTitleId(row.jobTitle()),
                row.countryCode(),
                new InitialSalaryRequest(row.salaryEffectiveDate(), amountMinor, row.currency()));
    }

    private int departmentId(final String name) {
        return referenceLookup.departmentByName(name)
                .map(DepartmentResponse::id)
                .orElseThrow(() -> new RowRejectedException("department", "Unknown department."));
    }

    private int jobTitleId(final String name) {
        return referenceLookup.jobTitleByName(name)
                .map(JobTitleResponse::id)
                .orElseThrow(() -> new RowRejectedException("jobTitle", "Unknown job title."));
    }

    private void rejectInvalid(final CreateEmployeeRequest request) {
        validator.validate(request).stream()
                .min(Comparator.comparing((ConstraintViolation<CreateEmployeeRequest> violation) ->
                        violation.getPropertyPath().toString()))
                .ifPresent(violation -> {
                    throw new RowRejectedException(violation.getPropertyPath().toString(), violation.getMessage());
                });
    }

    private void create(final CreateEmployeeRequest request) {
        try {
            employeeService.create(request);
        } catch (EmailAlreadyExistsException exception) {
            throw new RowRejectedException(
                    "email", "EMAIL_ALREADY_EXISTS: an employee with this email already exists.");
        } catch (UnknownReferenceException exception) {
            throw new RowRejectedException(exception.field(), exception.getMessage());
        } catch (SalaryRuleViolation exception) {
            throw new RowRejectedException("salaryEffectiveDate", exception.code() + ": " + exception.getMessage());
        }
    }
}
