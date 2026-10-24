# Slice 5: the employee directory

Status: Draft for review. Decisions were made one at a time with the project owner on 2026-10-24. Parent documents: [design.md](../../design.md) (the endpoint, filters, pagination and shared query definitions), [requirements.md](../../requirements.md) and the [slice 4 spec](2026-10-23-slice-4-csv-loader-and-seed-design.md). This spec adds only what slice 5 needs and does not change them, except decision 1, which amends design decision 12. Built with the [backend-slice skill](../../../.claude/skills/backend-slice/SKILL.md).

## Goal

HR finds employees among the 10,000 seeded ones: search, filter by country, department, job title, currency and status, sort, and page, then open one. Backend first (`feat(api)`), then the Angular directory (`feat(ui)`).

## Scope

In:

- `GET /api/employees`, as in [design.md](../../design.md#directory-filters-and-sort).
- A read-side `directory` package that joins across tables in SQL.
- An Angular directory at `/employees`, a link to it from the home page, and a "Back to directory" link on the detail page.
- Measured timings for the directory queries on the 10,000-row seed, in `docs/tradeoffs.md`.

Out (later slices): editing, terminating and deleting (slice 6), the insights (slice 8), and returning to the requested page after login (a separate small change to the guard and login). **No migration**: the existing indexes are enough, as the measurements below show.

## Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | The directory is a **read-side package** (`directory`) that reads `employee`, `salary_entry` and the reference tables with native SQL on `JdbcTemplate`. The boundary rule becomes: **writes** go only through the owning service, and **read models** (the directory, then the insights) may join across tables and never write. This amends design decision 12. | The directory must filter by the currency of the current salary and sort by salary over 10,000 rows, with the page cut in the database. Asking `salary` and `reference` row by row cannot do that, and a copy of the current salary on `employee` would go stale when a future-dated raise takes effect. |
| 2 | The current salary is a **lateral join**: the latest `salary_entry` on or before `asOf`, per employee. It is one SQL fragment that the insights can reuse. | Measured on 10,014 employees: 7.2 ms for filter and sort by salary, 9.2 ms for the default view, against 6.1 and 8.3 ms for a window function. The difference is noise at this size. The lateral form costs one index probe per employee, not a read of every entry, and reads like the rule it implements. |
| 3 | `q` is a case-insensitive **contains** on name or email (`ilike`), with `%`, `_` and `\` escaped in what the user typed. The text is trimmed, a blank `q` means no search, and a `q` over 100 characters is a `400`. | Measured at 5.5 ms for any search on 10,014 rows, so no index is needed. The escaping is for correctness: underscores are common in real emails, and a typed `%` would match every row. |
| 4 | The filters, sort, page and search live in the **URL query string**, for example `/employees?q=rao&countryCode=IN&sort=salary,desc&page=2`. The route is the single source of truth. A filter or sort change resets the page to 0, and typing in the search box is debounced. | Back and Forward move through views, and a link can be shared. Opening an employee and coming back keeps the view. |
| 5 | The directory is its own route, **`/employees`**. The home page stays the landing page, gains an "Employee directory" button, and becomes the insights dashboard in slice 8. | The product doc makes the insights the core and the directory the supporting layer. |
| 6 | The row's `status` and the `status` filter come from **one SQL fragment**: active when `termination_date` is null or on or after `asOf`. A test checks it against `EmployeeStatus` on the boundary day. | One definition, as the design requires, even though it is written in SQL and in Java. |
| 7 | The salary join is an **inner** join. An employee without a current salary does not appear. | The `NO_CURRENT_SALARY` rule guarantees every employee has one. A test asserts that the directory with `status=ALL` lists every employee, so a violation would fail loudly instead of hiding a person. |
| 8 | A request that breaks a directory rule is a `400` with `errors: [{ field, message }]`, raised as a new `InvalidDirectoryRequestException`. A malformed value (`page=abc`) is already a `400` from the base handler. | Exception type to status, as the handler's rule says. The rules themselves live in a pure class. |

## Real code facts that shaped this spec

- `EmployeeController` maps `/api/employees` with `POST` and `GET /{id}`. A new controller in `directory` can map `GET /api/employees` with no clash, because the existing one has no `GET` on the bare path.
- `ApiExceptionHandler` extends `ResponseEntityExceptionHandler`, so malformed parameters already give a problem+json `400`. It maps `UnknownReferenceException` and `CurrencyRequiredException` to a `400` with `errors`, which is the shape decision 8 needs.
- `JdbcTemplate` is available (Spring Data JPA brings `spring-jdbc`), and the integration tests already use it. The unique index on `salary_entry (employee_id, effective_date)` serves the lateral probe, and the existing indexes cover the three filters.
- `EmployeeStatus.on(today, terminationDate)` is the Java definition of active. The status SQL must agree with it.
- The frontend has `ReferenceApi.loadLists()` (countries, departments, job titles, currencies) for the filters, `formatMinor` for amounts, and `toFormErrors` for server errors. The login guard sends an unauthenticated user to `/login` with no return address, and login always goes to `/`.
- The seed has one salary entry per employee, so the measurements cannot show how the two lateral and window forms differ as histories deepen.

## API

`GET /api/employees?q=rao&countryCode=IN&departmentId=3&jobTitleId=12&currency=INR&status=ACTIVE&sort=salary,desc&page=0&size=20`

Every parameter is optional. Defaults: `status=ACTIVE`, `sort=fullName,asc`, `page=0`, `size=20`.

```
{ "content": [
    { "id": 10432, "fullName": "Asha Rao", "email": "asha.rao@acme.example",
      "department": "Engineering", "jobTitle": "Software Engineer", "country": "India",
      "status": "ACTIVE", "hireDate": "2024-04-01",
      "currentSalary": { "amountMinor": 125000000, "currency": "INR" } } ],
  "page": 0, "size": 20, "totalElements": 2979, "totalPages": 149 }
```

| Situation | Status and body |
|---|---|
| Valid | `200` with the envelope above |
| `size` over 100 or below 1, `page` below 0, `q` over 100 characters | `400`, `errors: [{ field, message }]` |
| Unknown sort field, direction other than `asc` or `desc`, unknown `status` | `400`, field `sort` or `status` |
| `sort=salary` without a `currency` filter | `400`, field `sort`: "Sorting by salary needs a currency, because salaries in different currencies cannot be compared." |
| A malformed value such as `page=abc` or `departmentId=x` | `400` from the base handler |
| An unknown `departmentId` or `countryCode` | `200` with an empty page (a filter that matches nothing) |

**Sort.** The fields are `fullName`, `department`, `jobTitle`, `country` (each by name, ignoring case), `hireDate` and `salary`. Every sort appends `id` ascending, so a page is stable and the pages do not overlap. The client never supplies SQL: each field maps to a fixed expression.

**Filters.** `currency` is the currency of the employee's **current** salary. `status` is `ACTIVE`, `FORMER` or `ALL`. `q` is as in decision 3.

## Backend

### Packages

| Package | Added in slice 5 |
|---|---|
| `directory/rules` | `DirectorySort` (the whitelist, the direction, the salary-needs-currency rule), `DirectoryStatus` (`ACTIVE`, `FORMER`, `ALL`), `SearchPattern` (trim, escape, blank means none) |
| `directory/errors` | `InvalidDirectoryRequestException` |
| `directory` | `DirectoryQuery` (the validated request), `DirectorySql` (builds the SQL and named parameters from a query), `DirectoryService` (runs the count and the page) |
| `directory/api` | `DirectoryController`, `DirectoryRow`, `PagedResponse` |
| `config` | handler for `InvalidDirectoryRequestException` (`400` with the field) |

`directory` imports no other feature's classes. It reads their tables, which is the read-side exception in decision 1, and it takes `asOf` from the injected `Clock`.

### The query

```sql
select e.id, e.full_name, e.email, d.name, jt.name, c.name, e.hire_date,
       cs.amount_minor, cs.currency_code,
       case when e.termination_date is null or e.termination_date >= :asOf
            then 'ACTIVE' else 'FORMER' end as status
from employee e
join department d on d.id = e.department_id
join job_title jt on jt.id = e.job_title_id
join country c on c.code = e.country_code
join lateral (select s.amount_minor, s.currency_code from salary_entry s
              where s.employee_id = e.id and s.effective_date <= :asOf
              order by s.effective_date desc limit 1) cs on true
where <filters, each only when given, all bound as parameters>
order by <fixed expression> <direction>, e.id
limit :size offset :offset
```

A second statement counts the same rows for `totalElements`, using the same `from` and `where`. `totalPages` is worked out from it and `size`. The `where` clauses are built from fixed text and bound parameters only.

## Frontend

- `core/employees` gains `DirectoryApi` (or extends `EmployeeApi`) with `listEmployees(query)`, which builds the parameters in one place and returns the envelope.
- A `features/directory` screen with a Material table (ID, name, department, job title, country, hired, salary with its currency), a search box, native selects for country, department, job title, currency and status, a sort header, and a paginator with page sizes 10, 20, 50 and 100.
- The route is the single source of truth. A filter, sort or page change navigates, and the screen reads the query from the route and loads. Changing a filter or the sort resets the page. The search box is debounced.
- The salary sort header is disabled until a currency is chosen, with a hint saying why. Choosing no currency while sorting by salary falls back to the default sort.
- A row's name links to `/employees/:id`. Loading, empty ("No employees match") and error states are shown, with a retry.
- The home page gets an "Employee directory" button. The detail page gets a "Back to directory" link to `/employees`. The browser's Back button, not that link, restores the earlier view.

## Testing, in build order

1. **Unit, no Spring:** `DirectorySort` (each field maps to its expression, a bad field, a bad direction, salary without a currency), `SearchPattern` (trim, a blank, escaping `%`, `_` and `\`), `DirectorySql` (each filter adds its clause and its parameter, none adds client text to the SQL, the order ends with the id, a hostile `q` stays a parameter). Each proven by breaking the code.
2. **Integration, against PostgreSQL:** every filter alone and combined, `q` on name, on email, and with a `%` or `_` in the term, status on the termination boundary day (checked against `EmployeeStatus`), a future-dated raise not yet current, a raise that took effect today, the currency filter following the current salary and not an old one, sort by every field including salary within one currency, equal sort values giving stable pages that do not overlap or skip, the page and total counts, and `status=ALL` listing every employee.
3. **Web slice:** each status in the table above, and the envelope.
4. **End to end through HTTP:** one request on seeded data.
5. **Frontend:** `DirectoryApi` (parameters), the directory component (reads the route, renders rows and formatted salaries, a filter navigates and resets the page, the debounced search, the salary sort disabled without a currency, empty, loading and error states, a row link), the home link and the detail link.
6. **Measured, not asserted:** the default view, filter plus salary sort, and search, through the real endpoint on the seed, recorded in `docs/tradeoffs.md`.

## Known constraints and risks

- **A read-side exception to the boundary rule.** It is deliberate and limited to read models. The directory reads other features' tables, so a column rename in `employee`, `salary_entry` or a reference table would break it. The integration tests catch that.
- **Offset paging.** A deep page costs more than a shallow one, and rows inserted between two requests can shift a page. At 10,000 rows and 20 per page this is not noticeable, and the design chose the page-number envelope.
- **No history depth in the seed**, so the lateral form was measured only with about one entry per employee. It scales with employees, not with history.
- **Reload logs the user out.** The URL state survives moving around the app, not a reload, until login can return to the requested page.
- **The business timezone setting is still not built.** `asOf` is the UTC date from the clock.
