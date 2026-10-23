# Slice 4: the CSV loader and the 10,000-employee seed, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (the owner chose to run it one task at a time) or superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax. Also follow the project skill `.claude/skills/backend-slice/SKILL.md`.

**Goal:** HR can load an employee CSV from the command line, and `make seed` produces and loads a deterministic 10,000-employee seed with realistic pay.

**Architecture:** A new feature package `dataload`. Its `rules/` folder holds pure classes: the CSV columns, an amount parser that converts major units to minor units, a row parser and a CSV reader. Its `seed/` folder holds the pay model, the generator and the CSV writer. The `dataload` package itself holds the Spring parts: a loader that calls the public `EmployeeService.create` once per row, and two runners that run as modes of the existing jar (`--acme.load=<file>` and `--acme.seed-csv=<file>`). `reference` gains two lookups by name.

**Tech Stack:** Java 25, Spring Boot 4.1, PostgreSQL 17 (Testcontainers), JUnit 5, AssertJ, Apache Commons CSV 1.14.1 (the one new dependency).

**Spec:** [2026-10-23-slice-4-csv-loader-and-seed-design.md](../specs/2026-10-23-slice-4-csv-loader-and-seed-design.md). Slice 3 plan, for conventions: [2026-10-22-slice-3-salary-history.md](2026-10-22-slice-3-salary-history.md).

## How this plan is run

- One task at a time. After each task, **stage the exact files and stop** with a suggested `type(scope): subject` message. **The owner makes every commit.** Never `git add .`. No worktree.
- Every task is test first and ends with breaking the code to prove each new test, then the **full** gates (`make test-backend`, and `make test-integration` for tasks with integration tests). A mutation must compile (`-Werror` and Error Prone reject an unused field or private method), and its target text must be asserted to exist before it is trusted. Quote test filters (`--tests '*Name'`).
- Comments only when the reason cannot be seen from the code. A one-line class comment is the repo convention. Do not comment each change.
- The Docker daemon must be running for integration tests.

## Deviations from the spec, found while probing the code and the library

1. **The loader runs with `--server.port=0`, not a switched-off web server.** `SecurityConfig` and the controllers expect a web application, and a random port also avoids clashing with a running API. The mode ends with `System.exit(SpringApplication.exit(context))` in `main`, which is the only way to return an exit status.
2. **Row numbers count the header as row 1.** Commons CSV counts records, not lines, so blank lines are skipped and a quoted newline counts once. The spec said "line number".
3. **Duplicate headers are rejected with `DuplicateHeaderMode.DISALLOW`.** Commons CSV allows them by default, and its older flag is deprecated, which `-Werror` would fail on.
4. **`LoadRunner` prints the elapsed milliseconds** (from the injected clock), so the 10,000-row timing the spec asks for is built in.
5. **The seed's dates depend on today's date.** The generator takes today from the injected clock (as the spec says), so the same file comes out for the same day, not across days. Recorded in `docs/tradeoffs.md`.

## Global Constraints

- Money is an integer in minor units. The loader converts the CSV's major units with the currency's minor-unit digits and **never rounds or truncates**: more decimals than the currency allows is a rejected row. The cap is `Money.MAX_AMOUNT_MINOR` (9007199254740991).
- Every row goes through `EmployeeService.create`, so imported data obeys the same rules as typed data. `dataload` imports only public classes of other features (`employee`, `employee.api`, `employee.errors`, `reference`, `salary.rules`). Nothing imports `dataload` except `PeopleManagementApplication`.
- A rejected **row** (bad text, unknown name, a broken rule, a duplicate email, a failed validation) never stops the run. Anything else aborts the run.
- `terminationDate` must be empty: a non-empty value is rejected with "Termination dates are not supported yet." (slice 6).
- The CSV header is `fullName,email,hireDate,department,jobTitle,countryCode,currency,salary,salaryEffectiveDate,terminationDate`. Dates are ISO. `salary` is major units with a dot and no thousands separators.
- Backend gates: `-Werror`, Error Prone, Checkstyle (120 columns, methods up to 25 non-empty lines, up to 5 parameters, cyclomatic complexity up to 10, no single-letter names, a private constructor on utility classes, a `package-info.java` per package), SpotBugs on tests too (no `\n` inside a text block passed to `.formatted()`).
- Before suggesting a commit, review the change against the engineering principles in the root `CLAUDE.md`.

## Review Focus

Behaviors the spec implies but a task could leave untested. Each has a test in the task named in brackets.

1. **A file saved by Excel loads**: a byte-order mark, `\r\n` line endings, and a quoted name with a comma and a quote. [Task 2]
2. **More decimals than the currency allows is rejected, never truncated**: `6500000.5` in JPY and `1.234` in INR. [Task 1, Task 5]
3. **A bad row never stops the others, and its row number matches the file.** [Task 5]
4. **A wrong or repeated header fails the whole file before any row is created.** [Task 2, Task 5]
5. **A generated file is readable by the loader, and the same input gives identical bytes.** [Task 3, Task 6]

Known gap, on purpose: "an unexpected failure aborts the run" (spec decision 8) has no test, because a database failure cannot be provoked without mocking the service. The code states it by catching exactly three exception types, and Task 5 lists it in the review checklist.

---

## File structure

Backend, under `backend/src/main/java/com/acme/peoplemanagement/`:

| File | Responsibility |
|---|---|
| `dataload/package-info.java` | package comment |
| `dataload/BatchMode.java` | public: the two property names, and whether a run asked for a batch mode |
| `dataload/EmployeeCsvLoader.java` | reads rows, resolves names, validates, calls `EmployeeService.create`, collects the report |
| `dataload/LoadReport.java`, `RejectedRow.java` | the loader's result |
| `dataload/LoadRunner.java` | `--acme.load`: runs the loader, prints the report, sets the exit status |
| `dataload/SeedRunner.java` | `--acme.seed-csv`: generates and writes the CSV |
| `dataload/rules/package-info.java` | package comment |
| `dataload/rules/Column.java` | the CSV columns, in order |
| `dataload/rules/RowRejectedException.java` | a row cannot be loaded: its field and reason |
| `dataload/rules/FileRejectedException.java` | the whole file cannot be loaded |
| `dataload/rules/MajorUnits.java` | major-unit text to minor units, and back |
| `dataload/rules/EmployeeRow.java` | one parsed row, and its cells |
| `dataload/rules/EmployeeCsv.java` | reads a CSV into numbered lines of cells |
| `dataload/seed/package-info.java` | package comment |
| `dataload/seed/PayModel.java` | titles, countries, and the pay formula |
| `dataload/seed/SeedGenerator.java` | the deterministic employees |
| `dataload/seed/SeedCsvWriter.java` | writes rows as CSV |
| `reference/ReferenceLookup.java` | gains `departmentByName` and `jobTitleByName` |
| `reference/DepartmentRepository.java`, `JobTitleRepository.java` | gain `findByNameIgnoreCase` |
| `PeopleManagementApplication.java` | exits after a batch mode |

Elsewhere: `backend/build.gradle` (the dependency), `scripts/seed.sh`, `Makefile`, `.gitignore`, `docs/design.md`, `docs/tradeoffs.md`, `docs/requirements.md`, `docs/ai-usage.md`, and the spec's two corrections.

Tests, under `backend/src/test/java/com/acme/peoplemanagement/`: `dataload/rules/MajorUnitsTest`, `EmployeeRowTest`, `EmployeeCsvTest`; `dataload/seed/PayModelTest`, `SeedGeneratorTest`, `SeedCsvWriterTest`, `SeedReferenceIntegrationTest`; `dataload/BatchModeTest`, `SeedRunnerTest`, `EmployeeCsvLoaderIntegrationTest`, `LoadRunnerIntegrationTest`; `reference/ReferenceLookupIntegrationTest` (extended).

---

### Task 1: The dependency, the columns and the amount parser

**Files:**
- Modify: `backend/build.gradle`
- Create: `dataload/rules/package-info.java`, `Column.java`, `RowRejectedException.java`, `MajorUnits.java`
- Test: `dataload/rules/MajorUnitsTest.java`

**Interfaces:**
- Produces: `enum Column` with `header()`, `required()` and `static List<String> headers()`; `RowRejectedException(String field, String reason)` with `field()` (the reason is `getMessage()`); `MajorUnits.toMinor(String text, int minorUnitDigits) -> long` and `MajorUnits.format(long amountMinor, int minorUnitDigits) -> String`.

- [ ] **Step 1: Add the dependency**

In `backend/build.gradle`, in `dependencies`, after `implementation 'org.springframework.boot:spring-boot-starter-webmvc'`:

```groovy
    implementation 'org.apache.commons:commons-csv:1.14.1'
```

- [ ] **Step 2: Write the failing test**

`backend/src/test/java/com/acme/peoplemanagement/dataload/rules/MajorUnitsTest.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class MajorUnitsTest {

    @Test
    void convertsAnAmountWithTwoDecimalsToMinorUnits() {
        assertThat(MajorUnits.toMinor("1250000.00", 2)).isEqualTo(125_000_000L);
        assertThat(MajorUnits.toMinor("1250000.5", 2)).isEqualTo(125_000_050L);
        assertThat(MajorUnits.toMinor("1250000", 2)).isEqualTo(125_000_000L);
    }

    @Test
    void keepsWholeUnitsForACurrencyWithNoDecimals() {
        assertThat(MajorUnits.toMinor("6500000", 0)).isEqualTo(6_500_000L);
    }

    @Test
    void acceptsZerosBeyondTheDecimalsTheCurrencyAllows() {
        assertThat(MajorUnits.toMinor("10.00", 0)).isEqualTo(10L);
        assertThat(MajorUnits.toMinor("1.500", 2)).isEqualTo(150L);
    }

    @Test
    void rejectsDecimalsForACurrencyWithNone() {
        assertRejected("6500000.5", 0, "This currency has no decimal places.");
        assertRejected("10.01", 0, "This currency has no decimal places.");
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfCuttingThemOff() {
        assertRejected("1.234", 2, "This currency allows at most 2 decimal places.");
    }

    @Test
    void rejectsZero() {
        assertRejected("0", 2, "Amount must be greater than zero.");
        assertRejected("0.00", 2, "Amount must be greater than zero.");
    }

    @Test
    void rejectsAnythingThatIsNotAPlainNumber() {
        final String message = "Amount must be a plain number such as 1250000.00, with no thousands separators.";
        assertRejected("1,250,000.00", 2, message);
        assertRejected("-5", 2, message);
        assertRejected("abc", 2, message);
        assertRejected("", 2, message);
        assertRejected(" 5", 2, message);
        assertRejected("1e5", 2, message);
    }

    @Test
    void acceptsTheLargestAmountAndRejectsOneMore() {
        assertThat(MajorUnits.toMinor("90071992547409.91", 2)).isEqualTo(9_007_199_254_740_991L);
        assertRejected("90071992547409.92", 2, "Amount is too large.");
        assertRejected("99999999999999999999999", 2, "Amount is too large.");
    }

    @Test
    void reportsEveryAmountProblemAgainstTheSalaryField() {
        assertThatExceptionOfType(RowRejectedException.class)
                .isThrownBy(() -> MajorUnits.toMinor("abc", 2))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("salary"));
    }

    @Test
    void formatsMinorUnitsAsMajorUnitsWithTheCurrencysDecimals() {
        assertThat(MajorUnits.format(125_000_000L, 2)).isEqualTo("1250000.00");
        assertThat(MajorUnits.format(5L, 2)).isEqualTo("0.05");
        assertThat(MajorUnits.format(6_500_000L, 0)).isEqualTo("6500000");
    }

    @Test
    void formatsWhatItParses() {
        assertThat(MajorUnits.toMinor(MajorUnits.format(987_654_321L, 2), 2)).isEqualTo(987_654_321L);
    }

    private static void assertRejected(final String text, final int digits, final String reason) {
        assertThatExceptionOfType(RowRejectedException.class)
                .isThrownBy(() -> MajorUnits.toMinor(text, digits))
                .withMessage(reason);
    }
}
```

- [ ] **Step 3: Run it, expect a compile failure**

Run: `cd backend && ./gradlew test --tests '*MajorUnitsTest' --console=plain --warning-mode=none`
Expected: FAIL to compile, "cannot find symbol" for `MajorUnits` and `RowRejectedException`.

