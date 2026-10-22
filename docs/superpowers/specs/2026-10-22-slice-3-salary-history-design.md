# Slice 3: salary history and adding a salary entry

Status: Draft for review. Decisions were made one at a time with the project owner on 2026-10-22. Parent documents: [design.md](../../design.md) (data model, API, rules), [requirements.md](../../requirements.md) and the [slice 2 spec](2026-10-19-slice-2-employee-create-read-design.md). This spec adds only what slice 3 needs and does not change them. Built with the [backend-slice skill](../../../.claude/skills/backend-slice/SKILL.md).

## Goal

HR sees an employee's salary history on the detail page and adds a new entry, such as a raise. The entry can be dated in the future (it shows as "scheduled") and can be in a different currency. Backend first (`feat(api)`), then the Angular table and dialog (`feat(ui)`).

## Scope

In:

- `GET /api/employees/{id}/salary-entries` and `POST /api/employees/{id}/salary-entries`, as in [design.md](../../design.md#endpoints).
- The new rule `DUPLICATE_SALARY_DATE`, and the derived history (end date, `current`, `scheduled`).
- The employee row lock around adding an entry.
- A salary history table and an "Add salary" dialog on the employee detail page.

Out (later slices): editing and deleting an entry (slice 7), the change log (slice 7), `SALARY_AFTER_TERMINATION` and the termination rules (slice 6). **No migration**: `salary_entry` already exists.

## Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | The endpoint lives in `employee`. A new `EmployeeSalaryService` in `employee` locks the employee row, reads the hire date, then calls `SalaryService`. `salary` never imports `employee`. | No cycle with the existing `employee` to `salary` dependency, and the validator still takes the hire date as a parameter. A separate service keeps salary use cases out of `EmployeeService` (single responsibility). |
| 2 | Adding an entry writes no change-log row in this slice. Slice 7 creates `salary_entry_change` and logs add, edit and delete together, which includes adding the logging call to `addEntry`. | Nothing can read the rows until slice 7, so writing them now is write-only data. There is no deployed data, and the seed does not log either. |
| 3 | A pure `SalaryHistory` in `salary/rules/` builds the list from the entries and today. Newest first. `endDate` is the **last day of the period** (the day before the next entry starts), and `null` for the newest. `current` is the latest entry on or before today. `scheduled` is any entry after today. | One definition, tested in milliseconds, using the server clock. The last-day convention matches the termination date meaning "last employed day" (design decision 10). |
| 4 | `DUPLICATE_SALARY_DATE` is a rule of `SalaryTimelineValidator`, checked on the resulting timeline under the employee row lock. The unique index on `(employee_id, effective_date)` stays as a backstop. | The lock admits one writer at a time, so the check cannot be raced. Every timeline invariant stays in one class, so slice 7's edit reuses it. This departs from slice 2's "the index is the only judge", which fits email, where no lock exists. |
| 5 | If a request omits the currency, it defaults to the entry **immediately before the new date**. If no entry precedes it (the new entry is the earliest), the currency is required and its absence is a `400` naming the field. | The default follows the timeline's own logic, and gives a sensible answer for a backdated entry. |
| 6 | `POST` returns `201` with the created entry, including its `id`, and a relative `Location` of `/api/employees/{id}/salary-entries/{entryId}`. The page then re-reads the list and the employee. | Every derived value comes from the server. `id` is needed by slice 7, and the `Location` URL becomes a real resource there. |
| 7 | The dialog always sends the currency, preselected to the employee's current salary currency, and HR can change it. | The amount check needs the currency's decimals before submit, so the rule "which currency" stays in one place. The server default serves other API clients. |

## Real code facts that shaped this spec

- `SalaryEntry` has an `id` field but no accessor. A package-private `id()` is added, because only `SalaryService` in the same package reads it.
- `EmployeeRepository` is an empty interface. It gains a locking read (`@Lock(PESSIMISTIC_WRITE)`), which does not bump `@Version`, as the design requires.
- `SalaryTimelineValidator.validate(hireDate, timeline, today)` already takes the whole timeline. The new rule is one more check inside it, after the hire date rule.
- `SalaryService`, `ReferenceLookup`, `TimelineEntry` and `Money` are public. `SalaryEntry` and its repository are package-private and stay so.
- `UnknownReferenceException` (in `employee.errors`) already maps to a `400` with a field. A missing currency uses a separate exception, because "unknown" and "required" are different messages.

## API

`POST /api/employees/{id}/salary-entries`:

```
{ "effectiveDate": "2026-01-01", "amountMinor": 150000000, "currency": "INR" }
```

`currency` is optional (decision 5). The response is `201`, with `Location`, and the entry:

```
{ "id": 5, "effectiveDate": "2026-01-01", "amountMinor": 150000000, "currency": "INR",
  "endDate": null, "current": true, "scheduled": false }
```

`GET /api/employees/{id}/salary-entries` returns a list of the same objects, newest first. The derived fields in the `POST` response describe the entry on the timeline after the change.

| Situation | Status and body |
|---|---|
| Valid | `201` / `200` |
| Invalid shape (missing date, amount below 1 or above the cap, bad currency format) | `400`, `errors: [{ field, message }]` |
| Currency omitted and no earlier entry | `400`, field `currency` |
| Currency not in the reference list | `400`, field `currency` |
| Date already used | `422`, `code: DUPLICATE_SALARY_DATE` |
| Date before the hire date | `422`, `code: SALARY_BEFORE_HIRE_DATE` |
| Employee does not exist | `404` |

## Backend

### Packages

| Package | Added in slice 3 |
|---|---|
| `salary/rules` | `SalaryHistory`, `HistoryEntry`, `IdentifiedEntry` (an entry with its id), the new `SalaryRuleCode.DUPLICATE_SALARY_DATE` and its check in the validator |
| `salary/errors` | `CurrencyRequiredException` |
| `salary` | `SalaryService.addEntry` and `history`; a repository method that lists an employee's entries; `SalaryEntry.id()` |
| `employee` | `EmployeeSalaryService`, `EmployeeRepository` locking read |
| `employee/api` | the controller methods, `AddSalaryEntryRequest`, `SalaryEntryResponse` |
| `config` | handler for `CurrencyRequiredException` (400 with the field) |

### Add flow

1. The controller validates the shape.
2. `EmployeeSalaryService.addEntry` starts a transaction and reads the employee **with the lock** (404 if absent).
3. If a currency was given, it must exist (`ReferenceLookup`), else `UnknownReferenceException("currency", ...)`.
4. It calls `SalaryService.addEntry(employeeId, hireDate, effectiveDate, amountMinor, currencyOrNull, today)`.
5. `SalaryService` loads the timeline, resolves the currency (decision 5), builds the resulting timeline, runs the validator, saves, and returns the entry as seen on the new timeline.
6. A violation rolls everything back. The response is `201` with `Location`.

### Locking

Salary operations take the row lock and do not bump `Employee.version`, so a salary change does not invalidate an open employee edit form. The lock is held until the transaction ends.

## Frontend

- `EmployeeApi` gains `listSalaryEntries(employeeId)` and `addSalaryEntry(employeeId, request)`, with the response and request types.
- Inside the `employee-detail` feature folder: a `salary-history` component (a Material table: effective date, end date, amount with its currency, and a Current or Scheduled marker) and an `add-salary-dialog` component (`MatDialog`): a native date input, an amount, and a currency select preselected to the current salary's currency.
- The dialog reuses `parseMajorToMinor` and `toFormErrors`, ignores a second click while saving, and keeps the typed values on a server error. A `422` shows the server wording in a banner, and a field error shows under its field.
- On success the dialog closes and the page re-reads the history and the employee.
- Loading, empty (no entries cannot happen, but the table handles it), and error states are shown, with a retry.

## Testing, in build order

1. **Unit, no Spring:** `SalaryHistory` (one entry; several entries; a future entry; a future entry as the only one besides a current one; the exact boundary of an entry dated today; the last-day end date). The duplicate rule (a duplicate; no duplicate), each proven by breaking the code.
2. **Integration:** `SalaryService.addEntry` (adds and returns derived fields; the currency default from the entry before the date, including a backdated entry; the required-currency error; duplicate; before hire date; nothing saved on a violation).
3. **Integration, concurrency:** two simultaneous adds on the same date leave exactly one entry in the database, one `201` and one `422`, and the final state is asserted, not just the responses.
4. **Web slice:** validation, each status in the table above, `Location`.
5. **End to end through HTTP:** add a raise, read the list, check the markers.
6. **Frontend:** `EmployeeApi`, the history table, the dialog (amount decimals by currency, double submit, server errors in place), and the detail page reloading after an add.

## Known constraints and risks

- **Slice 7 retrofit.** `addEntry` must gain the change-log write when slice 7 builds the table. Recorded in the slice 7 plan and here.
- **The lock is a database row lock.** It serializes adds per employee only. Different employees do not block each other.
- **A string where a number is expected** is still accepted (a minor from the slice 2 review).
- The `backend-slice` skill line "a duplicate is judged by the unique index alone" is reworded to "unless a row lock serializes the writers".
