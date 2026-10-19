# Slice 2: create and read an employee, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR can create an employee with the first salary entry and open the employee detail, through the API and two Angular screens.

**Architecture:** Package-by-feature modular monolith. `employee` calls `salary` (rules and rows) and `reference` (lookups). The salary rules are one pure class. One `@RestControllerAdvice` in `config` maps exceptions to problem+json. The Angular form converts money with pure string functions and maps server errors with one pure function.

**Tech Stack:** Java 25, Spring Boot 4.1, JPA, Liquibase, PostgreSQL 17 (Testcontainers), JUnit 5 and AssertJ, MockMvc. Angular 22 with Angular Material, Vitest in jsdom.

**Spec:** [2026-10-19-slice-2-employee-create-read-design.md](../specs/2026-10-19-slice-2-employee-create-read-design.md). Parent design: [design.md](../../design.md).

## Deviations from the spec, found while reading the code

Read these first. Each is small, and the behavior in the spec is unchanged.

1. **`reference` types are package-private.** `Department`, `JobTitle`, `Country`, `Currency` and their repositories cannot be imported by `employee`. So `Employee` stores plain ids (`departmentId`, `jobTitleId`, `countryCode`), as `SalaryEntry` stores a plain `employeeId`. `reference` gains one public class, `ReferenceLookup`, that returns the existing public response records. This replaces "lazy `@ManyToOne`" (decision 8) and "`findById` on the four repositories" (decision 5). Foreign keys in the database still protect the data.
2. **`SalaryService` takes `today` as a parameter** in both `recordFirstEntry` and `currentSalary`, instead of reading a `Clock` itself. One request reads the clock once, in `EmployeeService`.
3. **The advice does not reuse `ProblemResponseWriter`** (it is package-private in `auth` and writes to a servlet response). It extends `ResponseEntityExceptionHandler` and returns Spring's `ProblemDetail`, which has the same fields (`type`, `title`, `status`, `detail`) plus `code` and `errors`. This settles the "verify reuse" item in the spec.
4. **The migration file is `009-employee-and-salary-entry.sql`** with changesets `009-create-employee` and `010-create-salary-entry`. Ids 001 to 008 are taken.
5. **The form uses native `<input type="date">` and native `<select matNativeControl>`** inside Material form fields, not the Material datepicker and `mat-select`. A native date input gives the `YYYY-MM-DD` string directly, so there is no timezone conversion, and jsdom can test it. Say so if you prefer the Material datepicker.
6. **Integration tests get a fixed clock.** `@IntegrationTest` imports a fixed `Clock` (2026-10-19), so all integration tests share one Spring context and one PostgreSQL container, and dates are deterministic.
7. **A refinement to the build order.** The money functions come first on the frontend because the form depends on them.

## Layout update (after Task 1, on your feedback)

Feature packages are split into sub-folders, not kept flat. `salary` now has `salary/rules/` (package `com.acme.peoplemanagement.salary.rules`) holding `Money`, `TimelineEntry`, `SalaryRuleCode`, `SalaryRuleViolation` and `SalaryTimelineValidator`, with the test in `src/test/.../salary/rules/`. Everywhere this plan names those classes under `salary/`, read `salary/rules/`, and import them from `com.acme.peoplemanagement.salary.rules` (`SalaryEntry`, `SalaryService` and the employee code need the import). `employee/rules/` (package `com.acme.peoplemanagement.employee.rules`) holds `EmployeeStatus`, whose `on(today, terminationDate)` is public because the service and response record in other folders use it. `employee/api/` holds the request and response records (and the controller in Task 7), `employee/errors/` holds the three exceptions, and `employee/` keeps `Employee`, `EmployeeProfile`, `EmployeeRepository` and `EmployeeService`. `EmployeeService` and its `create` and `get` are public so the controller in `api/` can call them, and the request-to-profile mapping lives in the service so the records stay plain data. In Task 7 the controller and `EmployeeEndpointTest` go in `employee/api/`, and `ApiExceptionHandler` imports the exceptions from `employee.errors`. `reference` stays flat for now. For the remaining tasks, new classes go into sub-folders by role. A class stays package-private only if everything that uses it is in the same folder, so a split that would force a public class is decided in that task and ledgered.

## Global Constraints

- Salary is an integer in minor units. Never a float, in Java or TypeScript. The largest amount is `9007199254740991` (`Money.MAX_AMOUNT_MINOR`). The database check states the same literal.
- Every statistic and amount keeps its own currency. Nothing converts currencies.
- Email is unique ignoring case: the unique index on `lower(email)` is the only judge.
- `status` is `ACTIVE` when the termination date is null or on or after today, otherwise `FORMER`.
- The first salary date is on or after the hire date, and at least one entry is effective on or before today. A future hire date is rejected with `NO_CURRENT_SALARY`.
- Errors are `application/problem+json`. Status codes: 400 invalid input, 404 not found, 409 duplicate email, 422 business rule. Business errors carry a stable `code`. Validation errors carry `errors: [{ field, message }]`.
- Dates are `YYYY-MM-DD`. Fields are camelCase. Every `/api` call needs the shared login.
- Backend gates: `-Werror`, Error Prone, Checkstyle (120 columns, methods up to 25 non-empty lines, up to 5 parameters, no single-letter names), SpotBugs. Constructor injection with `final` fields. Records for values. No interface for a single implementation.
- Frontend gates: Prettier, ESLint, `tsc`, Knip (unused exports fail), Vitest. Standalone components, signals, `inject()`, no NgRx, no barrel files. User text is hard-coded English.
- Test first on core logic, and prove each new test by breaking the code it guards, watching it fail, then restoring.
- The owner makes every commit. A task ends by staging exact files and suggesting a message. Never `git add .` and never commit.
- Before suggesting a commit, review the change against the engineering principles in the root `CLAUDE.md` (YAGNI, KISS, DRY, single responsibility, injected `Clock`) and fix findings.

## Review Focus

Behaviors the spec implies but a task could easily leave untested. Each has a test in the task named in brackets.

1. **A hire date of exactly today with a first salary dated today is accepted** (the boundary of "on or before today"). [Task 1, Task 6]
2. **The same email in a different case is a duplicate** and answers 409, not 500. [Task 3, Task 6]
3. **An amount at the cap (9007199254740991) is accepted end to end, one above is a 400**, and the form shows "too large" for it. [Task 6, Task 7, Task 9]
4. **A malformed JSON body or a non-numeric employee id answers problem+json**, not an HTML error page or a 500. [Task 7]
5. **A double submit sends one request**, and a zero-decimal currency such as JPY rejects "10.5" in the form even when the amount was typed before the currency was chosen. [Task 13]

---

## File structure

Backend, under `backend/src/main/java/com/acme/peoplemanagement/`:

| File | Responsibility |
|---|---|
| `salary/Money.java` | Value: `amountMinor` and `currencyCode`, plus the amount cap constant |
| `salary/TimelineEntry.java` | Value: an effective date and a `Money` |
| `salary/SalaryRuleCode.java` | Enum of rule codes (two values in this slice) |
| `salary/SalaryRuleViolation.java` | Unchecked exception carrying a code and a detail message |
| `salary/SalaryTimelineValidator.java` | The pure rules. No Spring, no database |
| `salary/SalaryEntry.java` | JPA entity for `salary_entry`, plain `employeeId` |
| `salary/SalaryEntryRepository.java` | Spring Data repository, current-salary query |
| `salary/SalaryService.java` | The only salary write path: validate, then save |
| `reference/ReferenceLookup.java` | Public lookups returning the public response records |
| `employee/EmployeeStatus.java` | `ACTIVE` or `FORMER`, from today and the termination date |
| `employee/Employee.java` | JPA entity, plain ids for the references |
| `employee/EmployeeProfile.java` | Record: the fields needed to create an `Employee` |
| `employee/EmployeeRepository.java` | Spring Data repository |
| `employee/EmployeeService.java` | Create and read use cases, transaction owner |
| `employee/EmployeeController.java` | `POST /api/employees`, `GET /api/employees/{id}` |
| `employee/CreateEmployeeRequest.java`, `InitialSalaryRequest.java` | Request records with Bean Validation |
| `employee/EmployeeResponse.java`, `CurrentSalaryResponse.java` | Response records |
| `employee/EmailAlreadyExistsException.java`, `EmployeeNotFoundException.java`, `UnknownReferenceException.java` | Business exceptions |
| `config/ApiExceptionHandler.java` | Exception type to status and problem+json. No business decisions |
| `resources/db/changelog/changes/009-employee-and-salary-entry.sql` | The migration |

Backend tests, under `backend/src/test/java/com/acme/peoplemanagement/`: `salary/SalaryTimelineValidatorTest`, `employee/EmployeeStatusTest`, `employee/EmployeeMigrationIntegrationTest`, `employee/SalaryMigrationIntegrationTest` (in `salary/`), `reference/ReferenceLookupIntegrationTest`, `salary/SalaryServiceIntegrationTest`, `employee/EmployeeServiceIntegrationTest`, `employee/EmployeeEndpointTest` (web slice), `employee/EmployeeEndpointIntegrationTest`, and helpers `support/TestEmployees`, `support/FixedClockConfiguration`.

Frontend, under `frontend/src/app/`:

| File | Responsibility |
|---|---|
| `shared/money/parse-major-to-minor.ts` | Text to minor units, exact, no floats |
| `shared/money/format-minor.ts` | Minor units to text, exact |
| `core/api/to-form-errors.ts` | `HttpErrorResponse` to field errors and a form banner |
| `core/reference/reference-api.ts` | Loads the four reference lists |
| `core/employees/employee-api.ts` | `create` and `get` for employees |
| `features/employee-detail/employee-detail.{ts,html,scss}` | Detail page |
| `features/employee-form/employee-form.{ts,html,scss}` | Create form |
| `features/employee-form/salary-amount.validator.ts` | Validator and messages for the amount field |
| `features/employee-form/employee-form-fields.ts` | Server field name to form control name |

---

### Task 1: The salary timeline rules (pure Java, test first)

**Files:**
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/Money.java`
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/TimelineEntry.java`
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/SalaryRuleCode.java`
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/SalaryRuleViolation.java`
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/SalaryTimelineValidator.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/salary/SalaryTimelineValidatorTest.java`

**Interfaces:**
- Produces: `Money(long amountMinor, String currencyCode)` with `Money.MAX_AMOUNT_MINOR`; `TimelineEntry(LocalDate effectiveDate, Money salary)`; `SalaryRuleCode { SALARY_BEFORE_HIRE_DATE, NO_CURRENT_SALARY }`; `SalaryRuleViolation(SalaryRuleCode code, String detail)` with `code()`; `SalaryTimelineValidator.validate(LocalDate hireDate, List<TimelineEntry> timeline, LocalDate today)` which throws `SalaryRuleViolation`.

- [ ] **Step 1: Create the value types and a validator that checks nothing**

`Money.java`:

```java
package com.acme.peoplemanagement.salary;

/** An amount in minor units (the smallest unit of the currency, for example paise) and the currency it is paid in. */
public record Money(long amountMinor, String currencyCode) {

    /**
     * The largest integer that a double and a JavaScript number both hold exactly. The check on
     * salary_entry.amount_minor states the same value.
     */
    public static final long MAX_AMOUNT_MINOR = 9_007_199_254_740_991L;
}
```

`TimelineEntry.java`:

```java
package com.acme.peoplemanagement.salary;

import java.time.LocalDate;

/** One salary on an employee's timeline: what they are paid from the effective date until the next entry. */
public record TimelineEntry(LocalDate effectiveDate, Money salary) {
}
```

`SalaryRuleCode.java`:

```java
package com.acme.peoplemanagement.salary;

/** The salary timeline rules that can be broken. The names are the stable codes the API returns. */
public enum SalaryRuleCode {
    SALARY_BEFORE_HIRE_DATE,
    NO_CURRENT_SALARY
}
```

`SalaryRuleViolation.java`:

```java
package com.acme.peoplemanagement.salary;

/** A salary change would break a timeline rule. The detail is written for the person who made the change. */
public final class SalaryRuleViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final SalaryRuleCode code;

    public SalaryRuleViolation(final SalaryRuleCode code, final String detail) {
        super(detail);
        this.code = code;
    }

    public SalaryRuleCode code() {
        return code;
    }
}
```

`SalaryTimelineValidator.java` (the skeleton accepts everything, so the tests below fail for the right reason):

```java
package com.acme.peoplemanagement.salary;

import java.time.LocalDate;
import java.util.List;

/**
 * The rules a salary timeline must satisfy. Plain Java with no Spring or database, so the tests run in
 * milliseconds. Later slices add one rule and one test each.
 */
public final class SalaryTimelineValidator {

    public void validate(final LocalDate hireDate, final List<TimelineEntry> timeline, final LocalDate today) {
        // Rules arrive one at a time, each driven by a failing test.
    }
}
```

- [ ] **Step 2: Write the first cycle of tests (the hire date rule)**

`SalaryTimelineValidatorTest.java`:

```java
package com.acme.peoplemanagement.salary;

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
```

- [ ] **Step 3: Run the tests and watch the two rejection tests fail**

Run: `cd backend && ./gradlew test --tests '*SalaryTimelineValidatorTest' --console=plain`
Expected: `acceptsAFirstEntryOnTheHireDate` passes. `rejectsAnEntryDatedBeforeTheHireDate` and `rejectsAnEarlierEntryEvenWhenALaterOneIsOnTheHireDate` FAIL with "Expecting code to raise a throwable". If either fails for another reason (a compile error), fix that first.

- [ ] **Step 4: Implement the hire date rule**

Replace the body of `validate` and add the helper in `SalaryTimelineValidator.java`:

```java
    public void validate(final LocalDate hireDate, final List<TimelineEntry> timeline, final LocalDate today) {
        rejectEntriesBeforeHireDate(hireDate, timeline);
    }

    private static void rejectEntriesBeforeHireDate(final LocalDate hireDate, final List<TimelineEntry> timeline) {
        final boolean anyBeforeHireDate = timeline.stream()
                .anyMatch(entry -> entry.effectiveDate().isBefore(hireDate));
        if (anyBeforeHireDate) {
            throw new SalaryRuleViolation(SalaryRuleCode.SALARY_BEFORE_HIRE_DATE,
                    "A salary cannot be effective before the hire date.");
        }
    }
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `cd backend && ./gradlew test --tests '*SalaryTimelineValidatorTest' --console=plain`
Expected: 3 tests PASS.

- [ ] **Step 6: Prove the tests guard the rule**

Temporarily change `isBefore(hireDate)` to `isAfter(hireDate)` in `rejectEntriesBeforeHireDate`. Run the tests. Expected: the two rejection tests FAIL, and `acceptsAFirstEntryOnTheHireDate` still passes (an entry equal to the hire date is neither before nor after). Restore `isBefore`. Run again: PASS.

- [ ] **Step 7: Add the second cycle of tests (the current salary rule)**

Add these tests to `SalaryTimelineValidatorTest` above the helpers:

```java
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
```

- [ ] **Step 8: Run and watch the new rejection tests fail**

Run: `cd backend && ./gradlew test --tests '*SalaryTimelineValidatorTest' --console=plain`
Expected: `rejectsATimelineWhoseOnlyEntryIsInTheFuture`, `rejectsAnEmptyTimeline` and `rejectsAFutureHireDateAndSaysWhy` FAIL. The two `accepts...` tests pass.

- [ ] **Step 9: Implement the current salary rule**

In `SalaryTimelineValidator.java`, add a constant, call the new check in `validate`, and add the helper:

```java
    private static final String NO_CURRENT_SALARY_DETAIL = "There must be a salary effective on or before today, "
            + "so a new employee's hire date and first salary date cannot be in the future.";
```

