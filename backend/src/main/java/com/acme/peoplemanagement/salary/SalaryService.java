package com.acme.peoplemanagement.salary;

import com.acme.peoplemanagement.salary.errors.CurrencyRequiredException;
import com.acme.peoplemanagement.salary.rules.HistoryEntry;
import com.acme.peoplemanagement.salary.rules.IdentifiedEntry;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.NewSalaryEntry;
import com.acme.peoplemanagement.salary.rules.SalaryHistory;
import com.acme.peoplemanagement.salary.rules.SalaryTimelineValidator;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way salary entries are written, so every change is checked against the rules first.
 * Callers pass today, so one request reads the clock once.
 */
@Service
public class SalaryService {

    private final SalaryEntryRepository salaryEntryRepository;
    private final SalaryTimelineValidator validator = new SalaryTimelineValidator();

    SalaryService(final SalaryEntryRepository salaryEntryRepository) {
        this.salaryEntryRepository = salaryEntryRepository;
    }

    /** Joins the caller's transaction, so a broken rule also undoes the employee row. */
    @Transactional
    public void recordFirstEntry(
            final long employeeId, final LocalDate hireDate, final TimelineEntry entry, final LocalDate today) {
        validator.validate(hireDate, List.of(entry), today);
        salaryEntryRepository.save(new SalaryEntry(employeeId, entry));
    }

    @Transactional(readOnly = true)
    public Optional<TimelineEntry> currentSalary(final long employeeId, final LocalDate today) {
        return salaryEntryRepository
                .findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(employeeId, today)
                .map(SalaryEntry::toTimelineEntry);
    }

    /**
     * Adds an entry after checking the resulting timeline. The caller holds the employee row lock, so the
     * timeline read here cannot change before the entry is written.
     */
    @Transactional
    public HistoryEntry addEntry(
            final long employeeId, final LocalDate hireDate, final NewSalaryEntry newEntry, final LocalDate today) {
        final List<IdentifiedEntry> stored = storedEntriesOf(employeeId);
        final TimelineEntry candidate = new TimelineEntry(
                newEntry.effectiveDate(), new Money(newEntry.amountMinor(), newEntry.currencyCode()));
        // The rules read dates only, so they run before the currency is filled in: a date that cannot be
        // used is reported as that, and not as a missing currency.
        validator.validate(hireDate, timelineWith(stored, candidate), today);
        final TimelineEntry added = withResolvedCurrency(candidate, stored);
        final long id = salaryEntryRepository.save(new SalaryEntry(employeeId, added)).id();
        return historyEntryWithId(id, stored, added, today);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(final long employeeId, final LocalDate today) {
        return SalaryHistory.of(storedEntriesOf(employeeId), today);
    }

    private List<IdentifiedEntry> storedEntriesOf(final long employeeId) {
        return salaryEntryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId).stream()
                .map(stored -> new IdentifiedEntry(stored.id(), stored.toTimelineEntry()))
                .toList();
    }

    private static TimelineEntry withResolvedCurrency(
            final TimelineEntry candidate, final List<IdentifiedEntry> stored) {
        if (candidate.salary().currencyCode() != null) {
            return candidate;
        }
        final String currency = currencyBefore(candidate.effectiveDate(), stored);
        return new TimelineEntry(candidate.effectiveDate(), new Money(candidate.salary().amountMinor(), currency));
    }

    /** The currency in force just before a date: that of the latest entry dated before it. */
    private static String currencyBefore(final LocalDate date, final List<IdentifiedEntry> stored) {
        return stored.stream()
                .map(IdentifiedEntry::entry)
                .filter(entry -> entry.effectiveDate().isBefore(date))
                .max(Comparator.comparing(TimelineEntry::effectiveDate))
                .map(entry -> entry.salary().currencyCode())
                .orElseThrow(CurrencyRequiredException::new);
    }

    private static List<TimelineEntry> timelineWith(final List<IdentifiedEntry> stored, final TimelineEntry added) {
        return Stream.concat(stored.stream().map(IdentifiedEntry::entry), Stream.of(added)).toList();
    }

    private static HistoryEntry historyEntryWithId(
            final long id, final List<IdentifiedEntry> stored, final TimelineEntry added, final LocalDate today) {
        final List<IdentifiedEntry> all = Stream.concat(stored.stream(), Stream.of(new IdentifiedEntry(id, added)))
                .toList();
        return SalaryHistory.of(all, today).stream().filter(entry -> entry.id() == id).findFirst().orElseThrow();
    }
}
