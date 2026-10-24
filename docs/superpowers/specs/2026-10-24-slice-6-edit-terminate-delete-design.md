# Slice 6: edit, terminate and delete an employee

Status: Draft. The project owner asked for the design decisions to be made and recorded without a question-by-question dialogue for this slice, so each decision below states its reason and the alternative rejected. Parent documents: [design.md](../../design.md) (rules, locking, deleting, endpoints), [requirements.md](../../requirements.md) and the [slice 5 spec](2026-10-24-slice-5-directory-design.md). Built with the [backend-slice skill](../../../.claude/skills/backend-slice/SKILL.md).

## Goal

HR corrects an employee's profile, records that someone left, and removes a record entered by mistake, without breaking the salary timeline. The same slice lets the CSV loader and the seed carry leavers, which slice 4 deferred, so the directory's **Former** filter finally has data.

## Scope

In:

- `PUT /api/employees/{id}` (profile and termination date, needs `version`) and `DELETE /api/employees/{id}` (needs `version`).
- The two timeline rules that were waiting for this slice, `TERMINATION_BEFORE_HIRE_DATE` and `SALARY_AFTER_TERMINATION`, and the `VERSION_CONFLICT` error.
- An optional `terminationDate` on create, so the loader and the seed can carry leavers.
- Angular: an edit form, and Edit and Delete on the detail page, with a confirmation for delete.
- The loader stops rejecting termination dates, and the seed makes about 8% of employees leavers.

Out (later slices): editing and deleting a salary entry and the change log (slice 7). **No migration**: `termination_date`, its check constraint and the delete cascade already exist.

## Decisions

| # | Decision | Reason and what was rejected |
|---|---|---|
| 1 | **Terminating is setting `terminationDate` in the edit.** There is no separate terminate endpoint. The edit form has an optional termination date, with the hint "the last day employed", and clearing it makes the person active again. | The design lists `PUT` as editing the profile "including `terminationDate`". A second endpoint would repeat the same checks. A separate "Terminate" button was rejected as a second path to the same rule. |
| 2 | **`PUT` replaces the profile.** The body carries every profile field and the `version`: full name, email, hire date, department, job title, country, termination date. It never touches salary. The response is the same detail as `GET`, with the new `version`. | One shape to validate and test. A partial `PATCH` was rejected because "absent" and "set to empty" would need separate meanings for the nullable termination date. |
| 3 | **The `version` is checked by the service under the row lock.** The service reads the employee with the existing `PESSIMISTIC_WRITE` lock, compares the sent `version` with the stored one, and answers `409` with code `VERSION_CONFLICT` on a mismatch. A successful update bumps the version. Adding a salary entry does not bump it. | One request at a time per employee, and the comparison cannot be raced. This is the design's "two mechanisms that coexist". Relying only on Hibernate's `@Version` exception was rejected, because it would be raised at flush, after the rules ran against stale data. |
| 4 | **The timeline is always revalidated on an update,** not only when a date changes. | The validator is cheap, and "only when a date changes" is one more condition to get wrong. The design says "whenever", and always is a superset. |
| 5 | **The validator takes an `EmploymentPeriod(hireDate, terminationDate)`** instead of a hire date, and the salary service takes the same value. Order of checks: termination before hire, entry before hire, duplicate date, no current salary, entry after termination. | The rules need both dates, and passing them as one value keeps every method within five parameters. Termination-before-hire comes first because it is the root cause when an entry also falls after the termination date. An entry dated **on** the termination date is allowed. |
| 6 | **A termination date in the future is allowed.** The person stays active until that day is over, as the status rule already says. | HR records notice periods in advance. It follows decision 10 in `design.md`. |
| 7 | **Adding a salary entry also checks the termination date.** `EmployeeSalaryService.add` passes the employee's period, so a raise dated after the termination date is a `422 SALARY_AFTER_TERMINATION`. | Otherwise the rule would hold on edit and not on add. |
| 8 | **`DELETE` takes the version as a query parameter** (`?version=3`). A missing version is a `400`. It removes the employee and, through the existing cascade, their salary entries. Success is `204`. | The design says delete "requires the employee's `version`, like `PUT`". A request body on `DELETE` is unreliable across proxies. |
| 9 | **The email check stays with the unique index.** An update that collides answers `409 EMAIL_ALREADY_EXISTS`, using the same constraint-name check as create. Changing only the case of one's own email is allowed. | One judge of a duplicate email, as in slice 2. |
| 10 | **The edit form lives in its own feature folder** (`features/employee-edit`, route `/employees/:id/edit`) and has no salary fields. A stale version shows a banner with a "Reload" button that loads the latest values and discards the edits. | The create form is built around the first salary. A shared form was rejected as more indirection than six fields are worth. Merging the stale edit automatically was rejected: it would hide someone else's change. |
| 11 | **Delete asks for confirmation in a dialog** that names the person and says it removes their salary history and cannot be undone. A stale version shows the same reload message on the detail page. | The design says the confirmation only prevents a mis-click, and it is not a control. |
| 12 | **The loader and the seed carry leavers.** `terminationDate` is parsed as an optional date. The generator makes about 8% leavers, each with a termination date after their hire date and on or before today, a salary dated at their last work anniversary **on or before** that date, and a tenure uplift counted to the termination date. | This is what decisions 10 and 19 of `design.md` set up. Tenure runs to the earlier of today and the termination date. |

## Real code facts that shaped this spec