```java
    public void validate(final LocalDate hireDate, final List<TimelineEntry> timeline, final LocalDate today) {
        rejectEntriesBeforeHireDate(hireDate, timeline);
        requireCurrentSalary(timeline, today);
    }

    private static void requireCurrentSalary(final List<TimelineEntry> timeline, final LocalDate today) {
        final boolean hasCurrentSalary = timeline.stream()
                .anyMatch(entry -> !entry.effectiveDate().isAfter(today));
        if (!hasCurrentSalary) {
            throw new SalaryRuleViolation(SalaryRuleCode.NO_CURRENT_SALARY, NO_CURRENT_SALARY_DETAIL);
        }
    }
```

- [ ] **Step 10: Run and watch everything pass**

Run: `cd backend && ./gradlew test --tests '*SalaryTimelineValidatorTest' --console=plain`
Expected: 8 tests PASS.

- [ ] **Step 11: Prove the second rule's tests**

Temporarily change `!entry.effectiveDate().isAfter(today)` to `entry.effectiveDate().isBefore(today)`. Run. Expected: `acceptsAnEntryDatedToday` FAILS (the boundary: an entry dated today is current). Restore. Then temporarily change `anyMatch` to `allMatch`. Expected: `acceptsAScheduledRaiseWhenAnEarlierEntryIsAlreadyCurrent` FAILS. Restore. Run again: 8 PASS.

- [ ] **Step 12: Run the quality gates**

Run: `cd backend && ./gradlew check -x integrationTest --console=plain --warning-mode=none`
Expected: BUILD SUCCESSFUL. Fix any Checkstyle, Error Prone or SpotBugs finding in the new files.

- [ ] **Step 13: Stage and suggest the commit**

```bash
git add backend/src/main/java/com/acme/peoplemanagement/salary backend/src/test/java/com/acme/peoplemanagement/salary
```

Suggested message: `feat(api): add the salary timeline rules for the hire date and the current salary`

---

### Task 2: The employee status rule

**Files:**
- Create: `backend/src/main/java/com/acme/peoplemanagement/employee/EmployeeStatus.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeStatusTest.java`

**Interfaces:**
- Produces: `public enum EmployeeStatus { ACTIVE, FORMER }` with `static EmployeeStatus on(LocalDate today, LocalDate terminationDate)`. The termination date may be null.

- [ ] **Step 1: Write the failing test and a skeleton that always answers ACTIVE**

`EmployeeStatus.java` (skeleton):

```java
package com.acme.peoplemanagement.employee;

import java.time.LocalDate;

/** Whether an employee still works for ACME. The termination date is the last day they were employed. */
public enum EmployeeStatus {
    ACTIVE,
    FORMER;

    /** The status on a day. A null termination date means the employee has not left. */
    static EmployeeStatus on(final LocalDate today, final LocalDate terminationDate) {
        return ACTIVE;
    }
}
```

`EmployeeStatusTest.java`:

```java
package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EmployeeStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void isActiveWithoutATerminationDate() {
        assertThat(EmployeeStatus.on(TODAY, null)).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void isActiveOnTheLastDayWorked() {
        assertThat(EmployeeStatus.on(TODAY, TODAY)).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void isActiveBeforeAFutureTerminationDate() {
        assertThat(EmployeeStatus.on(TODAY, TODAY.plusDays(1))).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void isFormerTheDayAfterTheTerminationDate() {
        assertThat(EmployeeStatus.on(TODAY, TODAY.minusDays(1))).isEqualTo(EmployeeStatus.FORMER);
    }
}
```

- [ ] **Step 2: Run and watch the last test fail**

Run: `cd backend && ./gradlew test --tests '*EmployeeStatusTest' --console=plain`
Expected: `isFormerTheDayAfterTheTerminationDate` FAILS (expected FORMER, got ACTIVE). The other three pass.

- [ ] **Step 3: Implement**

Replace the body of `on`:

```java
    static EmployeeStatus on(final LocalDate today, final LocalDate terminationDate) {
        final boolean hasLeft = terminationDate != null && terminationDate.isBefore(today);
        return hasLeft ? FORMER : ACTIVE;
    }
```

- [ ] **Step 4: Run and watch all four pass**

Run: `cd backend && ./gradlew test --tests '*EmployeeStatusTest' --console=plain`
Expected: 4 PASS.

- [ ] **Step 5: Prove the boundary test**

Temporarily change `isBefore(today)` to `!terminationDate.isAfter(today)` (that is, `isBefore` or equal). Run. Expected: `isActiveOnTheLastDayWorked` FAILS. Restore, run again: PASS.

- [ ] **Step 6: Make the enum public (the response record uses it) and run the gates**

The enum is `public` (the response record uses it) and `on` stays package-private. Run: `cd backend && ./gradlew check -x integrationTest --console=plain --warning-mode=none`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Stage and suggest the commit**

```bash
git add backend/src/main/java/com/acme/peoplemanagement/employee/EmployeeStatus.java backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeStatusTest.java
```

Suggested message: `feat(api): add the employee status rule, active until the day after the termination date`

---

### Task 3: The migration and its constraints

**Files:**
- Create: `backend/src/main/resources/db/changelog/changes/009-employee-and-salary-entry.sql`
- Create: `backend/src/test/java/com/acme/peoplemanagement/support/TestEmployees.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeMigrationIntegrationTest.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/salary/SalaryMigrationIntegrationTest.java`

**Interfaces:**
- Produces: tables `employee` and `salary_entry`. Test helper `TestEmployees(JdbcTemplate)` with `long insert(String email, LocalDate hireDate)`, `long insert(String fullName, String email, LocalDate hireDate, LocalDate terminationDate)`, `void insertSalary(long employeeId, LocalDate effectiveDate, long amountMinor, String currencyCode)` and `void deleteAll()`. Employees are created in department `min(id)`, job title `min(id)` and country `IN`.

Docker must be running for the integration tests.

- [ ] **Step 1: Create the test helper**

`TestEmployees.java`:

```java
package com.acme.peoplemanagement.support;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts employee and salary rows directly, so database tests do not depend on the code under test. */
public final class TestEmployees {

    private final JdbcTemplate jdbcTemplate;

    public TestEmployees(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(final String email, final LocalDate hireDate) {
        return insert("Test Person", email, hireDate, null);
    }

    public long insert(
            final String fullName, final String email, final LocalDate hireDate, final LocalDate terminationDate) {
        final Long id = jdbcTemplate.queryForObject("""
                insert into employee (full_name, email, hire_date, termination_date,
                                      department_id, job_title_id, country_code)
                values (?, ?, ?, cast(? as date),
                        (select min(id) from department), (select min(id) from job_title), 'IN')
                returning id""", Long.class, fullName, email, hireDate, terminationDate);
        return Objects.requireNonNull(id);
    }

    public void insertSalary(
            final long employeeId, final LocalDate effectiveDate, final long amountMinor, final String currencyCode) {
        jdbcTemplate.update("""
                insert into salary_entry (employee_id, effective_date, amount_minor, currency_code)
                values (?, ?, ?, ?)""", employeeId, effectiveDate, amountMinor, currencyCode);
    }

    /** Salary entries go with their employees, through the delete cascade. */
    public void deleteAll() {
        jdbcTemplate.update("delete from employee");
    }
}
```

- [ ] **Step 2: Write the failing employee constraint tests**

`employee/EmployeeMigrationIntegrationTest.java`:

```java
package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeMigrationIntegrationTest {

    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;

    @BeforeEach
    void createHelper() {
        employees = new TestEmployees(jdbcTemplate);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void rejectsADuplicateEmailEvenWhenOnlyTheCaseDiffers() {
        employees.insert("asha@acme.example", HIRE_DATE);

        assertThatThrownBy(() -> employees.insert("ASHA@acme.example", HIRE_DATE))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("employee_email_unique");
    }

    @Test
    void rejectsABlankFullName() {
        assertThatThrownBy(() -> employees.insert("   ", "blank@acme.example", HIRE_DATE, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsATerminationDateBeforeTheHireDate() {
        assertThatThrownBy(() -> employees.insert("Left Early", "early@acme.example", HIRE_DATE,
                HIRE_DATE.minusDays(1))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsATerminationDateOnTheHireDate() {
        assertThatCode(() -> employees.insert("One Day", "oneday@acme.example", HIRE_DATE, HIRE_DATE))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsADepartmentThatDoesNotExist() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into employee (full_name, email, hire_date, department_id, job_title_id, country_code)
                values ('No Department', 'nodept@acme.example', ?, -1,
                        (select min(id) from job_title), 'IN')""", HIRE_DATE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void indexesTheColumnsTheDirectoryAndInsightsFilterOn() {
        final List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where tablename = 'employee'", String.class);

        assertThat(indexes).contains("employee_country_idx", "employee_department_idx", "employee_job_title_idx");
    }
}
```

- [ ] **Step 3: Write the failing salary constraint tests**

`salary/SalaryMigrationIntegrationTest.java`:

```java
package com.acme.peoplemanagement.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SalaryMigrationIntegrationTest {

    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);
    private static final long LARGEST_EXACT_INTEGER = 9_007_199_254_740_991L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("salary@acme.example", HIRE_DATE);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void rejectsASecondEntryOnTheSameDate() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");

        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, 200L, "INR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnAmountOfZero() {
        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, 0L, "INR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsTheLargestAmountAJavaScriptNumberHoldsExactly() {
        assertThatCode(() -> employees.insertSalary(employeeId, HIRE_DATE, LARGEST_EXACT_INTEGER, "INR"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOneMoreThanTheLargestExactAmount() {
        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, LARGEST_EXACT_INTEGER + 1, "INR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsACurrencyThatDoesNotExist() {
        assertThatThrownBy(() -> employees.insertSalary(employeeId, HIRE_DATE, 100L, "XXX"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removesTheEntriesOfADeletedEmployeeAndLeavesOtherEmployeesUntouched() {
        final long otherEmployeeId = employees.insert("other@acme.example", HIRE_DATE);
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(otherEmployeeId, HIRE_DATE, 200L, "USD");

        jdbcTemplate.update("delete from employee where id = ?", employeeId);

        assertThat(entryCountOf(employeeId)).isZero();
        assertThat(entryCountOf(otherEmployeeId)).isEqualTo(1);
    }

    private int entryCountOf(final long id) {
        final Integer count = jdbcTemplate.queryForObject(
                "select count(*) from salary_entry where employee_id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }
}
```

- [ ] **Step 4: Run and watch them fail**

Run: `cd backend && ./gradlew integrationTest --tests '*MigrationIntegrationTest' --console=plain`
Expected: the new tests FAIL with `relation "employee" does not exist`. The existing reference migration tests still pass.

- [ ] **Step 5: Write the migration**

`009-employee-and-salary-entry.sql`:

```sql
--liquibase formatted sql

--changeset acme:009-create-employee
CREATE TABLE employee (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name        VARCHAR(200) NOT NULL,
    email            VARCHAR(254) NOT NULL,
    hire_date        DATE         NOT NULL,
    termination_date DATE,
    department_id    INTEGER      NOT NULL REFERENCES department (id),
    job_title_id     INTEGER      NOT NULL REFERENCES job_title (id),
    country_code     VARCHAR(2)   NOT NULL REFERENCES country (code),
    -- The optimistic-lock column. Hibernate increments it on every profile update.
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT employee_full_name_is_not_blank CHECK (length(trim(full_name)) > 0),
    CONSTRAINT employee_email_is_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT employee_termination_not_before_hire CHECK (termination_date >= hire_date)
);
-- One email per employee, whatever the case. The service relies on this index's name to report a duplicate.
CREATE UNIQUE INDEX employee_email_unique ON employee (lower(email));
-- The directory and the insights filter and group on these three.
CREATE INDEX employee_country_idx ON employee (country_code);
CREATE INDEX employee_department_idx ON employee (department_id);
CREATE INDEX employee_job_title_idx ON employee (job_title_id);
--rollback DROP TABLE employee;

--changeset acme:010-create-salary-entry
CREATE TABLE salary_entry (
    id             BIGINT     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id    BIGINT     NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    effective_date DATE       NOT NULL,
    -- Annual base pay in minor units. No end date: it is derived from the next entry.
    amount_minor   BIGINT     NOT NULL,
    currency_code  VARCHAR(3) NOT NULL REFERENCES currency (code),
    CONSTRAINT salary_entry_one_per_employee_and_date UNIQUE (employee_id, effective_date),
    -- 9007199254740991 is the largest integer that a double and a JavaScript number both hold exactly.
    -- Money.MAX_AMOUNT_MINOR states the same value.
    CONSTRAINT salary_entry_amount_in_range CHECK (amount_minor BETWEEN 1 AND 9007199254740991)
);
--rollback DROP TABLE salary_entry;
```

The unique constraint on `(employee_id, effective_date)` also serves lookups by employee and date.

- [ ] **Step 6: Run and watch them pass**

Run: `cd backend && ./gradlew integrationTest --console=plain`
Expected: all integration tests PASS, including the earlier ones.

- [ ] **Step 7: Prove two of the constraints**

Temporarily change `lower(email)` to `email` in the unique index. Run the migration tests. Expected: `rejectsADuplicateEmailEvenWhenOnlyTheCaseDiffers` FAILS. Restore. Then temporarily change `>= hire_date` to `> hire_date`. Expected: `acceptsATerminationDateOnTheHireDate` FAILS. Restore. Note: this edits a migration that has not been committed or run in any shared database, which is allowed. A migration that has been committed is never edited. Run the full set again: PASS. Because Testcontainers starts a fresh database, no cleanup is needed.

- [ ] **Step 8: Stage and suggest the commit**

```bash
git add backend/src/main/resources/db/changelog/changes/009-employee-and-salary-entry.sql backend/src/test/java/com/acme/peoplemanagement/support/TestEmployees.java backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeMigrationIntegrationTest.java backend/src/test/java/com/acme/peoplemanagement/salary/SalaryMigrationIntegrationTest.java
```

Suggested message: `feat(api): add the employee and salary entry tables with their constraints`

---

### Task 4: A public lookup for the reference data

**Files:**
- Create: `backend/src/main/java/com/acme/peoplemanagement/reference/ReferenceLookup.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/reference/ReferenceLookupIntegrationTest.java`

**Interfaces:**
- Consumes: the package-private repositories in `reference` and the public records `DepartmentResponse(int id, String name)`, `JobTitleResponse(int id, String name)`, `CountryResponse(String code, String name)`, `CurrencyResponse(String code, int minorUnitDigits)`.
- Produces: `@Service public class ReferenceLookup` with `Optional<DepartmentResponse> department(int id)`, `Optional<JobTitleResponse> jobTitle(int id)`, `Optional<CountryResponse> country(String code)`, `Optional<CurrencyResponse> currency(String code)`.

- [ ] **Step 1: Write the failing test**

`ReferenceLookupIntegrationTest.java`:

```java
package com.acme.peoplemanagement.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ReferenceLookupIntegrationTest {

    @Autowired
    private ReferenceLookup lookup;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsADepartmentById() {
        final int id = idOf("department", "Engineering");

        assertThat(lookup.department(id)).contains(new DepartmentResponse(id, "Engineering"));
    }

    @Test
    void findsAJobTitleById() {
        final int id = idOf("job_title", "Software Engineer");

        assertThat(lookup.jobTitle(id)).contains(new JobTitleResponse(id, "Software Engineer"));
    }

    @Test
    void findsACountryByCode() {
        assertThat(lookup.country("IN")).contains(new CountryResponse("IN", "India"));
    }

    @Test
    void findsACurrencyWithItsMinorUnitDigits() {
        assertThat(lookup.currency("JPY")).contains(new CurrencyResponse("JPY", 0));
    }

    @Test
    void answersEmptyForValuesThatDoNotExist() {
        assertThat(lookup.department(-1)).isEmpty();
        assertThat(lookup.jobTitle(-1)).isEmpty();
        assertThat(lookup.country("ZZ")).isEmpty();
        assertThat(lookup.currency("ZZZ")).isEmpty();
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }
}
```