- [ ] **Step 4: Write the minimal code**

`backend/src/main/java/com/acme/peoplemanagement/dataload/rules/package-info.java`:

```java
/**
 * The rules for reading an employee CSV: plain Java with no Spring or database, so the tests run in milliseconds.
 */
package com.acme.peoplemanagement.dataload.rules;
```

`.../dataload/rules/Column.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

import java.util.Arrays;
import java.util.List;

/** The columns of the employee CSV, in the order the generator writes them. */
public enum Column {
    FULL_NAME("fullName", true),
    EMAIL("email", true),
    HIRE_DATE("hireDate", true),
    DEPARTMENT("department", true),
    JOB_TITLE("jobTitle", true),
    COUNTRY_CODE("countryCode", true),
    CURRENCY("currency", true),
    SALARY("salary", true),
    SALARY_EFFECTIVE_DATE("salaryEffectiveDate", false),
    TERMINATION_DATE("terminationDate", false);

    private final String header;
    private final boolean required;

    Column(final String header, final boolean required) {
        this.header = header;
        this.required = required;
    }

    public String header() {
        return header;
    }

    public boolean required() {
        return required;
    }

    public static List<String> headers() {
        return Arrays.stream(values()).map(Column::header).toList();
    }
}
```

`.../dataload/rules/RowRejectedException.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

/** One row cannot be loaded. The reason is written for the person who owns the file. */
public final class RowRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public RowRejectedException(final String field, final String reason) {
        super(reason);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
```

`.../dataload/rules/MajorUnits.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

import com.acme.peoplemanagement.salary.rules.Money;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Converts the major-unit amounts a spreadsheet holds (1250000.00) to minor units and back, without rounding. */
public final class MajorUnits {

    private static final String FIELD = "salary";
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+(\\.\\d+)?");
    private static final BigDecimal MAX_AMOUNT_MINOR = BigDecimal.valueOf(Money.MAX_AMOUNT_MINOR);

    private MajorUnits() {
    }

    public static long toMinor(final String text, final int minorUnitDigits) {
        if (!PLAIN_NUMBER.matcher(text).matches()) {
            throw rejected("Amount must be a plain number such as 1250000.00, with no thousands separators.");
        }
        final BigDecimal amount = new BigDecimal(text);
        if (amount.stripTrailingZeros().scale() > minorUnitDigits) {
            throw rejected(decimalsMessage(minorUnitDigits));
        }
        final BigDecimal minor = amount.movePointRight(minorUnitDigits);
        if (minor.signum() == 0) {
            throw rejected("Amount must be greater than zero.");
        }
        if (minor.compareTo(MAX_AMOUNT_MINOR) > 0) {
            throw rejected("Amount is too large.");
        }
        return minor.longValueExact();
    }

    public static String format(final long amountMinor, final int minorUnitDigits) {
        return BigDecimal.valueOf(amountMinor, minorUnitDigits).toPlainString();
    }

    private static String decimalsMessage(final int minorUnitDigits) {
        return minorUnitDigits == 0
                ? "This currency has no decimal places."
                : "This currency allows at most " + minorUnitDigits + " decimal places.";
    }

    private static RowRejectedException rejected(final String reason) {
        return new RowRejectedException(FIELD, reason);
    }
}
```

- [ ] **Step 5: Run the test, expect PASS**

Run: `cd backend && ./gradlew test --tests '*MajorUnitsTest' --console=plain --warning-mode=none`
Expected: 11 tests pass.

- [ ] **Step 6: Prove each test by breaking the code**

For each mutation: assert the target text exists in `MajorUnits.java`, change it, run the filtered test, read the failing names, restore, and confirm the file is byte-identical to the original (`git diff --stat` shows nothing for that file once staged copies match, or compare with a saved copy).

| Mutation | Expected failing tests |
|---|---|
| `scale() > minorUnitDigits` to `scale() >= minorUnitDigits` | `convertsAnAmountWithTwoDecimalsToMinorUnits`, `acceptsZerosBeyondTheDecimalsTheCurrencyAllows` |
| `scale() > minorUnitDigits` to `scale() > minorUnitDigits + 1` | `rejectsDecimalsForACurrencyWithNone`, `rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfCuttingThemOff` |
| `minor.signum() == 0` to `minor.signum() < 0` | `rejectsZero` |
| `.compareTo(MAX_AMOUNT_MINOR) > 0` to `.compareTo(MAX_AMOUNT_MINOR) > 1` | `acceptsTheLargestAmountAndRejectsOneMore` |
| the pattern `\\d+(\\.\\d+)?` to `[\\d,]+(\\.\\d+)?` | `rejectsAnythingThatIsNotAPlainNumber` (the `1,250,000.00` case now reaches `BigDecimal` and throws a `NumberFormatException`) |
| `private static final String FIELD = "salary"` to `"amount"` | `reportsEveryAmountProblemAgainstTheSalaryField` |
| `BigDecimal.valueOf(amountMinor, minorUnitDigits)` to `BigDecimal.valueOf(amountMinor, 2)` | `formatsMinorUnitsAsMajorUnitsWithTheCurrencysDecimals` |

- [ ] **Step 7: Gate and stage**

Run: `make test-backend`
Expected: exit 0.

```bash
git add backend/build.gradle backend/src/main/java/com/acme/peoplemanagement/dataload/rules backend/src/test/java/com/acme/peoplemanagement/dataload/rules
```

Suggested message: `feat(loader): add the CSV columns and a lossless major-to-minor amount parser`

---

### Task 2: Parse a row and read a CSV file

**Files:**
- Create: `dataload/rules/FileRejectedException.java`, `EmployeeRow.java`, `EmployeeCsv.java`
- Test: `dataload/rules/EmployeeRowTest.java`, `EmployeeCsvTest.java`

**Interfaces:**
- Consumes: `Column`, `RowRejectedException` (Task 1).
- Produces: `FileRejectedException(String reason)`; `EmployeeRow(String fullName, String email, LocalDate hireDate, String department, String jobTitle, String countryCode, String currency, String salary, LocalDate salaryEffectiveDate)` with `static EmployeeRow parse(Map<Column, String> cells)` and `Map<Column, String> cells()`; `EmployeeCsv.read(Reader) -> List<EmployeeCsv.Line>` where `Line(int rowNumber, Map<Column, String> cells)`.

- [ ] **Step 1: Write the failing row test**

`.../dataload/rules/EmployeeRowTest.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmployeeRowTest {

    private static final EmployeeRow ROW = new EmployeeRow("Asha Rao", "asha@acme.example", LocalDate.of(2024, 4, 1),
            "Engineering", "Software Engineer", "IN", "INR", "1250000.00", LocalDate.of(2025, 4, 1));

    @Test
    void parsesAFullRow() {
        assertThat(EmployeeRow.parse(ROW.cells())).isEqualTo(ROW);
    }

    @Test
    void leavesTheSalaryDateEmptyWhenTheCellIsEmpty() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.SALARY_EFFECTIVE_DATE, "");

        assertThat(EmployeeRow.parse(cells).salaryEffectiveDate()).isNull();
    }

    @Test
    void writesAnEmptySalaryDateAsAnEmptyCell() {
        final EmployeeRow withoutDate = new EmployeeRow("Asha Rao", "asha@acme.example", LocalDate.of(2024, 4, 1),
                "Engineering", "Software Engineer", "IN", "INR", "1250000.00", null);

        assertThat(withoutDate.cells()).containsEntry(Column.SALARY_EFFECTIVE_DATE, "");
        assertThat(withoutDate.cells()).containsEntry(Column.TERMINATION_DATE, "");
    }

    @Test
    void rejectsAMissingRequiredValueAgainstItsColumn() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.DEPARTMENT, "");

        assertRejected(cells, "department", "A value is required.");
    }

    @Test
    void rejectsADateThatIsNotInIsoFormat() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.HIRE_DATE, "01/04/2024");

        assertRejected(cells, "hireDate", "Dates must look like 2024-04-01 (year-month-day).");
    }

    @Test
    void rejectsADateThatDoesNotExist() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.SALARY_EFFECTIVE_DATE, "2024-02-30");

        assertRejected(cells, "salaryEffectiveDate", "Dates must look like 2024-04-01 (year-month-day).");
    }

    @Test
    void rejectsATerminationDateUntilLeaversAreSupported() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.TERMINATION_DATE, "2025-12-31");

        assertRejected(cells, "terminationDate", "Termination dates are not supported yet.");
    }

    @Test
    void treatsAnAbsentCellAsEmpty() {
        final Map<Column, String> cells = new EnumMap<>(ROW.cells());
        cells.remove(Column.SALARY_EFFECTIVE_DATE);
        cells.remove(Column.TERMINATION_DATE);

        assertThat(EmployeeRow.parse(cells).salaryEffectiveDate()).isNull();
    }

    private static void assertRejected(final Map<Column, String> cells, final String field, final String reason) {
        assertThatExceptionOfType(RowRejectedException.class)
                .isThrownBy(() -> EmployeeRow.parse(cells))
                .withMessage(reason)
                .satisfies(exception -> assertThat(exception.field()).isEqualTo(field));
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `cd backend && ./gradlew test --tests '*EmployeeRowTest' --console=plain --warning-mode=none`
Expected: FAIL to compile, "cannot find symbol" for `EmployeeRow`.

- [ ] **Step 3: Write `EmployeeRow`**

`.../dataload/rules/EmployeeRow.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;

