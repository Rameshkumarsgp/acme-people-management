package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.salary.rules.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** The first salary of a new employee. The effective date is optional and defaults to the hire date. */
public record InitialSalaryRequest(
        LocalDate effectiveDate,
        @NotNull @Min(1) @Max(Money.MAX_AMOUNT_MINOR) Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