The table name in `idOf` is a fixed string from this test, not user input.

- [ ] **Step 2: Run and watch it fail to compile**

Run: `cd backend && ./gradlew integrationTest --tests '*ReferenceLookupIntegrationTest' --console=plain`
Expected: FAIL, `cannot find symbol ReferenceLookup`.

- [ ] **Step 3: Implement**

`ReferenceLookup.java`:

```java
package com.acme.peoplemanagement.reference;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The way other features read reference data. The entities and repositories stay private to this package,
 * so a feature can never change a reference row or depend on how it is stored.
 */
@Service
public class ReferenceLookup {

    private final CurrencyRepository currencyRepository;
    private final CountryRepository countryRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;

    ReferenceLookup(
            final CurrencyRepository currencyRepository,
            final CountryRepository countryRepository,
            final DepartmentRepository departmentRepository,
            final JobTitleRepository jobTitleRepository) {
        this.currencyRepository = currencyRepository;
        this.countryRepository = countryRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
    }

    public Optional<DepartmentResponse> department(final int id) {
        return departmentRepository.findById(id)
                .map(department -> new DepartmentResponse(department.id(), department.name()));
    }

    public Optional<JobTitleResponse> jobTitle(final int id) {
        return jobTitleRepository.findById(id)
                .map(jobTitle -> new JobTitleResponse(jobTitle.id(), jobTitle.name()));
    }

    public Optional<CountryResponse> country(final String code) {
        return countryRepository.findById(code)
                .map(country -> new CountryResponse(country.code(), country.name()));
    }

    public Optional<CurrencyResponse> currency(final String code) {
        return currencyRepository.findById(code)
                .map(currency -> new CurrencyResponse(currency.code(), currency.minorUnitDigits()));
    }
}
```

`ReferenceController` still builds its list responses itself. It maps whole sorted lists, a different query from a single lookup, so it is left alone. The only shared piece is the one-line record construction.

- [ ] **Step 4: Run and watch it pass**

Run: `cd backend && ./gradlew integrationTest --tests '*ReferenceLookupIntegrationTest' --console=plain`
Expected: 5 PASS.

- [ ] **Step 5: Prove a test**

Temporarily change `country(code)` to use `findById("IN")`. Run. Expected: `answersEmptyForValuesThatDoNotExist` FAILS. Restore. Run: PASS.

- [ ] **Step 6: Run the gates and suggest the commit**

Run: `cd backend && ./gradlew check -x integrationTest --console=plain --warning-mode=none`
Expected: BUILD SUCCESSFUL.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/reference/ReferenceLookup.java backend/src/test/java/com/acme/peoplemanagement/reference/ReferenceLookupIntegrationTest.java
```

Suggested message: `feat(api): add a public lookup so other features can read reference data`

---

### Task 5: Salary entries and the salary service

**Files:**
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/SalaryEntry.java`
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/SalaryEntryRepository.java`
- Create: `backend/src/main/java/com/acme/peoplemanagement/salary/SalaryService.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/salary/SalaryServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `SalaryTimelineValidator`, `TimelineEntry`, `Money` (Task 1), `TestEmployees` (Task 3).
- Produces: `@Service public class SalaryService` with `void recordFirstEntry(long employeeId, LocalDate hireDate, TimelineEntry entry, LocalDate today)` (throws `SalaryRuleViolation`) and `Optional<TimelineEntry> currentSalary(long employeeId, LocalDate today)`.

- [ ] **Step 1: Write the failing test**

`SalaryServiceIntegrationTest.java`:

```java
package com.acme.peoplemanagement.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SalaryServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

    @Autowired
    private SalaryService salaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private long employeeId;

    @BeforeEach
    void createEmployee() {
        employees = new TestEmployees(jdbcTemplate);
        employeeId = employees.insert("salary-service@acme.example", HIRE_DATE);
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void recordsTheFirstEntryAndReportsItAsTheCurrentSalary() {
        final TimelineEntry first = entry(HIRE_DATE, 125_000_000L, "INR");

        salaryService.recordFirstEntry(employeeId, HIRE_DATE, first, TODAY);

        assertThat(salaryService.currentSalary(employeeId, TODAY)).contains(first);
    }

    @Test
    void reportsTheLatestEntryOnOrBeforeTheGivenDate() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2025, 4, 1), 120L, "INR");

        assertThat(salaryService.currentSalary(employeeId, LocalDate.of(2025, 3, 31)))
                .contains(entry(HIRE_DATE, 100L, "INR"));
        assertThat(salaryService.currentSalary(employeeId, LocalDate.of(2025, 4, 1)))
                .contains(entry(LocalDate.of(2025, 4, 1), 120L, "INR"));
    }

    @Test
    void ignoresAnEntryDatedInTheFuture() {
        employees.insertSalary(employeeId, TODAY.plusDays(1), 100L, "INR");

        assertThat(salaryService.currentSalary(employeeId, TODAY)).isEmpty();
    }

    @Test
    void keepsEachEntryInItsOwnCurrency() {
        employees.insertSalary(employeeId, HIRE_DATE, 100L, "INR");
        employees.insertSalary(employeeId, LocalDate.of(2025, 4, 1), 5_000L, "USD");

        assertThat(salaryService.currentSalary(employeeId, TODAY))
                .contains(entry(LocalDate.of(2025, 4, 1), 5_000L, "USD"));
    }

    @Test
    void savesNothingWhenTheTimelineBreaksARule() {
        final TimelineEntry beforeHire = entry(HIRE_DATE.minusDays(1), 100L, "INR");

        assertThatThrownBy(() -> salaryService.recordFirstEntry(employeeId, HIRE_DATE, beforeHire, TODAY))
                .isInstanceOf(SalaryRuleViolation.class);

        assertThat(salaryService.currentSalary(employeeId, TODAY)).isEmpty();
    }

    private static TimelineEntry entry(final LocalDate effectiveDate, final long amountMinor, final String currency) {
        return new TimelineEntry(effectiveDate, new Money(amountMinor, currency));
    }
}
```

- [ ] **Step 2: Run and watch it fail to compile**

Run: `cd backend && ./gradlew integrationTest --tests '*SalaryServiceIntegrationTest' --console=plain`
Expected: FAIL, `cannot find symbol SalaryService`.

- [ ] **Step 3: Implement the entity, repository and service**

`SalaryEntry.java`:

```java
package com.acme.peoplemanagement.salary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One salary on an employee's timeline. It refers to its employee by id only, so this package never
 * depends on the employee feature.
 */
@Entity
@Table(name = "salary_entry")
class SalaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "employee_id")
    private long employeeId;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "amount_minor")
    private long amountMinor;

    @Column(name = "currency_code")
    private String currencyCode;

    /** For JPA. */
    protected SalaryEntry() {
    }

    SalaryEntry(final long employeeId, final TimelineEntry entry) {
        this.employeeId = employeeId;
        this.effectiveDate = entry.effectiveDate();
        this.amountMinor = entry.salary().amountMinor();
        this.currencyCode = entry.salary().currencyCode();
    }

    TimelineEntry toTimelineEntry() {
        return new TimelineEntry(effectiveDate, new Money(amountMinor, currencyCode));
    }
}
```

`SalaryEntryRepository.java`:

```java
package com.acme.peoplemanagement.salary;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SalaryEntryRepository extends JpaRepository<SalaryEntry, Long> {

    /** The current salary: the latest entry dated on or before the given day. */
    Optional<SalaryEntry> findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            long employeeId, LocalDate asOf);
}
```

`SalaryService.java`:

```java
package com.acme.peoplemanagement.salary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
}
```

- [ ] **Step 4: Run and watch it pass**

Run: `cd backend && ./gradlew integrationTest --tests '*SalaryServiceIntegrationTest' --console=plain`
Expected: 5 PASS. If Hibernate reports a schema validation error, the entity column types and `009` disagree: fix the entity, not the migration.

- [ ] **Step 5: Prove two tests**

Temporarily change `LessThanEqual` to `LessThan` in the repository method. Expected: the `2025-04-01` assertion in `reportsTheLatestEntryOnOrBeforeTheGivenDate` FAILS. Restore. Then temporarily remove the `validator.validate(...)` line. Expected: `savesNothingWhenTheTimelineBreaksARule` FAILS. Restore. Run: PASS.

- [ ] **Step 6: Run the gates and suggest the commit**

Run: `cd backend && ./gradlew check -x integrationTest --console=plain --warning-mode=none`
Expected: BUILD SUCCESSFUL.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/salary backend/src/test/java/com/acme/peoplemanagement/salary/SalaryServiceIntegrationTest.java
```

Suggested message: `feat(api): add the salary service that checks the rules before saving an entry`

---

### Task 6: Create and read an employee (service layer)

**Files:**
- Create: `backend/src/test/java/com/acme/peoplemanagement/support/FixedClockConfiguration.java`
- Modify: `backend/src/test/java/com/acme/peoplemanagement/support/IntegrationTest.java`
- Create (employee package, main): `Employee.java`, `EmployeeProfile.java`, `EmployeeRepository.java`, `EmployeeService.java`, `CreateEmployeeRequest.java`, `InitialSalaryRequest.java`, `EmployeeResponse.java`, `CurrentSalaryResponse.java`, `EmailAlreadyExistsException.java`, `EmployeeNotFoundException.java`, `UnknownReferenceException.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `SalaryService.recordFirstEntry/currentSalary`, `TimelineEntry`, `Money` (Tasks 1 and 5), `ReferenceLookup` (Task 4), `EmployeeStatus.on` (Task 2), `TestEmployees` (Task 3).
- Produces: `EmployeeService.create(CreateEmployeeRequest) : EmployeeResponse` and `EmployeeService.get(long id) : EmployeeResponse`. Exceptions: `EmailAlreadyExistsException()`, `EmployeeNotFoundException(long id)`, `UnknownReferenceException(String field, String message)` with `field()`. Records: `CreateEmployeeRequest(String fullName, String email, LocalDate hireDate, Integer departmentId, Integer jobTitleId, String countryCode, InitialSalaryRequest initialSalary)`, `InitialSalaryRequest(LocalDate effectiveDate, Long amountMinor, String currency)`, `EmployeeResponse(long id, String fullName, String email, LocalDate hireDate, LocalDate terminationDate, EmployeeStatus status, DepartmentResponse department, JobTitleResponse jobTitle, CountryResponse country, CurrentSalaryResponse currentSalary, long version)`, `CurrentSalaryResponse(LocalDate effectiveDate, long amountMinor, String currency)`.

- [ ] **Step 1: Give the integration tests one fixed clock**

`FixedClockConfiguration.java`:

```java
package com.acme.peoplemanagement.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Every integration test runs on this day, so "today" rules are deterministic. All integration tests
 * share one configuration, so they also share one application context and one PostgreSQL container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
```

In `IntegrationTest.java`, change the import line and annotation so both configurations are imported:

```java
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
```

Run: `cd backend && ./gradlew integrationTest --console=plain`
Expected: all existing integration tests still PASS.

- [ ] **Step 2: Write the failing service tests**

`EmployeeServiceIntegrationTest.java`:

```java
package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.salary.Money;
import com.acme.peoplemanagement.salary.SalaryRuleCode;
import com.acme.peoplemanagement.salary.SalaryRuleViolation;
import com.acme.peoplemanagement.support.FixedClockConfiguration;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeServiceIntegrationTest {

    private static final LocalDate TODAY = FixedClockConfiguration.TODAY;
    private static final LocalDate HIRE_DATE = LocalDate.of(2024, 4, 1);

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestEmployees employees;
    private int departmentId;
    private int jobTitleId;

    @BeforeEach
    void findReferenceIds() {
        employees = new TestEmployees(jdbcTemplate);
        departmentId = idOf("department", "Engineering");
        jobTitleId = idOf("job_title", "Software Engineer");
    }

    @AfterEach
    void removeEmployees() {
        employees.deleteAll();
    }

    @Test
    void createsAnEmployeeWithTheFirstSalaryAndReadsItBack() {
        final EmployeeResponse created =
                employeeService.create(request("asha@acme.example", HIRE_DATE, null, 125_000_000L));

        assertThat(created.fullName()).isEqualTo("Asha Rao");
        assertThat(created.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(created.department().name()).isEqualTo("Engineering");
        assertThat(created.jobTitle().name()).isEqualTo("Software Engineer");
        assertThat(created.country().name()).isEqualTo("India");
        assertThat(created.currentSalary())
                .isEqualTo(new CurrentSalaryResponse(HIRE_DATE, 125_000_000L, "INR"));
        assertThat(created.version()).isZero();
        assertThat(employeeService.get(created.id())).isEqualTo(created);
    }

    @Test
    void usesTheGivenFirstSalaryDateInsteadOfTheHireDate() {
        final LocalDate later = LocalDate.of(2024, 9, 1);

        final EmployeeResponse created =
                employeeService.create(request("later@acme.example", HIRE_DATE, later, 100L));

        assertThat(created.currentSalary().effectiveDate()).isEqualTo(later);
    }

    @Test
    void acceptsAHireDateOfToday() {
        final EmployeeResponse created =
                employeeService.create(request("today@acme.example", TODAY, null, 100L));

        assertThat(created.hireDate()).isEqualTo(TODAY);
        assertThat(created.currentSalary().effectiveDate()).isEqualTo(TODAY);
    }

    @Test
    void acceptsTheLargestAmountTheDatabaseAllows() {
        final EmployeeResponse created = employeeService.create(
                request("cap@acme.example", HIRE_DATE, null, Money.MAX_AMOUNT_MINOR));

        assertThat(created.currentSalary().amountMinor()).isEqualTo(Money.MAX_AMOUNT_MINOR);
    }

    @Test
    void rejectsAnEmailThatDiffersOnlyInCaseFromAnExistingOne() {
        employeeService.create(request("dup@acme.example", HIRE_DATE, null, 100L));

        assertThatThrownBy(() -> employeeService.create(request("DUP@acme.example", HIRE_DATE, null, 100L)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void namesTheFieldWhenAReferenceDoesNotExist() {
        final CreateEmployeeRequest unknownDepartment = new CreateEmployeeRequest("Asha Rao", "unknown@acme.example",
                HIRE_DATE, -1, jobTitleId, "IN", new InitialSalaryRequest(null, 100L, "INR"));

        assertThatThrownBy(() -> employeeService.create(unknownDepartment))
                .isInstanceOfSatisfying(UnknownReferenceException.class,
                        exception -> assertThat(exception.field()).isEqualTo("departmentId"));
    }

    @Test
    void namesTheCurrencyFieldWhenTheCurrencyDoesNotExist() {
        final CreateEmployeeRequest unknownCurrency = new CreateEmployeeRequest("Asha Rao", "currency@acme.example",
                HIRE_DATE, departmentId, jobTitleId, "IN", new InitialSalaryRequest(null, 100L, "ZZZ"));

        assertThatThrownBy(() -> employeeService.create(unknownCurrency))
                .isInstanceOfSatisfying(UnknownReferenceException.class,
                        exception -> assertThat(exception.field()).isEqualTo("initialSalary.currency"));
    }

    @Test
    void leavesNoEmployeeBehindWhenTheFirstSalaryBreaksARule() {
        final LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> employeeService.create(request("future@acme.example", tomorrow, null, 100L)))
                .isInstanceOfSatisfying(SalaryRuleViolation.class,
                        violation -> assertThat(violation.code()).isEqualTo(SalaryRuleCode.NO_CURRENT_SALARY));

        assertThat(employeeCountWithEmail("future@acme.example")).isZero();
    }

    @Test
    void reportsAnEmployeeThatDoesNotExist() {
        assertThatThrownBy(() -> employeeService.get(-1L)).isInstanceOf(EmployeeNotFoundException.class);
    }

    private CreateEmployeeRequest request(
            final String email, final LocalDate hireDate, final LocalDate salaryDate, final long amountMinor) {
        return new CreateEmployeeRequest("  Asha Rao ", email, hireDate, departmentId, jobTitleId, "IN",
                new InitialSalaryRequest(salaryDate, amountMinor, "INR"));
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }

    private int employeeCountWithEmail(final String email) {
        final Integer count = jdbcTemplate.queryForObject(
                "select count(*) from employee where email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }
}
```

