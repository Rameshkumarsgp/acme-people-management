# Slice 3: salary history and adding a salary entry, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (the owner chose to run it one task at a time) or superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax. Also follow the project skill `.claude/skills/backend-slice/SKILL.md`.

**Goal:** HR sees an employee's salary history on the detail page and adds a salary entry (a raise, possibly future-dated or in another currency), through the API and the Angular detail page.

**Architecture:** `salary/rules` gains one validator rule and a pure `SalaryHistory`. `SalaryService` gains `addEntry` and `history`. A new `EmployeeSalaryService` in `employee` takes the employee row lock and calls `SalaryService`, so `salary` never imports `employee`. A new controller exposes `GET` and `POST /api/employees/{id}/salary-entries`. The Angular detail page gets a history table and an "Add salary" dialog.

**Tech Stack:** Java 25, Spring Boot 4.1, JPA, PostgreSQL 17 (Testcontainers), JUnit 5, AssertJ, MockMvc. Angular 22 with Angular Material (table, dialog), Vitest in jsdom.

**Spec:** [2026-10-22-slice-3-salary-history-design.md](../specs/2026-10-22-slice-3-salary-history-design.md). Slice 2 plan, for conventions: [2026-10-19-slice-2-employee-create-read.md](2026-10-19-slice-2-employee-create-read.md).

## How this plan is run

- One task at a time. After each task, **stage the exact files and stop** with a suggested `type(scope): subject` message. **The owner makes every commit.** Never `git add .`. No worktree.
- Every task is test first and ends with breaking the code to prove each new test, then the **full** gates (`make test-backend` and `make test-integration` for backend tasks, the frontend gates for frontend tasks). A mutation must compile (`-Werror` and Error Prone reject an unused field or private method), and its target text must be asserted to exist before it is trusted.
- The Docker daemon must be running for integration tests.

## Deviations from the spec, found while reading the code

1. **`SalaryService.addEntry` takes a `NewSalaryEntry` record** (effective date, amount, nullable currency) instead of separate arguments. The spec's call had six parameters, and Checkstyle allows five.
2. **`salary-amount.validator.ts` moves from `features/employee-form/` to `shared/money/`** (Task 9). The new dialog lives in `features/employee-detail/`, and features must not import each other. The move is a pure refactor with its tests.
3. **The controller is separate:** a new `SalaryEntryController` in `employee/api`, not extra methods on `EmployeeController`, so each controller keeps one job.
4. **Rule order in the validator:** hire date, then duplicate dates, then current salary.

## Global Constraints

- Money is an integer in minor units, never a float. The cap is `9007199254740991` (`Money.MAX_AMOUNT_MINOR`). A fractional number where a whole number is expected is a `400` (`accept-float-as-int=false`).
- `endDate` is the **last day of the period**, the day before the next entry starts, and `null` for the newest entry. `current` is the latest entry on or before today. `scheduled` is any entry after today.
- Adding an entry locks the employee row (`PESSIMISTIC_WRITE`), re-reads the committed timeline under the lock, validates the resulting timeline, then writes. It does **not** bump `Employee.version`. No change-log row is written in this slice (slice 7).
- The currency defaults to the entry immediately before the new date. If none precedes it, the currency is required (`400`, field `currency`).
- Errors are `application/problem+json`: `400` invalid shape, a missing or unknown currency (with `errors: [{ field, message }]`), `404` unknown employee, `422` a broken rule with `code` (`DUPLICATE_SALARY_DATE`, `SALARY_BEFORE_HIRE_DATE`).
- `salary` never imports `employee`. `employee` reaches `salary` only through public classes. `salary` takes the hire date and today as parameters.
- Backend gates: `-Werror`, Error Prone, Checkstyle (120 columns, methods up to 25 non-empty lines, up to 5 parameters, no single-letter names), SpotBugs on tests too (no `\n` inside a text block passed to `.formatted()`).
- Frontend gates: Prettier, ESLint, `tsc`, Knip (an unused export fails), Vitest. Standalone components, signals, `inject()`, no NgRx. Async specs poll with `vi.waitFor`, not `whenStable`. A request that fails cancels its `forkJoin` siblings, so answer the others first.
- Before suggesting a commit, review the change against the engineering principles in the root `CLAUDE.md`.

## Review Focus

Behaviors the spec implies but a task could leave untested. Each has a test in the task named in brackets.

1. **An entry dated today is current, and one dated tomorrow is scheduled and leaves the earlier entry current.** [Task 2, Task 3]
2. **Two simultaneous adds on the same date leave exactly one entry**, one success and one `DUPLICATE_SALARY_DATE`, asserted on the final database state. [Task 4]
3. **A backdated entry before the first one, with no currency, answers a clear `400` for `currency`**, not a `500`. [Task 3, Task 5]
4. **A fractional, zero or over-cap amount and a lowercase currency are `400`**, and the dialog rejects decimals for a currency such as JPY. [Task 5, Task 9]
5. **Adding a raise changes its neighbours' derived values** (the earlier entry's end date and `current` flag), and the page shows that after re-reading. [Task 6, Task 10]

---

## File structure

Backend, under `backend/src/main/java/com/acme/peoplemanagement/`:

| File | Responsibility |
|---|---|
| `salary/rules/SalaryRuleCode.java` | gains `DUPLICATE_SALARY_DATE` |
| `salary/rules/SalaryTimelineValidator.java` | gains the duplicate-date check |
| `salary/rules/NewSalaryEntry.java` | request value: date, amount, nullable currency |
| `salary/rules/IdentifiedEntry.java` | a stored entry with its id |
| `salary/rules/HistoryEntry.java` | an entry with its derived end date and flags |
| `salary/rules/SalaryHistory.java` | pure: builds the history list |
| `salary/errors/CurrencyRequiredException.java` | no earlier entry to take a currency from |
| `salary/SalaryEntry.java`, `SalaryEntryRepository.java` | gain `id()` and a list query |
| `salary/SalaryService.java` | gains `addEntry` and `history` |
| `employee/EmployeeRepository.java` | gains the locking read |
| `employee/EmployeeSalaryService.java` | lock, currency check, call `SalaryService`, map to responses |
| `employee/api/AddSalaryEntryRequest.java`, `SalaryEntryResponse.java` | request and response records |
| `employee/api/SalaryEntryController.java` | the two endpoints |
| `config/ApiExceptionHandler.java` | maps `CurrencyRequiredException` to a `400` with the field |

Frontend, under `frontend/src/app/`:

| File | Responsibility |
|---|---|
| `shared/money/salary-amount.validator.ts` (+ spec) | moved here from `employee-form` |
| `core/employees/employee-api.ts` | gains `listSalaryEntries`, `addSalaryEntry` and their types |
| `features/employee-detail/salary-history/salary-history.{ts,html,scss}` | the table |
| `features/employee-detail/add-salary-dialog/add-salary-dialog.{ts,html,scss}` | the dialog |
| `features/employee-detail/employee-detail.{ts,html}` | shows the history, opens the dialog, re-reads after an add |

---

### Task 1: The duplicate-date rule

**Files:**
- Modify: `backend/src/main/java/com/acme/peoplemanagement/salary/rules/SalaryRuleCode.java`
- Modify: `backend/src/main/java/com/acme/peoplemanagement/salary/rules/SalaryTimelineValidator.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/salary/rules/SalaryTimelineValidatorTest.java`

**Interfaces:**
- Produces: `SalaryRuleCode.DUPLICATE_SALARY_DATE`. `validate(hireDate, timeline, today)` also throws it when two entries share an effective date, after the hire date rule and before the current salary rule.

- [ ] **Step 1: Write the failing tests**

Add to `SalaryTimelineValidatorTest`, above the helper methods:

```java
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
```

- [ ] **Step 2: Run and watch it fail**

Run: `cd backend && ./gradlew test --tests '*SalaryTimelineValidatorTest' --console=plain --warning-mode=none`
Expected: FAIL to compile (`DUPLICATE_SALARY_DATE` does not exist). Add only the enum value to `SalaryRuleCode`:

```java
public enum SalaryRuleCode {
    SALARY_BEFORE_HIRE_DATE,
    DUPLICATE_SALARY_DATE,
    NO_CURRENT_SALARY
}
```

Run again. Expected: `rejectsTwoEntriesOnTheSameDate` and `namesTheDateThatIsTaken` FAIL (no exception thrown), and `acceptsEntriesOnDifferentDates` passes.

- [ ] **Step 3: Implement the rule**

In `SalaryTimelineValidator.java` add the imports `java.util.HashSet` and `java.util.Set`, call the check in `validate`, and add the helper:

```java
    public void validate(final LocalDate hireDate, final List<TimelineEntry> timeline, final LocalDate today) {
        rejectEntriesBeforeHireDate(hireDate, timeline);
        rejectDuplicateDates(timeline);
        requireCurrentSalary(timeline, today);
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
```

- [ ] **Step 4: Run and watch it pass, then prove the tests**

Run the same command. Expected: all tests PASS.
Prove: (a) change `!seen.add(entry.effectiveDate())` to `seen.add(entry.effectiveDate())` (inverts the check): expected `rejectsTwoEntriesOnTheSameDate` and `acceptsEntriesOnDifferentDates` FAIL. Restore. (b) change the message to `"There is already a salary."`: expected `namesTheDateThatIsTaken` FAILS. Restore. Confirm the file is identical to the good copy.

- [ ] **Step 5: Gates and stage**

