package com.acme.peoplemanagement.salary.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalaryTimelineValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

    private final SalaryTimelineValidator validator = new SalaryTimelineValidator();

    @Test
    void acceptsAFirstEntryOnTheHireDate() {
        assertAccepted(employedFrom(HIRE_DATE), List.of(entryOn(HIRE_DATE)));
    }

    @Test
    void rejectsAnEntryDatedBeforeTheHireDate() {
        assertRejectedWith(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE, employedFrom(HIRE_DATE),
                List.of(entryOn(HIRE_DATE.minusDays(1))));
    }

    @Test
    void rejectsAnEarlierEntryEvenWhenALaterOneIsOnTheHireDate() {
        assertRejectedWith(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE, employedFrom(HIRE_DATE),
                List.of(entryOn(HIRE_DATE.minusDays(1)), entryOn(HIRE_DATE)));
    }

    @Test
    void rejectsATimelineWhoseOnlyEntryIsInTheFuture() {
        assertRejectedWith(SalaryRuleCode.NO_CURRENT_SALARY, employedFrom(HIRE_DATE),
                List.of(entryOn(TODAY.plusDays(1))));
    }

    @Test
    void rejectsAnEmptyTimeline() {
        assertRejectedWith(SalaryRuleCode.NO_CURRENT_SALARY, employedFrom(HIRE_DATE), List.of());
    }

    @Test
    void acceptsAnEntryDatedToday() {
        assertAccepted(employedFrom(TODAY), List.of(entryOn(TODAY)));
    }

    @Test
    void acceptsAScheduledRaiseWhenAnEarlierEntryIsAlreadyCurrent() {
        assertAccepted(employedFrom(HIRE_DATE), List.of(entryOn(HIRE_DATE), entryOn(TODAY.plusMonths(3))));
    }

    @Test
    void rejectsAFutureHireDateAndSaysWhy() {
        final LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> validator.validate(employedFrom(tomorrow), List.of(entryOn(tomorrow)), TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.NO_CURRENT_SALARY))
                .hasMessageContaining("hire date");
    }

    @Test
    void rejectsTwoEntriesOnTheSameDate() {
        assertRejectedWith(SalaryRuleCode.DUPLICATE_SALARY_DATE, employedFrom(HIRE_DATE),
                List.of(entryOn(HIRE_DATE), entryOn(HIRE_DATE)));
    }

    @Test
    void acceptsEntriesOnDifferentDates() {
        assertAccepted(employedFrom(HIRE_DATE), List.of(entryOn(HIRE_DATE), entryOn(HIRE_DATE.plusDays(1))));
    }

    @Test
    void namesTheDateThatIsTaken() {
        assertThatThrownBy(() -> validator.validate(employedFrom(HIRE_DATE),
                List.of(entryOn(HIRE_DATE), entryOn(HIRE_DATE)), TODAY))
                .hasMessageContaining("2024-04-01");
    }

    @Test
    void acceptsATerminationDateOnTheHireDate() {
        assertAccepted(new EmploymentPeriod(HIRE_DATE, HIRE_DATE), List.of(entryOn(HIRE_DATE)));
    }

    @Test
    void rejectsATerminationDateBeforeTheHireDate() {
        assertRejectedWith(SalaryRuleCode.TERMINATION_BEFORE_HIRE_DATE,
                new EmploymentPeriod(HIRE_DATE, HIRE_DATE.minusDays(1)), List.of(entryOn(HIRE_DATE)));
    }

    @Test
    void reportsTheTerminationBeforeHireRuleFirstWhenAnEntryBreaksAnotherRuleToo() {
        assertRejectedWith(SalaryRuleCode.TERMINATION_BEFORE_HIRE_DATE,
                new EmploymentPeriod(HIRE_DATE, HIRE_DATE.minusDays(5)), List.of(entryOn(HIRE_DATE.minusDays(1))));
    }

    @Test
    void saysWhyAndNamesBothDatesWhenTheTerminationIsTooEarly() {
        assertThatThrownBy(() -> validator.validate(new EmploymentPeriod(HIRE_DATE, HIRE_DATE.minusDays(1)),
                List.of(entryOn(HIRE_DATE)), TODAY))
                .hasMessage("The termination date, 2024-03-31, cannot be before the hire date, 2024-04-01.");
    }

    @Test
    void acceptsAnEntryDatedOnTheTerminationDate() {
        final LocalDate termination = HIRE_DATE.plusMonths(6);

        assertAccepted(new EmploymentPeriod(HIRE_DATE, termination), List.of(entryOn(HIRE_DATE), entryOn(termination)));
    }

    @Test
    void rejectsAnEntryDatedAfterTheTerminationDate() {
        final LocalDate termination = HIRE_DATE.plusMonths(6);

        assertRejectedWith(SalaryRuleCode.SALARY_AFTER_TERMINATION, new EmploymentPeriod(HIRE_DATE, termination),
                List.of(entryOn(HIRE_DATE), entryOn(termination.plusDays(1))));
    }

    @Test
    void acceptsATerminationDateInTheFuture() {
        assertAccepted(new EmploymentPeriod(HIRE_DATE, TODAY.plusMonths(1)), List.of(entryOn(HIRE_DATE)));
    }

    @Test
    void rejectsAScheduledRaiseThatFallsAfterAFutureTerminationDate() {
        assertRejectedWith(SalaryRuleCode.SALARY_AFTER_TERMINATION,
                new EmploymentPeriod(HIRE_DATE, TODAY.plusDays(10)),
                List.of(entryOn(HIRE_DATE), entryOn(TODAY.plusDays(20))));
    }

    @Test
    void saysWhyAndNamesTheTerminationDateAnEntryFallsAfter() {
        final LocalDate termination = HIRE_DATE.plusMonths(6);

        assertThatThrownBy(() -> validator.validate(new EmploymentPeriod(HIRE_DATE, termination),
                List.of(entryOn(HIRE_DATE), entryOn(termination.plusDays(1))), TODAY))
                .hasMessage("A salary cannot be effective after the termination date, 2024-10-01.");
    }

    @Test
    void acceptsAnEmployeeWithNoTerminationDate() {
        assertAccepted(new EmploymentPeriod(HIRE_DATE, null), List.of(entryOn(HIRE_DATE)));
    }

    private void assertAccepted(final EmploymentPeriod period, final List<TimelineEntry> timeline) {
        assertThatCode(() -> validator.validate(period, timeline, TODAY)).doesNotThrowAnyException();
    }

    private void assertRejectedWith(
            final SalaryRuleCode code, final EmploymentPeriod period, final List<TimelineEntry> timeline) {
        assertThatThrownBy(() -> validator.validate(period, timeline, TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(code));
    }

    private static EmploymentPeriod employedFrom(final LocalDate hireDate) {
        return new EmploymentPeriod(hireDate, null);
    }

    private static TimelineEntry entryOn(final LocalDate effectiveDate) {
        return new TimelineEntry(effectiveDate, new Money(125_000_000L, "INR"));
    }
}