Note: the first test asserts the name is stored stripped (`"  Asha Rao "` in, `"Asha Rao"` out).

- [ ] **Step 3: Run and watch it fail to compile**

Run: `cd backend && ./gradlew integrationTest --tests '*EmployeeServiceIntegrationTest' --console=plain`
Expected: FAIL, `cannot find symbol` for the new types.

- [ ] **Step 4: Create the exceptions**

`EmailAlreadyExistsException.java`:

```java
package com.acme.peoplemanagement.employee;

/** Another employee already has this email, whatever the case. */
public final class EmailAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyExistsException() {
        super("An employee with this email already exists.");
    }
}
```

`EmployeeNotFoundException.java`:

```java
package com.acme.peoplemanagement.employee;

/** No employee has the requested id. */
public final class EmployeeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmployeeNotFoundException(final long id) {
        super("No employee has the id " + id + ".");
    }
}
```

`UnknownReferenceException.java`:

```java
package com.acme.peoplemanagement.employee;

/** A request names a department, job title, country or currency that does not exist. */
public final class UnknownReferenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public UnknownReferenceException(final String field, final String message) {
        super(message);
        this.field = field;
    }

    /** The request field to show the message under. */
    public String field() {
        return field;
    }
}
```

- [ ] **Step 5: Create the request and response records**

`InitialSalaryRequest.java`:

```java
package com.acme.peoplemanagement.employee;

import com.acme.peoplemanagement.salary.Money;
import com.acme.peoplemanagement.salary.TimelineEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** The first salary of a new employee. The effective date is optional and defaults to the hire date. */
public record InitialSalaryRequest(
        LocalDate effectiveDate,
        @NotNull @Min(1) @Max(Money.MAX_AMOUNT_MINOR) Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {

    TimelineEntry toTimelineEntry(final LocalDate hireDate) {
        final LocalDate date = effectiveDate == null ? hireDate : effectiveDate;
        return new TimelineEntry(date, new Money(amountMinor, currency));
    }
}
```

`CreateEmployeeRequest.java`:

```java
package com.acme.peoplemanagement.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** What HR enters to create an employee. Whether the referenced rows exist is checked by the service. */
public record CreateEmployeeRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull LocalDate hireDate,
        @NotNull Integer departmentId,
        @NotNull Integer jobTitleId,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @NotNull @Valid InitialSalaryRequest initialSalary) {

    EmployeeProfile toProfile() {
        return new EmployeeProfile(fullName.strip(), email, hireDate, departmentId, jobTitleId, countryCode);
    }
}
```

`CurrentSalaryResponse.java`:

```java
package com.acme.peoplemanagement.employee;

import java.time.LocalDate;

/** The salary an employee is paid today, in the currency of that entry. */
public record CurrentSalaryResponse(LocalDate effectiveDate, long amountMinor, String currency) {
}
```

`EmployeeResponse.java`:

```java
package com.acme.peoplemanagement.employee;

import com.acme.peoplemanagement.reference.CountryResponse;
import com.acme.peoplemanagement.reference.DepartmentResponse;
import com.acme.peoplemanagement.reference.JobTitleResponse;
import java.time.LocalDate;

/** The employee detail. {@code version} goes back on edits and deletes to detect a stale view. */
public record EmployeeResponse(
        long id,
        String fullName,
        String email,
        LocalDate hireDate,
        LocalDate terminationDate,
        EmployeeStatus status,
        DepartmentResponse department,
        JobTitleResponse jobTitle,
        CountryResponse country,
        CurrentSalaryResponse currentSalary,
        long version) {
}
```

- [ ] **Step 6: Create the entity, profile and repository**

`EmployeeProfile.java`:

```java
package com.acme.peoplemanagement.employee;

import java.time.LocalDate;

/** The details that describe a new employee, after the request has been validated. */
record EmployeeProfile(
        String fullName,
        String email,
        LocalDate hireDate,
        int departmentId,
        int jobTitleId,
        String countryCode) {
}
```

`Employee.java`:

```java
package com.acme.peoplemanagement.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * An employee. The department, job title and country are stored as plain keys, because the reference
 * entities are private to their package. The database foreign keys protect them.
 */
@Entity
@Table(name = "employee")
class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "department_id")
    private int departmentId;

    @Column(name = "job_title_id")
    private int jobTitleId;

    @Column(name = "country_code")
    private String countryCode;

    @Version
    @Column(name = "version")
    private long version;

    /** For JPA. */
    protected Employee() {
    }

    Employee(final EmployeeProfile profile) {
        this.fullName = profile.fullName();
        this.email = profile.email();
        this.hireDate = profile.hireDate();
        this.departmentId = profile.departmentId();
        this.jobTitleId = profile.jobTitleId();
        this.countryCode = profile.countryCode();
    }

    long id() {
        return id;
    }

    String fullName() {
        return fullName;
    }

    String email() {
        return email;
    }

    LocalDate hireDate() {
        return hireDate;
    }

    LocalDate terminationDate() {
        return terminationDate;
    }

    int departmentId() {
        return departmentId;
    }

    int jobTitleId() {
        return jobTitleId;
    }

    String countryCode() {
        return countryCode;
    }

    long version() {
        return version;
    }
}
```

`EmployeeRepository.java`:

```java
package com.acme.peoplemanagement.employee;

import org.springframework.data.jpa.repository.JpaRepository;

interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
```

- [ ] **Step 7: Create the service**

`EmployeeService.java`:

```java
package com.acme.peoplemanagement.employee;

import com.acme.peoplemanagement.reference.ReferenceLookup;
import com.acme.peoplemanagement.salary.SalaryService;
import com.acme.peoplemanagement.salary.TimelineEntry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and reads employees. Salary rules are applied by the salary service, in the same transaction. */
@Service
class EmployeeService {

    /** The unique index on lower(email) in migration 009. */
    private static final String EMAIL_UNIQUE_INDEX = "employee_email_unique";

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;
    private final ReferenceLookup referenceLookup;
    private final Clock clock;

    EmployeeService(
            final EmployeeRepository employeeRepository,
            final SalaryService salaryService,
            final ReferenceLookup referenceLookup,
            final Clock clock) {
        this.employeeRepository = employeeRepository;
        this.salaryService = salaryService;
        this.referenceLookup = referenceLookup;
        this.clock = clock;
    }

    @Transactional
    EmployeeResponse create(final CreateEmployeeRequest request) {
        verifyReferencesExist(request);
        final LocalDate today = LocalDate.now(clock);
        final Employee employee = insertEmployee(request);
        final TimelineEntry firstEntry = request.initialSalary().toTimelineEntry(request.hireDate());
        salaryService.recordFirstEntry(employee.id(), request.hireDate(), firstEntry, today);
        return toResponse(employee, today);
    }

    @Transactional(readOnly = true)
    EmployeeResponse get(final long id) {
        final Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return toResponse(employee, LocalDate.now(clock));
    }

    private void verifyReferencesExist(final CreateEmployeeRequest request) {
        requireFound(referenceLookup.department(request.departmentId()), "departmentId", "Unknown department.");
        requireFound(referenceLookup.jobTitle(request.jobTitleId()), "jobTitleId", "Unknown job title.");
        requireFound(referenceLookup.country(request.countryCode()), "countryCode", "Unknown country.");
        requireFound(referenceLookup.currency(request.initialSalary().currency()),
                "initialSalary.currency", "Unknown currency.");
    }

    private static void requireFound(final Optional<?> reference, final String field, final String message) {
        if (reference.isEmpty()) {
            throw new UnknownReferenceException(field, message);
        }
    }

    /** The unique index is the only judge of a duplicate email, so two requests at once cannot both succeed. */
    private Employee insertEmployee(final CreateEmployeeRequest request) {
        try {
            return employeeRepository.saveAndFlush(new Employee(request.toProfile()));
        } catch (DataIntegrityViolationException exception) {
            if (violatesEmailIndex(exception)) {
                throw new EmailAlreadyExistsException();
            }
            throw exception;
        }
    }

    private static boolean violatesEmailIndex(final DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                && EMAIL_UNIQUE_INDEX.equals(violation.getConstraintName());
    }

    private EmployeeResponse toResponse(final Employee employee, final LocalDate today) {
        return new EmployeeResponse(
                employee.id(),
                employee.fullName(),
                employee.email(),
                employee.hireDate(),
                employee.terminationDate(),
                EmployeeStatus.on(today, employee.terminationDate()),
                stored(referenceLookup.department(employee.departmentId()), "department", employee),
                stored(referenceLookup.jobTitle(employee.jobTitleId()), "job title", employee),
                stored(referenceLookup.country(employee.countryCode()), "country", employee),
                currentSalaryOf(employee, today),
                employee.version());
    }

    private CurrentSalaryResponse currentSalaryOf(final Employee employee, final LocalDate today) {
        final TimelineEntry entry = stored(salaryService.currentSalary(employee.id(), today),
                "current salary", employee);
        return new CurrentSalaryResponse(
                entry.effectiveDate(), entry.salary().amountMinor(), entry.salary().currencyCode());
    }

    /** A foreign key or a salary rule guarantees these exist, so a miss means the data is broken. */
    private static <T> T stored(final Optional<T> value, final String kind, final Employee employee) {
        return value.orElseThrow(() ->
                new IllegalStateException("Employee " + employee.id() + " has no " + kind + "."));
    }
}
```

- [ ] **Step 8: Run and watch the tests pass**

Run: `cd backend && ./gradlew integrationTest --tests '*EmployeeServiceIntegrationTest' --console=plain`
Expected: 9 PASS.

If `rejectsAnEmailThatDiffersOnlyInCaseFromAnExistingOne` fails with `DataIntegrityViolationException` and not `EmailAlreadyExistsException`, the assumption that Hibernate exposes the constraint name did not hold. Print `exception.getCause()` and its `getConstraintName()` in a throwaway test to see what PostgreSQL's driver reports. The fallback is `exception.getMostSpecificCause().getMessage().contains(EMAIL_UNIQUE_INDEX)`. Record the finding in `docs/tradeoffs.md` or the spec's risk list, whichever way it falls.

- [ ] **Step 9: Prove the service tests**

1. Remove the `salaryService.recordFirstEntry(...)` line. Expected: `createsAnEmployeeWithTheFirstSalaryAndReadsItBack` FAILS. Restore.
2. In `violatesEmailIndex`, return `false`. Expected: `rejectsAnEmailThatDiffersOnlyInCaseFromAnExistingOne` FAILS. Restore.
3. Remove `@Transactional` from `create`. Expected: `leavesNoEmployeeBehindWhenTheFirstSalaryBreaksARule` FAILS (the employee row stays). Restore.
Run the whole file: 9 PASS.

- [ ] **Step 10: Run the gates and suggest the commit**

Run: `cd backend && ./gradlew check --console=plain --warning-mode=none`
Expected: BUILD SUCCESSFUL, all integration tests pass.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/employee backend/src/test/java/com/acme/peoplemanagement/support backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeServiceIntegrationTest.java
```

Suggested message: `feat(api): create and read an employee with the first salary in one transaction`

---

### Task 7: The HTTP layer and the error mapping

**Files:**
- Create: `backend/src/main/java/com/acme/peoplemanagement/employee/EmployeeController.java`
- Create: `backend/src/main/java/com/acme/peoplemanagement/config/ApiExceptionHandler.java`
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeEndpointTest.java`

**Interfaces:**
- Consumes: `EmployeeService.create/get`, the exceptions and records from Task 6, `SalaryRuleViolation`.
- Produces: `POST /api/employees` (201, `Location: /api/employees/{id}`, body `EmployeeResponse`) and `GET /api/employees/{id}`. `ApiExceptionHandler` maps: `MethodArgumentNotValidException` 400 with `errors`, `UnknownReferenceException` 400 with `errors`, `EmployeeNotFoundException` 404, `EmailAlreadyExistsException` 409 with `code`, `SalaryRuleViolation` 422 with `code`. Other Spring MVC errors (malformed body, bad id type) come from `ResponseEntityExceptionHandler` as problem+json.

- [ ] **Step 1: Write the failing web slice tests**

`EmployeeEndpointTest.java`:

```java
package com.acme.peoplemanagement.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.auth.AuthTestSupport;
import com.acme.peoplemanagement.reference.CountryResponse;
import com.acme.peoplemanagement.reference.DepartmentResponse;
import com.acme.peoplemanagement.reference.JobTitleResponse;
import com.acme.peoplemanagement.salary.SalaryRuleCode;
import com.acme.peoplemanagement.salary.SalaryRuleViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

@WebMvcTest(EmployeeController.class)
@ActiveProfiles("test")
@Import({AuthTestSupport.class, EmployeeEndpointTest.ClockConfiguration.class})
class EmployeeEndpointTest {

    private static final String VALID_BODY = """
            {"fullName": "Asha Rao", "email": "asha.rao@acme.example", "hireDate": "2024-04-01",
             "departmentId": 3, "jobTitleId": 12, "countryCode": "IN",
             "initialSalary": {"amountMinor": 125000000, "currency": "INR"}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void createsAnEmployeeAndPointsToItsDetail() throws Exception {
        given(employeeService.create(any())).willReturn(sampleEmployee());

        postEmployee(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/10432"))
                .andExpect(jsonPath("$.id").value(10432))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.terminationDate").doesNotExist())
                .andExpect(jsonPath("$.currentSalary.amountMinor").value(125000000))
                .andExpect(jsonPath("$.currentSalary.currency").value("INR"));
    }

    @Test
    void readsAnEmployee() throws Exception {
        given(employeeService.get(10432L)).willReturn(sampleEmployee());

        mockMvc.perform(get("/api/employees/10432").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.department.name").value("Engineering"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void namesTheFieldWhenTheNameIsBlank() throws Exception {
        postEmployee(VALID_BODY.replace("Asha Rao", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));
    }

    @Test
    void namesTheNestedFieldWhenTheAmountIsZero() throws Exception {
        postEmployee(VALID_BODY.replace("125000000", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialSalary.amountMinor"));
    }

    @Test
    void rejectsAnAmountOneAboveTheLargestExactInteger() throws Exception {
        postEmployee(VALID_BODY.replace("125000000", "9007199254740992"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialSalary.amountMinor"));
    }

    @Test
    void answersProblemJsonForAMalformedBody() throws Exception {
        postEmployee("{\"fullName\": ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void answersProblemJsonForAnEmployeeIdThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/employees/abc").with(login()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void answers422WithTheRuleCode() throws Exception {
        given(employeeService.create(any())).willThrow(
                new SalaryRuleViolation(SalaryRuleCode.NO_CURRENT_SALARY, "No current salary."));

        postEmployee(VALID_BODY)
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NO_CURRENT_SALARY"))
                .andExpect(jsonPath("$.detail").value("No current salary."));
    }

    @Test
    void answers409ForADuplicateEmail() throws Exception {
        given(employeeService.create(any())).willThrow(new EmailAlreadyExistsException());

        postEmployee(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void answers400WithTheFieldForAnUnknownReference() throws Exception {
        given(employeeService.create(any()))
                .willThrow(new UnknownReferenceException("departmentId", "Unknown department."));

        postEmployee(VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("departmentId"))
                .andExpect(jsonPath("$.errors[0].message").value("Unknown department."));
    }

    @Test
    void answers404ForAnEmployeeThatDoesNotExist() throws Exception {
        given(employeeService.get(7L)).willThrow(new EmployeeNotFoundException(7L));

        mockMvc.perform(get("/api/employees/7").with(login()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions postEmployee(final String body) throws Exception {
        return mockMvc.perform(post("/api/employees")
                .with(login())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }

    private static EmployeeResponse sampleEmployee() {
        return new EmployeeResponse(10432L, "Asha Rao", "asha.rao@acme.example", LocalDate.of(2024, 4, 1), null,
                EmployeeStatus.ACTIVE, new DepartmentResponse(3, "Engineering"),
                new JobTitleResponse(12, "Software Engineer"), new CountryResponse("IN", "India"),
                new CurrentSalaryResponse(LocalDate.of(2024, 4, 1), 125_000_000L, "INR"), 0L);
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

`SecurityConfig` is package-private in `auth`, and `AuthEndpointTest` can import it only because it sits in the same package. This test is in `employee`, so it uses a small public test class in `auth` that switches the real shared login on. Create `backend/src/test/java/com/acme/peoplemanagement/auth/AuthTestSupport.java`:

```java
package com.acme.peoplemanagement.auth;

