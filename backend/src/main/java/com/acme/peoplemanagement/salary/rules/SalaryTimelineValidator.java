package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules a salary timeline must satisfy. Plain Java with no Spring or database, so the tests run in
 * milliseconds. Later slices add one rule and one test each.
 */
public final class SalaryTimelineValidator {

    private static final String NO_CURRENT_SALARY_DETAIL = "There must be a salary effective on or before today, "
            + "so a new employee's hire date and first salary date cannot be in the future.";

    public void validate(final EmploymentPeriod period, final List<TimelineEntry> timeline, final LocalDate today) {
        rejectTerminationBeforeHireDate(period);
        rejectEntriesBeforeHireDate(period.hireDate(), timeline);
        rejectDuplicateDates(timeline);
        requireCurrentSalary(timeline, today);
        rejectEntriesAfterTermination(period.terminationDate(), timeline);
    }

    private static void rejectTerminationBeforeHireDate(final EmploymentPeriod period) {
        final LocalDate termination = period.terminationDate();
        if (termination != null && termination.isBefore(period.hireDate())) {
            throw new SalaryRuleViolation(SalaryRuleCode.TERMINATION_BEFORE_HIRE_DATE,
                    "The termination date, " + termination + ", cannot be before the hire date, "
                            + period.hireDate() + ".");
        }
    }

    private static void rejectEntriesAfterTermination(final LocalDate termination, final List<TimelineEntry> timeline) {
        if (termination == null) {
            return;
        }
        final boolean anyAfterTermination = timeline.stream()
                .anyMatch(entry -> entry.effectiveDate().isAfter(termination));
        if (anyAfterTermination) {
            throw new SalaryRuleViolation(SalaryRuleCode.SALARY_AFTER_TERMINATION,
                    "A salary cannot be effective after the termination date, " + termination + ".");
        }
    }

    private static void rejectDuplicateDates(final List<TimelineEntry> timeline) {
        final Set<LocalDate> seen = new HashSet<>();
        for (final TimelineEntry entry : timeline) {
            if (!seen.add(entry.effectiveDate())) {
                throw new SalaryRuleViolation(SalaryRuleCode.DUPLICATE_SALARY_DATE,
                        "There is already a salary effective on " + entry.effectiveDate() + ".");
            }
        }
    }

    private static void requireCurrentSalary(final List<TimelineEntry> timeline, final LocalDate today) {
        final boolean hasCurrentSalary = timeline.stream()
                .anyMatch(entry -> !entry.effectiveDate().isAfter(today));
        if (!hasCurrentSalary) {
            throw new SalaryRuleViolation(SalaryRuleCode.NO_CURRENT_SALARY, NO_CURRENT_SALARY_DETAIL);
        }
    }

    private static void rejectEntriesBeforeHireDate(final LocalDate hireDate, final List<TimelineEntry> timeline) {
        final boolean anyBeforeHireDate = timeline.stream()
                .anyMatch(entry -> entry.effectiveDate().isBefore(hireDate));
        if (anyBeforeHireDate) {
            throw new SalaryRuleViolation(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE,
                    "A salary cannot be effective before the hire date.");
        }
    }
}
