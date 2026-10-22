package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the salary history from an employee's entries. Plain Java: no Spring, no database. */
public final class SalaryHistory {

    private SalaryHistory() {
    }

    /** The history, newest first. */
    public static List<HistoryEntry> of(final List<IdentifiedEntry> entries, final LocalDate today) {
        final List<IdentifiedEntry> oldestFirst = entries.stream()
                .sorted(Comparator.comparing((IdentifiedEntry stored) -> stored.entry().effectiveDate()))
                .toList();
        final long currentId = currentEntryId(oldestFirst, today);
        final List<HistoryEntry> history = new ArrayList<>();
        for (int index = 0; index < oldestFirst.size(); index++) {
            final IdentifiedEntry stored = oldestFirst.get(index);
            history.add(new HistoryEntry(stored.id(), stored.entry(), endDateOf(oldestFirst, index),
                    stored.id() == currentId, stored.entry().effectiveDate().isAfter(today)));
        }
        return history.reversed();
    }

    /** The latest entry dated on or before today, or -1 when every entry is still ahead. */
    private static long currentEntryId(final List<IdentifiedEntry> oldestFirst, final LocalDate today) {
        return oldestFirst.stream()
                .filter(stored -> !stored.entry().effectiveDate().isAfter(today))
                .reduce((earlier, later) -> later)
                .map(IdentifiedEntry::id)
                .orElse(-1L);
    }

    /** The last day of the period: the day before the next entry starts, or null for the newest. */
    private static LocalDate endDateOf(final List<IdentifiedEntry> oldestFirst, final int index) {
        return index + 1 < oldestFirst.size()
                ? oldestFirst.get(index + 1).entry().effectiveDate().minusDays(1)
                : null;
    }
}
