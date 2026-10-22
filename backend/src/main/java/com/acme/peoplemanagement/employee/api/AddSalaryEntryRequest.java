package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.salary.rules.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** A salary entry to add. The currency is optional: it defaults to that of the entry before the date. */
public record AddSalaryEntryRequest(
        @NotNull LocalDate effectiveDate,
        @NotNull @Min(1) @Max(Money.MAX_AMOUNT_MINOR) Long amountMinor,
        @Pattern(regexp = "[A-Z]{3}") String currency) {
}
