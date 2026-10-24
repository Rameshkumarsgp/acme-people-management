# Slice 5: the employee directory, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development. Also follow the project skill `.claude/skills/backend-slice/SKILL.md`.

**Goal:** HR searches, filters, sorts and pages through the 10,000 employees at `/employees`, and opens one.

**Architecture:** A read-side `directory` package. Its `rules/` folder holds pure classes (the validated query, the sort whitelist, the status, the search pattern). `DirectorySql` builds one page statement and one count statement from a validated query. `DirectoryService` runs them on `NamedParameterJdbcTemplate`. A `GET /api/employees` controller returns a page envelope. The Angular directory reads its whole state from the URL.

**Tech Stack:** Java 25, Spring Boot 4.1, JdbcTemplate, PostgreSQL 17 (Testcontainers), JUnit 5, AssertJ, MockMvc. Angular 22 with Angular Material (table, paginator, sort), Vitest in jsdom.

**Spec:** [2026-10-24-slice-5-directory-design.md](../specs/2026-10-24-slice-5-directory-design.md).

## How this plan is run

- Tasks run one after another without stopping. Each is test first and ends with breaking the code to prove each new test, then the full gates. A mutation must compile, and its target text must exist before it is trusted. Quote test filters.
- **The owner makes every commit.** Nothing is staged in the shared index while working. Each task ends with an exact `git add … && git commit` command.
- Comments only when the reason cannot be seen from the code.
- The Docker daemon must be running for integration tests.

## Global Constraints

- The client never supplies SQL. Every sort field maps to a fixed expression, and every filter value is a bound parameter. A hostile search text stays a parameter.
- `q` is a case-insensitive contains on name or email. `%`, `_` and `\` in it are escaped with an explicit `escape '\'`. It is trimmed, blank means none, and over 100 characters is a `400`.
- `sort=salary` needs a `currency` filter, else `400` on field `sort`. Every sort appends `e.id` ascending.
- Defaults: `status=ACTIVE`, `sort=fullName,asc`, `page=0`, `size=20`. `size` is 1 to 100, `page` is 0 or more.
- The current salary is the lateral fragment in the spec, inner-joined. `asOf` is the clock's UTC date. Active means `termination_date` is null or on or after `asOf`, written once in SQL.
- `directory` imports no other feature's classes. It reads their tables. It never writes.
- Backend gates: `-Werror`, Error Prone, Checkstyle (120 columns, methods up to 25 non-empty lines, up to 5 parameters, complexity up to 10), SpotBugs on tests too.
- Frontend gates: Prettier, ESLint, `tsc`, Knip, Vitest. Standalone components, signals, `inject()`. Async specs poll with `vi.waitFor`. Features do not import each other.

## Review Focus

1. A `%` or `_` typed in the search box matches itself, not everything. [Task 1, Task 3]
2. A future-dated raise is not the current salary, and a raise dated today is. The currency filter follows the current entry. [Task 3]
3. Equal sort values give pages that neither overlap nor skip a row. [Task 3]
4. An employee terminated on `asOf` is active, and the day after is former, matching `EmployeeStatus`. [Task 3]
5. A filter or sort change resets the page, and the salary sort is unavailable without a currency. [Task 5]

## Tasks

### Task 1: the rules (pure)
Files: `directory/rules/{DirectoryParams,DirectoryQuery,DirectorySort,DirectoryStatus,SearchPattern,package-info}.java`, `directory/errors/{InvalidDirectoryRequestException,package-info}.java`, `directory/package-info.java`. Tests: `DirectorySortTest`, `DirectoryStatusTest`, `SearchPatternTest`, `DirectoryQueryTest`.
Behaviors: each sort field maps to its expression; a bad field or direction, and salary without a currency, are rejected with field `sort`; the status parses with `ACTIVE` as the default; the search text is trimmed, escaped and wrapped, blank gives none, over 100 characters is rejected; `DirectoryQuery.from` applies the defaults and the page and size limits.
Mutations: swap two sort expressions, drop the salary rule, remove an escape, change a limit by one, change a default.

### Task 2: the SQL builder (pure)
Files: `directory/DirectorySql.java`. Test: `DirectorySqlTest`.
Behaviors: no filter gives no `where`; each filter adds its clause and its named parameter; the search adds one clause covering name and email with its escape; the order ends with `e.id`; the page statement has `limit` and `offset` parameters and the count has neither nor an `order by`; no client text appears in either statement.
Mutations: drop a filter, drop the id tie-breaker, put the search text into the SQL.

### Task 3: the service against PostgreSQL
Files: `directory/DirectoryService.java`, `directory/api/{DirectoryRow,PagedResponse}.java`. Test: `DirectoryServiceIntegrationTest` (rows inserted by SQL, deleted after each test).
Behaviors: see Review Focus 1 to 4, plus each filter alone and combined, `q` on name and on email, sort by every field and by salary within a currency, the page and total counts, and `status=ALL` listing every employee.
Mutations: `<=` to `<` on the salary date, `>=` to `>` on the termination date, drop `e.id`, drop the escape clause, break one filter, change offset arithmetic.

### Task 4: the endpoint
Files: `directory/api/DirectoryController.java`, `config/ApiExceptionHandler.java` (one handler). Tests: `DirectoryEndpointTest` (web slice), `DirectoryEndpointIntegrationTest` (one request over HTTP).
Behaviors: every row of the spec's status table, and the envelope shape.

### Task 5: the Angular directory
Files: `core/employees/employee-api.ts` (+ `listEmployees`, `DirectoryQuery`, `DirectoryPage`), `features/directory/directory.{ts,html,scss,spec.ts}`, `app.routes.ts`.
Behaviors: see Review Focus 5, plus rows with formatted salaries, search debounce, the row link, loading, empty and error states, the paginator.
Mutations: do not reset the page on a filter change, enable the salary sort without a currency, drop the debounce, break the parameter mapping.

### Task 6: the links
Files: `features/home/home.html`, `features/employee-detail/employee-detail.html`, their specs.

### Task 7: docs, measurements and the final checks
`docs/design.md` (decisions 20 to 23, amending 12), `docs/tradeoffs.md` (measured timings through the real endpoint), `docs/requirements.md` if a limit changed, `docs/ai-usage.md`. Then `make check`, a real run on the seed through my own API on another port, and a real-browser run.