/** One employee as the CSV describes them, with the names and the amount still as written. */
public record EmployeeRow(
        String fullName,
        String email,
        LocalDate hireDate,
        String department,
        String jobTitle,
        String countryCode,
        String currency,
        String salary,
        LocalDate salaryEffectiveDate) {

    private static final String DATE_HINT = "Dates must look like 2024-04-01 (year-month-day).";

    public static EmployeeRow parse(final Map<Column, String> cells) {
        if (!cell(cells, Column.TERMINATION_DATE).isEmpty()) {
            throw new RowRejectedException(
                    Column.TERMINATION_DATE.header(), "Termination dates are not supported yet.");
        }
        return new EmployeeRow(
                required(cells, Column.FULL_NAME),
                required(cells, Column.EMAIL),
                date(required(cells, Column.HIRE_DATE), Column.HIRE_DATE),
                required(cells, Column.DEPARTMENT),
                required(cells, Column.JOB_TITLE),
                required(cells, Column.COUNTRY_CODE),
                required(cells, Column.CURRENCY),
                required(cells, Column.SALARY),
                optionalDate(cells, Column.SALARY_EFFECTIVE_DATE));
    }

    public Map<Column, String> cells() {
        final Map<Column, String> cells = new EnumMap<>(Column.class);
        cells.put(Column.FULL_NAME, fullName);
        cells.put(Column.EMAIL, email);
        cells.put(Column.HIRE_DATE, hireDate.toString());
        cells.put(Column.DEPARTMENT, department);
        cells.put(Column.JOB_TITLE, jobTitle);
        cells.put(Column.COUNTRY_CODE, countryCode);
        cells.put(Column.CURRENCY, currency);
        cells.put(Column.SALARY, salary);
        cells.put(Column.SALARY_EFFECTIVE_DATE, salaryEffectiveDate == null ? "" : salaryEffectiveDate.toString());
        cells.put(Column.TERMINATION_DATE, "");
        return cells;
    }

    private static String cell(final Map<Column, String> cells, final Column column) {
        return cells.getOrDefault(column, "");
    }

    private static String required(final Map<Column, String> cells, final Column column) {
        final String value = cell(cells, column);
        if (value.isEmpty()) {
            throw new RowRejectedException(column.header(), "A value is required.");
        }
        return value;
    }

    private static LocalDate optionalDate(final Map<Column, String> cells, final Column column) {
        final String value = cell(cells, column);
        return value.isEmpty() ? null : date(value, column);
    }

    private static LocalDate date(final String value, final Column column) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new RowRejectedException(column.header(), DATE_HINT);
        }
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd backend && ./gradlew test --tests '*EmployeeRowTest' --console=plain --warning-mode=none`
Expected: 8 tests pass.

- [ ] **Step 5: Write the failing reader test**

`.../dataload/rules/EmployeeCsvTest.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeCsvTest {

    private static final String HEADER = String.join(",", Column.headers());
    private static final String ROW = "Asha Rao,asha@acme.example,2024-04-01,Engineering,Software Engineer,"
            + "IN,INR,1250000.00,,";

    @Test
    void readsCellsByHeaderNameWhateverTheColumnOrder() {
        final List<EmployeeCsv.Line> lines = read("email,fullName,hireDate,department,jobTitle,countryCode,currency,"
                + "salary\nasha@acme.example,Asha Rao,2024-04-01,Engineering,Software Engineer,IN,INR,100.00");

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().cells())
                .containsEntry(Column.FULL_NAME, "Asha Rao")
                .containsEntry(Column.EMAIL, "asha@acme.example")
                .containsEntry(Column.SALARY, "100.00");
    }

    @Test
    void numbersTheRowsWithTheHeaderAsRowOne() {
        final List<EmployeeCsv.Line> lines = read(HEADER + "\n" + ROW + "\n" + ROW);

        assertThat(lines).extracting(EmployeeCsv.Line::rowNumber).containsExactly(2, 3);
    }

    @Test
    void readsAFileSavedByExcel() {
        final String quotedName = "\"Rao, Asha \"\"A\"\"\"";
        final String excel = "﻿" + HEADER + "\r\n" + ROW.replace("Asha Rao", quotedName) + "\r\n";

        final List<EmployeeCsv.Line> lines = read(excel);

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().cells()).containsEntry(Column.FULL_NAME, "Rao, Asha \"A\"");
    }

    @Test
    void trimsSpacesAroundCellsAndSkipsBlankLines() {
        final List<EmployeeCsv.Line> lines = read(HEADER + "\n\n" + ROW.replace("Engineering", "  Engineering ") + "\n\n");

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().cells()).containsEntry(Column.DEPARTMENT, "Engineering");
    }

    @Test
    void treatsAnAbsentOptionalColumnAndAShortRowAsEmptyCells() {
        final List<EmployeeCsv.Line> lines = read("fullName,email,hireDate,department,jobTitle,countryCode,currency,"
                + "salary\nAsha Rao,asha@acme.example,2024-04-01");

        assertThat(lines.getFirst().cells())
                .containsEntry(Column.SALARY_EFFECTIVE_DATE, "")
                .containsEntry(Column.TERMINATION_DATE, "")
                .containsEntry(Column.SALARY, "");
    }

    @Test
    void failsTheFileWhenARequiredColumnIsMissing() {
        assertFileRejected("fullName,email\nA,a@x", "Missing required columns: hireDate, department, jobTitle, "
                + "countryCode, currency, salary.");
    }

    @Test
    void failsTheFileOnAnUnknownColumn() {
        assertFileRejected(HEADER + ",bonus\n" + ROW + ",5", "Unknown columns: bonus.");
    }

    @Test
    void failsTheFileOnARepeatedColumn() {
        assertFileRejected(HEADER + ",email\n" + ROW + ",x", "The header repeats a column name.");
    }

    @Test
    void failsTheFileWhenItIsEmpty() {
        assertFileRejected("", "The file has no header row.");
    }

    private static List<EmployeeCsv.Line> read(final String text) {
        return EmployeeCsv.read(new StringReader(text));
    }

    private static void assertFileRejected(final String text, final String reason) {
        assertThatExceptionOfType(FileRejectedException.class)
                .isThrownBy(() -> read(text))
                .withMessage(reason);
    }
}
```

- [ ] **Step 6: Run, expect a compile failure**

Run: `cd backend && ./gradlew test --tests '*EmployeeCsvTest' --console=plain --warning-mode=none`
Expected: FAIL to compile, "cannot find symbol" for `EmployeeCsv` and `FileRejectedException`.

- [ ] **Step 7: Write the reader**

`.../dataload/rules/FileRejectedException.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

/** The whole file cannot be loaded, for example because a column is missing. */
public final class FileRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FileRejectedException(final String reason) {
        super(reason);
    }
}
```

`.../dataload/rules/EmployeeCsv.java`:

```java
package com.acme.peoplemanagement.dataload.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;

/** Reads an employee CSV into numbered lines of cells. It checks the header, and leaves each row's meaning to the row. */
public final class EmployeeCsv {

    private static final char BYTE_ORDER_MARK = '﻿';

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .get();

    private EmployeeCsv() {
    }

    /** A row's cells and its number in the file, where the header is row 1. */
    public record Line(int rowNumber, Map<Column, String> cells) {
    }