Run: `make test-backend`. Expected: exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/salary/rules backend/src/test/java/com/acme/peoplemanagement/salary/rules
```

Suggested message: `feat(api): reject a second salary entry on the same date`

---

### Task 2: The salary history, derived from the entries

**Files:**
- Create: `salary/rules/IdentifiedEntry.java`, `salary/rules/HistoryEntry.java`, `salary/rules/SalaryHistory.java` (under `backend/src/main/java/com/acme/peoplemanagement/`)
- Test: `backend/src/test/java/com/acme/peoplemanagement/salary/rules/SalaryHistoryTest.java`

**Interfaces:**
- Produces: `IdentifiedEntry(long id, TimelineEntry entry)`; `HistoryEntry(long id, TimelineEntry entry, LocalDate endDate, boolean current, boolean scheduled)`; `SalaryHistory.of(List<IdentifiedEntry> entries, LocalDate today) : List<HistoryEntry>` newest first. The input order does not matter.

- [ ] **Step 1: Create the records and a skeleton that returns nothing**

`IdentifiedEntry.java`:

```java
package com.acme.peoplemanagement.salary.rules;

/** A stored salary entry with its id. */
public record IdentifiedEntry(long id, TimelineEntry entry) {
}
```

`HistoryEntry.java`:

```java
package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;

/**
 * An entry as shown in the history. {@code endDate} is the last day of its period, or null for the
 * newest entry. {@code current} marks the salary paid today, and {@code scheduled} a start still ahead.
 */
public record HistoryEntry(long id, TimelineEntry entry, LocalDate endDate, boolean current, boolean scheduled) {
}
```

`SalaryHistory.java` (skeleton):

```java
package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;
import java.util.List;

/** Builds the salary history from an employee's entries. Plain Java: no Spring, no database. */
public final class SalaryHistory {

    private SalaryHistory() {
    }

    /** The history, newest first. */
    public static List<HistoryEntry> of(final List<IdentifiedEntry> entries, final LocalDate today) {
        return List.of();
    }
}
```

- [ ] **Step 2: Write the failing tests**

`SalaryHistoryTest.java`:

```java
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
```

- [ ] **Step 3: Run and watch them fail**

Run: `cd backend && ./gradlew test --tests '*SalaryHistoryTest' --console=plain --warning-mode=none`
Expected: all 6 FAIL (the skeleton returns an empty list).

- [ ] **Step 4: Implement**

Replace the body of `SalaryHistory.java` below the constructor:

```java
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
```

Add the imports `java.util.ArrayList` and `java.util.Comparator`.

- [ ] **Step 5: Run and watch it pass, then prove the tests**

Expected: 6 PASS. Prove, restoring after each:
1. `.minusDays(1)` to `.minusDays(0)`: `listsNewestFirstWithTheLastDayOfEachPeriodAsItsEndDate`, the future-entry test and the leap-day test FAIL.
2. `isAfter(today)` in the `scheduled` argument to `!isBefore(today)`: `anEntryDatedTodayIsCurrentAndOneDatedTomorrowIsScheduled` FAILS (today becomes scheduled).
3. Remove the `.sorted(...)` call (keep the stream): `sortsByDateWhateverOrderTheEntriesArriveIn` FAILS.
4. `.filter(stored -> !stored.entry().effectiveDate().isAfter(today))` to `.filter(stored -> stored.entry().effectiveDate().isBefore(today))`: `anEntryDatedTodayIsCurrentAndOneDatedTomorrowIsScheduled` FAILS (today is no longer current).
Confirm the file is identical afterwards.

- [ ] **Step 6: Gates and stage**

Run: `make test-backend`. Expected: exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/salary/rules backend/src/test/java/com/acme/peoplemanagement/salary/rules
```

Suggested message: `feat(api): derive the salary history with end dates and current and scheduled flags`

---

### Task 3: Adding an entry and reading the history in the salary service

**Files:**
- Create: `salary/rules/NewSalaryEntry.java`, `salary/errors/CurrencyRequiredException.java`, `salary/errors/package-info.java`
- Modify: `salary/SalaryEntry.java`, `salary/SalaryEntryRepository.java`, `salary/SalaryService.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/salary/SalaryServiceIntegrationTest.java` (append)

**Interfaces:**
- Consumes: `SalaryTimelineValidator`, `SalaryHistory`, `IdentifiedEntry`, `HistoryEntry`, `TimelineEntry`, `Money` (Tasks 1 and 2 and slice 2), `TestEmployees`.
- Produces: `NewSalaryEntry(LocalDate effectiveDate, long amountMinor, String currencyCode)` with a nullable currency; `CurrencyRequiredException()` with `field()` returning `"currency"`; `SalaryService.addEntry(long employeeId, LocalDate hireDate, NewSalaryEntry newEntry, LocalDate today) : HistoryEntry` and `SalaryService.history(long employeeId, LocalDate today) : List<HistoryEntry>`.

- [ ] **Step 1: Create the two small types**

`salary/rules/NewSalaryEntry.java`:

```java
package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;

/** A salary entry to add. The currency may be null: it then defaults to that of the entry before the date. */
public record NewSalaryEntry(LocalDate effectiveDate, long amountMinor, String currencyCode) {
}
```

`salary/errors/CurrencyRequiredException.java`:

```java
package com.acme.peoplemanagement.salary.errors;

/** A currency was not given and there is no earlier entry to take it from. */
public final class CurrencyRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CurrencyRequiredException() {
        super("A currency is required, because there is no earlier salary to take it from.");
    }

    /** The request field to show the message under. */
    public String field() {
        return "currency";
    }
}
```

`salary/errors/package-info.java`:

```java
/**
 * The ways adding a salary entry can fail that are not timeline rules.
 */
package com.acme.peoplemanagement.salary.errors;
```

- [ ] **Step 2: Write the failing tests**

Append to `SalaryServiceIntegrationTest` (add the imports `com.acme.peoplemanagement.salary.errors.CurrencyRequiredException`, `com.acme.peoplemanagement.salary.rules.HistoryEntry`, `com.acme.peoplemanagement.salary.rules.NewSalaryEntry`, `com.acme.peoplemanagement.salary.rules.SalaryRuleCode`, and `java.util.List`):

```java
    @Test
    void addsAnEntryThatBecomesTheCurrentSalaryAndEndsTheEarlierOne() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        final HistoryEntry added = salaryService.addEntry(
                employeeId, HIRE_DATE, new NewSalaryEntry(LocalDate.of(2026, 4, 1), 150L, "INR"), TODAY);

        assertThat(added.current()).isTrue();
        assertThat(added.endDate()).isNull();
        assertThat(salaryService.history(employeeId, TODAY)).extracting(HistoryEntry::endDate)
                .containsExactly(null, LocalDate.of(2026, 3, 31));
    }

    @Test
    void addsAFutureEntryAsScheduledAndLeavesTheEarlierOneCurrent() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        final HistoryEntry added = salaryService.addEntry(
                employeeId, HIRE_DATE, new NewSalaryEntry(TODAY.plusDays(1), 150L, "INR"), TODAY);

        assertThat(added.scheduled()).isTrue();
        assertThat(salaryService.history(employeeId, TODAY)).extracting(HistoryEntry::current)
                .containsExactly(false, true);
    }

    @Test
    void defaultsTheCurrencyToTheEntryBeforeTheNewDate() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2027, 1, 1), 5_000L, "USD");

        final HistoryEntry added = salaryService.addEntry(
                employeeId, HIRE_DATE, new NewSalaryEntry(LocalDate.of(2026, 3, 1), 120L, null), TODAY);

        assertThat(added.entry().salary().currencyCode()).isEqualTo("INR");
    }

    @Test
    void requiresACurrencyWhenNoEntryPrecedesTheNewDate() {
        employees.insertSalary(employeeId, LocalDate.of(2025, 1, 1), 100L, "INR");

        assertThatThrownBy(() -> salaryService.addEntry(employeeId, HIRE_DATE,
                new NewSalaryEntry(LocalDate.of(2024, 6, 1), 90L, null), TODAY))
                .isInstanceOf(CurrencyRequiredException.class);
    }

    @Test
    void rejectsADateThatIsAlreadyUsed() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> salaryService.addEntry(employeeId, HIRE_DATE,
                new NewSalaryEntry(HIRE_DATE, 150L, "INR"), TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.DUPLICATE_SALARY_DATE));
    }

    @Test
    void rejectsADateBeforeTheHireDateAndSavesNothing() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> salaryService.addEntry(employeeId, HIRE_DATE,
                new NewSalaryEntry(HIRE_DATE.minusDays(1), 90L, "INR"), TODAY))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE));

        assertThat(salaryService.history(employeeId, TODAY)).hasSize(1);
    }

    @Test
    void listsTheHistoryNewestFirstWithTheDerivedValues() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2025, 4, 1), 120L, "INR");

        final List<HistoryEntry> history = salaryService.history(employeeId, TODAY);

        assertThat(history).extracting(entry -> entry.entry().effectiveDate())
                .containsExactly(LocalDate.of(2025, 4, 1), HIRE_DATE);
        assertThat(history.getFirst().current()).isTrue();
        assertThat(history.getLast().endDate()).isEqualTo(LocalDate.of(2025, 3, 31));
    }
```

- [ ] **Step 3: Run and watch it fail to compile**

Run: `cd backend && ./gradlew integrationTest --tests '*SalaryServiceIntegrationTest' --console=plain --warning-mode=none`
Expected: FAIL, `cannot find symbol addEntry`.

- [ ] **Step 4: Implement**

`SalaryEntry.java`: add

