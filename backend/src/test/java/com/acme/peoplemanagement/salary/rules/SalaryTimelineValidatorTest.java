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
        assertAccepted(HIRE_DATE, List.of(entryOn(HIRE_DATE)));
    }

    @Test
    void rejectsAnEntryDatedBeforeTheHireDate() {
        assertRejectedWith(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE, HIRE_DATE, List.of(entryOn(HIRE_DATE.minusDays(1))));
    }

    @Test
    void rejectsAnEarlierEntryEvenWhenALaterOneIsOnTheHireDate() {
        assertRejectedWith(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE, HIRE_DATE,
                List.of(entryOn(HIRE_DATE.minusDays(1)), entryOn(HIRE_DATE)));
    }

    @Test
    void rejectsATimelineWhoseOnlyEntryIsInTheFuture() {
        assertRejectedWith(SalaryRuleCode.NO_CURRENT_SALARY, HIRE_DATE, List.of(entryOn(TODAY.plusDays(1))));
    }

    @Test
    void rejectsAnEmptyTimeline() {
        assertRejectedWith(SalaryRuleCode.NO_CURRENT_SALARY, HIRE_DATE, List.of());
    }

    @Test
    void acceptsAnEntryDatedToday() {
        assertAccepted(TODAY, List.of(entryOn(TODAY)));
    }

    @Test
    void acceptsAScheduledRaiseWhenAnEarlierEntryIsAlreadyCurrent() {
        assertAccepted(HIRE_DATE, List.of(entryOn(HIRE_DATE), entryOn(TODAY.plusMonths(3))));
    }

    @Test
    void rejectsAFutureHireDateAndSaysWhy() {
        final LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> validator.validate(tomorrow, List.of(entryOn(tomorrow)), TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.NO_CURRENT_SALARY))
                .hasMessageContaining("hire date");
    }

    @Test
    void rejectsTwoEntriesOnTheSameDate() {
        assertRejectedWith(SalaryRuleCode.DUPLICATE_SALARY_DATE, HIRE_DATE,
                List.of(entryOn(HIRE_DATE), entryOn(HIRE_DATE)));
    }

    @Test
    void acceptsEntriesOnDifferentDates() {
        assertAccepted(HIRE_DATE, List.of(entryOn(HIRE_DATE), entryOn(HIRE_DATE.plusDays(1))));
    }

    @Test
    void namesTheDateThatIsTaken() {
        assertThatThrownBy(() -> validator.validate(HIRE_DATE,
                List.of(entryOn(HIRE_DATE), entryOn(HIRE_DATE)), TODAY))
                .hasMessageContaining("2024-04-01");
    }

    private void assertAccepted(final LocalDate hireDate, final List<TimelineEntry> timeline) {
        assertThatCode(() -> validator.validate(hireDate, timeline, TODAY)).doesNotThrowAnyException();
    }

    private void assertRejectedWith(
            final SalaryRuleCode code, final LocalDate hireDate, final List<TimelineEntry> timeline) {
        assertThatThrownBy(() -> validator.validate(hireDate, timeline, TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(code));
    }

    private static TimelineEntry entryOn(final LocalDate effectiveDate) {
        return new TimelineEntry(effectiveDate, new Money(125_000_000L, "INR"));
    }
}
