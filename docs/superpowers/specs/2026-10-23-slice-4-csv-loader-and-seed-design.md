# Slice 4: the CSV loader and the 10,000-employee seed

Status: Draft for review. Decisions were made one at a time with the project owner on 2026-10-23. Parent documents: [design.md](../../design.md#data-loading) (the CSV format and the loading rules), [requirements.md](../../requirements.md) and the [slice 3 spec](2026-10-22-slice-3-salary-history-design.md). This spec adds only what slice 4 needs and does not change them. Built with the [backend-slice skill](../../../.claude/skills/backend-slice/SKILL.md).

## Goal

HR can load an employee CSV from the command line, and the project can produce a deterministic 10,000-employee seed with realistic pay. The same loader serves both: migrating ACME's spreadsheet, and loading the seed. Backend only, with no screen.

## Scope

In:

- A loader that reads a CSV in the format in [design.md](../../design.md#data-loading) and creates employees through the existing `EmployeeService.create`.
- A seed generator that writes a CSV in the loader's format, from a fixed random seed.
- Two command-line modes of the existing jar, and a `make seed` target that runs both.
- Per-row rejection with a row number, a field and a reason, a summary, and a non-zero exit status when anything is rejected.
- A measured load time for 10,000 rows, recorded in `docs/tradeoffs.md`.

Out: an upload screen, `.xlsx` parsing, the compose service that seeds on first start (a later slice), leavers and termination dates (slice 6, decision 6), currency conversion, and an audit of loaded rows (slice 7 builds the change log).

## Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | The loader is a mode of the existing Spring Boot jar: `--acme.load=<file>`. It starts the app, runs the import through the same services, prints a summary and exits. | Imported rows obey exactly the same rules as typed ones. There is one jar to build, and no second write path to salary rows. The cost is a few seconds of startup. |
| 2 | The seed generator is Java in the same jar: `--acme.seed-csv=<file>`. It uses `java.util.Random` with a fixed seed. | One language, no second runtime in the compose image, and determinism gets a fast unit test. The generator keeps its own table of titles, departments and countries. An integration test asserts that every name in it exists in the reference data, so the table cannot drift from the migrations without a failing test. |
| 3 | Pay is a multiplicative model: a base per job title, times a factor per country, times a tenure uplift (about 4% a year, capped at 10 years), times seeded noise of about ±10%. The result is in the country's own currency and in minor units. | Insights by country, title and tenure find real differences, and the model has few constants. The constants are stated in one class as synthetic seed data. They are not currency conversion, and the product never converts. |
| 4 | Hire dates are spread over the last 10 years. Each employee's single salary entry is dated at the **most recent work anniversary** on or before today (the hire date, for someone hired under a year ago). | The tenure uplift was earned through past raises, so the current pay took effect at the last one. The "current salary" and its date agree. The CSV carries one entry per employee, so the seed has no multi-step history. Slice 3's tests and the UI cover history. |
| 5 | Two commands and a `make seed` target. The generator writes the CSV, the loader loads any CSV. The later compose slice adds a one-shot `seed` service that runs the same two commands once the database is up and empty. | Slice 4 delivers working commands, and builds nothing for a container that does not exist yet. The "only when empty" guard belongs to that service. |
| 6 | Leavers are deferred to slice 6. The CSV column `terminationDate` stays in the format, and the loader rejects a non-empty value with the reason "termination dates are not supported yet". The generator writes none. | The create path has no termination date, and the two rules that govern it (`SALARY_AFTER_TERMINATION`, `TERMINATION_BEFORE_HIRE_DATE`) arrive with their edit endpoint in slice 6. Slice 4 adds no rule that nothing in the UI can reach. Slice 6 removes the rejection and turns on about 8% leavers in the generator, a one-line change. |
| 7 | A new dependency, Apache Commons CSV, parses the file. The version is pinned and checked when the plan is written. | Quoted commas and quotes in names are easy to get wrong by hand, and the library is small and widely used. |
| 8 | An unexpected failure aborts the run. Only a **row** problem counts as a rejected row. | A broken database is not a bad row. Counting it as one would hide a failing run behind a long list of rejections. |

## Real code facts that shaped this spec

- `EmployeeService.create(CreateEmployeeRequest)` is public and `@Transactional`. A caller with no surrounding transaction gets one transaction per row, as the design requires. It checks that the references exist and the salary rules, and it throws `UnknownReferenceException`, `EmailAlreadyExistsException` or a `SalaryRuleViolation`.
- `CreateEmployeeRequest` carries Bean Validation, which the controller normally runs. The loader bypasses the controller, so it runs `jakarta.validation.Validator` itself on each request.
- `ReferenceLookup` finds only by id or code. The CSV names departments and job titles, so two lookups by name are added, ignoring case and surrounding spaces. `reference` keeps its entities and repositories private.
- A leaver cannot be created: `CreateEmployeeRequest` has no termination date (decision 6).
- The loader lives in a new feature package, `dataload`. It may import the public `employee`, `employee.api` and `reference` classes and nothing private. Nothing imports `dataload`, and `config` does not need to.

## Loader

### Input

- UTF-8, with a leading byte-order mark ignored (Excel adds one). Columns are found by their header name, so their order does not matter. An unknown or missing required header fails the whole file, before any row is read.
- Header: `fullName, email, hireDate, department, jobTitle, countryCode, currency, salary, salaryEffectiveDate, terminationDate`.
- Dates are ISO (`2024-04-01`). Excel's local formats are not accepted, and the reason says so.
- `salary` is in major units with a dot as the decimal separator and no thousands separators (`1250000.00`). It is converted with the currency's minor-unit digits. More decimals than the currency allows is rejected, so `1250000.5` is valid for INR and `1250000.5` is rejected for JPY.
- `salaryEffectiveDate` is optional and defaults to the hire date. `terminationDate` must be empty (decision 6).
- Names of departments and job titles match ignoring case and surrounding spaces.

### Per row

1. Map the text to a `CreateEmployeeRequest`. A text problem (a bad date, a bad amount, an unknown department name) rejects the row with its field.
2. Run Bean Validation on the request.
3. Call `EmployeeService.create`. A `SalaryRuleViolation`, `EmailAlreadyExistsException` or `UnknownReferenceException` rejects the row. Anything else aborts the run (decision 8).

A rejected row does not stop the others, and each loaded row stays committed.

### Output

- A line per rejected row: the row number (the line number in the file, counting the header as line 1), the field, and the reason.
- A summary of the counts loaded and rejected.
- Exit status 0 only when nothing was rejected. Re-running a loaded file reports every row as `EMAIL_ALREADY_EXISTS` and exits non-zero (as in the design).

### Running it

```
./gradlew bootRun --args="--spring.main.web-application-type=none --acme.load=seed.csv"
```

The app needs the datasource variables, as for any run. The web server is switched off so the process can exit.

## Seed generator

- Input: a fixed random seed, a count (10,000 for the project seed), and today's date from the injected clock. Output: a CSV in the loader's format. The same input gives the same bytes.
- Names are drawn from two fixed lists. The email is unique by construction (the employee's number is part of it) and ends in `@acme.example`.
- The country mix is a set of constants, about India 30%, United States 22%, United Kingdom 10%, Germany 8%, Canada 6%, France 6%, Singapore 6%, Australia 5%, Japan 4%, United Arab Emirates 3%. Each country pays in one home currency.
- A title belongs to one department in the generator's table (for example Software Engineer to Engineering). The base pay and country factors are constants in one class, which the plan lists with their values.
- Salary entries are never dated after today, never before the hire date, and always inside the money range.

## Commands

| Command | What it does |
|---|---|
| `make seed` | Generates `seed.csv` (git-ignored) and loads it into the database from `.env`. Run it on an empty database. |
| `--acme.seed-csv=<file>` | Only generates the CSV. |
| `--acme.load=<file>` | Only loads a CSV. |

## Testing, in build order

1. **Unit, no Spring:** the amount parser (two decimals, no decimals for JPY, too many decimals, zero or negative, a thousands separator), the row mapper (header order, optional columns, trimming, case-insensitive names, bad dates), the pay model (the same seed gives the same figures, a Manager earns more than an Analyst in the same country, pay rises with tenure up to the cap, an amount is in minor units), and the generator (deterministic, unique emails, every date in range, mix of countries). Each is proven by breaking the code.
2. **Integration:** loads a valid file; a rejected row is reported with its number, field and reason while the others load; a duplicate email; an unknown department; a non-empty `terminationDate`; a missing required header fails before any row loads; re-running reports `EMAIL_ALREADY_EXISTS` for every row; every name in the generator's table exists in the reference data.
3. **Wiring:** the exit status is non-zero when a row was rejected, and zero when none was.
4. **End to end:** generate a small file and load it, then read an employee through the API.
5. **Measured, not asserted:** the full 10,000-row load time, recorded in `docs/tradeoffs.md`.

## Known constraints and risks

- **Throughput is unmeasured.** Each row is its own transaction through the service. If the 10,000-row load takes more than a minute, writes are batched, and the rule checks stay per row. The design already says to measure first.
- **No leavers until slice 6.** Anyone loading a sheet with termination dates gets those rows rejected with a clear reason until then.
- **Excel quirks stay out.** Local date formats, thousands separators and `.xlsx` are rejected or unsupported, and the sheet must be saved as CSV with ISO dates.
- **The seed is synthetic.** The pay constants are plausible, not real market data. The tradeoffs document says so.
- **The generator table can drift from the reference data.** The integration test in step 2 catches it.
- **Seeding twice.** The second run rejects every row. The compose service guards this later.
