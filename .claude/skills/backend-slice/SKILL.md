---
name: backend-slice
description: The order of work for a backend slice in this repo, meaning a new or changed endpoint with a database change. Use when adding a table, a migration, a rule, a service or an endpoint under backend/. Covers the migration, the pure rules, the feature layout, the error mapping, the three test layers, the mutation proof and the gates.
---

# Backend slice recipe

How a backend slice is built here, in order. Coding rules (Java style, naming, immutability, test tagging) are in `backend/CLAUDE.md`, and the product decisions are in the root `CLAUDE.md` and `docs/design.md`. This file is the procedure and the mistakes already paid for in slice 2.

## 0. Before any code

- Put the design decisions to the owner **one at a time, each with a recommendation**. Wait for the answer. Write the spec, then a plan, and get both approved before building.
- **Read the real code before writing the spec.** Slice 2's spec said `Employee` would hold `@ManyToOne` links to reference entities, but those are package-private. Check visibility, signatures and existing conventions first.
- Build one task at a time. After each task, stage the exact files and suggest a `type(scope): subject` message. **The owner runs every commit.** Never `git add .`.

## 1. Migration first

- A new file in `backend/src/main/resources/db/changelog/changes/`, named after the id of its first changeset. Check the highest id in use and continue from it. **Never edit a migration that has been committed.**
- Every changeset has a `--rollback`.
- Put in the database what a constraint can express: keys, foreign keys, not null, unique (use `lower(...)` for case-insensitive), check ranges. Rules that span several rows go in a pure class instead (step 2).
- Entity column types must match the migration: `ddl-auto=validate` fails startup otherwise.
- Test first with `@IntegrationTest`: one test per constraint, including the boundary (the largest allowed value passes, one more fails). Insert rows with `support/TestEmployees`-style helpers, never through the code under test, and delete them in `@AfterEach`.

## 2. Rules as plain classes

- A rule that needs no database lives in `<feature>/rules/` with no Spring. It throws one unchecked exception that carries a stable code (an enum). Add one rule and one test at a time, test first.
- Time comes from an injected `Clock` at the edge, and `today` is passed down as a parameter. Rules never call `now()`.

## 3. Layout of a feature package

```
<feature>/rules/    pure rules and their exception
<feature>/api/      request and response records, and the controller
<feature>/errors/   exceptions that other packages (the handler) need
<feature>/          entity, repository, service, internal records
```

- Entity, repository and service stay together and package-private. Make a class public only when another folder must call it, and say so in the summary.
- Features read each other through a **public service or lookup**, never an entity or repository, and store the other feature's rows by **plain id**. No cycles. `config/` may import from features, never the reverse.
- The service owns the transaction. One write path per table (salary rows are written only by `SalaryService`).
- A duplicate is judged by the **unique index** alone. Catch the violation and check the constraint name, rather than pre-checking, so two requests at once cannot both succeed.

## 4. The API

- Request records carry Bean Validation. Controllers stay thin. Money is integer minor units plus a currency code, never a float. Jackson is set to reject a fraction where a whole number is expected (`accept-float-as-int=false`).
- One `@RestControllerAdvice` in `config/` maps **exception type to status and nothing else**, as `application/problem+json`. Statuses: 400 invalid, 404 missing, 409 duplicate or stale version, 422 broken business rule with its `code`. Add a handler only for a new exception type.
- A `Location` header is relative, so it works behind the nginx proxy.

## 5. Tests, three layers

| Layer | Tooling | Covers |
|---|---|---|
| Unit | JUnit, no Spring, no Docker | rules, status and other pure logic |
| Web slice | `@WebMvcTest`, `@MockitoBean` service, `AuthTestSupport` | validation, status codes, error shapes |
| Integration | `@IntegrationTest` (Testcontainers, fixed clock 2026-10-19) | migration, service behavior, rollback, one end-to-end HTTP test |

Do not unit-test a service with a mocked repository. Test it against the real database. Name tests by behavior ("rejectsAnEmailThatDiffersOnlyInCase").

## 6. Prove every test by breaking the code

After GREEN, change the code the test guards, run, and confirm **that test fails for the intended reason**, then restore and confirm the file is identical. Pitfalls that have already cost time:

- The mutation must **compile**. This repo builds with `-Werror` and Error Prone, so deleting a call can leave an unused field, variable or private method, which Error Prone rejects before any test runs. That proves nothing. Mutate a value or an operator instead.
- Check the mutation actually changed the file (assert the target text exists). A `sed` that matches nothing "passes" silently.
- Quote test filters (`--tests '*Name'`). An unquoted `*` printed nothing.
- Read the failing test names. If **every** test fails, the setup is broken, not the mutation. Fix the test first.
- Async Angular specs need `vi.waitFor`, not `whenStable`.

## 7. Gates, every task

Run the **full** check, not just the task you edited. SpotBugs and Checkstyle also run on test code, and they caught two issues late in slice 2.

```bash
make test-backend      # Checkstyle, SpotBugs, Error Prone, fast tests (no Docker)
make test-integration  # database tests (Docker must be running)
```

Limits: 120 columns, methods up to 25 non-empty lines, up to 5 parameters, no single-letter names. No `\n` inside a text block passed to `.formatted()` (SpotBugs).

## 8. Before calling it done

- Run it for real, not only the tests: start Postgres and the API, then drive it with curl or the browser. A shell tab needs `.env` loaded, and the API port may not be 8080.
- Add what the slice taught you to `docs/ai-usage.md`, and any measured number to `docs/tradeoffs.md`. Record a design decision in the decisions table in `docs/design.md`, and a limit in `docs/requirements.md`.
- Do a final pass for behavior the tests could miss, by probing with odd input (fractions, strings, huge numbers, other casing). Slice 2's review found a money-truncation bug this way.
