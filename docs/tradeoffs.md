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

- **Titles and departments must already exist.** They are a standardized list that HR cannot extend, so a spreadsheet with free-text titles has those rows rejected for unknown values. The report lists each rejected row with its field (it does not group them), and adding a title needs a migration. For a real cutover this limitation may not hold, and it ties back to the question to Incubyte about standardized versus free-text titles.
- Only the current salary is loaded. Salary history needs a second file.
- HR must export the sheet to CSV first.
- A leaver is loaded with their termination date, and a row is rejected when the date is before the hire date or a first salary falls after it.
- Dates must be ISO, and amounts must be plain numbers with a dot (no thousands separators). Excel's local formats are rejected, not guessed.

**Measured.** Loading the 10,000-employee seed through the loader, one transaction per row through `EmployeeService.create`, on the owner's MacBook Pro with PostgreSQL 17 in Docker on the same machine, 23 October 2026:

| Run | Result | Time | Per row |
|---|---|---|---|
| First load of `seed.csv` | 10,000 loaded, 0 rejected | 32,958 ms | about 3.3 ms |
| Same file again | 0 loaded, 10,000 rejected as `EMAIL_ALREADY_EXISTS` | 75,870 ms | about 7.6 ms |

The load is well under the one-minute line set in the slice 4 plan, so writes are not batched and the per-row rules stay as they are. A rejected row costs about twice a loaded one, because a failed insert rolls back and builds an exception. A file with many rejections is therefore the slow case. These are single runs on one machine, not a benchmark.

**Alternatives rejected.**

| Option | Why not |
|---|---|
| In-app upload screen | Column mapping, preview and row-level error review are a feature of their own. |
| No migration path | The first question an evaluator would ask, and a real system cannot start empty. |
| Loading with plain SQL (`COPY`) | It would be faster, but it would skip the salary rules and become a second write path to salary rows. |

**Related.** [design.md](design.md), section "Data loading".

## The seed is synthetic, and its dates depend on the day it is made

**Decision.** The 10,000-employee seed comes from a deterministic generator with a fixed random seed. Pay is a base per job title, times a factor per country, times a tenure uplift, times noise of up to 10%, in each country's own currency. Each salary is dated at the employee's last work anniversary.

**Why.** Insights by country, title and tenure need real differences to find. A few constants in one class give them, and a unit test can pin the shape.

**What it costs.**

- **The figures are invented.** The factors and the units-per-USD rates are plausible, not market data, and the product never converts currency. Nothing in the insights should be read as real pay.
- **The same file comes out only for the same day.** Hire dates are relative to today, which the generator takes from the clock. Two runs on one day give identical bytes (checked across two separate processes). Runs on different days shift every date.
- **No salary history.** The CSV carries one entry per employee, so the seed has none. Slice 3's tests and the UI cover history.
- **About 8% of the seed are leavers**, each leaving on or after their hire date and before today. The seed with leavers loads 10,000 rows with none rejected in about 33 seconds, the same as before.
- **The generator's table can drift from the reference data.** An integration test checks titles, departments, countries and currency codes in both directions. It cannot check which currency a country uses, because the schema does not say.

**Related.** [design.md](design.md), decisions 16 to 19.

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

## Frontend configuration is fixed at build time

**Decision.** The API address and the request timeout live in `environment.ts` and are compiled into the app.

**Why.** It is the simplest typed option, and behind nginx the address is a relative `/api` that rarely changes. A runtime mechanism would be overbuilding for one HR user and one deployment.

**What it costs.** Changing a value means rebuilding the app, and a different environment needs its own build, so the artifact that was tested is not the one that ships.

**Upgrade path.** Load a small `config.json` before the app starts, written by the container from its environment variables, with built-in defaults if the file is missing. That gives one build for every environment. A central configuration service would also allow per-endpoint timeouts and audited changes without a redeploy, at the price of a startup dependency.

**Related.** `frontend/src/environments/environment.ts`.

## Job levels and grades are not modelled

