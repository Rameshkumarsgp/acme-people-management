# ADR 0001: Database choice [WIP]

Status: Proposed (awaiting approval)

## Context

- ~10,000 employees, with salary history (several rows per employee).
- The core product is analytics: headcount, total, min, median, mean and max, sliced by country, department, title, title x country and tenure, **always per currency**.
- The brief requires a relational database.
- Salary history rules (append-only, later effective date) need transactions and constraints.

## Options considered

| Option | Median in SQL | Prod/test parity | Notes |
|---|---|---|---|
| **PostgreSQL** | `percentile_cont` built in | Same engine in tests via Testcontainers | Needs Docker for tests and a hosted instance |
| SQLite | No built-in median; needs window-function workaround | Same engine, no Docker | Single writer, weaker Spring/Liquibase support |
| H2 | `PERCENTILE_CONT` supported | Different engine from prod if prod is Postgres | Fast tests, but proves little about prod SQL |
| MySQL / MariaDB | MySQL 8 has none; MariaDB only as a window function | Same engine | Median needs workarounds |

## Decision (proposed)

PostgreSQL, with Liquibase migrations, Testcontainers for integration tests, and a managed free-tier Postgres for deployment (provider to be chosen in `docs/design.md`).

## Consequences

- Median and other stats are computed in the database with `percentile_cont`, not in Java.
- Integration tests need Docker locally and in CI. Unit tests on salary rules stay DB-free and fast.
- The database lives outside the app host, so redeploys do not wipe data. A free tier may cold-start or expire. Note this in the README.
- If Docker is unavailable to evaluators, they can still run unit tests. Integration tests are the cost of real-engine parity.