import org.springframework.context.annotation.Import;

/** Lets web slice tests in other packages switch on the real shared login without opening up SecurityConfig. */
@Import(SecurityConfig.class)
public class AuthTestSupport {
}
```

- [ ] **Step 2: Run and watch it fail to compile**

Run: `cd backend && ./gradlew test --tests '*EmployeeEndpointTest' --console=plain`
Expected: FAIL, `cannot find symbol EmployeeController`.

- [ ] **Step 3: Create the controller**

`EmployeeController.java`:

```java
package com.acme.peoplemanagement.employee;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates and reads employees. Errors are turned into problem+json by the shared exception handler. */
@RestController
@RequestMapping("/api/employees")
class EmployeeController {

    private final EmployeeService employeeService;

    EmployeeController(final EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    ResponseEntity<EmployeeResponse> create(@Valid @RequestBody final CreateEmployeeRequest request) {
        final EmployeeResponse created = employeeService.create(request);
        // A relative Location works behind the nginx proxy, where the host the API sees is not the browser's.
        return ResponseEntity.created(URI.create("/api/employees/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    EmployeeResponse get(@PathVariable final long id) {
        return employeeService.get(id);
    }
}
```

- [ ] **Step 4: Create the exception handler**

`ApiExceptionHandler.java`:

```java
package com.acme.peoplemanagement.config;

import com.acme.peoplemanagement.employee.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.UnknownReferenceException;
import com.acme.peoplemanagement.salary.SalaryRuleViolation;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into application/problem+json. It maps an exception type to a status and nothing
 * more: which situations are errors is decided by the services and the salary rules. Errors that
 * Spring MVC raises itself (a malformed body, an id that is not a number) are handled by the base class.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    @ExceptionHandler(SalaryRuleViolation.class)
    ProblemDetail salaryRuleBroken(final SalaryRuleViolation violation) {
        return problemWithCode(UNPROCESSABLE, violation.getMessage(), violation.code().name());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ProblemDetail emailAlreadyExists(final EmailAlreadyExistsException exception) {
        return problemWithCode(HttpStatus.CONFLICT, exception.getMessage(), "EMAIL_ALREADY_EXISTS");
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    ProblemDetail employeeNotFound(final EmployeeNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnknownReferenceException.class)
    ProblemDetail unknownReference(final UnknownReferenceException exception) {
        return invalidRequest(List.of(new FieldProblem(exception.field(), exception.getMessage())));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final List<FieldProblem> problems = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid")))
                .toList();
        return handleExceptionInternal(exception, invalidRequest(problems), headers, status, request);
    }

    private static ProblemDetail invalidRequest(final List<FieldProblem> problems) {
        final ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid.");
        problem.setProperty("errors", problems);
        return problem;
    }

    private static ProblemDetail problemWithCode(final HttpStatusCode status, final String detail, final String code) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    /** One entry of the {@code errors} list in a 400 response. */
    record FieldProblem(String field, String message) {
    }
}
```

If the compiler rejects the `handleMethodArgumentNotValid` override, the signature in this Spring Framework version differs from the one above. Read the error, check `ResponseEntityExceptionHandler` in the IDE, and match its parameter types. Do not change the behavior.

- [ ] **Step 5: Run and watch the tests pass**

Run: `cd backend && ./gradlew test --tests '*EmployeeEndpointTest' --console=plain`
Expected: 12 PASS.

- [ ] **Step 6: Prove the tests**

1. Change `HttpStatus.CONFLICT` to `HttpStatus.BAD_REQUEST` in `emailAlreadyExists`. Expected: `answers409ForADuplicateEmail` FAILS. Restore.
2. Remove `extends ResponseEntityExceptionHandler` and the `@Override` (and its imports). Expected: `answersProblemJsonForAMalformedBody` and `answersProblemJsonForAnEmployeeIdThatIsNotANumber` FAIL. Restore with `git checkout` of the file only if it was staged; otherwise undo by hand.
3. Change `@Max(Money.MAX_AMOUNT_MINOR)` to `@Max(Long.MAX_VALUE)` in `InitialSalaryRequest`. Expected: `rejectsAnAmountOneAboveTheLargestExactInteger` FAILS. Restore.
Run: 12 PASS.

- [ ] **Step 7: Run the gates and suggest the commit**

Run: `cd backend && ./gradlew check -x integrationTest --console=plain --warning-mode=none`
Expected: BUILD SUCCESSFUL.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/employee/EmployeeController.java backend/src/main/java/com/acme/peoplemanagement/config/ApiExceptionHandler.java backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeEndpointTest.java backend/src/test/java/com/acme/peoplemanagement/auth/AuthTestSupport.java
```

Suggested message: `feat(api): add POST and GET /api/employees with problem+json errors`

---

### Task 8: End-to-end check through HTTP and the database

**Files:**
- Test: `backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeEndpointIntegrationTest.java`

**Interfaces:**
- Consumes: everything above, with the real `ApiExceptionHandler`, controller, service and PostgreSQL.

- [ ] **Step 1: Write the test**

`EmployeeEndpointIntegrationTest.java`:

```java
package com.acme.peoplemanagement.employee;

import static org.assertj.core.api.Assertions.assertThat;
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
class EmployeeEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void createsAnEmployeeAndReadsItFromTheLocationHeader() throws Exception {
        final String location = postEmployee(body("2024-04-01", "asha@acme.example"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentSalary.effectiveDate").value("2024-04-01"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(httpBasic("hr-test", "test-only-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@acme.example"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.country.name").value("India"));
    }

    @Test
    void answers409ForAnEmailThatDiffersOnlyInCase() throws Exception {
        postEmployee(body("2024-04-01", "same@acme.example")).andExpect(status().isCreated());

        postEmployee(body("2024-04-01", "SAME@acme.example"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void answers422AndStoresNothingForAHireDateInTheFuture() throws Exception {
        postEmployee(body("2026-10-20", "future@acme.example"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("NO_CURRENT_SALARY"));

        final Integer stored = jdbcTemplate.queryForObject(
                "select count(*) from employee where email = 'future@acme.example'", Integer.class);
        assertThat(stored).isZero();
    }

    private ResultActions postEmployee(final String body) throws Exception {
        return mockMvc.perform(post("/api/employees")
                .with(httpBasic("hr-test", "test-only-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String body(final String hireDate, final String email) {
        return """
                {"fullName": "Asha Rao", "email": "%s", "hireDate": "%s",
                 "departmentId": %d, "jobTitleId": %d, "countryCode": "IN",
                 "initialSalary": {"amountMinor": 125000000, "currency": "INR"}}"""
                .formatted(email, hireDate,
                        idOf("department", "Engineering"), idOf("job_title", "Software Engineer"));
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }
}
```

The fixed clock makes "today" 2026-10-19, so `2026-10-20` is tomorrow.

- [ ] **Step 2: Run it**

Run: `cd backend && ./gradlew integrationTest --tests '*EmployeeEndpointIntegrationTest' --console=plain`
Expected: 3 PASS. This is the first test where a real request meets the real database, the real validation and the real error mapping. A failure here that the earlier layers did not catch is a real finding: report it, do not patch around it.

- [ ] **Step 3: Prove one test**

Temporarily delete the `@ExceptionHandler(EmailAlreadyExistsException.class)` method. Expected: `answers409ForAnEmailThatDiffersOnlyInCase` FAILS. Restore.

- [ ] **Step 4: Run all backend checks**

Run: `make test-backend && make test-integration`
Expected: both succeed.

- [ ] **Step 5: Stage and suggest the commit**

```bash
git add backend/src/test/java/com/acme/peoplemanagement/employee/EmployeeEndpointIntegrationTest.java
```

Suggested message: `test(api): check creating and reading an employee through HTTP against PostgreSQL`

---

### Task 9: Money text to minor units and back (frontend, pure functions)

**Files:**
- Create: `frontend/src/app/shared/money/parse-major-to-minor.ts`
- Create: `frontend/src/app/shared/money/format-minor.ts`
- Test: `frontend/src/app/shared/money/parse-major-to-minor.spec.ts`
- Test: `frontend/src/app/shared/money/format-minor.spec.ts`

**Interfaces:**
- Produces: `parseMajorToMinor(text: string, minorUnitDigits: number): ParsedAmount`, `type AmountError = 'not-a-number' | 'too-many-decimals' | 'not-positive' | 'too-large'`, `type ParsedAmount = { ok: true; amountMinor: number } | { ok: false; reason: AmountError }`, and `formatMinor(amountMinor: number, minorUnitDigits: number): string`.

Run frontend commands with Node set up: from the repo root, `. scripts/use-node.sh && cd frontend && ...`.

- [ ] **Step 1: Write the failing parse tests**

`parse-major-to-minor.spec.ts`:

```ts
import { parseMajorToMinor } from './parse-major-to-minor';

describe('parseMajorToMinor', () => {
  it('converts a two-decimal amount to minor units exactly', () => {
    expect(parseMajorToMinor('1250000.50', 2)).toEqual({ ok: true, amountMinor: 125000050 });
  });

  it('accepts thousands separators', () => {
    expect(parseMajorToMinor('1,250,000.50', 2)).toEqual({ ok: true, amountMinor: 125000050 });
  });

  it('adds the missing decimals', () => {
    expect(parseMajorToMinor('1250000', 2)).toEqual({ ok: true, amountMinor: 125000000 });
    expect(parseMajorToMinor('10.5', 2)).toEqual({ ok: true, amountMinor: 1050 });
  });

  it('is exact where multiplying a float is not', () => {
    // 8.2 * 100 is 819.9999999999999 and 0.29 * 100 is 28.999999999999996 in floating point.
    expect(parseMajorToMinor('8.2', 2)).toEqual({ ok: true, amountMinor: 820 });
    expect(parseMajorToMinor('0.29', 2)).toEqual({ ok: true, amountMinor: 29 });
  });

  it('handles a currency with three decimals', () => {
    expect(parseMajorToMinor('1.005', 3)).toEqual({ ok: true, amountMinor: 1005 });
  });

  it('needs no decimals for a currency without minor units, such as JPY', () => {
    expect(parseMajorToMinor('125,000', 0)).toEqual({ ok: true, amountMinor: 125000 });
  });

  it('rejects any decimals for a currency without minor units', () => {
    expect(parseMajorToMinor('10.5', 0)).toEqual({ ok: false, reason: 'too-many-decimals' });
    expect(parseMajorToMinor('10.0', 0)).toEqual({ ok: false, reason: 'too-many-decimals' });
  });

  it('rejects more decimals than the currency allows', () => {
    expect(parseMajorToMinor('10.505', 2)).toEqual({ ok: false, reason: 'too-many-decimals' });
  });

  it.each(['', '  ', 'abc', '1,25', '1.', '.5', '-5', '1e3', '1 000'])(
    'rejects "%s" as not an amount',
    (text) => {
      expect(parseMajorToMinor(text, 2)).toEqual({ ok: false, reason: 'not-a-number' });
    },
  );

  it('rejects zero', () => {
    expect(parseMajorToMinor('0.00', 2)).toEqual({ ok: false, reason: 'not-positive' });
  });

  it('accepts the largest amount a JavaScript number holds exactly and rejects one more', () => {
    expect(parseMajorToMinor('90071992547409.91', 2)).toEqual({
      ok: true,
      amountMinor: 9007199254740991,
    });
    expect(parseMajorToMinor('90071992547409.92', 2)).toEqual({ ok: false, reason: 'too-large' });
  });
});
```

- [ ] **Step 2: Run and watch it fail**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/shared/money/parse-major-to-minor.spec.ts`
Expected: FAIL, the module `./parse-major-to-minor` cannot be found. If `--include` is not accepted by this Angular version, run `npm run test` and read only the new spec's results.

- [ ] **Step 3: Implement**

`parse-major-to-minor.ts`:

```ts
const MAX_AMOUNT_MINOR = 9007199254740991n;

/** Digits with optional thousands groups, then an optional fraction. No sign, no exponent. */
const AMOUNT_PATTERN = /^(\d{1,3}(,\d{3})+|\d+)(\.\d+)?$/;

export type AmountError = 'not-a-number' | 'too-many-decimals' | 'not-positive' | 'too-large';

export type ParsedAmount = { ok: true; amountMinor: number } | { ok: false; reason: AmountError };

/**
 * Turns what HR typed ("1,250,000.50") into integer minor units. It works on the digits as text and
 * never multiplies a float, so the result is exact. The largest amount is the largest integer a
 * JavaScript number holds exactly, which is also the limit the API enforces.
 */
export function parseMajorToMinor(text: string, minorUnitDigits: number): ParsedAmount {
  const trimmed = text.trim();
  if (!AMOUNT_PATTERN.test(trimmed)) {
    return { ok: false, reason: 'not-a-number' };
  }
  const [whole, fraction = ''] = trimmed.replaceAll(',', '').split('.');
  if (fraction.length > minorUnitDigits) {
    return { ok: false, reason: 'too-many-decimals' };
  }
  const amountMinor = BigInt(whole + fraction.padEnd(minorUnitDigits, '0'));
  if (amountMinor <= 0n) {
    return { ok: false, reason: 'not-positive' };
  }
  if (amountMinor > MAX_AMOUNT_MINOR) {
    return { ok: false, reason: 'too-large' };
  }
  return { ok: true, amountMinor: Number(amountMinor) };
}
```

- [ ] **Step 4: Run and watch it pass**

Run the same command. Expected: all parse tests PASS.

- [ ] **Step 5: Prove the parse tests**

Temporarily replace everything after the pattern check with `return { ok: true, amountMinor: Number(trimmed.replaceAll(',', '')) * 10 ** minorUnitDigits };` (the float approach, no rounding). Expected: `is exact where multiplying a float is not` FAILS on `8.2` (819.9999999999999), and so does the three-decimal test. Restore the real code. Then temporarily change `> MAX_AMOUNT_MINOR` to `>= MAX_AMOUNT_MINOR`: expected `accepts the largest amount...` FAILS. Restore. All PASS.

- [ ] **Step 6: Write the failing format tests**

`format-minor.spec.ts`:

```ts
import { formatMinor } from './format-minor';

describe('formatMinor', () => {
  it('groups thousands and shows the decimals of the currency', () => {
    expect(formatMinor(125000050, 2)).toBe('1,250,000.50');
  });

  it('pads amounts below one major unit', () => {
    expect(formatMinor(5, 2)).toBe('0.05');
    expect(formatMinor(0, 2)).toBe('0.00');
  });

  it('shows no decimals for a currency without minor units', () => {
    expect(formatMinor(125000, 0)).toBe('125,000');
  });

  it('handles a currency with three decimals', () => {
    expect(formatMinor(1005, 3)).toBe('1.005');
  });

  it('shows the largest exact amount without rounding it', () => {
    expect(formatMinor(9007199254740991, 2)).toBe('90,071,992,547,409.91');
  });
});
```

- [ ] **Step 7: Run, then implement**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/shared/money/format-minor.spec.ts`. Expected: FAIL (module missing).

`format-minor.ts`:

```ts
/**
 * Turns integer minor units into text such as "1,250,000.50". It splits the digits as text instead of
 * dividing, so a large amount is never rounded by floating point.
 */
export function formatMinor(amountMinor: number, minorUnitDigits: number): string {
  const digits = String(amountMinor).padStart(minorUnitDigits + 1, '0');
  const splitAt = digits.length - minorUnitDigits;
  const whole = digits.slice(0, splitAt).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return minorUnitDigits === 0 ? whole : `${whole}.${digits.slice(splitAt)}`;
}
```

Run again. Expected: 5 PASS.

- [ ] **Step 8: Prove a format test**

Temporarily change `padStart(minorUnitDigits + 1, '0')` to `padStart(minorUnitDigits, '0')`. Expected: `pads amounts below one major unit` FAILS. Restore.

- [ ] **Step 9: Run the frontend gates and suggest the commit**

Run: `. scripts/use-node.sh && cd frontend && npm run format:write && npm run format:check && npm run lint && npm run test && npm run typecheck && npm run knip`
Expected: all succeed. Knip may report the two exported types if nothing imports them yet: `AmountError` is used by Task 13, so leave a note and re-run Knip after Task 13. If Knip fails now, temporarily keep `AmountError` and `ParsedAmount` un-exported (remove `export`) and export them in Task 13 when they are first imported.

```bash
git add frontend/src/app/shared/money
```

Suggested message: `feat(ui): add exact conversion between typed salary and minor units`

---

### Task 10: Server errors to form errors

**Files:**
- Create: `frontend/src/app/core/api/to-form-errors.ts`
- Test: `frontend/src/app/core/api/to-form-errors.spec.ts`

**Interfaces:**
- Produces: `interface FormErrors { fieldErrors: Record<string, string>; formError: string | null }` and `toFormErrors(error: HttpErrorResponse): FormErrors`. Field names are the server's (`email`, `initialSalary.amountMinor`).

- [ ] **Step 1: Write the failing tests**

`to-form-errors.spec.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { toFormErrors } from './to-form-errors';

function failure(status: number, body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('toFormErrors', () => {
  it('puts each validation message under its field', () => {
    const errors = toFormErrors(
      failure(400, {
        detail: 'The request is invalid.',
        errors: [
          { field: 'fullName', message: 'must not be blank' },
          { field: 'initialSalary.amountMinor', message: 'must be greater than or equal to 1' },
        ],
      }),
    );

    expect(errors).toEqual({
      fieldErrors: {
        fullName: 'must not be blank',
        'initialSalary.amountMinor': 'must be greater than or equal to 1',
      },
      formError: null,
    });
  });

  it('keeps the first message when a field has several', () => {
    const errors = toFormErrors(
      failure(400, {
        errors: [
          { field: 'email', message: 'first' },
          { field: 'email', message: 'second' },
        ],
      }),
    );

    expect(errors.fieldErrors).toEqual({ email: 'first' });
  });

  it('puts a duplicate email under the email field', () => {
    const errors = toFormErrors(
      failure(409, { code: 'EMAIL_ALREADY_EXISTS', detail: 'An employee with this email already exists.' }),
    );

    expect(errors).toEqual({
      fieldErrors: { email: 'An employee with this email already exists.' },
      formError: null,
    });
  });

  it('shows the server wording for a broken business rule in the banner', () => {
    const errors = toFormErrors(
      failure(422, { code: 'NO_CURRENT_SALARY', detail: 'There must be a salary effective today.' }),
    );

    expect(errors).toEqual({ fieldErrors: {}, formError: 'There must be a salary effective today.' });
  });

  it.each([
    ['a server error', failure(500, { detail: 'Internal error text' })],
    ['no answer from the server', failure(0, null)],
    ['a timeout', failure(408, null)],
    ['a body that is not JSON', failure(502, '<html>Bad gateway</html>')],
  ])('shows a generic message for %s and never the server text', (_, error) => {
    expect(toFormErrors(error)).toEqual({
      fieldErrors: {},
      formError: 'Could not save. Check your connection and try again.',
    });
  });
});
```

- [ ] **Step 2: Run and watch it fail**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/core/api/to-form-errors.spec.ts`
Expected: FAIL (module missing).

- [ ] **Step 3: Implement**

`to-form-errors.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';

export interface FormErrors {
  fieldErrors: Record<string, string>;
  formError: string | null;
}

interface ProblemBody {
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}

const EMAIL_ALREADY_EXISTS = 'EMAIL_ALREADY_EXISTS';
const GENERIC_MESSAGE = 'Could not save. Check your connection and try again.';

/**
 * Decides where a failed save is shown. Validation messages go under their field, a duplicate email
 * goes under the email field, and a broken business rule goes in a banner with the server's wording.
 * Anything without a stable shape (server error, timeout, no answer) gets one generic message, so
 * server internals are never shown.
 */
export function toFormErrors(error: HttpErrorResponse): FormErrors {
  const problem = readProblem(error.error);
  if (problem?.errors?.length) {
    return { fieldErrors: firstMessagePerField(problem.errors), formError: null };
  }
  if (problem?.code === EMAIL_ALREADY_EXISTS && problem.detail) {
    return { fieldErrors: { email: problem.detail }, formError: null };
  }
  if (problem?.code && problem.detail) {
    return { fieldErrors: {}, formError: problem.detail };
  }
  return { fieldErrors: {}, formError: GENERIC_MESSAGE };
}

function readProblem(body: unknown): ProblemBody | null {
  return typeof body === 'object' && body !== null ? (body as ProblemBody) : null;
}

function firstMessagePerField(errors: { field: string; message: string }[]): Record<string, string> {
  const messages: Record<string, string> = {};
  for (const { field, message } of errors) {
    messages[field] ??= message;
  }
  return messages;
}
```

- [ ] **Step 4: Run and watch it pass, then prove it**

Run the same command. Expected: PASS. Then temporarily change `messages[field] ??= message` to `messages[field] = message`: expected `keeps the first message when a field has several` FAILS. Restore. Then temporarily make the last `return` use `problem?.detail ?? GENERIC_MESSAGE`: expected the `a server error` case FAILS. Restore.

- [ ] **Step 5: Run the frontend gates and suggest the commit**

Run: `. scripts/use-node.sh && cd frontend && npm run format:write && npm run format:check && npm run lint && npm run test && npm run typecheck`
Expected: succeed (Knip is checked after Task 13, when the function gets its first user).

```bash
git add frontend/src/app/core/api/to-form-errors.ts frontend/src/app/core/api/to-form-errors.spec.ts
```

Suggested message: `feat(ui): map API errors to field errors and a form banner in one place`

---

### Task 11: The API services

**Files:**
- Create: `frontend/src/app/core/reference/reference-api.ts`
- Create: `frontend/src/app/core/employees/employee-api.ts`
- Test: `frontend/src/app/core/reference/reference-api.spec.ts`
- Test: `frontend/src/app/core/employees/employee-api.spec.ts`

**Interfaces:**
- Produces: `ReferenceApi.loadLists(): Promise<ReferenceLists>` and `ReferenceApi.loadCurrencies(): Promise<Currency[]>`; types `Country { code; name }`, `NamedReference { id; name }`, `Currency { code; minorUnitDigits }`, `ReferenceLists { countries; departments; jobTitles; currencies }`. `EmployeeApi.create(request: CreateEmployeeRequest): Promise<Employee>` and `EmployeeApi.get(id: number): Promise<Employee>`; types `CreateEmployeeRequest`, `Employee`.

- [ ] **Step 1: Write the failing tests**

`reference-api.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReferenceApi } from './reference-api';

describe('ReferenceApi', () => {
  let api: ReferenceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ReferenceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the four lists the forms need', async () => {
    const loading = api.loadLists();
    http.expectOne('/api/reference/countries').flush([{ code: 'IN', name: 'India' }]);
    http.expectOne('/api/reference/departments').flush([{ id: 1, name: 'Engineering' }]);
    http.expectOne('/api/reference/job-titles').flush([{ id: 2, name: 'Software Engineer' }]);
    http.expectOne('/api/reference/currencies').flush([{ code: 'INR', minorUnitDigits: 2 }]);

    expect(await loading).toEqual({
      countries: [{ code: 'IN', name: 'India' }],
      departments: [{ id: 1, name: 'Engineering' }],
      jobTitles: [{ id: 2, name: 'Software Engineer' }],
      currencies: [{ code: 'INR', minorUnitDigits: 2 }],
    });
  });

  it('loads only the currencies for a page that just shows money', async () => {
    const loading = api.loadCurrencies();
    http.expectOne('/api/reference/currencies').flush([{ code: 'JPY', minorUnitDigits: 0 }]);

    expect(await loading).toEqual([{ code: 'JPY', minorUnitDigits: 0 }]);
  });
});
```

`employee-api.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateEmployeeRequest, EmployeeApi } from './employee-api';

