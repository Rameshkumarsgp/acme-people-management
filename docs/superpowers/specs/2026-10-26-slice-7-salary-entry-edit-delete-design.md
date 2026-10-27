# Slice 7: edit and delete a salary entry, and the change log

Status: Draft. The owner asked for the remaining slices to be completed without a decision-by-decision dialogue, so each decision states its reason. Parent documents: [design.md](../../design.md) (the `salary_entry_change` table, the locking rules, the endpoints), the [slice 3 spec](2026-10-22-slice-3-salary-history-design.md) and the [slice 6 spec](2026-10-24-slice-6-edit-terminate-delete-design.md).

## Goal

HR corrects a salary entry entered by mistake, removes one, and sees what changed and when. This also removes the slice 6 limit: an employee with a future-dated raise can now be terminated once that entry is deleted.

## Scope

In: `PUT` and `DELETE` on a salary entry, `GET /api/employees/{id}/salary-changes`, the `salary_entry_change` table (migration 011), the change-log write for add, edit and delete, and the Angular edit and delete in the history table plus a change history list. Out: auditing profile changes and terminations (the design excludes them), and who made a change (one shared login).

## Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Edit and delete take the employee row lock, build the **resulting timeline**, and run the same validator, with the employee's `EmploymentPeriod`. A rule violation changes nothing. | The design says one validator covers add, edit and delete. A separate rule per operation was rejected. |
| 2 | **Editing replaces the entry's date, amount and currency.** An omitted currency keeps the entry's own. An edit that changes nothing writes no change row. | Edit has no "entry before the date" to default from, so the entry's own currency is the natural default. A no-op logged as a change would be noise. |
| 3 | The **change row is written in the same transaction** as the change, by `SalaryService`, the only writer of salary rows. A failed change leaves no row. Adding an entry now logs `ADDED` (slice 3 wrote none). Creating an employee and the CSV load log nothing. | The design requires a salary change and its row to succeed or fail together. An initial load is not a change. |
| 4 | `salary_entry_change.entry_id` is **not a foreign key**, and `employee_id` cascades on delete. Deleting an entry keeps its change rows, and deleting the employee removes them. | As the design states. |
| 5 | The change table has a **check on its shape**: `ADDED` has only new values, `DELETED` only old ones, `EDITED` both. | What a constraint can express goes in the database. |
| 6 | A `PUT` or `DELETE` of an entry that does not exist, or that belongs to another employee, is `404`. | The employee in the path must own the entry. |
| 7 | `AddSalaryEntryRequest` is renamed `SalaryEntryRequest`, because the edit uses the same fields. | A name should not say "add" for an edit. |
| 8 | `changed_at` comes from the injected clock, not from the database. | The clock is injected everywhere, so tests can fix it. |
| 9 | The frontend renames `AddSalaryDialog` to `SalaryEntryDialog`, which adds or edits, and generalizes the delete confirmation to take its own title and message. | One dialog for the same fields, and one confirmation for both deletes. |

## API

`PUT /api/employees/{id}/salary-entries/{entryId}` with `{ effectiveDate, amountMinor, currency? }` answers `200` with the entry as it now sits on the timeline. `DELETE` answers `204`. `GET /api/employees/{id}/salary-changes` answers the change log, newest first:

```
[ { "id": 9, "entryId": 5, "action": "EDITED", "changedAt": "2026-10-26T09:00:00Z",
    "before": { "effectiveDate": "2026-04-01", "amountMinor": 150000000, "currency": "INR" },
    "after":  { "effectiveDate": "2026-04-01", "amountMinor": 160000000, "currency": "INR" } } ]
```

`before` is null for `ADDED` and `after` is null for `DELETED`.

| Situation | Status |
|---|---|
| Valid change | `200` / `204` |
| Invalid shape | `400` with `errors` |
| Entry or employee not found | `404` |
| Duplicate date, before hire, after termination, or no current salary left | `422` with the rule code |

## Testing

1. The migration: each constraint, the cascade, and that deleting an entry keeps its change rows.
2. The salary service against PostgreSQL: edit, delete and add, each with its change row, each rule violation leaving no row, the no-op edit, an entry of another employee, and the newest-first order.
3. Concurrency: two simultaneous deletes of the only two entries leave exactly one entry.
4. The web slice and one end-to-end path over HTTP.
5. The frontend: the API client, the dialog in edit mode, the history table's actions, the change list, and the detail page.
6. A real run on the database and in the browser.

## Constraints and risks

- **Deleting every entry is refused** (`NO_CURRENT_SALARY`), so an employee always has a current salary.
- **The change log is not tamper-proof.** Anyone with the shared login can add and delete entries, and the log records what and when, never who.
