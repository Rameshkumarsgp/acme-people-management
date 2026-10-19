package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;

/** One salary on an employee's timeline: what they are paid from the effective date until the next entry. */
public record TimelineEntry(LocalDate effectiveDate, Money salary) {
}
