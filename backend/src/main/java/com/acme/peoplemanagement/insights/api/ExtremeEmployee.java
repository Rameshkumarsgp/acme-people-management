package com.acme.peoplemanagement.insights.api;

import java.time.LocalDate;

/** One of the highest or lowest paid employees, with the salary entry that is current today. */
public record ExtremeEmployee(
        long id,
        String fullName,
        String department,
        String jobTitle,
        String country,
        long amountMinor,
        LocalDate effectiveDate) {
}