    public static List<Line> read(final Reader source) {
        try (CSVParser parser = parse(source)) {
            requireKnownHeaders(parser.getHeaderNames());
            return parser.stream()
                    .map(record -> new Line((int) record.getRecordNumber() + 1, cellsOf(record)))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static CSVParser parse(final Reader source) throws IOException {
        final BufferedReader reader = new BufferedReader(source);
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
        try {
            return FORMAT.parse(reader);
        } catch (IllegalArgumentException exception) {
            throw new FileRejectedException("The header repeats a column name.");
        }
    }

    private static void requireKnownHeaders(final List<String> headers) {
        if (headers.isEmpty()) {
            throw new FileRejectedException("The file has no header row.");
        }
        final Set<String> known = new HashSet<>(Column.headers());
        final List<String> unknown = headers.stream().filter(header -> !known.contains(header)).toList();
        if (!unknown.isEmpty()) {
            throw new FileRejectedException("Unknown columns: " + String.join(", ", unknown) + ".");
        }
        final List<String> missing = Arrays.stream(Column.values())
                .filter(Column::required)
                .map(Column::header)
                .filter(header -> !headers.contains(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new FileRejectedException("Missing required columns: " + String.join(", ", missing) + ".");
        }
    }

    private static Map<Column, String> cellsOf(final CSVRecord record) {
        final Map<Column, String> cells = new EnumMap<>(Column.class);
        for (final Column column : Column.values()) {
            cells.put(column, record.isSet(column.header()) ? record.get(column.header()) : "");
        }
        return cells;
    }
}
```

- [ ] **Step 8: Run, expect PASS**

Run: `cd backend && ./gradlew test --tests '*EmployeeCsvTest' --tests '*EmployeeRowTest' --console=plain --warning-mode=none`
Expected: all pass (17 tests). If `setDuplicateHeaderMode` or `.get()` does not compile against 1.14.1, read the library's `CSVFormat.Builder` and use the current non-deprecated equivalent, then ledger the ruling.

- [ ] **Step 9: Prove each test by breaking the code**

| Mutation | Expected failing tests |
|---|---|
| `EmployeeRow`: `required(cells, Column.DEPARTMENT)` to `cell(cells, Column.DEPARTMENT)` | `rejectsAMissingRequiredValueAgainstItsColumn` |
| `EmployeeRow`: `DATE_HINT` text changed | `rejectsADateThatIsNotInIsoFormat`, `rejectsADateThatDoesNotExist` |
| `EmployeeRow`: `if (!cell(cells, Column.TERMINATION_DATE).isEmpty())` to `if (cell(cells, Column.TERMINATION_DATE).isEmpty())` | `rejectsATerminationDateUntilLeaversAreSupported`, `parsesAFullRow` |
| `EmployeeRow.cells()`: `cells.put(Column.EMAIL, email)` to `cells.put(Column.EMAIL, fullName)` | `parsesAFullRow` |
| `EmployeeCsv`: `+ 1)` in the row number to `+ 0)` | `numbersTheRowsWithTheHeaderAsRowOne` |
| `EmployeeCsv`: `BYTE_ORDER_MARK = '﻿'` to `'﻾'` | `readsAFileSavedByExcel` |
| `EmployeeCsv`: `.setTrim(true)` to `.setTrim(false)` | `trimsSpacesAroundCellsAndSkipsBlankLines` |
| `EmployeeCsv`: `.setIgnoreEmptyLines(true)` to `false` | `trimsSpacesAroundCellsAndSkipsBlankLines` |
| `EmployeeCsv`: `record.isSet(column.header())` to `record.isMapped(column.header())` | `treatsAnAbsentOptionalColumnAndAShortRowAsEmptyCells` (a short row then throws on `get`) |
| `EmployeeCsv`: `DuplicateHeaderMode.DISALLOW` to `DuplicateHeaderMode.ALLOW_ALL` | `failsTheFileOnARepeatedColumn` |
| `EmployeeCsv`: `.filter(Column::required)` to `.filter(column -> !column.required())` | `failsTheFileWhenARequiredColumnIsMissing` |

- [ ] **Step 10: Gate and stage**

Run: `make test-backend`
Expected: exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/dataload/rules backend/src/test/java/com/acme/peoplemanagement/dataload/rules
```

Suggested message: `feat(loader): parse a CSV row and read a file, rejecting a bad header before any row`

---

### Task 3: The pay model, the generator and the CSV writer

**Files:**
- Create: `dataload/seed/package-info.java`, `PayModel.java`, `SeedGenerator.java`, `SeedCsvWriter.java`
- Test: `dataload/seed/PayModelTest.java`, `SeedGeneratorTest.java`, `SeedCsvWriterTest.java`

**Interfaces:**
- Consumes: `EmployeeRow`, `Column`, `EmployeeCsv` (Tasks 1 and 2).
- Produces: `PayModel.titles() -> List<TitlePay>` and `PayModel.countries() -> List<CountryPay>`; `PayModel.TitlePay(String title, String department, long baseUsd)`; `PayModel.CountryPay(String code, String currency, int weightPercent, BigDecimal payFactor, BigDecimal perUsd)`; `PayModel.majorSalary(TitlePay, CountryPay, int tenureYears, int noisePercent) -> long` (whole major units, a multiple of 1,000); `PayModel.MAX_NOISE_PERCENT`; `SeedGenerator.generate(int count, long randomSeed, LocalDate today) -> List<EmployeeRow>`; `SeedCsvWriter.write(List<EmployeeRow>, Appendable) throws IOException`.

- [ ] **Step 1: Write the failing pay model test**

`.../dataload/seed/PayModelTest.java`:

```java
package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import org.junit.jupiter.api.Test;

class PayModelTest {

    @Test
    void paysAManagerMoreThanAnAnalystInTheSameCountry() {
        final TitlePay manager = title("Finance Manager");
        final TitlePay analyst = title("Financial Analyst");

        for (final CountryPay country : PayModel.countries()) {
            assertThat(PayModel.majorSalary(manager, country, 3, 0))
                    .as(country.code())
                    .isGreaterThan(PayModel.majorSalary(analyst, country, 3, 0));
        }
    }

    @Test
    void paysMoreInTheUnitedStatesThanInIndiaForTheSameTitleInUsdTerms() {
        final TitlePay engineer = title("Software Engineer");

        assertThat(PayModel.majorSalary(engineer, country("US"), 0, 0)).isEqualTo(90_000L);
        assertThat(PayModel.majorSalary(engineer, country("IN"), 0, 0)).isEqualTo(2_241_000L);
    }

    @Test
    void paysMoreWithTenureUpToTenYears() {
        final TitlePay engineer = title("Software Engineer");
        final CountryPay unitedStates = country("US");

        assertThat(PayModel.majorSalary(engineer, unitedStates, 5, 0))
                .isGreaterThan(PayModel.majorSalary(engineer, unitedStates, 0, 0));
        assertThat(PayModel.majorSalary(engineer, unitedStates, 10, 0))
                .isGreaterThan(PayModel.majorSalary(engineer, unitedStates, 9, 0));
    }

    @Test
    void stopsTheTenureUpliftAfterTenYears() {
        final TitlePay engineer = title("Software Engineer");
        final CountryPay unitedStates = country("US");

        assertThat(PayModel.majorSalary(engineer, unitedStates, 25, 0))
                .isEqualTo(PayModel.majorSalary(engineer, unitedStates, 10, 0));
    }

    @Test
    void movesPayByTheNoiseButNeverMoreThanTenPercent() {
        final TitlePay engineer = title("Software Engineer");
        final CountryPay unitedStates = country("US");
        final long exact = PayModel.majorSalary(engineer, unitedStates, 0, 0);

        assertThat(PayModel.majorSalary(engineer, unitedStates, 0, PayModel.MAX_NOISE_PERCENT))
                .isGreaterThan(exact).isLessThanOrEqualTo(exact * 110 / 100);
        assertThat(PayModel.majorSalary(engineer, unitedStates, 0, -PayModel.MAX_NOISE_PERCENT))
                .isLessThan(exact).isGreaterThanOrEqualTo(exact * 90 / 100);
    }

    @Test
    void roundsEveryAmountToAWholeThousand() {
        for (final TitlePay title : PayModel.titles()) {
            for (final CountryPay country : PayModel.countries()) {
                final long salary = PayModel.majorSalary(title, country, 4, 7);
                assertThat(salary).isPositive().isEqualTo(salary / 1_000 * 1_000);
            }
        }
    }

    @Test
    void weighsTheCountriesToOneHundredPercent() {
        assertThat(PayModel.countries().stream().mapToInt(CountryPay::weightPercent).sum()).isEqualTo(100);
    }

    @Test
    void paysEachCountryInItsOwnCurrency() {
        assertThat(country("IN").currency()).isEqualTo("INR");
        assertThat(country("DE").currency()).isEqualTo("EUR");
        assertThat(country("FR").currency()).isEqualTo("EUR");
        assertThat(country("JP").currency()).isEqualTo("JPY");
    }

    private static TitlePay title(final String name) {
        return PayModel.titles().stream().filter(title -> title.title().equals(name)).findFirst().orElseThrow();
    }

    private static CountryPay country(final String code) {
        return PayModel.countries().stream().filter(country -> country.code().equals(code)).findFirst().orElseThrow();
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd backend && ./gradlew test --tests '*PayModelTest' --console=plain --warning-mode=none`
Expected: FAIL to compile, "cannot find symbol" for `PayModel`.

- [ ] **Step 3: Write `PayModel`**

`.../dataload/seed/package-info.java`:

```java
/**
 * The seed: realistic, deterministic employees written as a CSV in the loader's format.
 */
package com.acme.peoplemanagement.dataload.seed;
```

`.../dataload/seed/PayModel.java`:

```java
package com.acme.peoplemanagement.dataload.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Synthetic pay for the seed: a base per job title, times a factor per country, times a tenure uplift, times noise.
 * The factors and rates are invented to give insights something to find. They are not market data and not currency
 * conversion, and the product never converts.
 */
public final class PayModel {

    public static final int MAX_NOISE_PERCENT = 10;

    private static final BigDecimal ANNUAL_UPLIFT = new BigDecimal("1.04");
    private static final int MAX_UPLIFT_YEARS = 10;
    private static final BigDecimal ROUNDING_STEP = BigDecimal.valueOf(1_000);

    private static final List<TitlePay> TITLES = List.of(
            new TitlePay("Software Engineer", "Engineering", 90_000),
            new TitlePay("QA Engineer", "Engineering", 70_000),
            new TitlePay("DevOps Engineer", "Engineering", 95_000),
            new TitlePay("Engineering Manager", "Engineering", 140_000),
            new TitlePay("Data Analyst", "Data and Analytics", 70_000),
            new TitlePay("Data Scientist", "Data and Analytics", 110_000),
            new TitlePay("Product Manager", "Product", 120_000),
            new TitlePay("Product Designer", "Design", 85_000),
            new TitlePay("UX Researcher", "Design", 80_000),
            new TitlePay("Sales Executive", "Sales", 65_000),
            new TitlePay("Account Manager", "Sales", 80_000),
            new TitlePay("Sales Manager", "Sales", 115_000),
            new TitlePay("Marketing Specialist", "Marketing", 65_000),
            new TitlePay("Content Writer", "Marketing", 50_000),
            new TitlePay("Customer Support Agent", "Customer Support", 38_000),
            new TitlePay("Support Team Lead", "Customer Support", 55_000),
            new TitlePay("Accountant", "Finance", 60_000),
            new TitlePay("Financial Analyst", "Finance", 75_000),
            new TitlePay("Finance Manager", "Finance", 110_000),
            new TitlePay("Recruiter", "Human Resources", 55_000),
            new TitlePay("HR Business Partner", "Human Resources", 75_000),
            new TitlePay("HR Manager", "Human Resources", 105_000),
            new TitlePay("Operations Analyst", "Operations", 60_000),
            new TitlePay("Operations Manager", "Operations", 100_000));

    private static final List<CountryPay> COUNTRIES = List.of(
            country("IN", "INR", 30, "0.30", "83"),
            country("US", "USD", 22, "1.00", "1"),
            country("GB", "GBP", 10, "0.78", "0.79"),
            country("DE", "EUR", 8, "0.82", "0.92"),
            country("CA", "CAD", 6, "0.85", "1.36"),
            country("FR", "EUR", 6, "0.75", "0.92"),
            country("SG", "SGD", 6, "0.90", "1.34"),
            country("AU", "AUD", 5, "0.85", "1.52"),
            country("JP", "JPY", 4, "0.65", "150"),
            country("AE", "AED", 3, "0.80", "3.67"));

    private PayModel() {
    }

    /** A job title, the department it belongs to, and its base annual pay in USD. */
    public record TitlePay(String title, String department, long baseUsd) {
    }

    /** A country, its currency, its share of the seed, its pay level against the US, and local units per USD. */
    public record CountryPay(
            String code, String currency, int weightPercent, BigDecimal payFactor, BigDecimal perUsd) {
    }

    public static List<TitlePay> titles() {
        return TITLES;
    }

    public static List<CountryPay> countries() {
        return COUNTRIES;
    }

    /** Annual pay in whole major units of the country's currency, rounded to the nearest thousand. */
    public static long majorSalary(
            final TitlePay title, final CountryPay country, final int tenureYears, final int noisePercent) {
        final BigDecimal uplift = ANNUAL_UPLIFT.pow(Math.min(tenureYears, MAX_UPLIFT_YEARS));
        final BigDecimal noise = BigDecimal.valueOf(100L + noisePercent).movePointLeft(2);
        final BigDecimal exact = BigDecimal.valueOf(title.baseUsd())
                .multiply(country.payFactor())
                .multiply(country.perUsd())
                .multiply(uplift)
                .multiply(noise);
        return exact.divide(ROUNDING_STEP, 0, RoundingMode.HALF_UP).multiply(ROUNDING_STEP).longValueExact();
    }

    private static CountryPay country(
            final String code, final String currency, final int weight, final String payFactor, final String perUsd) {
        return new CountryPay(code, currency, weight, new BigDecimal(payFactor), new BigDecimal(perUsd));
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd backend && ./gradlew test --tests '*PayModelTest' --console=plain --warning-mode=none`
Expected: 8 tests pass. (`90_000 * 0.30 * 83 = 2,241,000` is exact, so the India figure holds.)

- [ ] **Step 5: Write the failing generator and writer tests**

`.../dataload/seed/SeedGeneratorTest.java`:

```java
package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SeedGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final int COUNT = 10_000;
    private static final List<EmployeeRow> ROWS = SeedGenerator.generate(COUNT, 42L, TODAY);

    @Test
    void producesTheRequestedNumberOfEmployees() {
        assertThat(ROWS).hasSize(COUNT);
        assertThat(SeedGenerator.generate(25, 42L, TODAY)).hasSize(25);
    }

    @Test
    void givesTheSameEmployeesForTheSameInputAndOtherEmployeesForAnotherSeed() {
        assertThat(SeedGenerator.generate(500, 42L, TODAY)).isEqualTo(SeedGenerator.generate(500, 42L, TODAY));
        assertThat(SeedGenerator.generate(500, 43L, TODAY)).isNotEqualTo(SeedGenerator.generate(500, 42L, TODAY));
    }

    @Test
    void givesEveryEmployeeAUniqueEmail() {
        assertThat(ROWS.stream().map(EmployeeRow::email).distinct().count()).isEqualTo(COUNT);
    }

    @Test
    void hiresInTheLastTenYearsAndNeverInTheFuture() {
        assertThat(ROWS).allSatisfy(row -> assertThat(row.hireDate())
                .isAfterOrEqualTo(TODAY.minusDays(3_650)).isBeforeOrEqualTo(TODAY));
        assertThat(ROWS.stream().map(EmployeeRow::hireDate).min(LocalDate::compareTo).orElseThrow())
                .isBefore(TODAY.minusYears(9));
        assertThat(ROWS.stream().map(EmployeeRow::hireDate).max(LocalDate::compareTo).orElseThrow())
                .isAfter(TODAY.minusYears(1));
    }

    @Test
    void datesTheSalaryAtTheMostRecentWorkAnniversary() {
        assertThat(ROWS).allSatisfy(row -> {
            assertThat(row.salaryEffectiveDate()).isAfterOrEqualTo(row.hireDate()).isBeforeOrEqualTo(TODAY);
            assertThat(row.salaryEffectiveDate().plusYears(1)).isAfter(TODAY);
        });
    }

    @Test
    void paysEveryCountryInItsOwnCurrencyAndUsesAllOfThem() {
        final Map<String, String> currencyByCountry = PayModel.countries().stream()
                .collect(Collectors.toMap(CountryPay::code, CountryPay::currency));

        assertThat(ROWS).allSatisfy(row ->
                assertThat(row.currency()).isEqualTo(currencyByCountry.get(row.countryCode())));
        assertThat(ROWS.stream().map(EmployeeRow::countryCode).distinct().count())
                .isEqualTo(PayModel.countries().size());
    }

    @Test
    void putsEveryTitleInItsOwnDepartment() {
        final Map<String, String> departmentByTitle = PayModel.titles().stream()
                .collect(Collectors.toMap(TitlePay::title, TitlePay::department));

        assertThat(ROWS).allSatisfy(row ->
                assertThat(row.department()).isEqualTo(departmentByTitle.get(row.jobTitle())));
        assertThat(ROWS.stream().map(EmployeeRow::jobTitle).distinct().count()).isEqualTo(PayModel.titles().size());
    }

    @Test
    void writesSalariesAsWholeThousandsOfMajorUnits() {
        assertThat(ROWS).allSatisfy(row -> assertThat(Long.parseLong(row.salary()) % 1_000).isZero());
    }

    @Test
    void followsTheCountryMixWithinAFewPoints() {
        final Map<String, Long> counts = ROWS.stream()
                .collect(Collectors.groupingBy(EmployeeRow::countryCode, Collectors.counting()));

        for (final CountryPay country : PayModel.countries()) {
            assertThat(counts.get(country.code()) * 100.0 / COUNT)
                    .as(country.code()).isBetween(country.weightPercent() - 2.0, country.weightPercent() + 2.0);
        }
    }

    @Test
    void paysTenuredEmployeesMoreOnAverageForTheSameTitleAndCountry() {
        final double juniors = averageIndianEngineerSalary(row -> row.hireDate().isAfter(TODAY.minusYears(2)));
        final double seniors = averageIndianEngineerSalary(row -> row.hireDate().isBefore(TODAY.minusYears(7)));

        assertThat(seniors).isGreaterThan(juniors);
    }

    private static double averageIndianEngineerSalary(final Predicate<EmployeeRow> tenure) {
        return ROWS.stream()
                .filter(row -> row.countryCode().equals("IN") && row.jobTitle().equals("Software Engineer"))
                .filter(tenure)
                .mapToLong(row -> Long.parseLong(row.salary()))
                .average()
                .orElseThrow();
    }
}
```

Keep every line within 120 columns when pasting. Checkstyle reports any that are longer.

`.../dataload/seed/SeedCsvWriterTest.java`:

```java
package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.dataload.rules.EmployeeCsv;
import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeedCsvWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void writesTheHeaderInTheLoadersColumnOrder() throws IOException {
        final String csv = write(SeedGenerator.generate(3, 42L, TODAY));

        assertThat(csv.lines().findFirst().orElseThrow()).isEqualTo(String.join(",", Column.headers()));
    }

    @Test
    void writesAFileTheLoadersReaderReadsBackAsTheSameEmployees() throws IOException {
        final List<EmployeeRow> rows = SeedGenerator.generate(200, 42L, TODAY);

        final List<EmployeeCsv.Line> lines = EmployeeCsv.read(new StringReader(write(rows)));

        final List<EmployeeRow> readBack = new ArrayList<>();
        lines.forEach(line -> readBack.add(EmployeeRow.parse(line.cells())));
        assertThat(readBack).isEqualTo(rows);
        assertThat(lines.getFirst().rowNumber()).isEqualTo(2);
        assertThat(lines.getLast().rowNumber()).isEqualTo(201);
    }

    @Test
    void quotesANameThatHoldsAComma() throws IOException {
        final EmployeeRow row = new EmployeeRow("Rao, Asha", "asha@acme.example", LocalDate.of(2024, 4, 1),
                "Engineering", "Software Engineer", "IN", "INR", "1250000", null);

        final List<EmployeeCsv.Line> lines = EmployeeCsv.read(new StringReader(write(List.of(row))));

        assertThat(EmployeeRow.parse(lines.getFirst().cells())).isEqualTo(row);
    }

    private static String write(final List<EmployeeRow> rows) throws IOException {
        final StringWriter writer = new StringWriter();
        SeedCsvWriter.write(rows, writer);
        return writer.toString();
    }
}
```

- [ ] **Step 6: Run, expect a compile failure**

Run: `cd backend && ./gradlew test --tests '*SeedGeneratorTest' --tests '*SeedCsvWriterTest' --console=plain --warning-mode=none`
Expected: FAIL to compile, "cannot find symbol" for `SeedGenerator` and `SeedCsvWriter`.

- [ ] **Step 7: Write the generator and the writer**

`.../dataload/seed/SeedGenerator.java`:

```java
package com.acme.peoplemanagement.dataload.seed;

import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Builds the same employees every time for the same random seed and the same day. */
public final class SeedGenerator {

    private static final int HIRE_WINDOW_DAYS = 10 * 365;
    private static final int TOTAL_WEIGHT_PERCENT = 100;

    private static final List<String> FIRST_NAMES = List.of(
            "Asha", "Ravi", "Priya", "Arjun", "Meera", "Kiran", "Sanjay", "Anita", "John", "Emma",
            "Liam", "Olivia", "Noah", "Sophia", "Lucas", "Mia", "Hiroshi", "Yuki", "Fatima", "Omar");
    private static final List<String> LAST_NAMES = List.of(
            "Rao", "Sharma", "Patel", "Singh", "Nair", "Smith", "Jones", "Brown", "Taylor", "Wilson",
            "Muller", "Schmidt", "Dubois", "Martin", "Tan", "Lim", "Nguyen", "Sato", "Khan", "Haddad");

    private SeedGenerator() {
    }

    public static List<EmployeeRow> generate(final int count, final long randomSeed, final LocalDate today) {
        final Random random = new Random(randomSeed);
        final List<EmployeeRow> rows = new ArrayList<>(count);
        for (int number = 1; number <= count; number++) {
            rows.add(employee(number, random, today));
        }
        return rows;
    }

    private static EmployeeRow employee(final int number, final Random random, final LocalDate today) {
        final String first = pick(FIRST_NAMES, random);
        final String last = pick(LAST_NAMES, random);
        final TitlePay title = pick(PayModel.titles(), random);
        final CountryPay country = pickCountry(random);
        final LocalDate hireDate = today.minusDays(random.nextInt(HIRE_WINDOW_DAYS));
        final int tenureYears = (int) ChronoUnit.YEARS.between(hireDate, today);
        final int noisePercent = random.nextInt(2 * PayModel.MAX_NOISE_PERCENT + 1) - PayModel.MAX_NOISE_PERCENT;
        final long salary = PayModel.majorSalary(title, country, tenureYears, noisePercent);
        return new EmployeeRow(first + " " + last, email(first, last, number), hireDate, title.department(),
                title.title(), country.code(), country.currency(), Long.toString(salary),
                hireDate.plusYears(tenureYears));
    }

    private static String email(final String first, final String last, final int number) {
        return (first + "." + last + number + "@acme.example").toLowerCase(Locale.ROOT);
    }

    private static <T> T pick(final List<T> options, final Random random) {
        return options.get(random.nextInt(options.size()));
    }

    private static CountryPay pickCountry(final Random random) {
        int remaining = random.nextInt(TOTAL_WEIGHT_PERCENT);
        for (final CountryPay country : PayModel.countries()) {
            remaining -= country.weightPercent();
            if (remaining < 0) {
                return country;
            }
        }
        throw new IllegalStateException("The country weights do not add up to 100.");
    }
}
```

`.../dataload/seed/SeedCsvWriter.java`:

```java
package com.acme.peoplemanagement.dataload.seed;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Writes rows in the loader's format. The column order comes from {@link Column}, so the two cannot drift. */
public final class SeedCsvWriter {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(Column.headers().toArray(String[]::new))
            .get();

    private SeedCsvWriter() {
    }

    public static void write(final List<EmployeeRow> rows, final Appendable out) throws IOException {
        try (CSVPrinter printer = FORMAT.print(out)) {
            for (final EmployeeRow row : rows) {
                printer.printRecord(Arrays.stream(Column.values()).map(row.cells()::get).toList());
            }
        }
    }
}
```

- [ ] **Step 8: Run, expect PASS**

Run: `cd backend && ./gradlew test --tests '*PayModelTest' --tests '*SeedGeneratorTest' --tests '*SeedCsvWriterTest' --console=plain --warning-mode=none`
Expected: all pass (8 + 10 + 3). If `row.cells()::get` evaluates `row.cells()` once per column that is fine, but Error Prone may prefer a local; extract `final Map<Column, String> cells = row.cells();` if it complains. If the country-mix or tenure test is flaky across seeds (it must not be: the seed is fixed), report it, do not widen the bounds without a ruling.

- [ ] **Step 9: Prove each test by breaking the code**

| Mutation | Expected failing tests |
|---|---|
| `PayModel`: `"0.30"` (India) to `"0.35"` | `paysMoreInTheUnitedStatesThanInIndia...` |
| `PayModel`: `Math.min(tenureYears, MAX_UPLIFT_YEARS)` to `tenureYears` | `stopsTheTenureUpliftAfterTenYears` |
| `PayModel`: `ANNUAL_UPLIFT = new BigDecimal("1.04")` to `"1.00"` | `paysMoreWithTenureUpToTenYears`, `paysTenuredEmployees...` |
| `PayModel`: `ROUNDING_STEP = BigDecimal.valueOf(1_000)` to `valueOf(100)` | `roundsEveryAmountToAWholeThousand`, `writesSalariesAsWholeThousandsOfMajorUnits` |
| `PayModel`: weight of `"US"` `22` to `23` | `weighsTheCountriesToOneHundredPercent` |
| `PayModel`: `"Finance Manager"` base `110_000` to `50_000` | `paysAManagerMoreThanAnAnalyst...` |
| `SeedGenerator`: `hireDate.plusYears(tenureYears)` to `hireDate` | `datesTheSalaryAtTheMostRecentWorkAnniversary` |
| `SeedGenerator`: `email(first, last, number)` to `email(first, last, 1)` | `givesEveryEmployeeAUniqueEmail` |
| `SeedGenerator`: `new Random(randomSeed)` to `new Random()` | `givesTheSameEmployeesForTheSameInput...` |
| `SeedGenerator`: `random.nextInt(HIRE_WINDOW_DAYS)` to `random.nextInt(HIRE_WINDOW_DAYS) - 400` | `hiresInTheLastTenYearsAndNeverInTheFuture` |
| `SeedGenerator`: `country.currency()` to `"USD"` | `paysEveryCountryInItsOwnCurrency...` |
| `SeedGenerator`: `title.department()` to `"Sales"` | `putsEveryTitleInItsOwnDepartment` |
| `SeedCsvWriter`: `.setHeader(...)` to `.setHeader("x")` | `writesTheHeaderInTheLoadersColumnOrder` |

- [ ] **Step 10: Gate and stage**

Run: `make test-backend`
Expected: exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/dataload/seed backend/src/test/java/com/acme/peoplemanagement/dataload/seed
```

Suggested message: `feat(loader): add a deterministic seed generator with a country, title and tenure pay model`

---

### Task 4: Look up departments and job titles by name

**Files:**
- Modify: `reference/DepartmentRepository.java`, `JobTitleRepository.java`, `ReferenceLookup.java`
- Test: `reference/ReferenceLookupIntegrationTest.java` (extend), `dataload/seed/SeedReferenceIntegrationTest.java` (new)

**Interfaces:**
- Consumes: `PayModel` (Task 3).
- Produces: `ReferenceLookup.departmentByName(String) -> Optional<DepartmentResponse>` and `jobTitleByName(String) -> Optional<JobTitleResponse>`, both ignoring case and surrounding spaces.

- [ ] **Step 1: Write the failing lookup tests**

Add to `ReferenceLookupIntegrationTest` (before the private helper):

```java
    @Test
    void findsADepartmentByNameIgnoringCaseAndSurroundingSpaces() {
        final int id = idOf("department", "Data and Analytics");

        assertThat(lookup.departmentByName("  data AND analytics ")).contains(new DepartmentResponse(id, "Data and Analytics"));
    }