describe('EmployeeApi', () => {
  let api: EmployeeApi;
  let http: HttpTestingController;

  const request: CreateEmployeeRequest = {
    fullName: 'Asha Rao',
    email: 'asha.rao@acme.example',
    hireDate: '2024-04-01',
    departmentId: 3,
    jobTitleId: 12,
    countryCode: 'IN',
    initialSalary: { amountMinor: 125000000, currency: 'INR' },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(EmployeeApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the new employee and returns the created detail', async () => {
    const creating = api.create(request);
    const call = http.expectOne('/api/employees');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    call.flush({ id: 10432 });

    expect((await creating).id).toBe(10432);
  });

  it('gets an employee by id', async () => {
    const reading = api.get(10432);
    http.expectOne('/api/employees/10432').flush({ id: 10432 });

    expect((await reading).id).toBe(10432);
  });
});
```

- [ ] **Step 2: Run and watch them fail**

Run: `. scripts/use-node.sh && cd frontend && npm run test`
Expected: the two new specs FAIL (modules missing).

- [ ] **Step 3: Implement**

`reference-api.ts`:

```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, forkJoin } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';

export interface Country {
  code: string;
  name: string;
}

export interface NamedReference {
  id: number;
  name: string;
}

export interface Currency {
  code: string;
  minorUnitDigits: number;
}

export interface ReferenceLists {
  countries: Country[];
  departments: NamedReference[];
  jobTitles: NamedReference[];
  currencies: Currency[];
}

/** The standardized lists that forms and filters offer. They change only through a migration. */
@Injectable({ providedIn: 'root' })
export class ReferenceApi {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  loadLists(): Promise<ReferenceLists> {
    return firstValueFrom(
      forkJoin({
        countries: this.http.get<Country[]>(`${this.apiBaseUrl}/reference/countries`),
        departments: this.http.get<NamedReference[]>(`${this.apiBaseUrl}/reference/departments`),
        jobTitles: this.http.get<NamedReference[]>(`${this.apiBaseUrl}/reference/job-titles`),
        currencies: this.http.get<Currency[]>(`${this.apiBaseUrl}/reference/currencies`),
      }),
    );
  }

  loadCurrencies(): Promise<Currency[]> {
    return firstValueFrom(this.http.get<Currency[]>(`${this.apiBaseUrl}/reference/currencies`));
  }
}
```

`employee-api.ts`:

```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import { Country, NamedReference } from '../reference/reference-api';

export interface CreateEmployeeRequest {
  fullName: string;
  email: string;
  hireDate: string;
  departmentId: number;
  jobTitleId: number;
  countryCode: string;
  initialSalary: { effectiveDate?: string; amountMinor: number; currency: string };
}

export interface Employee {
  id: number;
  fullName: string;
  email: string;
  hireDate: string;
  terminationDate: string | null;
  status: 'ACTIVE' | 'FORMER';
  department: NamedReference;
  jobTitle: NamedReference;
  country: Country;
  currentSalary: { effectiveDate: string; amountMinor: number; currency: string };
  version: number;
}

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  create(request: CreateEmployeeRequest): Promise<Employee> {
    return firstValueFrom(this.http.post<Employee>(`${this.apiBaseUrl}/employees`, request));
  }

  get(id: number): Promise<Employee> {
    return firstValueFrom(this.http.get<Employee>(`${this.apiBaseUrl}/employees/${id}`));
  }
}
```

- [ ] **Step 4: Run and watch them pass, then prove one**

Run: `. scripts/use-node.sh && cd frontend && npm run test`. Expected: PASS. Then change `/reference/job-titles` to `/reference/jobtitles` in `reference-api.ts`: expected `loads the four lists the forms need` FAILS. Restore.

- [ ] **Step 5: Gates and commit suggestion**

Run: `. scripts/use-node.sh && cd frontend && npm run format:write && npm run format:check && npm run lint && npm run typecheck`
Expected: succeed.

```bash
git add frontend/src/app/core/reference frontend/src/app/core/employees
```

Suggested message: `feat(ui): add the reference and employee API services`

---

### Task 12: The employee detail page

**Files:**
- Create: `frontend/src/app/features/employee-detail/employee-detail.ts`
- Create: `frontend/src/app/features/employee-detail/employee-detail.html`
- Create: `frontend/src/app/features/employee-detail/employee-detail.scss`
- Test: `frontend/src/app/features/employee-detail/employee-detail.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.routes.spec.ts`

**Interfaces:**
- Consumes: `EmployeeApi.get`, `ReferenceApi.loadCurrencies`, `formatMinor`.
- Produces: the route `employees/:id` (behind `authGuard`) showing one employee.

- [ ] **Step 1: Write the failing tests**

`employee-detail.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { EmployeeDetail } from './employee-detail';

const EMPLOYEE = {
  id: 10432,
  fullName: 'Asha Rao',
  email: 'asha.rao@acme.example',
  hireDate: '2024-04-01',
  terminationDate: null,
  status: 'ACTIVE',
  department: { id: 3, name: 'Engineering' },
  jobTitle: { id: 12, name: 'Software Engineer' },
  country: { code: 'IN', name: 'India' },
  currentSalary: { effectiveDate: '2024-04-01', amountMinor: 125000050, currency: 'INR' },
  version: 0,
};