```java
    long id() {
        return id;
    }
```

`SalaryEntryRepository.java`: add (and the `List` import)

```java
    List<SalaryEntry> findByEmployeeIdOrderByEffectiveDateDesc(long employeeId);
```

`SalaryService.java`: add the imports (`CurrencyRequiredException`, `Money`, `NewSalaryEntry`, `IdentifiedEntry`, `HistoryEntry`, `SalaryHistory`, `java.util.Comparator`, `java.util.stream.Stream`) and the methods:

```java
    /**
     * Adds an entry after checking the resulting timeline. The caller holds the employee row lock, so the
     * timeline read here cannot change before the entry is written.
     */
    @Transactional
    public HistoryEntry addEntry(
            final long employeeId, final LocalDate hireDate, final NewSalaryEntry newEntry, final LocalDate today) {
        final List<IdentifiedEntry> stored = storedEntriesOf(employeeId);
        final TimelineEntry added = toTimelineEntry(newEntry, stored);
        validator.validate(hireDate, timelineWith(stored, added), today);
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

    private static TimelineEntry toTimelineEntry(final NewSalaryEntry newEntry, final List<IdentifiedEntry> stored) {
        final String currency = newEntry.currencyCode() != null
                ? newEntry.currencyCode()
                : currencyBefore(newEntry.effectiveDate(), stored);
        return new TimelineEntry(newEntry.effectiveDate(), new Money(newEntry.amountMinor(), currency));
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
```

- [ ] **Step 5: Run and watch it pass, then prove the tests**

Run the same command. Expected: all PASS (the earlier tests in the file too). Prove, restoring after each:
1. In `currencyBefore`, `.max(` to `.min(`: `defaultsTheCurrencyToTheEntryBeforeTheNewDate` FAILS.
2. In `currencyBefore`, `isBefore(date)` to `isAfter(date)`: the default test FAILS.
3. In `addEntry`, `validator.validate(hireDate, timelineWith(stored, added), today)` to `validator.validate(added.effectiveDate(), timelineWith(stored, added), today)` (the hire date rule can never fire): `rejectsADateBeforeTheHireDateAndSavesNothing` FAILS.
4. In `timelineWith`, replace `Stream.of(added)` with `Stream.of(new TimelineEntry(added.effectiveDate().plusYears(100), added.salary()))` (the validator then never sees the real date): `rejectsADateThatIsAlreadyUsed` FAILS. Do not drop `added` from the method: an unused parameter is rejected by Error Prone under `-Werror`, so nothing would run.

- [ ] **Step 6: Gates and stage**

Run: `make test-backend && make test-integration`. Expected: both exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/salary backend/src/test/java/com/acme/peoplemanagement/salary/SalaryServiceIntegrationTest.java
```

Suggested message: `feat(api): add a salary entry and read the history in the salary service`

---

### Task 4: The employee row lock and the employee salary service

**Files:**
- Modify: `employee/EmployeeRepository.java`
- Create: `employee/EmployeeSalaryService.java`, `employee/api/AddSalaryEntryRequest.java`, `employee/api/SalaryEntryResponse.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeSalaryServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `SalaryService.addEntry/history`, `NewSalaryEntry`, `HistoryEntry` (Task 3), `ReferenceLookup`, `EmployeeNotFoundException`, `UnknownReferenceException`.
- Produces: `EmployeeRepository.findByIdForUpdate(long id) : Optional<Employee>` with `PESSIMISTIC_WRITE`; `AddSalaryEntryRequest(LocalDate effectiveDate, Long amountMinor, String currency)`; `SalaryEntryResponse(long id, LocalDate effectiveDate, long amountMinor, String currency, LocalDate endDate, boolean current, boolean scheduled)`; `@Service public class EmployeeSalaryService` with `SalaryEntryResponse add(long employeeId, AddSalaryEntryRequest request)` and `List<SalaryEntryResponse> list(long employeeId)`.

- [ ] **Step 1: Create the two records**

`employee/api/AddSalaryEntryRequest.java`:

```java
package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.salary.rules.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** A salary entry to add. The currency is optional: it defaults to that of the entry before the date. */
public record AddSalaryEntryRequest(
        @NotNull LocalDate effectiveDate,
        @NotNull @Min(1) @Max(Money.MAX_AMOUNT_MINOR) Long amountMinor,
        @Pattern(regexp = "[A-Z]{3}") String currency) {
}
```

`employee/api/SalaryEntryResponse.java`:

```java
package com.acme.peoplemanagement.employee.api;

import java.time.LocalDate;

/** One entry of the salary history, with its derived end date and markers. */
public record SalaryEntryResponse(
        long id,
        LocalDate effectiveDate,
        long amountMinor,
        String currency,
        LocalDate endDate,
        boolean current,
        boolean scheduled) {
}
```

- [ ] **Step 2: Write the failing tests, including the concurrency test**

`EmployeeSalaryServiceIntegrationTest.java`:

```java
package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.employee.api.AddSalaryEntryRequest;
import com.acme.peoplemanagement.employee.api.SalaryEntryResponse;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeSalaryServiceIntegrationTest {

    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

    @Autowired
    private EmployeeSalaryService employeeSalaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("employee-salary@acme.example", HIRE_DATE);
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void addsAnEntryAndReturnsItWithItsIdAndTheDerivedValues() {
        final SalaryEntryResponse added = employeeSalaryService.add(employeeId, request("2026-04-01", 150L, "INR"));

        assertThat(added.id()).isPositive();
        assertThat(added.current()).isTrue();
        assertThat(added.endDate()).isNull();
        assertThat(added.currency()).isEqualTo("INR");
    }

    @Test
    void listsTheHistoryNewestFirst() {
        employeeSalaryService.add(employeeId, request("2026-04-01", 150L, "INR"));

        final List<SalaryEntryResponse> history = employeeSalaryService.list(employeeId);

        assertThat(history).extracting(SalaryEntryResponse::effectiveDate)
                .containsExactly(LocalDate.of(2026, 4, 1), HIRE_DATE);
    }

    @Test
    void reportsAnEmployeeThatDoesNotExistWhenAdding() {
        assertThatThrownBy(() -> employeeSalaryService.add(-1L, request("2026-04-01", 150L, "INR")))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void reportsAnEmployeeThatDoesNotExistWhenListing() {
        assertThatThrownBy(() -> employeeSalaryService.list(-1L)).isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void namesTheCurrencyFieldWhenTheCurrencyDoesNotExist() {
        assertThatThrownBy(() -> employeeSalaryService.add(employeeId, request("2026-04-01", 150L, "ZZZ")))
                .isInstanceOfSatisfying(UnknownReferenceException.class,
                        exception -> assertThat(exception.field()).isEqualTo("currency"));
    }

    @Test
    void twoSimultaneousAddsOnTheSameDateLeaveExactlyOneEntry() throws Exception {
        final CyclicBarrier bothReady = new CyclicBarrier(2);
        final Callable<String> add = () -> {
            bothReady.await();
            try {
                employeeSalaryService.add(employeeId, request("2026-01-01", 150L, "INR"));
                return "added";
            } catch (RuntimeException exception) {
                return exception.getClass().getSimpleName() + ":" + exception.getMessage();
            }
        };
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            final List<Future<String>> outcomes = pool.invokeAll(List.of(add, add), 60, TimeUnit.SECONDS);
            final List<String> results = List.of(outcomes.get(0).get(), outcomes.get(1).get());

            assertThat(results).containsOnlyOnce("added");
            assertThat(results).filteredOn(result -> !result.equals("added"))
                    .singleElement().asString().startsWith("SalaryRuleViolation");
            assertThat(entriesOn("2026-01-01")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private int entriesOn(final String date) {
        final Integer count = jdbcTemplate.queryForObject(
                "select count(*) from salary_entry where employee_id = ? and effective_date = ?",
                Integer.class, employeeId, LocalDate.parse(date));
        return count == null ? 0 : count;
    }

    private static AddSalaryEntryRequest request(final String date, final long amountMinor, final String currency) {
        return new AddSalaryEntryRequest(LocalDate.parse(date), amountMinor, currency);
    }
}
```

- [ ] **Step 3: Run and watch it fail to compile**

Run: `cd backend && ./gradlew integrationTest --tests '*EmployeeSalaryServiceIntegrationTest' --console=plain --warning-mode=none`
Expected: FAIL, `cannot find symbol EmployeeSalaryService`.

- [ ] **Step 4: Implement the lock and the service**

`EmployeeRepository.java`:

```java
package com.acme.peoplemanagement.employee;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Reads an employee and locks the row until the transaction ends, so only one salary change per
     * employee runs at a time. A pessimistic lock does not change {@code version}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") long id);
}
```

`EmployeeSalaryService.java`:

