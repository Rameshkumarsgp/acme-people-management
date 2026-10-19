# Slice 2: create and read an employee with the first salary entry

Status: Draft for review. Decisions were made one at a time with the project owner on 2026-10-19. Parent documents: [design.md](../../design.md) (data model, API, rules) and [requirements.md](../../requirements.md). This spec only adds what slice 2 needs and does not change them.

## Goal

HR can create an employee together with their first salary entry, and open the employee detail with the current salary. Backend first (`feat(api)`), then the Angular form and detail page (`feat(ui)`).

## Scope

In:

- `POST /api/employees` and `GET /api/employees/{id}`, with the shapes in [design.md](../../design.md#shapes-first-slices).
- Migration for `employee` and `salary_entry`.
- Two timeline rules: `SALARY_BEFORE_HIRE_DATE` and `NO_CURRENT_SALARY`.
- Create-employee form and employee detail page.

Out (later slices): `terminationDate` on create, `DUPLICATE_SALARY_DATE`, `SALARY_AFTER_TERMINATION`, `TERMINATION_BEFORE_HIRE_DATE`, the history list, the add-entry dialog, the change log, the locking described in design.md.

## Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | The timeline rules live in one pure class, `salary/SalaryTimelineValidator.validate(hireDate, timeline, today)`. Slice 2 has only the two rules it can break. Later slices add one rule and one test each to the same class. | One home for the rule, testable in milliseconds with no Spring. Matches design.md ("one pure domain class"). Inline checks would be rewritten in slice 3. |
| 2 | A broken rule throws the unchecked `SalaryRuleViolation(code, detail)`. One `@RestControllerAdvice` in `config` maps exception type to status and writes problem+json. The advice contains no business decisions. | Exceptions cannot be silently ignored like a returned list. `salary` stays free of HTTP. A new rule is a new enum value. |
| 3 | `salary` owns the `SalaryEntry` entity, its repository and `SalaryService` (`recordFirstEntry`, `currentSalary(employeeId, today)`). The entry stores a plain `employeeId` column, not a JPA link to `Employee`. `EmployeeService.create` calls `SalaryService` in one transaction. | One owner per table and one salary write path. The dependency points `employee` to `salary` only, as in the architecture diagram. |
| 4 | A duplicate email is detected only by the unique index on `lower(email)`. `EmployeeService` uses `saveAndFlush`, catches the violation, checks the constraint name, and throws `EmailAlreadyExistsException` (409). Other violations are rethrown. | Race-proof, and the rule lives in one place. A pre-check would be racy or duplicate the rule. |
| 5 | `EmployeeService` resolves department, job title, country and currency with `findById`. A missing row throws `UnknownReferenceException(field)` (400 with `errors: [{ field, message }]`). `SalaryService` receives a resolved currency code. | The response needs the names anyway. Reference rows change only by migration, so there is no race. `salary` does not depend on `reference`. |
| 6 | Request records use Bean Validation (`@Valid` in the controller). The advice maps `MethodArgumentNotValidException` to the design's `errors` list. The amount bounds come from one Java constant that the database check also states. | The contract sits next to the fields. One standard path. |
| 7 | A future hire date is rejected. It fails `NO_CURRENT_SALARY`, and the `detail` explains: the first salary must be effective on or before today, so the hire date cannot be in the future. The same code and message cover a past hire date with a first salary dated in the future. | Pre-hires are recruitment, which is out of scope. Every employee keeps a current salary. No second rule duplicating `NO_CURRENT_SALARY`. |
| 8 | `GET /api/employees/{id}` loads the entity (lazy `@ManyToOne` to the reference entities), gets the current salary from `SalaryService`, and derives `status` in Java from the injected `Clock`. ACTIVE when the termination date is null or on or after today. | Simple and testable. One employee needs a handful of small queries. |
| 9 | Tests use three layers, built in the order below. The service is not unit-tested with mocked repositories. | Fast where possible, real database where the behavior is the database. |
| 10 | The form converts input with `parseMajorToMinor(text, minorUnitDigits)` in `shared/`, working on digits as text. A matching `formatMinor` serves the detail page. No floats. | Exact for JPY, USD and KWD, and one place for the rule. `Math.round(x * 10 ** d)` is wrong for values such as 1.005. |
| 11 | `toFormErrors(problem)` in `core/` returns `{ fieldErrors, formError? }`. The component applies it and stays presentational. | The mapping is a decision, so it lives once. Reused by slices 3, 6 and 7. |

## Backend

### Packages

| Package | Contents added in slice 2 |
|---|---|
| `salary` | `SalaryTimelineValidator`, `SalaryRuleViolation`, `SalaryRuleCode` (two values), `SalaryEntry` (entity), `SalaryEntryRepository`, `SalaryService`, a money value for the entry |
| `employee` | `Employee` entity, `EmployeeRepository`, `EmployeeService`, `EmployeeController`, request and response records, `EmailAlreadyExistsException`, `EmployeeNotFoundException`, `UnknownReferenceException`, the status rule |
| `config` | The `@RestControllerAdvice` |

Dependencies: `employee` to `salary`, and `employee` to `reference`. `salary` imports neither `employee` nor `reference`.

### Create flow

1. The controller validates the shape (decision 6).
2. `EmployeeService.create` starts one transaction.
3. It resolves the four references (decision 5).
4. It saves the employee with `saveAndFlush` and translates the email violation (decision 4).
5. It calls `SalaryService.recordFirstEntry(employeeId, hireDate, entry)`, which builds the one-entry timeline, runs the validator with today from the `Clock`, and saves the entry. The entry date defaults to the hire date.
6. A violation rolls back the whole transaction, so no employee row is left behind.
7. The response is `201` with `Location` and the detail.

### Errors

| Exception | Status | Body |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `errors: [{ field, message }]` |
| `UnknownReferenceException` | 400 | one field error |
| `EmployeeNotFoundException` | 404 | title and detail |
| `EmailAlreadyExistsException` | 409 | `code: EMAIL_ALREADY_EXISTS` |
| `SalaryRuleViolation` | 422 | `code`, `detail` |

The body shape matches `ProblemResponseWriter`. Whether the advice reuses its body logic is to be verified when planning, so filter errors and controller errors stay identical.

### Migration

One file in `db/changelog/changes/` named after its first changeset, with two changesets (`employee`, then `salary_entry`), as in the data model in design.md. Every changeset has a `--rollback`. Slice 2 also creates the three indexes on `employee`.

## Frontend

- Lazy-loaded feature routes for the create form and the detail page.
- Reference lists (countries, departments, job titles, currencies) come from the existing reference endpoints through `core/`.
- `parseMajorToMinor` rejects more decimals than the currency allows and anything above the safe-integer limit. Changing the currency re-validates the amount field.
- `toFormErrors`: `400` field errors go under their fields (`initialSalary.*` maps to the salary controls). `EMAIL_ALREADY_EXISTS` goes under the email field. Other business codes show the server's `detail` in a banner. Anything else shows a generic "could not save, try again" banner. A field error clears when the user edits that field. Entered values are kept.
- Loading, empty, validation and error states are handled, as the slice plan requires.

## Testing, in build order

1. **Validator unit tests** (no Spring, no Docker). Test first, one rule at a time, each proven by breaking the check: entry before the hire date; only entry in the future; entry on the hire date; entry on today; a valid timeline. Also the status rule: termination date equal to today is ACTIVE.
2. **Migration integration tests:** constraints, the unique index on `lower(email)`, the amount range, `termination_date >= hire_date`, the cascade.
3. **Create and read integration tests:** the round trip, a duplicate email differing only in case (this also proves that the constraint name is readable through Hibernate, to be shown by a failing test first), unknown references, rollback (an invalid salary leaves no employee row).
4. **Web slice tests (`@WebMvcTest`):** shape validation, `201` with `Location`, and each exception-to-status mapping. The service is mocked here because it is the boundary.
5. **Frontend unit tests:** `parseMajorToMinor` and `formatMinor` (JPY, USD, KWD, trailing zeros, extra decimals, the cap), `toFormErrors`, then the component behavior. Playwright covers only what jsdom cannot.

## Known constraints and risks

- **Slice 3 and the cycle.** Salary operations will need the employee lock and hire date. `EmployeeService` will do the locking and pass the hire date into `SalaryService`, so `salary` never imports `employee`.
- **The "active" rule is stated twice.** Java here, SQL in slices 5 and 8. The boundary test (termination date equal to today is active) is repeated there.
- **Constraint-name detection is unverified.** The assumption is that Hibernate exposes the violated constraint's name on PostgreSQL. Test 3 proves it. The fallback is matching the index name in the exception.
- **Future hire dates are rejected.** To be recorded as a stated limit in the requirements.

## After this slice

The backend skill for the repeated pattern (endpoint plus database change) is written once a read and a write both exist, as the slice plan says.
