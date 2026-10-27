package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.salary.rules.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * A salary entry to add or to change to. The currency is optional: an added entry takes that of the entry before its
 * date, and an edited one keeps its own.
 */
public record SalaryEntryRequest(
        @NotNull LocalDate effectiveDate,
        @NotNull @Min(1) @Max(Money.MAX_AMOUNT_MINOR) Long amountMinor,
        @Pattern(regexp = "[A-Z]{3}") String currency) {
}