describe('EmployeeDetail', () => {
  let fixture: ComponentFixture<EmployeeDetail>;
  let page: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '10432' }) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDetail);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function answer(employee: unknown): void {
    http.expectOne('/api/employees/10432').flush(employee);
    http.expectOne('/api/reference/currencies').flush([{ code: 'INR', minorUnitDigits: 2 }]);
  }

  it('says it is loading until the answers arrive', () => {
    expect(page.textContent).toContain('Loading');
    answer(EMPLOYEE);
  });

  it('shows the profile and the current salary in its own currency', async () => {
    answer(EMPLOYEE);
    await settle();

    const text = page.textContent ?? '';
    expect(text).toContain('Asha Rao');
    expect(text).toContain('asha.rao@acme.example');
    expect(text).toContain('Engineering');
    expect(text).toContain('Software Engineer');
    expect(text).toContain('India');
    expect(text).toContain('1,250,000.50 INR');
    expect(text).toContain('Active');
  });

  it('says the employee does not exist when the server answers 404', async () => {
    http.expectOne('/api/employees/10432').flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/reference/currencies').flush([]);
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('No employee with this ID');
  });

  it('offers to try again when loading fails, and shows the employee after a retry', async () => {
    http.expectOne('/api/employees/10432').error(new ProgressEvent('error'));
    http.expectOne('/api/reference/currencies').flush([]);
    await settle();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not load');

    page.querySelector<HTMLButtonElement>('button')!.click();
    answer(EMPLOYEE);
    await settle();

    expect(page.textContent).toContain('Asha Rao');
  });
});
```

- [ ] **Step 2: Run and watch it fail**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/features/employee-detail/employee-detail.spec.ts`
Expected: FAIL (module missing).

- [ ] **Step 3: Implement the component**

`employee-detail.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Employee, EmployeeApi } from '../../core/employees/employee-api';
import { Currency, ReferenceApi } from '../../core/reference/reference-api';
import { formatMinor } from '../../shared/money/format-minor';

type LoadState = 'loading' | 'loaded' | 'not-found' | 'failed';

const NOT_FOUND = 404;

@Component({
  selector: 'app-employee-detail',
  imports: [RouterLink, MatButtonModule, MatCardModule],
  templateUrl: './employee-detail.html',
  styleUrl: './employee-detail.scss',
})
export class EmployeeDetail implements OnInit {
  private readonly employeeApi = inject(EmployeeApi);
  private readonly referenceApi = inject(ReferenceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly currencies = signal<Currency[]>([]);

  protected readonly loadState = signal<LoadState>('loading');
  protected readonly employee = signal<Employee | null>(null);
  protected readonly salaryText = computed(() => {
    const salary = this.employee()?.currentSalary;
    const currency = this.currencies().find((candidate) => candidate.code === salary?.currency);
    return salary && currency
      ? `${formatMinor(salary.amountMinor, currency.minorUnitDigits)} ${currency.code}`
      : null;
  });

  ngOnInit(): void {
    void this.load();
  }

  protected retry(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loadState.set('loading');
    const id = Number(this.route.snapshot.paramMap.get('id'));
    try {
      const [employee, currencies] = await Promise.all([
        this.employeeApi.get(id),
        this.referenceApi.loadCurrencies(),
      ]);
      this.currencies.set(currencies);
      this.employee.set(employee);
      this.loadState.set('loaded');
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.loadState.set(error.status === NOT_FOUND ? 'not-found' : 'failed');
    }
  }
}
```

`employee-detail.html`:

```html
<!-- The labels and messages are hard-coded in English for now. For localization they should come from one central place, such as Angular's i18n. -->
<section class="detail">
  @switch (loadState()) {
    @case ('loading') {
      <p>Loading…</p>
    }
    @case ('not-found') {
      <p role="alert">No employee with this ID.</p>
      <a mat-stroked-button routerLink="/">Back to home</a>
    }
    @case ('failed') {
      <p role="alert">Could not load the employee. Check your connection and try again.</p>
      <button mat-stroked-button type="button" (click)="retry()">Try again</button>
    }
    @case ('loaded') {
      @if (employee(); as person) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-card-title>{{ person.fullName }}</mat-card-title>
            <mat-card-subtitle>{{ person.status === 'ACTIVE' ? 'Active' : 'Former' }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <dl>
              <dt>Email</dt>
              <dd>{{ person.email }}</dd>
              <dt>Department</dt>
              <dd>{{ person.department.name }}</dd>
              <dt>Job title</dt>
              <dd>{{ person.jobTitle.name }}</dd>
              <dt>Country</dt>
              <dd>{{ person.country.name }}</dd>
              <dt>Hire date</dt>
              <dd>{{ person.hireDate }}</dd>
              <dt>Current salary (annual)</dt>
              <dd>{{ salaryText() }} <span class="note">since {{ person.currentSalary.effectiveDate }}</span></dd>
            </dl>
          </mat-card-content>
        </mat-card>
        <a mat-stroked-button routerLink="/">Back to home</a>
      }
    }
  }
</section>
```

`employee-detail.scss`:

```scss
.detail {
  max-width: 40rem;
  margin: 2rem auto;
  padding: 0 1rem;
}

dl {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 0.5rem 1.5rem;
}

dt {
  font-weight: 500;
}

dd {
  margin: 0;
}

.note {
  opacity: 0.7;
}
```

- [ ] **Step 4: Run and watch the tests pass**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/features/employee-detail/employee-detail.spec.ts`
Expected: 4 PASS. The spec uses `await fixture.whenStable()` after flushing; if the change detection timing makes a test flaky, follow the `vi.waitFor` pattern in `login.spec.ts`.

- [ ] **Step 5: Prove the tests**

Temporarily change `formatMinor(salary.amountMinor, currency.minorUnitDigits)` to `String(salary.amountMinor)`. Expected: the profile test FAILS on `1,250,000.50 INR`. Restore. Temporarily change `error.status === NOT_FOUND` to `false`. Expected: the 404 test FAILS. Restore.

- [ ] **Step 6: Add the route and its tests**

In `app.routes.ts`, add before the `'**'` entry:

```ts
  {
    path: 'employees/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/employee-detail/employee-detail').then((module) => module.EmployeeDetail),
  },
