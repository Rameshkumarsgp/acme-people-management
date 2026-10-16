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

## Excel migration is a CSV loader, not an upload screen

**Decision.** Migrating ACME's existing spreadsheet is a one-time CSV load from the command line. The 10,000-employee seed is the same loader fed by a deterministic generator. There is no upload screen, no `.xlsx` parsing and no export.

**Why.** HR will not retype 10,000 employees, so a migration path must exist. The loader is needed for the seed anyway, so supporting a documented CSV format costs little. Excel saves as CSV. An upload screen with column mapping and a per-row error review is a feature on the scale of the directory.

**What it costs.**

- **Titles and departments must already exist.** They are a standardized list that HR cannot extend, so a spreadsheet with free-text titles has those rows rejected for unknown values. The report groups the rejections, and adding a title needs a migration. For a real cutover this limitation may not hold, and it ties back to the question to Incubyte about standardized versus free-text titles.
- Only the current salary is loaded. Salary history needs a second file.
- HR must export the sheet to CSV first.
- Throughput of loading 10,000 rows is unmeasured. We record the number here once the seed slice is built.

**Alternatives rejected.**

| Option | Why not |
|---|---|
| In-app upload screen | Column mapping, preview and row-level error review are a feature of their own. |
| No migration path | The first question an evaluator would ask, and a real system cannot start empty. |

**Related.** [design.md](design.md), section "Data loading".

## The shared HTTP Basic login is proportionate for this assessment, not production authentication

**Decision.** One shared account, HTTP Basic, credentials from environment config, no session and no cookie. Every `/api` endpoint is authenticated by default, the app refuses to start without credentials, and a throttle limits failed attempts.

**Why.** The brief has one HR persona and a shared login. This needs no identity provider, no token service and no user table, and it is easy to test.

**What it costs.**

- The password is sent on every request, so a stolen request or a script injected into the page exposes the password itself, not a short-lived token.
- The frontend keeps the credentials in memory, so a cross-site scripting flaw could read them. A page refresh means logging in again.
- One shared account cannot attribute a change to a person. The change log records what and when, never who.
- There is no multi-factor authentication or rotation, and the password lives in an environment variable.
- Basic over plain HTTP is only acceptable on localhost. A hosted deployment needs HTTPS.
- **The throttle is effectively global.** Behind nginx, every request reaches the API from nginx's address, so failed attempts are counted together. Someone who keeps guessing wrongly can block logins for the throttle window. We accept this for one user and do not trust forwarded-address headers, which a client can forge.

**Safeguards.** Authenticated by default. Fail-fast startup without credentials. The 401 carries no `WWW-Authenticate` challenge, as defense in depth against browser credential caching. After 5 failed attempts within 60 seconds the API answers 429 with `Retry-After`, and credentials are not checked during that window, so a correct guess cannot slip through.

**Upgrade path.** Short-lived tokens issued by a login endpoint, so the password is sent once. Individual accounts and roles, which also enable a "who changed it" trail (question 6 in `requirements.md`). Then a real identity provider, used as a service and not built here, because writing our own login is where most security mistakes happen. Credentials would move from environment variables to a secrets vault: environment variables can be read by anyone with access to the host or container and can appear in process listings and crash dumps, while a vault controls and logs access and supports rotation.

**Related.** [design.md](design.md), the API conventions table.