    @Test
    void findsAJobTitleByNameIgnoringCaseAndSurroundingSpaces() {
        final int id = idOf("job_title", "Software Engineer");

        assertThat(lookup.jobTitleByName(" SOFTWARE engineer")).contains(new JobTitleResponse(id, "Software Engineer"));
    }

    @Test
    void answersEmptyForANameThatDoesNotExist() {
        assertThat(lookup.departmentByName("Astrology")).isEmpty();
        assertThat(lookup.jobTitleByName("Astrologer")).isEmpty();
    }
```

(Wrap the two long `assertThat` lines to 120 columns.)

`backend/src/test/java/com/acme/peoplemanagement/dataload/seed/SeedReferenceIntegrationTest.java`:

```java
package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import com.acme.peoplemanagement.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The generator keeps its own table, so a change to the reference data must fail here rather than in a load. */
@IntegrationTest
class SeedReferenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usesExactlyTheJobTitlesInTheReferenceData() {
        assertThat(PayModel.titles().stream().map(TitlePay::title).toList())
                .containsExactlyInAnyOrderElementsOf(names("job_title"));
    }

    @Test
    void usesExactlyTheDepartmentsInTheReferenceData() {
        assertThat(PayModel.titles().stream().map(TitlePay::department).distinct().toList())
                .containsExactlyInAnyOrderElementsOf(names("department"));
    }

    @Test
    void usesExactlyTheCountriesInTheReferenceData() {
        assertThat(PayModel.countries().stream().map(CountryPay::code).toList())
                .containsExactlyInAnyOrderElementsOf(codes("country"));
    }

    @Test
    void paysOnlyInCurrenciesInTheReferenceData() {
        assertThat(codes("currency")).containsAll(PayModel.countries().stream().map(CountryPay::currency).toList());
    }

    private List<String> names(final String table) {
        return jdbcTemplate.queryForList("select name from " + table, String.class);
    }

    private List<String> codes(final String table) {
        return jdbcTemplate.queryForList("select code from " + table, String.class);
    }
}
```

The `country` table has no home-currency column (migration 003: "a country does not decide a currency"), so the country-to-currency pairing lives only in `PayModel`, and `PayModelTest` pins it.

- [ ] **Step 2: Run, expect failures**

Run: `cd backend && ./gradlew integrationTest --tests '*ReferenceLookupIntegrationTest' --tests '*SeedReferenceIntegrationTest' --console=plain --warning-mode=none`
Expected: FAIL to compile for `departmentByName` and `jobTitleByName`. After Step 3, `SeedReferenceIntegrationTest` must pass at once, because it tests the Task 3 table against the existing migrations. If a name or code differs, fix `PayModel` (the table was written from the migrations on 2026-10-23, so a mismatch means a typo).

- [ ] **Step 3: Write the lookups**

`DepartmentRepository.java`:

```java
package com.acme.peoplemanagement.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface DepartmentRepository extends JpaRepository<Department, Integer> {

    Optional<Department> findByNameIgnoreCase(String name);
}
```

`JobTitleRepository.java`:

```java
package com.acme.peoplemanagement.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobTitleRepository extends JpaRepository<JobTitle, Integer> {

    Optional<JobTitle> findByNameIgnoreCase(String name);
}
```

In `ReferenceLookup.java`, replace the two id lookups' lambdas with shared converters and add the name lookups:

```java
    public Optional<DepartmentResponse> department(final int id) {
        return departmentRepository.findById(id).map(ReferenceLookup::toResponse);
    }

    public Optional<DepartmentResponse> departmentByName(final String name) {
        return departmentRepository.findByNameIgnoreCase(name.strip()).map(ReferenceLookup::toResponse);
    }

    public Optional<JobTitleResponse> jobTitle(final int id) {
        return jobTitleRepository.findById(id).map(ReferenceLookup::toResponse);
    }

    public Optional<JobTitleResponse> jobTitleByName(final String name) {
        return jobTitleRepository.findByNameIgnoreCase(name.strip()).map(ReferenceLookup::toResponse);
    }

    private static DepartmentResponse toResponse(final Department department) {
        return new DepartmentResponse(department.id(), department.name());
    }

    private static JobTitleResponse toResponse(final JobTitle jobTitle) {
        return new JobTitleResponse(jobTitle.id(), jobTitle.name());
    }