**Decision.** Job titles are a standardized list with no levels or grades. This follows the working assumption in `requirements.md` (question 7, awaiting Incubyte's reply). Titles such as "Senior" and "Junior" are not used, because they would be levels in disguise.

**Why.** The brief names country, department, title and tenure as the slices, and whether ACME has a level structure is exactly what we asked. Inventing names and an order for levels would guess at their business, and it touches four slices: a reference table, an employee field, a directory filter and an insights dimension.

**What it costs.** "Same role" comparisons mix seniority. The median for a software engineer in one country covers someone hired last month and someone with fifteen years. Tenure as a slice partly compensates, but tenure is not level.

**Upgrade path.** Add a standardized `job_level` reference table and a nullable `job_level_id` on the employee. It is additive: existing rows stay valid, and the directory filter and an insights dimension follow. Nothing built before that point has to change.

**Related.** [requirements.md](requirements.md), the open question on titles and departments.

## The directory is a read model that reads other features' tables

**Decision.** `GET /api/employees` is served by a read-side `directory` package. It joins `employee`, `salary_entry` and the reference tables in one SQL statement on `JdbcTemplate`, and cuts the page in the database. Writes still go only through the owning service. The rule "features read each other only through public services" now has one stated exception: read models, which may join across tables and never write.

**Why.** The directory must filter by the currency of the employee's *current* salary and sort by salary, over 10,000 rows. Asking each feature for its rows cannot do that. A copy of the current salary on `employee` would go stale when a future-dated raise takes effect, with no write to refresh it.

**What it costs.**

- The directory knows the column names of three features' tables. A rename in `employee`, `salary_entry` or a reference table breaks it. The integration tests catch that, but the coupling is real, and the insights slice adds a second reader.
- **Offset paging.** A deep page costs more than a shallow one, and a row added between two requests can shift a page. At 10,000 rows and 20 per page neither is noticeable.
- **No index was added.** The plan for the default view is a sequential scan of the employee table with a top-N sort. That is fine at 10,000 rows. If it ever is not, the first move is an index on `lower(full_name)`, added only after measuring.
- **The seed has one salary entry per employee**, so the lateral join was measured with almost no history. It costs one index probe per employee, so it should not grow with history, but that is not shown by these numbers.
- A page reload logs the user out, so the state kept in the URL survives moving around the app, not a reload.

**Measured.** Through the real endpoint, including the login check, the two SQL statements (a count and a page), JSON and HTTP, on the owner's MacBook Pro with PostgreSQL 17 in Docker on the same machine, against 10,014 employees on 24 October 2026. Each query was run once to warm up, then nine times, timed with `curl`:

| Request | Min | Median | Max |
|---|---|---|---|
| Default view (active, by name, page 1) | 27.8 ms | 30.6 ms | 38.6 ms |
| Search `rao` (487 matches) | 21.3 ms | 22.3 ms | 24.0 ms |
| Search `asha.rao1` (few matches) | 23.7 ms | 25.2 ms | 26.3 ms |
| Currency INR, sort by salary descending | 24.9 ms | 27.0 ms | 28.1 ms |
| Country IN and department 1 | 7.1 ms | 7.6 ms | 10.3 ms |
| Deep page (page 499 of 500) | 33.0 ms | 34.5 ms | 34.9 ms |
| Page size 100 | 24.5 ms | 30.4 ms | 32.8 ms |
| All statuses, sort by hire date descending | 27.4 ms | 28.3 ms | 31.2 ms |

The SQL alone, from `EXPLAIN ANALYZE` (best of three), when choosing how to compute the current salary:

| Query | Lateral join | Window function |
|---|---|---|
| Active, currency INR, sort by salary, page 1 | 7.2 ms | 6.1 ms |
| Active, sort by name, page 1 | 9.2 ms | 8.3 ms |

A plain contains-search on name and email took about 5.5 ms whether it matched 487 rows, 2 or none. The two forms differ by about a millisecond, which is noise, so the lateral join was chosen for clarity and because it costs one index probe per employee. The default view is the slowest because it runs both statements over every row (the count and the page each join everything), and a narrow filter is the fastest. These are single sessions on one machine, not a benchmark.

**Alternatives rejected.**

| Option | Why not |
|---|---|
| Keep the boundary rule absolute, with `employee` paging its own rows and asking `salary` and `reference` for the rest | The directory could not sort or filter by salary or currency in the database, which breaks a requirement. |
| Copy the current salary onto `employee` | It goes stale when a future-dated raise takes effect, with no write to refresh it. |
| A window function for the current salary | About a millisecond faster here, but it numbers every salary entry of every employee on each query, so its cost grows with history. |
| A trigram index for search | At 10,000 rows it speeds up nothing a person can notice, and it needs an extension, a migration and an index to maintain. |

**Related.** [design.md](design.md), decisions 20 to 23 and "Directory filters and sort".

## Editing, terminating and deleting an employee

**Decision.** Terminating is setting a termination date in the profile edit, there is no separate action, and the same edit can clear it. A profile update and a delete both carry the `version` the person saw, read the employee under the row lock, and refuse a stale one with `409 VERSION_CONFLICT`. The salary timeline is revalidated against the new dates before anything is written.

**Why.** One path to the rules means one place for them to be wrong. Checking the version under the lock means a check cannot be raced, which a test shows by firing two updates with the same version ten times and getting one winner each time.

**What it costs.**

- **A stale edit discards the user's changes.** The banner offers Reload, which loads the latest values and drops what was typed. Merging would hide someone else's change.
- **Terminating an employee who has a future-dated raise is refused**, because no entry may be dated after the termination date. HR deletes that entry first, which slice 7 made possible, and the message says why.
- **Delete is irreversible** and leaves no record that it happened, as the hard-delete note above says.
- **The version bumps only on a profile change.** A salary change does not bump it, so an edit form opened before a raise still saves. That is safe because the profile does not depend on the salary amounts, and the timeline is checked again on save.

**Alternatives rejected.**

| Option | Why not |
|---|---|
| A separate "Terminate" endpoint | The same checks again, a second path to one rule. |
| A partial `PATCH` | "Absent" and "set to empty" would need different meanings for the nullable termination date. |
| Relying only on Hibernate's `@Version` exception | It fires at flush, after the rules have already run on stale data. |
| Merging a stale edit automatically | It would hide someone else's change. |

**Related.** [design.md](design.md), decisions 24 to 29.

## The change log records what and when, never who, and is not tamper-proof

**Decision.** Every add, edit and delete of a salary entry writes a row to `salary_entry_change` in the same transaction, with the old and the new date, amount and currency and the time. The page shows it newest first. Editing and deleting an entry run the same timeline rules as adding one, under the employee row lock.

**What it costs.**

- **There is no "who".** One shared login cannot attribute a change to a person. The log answers what changed and when, which is what the brief needs.
- **It is not tamper-proof.** Anyone with the shared login can add, edit and delete entries, and the log's rows are ordinary rows. It is a record for HR, not an audit trail for an auditor.
- **Deleting an employee deletes their change rows** (`ON DELETE CASCADE`), as the hard-delete note above says. Deleting a single entry keeps its rows.
- **Profile changes and terminations are not logged.** The design limits the log to salary entries.
- **The initial salary and the CSV load log nothing,** because an initial load is not a change. The log starts with the first edit.

**Related.** [design.md](design.md), decisions 30 to 33.

## Insights are one generic query, computed in the database

**Decision.** `GET /api/insights` and `/extremes` are a read model (`insights`) like the directory. One statement serves every dimension: each dimension owns fixed SQL for its group key, label and order, and every request value is a bound parameter. The statistics come from PostgreSQL (`count`, `sum`, `min`, `max`, `avg`, `percentile_cont`), never from Java. A group is a slice value and a currency, so currencies are never mixed. The current-salary join and the "active" rule are shared with the directory in a `readmodel` package.

**Why.** The questions HR asks are group-bys over 10,000 current salaries. One query that takes its dimension from a closed list is less code than six, and it cannot be given SQL by a client. Computing in the database keeps the rounding rules in one place.

**What it costs.**

- **The median is exact only up to 2^52 minor units (about 4.5 quadrillion).** `percentile_cont` works in double precision. Measured on PostgreSQL 17, the median of 9007199254740990 and 9007199254740991 comes back as 9007199254740990, where exact half-up rounding gives 9007199254740991. A test pins this. No realistic salary is near that size, so an exact median by row position was not built.
- **The total is a string in the API.** A sum of many amounts can pass 2^53, which a browser cannot hold exactly. The page formats it from its digits as text. The other measures stay within one amount's cap (2^53 - 1) and are numbers.
- **Today's figures only.** Insights use each person's current salary as of today. A salary as of a past date, and a trend over time, need a different query and are out of scope.
- **Tenure and the 240 title-by-country groups are many rows.** Tenure by band and currency is 36 rows on the seed, and job title in country is 240. There are no charts, so the table is long. A chart is a UI feature for later.
- **The ranking waits for the groups when no currency is chosen.** Its default currency is the first group's, so it is asked for after the groups answer. One more round trip on first paint, in exchange for never ranking in a stale currency.

**Measured.** Through the real endpoints behind nginx in `docker compose`, including the login check, JSON and HTTP, on the owner's MacBook Pro with PostgreSQL 17 in Docker, on the 10,000-employee seed (curl total time, 9 runs after a warm-up):

| Request | Min | Median | Max |
|---|---|---|---|
| Overall | 14.9 ms | 15.7 ms | 17.1 ms |
| By country (10 groups) | 14.5 ms | 16.0 ms | 24.3 ms |
| By department | 15.5 ms | 16.1 ms | 16.9 ms |
| By job title | 16.1 ms | 17.2 ms | 17.6 ms |
| By job title in country (240 groups) | 19.4 ms | 19.8 ms | 28.5 ms |
| By tenure | 23.8 ms | 24.3 ms | 33.0 ms |
| By country, all statuses | 15.7 ms | 16.5 ms | 18.1 ms |
| By job title, country IN, currency INR | 8.8 ms | 9.3 ms | 10.8 ms |
| Highest 10 in INR | 13.0 ms | 14.5 ms | 20.1 ms |
| Lowest 50 in USD | 12.8 ms | 13.6 ms | 15.0 ms |

Tenure is the slowest because its band is computed from two dates for every row. At 24 ms it needs no index, and none was added. The by-country group for Australia (436 people) was checked against a separate SQL query: headcount, total, min, median, mean and max were identical.

**Alternatives rejected.**

| Option | Why not |
|---|---|
| A query per view | Six queries to keep in step. The generic one is shorter and safer. |
| Statistics in Java | Moves rows to the app to compute what the database does, and the rounding would live in two places. |
| An exact median by row position | Exact above 2^52, but more SQL for a size that cannot occur. |
| Convert currencies to one | Needs rates with a documented date. Out of scope, per the requirements. |

**Related.** [design.md](design.md), decisions 34 to 40.

## `docker compose up` runs everything, and the seed runs only on an empty database

**Decision.** Four containers: PostgreSQL, the API, `web` (nginx, unprivileged, serves the Angular build and proxies `/api`, so the browser sees one origin and needs no CORS) and a one-shot `seed`. The seed script asks the API for the employee count with the app's own login and loads the 10,000 employees only when it is 0, so running `up` again changes nothing.

**Why.** A reviewer should run one command and see data. Without the check, a second `up` would try to load 10,000 duplicate emails and reject every row, which took 75.9 s when measured with the loader.

**What it costs.**

- **The first start is slower than later ones.** It builds two images (the API with Gradle, the web app with npm), and the seed runs after the API is healthy. I did not time the build. In one run the load itself took 9.3 s (10,000 loaded, 0 rejected). The earlier 33 s was measured with the loader on the host, so the two are not the same setup, and the difference was not investigated.
- **The login lockout counts per connection address.** Behind nginx every browser has the proxy's address, so five failed attempts lock everyone for a minute. The app does not trust forwarded-address headers, because a client can forge them. This is acceptable for one shared HR login and wrong for many users.
- **Images are tagged by major version, not pinned by digest,** and the build downloads dependencies, so it needs the network.
- **`/actuator` is not proxied.** The health check runs inside the API container.