```

In `app.routes.spec.ts`, add inside the `describe`:

```ts
  it('sends a visitor who is not logged in from an employee page to the login page', async () => {
    await RouterTestingHarness.create('/employees/10432');

    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('lets a logged-in user open an employee page', async () => {
    await logIn();

    await RouterTestingHarness.create('/employees/10432');

    expect(TestBed.inject(Router).url).toBe('/employees/10432');
    TestBed.inject(HttpTestingController).expectOne('/api/employees/10432');
  });
```

Run: `. scripts/use-node.sh && cd frontend && npm run test`. Expected: PASS. Temporarily remove `canActivate: [authGuard]` from the new route: expected the logged-out test FAILS. Restore.

- [ ] **Step 7: Gates and commit suggestion**

Run: `. scripts/use-node.sh && cd frontend && npm run format:write && npm run format:check && npm run lint && npm run test && npm run typecheck`
Expected: succeed.

```bash
git add frontend/src/app/features/employee-detail frontend/src/app/app.routes.ts frontend/src/app/app.routes.spec.ts
```

Suggested message: `feat(ui): add the employee detail page with the current salary`

---

### Task 13: The create-employee form

**Files:**
- Create: `frontend/src/app/features/employee-form/salary-amount.validator.ts`
- Create: `frontend/src/app/features/employee-form/employee-form-fields.ts`
- Create: `frontend/src/app/features/employee-form/employee-form.ts`, `employee-form.html`, `employee-form.scss`
- Test: `frontend/src/app/features/employee-form/salary-amount.validator.spec.ts`, `employee-form.spec.ts`
- Modify: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.routes.spec.ts`, `frontend/src/app/features/home/home.html`, `home.ts`, `home.spec.ts`
- Modify: `frontend/src/app/shared/money/parse-major-to-minor.ts` (export `AmountError` if it was un-exported in Task 9)

**Interfaces:**
- Consumes: `parseMajorToMinor`, `AmountError`, `toFormErrors`, `FormErrors`, `ReferenceApi.loadLists`, `EmployeeApi.create`.
- Produces: the route `employees/new` (behind `authGuard`) and a link to it on the home page.

- [ ] **Step 1: Write the failing validator tests**

`salary-amount.validator.spec.ts`:

```ts
import { FormControl } from '@angular/forms';
import { describeAmountError, salaryAmountValidator } from './salary-amount.validator';

describe('salaryAmountValidator', () => {
  function check(text: string, digits: number | null) {
    return salaryAmountValidator(() => digits)(new FormControl(text));
  }

  it('accepts an amount that fits the currency', () => {
    expect(check('1,250,000.50', 2)).toBeNull();
  });

  it('leaves an empty field to the required validator', () => {
    expect(check('', 2)).toBeNull();
  });

  it('waits for a currency before judging the decimals', () => {
    expect(check('10.505', null)).toBeNull();
  });

  it('reports why an amount is not valid', () => {
    expect(check('10.505', 2)).toEqual({ amount: 'too-many-decimals' });
    expect(check('abc', 2)).toEqual({ amount: 'not-a-number' });
  });
});

describe('describeAmountError', () => {
  it('names the decimal places a currency allows', () => {
    expect(describeAmountError('too-many-decimals', 2)).toBe('Use at most 2 decimal places.');
  });

  it('says when a currency has no decimal places', () => {
    expect(describeAmountError('too-many-decimals', 0)).toBe('This currency has no decimal places.');
  });

  it('explains each of the other reasons', () => {
    expect(describeAmountError('not-a-number', 2)).toBe('Enter an amount such as 1,250,000.50.');
    expect(describeAmountError('not-positive', 2)).toBe('The amount must be greater than zero.');
    expect(describeAmountError('too-large', 2)).toBe('The amount is too large.');
  });
});
```

- [ ] **Step 2: Run, then implement the validator**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/features/employee-form/salary-amount.validator.spec.ts`. Expected: FAIL (module missing).

First make sure `AmountError` is exported from `parse-major-to-minor.ts`.

`salary-amount.validator.ts`:

```ts
import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { AmountError, parseMajorToMinor } from '../../shared/money/parse-major-to-minor';

/**
 * Checks the typed salary against the decimals of the chosen currency. An empty field is left to the
 * required validator, and nothing is judged until a currency is chosen.
 */
export function salaryAmountValidator(minorUnitDigits: () => number | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const text = String(control.value ?? '');
    const digits = minorUnitDigits();
    if (text.trim() === '' || digits === null) {
      return null;
    }
    const parsed = parseMajorToMinor(text, digits);
    return parsed.ok ? null : { amount: parsed.reason };
  };
}

export function describeAmountError(reason: AmountError, minorUnitDigits: number | null): string {
  switch (reason) {
    case 'not-a-number':
      return 'Enter an amount such as 1,250,000.50.';
    case 'too-many-decimals':
      return minorUnitDigits === 0
        ? 'This currency has no decimal places.'
        : `Use at most ${minorUnitDigits} decimal places.`;
    case 'not-positive':
      return 'The amount must be greater than zero.';
    case 'too-large':
      return 'The amount is too large.';
  }
}
```

Run again. Expected: 7 PASS. Prove: change `digits === null` to `false` in the guard: expected `waits for a currency...` FAILS. Restore.

- [ ] **Step 3: Create the field map**

`employee-form-fields.ts`:

```ts
export type ControlName =
  | 'fullName'
  | 'email'
  | 'hireDate'
  | 'departmentId'
  | 'jobTitleId'
  | 'countryCode'
  | 'currency'
  | 'salaryAmount'
  | 'salaryEffectiveDate';

/** The API names a field by its request path. This maps it to the form control that shows its error. */
export const SERVER_FIELD_TO_CONTROL: Readonly<Record<string, ControlName | undefined>> = {
  fullName: 'fullName',
  email: 'email',
  hireDate: 'hireDate',
  departmentId: 'departmentId',
  jobTitleId: 'jobTitleId',
  countryCode: 'countryCode',
  'initialSalary.currency': 'currency',
  'initialSalary.amountMinor': 'salaryAmount',
  'initialSalary.effectiveDate': 'salaryEffectiveDate',
};
```

- [ ] **Step 4: Write the failing form tests**

`employee-form.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { EmployeeForm } from './employee-form';

const LISTS = {
  countries: [{ code: 'IN', name: 'India' }],
  departments: [{ id: 3, name: 'Engineering' }],
  jobTitles: [{ id: 12, name: 'Software Engineer' }],
  currencies: [
    { code: 'INR', minorUnitDigits: 2 },
    { code: 'JPY', minorUnitDigits: 0 },
  ],
};

describe('EmployeeForm', () => {
  let fixture: ComponentFixture<EmployeeForm>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(EmployeeForm);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function loadLists(): Promise<void> {
    http.expectOne('/api/reference/countries').flush(LISTS.countries);
    http.expectOne('/api/reference/departments').flush(LISTS.departments);
    http.expectOne('/api/reference/job-titles').flush(LISTS.jobTitles);
    http.expectOne('/api/reference/currencies').flush(LISTS.currencies);
    await settle();
  }

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function type(name: string, value: string): void {
    const field = page.querySelector<HTMLInputElement | HTMLSelectElement>(`[formControlName="${name}"]`)!;
    field.value = value;
    field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  function fillValidForm(): void {
    type('fullName', 'Asha Rao');
    type('email', 'asha.rao@acme.example');
    type('hireDate', '2024-04-01');
    type('departmentId', '3');
    type('jobTitleId', '12');
    type('countryCode', 'IN');
    type('currency', 'INR');
    type('salaryAmount', '1,250,000.50');
  }

  function submit(): void {
    page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  }

  function errorText(): string {
    return Array.from(page.querySelectorAll('mat-error'))
      .map((element) => element.textContent?.trim())
      .join(' | ');
  }

  it('says it is loading the lists, then shows the form', async () => {
    expect(page.textContent).toContain('Loading');

    await loadLists();

    expect(page.querySelector('input[formControlName="fullName"]')).not.toBeNull();
    expect(page.querySelector('select[formControlName="departmentId"]')?.textContent).toContain('Engineering');
  });

  it('offers to try again when the lists cannot be loaded', async () => {
    http.expectOne('/api/reference/countries').error(new ProgressEvent('error'));
    http.expectOne('/api/reference/departments').flush([]);
    http.expectOne('/api/reference/job-titles').flush([]);
    http.expectOne('/api/reference/currencies').flush([]);
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not load');
    expect(page.querySelector('button')?.textContent).toContain('Try again');
  });

  it('does not call the server while required fields are empty and says what is missing', async () => {
    await loadLists();

    submit();
    fixture.detectChanges();

    http.expectNone('/api/employees');
    expect(errorText()).toContain('This field is required.');
  });

  it('sends the salary as exact minor units and opens the new employee', async () => {
    await loadLists();
    fillValidForm();

    submit();
    const call = http.expectOne('/api/employees');
    expect(call.request.body).toEqual({
      fullName: 'Asha Rao',
      email: 'asha.rao@acme.example',
      hireDate: '2024-04-01',
      departmentId: 3,
      jobTitleId: 12,
      countryCode: 'IN',
      initialSalary: { amountMinor: 125000050, currency: 'INR' },
    });
    call.flush({ id: 10432 });
    await settle();

    expect(navigate).toHaveBeenCalledWith('/employees/10432');
  });

  it('sends one request when the button is clicked twice', async () => {
    await loadLists();
    fillValidForm();

    submit();
    submit();

    http.expectOne('/api/employees').flush({ id: 10432 });
    await settle();
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('rejects decimals for a currency without minor units, even if the amount was typed first', async () => {
    await loadLists();
    fillValidForm();
    type('salaryAmount', '10.50');
    type('currency', 'JPY');
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    http.expectNone('/api/employees');
    expect(errorText()).toContain('This currency has no decimal places.');
  });

  it('shows a duplicate email under the email field and keeps what was typed', async () => {
    await loadLists();
    fillValidForm();

    submit();
    http.expectOne('/api/employees').flush(
      { code: 'EMAIL_ALREADY_EXISTS', detail: 'An employee with this email already exists.' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    expect(errorText()).toContain('An employee with this email already exists.');
    expect(page.querySelector<HTMLInputElement>('input[formControlName="fullName"]')!.value).toBe('Asha Rao');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('clears a server error on a field as soon as it is edited', async () => {
    await loadLists();
    fillValidForm();
    submit();
    http.expectOne('/api/employees').flush(
      { code: 'EMAIL_ALREADY_EXISTS', detail: 'An employee with this email already exists.' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();

    type('email', 'asha.other@acme.example');
    fixture.detectChanges();

    expect(errorText()).not.toContain('already exists');
  });

  it('shows the server wording for a broken salary rule in a banner', async () => {
    await loadLists();
    fillValidForm();

    submit();
    http.expectOne('/api/employees').flush(
      { code: 'NO_CURRENT_SALARY', detail: 'The hire date cannot be in the future.' },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await settle();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('The hire date cannot be in the future.');
  });

  it('puts a server error about the amount under the amount field', async () => {
    await loadLists();
    fillValidForm();

    submit();
    http.expectOne('/api/employees').flush(
      { errors: [{ field: 'initialSalary.amountMinor', message: 'must be greater than or equal to 1' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(errorText()).toContain('must be greater than or equal to 1');
  });

  it('enables the button again after a failed save', async () => {
    await loadLists();
    fillValidForm();
    const button = page.querySelector<HTMLButtonElement>('button[type="submit"]')!;

    submit();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    http.expectOne('/api/employees').error(new ProgressEvent('error'));
    await settle();

    expect(button.disabled).toBe(false);
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not save');
  });
});
```

- [ ] **Step 5: Run and watch it fail**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/features/employee-form/employee-form.spec.ts`
Expected: FAIL (module missing).

- [ ] **Step 6: Implement the component**

`employee-form.ts`:

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { toFormErrors, FormErrors } from '../../core/api/to-form-errors';
import { CreateEmployeeRequest, EmployeeApi } from '../../core/employees/employee-api';
import { ReferenceApi, ReferenceLists } from '../../core/reference/reference-api';
import { AmountError, parseMajorToMinor } from '../../shared/money/parse-major-to-minor';
import { ControlName, SERVER_FIELD_TO_CONTROL } from './employee-form-fields';
import { describeAmountError, salaryAmountValidator } from './salary-amount.validator';

@Component({
  selector: 'app-employee-form',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './employee-form.html',
  styleUrl: './employee-form.scss',
})
export class EmployeeForm implements OnInit {
  private readonly referenceApi = inject(ReferenceApi);
  private readonly employeeApi = inject(EmployeeApi);
  private readonly router = inject(Router);
  private readonly selectedCurrencyCode = signal('');

  protected readonly lists = signal<ReferenceLists | null>(null);
  protected readonly listsFailed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  private readonly minorUnitDigits = computed(
    () =>
      this.lists()?.currencies.find((currency) => currency.code === this.selectedCurrencyCode())
        ?.minorUnitDigits ?? null,
  );
  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    hireDate: ['', Validators.required],
    departmentId: ['', Validators.required],
    jobTitleId: ['', Validators.required],
    countryCode: ['', Validators.required],
    currency: ['', Validators.required],
    salaryAmount: ['', [Validators.required, salaryAmountValidator(() => this.minorUnitDigits())]],
    salaryEffectiveDate: [''],
  });

  constructor() {
    // The decimals an amount may have depend on the currency, so choosing one re-checks the amount.
    this.form.controls.currency.valueChanges.pipe(takeUntilDestroyed()).subscribe((code) => {
      this.selectedCurrencyCode.set(code);
      this.form.controls.salaryAmount.updateValueAndValidity();
    });
  }

  ngOnInit(): void {
    void this.loadLists();
  }

  protected retryLoadingLists(): void {
    void this.loadLists();
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
    if (errors['email']) {
      return 'Enter a valid email address.';
    }
    if (errors['amount']) {
      return describeAmountError(errors['amount'] as AmountError, this.minorUnitDigits());
    }
    return 'This value is not valid.';
  }

  protected async createEmployee(): Promise<void> {
    // The button is disabled while saving, but a second click can arrive before the page redraws.
    if (this.submitting()) {
      return;
    }
    const request = this.form.valid ? this.toRequest() : null;
    if (request === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    try {
      const created = await this.employeeApi.create(request);
      await this.router.navigateByUrl(`/employees/${created.id}`);
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.showServerErrors(toFormErrors(error));
    } finally {
      this.submitting.set(false);
    }
  }

  private toRequest(): CreateEmployeeRequest | null {
    const value = this.form.getRawValue();
    const digits = this.minorUnitDigits();
    const amount = digits === null ? null : parseMajorToMinor(value.salaryAmount, digits);
    if (amount === null || !amount.ok) {
      return null;
    }
    return {
      fullName: value.fullName,
      email: value.email,
      hireDate: value.hireDate,
      departmentId: Number(value.departmentId),
      jobTitleId: Number(value.jobTitleId),
      countryCode: value.countryCode,
      initialSalary: {
        effectiveDate: value.salaryEffectiveDate || undefined,
        amountMinor: amount.amountMinor,
        currency: value.currency,
      },
    };
  }

  /** A field with no control on this form has nowhere to show its message, so it goes in the banner. */
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

  private async loadLists(): Promise<void> {
    this.listsFailed.set(false);
    try {
      this.lists.set(await this.referenceApi.loadLists());
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.listsFailed.set(true);
    }
  }
}
```

`employee-form.html`:

```html
<!-- The labels and messages are hard-coded in English for now. For localization they should come from one central place, such as Angular's i18n. -->
<mat-card class="form-card" appearance="outlined">
  <mat-card-header>
    <mat-card-title>New employee</mat-card-title>
    <mat-card-subtitle>Enter the employee and their first annual base salary.</mat-card-subtitle>
  </mat-card-header>
  <mat-card-content>
    @if (listsFailed()) {
      <p role="alert">Could not load the lists for this form. Check your connection and try again.</p>
      <button mat-stroked-button type="button" (click)="retryLoadingLists()">Try again</button>
    } @else if (lists(); as available) {
      <form [formGroup]="form" (ngSubmit)="createEmployee()" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Full name</mat-label>
          <input matInput formControlName="fullName" autocomplete="name" />
          <mat-error>{{ errorMessage('fullName') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="email" />
          <mat-error>{{ errorMessage('email') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Hire date</mat-label>
          <input matInput type="date" formControlName="hireDate" />
          <mat-error>{{ errorMessage('hireDate') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Department</mat-label>
          <select matNativeControl formControlName="departmentId">
            <option value=""></option>
            @for (department of available.departments; track department.id) {
              <option [value]="department.id">{{ department.name }}</option>
            }
          </select>
          <mat-error>{{ errorMessage('departmentId') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Job title</mat-label>
          <select matNativeControl formControlName="jobTitleId">
            <option value=""></option>
            @for (jobTitle of available.jobTitles; track jobTitle.id) {
              <option [value]="jobTitle.id">{{ jobTitle.name }}</option>
            }
          </select>
          <mat-error>{{ errorMessage('jobTitleId') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Country</mat-label>
          <select matNativeControl formControlName="countryCode">
            <option value=""></option>
            @for (country of available.countries; track country.code) {
              <option [value]="country.code">{{ country.name }}</option>
            }
          </select>
          <mat-error>{{ errorMessage('countryCode') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Salary currency</mat-label>
          <select matNativeControl formControlName="currency">
            <option value=""></option>
            @for (currency of available.currencies; track currency.code) {
              <option [value]="currency.code">{{ currency.code }}</option>
            }
          </select>
          <mat-error>{{ errorMessage('currency') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Annual base salary</mat-label>
          <input matInput formControlName="salaryAmount" inputmode="decimal" />
          <mat-hint>For example 1,250,000.50</mat-hint>
          <mat-error>{{ errorMessage('salaryAmount') }}</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Salary effective date (optional)</mat-label>
          <input matInput type="date" formControlName="salaryEffectiveDate" />
          <mat-hint>Defaults to the hire date</mat-hint>
          <mat-error>{{ errorMessage('salaryEffectiveDate') }}</mat-error>
        </mat-form-field>

        @if (formError(); as message) {
          <p class="error" role="alert">{{ message }}</p>
        }

        <button mat-flat-button type="submit" [disabled]="submitting()">
          {{ submitting() ? 'Saving…' : 'Create employee' }}
        </button>
      </form>
    } @else {
      <p>Loading…</p>
    }
  </mat-card-content>
</mat-card>
```

`employee-form.scss`:

```scss
.form-card {
  max-width: 36rem;
  margin: 2rem auto;
}

form {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding-top: 1rem;
}

.error {
  color: var(--mat-sys-error);
}
```

- [ ] **Step 7: Run and watch the tests pass**

Run: `. scripts/use-node.sh && cd frontend && npm run test -- --include src/app/features/employee-form/employee-form.spec.ts`
Expected: 11 PASS. Likely snags to read the failure for, not to patch around: a select option not yet rendered when `type` runs (call `fixture.detectChanges()` inside `loadLists`, already done by `settle`), and `ExpressionChangedAfterItHasBeenChecked` from `setErrors` during the response (set errors before change detection, as written).

- [ ] **Step 8: Prove the tests**

1. In `createEmployee`, remove the `if (this.submitting()) { return; }` guard. Expected: `sends one request when the button is clicked twice` FAILS (two requests). Restore. Then remove `this.submitting.set(true);`. Expected: that test and `enables the button again after a failed save` FAIL. Restore.
2. In the constructor, delete the `updateValueAndValidity()` line. Expected: `rejects decimals for a currency without minor units, even if the amount was typed first` FAILS. Restore.
3. In `showServerErrors`, change `SERVER_FIELD_TO_CONTROL[field]` to always `undefined`. Expected: the duplicate email and amount-field tests FAIL. Restore.
4. In `toRequest`, change `Number(value.departmentId)` to `value.departmentId as unknown as number`. Expected: `sends the salary as exact minor units...` FAILS (the body has `'3'`). Restore.

- [ ] **Step 9: Add the routes, the home link and their tests**

In `app.routes.ts`, add before `employees/:id`:

```ts
  {
    path: 'employees/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/employee-form/employee-form').then((module) => module.EmployeeForm),
  },
```

In `app.routes.spec.ts`, add:

```ts
  it('sends a visitor who is not logged in from the new employee page to the login page', async () => {
    await RouterTestingHarness.create('/employees/new');

    expect(TestBed.inject(Router).url).toBe('/login');
  });
```

In `home.ts`, add `RouterLink` to the imports array (import it from `@angular/router`) and to `imports: [MatButtonModule, RouterLink]`. In `home.html`, add before the sign-out button:

```html
  <a mat-flat-button routerLink="/employees/new">New employee</a>
```

In `home.spec.ts`, add:

```ts
  it('links to the new employee form', () => {
    const link = page.querySelector<HTMLAnchorElement>('a[href="/employees/new"]');

    expect(link?.textContent).toContain('New employee');
  });
```

The existing sign-out test uses `page.querySelector('button')`; the new link is an `<a>`, so it still finds the sign-out button.

Run: `. scripts/use-node.sh && cd frontend && npm run test`. Expected: PASS. Prove: remove `routerLink` from the home link: expected the link test FAILS. Restore.

- [ ] **Step 10: Full frontend gates**

Run: `. scripts/use-node.sh && cd frontend && npm run format:write && npm run format:check && npm run lint && npm run test && npm run typecheck && npm run knip && npm run build`
Expected: all succeed. If Knip flags an unused export (`FormErrors`, `ParsedAmount`, `Currency`, `NamedReference`), remove the `export` from the one that no other file imports, and re-run.

- [ ] **Step 11: Stage and suggest the commit**

```bash
git add frontend/src/app/features/employee-form frontend/src/app/features/home frontend/src/app/app.routes.ts frontend/src/app/app.routes.spec.ts frontend/src/app/shared/money
```

Suggested message: `feat(ui): add the create employee form with exact salary entry and server error placement`

---

### Task 14: Docs, full verification and hand-off

**Files:**
- Modify: `docs/requirements.md` (the salary history item, line 34)
- Modify: `docs/ai-usage.md` (append a session entry)
- Modify: `docs/design.md` (the slice 2 notes)

- [ ] **Step 1: Record the future hire date limit**

In `docs/requirements.md`, at the end of item 4 of "Scope (MVP)" (the "Salary history" line), append:

```
 A new employee's hire date and first salary date cannot be in the future, because a new employee must have a current salary. Entering an accepted offer before the start date belongs to recruitment, which is out of scope.
```

- [ ] **Step 2: Record the slice 2 build decisions in the design**

In `docs/design.md`, under "Domain decisions", add row 12 to the table:

```
| 12 | Features read each other's data through public lookups and services, never through entities or repositories. `employee` stores plain keys for the department, job title and country, and `reference` exposes a public `ReferenceLookup`. `salary` stores a plain `employeeId`. | Keeps entities and repositories private to their package, so no feature can change another's rows or depend on how they are stored. The foreign keys still protect the data. |
```

- [ ] **Step 3: Add the session entry to the AI usage log**

Append to `docs/ai-usage.md` a new section "Session N: slice 2 design and plan (19 Oct 2026)" using the file's existing layout (What we did, Prompts, What the AI got wrong or I had to correct). Fill it from this session:

- What we did: eleven design decisions taken one at a time (validator location, error mapping, salary ownership, duplicate email, reference lookups, request validation, future hire dates, detail assembly, test layers, money conversion, form error placement), then the spec, then this plan.
- Prompts: the opening prompt ("Put the design decisions to me one at a time, with a recommendation"), and the follow-ups "expand it", "what's clean and explainable?" and "my concern was business logic should be separate?".
- What the AI got wrong or I had to correct: the spec said `Employee` would hold lazy `@ManyToOne` links to the reference entities and that the service would call their repositories, but those types are package-private. This was found only when the plan was written from the real code, not when the spec was written. Add what you corrected during execution, as it happens.

Write this entry in your own voice at the end of the work session, not now.

- [ ] **Step 4: Full verification**

Run: `make check`
Expected: backend checks and integration tests, frontend format, lint and tests, typecheck, Knip and build all succeed.

Then run the app end to end once: start PostgreSQL (`docker compose up -d --wait` if the compose file defines it), run the API and `ng serve`, sign in, create an employee in INR and one in JPY, open each detail page, and try a duplicate email and a future hire date. Report exactly what you saw. Do not claim the slice works from the test results alone.

- [ ] **Step 5: Stage and suggest the commit**

```bash
git add docs/requirements.md docs/design.md docs/ai-usage.md docs/superpowers
```

Suggested message: `docs: record the slice 2 limits and decisions, with its spec and plan`

---

## After this slice

The backend skill is next, as the slice plan says: it captures the repeated pattern (an endpoint plus a database change) now that one read and one write both exist. It is a separate piece of work and is not part of this plan.

## Self-review notes

- **Spec coverage.** Decision 1: Task 1. 2: Tasks 1 and 7. 3: Tasks 5 and 6. 4: Task 6. 5: Tasks 4 and 6. 6: Tasks 6 and 7. 7: Tasks 1, 6, 8 and 14. 8: Tasks 2 and 6. 9: the test layers across Tasks 1 to 8. 10: Task 9. 11: Tasks 10 and 13. Migration: Task 3. Frontend routes, detail and form: Tasks 12 and 13. Recorded risks: constraint-name detection is proven in Task 6 Step 8, the cycle and the repeated "active" rule are spec items for later slices and need no code now.
- **Known unverified points**, each with a stated fallback in its task: the `ResponseEntityExceptionHandler` override signature in this Spring version (Task 7), Hibernate's constraint name on PostgreSQL (Task 6), the Angular `--include` test flag (Task 9), and Knip's treatment of exported types (Tasks 9 and 13).
