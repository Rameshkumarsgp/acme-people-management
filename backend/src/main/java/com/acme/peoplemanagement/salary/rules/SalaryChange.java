package com.acme.peoplemanagement.salary.rules;

import java.time.Instant;

/**
 * One line of the change log. {@code before} is null for an added entry and {@code after} for a deleted one.
 */
public record SalaryChange(
    long id, long entryId, ChangeAction action, TimelineEntry before, TimelineEntry after,
    Instant changedAt) {
}
