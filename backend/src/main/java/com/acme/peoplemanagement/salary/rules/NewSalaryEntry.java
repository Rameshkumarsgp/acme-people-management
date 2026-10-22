package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;

/** A salary entry to add. The currency may be null: it then defaults to that of the entry before the date. */
public record NewSalaryEntry(LocalDate effectiveDate, long amountMinor, String currencyCode) {
}
