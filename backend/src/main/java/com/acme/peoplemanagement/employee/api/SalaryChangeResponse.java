package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.salary.rules.ChangeAction;
import java.time.Instant;
import java.time.LocalDate;

/** One line of an employee's change log. {@code before} is empty for an add, and {@code after} for a delete. */
public record SalaryChangeResponse(
        long id, long entryId, ChangeAction action, Instant changedAt, Values before, Values after) {

    /** A salary entry's date, amount and currency at one moment. */
    public record Values(LocalDate effectiveDate, long amountMinor, String currency) {
    }
}
