package com.acme.peoplemanagement.employee.api;

import java.time.LocalDate;

/** The salary an employee is paid today, in the currency of that entry. */
public record CurrentSalaryResponse(LocalDate effectiveDate, long amountMinor, String currency) {
}