```

(Leave `country` and `currency` as they are.)

- [ ] **Step 4: Run, expect PASS**

Run: `cd backend && ./gradlew integrationTest --tests '*ReferenceLookupIntegrationTest' --tests '*SeedReferenceIntegrationTest' --console=plain --warning-mode=none`
Expected: all pass.

- [ ] **Step 5: Prove each test by breaking the code**

| Mutation | Expected failing tests |
|---|---|
| `ReferenceLookup`: `findByNameIgnoreCase(name.strip())` (department) to `findByNameIgnoreCase(name)` | `findsADepartmentByNameIgnoringCaseAndSurroundingSpaces` |
| `DepartmentRepository` and its one call: rename `findByNameIgnoreCase` to `findByName` | `findsADepartmentByNameIgnoringCaseAndSurroundingSpaces` |
| the same two changes for `JobTitleRepository` | `findsAJobTitleByNameIgnoringCaseAndSurroundingSpaces` |
| `PayModel`: rename one title, `"Recruiter"` to `"Recruiters"` | `usesExactlyTheJobTitlesInTheReferenceData` |
| `PayModel`: change `"Operations"` to `"Operation"` in one title | `usesExactlyTheDepartmentsInTheReferenceData` |
| `PayModel`: `country("AE", ...)` to `country("AF", ...)` | `usesExactlyTheCountriesInTheReferenceData` |
| `PayModel`: `"AED"` to `"AEX"` | `paysOnlyInCurrenciesInTheReferenceData` |

- [ ] **Step 6: Gate and stage**

Run: `make test-backend && make test-integration`
Expected: both exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/reference backend/src/test/java/com/acme/peoplemanagement/reference backend/src/test/java/com/acme/peoplemanagement/dataload/seed/SeedReferenceIntegrationTest.java
```

Suggested message: `feat(reference): look up departments and job titles by name, and pin the seed table to the reference data`

---

### Task 5: The loader

**Files:**
- Create: `dataload/package-info.java`, `LoadReport.java`, `RejectedRow.java`, `EmployeeCsvLoader.java`
- Test: `dataload/EmployeeCsvLoaderIntegrationTest.java`

