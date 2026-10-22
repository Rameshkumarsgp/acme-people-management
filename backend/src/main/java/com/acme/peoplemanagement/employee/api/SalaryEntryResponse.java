package com.acme.peoplemanagement.employee.api;

import java.time.LocalDate;

/** One entry of the salary history, with its derived end date and markers. */
public record SalaryEntryResponse(
        long id,
        LocalDate effectiveDate,
        long amountMinor,
        String currency,
        LocalDate endDate,
        boolean current,
        boolean scheduled) {
}
