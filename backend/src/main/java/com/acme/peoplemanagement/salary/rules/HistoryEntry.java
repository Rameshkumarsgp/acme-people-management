package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;

/**
 * An entry as shown in the history. {@code endDate} is the last day of its period, or null for the
 * newest entry. {@code current} marks the salary paid today, and {@code scheduled} a start still ahead.
 */
public record HistoryEntry(long id, TimelineEntry entry, LocalDate endDate, boolean current, boolean scheduled) {
}
