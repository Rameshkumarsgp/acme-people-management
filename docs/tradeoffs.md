# Trade-offs

Decisions where we chose one cost over another, with the cost stated. Performance numbers from the 10,000-row seed are added here as they are measured.

## Hard delete removes the employee's audit trail

**Decision.** Deleting an employee removes the employee, their salary entries and their change-log rows (`ON DELETE CASCADE`). An employee who left is not deleted: they get a termination date and keep their history. Delete exists for records entered by mistake.

**Why.** It is the simplest option, and it leaves nothing retained or mis-handled after a delete. A larger design would need a retention policy that nobody has asked for.

**What it costs.**

- Nothing records that a deletion happened, and an accidental delete of a real employee is unrecoverable.
- It does not by itself meet any legal erasure or retention requirement. Backups and logs are outside it, and some jurisdictions require salary records to be kept for a period.

**Safeguards.**

- `DELETE` requires the employee's `version`, so a delete based on a stale view fails with 409.
- The UI asks for confirmation. This only prevents a mis-click.
- Database backups are the real protection, and a local deployment does not have them. This is the residual risk.

**Upgrade path.** If deletions must be traceable, add a minimal deletion record (employee ID, name, time, no salary data) written in the same transaction as the delete. It is an additive migration and touches no read path.

**Alternatives rejected.**

| Option | Why not |
|---|---|
| Drop the foreign key so the change log survives | Keeps a deleted person's salary data indefinitely, which creates a retention policy by accident. |
| Soft delete | Every read path, including the native-SQL insights, must exclude deleted employees, and one missed predicate leaks their salary into the statistics. It also blurs "left the company" with "entered by mistake". |
| Allow delete only with one salary entry | A speed bump only: HR can delete entries one by one first. |

**Related.** [design.md](design.md), section "Deleting an employee".
