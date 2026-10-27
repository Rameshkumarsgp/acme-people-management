# Slice 8: the insights

Status: Draft. The owner asked for the remaining slices to be completed without a decision-by-decision dialogue, so each decision states its reason. Parent documents: [design.md](../../design.md) (the insights section and the numeric contract), [requirements.md](../../requirements.md) and the [slice 5 spec](2026-10-24-slice-5-directory-design.md), whose read-side rule and current-salary SQL this slice reuses.

## Goal

The HR Manager answers "how does ACME pay people?": headcount, total, minimum, median, mean and maximum salary, by country, department, job title, job title in a country, and tenure band, always per currency, plus the highest and lowest paid people.

## Scope

In: `GET /api/insights`, `GET /api/insights/extremes`, a `readmodel` package for the SQL both read models share, and an Angular dashboard on the home page. **No migration.** Out: historical snapshots (the design excludes them), currency conversion, and charts.

## Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | `insights` is a read model like the directory: native SQL on `JdbcTemplate`, no writes, no other feature's classes. The current-salary join, the "active" condition and the status filter move into a shared `readmodel` package, and `directory` uses them too. | Each decision lives in one place. A third copy of "active" is how two screens come to disagree. |
| 2 | One generic query serves all five dimensions. Each dimension maps to **fixed SQL expressions** for its key, label and order, and every filter value is a bound parameter. | The design says the client never supplies SQL. One mechanism is less code than five queries. |
| 3 | The statistics are computed in the database. `total` is `sum` as an exact `numeric`, `min` and `max` are exact, `mean` is `round(avg(amount::numeric))`, and `median` is `round(percentile_cont(0.5)…::numeric)`, so both round half up to whole minor units. | As the design's numeric contract says. |
| 4 | **`total` is a decimal string.** `min`, `max`, `mean`, `median` and `headcount` are numbers. | A sum of many amounts can exceed 2^53 and would lose digits in the browser. The other figures are bounded by the per-entry cap. |
| 5 | **`percentile_cont` works in double precision,** so the median is exact up to 2^52 (about 4.5 quadrillion minor units) and can be one unit low above that. A test pins the behaviour at the cap, and `docs/tradeoffs.md` records it. | Measured on PostgreSQL 17: the median of 9007199254740990 and 9007199254740991 comes back as 9007199254740990, where exact half-up rounding gives …991. No realistic salary is near that size. An exact median by row position was rejected as a departure from the design for a case that cannot occur. |
| 6 | Tenure runs from the hire date to the earlier of today and the termination date, in **completed years** (`extract(year from age(…))`). The bands are under 1 year, 1 to under 3, 3 to under 5, 5 to under 10, and 10 or more. | As the design says. A hire date exactly one year ago is in the 1 to 3 band, one day later is under 1. |
| 7 | Filters are `countryCode`, `departmentId`, `jobTitleId`, `currency` and `status` (default `ACTIVE`), with the directory's meaning. The `currency` filter means the currency of the **current** salary. | The same questions, answered the same way. |
| 8 | A group is a slice value **and a currency**. Headcount counts the people in that group only, and nothing from different currencies is ever added. | The product rule. |
| 9 | Extremes need a `currency`, take `order` (`HIGHEST` or `LOWEST`) and a `limit` (1 to 50, default 10), and sort by amount then employee id, so ties are stable. At most `limit` people are returned, even when that splits a tie. | As the design says. |
| 10 | The dashboard is the **home page**. Its state (dimension, filters, extremes currency) is in the URL, like the directory's, so opening a person from the lists and going Back returns to the same view. | Decision 5 of slice 5 planned this. |
| 11 | The browser parses and formats `total` from its **digits as text**, never as a number. | A total above the safe integer must round-trip unchanged through the API and the page. |

## Real code facts that shaped this spec

- `DirectorySql` holds the active condition and the lateral current-salary join as private text, so they have to move before the insights can share them.
- `DirectoryStatus` and `InvalidDirectoryRequestException` are directory classes, but the insights need both, so they move to `readmodel` under neutral names.
- The `insights` package exists and is empty. The home page is a welcome page that already says the insights are to come.
- `formatMinor` formats a number from its digits as text, so it can be given a string for a total beyond 2^53.
- The seeded data has about 10,000 employees in 10 countries, 24 job titles and 10 departments, with 781 leavers after slice 6.

## API

`GET /api/insights?dimension=COUNTRY&department=…` (every filter is optional):

```
{ "dimension": "COUNTRY",
  "groups": [ { "key": "IN", "label": "India", "currency": "INR", "headcount": 2300,
                "total": "6900000000000", "min": 63000000, "median": 255000000,
                "mean": 300000000, "max": 540800000 } ] }
```

Groups are ordered by label then currency, except tenure, which is ordered by band. `GET /api/insights/extremes?order=HIGHEST&currency=INR&limit=10`:

```
{ "order": "HIGHEST", "currency": "INR",
  "employees": [ { "id": 7433, "fullName": "Arjun Rao", "department": "Engineering",
                   "jobTitle": "Engineering Manager", "country": "India",
                   "amountMinor": 540800000, "effectiveDate": "2025-04-01" } ] }
```

| Situation | Status |
|---|---|
| Valid | `200` |
| Unknown dimension, order or status, `limit` outside 1 to 50 | `400`, `errors: [{ field, message }]` |
| Extremes without a `currency` | `400`, field `currency` |
| A filter that matches nothing | `200` with an empty list |

## Backend

| Package | Added in slice 8 |
|---|---|
| `readmodel` | `CurrentSalarySql` (the join, the active condition), `StatusFilter` (moved from the directory), `InvalidQueryException` (moved) |
| `insights/rules` | `Dimension`, `InsightsQuery`, `ExtremesQuery`, `ExtremesOrder` |
| `insights` | `InsightsSql`, `InsightsService`, `ExtremesSql` |
| `insights/api` | `InsightsController`, `InsightGroup`, `InsightsResponse`, `ExtremesResponse`, `ExtremeEmployee` |

## Testing, in build order

1. **Unit, no Spring:** the dimension mapping, the query rules, and the SQL builders (every filter bound, no client text in the SQL), each proven by breaking the code.
2. **Integration:** an empty group, a single employee, even and odd group sizes with the exact rounding (middle values 100 and 101 give 101), several currencies in one slice, a future-dated entry that is not yet current, a former employee under each status, an employee on exactly a tenure boundary and one day either side, and a tie in the extremes with a limit that splits it. A total above 2^53 comes back as an exact string. The behaviour of the median at the cap is pinned.
3. **Web slice and one end-to-end path over HTTP.**
4. **Frontend:** the API client, the string-safe money formatting, the dashboard (reads the URL, renders groups per currency, a filter navigates, extremes, empty and error states), and the home page.
5. **Measured and real:** timings of each dimension on the 10,000-employee seed, recorded in `docs/tradeoffs.md`, and a real run in the browser.

## Constraints and risks

- **One generic query is as fast as its plan.** A grouped aggregate over 10,000 joined rows is measured, and an index is added only if a number calls for it.
- **The median is off by at most one minor unit above 2^52,** as decision 5 says.
- **Insights cover the current salary only.** A salary as of a past date is out of scope.
