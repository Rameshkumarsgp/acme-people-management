package com.acme.peoplemanagement.salary.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalaryHistoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void aSingleEntryIsCurrentAndHasNoEndDate() {
        final List<HistoryEntry> history = SalaryHistory.of(List.of(entry(1, "2024-04-01")), TODAY);

        assertThat(history).singleElement().satisfies(first -> {
            assertThat(first.endDate()).isNull();
            assertThat(first.current()).isTrue();
            assertThat(first.scheduled()).isFalse();
        });
    }

    @Test
    void listsNewestFirstWithTheLastDayOfEachPeriodAsItsEndDate() {
        final List<HistoryEntry> history = SalaryHistory.of(
                List.of(entry(1, "2024-04-01"), entry(2, "2025-04-01"), entry(3, "2026-04-01")), TODAY);

        assertThat(history).extracting(HistoryEntry::id).containsExactly(3L, 2L, 1L);
        assertThat(history).extracting(HistoryEntry::endDate)
                .containsExactly(null, LocalDate.of(2026, 3, 31), LocalDate.of(2025, 3, 31));
        assertThat(history).extracting(HistoryEntry::current).containsExactly(true, false, false);
    }

    @Test
    void sortsByDateWhateverOrderTheEntriesArriveIn() {
        final List<HistoryEntry> history = SalaryHistory.of(
                List.of(entry(2, "2025-04-01"), entry(3, "2026-04-01"), entry(1, "2024-04-01")), TODAY);

        assertThat(history).extracting(HistoryEntry::id).containsExactly(3L, 2L, 1L);
    }

    @Test
    void aFutureEntryIsScheduledAndTheEarlierOneStaysCurrent() {
        final List<HistoryEntry> history = SalaryHistory.of(
                List.of(entry(1, "2024-04-01"), entry(2, "2027-01-01")), TODAY);

        assertThat(history).extracting(HistoryEntry::scheduled).containsExactly(true, false);
        assertThat(history).extracting(HistoryEntry::current).containsExactly(false, true);
        assertThat(history.get(1).endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void anEntryDatedTodayIsCurrentAndOneDatedTomorrowIsScheduled() {
        final List<HistoryEntry> today = SalaryHistory.of(List.of(entry(1, "2026-10-19")), TODAY);
        final List<HistoryEntry> tomorrow = SalaryHistory.of(
                List.of(entry(1, "2024-04-01"), entry(2, "2026-10-20")), TODAY);

        assertThat(today.getFirst().current()).isTrue();
        assertThat(today.getFirst().scheduled()).isFalse();
        assertThat(tomorrow.getFirst().scheduled()).isTrue();
        assertThat(tomorrow.getLast().current()).isTrue();
    }

    @Test
    void theEndDateCrossesMonthAndLeapDayBoundaries() {
        final List<HistoryEntry> history = SalaryHistory.of(
                List.of(entry(1, "2024-01-01"), entry(2, "2024-03-01"), entry(3, "2025-03-01")), TODAY);

        assertThat(history).extracting(HistoryEntry::endDate)
                .containsExactly(null, LocalDate.of(2025, 2, 28), LocalDate.of(2024, 2, 29));
    }

    private static IdentifiedEntry entry(final long id, final String effectiveDate) {
        return new IdentifiedEntry(id, new TimelineEntry(LocalDate.parse(effectiveDate), new Money(100L, "INR")));
    }
}
