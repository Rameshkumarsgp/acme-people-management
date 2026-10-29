# ADR 0001: Database choice

Status: Accepted. Built and in use.

## Context

- About 10,000 employees, each with a salary history of one or more dated entries.
- The core product is analytics: headcount, total, min, median, mean and max, sliced by country, department, job title, job title in a country and tenure, **always per currency**.
- The brief requires a relational database.
- The salary rules need transactions and constraints. Every add, edit or delete of an entry is checked against the whole resulting timeline, and two people changing the same employee at once must not corrupt it.

## Options considered

| Option          | Median in SQL                                        | Prod/test parity                               | Notes                                          |
| --------------- | ---------------------------------------------------- | ---------------------------------------------- | ---------------------------------------------- |
| **PostgreSQL**  | `percentile_cont` built in                           | Same engine in tests via Testcontainers        | Needs Docker for tests and for the local run   |
| SQLite          | No built-in median; needs window-function workaround | Same engine, no Docker                         | Single writer, weaker Spring/Liquibase support |
| H2              | `PERCENTILE_CONT` supported                          | Different engine from prod if prod is Postgres | Fast tests, but proves little about prod SQL   |
| MySQL / MariaDB | MySQL 8 has none; MariaDB only as a window function  | Same engine                                    | Median needs workarounds                       |

## Decision

PostgreSQL 17, with Liquibase migrations and Testcontainers for the integration tests. Locally it runs in Docker through `docker compose up -d postgres`, with the same major version as the tests. A hosted database is not set up, because hosting is deferred (see [design.md](../design.md#deployment)).

## Consequences

- **Statistics are computed in the database** with `count`, `sum`, `min`, `max`, `avg` and `percentile_cont`, not in Java. One generic query serves every slice. 10,000 employees answer in 9 to 33 ms (see [tradeoffs.md](../../tradeoffs.md)).
- **`percentile_cont` works in double precision,** so the median is exact only up to 2^52 minor units and can be one unit low above that. A test pins this. It does not matter for realistic salaries.
- **Rules go in the database where a constraint can express them:** unique email ignoring case, one salary entry per employee and date, an amount range, a termination date not before the hire date, and the shape of each change-log row. Rules that span several rows live in plain Java classes and run before any write.
- **Concurrency uses a row lock.** Salary changes and employee updates take `SELECT ... FOR UPDATE` on the employee row, so two changes to one employee run one after the other. A test races two deletes of an employee's only two entries ten times, and exactly one entry is left each time.
- **Migrations are SQL files** named after their first changeset, each with a rollback. A migration that has run is never edited.
- **The current salary is a lateral join:** the latest entry on or before today, per employee. It is shared by the directory and the insights.
- **Integration tests need Docker** locally and in CI. Unit tests on the rules stay database-free and fast. Evaluators without Docker can still run those.
- **No hosted database yet.** If hosting is required, a managed free-tier PostgreSQL is the plan. It may cold-start or expire, which the README would have to say.