**Interfaces:**
- Consumes: `EmployeeCsv`, `EmployeeRow`, `MajorUnits`, `RowRejectedException`, `FileRejectedException` (Tasks 1 and 2); `ReferenceLookup.departmentByName` and `jobTitleByName` (Task 4); the public `EmployeeService.create`.
- Produces: package-private `EmployeeCsvLoader.load(Reader) -> LoadReport`; `LoadReport(int loaded, List<RejectedRow> rejected)` with `hasRejections()`; `RejectedRow(int rowNumber, String field, String reason)`.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/acme/peoplemanagement/dataload/EmployeeCsvLoaderIntegrationTest.java`:

```java
package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.dataload.rules.FileRejectedException;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class EmployeeCsvLoaderIntegrationTest {

    private static final String HEADER = String.join(",", Column.headers());
    private static final String ROW =
            "Asha Rao,asha@acme.example,2024-04-01,Engineering,Software Engineer,IN,INR,1250000.00,,";

    @Autowired
    private EmployeeCsvLoader loader;

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
    void loadsARowAsAnEmployeeWithItsFirstSalaryOnTheHireDate() {
        final LoadReport report = load(ROW);

        assertThat(report).isEqualTo(new LoadReport(1, List.of()));
        final Map<String, Object> stored = jdbcTemplate.queryForMap("""
                select e.full_name, s.amount_minor, s.currency_code, s.effective_date
                from employee e join salary_entry s on s.employee_id = e.id
                where e.email = 'asha@acme.example'""");
        assertThat(stored).containsEntry("full_name", "Asha Rao")
                .containsEntry("amount_minor", 125_000_000L)
                .containsEntry("currency_code", "INR");
        assertThat(stored.get("effective_date").toString()).isEqualTo("2024-04-01");
    }

    @Test
    void convertsTheAmountWithTheCurrencysOwnDecimals() {
        load(ROW.replace("IN,INR,1250000.00", "JP,JPY,6500000"));

        assertThat(amountMinorOf("asha@acme.example")).isEqualTo(6_500_000L);
    }

    @Test
    void usesTheGivenSalaryEffectiveDate() {
        load(ROW.replace("1250000.00,,", "1250000.00,2024-10-01,"));

        assertThat(effectiveDateOf("asha@acme.example")).isEqualTo(LocalDate.of(2024, 10, 1));
    }

    @Test
    void matchesNamesIgnoringCaseAndSurroundingSpaces() {
        final LoadReport report = load(ROW.replace("Engineering", " engineering ")
                .replace("Software Engineer", "SOFTWARE engineer"));

        assertThat(report.loaded()).isEqualTo(1);
    }

    @Test
    void rejectsOneRowAndStillLoadsTheOthers() {
        final LoadReport report = load(
                row("first@acme.example"),
                row("second@acme.example").replace("2024-04-01", "not-a-date"),
                row("third@acme.example"));

        assertThat(report.loaded()).isEqualTo(2);
        assertThat(report.rejected()).containsExactly(new RejectedRow(
                3, "hireDate", "Dates must look like 2024-04-01 (year-month-day)."));
        assertThat(employeeCount()).isEqualTo(2);
    }

    @Test
    void rejectsAnUnknownDepartmentAgainstItsColumn() {
        final LoadReport report = load(ROW.replace("Engineering", "Astrology"));

        assertThat(report.rejected()).containsExactly(new RejectedRow(2, "department", "Unknown department."));
    }

    @Test
    void rejectsAnUnknownJobTitleAndAnUnknownCurrencyAndAnUnknownCountry() {
        assertThat(load(ROW.replace("Software Engineer", "Astrologer")).rejected())
                .containsExactly(new RejectedRow(2, "jobTitle", "Unknown job title."));
        assertThat(load(ROW.replace("INR", "ZZZ")).rejected())
                .containsExactly(new RejectedRow(2, "currency", "Unknown currency."));
        assertThat(load(ROW.replace(",IN,", ",ZZ,")).rejected())
                .containsExactly(new RejectedRow(2, "countryCode", "Unknown country."));
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfCuttingThemOff() {
        assertThat(load(ROW.replace("IN,INR,1250000.00", "JP,JPY,6500000.5")).rejected())
                .containsExactly(new RejectedRow(2, "salary", "This currency has no decimal places."));
        assertThat(load(ROW.replace("1250000.00", "1.234")).rejected())
                .containsExactly(new RejectedRow(2, "salary", "This currency allows at most 2 decimal places."));
        assertThat(employeeCount()).isZero();
    }

    @Test
    void rejectsARowWhoseSalaryStartsBeforeTheHireDate() {
        final LoadReport report = load(ROW.replace("1250000.00,,", "1250000.00,2024-03-01,"));

        assertThat(report.rejected()).hasSize(1);
        assertThat(report.rejected().getFirst().field()).isEqualTo("salaryEffectiveDate");
        assertThat(report.rejected().getFirst().reason()).startsWith("SALARY_BEFORE_HIRE_DATE");
    }

    @Test
    void rejectsARowThatFailsValidationAgainstTheFieldName() {
        final LoadReport report = load(ROW.replace("Asha Rao", "x".repeat(201)));

        assertThat(report.rejected()).hasSize(1);
        assertThat(report.rejected().getFirst().field()).isEqualTo("fullName");
    }

    @Test
    void rejectsATerminationDateUntilLeaversAreSupported() {
        final LoadReport report = load(ROW.replace("1250000.00,,", "1250000.00,,2025-12-31"));

        assertThat(report.rejected()).containsExactly(
                new RejectedRow(2, "terminationDate", "Termination dates are not supported yet."));
        assertThat(employeeCount()).isZero();
    }

    @Test
    void rejectsAnEmailThatAnEarlierRowOfTheSameFileAlreadyLoaded() {
        final LoadReport report = load(ROW, ROW);

        assertThat(report.loaded()).isEqualTo(1);
        assertThat(report.rejected().getFirst().rowNumber()).isEqualTo(3);
        assertThat(report.rejected().getFirst().field()).isEqualTo("email");
        assertThat(report.rejected().getFirst().reason()).startsWith("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void reportsEveryRowAsAlreadyLoadedWhenTheSameFileRunsAgain() {
        load(row("first@acme.example"), row("second@acme.example"));

        final LoadReport second = load(row("first@acme.example"), row("second@acme.example"));

        assertThat(second.loaded()).isZero();
        assertThat(second.rejected()).hasSize(2).allSatisfy(rejected ->
                assertThat(rejected.reason()).startsWith("EMAIL_ALREADY_EXISTS"));
        assertThat(second.hasRejections()).isTrue();
        assertThat(employeeCount()).isEqualTo(2);
    }

    @Test
    void failsTheWholeFileOnAWrongHeaderBeforeAnyRowIsCreated() {
        assertThatExceptionOfType(FileRejectedException.class)
                .isThrownBy(() -> loader.load(new StringReader("fullName,email\nAsha Rao,asha@acme.example")));
        assertThat(employeeCount()).isZero();
    }

    private LoadReport load(final String... rows) {
        return loader.load(new StringReader(HEADER + "\n" + String.join("\n", rows)));
    }

    private static String row(final String email) {
        return ROW.replace("asha@acme.example", email);
    }

    private int employeeCount() {
        final Integer count = jdbcTemplate.queryForObject("select count(*) from employee", Integer.class);
        return count == null ? -1 : count;
    }

    private long amountMinorOf(final String email) {
        final Long amount = jdbcTemplate.queryForObject("""
                select s.amount_minor from salary_entry s join employee e on e.id = s.employee_id
                where e.email = ?""", Long.class, email);
        return amount == null ? -1 : amount;
    }

    private LocalDate effectiveDateOf(final String email) {
        return jdbcTemplate.queryForObject("""
                select s.effective_date from salary_entry s join employee e on e.id = s.employee_id
                where e.email = ?""", LocalDate.class, email);
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd backend && ./gradlew integrationTest --tests '*EmployeeCsvLoaderIntegrationTest' --console=plain --warning-mode=none`
Expected: FAIL to compile, "cannot find symbol" for `EmployeeCsvLoader`, `LoadReport`, `RejectedRow`.

- [ ] **Step 3: Write the loader**

`.../dataload/package-info.java`:

```java
/**
 * Loads employees from a CSV through the same service HR uses, and generates the seed file. Runs as a mode of the
 * application jar, not as an endpoint.
 */
package com.acme.peoplemanagement.dataload;
```

`.../dataload/RejectedRow.java`:

```java
package com.acme.peoplemanagement.dataload;

/** A row that was not loaded: where it is, which field, and why. */
record RejectedRow(int rowNumber, String field, String reason) {
}
```

`.../dataload/LoadReport.java`:

```java
package com.acme.peoplemanagement.dataload;

import java.util.List;

/** What a load did: how many rows became employees, and the rows that did not. */
record LoadReport(int loaded, List<RejectedRow> rejected) {

    LoadReport {
        rejected = List.copyOf(rejected);
    }

    boolean hasRejections() {
        return !rejected.isEmpty();
    }
}
```

`.../dataload/EmployeeCsvLoader.java`:

```java
package com.acme.peoplemanagement.dataload;

import com.acme.peoplemanagement.dataload.rules.EmployeeCsv;
import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.rules.MajorUnits;
import com.acme.peoplemanagement.dataload.rules.RowRejectedException;
import com.acme.peoplemanagement.employee.EmployeeService;
import com.acme.peoplemanagement.employee.api.CreateEmployeeRequest;
import com.acme.peoplemanagement.employee.api.InitialSalaryRequest;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.reference.CurrencyResponse;
import com.acme.peoplemanagement.reference.DepartmentResponse;
import com.acme.peoplemanagement.reference.JobTitleResponse;
import com.acme.peoplemanagement.reference.ReferenceLookup;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates one employee per row through the service HR uses, so a loaded row obeys the same rules as a typed one.
 * The loader has no transaction of its own: each row commits or rolls back by itself.
 */
@Component
class EmployeeCsvLoader {

    private final EmployeeService employeeService;
    private final ReferenceLookup referenceLookup;
    private final Validator validator;

    EmployeeCsvLoader(
            final EmployeeService employeeService, final ReferenceLookup referenceLookup, final Validator validator) {
        this.employeeService = employeeService;
        this.referenceLookup = referenceLookup;
        this.validator = validator;
    }

    LoadReport load(final Reader source) {
        final List<RejectedRow> rejected = new ArrayList<>();
        int loaded = 0;
        for (final EmployeeCsv.Line line : EmployeeCsv.read(source)) {
            try {
                createFrom(line);
                loaded++;
            } catch (RowRejectedException exception) {
                rejected.add(new RejectedRow(line.rowNumber(), exception.field(), exception.getMessage()));
            }
        }
        return new LoadReport(loaded, rejected);
    }

    private void createFrom(final EmployeeCsv.Line line) {
        final CreateEmployeeRequest request = toRequest(EmployeeRow.parse(line.cells()));
        rejectInvalid(request);
        create(request);
    }

    private CreateEmployeeRequest toRequest(final EmployeeRow row) {
        final CurrencyResponse currency = referenceLookup.currency(row.currency())
                .orElseThrow(() -> new RowRejectedException("currency", "Unknown currency."));
        final long amountMinor = MajorUnits.toMinor(row.salary(), currency.minorUnitDigits());
        return new CreateEmployeeRequest(
                row.fullName(),
                row.email(),
                row.hireDate(),
                departmentId(row.department()),
                jobTitleId(row.jobTitle()),
                row.countryCode(),
                new InitialSalaryRequest(row.salaryEffectiveDate(), amountMinor, row.currency()));
    }

    private int departmentId(final String name) {
        return referenceLookup.departmentByName(name)
                .map(DepartmentResponse::id)
                .orElseThrow(() -> new RowRejectedException("department", "Unknown department."));
    }

    private int jobTitleId(final String name) {
        return referenceLookup.jobTitleByName(name)
                .map(JobTitleResponse::id)
                .orElseThrow(() -> new RowRejectedException("jobTitle", "Unknown job title."));
    }

    private void rejectInvalid(final CreateEmployeeRequest request) {
        validator.validate(request).stream()
                .min(Comparator.comparing((ConstraintViolation<CreateEmployeeRequest> violation) ->
                        violation.getPropertyPath().toString()))
                .ifPresent(violation -> {
                    throw new RowRejectedException(violation.getPropertyPath().toString(), violation.getMessage());
                });
    }

    private void create(final CreateEmployeeRequest request) {
        try {
            employeeService.create(request);
        } catch (EmailAlreadyExistsException exception) {
            throw new RowRejectedException("email", "EMAIL_ALREADY_EXISTS: an employee with this email already exists.");
        } catch (UnknownReferenceException exception) {
            throw new RowRejectedException(exception.field(), exception.getMessage());
        } catch (SalaryRuleViolation exception) {
            throw new RowRejectedException("salaryEffectiveDate", exception.code() + ": " + exception.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd backend && ./gradlew integrationTest --tests '*EmployeeCsvLoaderIntegrationTest' --console=plain --warning-mode=none`
Expected: all 14 tests pass. If `employeeService.create` is rejected because it is not public to `dataload`, it is public already (read on 2026-10-23). If the Bean Validation `@Pattern` message differs from what a test expects, those tests assert only the field name, not the message.

- [ ] **Step 5: Prove each test by breaking the code**

| Mutation | Expected failing tests |
|---|---|
| `MajorUnits.toMinor(row.salary(), currency.minorUnitDigits())` to `MajorUnits.toMinor(row.salary(), 2)` | `convertsTheAmountWithTheCurrencysOwnDecimals`, `rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfCuttingThemOff` |
| `loaded++;` to `loaded += 2;` | `loadsARowAsAnEmployee...`, `rejectsOneRowAndStillLoadsTheOthers` |
| `line.rowNumber()` in the `RejectedRow` to `line.rowNumber() - 1` | `rejectsOneRowAndStillLoadsTheOthers`, `rejectsAnUnknownDepartment...` |
| `"department", "Unknown department."` to `"jobTitle", "Unknown department."` | `rejectsAnUnknownDepartmentAgainstItsColumn` |
| delete the `catch (SalaryRuleViolation ...)` body's rethrow by changing `"salaryEffectiveDate"` to `"email"` | `rejectsARowWhoseSalaryStartsBeforeTheHireDate` |
| `rejectInvalid`: the lambda `violation -> { throw new RowRejectedException(...); }` to `violation -> { }` | `rejectsARowThatFailsValidationAgainstTheFieldName` (the 201-character name then reaches the database and the test fails with an exception instead of a rejected row) |
| the `EmailAlreadyExistsException` branch: `"email"` to `"fullName"` | `rejectsAnEmailThatAnEarlierRowOfTheSameFileAlreadyLoaded`, `reportsEveryRowAsAlreadyLoadedWhenTheSameFileRunsAgain` |

- [ ] **Step 6: Review checklist (no test)**

Read `EmployeeCsvLoader.load` and confirm that only `RowRejectedException` is caught around a row, and that `create` translates exactly three exception types. Anything else (a database error) escapes and aborts the run, which is spec decision 8. Ledger the confirmation.

- [ ] **Step 7: Gate and stage**

Run: `make test-backend && make test-integration`
Expected: both exit 0.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/dataload backend/src/test/java/com/acme/peoplemanagement/dataload/EmployeeCsvLoaderIntegrationTest.java
```

Suggested message: `feat(loader): load an employee CSV through the employee service, rejecting bad rows by number and field`

---

### Task 6: The two command-line modes

**Files:**
- Create: `dataload/BatchMode.java`, `LoadRunner.java`, `SeedRunner.java`
- Modify: `PeopleManagementApplication.java`
- Test: `dataload/BatchModeTest.java`, `SeedRunnerTest.java`, `LoadRunnerIntegrationTest.java`

**Interfaces:**
- Consumes: `EmployeeCsvLoader`, `LoadReport` (Task 5); `SeedGenerator`, `SeedCsvWriter` (Task 3).
- Produces: `BatchMode.LOAD_PROPERTY = "acme.load"`, `BatchMode.SEED_PROPERTY = "acme.seed-csv"`, `BatchMode.requestedBy(Environment) -> boolean`; package-private `LoadRunner(EmployeeCsvLoader, Path, PrintStream, Clock)` (plus the Spring constructor) and `SeedRunner(Clock, Path, PrintStream)`.

- [ ] **Step 1: Write the failing tests**

`.../dataload/BatchModeTest.java`:

```java
package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BatchModeTest {

    @Test
    void isRequestedByTheLoadOption() {
        assertThat(BatchMode.requestedBy(new MockEnvironment().withProperty("acme.load", "employees.csv"))).isTrue();
    }

    @Test
    void isRequestedByTheSeedOption() {
        assertThat(BatchMode.requestedBy(new MockEnvironment().withProperty("acme.seed-csv", "seed.csv"))).isTrue();
    }

    @Test
    void isNotRequestedByAnOrdinaryRun() {
        assertThat(BatchMode.requestedBy(new MockEnvironment())).isFalse();
    }
}
```

`.../dataload/SeedRunnerTest.java`:

```java
package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class SeedRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path directory;

    @Test
    void writesTenThousandEmployeesAndAHeaderAndSaysSo() throws IOException {
        final Path file = directory.resolve("seed.csv");
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        new SeedRunner(CLOCK, file, new PrintStream(output, true, StandardCharsets.UTF_8))
                .run(new DefaultApplicationArguments());

        assertThat(Files.readAllLines(file)).hasSize(10_001);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Wrote 10000 employees to " + file);
    }

    @Test
    void writesTheSameBytesEveryTimeOnTheSameDay() throws IOException {
        final Path first = directory.resolve("first.csv");
        final Path second = directory.resolve("second.csv");

        run(first);
        run(second);

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
    }

    private static void run(final Path file) throws IOException {
        new SeedRunner(CLOCK, file, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(new DefaultApplicationArguments());
    }
}
```

`.../dataload/LoadRunnerIntegrationTest.java`:

```java
package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class LoadRunnerIntegrationTest {

    private static final String HEADER = String.join(",", Column.headers());
    private static final String ROW =
            "Asha Rao,asha@acme.example,2024-04-01,Engineering,Software Engineer,IN,INR,1250000.00,,";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path directory;

    @Autowired
    private EmployeeCsvLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void exitsWithZeroAndSaysHowManyWereLoadedWhenEveryRowLoads() throws IOException {
        final LoadRunner runner = runnerFor(HEADER + "\n" + ROW + "\n");

        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isZero();
        assertThat(printed()).contains("Loaded 1, rejected 0, in 0 ms.");
    }

    @Test
    void exitsNonZeroAndListsTheRejectedRowWhenARowIsRejected() throws IOException {
        final LoadRunner runner = runnerFor(HEADER + "\n" + ROW + "\n" + ROW + "\n");

        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isEqualTo(1);
        assertThat(printed()).contains("Row 3 [email]: EMAIL_ALREADY_EXISTS").contains("Loaded 1, rejected 1");
    }

    @Test
    void exitsNonZeroAndSaysWhyWhenTheHeaderIsWrong() throws IOException {
        final LoadRunner runner = runnerFor("fullName,email\nAsha Rao,asha@acme.example\n");

        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isEqualTo(1);
        assertThat(printed()).contains("Not loaded: Missing required columns");
    }

    private LoadRunner runnerFor(final String csv) throws IOException {
        final Path file = Files.writeString(directory.resolve("employees.csv"), csv);
        return new LoadRunner(loader, file, new PrintStream(output, true, StandardCharsets.UTF_8), CLOCK);
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cd backend && ./gradlew test --tests '*BatchModeTest' --tests '*SeedRunnerTest' --console=plain --warning-mode=none`
Expected: FAIL to compile, "cannot find symbol" for `BatchMode`, `SeedRunner`, `LoadRunner`.

- [ ] **Step 3: Write the modes**

`.../dataload/BatchMode.java`:

```java
package com.acme.peoplemanagement.dataload;

import org.springframework.core.env.Environment;

/** The command-line options that turn a run of the jar into a one-off job that ends when it is done. */
public final class BatchMode {

    public static final String LOAD_PROPERTY = "acme.load";
    public static final String SEED_PROPERTY = "acme.seed-csv";

    private BatchMode() {
    }

    public static boolean requestedBy(final Environment environment) {
        return environment.containsProperty(LOAD_PROPERTY) || environment.containsProperty(SEED_PROPERTY);
    }
}
```

`.../dataload/SeedRunner.java`:

```java
package com.acme.peoplemanagement.dataload;

import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.seed.SeedCsvWriter;
import com.acme.peoplemanagement.dataload.seed.SeedGenerator;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code --acme.seed-csv=<file>}: writes the 10,000-employee seed. The same day always gives the same file. */
@Component
@ConditionalOnProperty(BatchMode.SEED_PROPERTY)
class SeedRunner implements ApplicationRunner {

    static final int EMPLOYEE_COUNT = 10_000;
    static final long RANDOM_SEED = 20_261_023L;

    private final Clock clock;
    private final Path file;
    private final PrintStream output;

    @Autowired
    SeedRunner(final Clock clock, @Value("${" + BatchMode.SEED_PROPERTY + "}") final Path file) {
        this(clock, file, System.out);
    }

    SeedRunner(final Clock clock, final Path file, final PrintStream output) {
        this.clock = clock;
        this.file = file;
        this.output = output;
    }

    @Override
    public void run(final ApplicationArguments arguments) throws IOException {
        final List<EmployeeRow> rows = SeedGenerator.generate(EMPLOYEE_COUNT, RANDOM_SEED, LocalDate.now(clock));
        try (Writer writer = Files.newBufferedWriter(file)) {
            SeedCsvWriter.write(rows, writer);
        }
        output.println("Wrote %d employees to %s.".formatted(rows.size(), file));
    }
}
```

`.../dataload/LoadRunner.java`:

```java
package com.acme.peoplemanagement.dataload;

import com.acme.peoplemanagement.dataload.rules.FileRejectedException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code --acme.load=<file>}: loads a CSV, prints what happened, and leaves a non-zero exit status if a row failed. */
@Component
@ConditionalOnProperty(BatchMode.LOAD_PROPERTY)
class LoadRunner implements ApplicationRunner, ExitCodeGenerator {

    private final EmployeeCsvLoader loader;
    private final Path file;
    private final PrintStream output;
    private final Clock clock;
    private int exitCode;

    @Autowired
    LoadRunner(
            final EmployeeCsvLoader loader, @Value("${" + BatchMode.LOAD_PROPERTY + "}") final Path file,
            final Clock clock) {
        this(loader, file, System.out, clock);
    }

    LoadRunner(final EmployeeCsvLoader loader, final Path file, final PrintStream output, final Clock clock) {
        this.loader = loader;
        this.file = file;
        this.output = output;
        this.clock = clock;
    }

    @Override
    public void run(final ApplicationArguments arguments) throws IOException {
        final long startedAt = clock.millis();
        try (Reader reader = Files.newBufferedReader(file)) {
            final LoadReport report = loader.load(reader);
            report.rejected().forEach(rejected -> output.println(
                    "Row %d [%s]: %s".formatted(rejected.rowNumber(), rejected.field(), rejected.reason())));
            output.println("Loaded %d, rejected %d, in %d ms.".formatted(
                    report.loaded(), report.rejected().size(), clock.millis() - startedAt));
            exitCode = report.hasRejections() ? 1 : 0;
        } catch (FileRejectedException exception) {
            output.println("Not loaded: " + exception.getMessage());
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
```

`PeopleManagementApplication.java`:

```java
package com.acme.peoplemanagement;

import com.acme.peoplemanagement.dataload.BatchMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class PeopleManagementApplication {

    public static void main(final String[] args) {
        final ConfigurableApplicationContext context = SpringApplication.run(PeopleManagementApplication.class, args);
        if (BatchMode.requestedBy(context.getEnvironment())) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd backend && ./gradlew test --tests '*BatchModeTest' --tests '*SeedRunnerTest' --console=plain --warning-mode=none` then `./gradlew integrationTest --tests '*LoadRunnerIntegrationTest' --console=plain --warning-mode=none`
Expected: 5 fast tests and 3 integration tests pass. The summary line in the first runner test is exact because the fixed clock gives 0 ms.

- [ ] **Step 5: Prove each test by breaking the code**

| Mutation | Expected failing tests |
|---|---|
| `BatchMode`: `LOAD_PROPERTY = "acme.load"` to `"acme.loaded"` | `isRequestedByTheLoadOption` (and the integration runner tests do not start the runner, so only this) |
| `BatchMode`: `\|\|` to `&&` | `isRequestedByTheLoadOption`, `isRequestedByTheSeedOption` |
| `SeedRunner`: `EMPLOYEE_COUNT = 10_000` to `9_999` | `writesTenThousandEmployeesAndAHeaderAndSaysSo` |
| `SeedRunner`: the `RANDOM_SEED` argument of `SeedGenerator.generate(...)` to `System.nanoTime()` | `writesTheSameBytesEveryTimeOnTheSameDay` |
| `LoadRunner`: `report.hasRejections() ? 1 : 0` to `0` | `exitsNonZeroAndListsTheRejectedRowWhenARowIsRejected` |
| `LoadRunner`: `exitCode = 1;` in the `FileRejectedException` branch to `exitCode = 0;` | `exitsNonZeroAndSaysWhyWhenTheHeaderIsWrong` |
| `LoadRunner`: `"Row %d [%s]: %s"` to `"Row %d (%s): %s"` | `exitsNonZeroAndListsTheRejectedRowWhenARowIsRejected` |

- [ ] **Step 6: Gate, and settle the two open risks**

Run: `make test-backend && make test-integration`
Expected: both exit 0. Then handle these, each with a ledger line:

1. If SpotBugs reports `DM_EXIT` on `PeopleManagementApplication.main`, add this narrow match to `backend/config/spotbugs/exclude.xml` and rerun (a `System.exit` in `main` is the only way to return an exit status):

```xml
    <!-- The one place a batch run turns its result into an exit status -->
    <Match>
        <Bug pattern="DM_EXIT"/>
        <Class name="com.acme.peoplemanagement.PeopleManagementApplication"/>
        <Method name="main"/>
    </Match>
```

2. If SpotBugs reports `EI_EXPOSE_REP2` for `LoadRunner` or `SeedRunner` storing the `PrintStream`, the constructors are package-private, so first check that report is real, then add a narrow exclude naming the two classes, the same way the `TestEmployees` one is written.

```bash
git add backend/src/main/java/com/acme/peoplemanagement/dataload backend/src/main/java/com/acme/peoplemanagement/PeopleManagementApplication.java backend/src/test/java/com/acme/peoplemanagement/dataload backend/config/spotbugs/exclude.xml
```

(`git add` of `exclude.xml` only if it changed.)

Suggested message: `feat(loader): add the --acme.load and --acme.seed-csv modes with an exit status`

---

### Task 7: `make seed`, a real run and the 10,000-row timing

**Files:**
- Create: `scripts/seed.sh`
- Modify: `Makefile`, `.gitignore`

**Interfaces:**
- Consumes: the two modes (Task 6).

- [ ] **Step 1: Write the script**

`scripts/seed.sh`:

```sh
#!/bin/sh
# Generates seed.csv and loads it into the database named in .env. Run it on an empty database.
set -eu
ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"
set -a
. ./.env
set +a
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${ACME_DB_PORT:-5432}/${ACME_DB_NAME}"
export SPRING_DATASOURCE_USERNAME="$ACME_DB_USER"
export SPRING_DATASOURCE_PASSWORD="$ACME_DB_PASSWORD"
FILE="$ROOT/seed.csv"
QUIET="--server.port=0 --spring.main.banner-mode=off --logging.level.root=WARN"
cd backend
./gradlew bootRun --quiet --console=plain --args="$QUIET --acme.seed-csv=$FILE"
./gradlew bootRun --quiet --console=plain --args="$QUIET --acme.load=$FILE"
```

- [ ] **Step 2: Add the target and the ignore rule**

In `Makefile`, add `seed` to the `.PHONY` list and add after `test-e2e`:

```make
seed: ## Generate seed.csv (10,000 employees) and load it into the database named in .env. Run on an empty database
	sh scripts/seed.sh
```

In `.gitignore`, after the `# Env` block:

```
# Generated seed
seed.csv
```

- [ ] **Step 3: Run it for real (the owner runs it, because it writes 10,000 rows to their database)**

Postgres must be up (`docker compose ps` shows `healthy`). Ask the owner to run, in their terminal:

```bash
! make seed
```

Expected: `Wrote 10000 employees to .../seed.csv.` then `Loaded 10000, rejected 0, in <N> ms.`, exit status 0. If the owner's dev database already holds the `real-run-` employees, they do not clash, because the seed emails end in `@acme.example` and carry a number.

If it is slow (more than 60 seconds for the load), stop here: the plan has no batching task. Record the number and put the choice to the owner with the profile in hand, as the spec says.

- [ ] **Step 4: Check the data and the second run**

Ask the owner to run, then read the output:

```bash
! make seed
```

Expected: the generate step succeeds, and the load prints 10,000 `[email]: EMAIL_ALREADY_EXISTS` lines, then `Loaded 0, rejected 10000`, and `make` fails with a non-zero exit. That is the documented duplicate-safe, not-a-no-op behaviour.

Then a read-only spot check against the database (the owner runs it, or ask before running a query yourself):

```sql
select country_code, count(*) from employee group by 1 order by 2 desc;
select s.currency_code, count(*), min(s.amount_minor), max(s.amount_minor)
from salary_entry s join employee e on e.id = s.employee_id
where e.email like '%@acme.example' group by 1 order by 2 desc;
```

Expected: ten countries in roughly the planned proportions, and each currency's minimum and maximum amounts plausible (INR in the tens of millions of minor units, JPY in the millions).

- [ ] **Step 5: Gate and stage**

Run: `make test-backend`
Expected: exit 0 (no Java changed, so this only confirms the tree is clean).

```bash
git add scripts/seed.sh Makefile .gitignore
```

Suggested message: `chore(loader): add make seed to generate and load the 10,000-employee seed`

---

### Task 8: Docs and the final verification

**Files:**
- Modify: `docs/design.md`, `docs/tradeoffs.md`, `docs/requirements.md`, `docs/ai-usage.md`, `docs/superpowers/specs/2026-10-23-slice-4-csv-loader-and-seed-design.md`, `.claude/skills/backend-slice/SKILL.md`

- [ ] **Step 1: Correct the spec to match what was built**

In the spec: replace "The web server is switched off so the process can exit." with "A random port (`--server.port=0`) avoids clashing with a running API, and `main` exits with the run's status."; replace the `--args` line to read `"--server.port=0 --acme.load=seed.csv"`; and replace "the row number (the line number in the file, counting the header as line 1)" with "the row number, counting the header as row 1 and skipping blank lines".

- [ ] **Step 2: Update `docs/design.md`**

In the "Data loading" section replace "(the exact mechanism is chosen in slice 4)" with the chosen mechanism (two modes of the jar, `make seed`). Add decision rows for the choices in the spec (the loader as a jar mode, the generator in Java with its own table checked against the reference data, the multiplicative pay model, anniversary-dated entries, leavers deferred, Commons CSV). Remove "Seed generator: realistic and deterministic pay by title..." from "Still to write". In the slice list, note that slice 6 must remove the `terminationDate` rejection in the loader and turn on about 8% leavers in the generator.

- [ ] **Step 3: Record the numbers in `docs/tradeoffs.md`**

Add the measured 10,000-row load time from Task 7, the machine it ran on, whether batching was needed, and the note that the seed's dates depend on the day it is generated (the same file for the same day).

- [ ] **Step 4: Update `docs/requirements.md` and the skill**

Requirements: the loader's limits (ISO dates only, major units with a dot and no thousands separators, `.xlsx` not parsed, no leavers until slice 6, a re-run rejects every row). Skill `backend-slice/SKILL.md`: add one pitfall line under section 6 or 8: "A command-line mode of the jar ends through `System.exit(SpringApplication.exit(context))` in `main`. Run it with `--server.port=0`, and never switch the web application off, because the security configuration needs it."

- [ ] **Step 5: Add the Session 5 entry to `docs/ai-usage.md`**

Follow the existing entries' style. Record: the six decisions and what the owner chose; what the AI got wrong or the code contradicted (the spec assumed a termination date on create, the generator rationale about reading reference lists was wrong and was replaced by a drift test, the deprecated duplicate-header flag, `System.exit` and the web server); the mutation proofs; the timing. Mark what is for the owner to rewrite in their own voice.

- [ ] **Step 6: Full verification**

Run: `make check`
Expected: exit 0. Report the test counts (the fast and integration totals, previously 77 and 85, plus slice 4's).

- [ ] **Step 7: Whole-branch review**

Ask the owner whether to run a review of slice 4 (`/code-review`, a self-review, or skip). Do not decide for them.

- [ ] **Step 8: Stage**

```bash
git add docs/design.md docs/tradeoffs.md docs/requirements.md docs/ai-usage.md docs/superpowers/specs/2026-10-23-slice-4-csv-loader-and-seed-design.md .claude/skills/backend-slice/SKILL.md
```

Suggested message: `docs: record the slice 4 decisions, the load timing and the session`

Spec and plan are committed separately before Task 1, as before: `docs/superpowers/specs/2026-10-23-slice-4-csv-loader-and-seed-design.md` with `docs(spec): design the CSV loader and the seed (slice 4)`, then this plan with `docs(plan): plan slice 4 (the CSV loader and the seed)`. If the spec is committed first, Step 1 of this task edits an already committed file, which is fine.
