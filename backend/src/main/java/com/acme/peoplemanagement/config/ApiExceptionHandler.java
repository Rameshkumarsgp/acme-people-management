package com.acme.peoplemanagement.config;

import com.acme.peoplemanagement.directory.errors.InvalidDirectoryRequestException;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.StaleVersionException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.salary.errors.CurrencyRequiredException;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into application/problem+json. It maps an exception type to a status and nothing
 * more: which situations are errors is decided by the services and the salary rules. Errors that
 * Spring MVC raises itself (a malformed body, an id that is not a number) are handled by the base class.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    @ExceptionHandler(SalaryRuleViolation.class)
    ProblemDetail salaryRuleBroken(final SalaryRuleViolation violation) {
        return problemWithCode(UNPROCESSABLE, violation.getMessage(), violation.code().name());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ProblemDetail emailAlreadyExists(final EmailAlreadyExistsException exception) {
        return problemWithCode(HttpStatus.CONFLICT, exception.getMessage(), "EMAIL_ALREADY_EXISTS");
    }

    @ExceptionHandler(StaleVersionException.class)
    ProblemDetail staleVersion(final StaleVersionException exception) {
        return problemWithCode(HttpStatus.CONFLICT, exception.getMessage(), "VERSION_CONFLICT");
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    ProblemDetail employeeNotFound(final EmployeeNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnknownReferenceException.class)
    ProblemDetail unknownReference(final UnknownReferenceException exception) {
        return invalidRequest(List.of(new FieldProblem(exception.field(), exception.getMessage())));
    }

    @ExceptionHandler(CurrencyRequiredException.class)
    ProblemDetail currencyRequired(final CurrencyRequiredException exception) {
        return invalidRequest(List.of(new FieldProblem(exception.field(), exception.getMessage())));
    }

    @ExceptionHandler(InvalidDirectoryRequestException.class)
    ProblemDetail invalidDirectoryRequest(final InvalidDirectoryRequestException exception) {
        return invalidRequest(List.of(new FieldProblem(exception.field(), exception.getMessage())));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final List<FieldProblem> problems = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid")))
                .toList();
        return handleExceptionInternal(exception, invalidRequest(problems), headers, status, request);
    }

    private static ProblemDetail invalidRequest(final List<FieldProblem> problems) {
        final ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid.");
        problem.setProperty("errors", problems);
        return problem;
    }

    private static ProblemDetail problemWithCode(final HttpStatusCode status, final String detail, final String code) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    /** One entry of the {@code errors} list in a 400 response. */
    record FieldProblem(String field, String message) {
    }
}
