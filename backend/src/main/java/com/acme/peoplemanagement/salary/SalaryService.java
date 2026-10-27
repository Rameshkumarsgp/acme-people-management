package com.acme.peoplemanagement.salary;

import com.acme.peoplemanagement.salary.errors.CurrencyRequiredException;
import com.acme.peoplemanagement.salary.errors.SalaryEntryNotFoundException;
import com.acme.peoplemanagement.salary.rules.EmploymentPeriod;
import com.acme.peoplemanagement.salary.rules.HistoryEntry;
import com.acme.peoplemanagement.salary.rules.IdentifiedEntry;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.NewSalaryEntry;
import com.acme.peoplemanagement.salary.rules.SalaryChange;
import com.acme.peoplemanagement.salary.rules.SalaryHistory;
import com.acme.peoplemanagement.salary.rules.SalaryTimelineValidator;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import java.time.Clock;
import java.time.Instant;
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
    private final SalaryEntryChangeRepository changeRepository;
    private final Clock clock;
    private final SalaryTimelineValidator validator = new SalaryTimelineValidator();

    SalaryService(
            final SalaryEntryRepository salaryEntryRepository,
            final SalaryEntryChangeRepository changeRepository,
            final Clock clock) {
        this.salaryEntryRepository = salaryEntryRepository;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    /** Checks a first entry before any row exists, so a rule is reported before the database's own checks. */
    public void checkFirstEntry(final EmploymentPeriod period, final TimelineEntry entry, final LocalDate today) {
        validator.validate(period, List.of(entry), today);
    }

    /** Joins the caller's transaction, so a broken rule also undoes the employee row. */
    @Transactional
    public void recordFirstEntry(
            final long employeeId, final EmploymentPeriod period, final TimelineEntry entry, final LocalDate today) {
        checkFirstEntry(period, entry, today);
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
            final long employeeId,
            final EmploymentPeriod period,
            final NewSalaryEntry newEntry,
            final LocalDate today) {
        final List<IdentifiedEntry> stored = storedEntriesOf(employeeId);
        final TimelineEntry candidate = new TimelineEntry(
                newEntry.effectiveDate(), new Money(newEntry.amountMinor(), newEntry.currencyCode()));
        // The rules read dates only, so they run before the currency is filled in: a date that cannot be
        // used is reported as that, and not as a missing currency.
        validator.validate(period, timelineWith(stored, candidate), today);
        final TimelineEntry added = withResolvedCurrency(candidate, stored);
        final long id = salaryEntryRepository.save(new SalaryEntry(employeeId, added)).id();
        logChange(employeeId, id, null, added);
        return historyEntryWithId(id, stored, added, today);
    }

    /**
     * Replaces an entry's date, amount and currency after checking the resulting timeline. An omitted currency keeps
     * the entry's own. An edit that changes nothing writes no change row. The caller holds the employee row lock.
     */
    @Transactional
    public HistoryEntry editEntry(
            final long employeeId,
            final EmploymentPeriod period,
            final long entryId,
            final NewSalaryEntry changed,
            final LocalDate today) {
        final SalaryEntry entry = entryOf(employeeId, entryId);
        final TimelineEntry before = entry.toTimelineEntry();
        final String currency =
                changed.currencyCode() == null ? before.salary().currencyCode() : changed.currencyCode();
        final TimelineEntry after =
                new TimelineEntry(changed.effectiveDate(), new Money(changed.amountMinor(), currency));
        final List<IdentifiedEntry> others = storedEntriesOf(employeeId).stream()
                .filter(stored -> stored.id() != entryId).toList();
        validator.validate(period, timelineWith(others, after), today);
        if (!after.equals(before)) {
            entry.change(after);
            logChange(employeeId, entryId, before, after);
        }
        return historyEntryWithId(entryId, others, after, today);
    }

    /** Deletes an entry unless that would break a rule, such as leaving the employee with no current salary. */
    @Transactional
    public void deleteEntry(
            final long employeeId, final EmploymentPeriod period, final long entryId, final LocalDate today) {
        final SalaryEntry entry = entryOf(employeeId, entryId);
        final List<TimelineEntry> remaining = storedEntriesOf(employeeId).stream()
                .filter(stored -> stored.id() != entryId).map(IdentifiedEntry::entry).toList();
        validator.validate(period, remaining, today);
        logChange(employeeId, entryId, entry.toTimelineEntry(), null);
        salaryEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<SalaryChange> changes(final long employeeId) {
        return changeRepository.findByEmployeeIdOrderByChangedAtDescIdDesc(employeeId).stream()
                .map(SalaryEntryChange::toChange).toList();
    }

    private SalaryEntry entryOf(final long employeeId, final long entryId) {
        return salaryEntryRepository.findByIdAndEmployeeId(entryId, employeeId)
                .orElseThrow(() -> new SalaryEntryNotFoundException(entryId));
    }

    private void logChange(
            final long employeeId, final long entryId, final TimelineEntry before, final TimelineEntry after) {
        changeRepository.save(new SalaryEntryChange(employeeId, entryId, before, after, Instant.now(clock)));
    }

    /** Checks the stored timeline against an employment period, for a change to the hire or termination date. */
    @Transactional(readOnly = true)
    public void revalidate(final long employeeId, final EmploymentPeriod period, final LocalDate today) {
        validator.validate(period, storedEntriesOf(employeeId).stream().map(IdentifiedEntry::entry).toList(), today);
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