- `Employee` has `terminationDate` and a `@Version`, but no way to change either. `EmployeeProfile` has no termination date.
- `EmployeeRepository.findByIdForUpdate` already takes `PESSIMISTIC_WRITE` without bumping the version, and `EmployeeSalaryService.add` uses it.
- `SalaryTimelineValidator.validate(hireDate, timeline, today)` has two production callers, `SalaryService.recordFirstEntry` and `addEntry`, and a handful of tests.
- `ApiExceptionHandler` maps by exception type. It has no `VERSION_CONFLICT` handler yet, and it already builds problem+json with a `code`.
- `EmployeeService.create` already judges a duplicate email by the unique index's name.
- The directory reads `termination_date` already, so leavers appear there with no change.
- `EmployeeRow` and the CSV have a `terminationDate` column that is always rejected, and the seed writes it empty.

## API

`PUT /api/employees/{id}`:

```
{ "fullName": "Asha Rao", "email": "asha.rao@acme.example", "hireDate": "2024-04-01",
  "departmentId": 3, "jobTitleId": 12, "countryCode": "IN",
  "terminationDate": null, "version": 4 }
```

`DELETE /api/employees/{id}?version=4`.

| Situation | Status and body |
|---|---|
| Valid update | `200`, the employee detail with the new `version` |
| Invalid shape, or an unknown department, job title or country | `400`, `errors: [{ field, message }]` |
| `version` missing on update or delete | `400` |
| Employee does not exist | `404` |
| `version` differs from the stored one | `409`, `code: VERSION_CONFLICT` |
| Email already used by another employee | `409`, `code: EMAIL_ALREADY_EXISTS` |
| Termination before the hire date | `422`, `code: TERMINATION_BEFORE_HIRE_DATE` |
| A salary entry would fall before the hire date | `422`, `code: SALARY_BEFORE_HIRE_DATE` |
| A salary entry is dated after the termination date | `422`, `code: SALARY_AFTER_TERMINATION` |
| Valid delete | `204` |

`POST /api/employees` gains an optional `terminationDate` and the same `422` rules.

## Backend

| Package | Added or changed |
|---|---|
| `salary/rules` | `EmploymentPeriod`, the two rule codes, the two checks, `validate(period, timeline, today)` |
| `salary` | `recordFirstEntry`, `addEntry` take an `EmploymentPeriod`, and a new `revalidate(employeeId, period, today)` loads the stored timeline and runs the rules |
| `employee` | `Employee` gains `applyProfile`, `EmployeeProfile` gains the termination date, `EmployeeService` gains `update` and `delete`, `EmployeeSalaryService.add` passes the period |
| `employee/api` | `UpdateEmployeeRequest`, the `PUT` and `DELETE` methods, `terminationDate` on `CreateEmployeeRequest` |
| `employee/errors` | `StaleVersionException` |
| `config` | handler for `StaleVersionException` (`409` with code `VERSION_CONFLICT`) |
| `dataload` | `EmployeeRow` and the loader carry the termination date |
| `dataload/seed` | about 8% leavers |

### Update flow

1. The controller validates the shape.
2. `EmployeeService.update` starts a transaction and reads the employee **with the lock** (`404` if absent).
3. It compares the versions (`409` on a mismatch) and checks that the references exist.
4. It applies the profile, flushes (the unique index judges the email), and asks the salary service to revalidate the timeline against the new period.
5. A violation rolls everything back, including the profile change.

### Delete flow

Lock, compare versions, delete, in one transaction. The cascade removes the salary entries.

## Frontend

- `EmployeeApi` gains `update(id, request)` and `delete(id, version)`, with the types.
- `features/employee-edit`: a form prefilled from the employee, with the termination date optional. Server field errors show under their fields and others in a banner. A `409 VERSION_CONFLICT` shows a banner with "Reload". Saving goes back to the detail page.
- The detail page gets Edit and Delete. Delete opens a confirmation dialog, then goes to the directory. A `409` or `404` shows a message on the page.
- `app.routes.ts` gets `/employees/:id/edit`.

## Testing, in build order

1. **Unit, no Spring:** the two new rules (each accepted and rejected case, the boundary day on the termination date, the order of checks), proven by breaking the code.
2. **Integration:** the salary service revalidates (an entry after the termination date, a hire date moved after an entry); adding an entry after the termination date is refused; create with a termination date; update applies the profile and bumps the version; a stale version is a conflict and changes nothing; a rule violation undoes the profile change; an email collision; changing only the case of one's own email; delete removes the employee and the entries and refuses a stale version; a salary add does not bump the version.
3. **Integration, concurrency:** two updates with the same version leave exactly one success and one conflict, repeated several rounds. A stale profile form submitted after a concurrent salary change succeeds and is revalidated (the design's named test). A raise added while the employee is terminated before it must fail.
4. **Web slice and end to end:** each status in the table above.
5. **Loader and seed:** the loader loads a leaver and rejects an impossible one (termination before hire, an entry after it), and the seed's leavers all satisfy the rules.
6. **Frontend:** the API client, the edit form (prefill, validation, server errors in place, the conflict banner and reload, double submit), the detail page (Edit link, delete confirmation, cancel does nothing, conflict and not-found messages).
7. **Measured and real:** a real run on the database through the new endpoints and the browser, recorded in `docs/ai-usage.md`.

## Known constraints and risks

- **Terminating an employee who has a future-dated raise is refused** (`SALARY_AFTER_TERMINATION`), and until slice 7 there is no way in the app to delete that entry. This is the rule working, and the message says why. Slice 7 removes the limitation.
- **Delete is irreversible** and leaves no record that it happened. That is the accepted cost recorded in `docs/tradeoffs.md`.
- **A stale edit discards the user's changes** when they reload. This is deliberate (decision 10).
- **The seed's date logic gets harder.** A leaver's salary must be dated on or before the termination date, and a test pins that for every seeded leaver.
- **The directory's "current salary" for a leaver** is still the latest entry on or before today, which is by the rule at most the termination date.