```java
package com.acme.peoplemanagement.employee;

import com.acme.peoplemanagement.employee.api.AddSalaryEntryRequest;
import com.acme.peoplemanagement.employee.api.SalaryEntryResponse;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.reference.ReferenceLookup;
import com.acme.peoplemanagement.salary.SalaryService;
import com.acme.peoplemanagement.salary.rules.HistoryEntry;
import com.acme.peoplemanagement.salary.rules.NewSalaryEntry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The salary use cases of an employee. It owns what belongs to the employee (the row lock and the hire
 * date) and leaves the salary rules to the salary service, which never needs to know about employees.
 */
@Service
public class EmployeeSalaryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;
    private final ReferenceLookup referenceLookup;
    private final Clock clock;

    EmployeeSalaryService(
            final EmployeeRepository employeeRepository,
            final SalaryService salaryService,
            final ReferenceLookup referenceLookup,
            final Clock clock) {
        this.employeeRepository = employeeRepository;
        this.salaryService = salaryService;
        this.referenceLookup = referenceLookup;
        this.clock = clock;
    }

    /** Locks the employee first, so two adds for the same employee are checked one after the other. */
    @Transactional
    public SalaryEntryResponse add(final long employeeId, final AddSalaryEntryRequest request) {
        final Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        verifyCurrencyExists(request.currency());
        final NewSalaryEntry newEntry =
                new NewSalaryEntry(request.effectiveDate(), request.amountMinor(), request.currency());
        return toResponse(salaryService.addEntry(employeeId, employee.hireDate(), newEntry, LocalDate.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<SalaryEntryResponse> list(final long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return salaryService.history(employeeId, LocalDate.now(clock)).stream().map(this::toResponse).toList();
    }

    private void verifyCurrencyExists(final String currency) {
        if (currency != null && referenceLookup.currency(currency).isEmpty()) {
            throw new UnknownReferenceException("currency", "Unknown currency.");
        }
    }

    private SalaryEntryResponse toResponse(final HistoryEntry history) {
        return new SalaryEntryResponse(history.id(), history.entry().effectiveDate(),
                history.entry().salary().amountMinor(), history.entry().salary().currencyCode(),
                history.endDate(), history.current(), history.scheduled());
    }
}
```

- [ ] **Step 5: Run and watch it pass**

Run: `cd backend && ./gradlew integrationTest --tests '*EmployeeSalaryServiceIntegrationTest' --console=plain --warning-mode=none`
Expected: 6 PASS. Run the concurrency test a few times (`for run in 1 2 3; do ...`) to see it is stable.

- [ ] **Step 6: Prove the tests, above all the lock**

Restore after each and confirm the file is identical:
1. In `EmployeeSalaryService.add`, use `employeeRepository.findById(employeeId)` (no lock) instead of `findByIdForUpdate`. Run the concurrency test several times. Expected: it FAILS (the second add hits the unique index and returns a `DataIntegrityViolationException`, not a rule violation, or both are added). If it passes in all runs, the test is not racing and must be strengthened before this task is done: add more barrier-synchronized attempts.
2. Change `verifyCurrencyExists` to never throw: `namesTheCurrencyFieldWhenTheCurrencyDoesNotExist` FAILS.
3. In `list`, remove the `existsById` check: `reportsAnEmployeeThatDoesNotExistWhenListing` FAILS.
4. In `toResponse`, swap `history.current()` and `history.scheduled()`: `addsAnEntryAndReturnsItWithItsIdAndTheDerivedValues` FAILS.

- [ ] **Step 7: Gates and stage**

Run: `make test-backend && make test-integration`. Expected: both exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/employee backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeSalaryServiceIntegrationTest.java
```

Suggested message: `feat(api): lock the employee row while adding a salary entry`

---

### Task 5: The HTTP layer and the error mapping

**Files:**
- Create: `employee/api/SalaryEntryController.java`
- Modify: `config/ApiExceptionHandler.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/api/SalaryEntryEndpointTest.java`

**Interfaces:**
- Consumes: `EmployeeSalaryService.add/list`, the records from Task 4, `CurrencyRequiredException`.
- Produces: `GET /api/employees/{id}/salary-entries` (`200`, newest first) and `POST` (`201`, relative `Location`: `/api/employees/{id}/salary-entries/{entryId}`). `ApiExceptionHandler` maps `CurrencyRequiredException` to a `400` with `errors: [{ field: "currency", message }]`.

- [ ] **Step 1: Write the failing web slice tests**

`SalaryEntryEndpointTest.java`:

```java
package com.acme.peoplemanagement.employee.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.auth.AuthTestSupport;
import com.acme.peoplemanagement.employee.EmployeeSalaryService;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.salary.errors.CurrencyRequiredException;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SalaryEntryController.class)
@ActiveProfiles("test")
@Import({AuthTestSupport.class, SalaryEntryEndpointTest.ClockConfiguration.class})
class SalaryEntryEndpointTest {

    private static final String VALID_BODY = "{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 150000000, "
            + "\"currency\": \"INR\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeSalaryService employeeSalaryService;

    @Test
    void addsAnEntryAndPointsToIt() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willReturn(sampleEntry());

        postEntry(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/7/salary-entries/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.endDate").doesNotExist());
    }

    @Test
    void acceptsARequestWithoutACurrency() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willReturn(sampleEntry());

        postEntry("{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 150000000}")
                .andExpect(status().isCreated());
    }

    @Test
    void listsTheHistory() throws Exception {
        given(employeeSalaryService.list(7L)).willReturn(List.of(sampleEntry()));

        mockMvc.perform(get("/api/employees/7/salary-entries").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].amountMinor").value(150000000))
                .andExpect(jsonPath("$[0].scheduled").value(false));
    }

    @Test
    void namesTheFieldWhenTheDateIsMissing() throws Exception {
        postEntry("{\"amountMinor\": 150000000, \"currency\": \"INR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("effectiveDate"));
    }

    @Test
    void rejectsAZeroAnOverCapAndAFractionalAmount() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willReturn(sampleEntry());

        postEntry(VALID_BODY.replace("150000000", "0")).andExpect(status().isBadRequest());
        postEntry(VALID_BODY.replace("150000000", "9007199254740992")).andExpect(status().isBadRequest());
        postEntry(VALID_BODY.replace("150000000", "100.9")).andExpect(status().isBadRequest());

        verify(employeeSalaryService, never()).add(eq(7L), any());
    }

    @Test
    void rejectsALowerCaseCurrency() throws Exception {
        postEntry(VALID_BODY.replace("INR", "inr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void answers422WithTheCodeForADuplicateDate() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willThrow(
                new SalaryRuleViolation(SalaryRuleCode.DUPLICATE_SALARY_DATE, "There is already a salary."));

        postEntry(VALID_BODY)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DUPLICATE_SALARY_DATE"));
    }

    @Test
    void answers400WithTheCurrencyFieldWhenACurrencyIsRequired() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willThrow(new CurrencyRequiredException());

        postEntry("{\"effectiveDate\": \"2024-06-01\", \"amountMinor\": 90}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void answers400WithTheCurrencyFieldForAnUnknownCurrency() throws Exception {
        given(employeeSalaryService.add(eq(7L), any()))
                .willThrow(new UnknownReferenceException("currency", "Unknown currency."));

        postEntry(VALID_BODY.replace("INR", "ZZZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Unknown currency."));
    }

    @Test
    void answers404ForAnEmployeeThatDoesNotExist() throws Exception {
        given(employeeSalaryService.list(9L)).willThrow(new EmployeeNotFoundException(9L));

        mockMvc.perform(get("/api/employees/9/salary-entries").with(login()))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersProblemJsonForAnEmployeeIdThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/employees/abc/salary-entries").with(login()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(post("/api/employees/7/salary-entries")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions postEntry(final String body) throws Exception {
        return mockMvc.perform(post("/api/employees/7/salary-entries")
                .with(login()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }

    private static SalaryEntryResponse sampleEntry() {
        return new SalaryEntryResponse(5L, LocalDate.of(2026, 4, 1), 150_000_000L, "INR", null, true, false);
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
```

- [ ] **Step 2: Run and watch it fail to compile**

Run: `cd backend && ./gradlew test --tests '*SalaryEntryEndpointTest' --console=plain --warning-mode=none`
Expected: FAIL, `cannot find symbol SalaryEntryController`.

- [ ] **Step 3: Implement the controller and the handler**

`employee/api/SalaryEntryController.java`:

```java
package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.employee.EmployeeSalaryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The salary history of an employee. Errors are turned into problem+json by the shared exception handler. */
@RestController
@RequestMapping("/api/employees/{employeeId}/salary-entries")
class SalaryEntryController {

    private final EmployeeSalaryService employeeSalaryService;

    SalaryEntryController(final EmployeeSalaryService employeeSalaryService) {
        this.employeeSalaryService = employeeSalaryService;
    }

    @GetMapping
    List<SalaryEntryResponse> list(@PathVariable final long employeeId) {
        return employeeSalaryService.list(employeeId);
    }

    @PostMapping
    ResponseEntity<SalaryEntryResponse> add(
            @PathVariable final long employeeId, @Valid @RequestBody final AddSalaryEntryRequest request) {
        final SalaryEntryResponse added = employeeSalaryService.add(employeeId, request);
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/salary-entries/" + added.id()))
                .body(added);
    }
}
```

In `ApiExceptionHandler.java` add the import `com.acme.peoplemanagement.salary.errors.CurrencyRequiredException` and the handler:

```java
    @ExceptionHandler(CurrencyRequiredException.class)
    ProblemDetail currencyRequired(final CurrencyRequiredException exception) {
        return invalidRequest(List.of(new FieldProblem(exception.field(), exception.getMessage())));
    }
```

- [ ] **Step 4: Run and watch it pass, then prove the tests**

Expected: 13 PASS. Prove, restoring after each:
1. Remove the `currencyRequired` handler method: `answers400WithTheCurrencyFieldWhenACurrencyIsRequired` FAILS (the exception propagates).
2. Change `ResponseEntity.created(...)` to `ResponseEntity.ok()` with the same body: `addsAnEntryAndPointsToIt` FAILS.
3. Change `@Max(Money.MAX_AMOUNT_MINOR)` in `AddSalaryEntryRequest` to `@Max(Long.MAX_VALUE)`: `rejectsAZeroAnOverCapAndAFractionalAmount` FAILS.
4. Remove `@NotNull` from `effectiveDate`: `namesTheFieldWhenTheDateIsMissing` FAILS.
5. Change the `Location` URL to omit `added.id()` (use `employeeId` twice): `addsAnEntryAndPointsToIt` FAILS.

- [ ] **Step 5: Gates and stage**

Run: `make test-backend && make test-integration`. Expected: both exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/employee/api/SalaryEntryController.java backend/src/main/java/com/acme/peoplemanagement/config/ApiExceptionHandler.java backend/src/test/java/com/acme/peoplemanagement/employee/api/SalaryEntryEndpointTest.java
```

Suggested message: `feat(api): add GET and POST /api/employees/{id}/salary-entries`

---

### Task 6: The end-to-end check through HTTP and PostgreSQL

**Files:**
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/api/SalaryEntryEndpointIntegrationTest.java`

**Interfaces:**
- Consumes: everything above, with the real handler, controller, services and database. No production change expected. A failure here that the earlier layers did not catch is a real finding: report it.

- [ ] **Step 1: Write the test**

```java
package com.acme.peoplemanagement.employee.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
class SalaryEntryEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void addingARaiseEndsTheEarlierEntryAndMakesTheRaiseCurrent() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "raise@acme.example");

        postEntry(employeeId, "{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 150000000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.current").value(true));

        mockMvc.perform(get("/api/employees/" + employeeId + "/salary-entries").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].effectiveDate").value("2026-04-01"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].endDate").value("2026-03-31"))
                .andExpect(jsonPath("$[1].current").value(false));
    }

    @Test
    void aFutureRaiseIsScheduledAndTheEarlierEntryStaysCurrent() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "future-raise@acme.example");

        postEntry(employeeId, "{\"effectiveDate\": \"2026-10-20\", \"amountMinor\": 150000000, \"currency\": \"USD\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduled").value(true));

        mockMvc.perform(get("/api/employees/" + employeeId + "/salary-entries").with(login()))
                .andExpect(jsonPath("$[1].current").value(true));
    }

    @Test
    void answers422ForADateThatIsAlreadyUsed() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "dup-date@acme.example");

        postEntry(employeeId, "{\"effectiveDate\": \"2024-04-01\", \"amountMinor\": 150000000}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DUPLICATE_SALARY_DATE"));
    }

    @Test
    void answers400NamingTheCurrencyForABackdatedEntryWithNoEarlierEntry() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "backdated@acme.example");
        postEntry(employeeId, "{\"effectiveDate\": \"2025-06-01\", \"amountMinor\": 100, \"currency\": \"INR\"}")
                .andExpect(status().isCreated());
        jdbcTemplate.update("delete from salary_entry where employee_id = ? and effective_date = ?",
                employeeId, java.time.LocalDate.parse("2024-04-01"));

        postEntry(employeeId, "{\"effectiveDate\": \"2024-06-01\", \"amountMinor\": 90}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void answers404ForAnEmployeeThatDoesNotExist() throws Exception {
        postEntry(-1L, "{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 100}").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employees/-1/salary-entries").with(login())).andExpect(status().isNotFound());
    }

    private long employeeHiredOn(final String hireDate, final String email) {
        final TestEmployees employees = new TestEmployees(jdbcTemplate);
        final long id = employees.insert(email, java.time.LocalDate.parse(hireDate));
        employees.insertSalary(id, java.time.LocalDate.parse(hireDate), 100_000_000L, "INR");
        return id;
    }

    private ResultActions postEntry(final long employeeId, final String body) throws Exception {
        return mockMvc.perform(post("/api/employees/" + employeeId + "/salary-entries")
                .with(login()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }
}
```

- [ ] **Step 2: Run it**

Run: `cd backend && ./gradlew integrationTest --tests '*SalaryEntryEndpointIntegrationTest' --console=plain --warning-mode=none`
Expected: 5 PASS. There is no RED step: everything it exercises already exists, so it is a cross-layer check proven by mutation.

- [ ] **Step 3: Prove the tests**

Restore after each:
1. In `SalaryService.addEntry`, change `isBefore(date)` in `currencyBefore` to `isAfter(date)`: `addingARaiseEndsTheEarlierEntryAndMakesTheRaiseCurrent` FAILS (no earlier entry found, so a currency-required error).
2. In `SalaryHistory.endDateOf`, change `.minusDays(1)` to `.minusDays(0)`: the same test FAILS on `endDate`.
3. Delete the `@ExceptionHandler(EmployeeNotFoundException.class)` method: `answers404ForAnEmployeeThatDoesNotExist` FAILS.
4. In `EmployeeSalaryService.add`, change `LocalDate.now(clock)` to `LocalDate.now(clock).plusYears(1)`: `aFutureRaiseIsScheduledAndTheEarlierEntryStaysCurrent` FAILS.

- [ ] **Step 4: Gates and stage**

Run: `make test-backend && make test-integration`. Expected: both exit 0.

```bash
git add backend/src/test/java/com/acme/peoplemanagement/employee/api/SalaryEntryEndpointIntegrationTest.java
```

Suggested message: `test(api): check the salary history and adding a raise through HTTP against PostgreSQL`

---

### Task 7: The API client for the salary history

**Files:**
- Modify: `frontend/src/app/core/employees/employee-api.ts`
- Test: `frontend/src/app/core/employees/employee-api.spec.ts`

**Interfaces:**
- Produces: `interface SalaryEntry { id; effectiveDate; amountMinor; currency; endDate: string | null; current; scheduled }`, `interface AddSalaryEntryRequest { effectiveDate: string; amountMinor: number; currency?: string }`, `EmployeeApi.listSalaryEntries(employeeId: number): Promise<SalaryEntry[]>` and `EmployeeApi.addSalaryEntry(employeeId: number, request: AddSalaryEntryRequest): Promise<SalaryEntry>`.

Run frontend commands with Node set up: from the repo root, `. scripts/use-node.sh && cd frontend && ...`.

- [ ] **Step 1: Write the failing tests**

In `employee-api.spec.ts`, change the first import to `import { AddSalaryEntryRequest, CreateEmployeeRequest, EmployeeApi, SalaryEntry } from './employee-api';` and add inside the `describe`:

```ts
  it('lists the salary entries of an employee', async () => {
    const entry: SalaryEntry = {
      id: 5,
      effectiveDate: '2026-04-01',
      amountMinor: 150000000,
      currency: 'INR',
      endDate: null,
      current: true,
      scheduled: false,
    };
    const listing = api.listSalaryEntries(10432);
    http.expectOne('/api/employees/10432/salary-entries').flush([entry]);

    expect(await listing).toEqual([entry]);
  });

  it('posts a new salary entry for an employee and returns it', async () => {
    const request: AddSalaryEntryRequest = {
      effectiveDate: '2026-04-01',
      amountMinor: 150000000,
      currency: 'INR',
    };
    const adding = api.addSalaryEntry(10432, request);
    const call = http.expectOne('/api/employees/10432/salary-entries');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    call.flush({ id: 5 });

    expect((await adding).id).toBe(5);
  });
```

- [ ] **Step 2: Run and watch it fail**

Run: `npm run test -- --include src/app/core/employees/employee-api.spec.ts`. Expected: FAIL (`listSalaryEntries` does not exist).

- [ ] **Step 3: Implement**

In `employee-api.ts` add:

```ts
export interface SalaryEntry {
  id: number;
  effectiveDate: string;
  amountMinor: number;
  currency: string;
  endDate: string | null;
  current: boolean;
  scheduled: boolean;
}

export interface AddSalaryEntryRequest {
  effectiveDate: string;
  amountMinor: number;
  currency?: string;
}
```

and in `EmployeeApi`:

```ts
  listSalaryEntries(employeeId: number): Promise<SalaryEntry[]> {
    return firstValueFrom(
      this.http.get<SalaryEntry[]>(`${this.apiBaseUrl}/employees/${employeeId}/salary-entries`),
    );
  }

  addSalaryEntry(employeeId: number, request: AddSalaryEntryRequest): Promise<SalaryEntry> {
    return firstValueFrom(
      this.http.post<SalaryEntry>(
        `${this.apiBaseUrl}/employees/${employeeId}/salary-entries`,
        request,
      ),
    );
  }
```

- [ ] **Step 4: Run, prove, gates and stage**

Expected: PASS. Prove (restoring after each): change the list URL to `/salary-entry`: the list test FAILS. Change `post` to `put`: the add test FAILS. Then run `npm run format:write && npm run format:check && npm run lint && npm run test && npm run typecheck && npm run knip`, all exit 0.

```bash
git add frontend/src/app/core/employees
```

Suggested message: `feat(ui): add the salary history calls to the employee API service`

---

### Task 8: The salary history table

**Files:**
- Create: `frontend/src/app/features/employee-detail/salary-history/salary-history.{ts,html,scss}`
- Test: `frontend/src/app/features/employee-detail/salary-history/salary-history.spec.ts`

**Interfaces:**
- Consumes: `SalaryEntry` (Task 7), `Currency` from `core/reference/reference-api`, `formatMinor`.
- Produces: `SalaryHistory` component, selector `app-salary-history`, inputs `entries: SalaryEntry[]` and `currencies: Currency[]`.

- [ ] **Step 1: Write the failing spec**

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SalaryEntry } from '../../../core/employees/employee-api';
import { SalaryHistory } from './salary-history';

const ENTRIES: SalaryEntry[] = [
  {
    id: 3,
    effectiveDate: '2027-01-01',
    amountMinor: 20000000,
    currency: 'JPY',
    endDate: null,
    current: false,
    scheduled: true,
  },
  {
    id: 2,
    effectiveDate: '2025-04-01',
    amountMinor: 150000050,
    currency: 'INR',
    endDate: '2026-12-31',
    current: true,
    scheduled: false,
  },
  {
    id: 1,
    effectiveDate: '2024-04-01',
    amountMinor: 125000000,
    currency: 'INR',
    endDate: '2025-03-31',
    current: false,
    scheduled: false,
  },
];

const CURRENCIES = [
  { code: 'INR', minorUnitDigits: 2 },
  { code: 'JPY', minorUnitDigits: 0 },
];

describe('SalaryHistory', () => {
  let fixture: ComponentFixture<SalaryHistory>;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SalaryHistory] }).compileComponents();
    fixture = TestBed.createComponent(SalaryHistory);
    page = fixture.nativeElement as HTMLElement;
    fixture.componentRef.setInput('entries', ENTRIES);
    fixture.componentRef.setInput('currencies', CURRENCIES);
    fixture.detectChanges();
  });

  function rows(): string[][] {
    return Array.from(page.querySelectorAll('tr[mat-row]')).map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim() ?? ''),
    );
  }

  it('shows one row per entry in the order given, newest first', () => {
    expect(rows().map((row) => row[0])).toEqual(['2027-01-01', '2025-04-01', '2024-04-01']);
  });

  it('shows the last day of each period, and a dash for the newest', () => {
    expect(rows().map((row) => row[1])).toEqual(['—', '2026-12-31', '2025-03-31']);
  });

  it('shows each amount in its own currency with that currency decimals', () => {
    expect(rows().map((row) => row[2])).toEqual([
      '20,000,000 JPY',
      '1,500,000.50 INR',
      '1,250,000.00 INR',
    ]);
  });

  it('marks the current and the scheduled entries', () => {
    expect(rows().map((row) => row[3])).toEqual(['Scheduled', 'Current', '']);
  });
});
```

- [ ] **Step 2: Run and watch it fail**

Run: `npm run test -- --include src/app/features/employee-detail/salary-history/salary-history.spec.ts`. Expected: FAIL (module missing).

- [ ] **Step 3: Implement**

`salary-history.ts`:

```ts
import { Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { SalaryEntry } from '../../../core/employees/employee-api';
import { Currency } from '../../../core/reference/reference-api';
import { formatMinor } from '../../../shared/money/format-minor';

@Component({
  selector: 'app-salary-history',
  imports: [MatTableModule],
  templateUrl: './salary-history.html',
  styleUrl: './salary-history.scss',
})
export class SalaryHistory {
  readonly entries = input.required<SalaryEntry[]>();
  readonly currencies = input.required<Currency[]>();

  protected readonly columns = ['effectiveDate', 'endDate', 'amount', 'marker'];

  protected amountText(entry: SalaryEntry): string {
    const currency = this.currencies().find((candidate) => candidate.code === entry.currency);
    return currency
      ? `${formatMinor(entry.amountMinor, currency.minorUnitDigits)} ${currency.code}`
      : `${entry.amountMinor} ${entry.currency}`;
  }

  protected markerText(entry: SalaryEntry): string {
    if (entry.scheduled) {
      return 'Scheduled';
    }
    return entry.current ? 'Current' : '';
  }
}
```

`salary-history.html`:

```html
<table mat-table [dataSource]="entries()" class="history">
  <ng-container matColumnDef="effectiveDate">
    <th mat-header-cell *matHeaderCellDef>From</th>
    <td mat-cell *matCellDef="let entry">{{ entry.effectiveDate }}</td>
  </ng-container>
  <ng-container matColumnDef="endDate">
    <th mat-header-cell *matHeaderCellDef>To</th>
    <td mat-cell *matCellDef="let entry">{{ entry.endDate ?? '—' }}</td>
  </ng-container>
  <ng-container matColumnDef="amount">
    <th mat-header-cell *matHeaderCellDef>Annual salary</th>
    <td mat-cell *matCellDef="let entry">{{ amountText(entry) }}</td>
  </ng-container>
  <ng-container matColumnDef="marker">
    <th mat-header-cell *matHeaderCellDef></th>
    <td mat-cell *matCellDef="let entry">{{ markerText(entry) }}</td>
  </ng-container>
  <tr mat-header-row *matHeaderRowDef="columns"></tr>
  <tr mat-row *matRowDef="let row; columns: columns"></tr>
</table>
```

`salary-history.scss`:

```scss
.history {
  width: 100%;
}
```

- [ ] **Step 4: Run, prove, gates and stage**

Expected: PASS. Prove (restoring after each): in `amountText`, replace the formatted text with `String(entry.amountMinor)`: the amounts test FAILS. In `markerText`, change `'Scheduled'` to `'Current'`: the markers test FAILS. Change `entry.endDate ?? '—'` to `entry.endDate`: the dash test FAILS. Then run all frontend gates, all exit 0.

```bash
git add frontend/src/app/features/employee-detail/salary-history
```

Suggested message: `feat(ui): add the salary history table with end dates and current and scheduled markers`

---

### Task 9: The "Add salary" dialog

**Files:**
- Move: `features/employee-form/salary-amount.validator.ts` and its spec to `shared/money/`
- Modify: `features/employee-form/employee-form.ts` (the import)
- Create: `features/employee-detail/add-salary-dialog/add-salary-dialog.{ts,html,scss}`
- Test: `features/employee-detail/add-salary-dialog/add-salary-dialog.spec.ts`

**Interfaces:**
- Consumes: `salaryAmountValidator` and `describeAmountError` (moved), `parseMajorToMinor`, `toFormErrors`, `EmployeeApi.addSalaryEntry`, `Currency`.
- Produces: `AddSalaryDialog` (a `MatDialog` component), `AddSalaryDialogData { employeeId: number; currencies: Currency[]; defaultCurrency: string }`. It closes with `true` after a successful add.

- [ ] **Step 1: Move the validator to `shared/money/` (a refactor, tests stay green)**

```bash
git mv frontend/src/app/features/employee-form/salary-amount.validator.ts frontend/src/app/shared/money/salary-amount.validator.ts
git mv frontend/src/app/features/employee-form/salary-amount.validator.spec.ts frontend/src/app/shared/money/salary-amount.validator.spec.ts
```

In the moved file, change `'../../shared/money/parse-major-to-minor'` to `'./parse-major-to-minor'`. In `employee-form.ts`, change the import to `'../../shared/money/salary-amount.validator'`. Run `npm run test` and `npm run typecheck`. Expected: all tests still PASS and no type errors.

- [ ] **Step 2: Write the failing dialog spec**

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AddSalaryDialog, AddSalaryDialogData } from './add-salary-dialog';

const DATA: AddSalaryDialogData = {
  employeeId: 10432,
  defaultCurrency: 'INR',
  currencies: [
    { code: 'INR', minorUnitDigits: 2 },
    { code: 'JPY', minorUnitDigits: 0 },
  ],
};

describe('AddSalaryDialog', () => {
  let fixture: ComponentFixture<AddSalaryDialog>;
  let page: HTMLElement;
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(async () => {
    close.mockReset();
    await TestBed.configureTestingModule({
      imports: [AddSalaryDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: DATA },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddSalaryDialog);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function until(check: () => void): Promise<void> {
    await vi.waitFor(() => {
      fixture.detectChanges();
      check();
    });
  }

  function type(name: string, value: string): void {
    const field = page.querySelector<HTMLInputElement | HTMLSelectElement>(
      `[formControlName="${name}"]`,
    )!;
    field.value = value;
    field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  function submit(): void {
    page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  }

  function errorOf(name: string): string {
    const field = page.querySelector(`[formControlName="${name}"]`)!;
    return field.closest('mat-form-field')?.querySelector('mat-error')?.textContent?.trim() ?? '';
  }

  function alertText(): string {
    return page.querySelector('[role="alert"]')?.textContent ?? '';
  }

  it('preselects the current currency of the employee', () => {
    expect(page.querySelector<HTMLSelectElement>('select[formControlName="currency"]')!.value).toBe(
      'INR',
    );
  });

  it('does not call the server while the date or amount is empty and says what is missing', () => {
    submit();
    fixture.detectChanges();

    http.expectNone('/api/employees/10432/salary-entries');
    expect(errorOf('effectiveDate')).toBe('This field is required.');
    expect(errorOf('amount')).toBe('This field is required.');
  });

  it('sends the amount as exact minor units with the chosen currency and closes', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '1,500,000.50');

    submit();
    const call = http.expectOne('/api/employees/10432/salary-entries');
    expect(call.request.body).toEqual({
      effectiveDate: '2026-04-01',
      amountMinor: 150000050,
      currency: 'INR',
    });
    call.flush({ id: 5 });

    await vi.waitFor(() => expect(close).toHaveBeenCalledWith(true));
  });

  it('rejects decimals for a currency without minor units, even if the amount was typed first', () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '10.50');
    type('currency', 'JPY');
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    http.expectNone('/api/employees/10432/salary-entries');
    expect(errorOf('amount')).toBe('This currency has no decimal places.');
  });

  it('sends one request when the button is clicked twice', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');

    submit();
    submit();

    http.expectOne('/api/employees/10432/salary-entries').flush({ id: 5 });
    await vi.waitFor(() => expect(close).toHaveBeenCalledTimes(1));
  });

  it('shows a duplicate date from the server in a banner and keeps what was typed', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');

    submit();
    http.expectOne('/api/employees/10432/salary-entries').flush(
      { code: 'DUPLICATE_SALARY_DATE', detail: 'There is already a salary effective on 2026-04-01.' },
      { status: 422, statusText: 'Unprocessable Content' },
    );

    await until(() => expect(alertText()).toContain('There is already a salary effective on'));
    expect(page.querySelector<HTMLInputElement>('input[formControlName="amount"]')!.value).toBe(
      '100',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('puts a server error about the amount under the amount field', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');

    submit();
    http.expectOne('/api/employees/10432/salary-entries').flush(
      { errors: [{ field: 'amountMinor', message: 'must be greater than or equal to 1' }] },
      { status: 400, statusText: 'Bad Request' },
    );

    await until(() => expect(errorOf('amount')).toBe('must be greater than or equal to 1'));
  });

  it('enables the button again after a failed save', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');
    const button = page.querySelector<HTMLButtonElement>('button[type="submit"]')!;

    submit();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    http.expectOne('/api/employees/10432/salary-entries').error(new ProgressEvent('error'));

    await until(() => {
      expect(button.disabled).toBe(false);
      expect(alertText()).toContain('Could not save');
    });
  });
});
```

- [ ] **Step 3: Run and watch it fail**

Run: `npm run test -- --include src/app/features/employee-detail/add-salary-dialog/add-salary-dialog.spec.ts`. Expected: FAIL (module missing).

- [ ] **Step 4: Implement**

`add-salary-dialog.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormErrors, toFormErrors } from '../../../core/api/to-form-errors';
import { EmployeeApi } from '../../../core/employees/employee-api';
import { Currency } from '../../../core/reference/reference-api';
import { AmountError, parseMajorToMinor } from '../../../shared/money/parse-major-to-minor';
import {
  describeAmountError,
  salaryAmountValidator,
} from '../../../shared/money/salary-amount.validator';

export interface AddSalaryDialogData {
  employeeId: number;
  currencies: Currency[];
  defaultCurrency: string;
}

type ControlName = 'effectiveDate' | 'amount' | 'currency';

/** The API names a field by its request path. This maps it to the control that shows its error. */
const SERVER_FIELD_TO_CONTROL: Readonly<Record<string, ControlName | undefined>> = {
  effectiveDate: 'effectiveDate',
  amountMinor: 'amount',
  currency: 'currency',
};

@Component({
  selector: 'app-add-salary-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './add-salary-dialog.html',
  styleUrl: './add-salary-dialog.scss',
})
export class AddSalaryDialog {
  private readonly employeeApi = inject(EmployeeApi);
  private readonly dialogRef = inject(MatDialogRef<AddSalaryDialog, boolean>);
  private readonly data = inject<AddSalaryDialogData>(MAT_DIALOG_DATA);
  private readonly selectedCurrencyCode = signal(this.data.defaultCurrency);

  protected readonly currencies = this.data.currencies;
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  private readonly minorUnitDigits = computed(
    () =>
      this.data.currencies.find((currency) => currency.code === this.selectedCurrencyCode())
        ?.minorUnitDigits ?? null,
  );
  protected readonly form = inject(NonNullableFormBuilder).group({
    effectiveDate: ['', Validators.required],
    amount: ['', [Validators.required, salaryAmountValidator(() => this.minorUnitDigits())]],
    currency: [this.data.defaultCurrency, Validators.required],
  });

  constructor() {
    // The decimals an amount may have depend on the currency, so choosing one re-checks the amount.
    this.form.controls.currency.valueChanges.pipe(takeUntilDestroyed()).subscribe((code) => {
      this.selectedCurrencyCode.set(code);
      this.form.controls.amount.updateValueAndValidity();
    });
  }

  protected errorMessage(name: ControlName): string | null {
    const errors = this.form.controls[name].errors;
    if (!errors) {
      return null;
    }
    if (errors['server']) {
      return String(errors['server']);
    }
    if (errors['required']) {
      return 'This field is required.';
    }
    if (errors['amount']) {
      return describeAmountError(errors['amount'] as AmountError, this.minorUnitDigits());
    }
    return 'This value is not valid.';
  }

  protected async addSalary(): Promise<void> {
    // The button is disabled while saving, but a second click can arrive before the page redraws.
    if (this.submitting()) {
      return;
    }
    const value = this.form.getRawValue();
    const digits = this.minorUnitDigits();
    const amount = digits === null ? null : parseMajorToMinor(value.amount, digits);
    if (!this.form.valid || amount === null || !amount.ok) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    try {
      await this.employeeApi.addSalaryEntry(this.data.employeeId, {
        effectiveDate: value.effectiveDate,
        amountMinor: amount.amountMinor,
        currency: value.currency,
      });
      this.dialogRef.close(true);
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.showServerErrors(toFormErrors(error));
    } finally {
      this.submitting.set(false);
    }
  }

  /** A field with no control here has nowhere to show its message, so it goes in the banner. */
  private showServerErrors(errors: FormErrors): void {
    const unplaced: string[] = errors.formError ? [errors.formError] : [];
    for (const [field, message] of Object.entries(errors.fieldErrors)) {
      const controlName = SERVER_FIELD_TO_CONTROL[field];
      if (controlName === undefined) {
        unplaced.push(message);
        continue;
      }
      this.form.controls[controlName].setErrors({ server: message });
      this.form.controls[controlName].markAsTouched();
    }
    this.formError.set(unplaced.length > 0 ? unplaced.join(' ') : null);
  }
}
```

`add-salary-dialog.html`:

```html
<!-- The labels and messages are hard-coded in English for now. For localization they should come from one central place, such as Angular's i18n. -->
<form [formGroup]="form" (ngSubmit)="addSalary()" novalidate>
  <h2 mat-dialog-title>Add salary</h2>
  <mat-dialog-content>
    <mat-form-field appearance="outline">
      <mat-label>Effective date</mat-label>
      <input matInput type="date" formControlName="effectiveDate" />
      <mat-error>{{ errorMessage('effectiveDate') }}</mat-error>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Currency</mat-label>
      <select matNativeControl formControlName="currency">
        @for (currency of currencies; track currency.code) {
          <option [value]="currency.code">{{ currency.code }}</option>
        }
      </select>
      <mat-error>{{ errorMessage('currency') }}</mat-error>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>Annual base salary</mat-label>
      <input matInput formControlName="amount" inputmode="decimal" />
      <mat-hint>For example 1,500,000.50</mat-hint>
      <mat-error>{{ errorMessage('amount') }}</mat-error>
    </mat-form-field>

    @if (formError(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
  </mat-dialog-content>
  <mat-dialog-actions align="end">
    <button mat-button type="button" mat-dialog-close>Cancel</button>
    <button mat-flat-button type="submit" [disabled]="submitting()">
      {{ submitting() ? 'Saving…' : 'Add salary' }}
    </button>
  </mat-dialog-actions>
</form>
```

`add-salary-dialog.scss`:

```scss
mat-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding-top: 0.5rem;
}

.error {
  color: var(--mat-sys-error);
}
```

- [ ] **Step 5: Run and watch it pass, then prove the tests**

Expected: 8 PASS (the page is in the dialog component, so jsdom queries work on the component itself). Prove, each applied through a script that asserts the target text exists, then restored:
1. Remove the `if (this.submitting()) { return; }` guard: `sends one request when the button is clicked twice` FAILS.
2. Remove the `updateValueAndValidity()` line in the constructor: `rejects decimals for a currency without minor units...` FAILS.
3. In `SERVER_FIELD_TO_CONTROL`, map `amountMinor` to `'effectiveDate'`: `puts a server error about the amount under the amount field` FAILS.
4. Replace `currency: value.currency` in the request with `currency: 'USD'`: `sends the amount as exact minor units...` FAILS.
5. Replace `this.dialogRef.close(true)` with `this.dialogRef.close(false)`: the same test FAILS.
6. Add `this.form.reset();` before `this.showServerErrors(...)`: `shows a duplicate date from the server in a banner and keeps what was typed` FAILS.

- [ ] **Step 6: Gates and stage**

Run `npm run format:write && npm run format:check && npm run lint && npm run test && npm run typecheck && npm run knip && npm run build`, all exit 0. Knip may flag `AddSalaryDialogData` or the moved validator's exports: fix by un-exporting what nothing imports.

```bash
git add frontend/src/app/shared/money frontend/src/app/features/employee-form/employee-form.ts frontend/src/app/features/employee-detail/add-salary-dialog
```

Suggested message: `feat(ui): add the Add salary dialog and move the amount validator to shared`

---

### Task 10: Show the history and the dialog on the detail page

**Files:**
- Modify: `frontend/src/app/features/employee-detail/employee-detail.{ts,html}`
- Test: `frontend/src/app/features/employee-detail/employee-detail.spec.ts`

**Interfaces:**
- Consumes: `SalaryHistory` (Task 8), `AddSalaryDialog` and `AddSalaryDialogData` (Task 9), `EmployeeApi.listSalaryEntries` (Task 7), `MatDialog`.
- Produces: the detail page loads the employee, the currencies and the history together, shows the history table, and has an "Add salary" button that opens the dialog. After the dialog closes with `true`, the page re-reads all three.

- [ ] **Step 1: Update the specs first (they fail until the page loads the history)**

In `employee-detail.spec.ts`:

- Add a constant and extend `answer`:

```ts
const ENTRIES = [
  {
    id: 1,
    effectiveDate: '2024-04-01',
    amountMinor: 125000050,
    currency: 'INR',
    endDate: null,
    current: true,
    scheduled: false,
  },
];
```

```ts
  function answer(employee: object, entries: object[] = ENTRIES): void {
    http.expectOne('/api/employees/10432').flush(employee);
    http.expectOne('/api/reference/currencies').flush([{ code: 'INR', minorUnitDigits: 2 }]);
    http.expectOne('/api/employees/10432/salary-entries').flush(entries);
  }
```

- In the 404 test and the failing-load test (which flush the employee and currencies by hand), also flush `/api/employees/10432/salary-entries` with `[]`, because `http.verify()` fails on any request left open. For example in the 404 test:

```ts
    http.expectOne('/api/reference/currencies').flush([]);
    http.expectOne('/api/employees/10432/salary-entries').flush([]);
    http.expectOne('/api/employees/10432').flush(null, { status: 404, statusText: 'Not Found' });
```

- Add the new tests (import `MatDialog`, `of` from `rxjs`, and `MatDialogRef`):

```ts
  it('shows the salary history under the profile', async () => {
    answer(EMPLOYEE);

    await until(() => {
      expect(page.querySelectorAll('tr[mat-row]').length).toBe(1);
      expect(page.querySelector('tr[mat-row]')?.textContent).toContain('Current');
    });
  });

  it('opens the add salary dialog with the employee, the currencies and the current currency', async () => {
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(false) } as MatDialogRef<unknown, boolean>);
    answer(EMPLOYEE);
    await until(() => expect(page.textContent).toContain('Asha Rao'));

    page.querySelector<HTMLButtonElement>('button[data-test="add-salary"]')!.click();

    expect(open).toHaveBeenCalledTimes(1);
    expect(open.mock.calls[0][1]?.data).toEqual({
      employeeId: 10432,
      currencies: [{ code: 'INR', minorUnitDigits: 2 }],
      defaultCurrency: 'INR',
    });
  });

  it('reads the employee and the history again after a salary was added', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as MatDialogRef<unknown, boolean>);
    answer(EMPLOYEE);
    await until(() => expect(page.textContent).toContain('Asha Rao'));

    page.querySelector<HTMLButtonElement>('button[data-test="add-salary"]')!.click();

    // The reads start after the dialog promise resolves, so wait for the requests to appear.
    await vi.waitFor(() => answer(EMPLOYEE));
    await until(() => expect(page.textContent).toContain('Asha Rao'));
  });

  it('does not read again when the dialog was closed without saving', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(false),
    } as MatDialogRef<unknown, boolean>);
    answer(EMPLOYEE);
    await until(() => expect(page.textContent).toContain('Asha Rao'));

    page.querySelector<HTMLButtonElement>('button[data-test="add-salary"]')!.click();
    await new Promise((resolve) => setTimeout(resolve));

    http.expectNone('/api/employees/10432');
  });
```

- [ ] **Step 2: Run and watch them fail**

Run: `npm run test -- --include src/app/features/employee-detail/employee-detail.spec.ts`. Expected: FAIL (the page does not request the history yet, so the unmatched-request and missing-table checks fail).

- [ ] **Step 3: Implement**

In `employee-detail.ts`: import `MatDialog`, `SalaryEntry`, `SalaryHistory`, `AddSalaryDialog` and `AddSalaryDialogData` (from `./add-salary-dialog/add-salary-dialog`), and `firstValueFrom` from `rxjs`; add `SalaryHistory` to `imports`; inject `MatDialog`; change `currencies` from `private` to `protected` so the template can use it; add a signal and the dialog opener; load the history with the rest:

```ts
  private readonly dialog = inject(MatDialog);

  protected readonly entries = signal<SalaryEntry[]>([]);
```

Change `load()` to read the three together:

```ts
      const [employee, currencies, entries] = await Promise.all([
        this.employeeApi.get(id),
        this.referenceApi.loadCurrencies(),
        this.employeeApi.listSalaryEntries(id),
      ]);
      this.currencies.set(currencies);
      this.entries.set(entries);
      this.employee.set(employee);
      this.loadState.set('loaded');
```

and add:

```ts
  protected async openAddSalary(): Promise<void> {
    const employee = this.employee();
    if (employee === null) {
      return;
    }
    const dialogRef = this.dialog.open<AddSalaryDialog, AddSalaryDialogData, boolean>(AddSalaryDialog, {
      data: {
        employeeId: employee.id,
        currencies: this.currencies(),
        defaultCurrency: employee.currentSalary.currency,
      },
    });
    if (await firstValueFrom(dialogRef.afterClosed())) {
      await this.load();
    }
  }
```

In `employee-detail.html`, inside the loaded card area after the `<mat-card>`, add:

```html
        <section class="history-section">
          <h3>Salary history</h3>
          <app-salary-history [entries]="entries()" [currencies]="currencies()" />
          <button mat-flat-button type="button" data-test="add-salary" (click)="openAddSalary()">
            Add salary
          </button>
        </section>
```

and add to `employee-detail.scss`:

```scss
.history-section {
  margin: 1.5rem 0;
}
```

- [ ] **Step 4: Run and watch them pass, then prove the tests**

Expected: all PASS. Prove, each applied through a script that asserts the target text exists, then restored:
1. Remove `this.employeeApi.listSalaryEntries(id)` from the `Promise.all` (and its destructured name): the history test FAILS.
2. In `openAddSalary`, change `defaultCurrency: employee.currentSalary.currency` to `'JPY'`: the "opens the dialog" test FAILS.
3. Change `if (await firstValueFrom(dialogRef.afterClosed()))` to `if (!(await firstValueFrom(dialogRef.afterClosed())))`: the "reads again" test and the "does not read again" test FAIL.
4. Remove the `await this.load();` line: the "reads again" test FAILS.

- [ ] **Step 5: Gates and stage**

Run the frontend gates (format, lint, test, typecheck, knip, build), all exit 0.

```bash
git add frontend/src/app/features/employee-detail
```

Suggested message: `feat(ui): show the salary history and an Add salary dialog on the employee detail page`

---

### Task 11: Docs, full verification and a real run

**Files:**
- Modify: `docs/design.md`, `docs/ai-usage.md`

- [ ] **Step 1: Record the decisions in the design**

In `docs/design.md`, under "Domain decisions", add rows after row 12:

```
| 13 | A salary entry's end date is the **last day of its period**, the day before the next entry starts, and empty for the newest. `current` is the latest entry on or before today and `scheduled` is any entry after today, all worked out on the server from the injected clock. | One definition, matching the termination date meaning "last employed day". The browser's clock never decides which entry is current. |
| 14 | A second salary entry on an existing date is refused by the timeline validator under the employee row lock, with the unique index as a backstop. | The lock admits one writer at a time, so the check cannot be raced, and every timeline invariant stays in one class. |
| 15 | A salary entry added without a currency takes the currency of the entry immediately before its date. If there is none, the currency is required. | The default follows the timeline, and gives a sensible answer for a backdated entry. |
```

In the slice plan table, in the slice 7 row, append to the Backend cell: `, and add the change-log write to adding an entry`.

- [ ] **Step 2: Add this slice to the AI usage log**

Append a "Session 4: slice 3, salary history" entry to `docs/ai-usage.md`, in the file's format and in the owner's first-person voice, from what actually happened during the build (the decisions, what the AI got wrong, what was verified). Write it at the end of the work, not before.

- [ ] **Step 3: Full verification**

Run: `make check`. Expected: exit 0.

- [ ] **Step 4: Run it for real**

With the owner's Postgres and API running (the API port may be 8081) and `ng serve`, drive the real screens in a headless browser from a throwaway script outside the repo, using in-app navigation (a page reload drops the in-memory login). Check: the history table appears on a detail page; adding a same-currency raise shows it as Current and ends the earlier row the day before; adding a future-dated raise shows Scheduled and leaves the earlier row Current; a date already used shows the server's message in the dialog; JPY rejects a decimal amount; Cancel adds nothing. Mark test employees with a `real-run-` email prefix, report exactly what was seen, and do not delete rows.

- [ ] **Step 5: A final probing pass for what the tests could miss**

Probe the endpoints with odd input against a real server: a fractional amount, a string amount, an extra unknown field, a date in the year 9999, a very large employee id, a duplicate date sent twice at the same moment over HTTP. Add a test for any defect found, test first.

- [ ] **Step 6: Stage and stop**

```bash
git add docs/design.md docs/ai-usage.md
```

Suggested message: `docs: record the slice 3 decisions and log the session`

---

## Self-review notes

- **Spec coverage.** Decision 1: Tasks 4 and 5. Decision 2: nothing written, recorded in the slice 7 row (Task 11). Decision 3: Task 2 and design row 13. Decision 4: Tasks 1, 3 and 4 and design row 14. Decision 5: Task 3 and design row 15. Decision 6: Tasks 5 and 10. Decision 7: Tasks 9 and 10. Tests in the spec's build order: Tasks 1 to 6 backend, 7 to 10 frontend. Known constraints: the lock is exercised in Task 4, the slice 7 retrofit is recorded in Task 11.
- **Unverified points, each with a fallback in its task:** whether the concurrency test actually races (Task 4 step 6, strengthen it if the no-lock mutation does not fail); whether `MatDialog` content queries work in jsdom for the detail spec (the dialog spec tests the component directly, the detail spec spies on `open`); Knip on the new exports (Tasks 8 and 9).
