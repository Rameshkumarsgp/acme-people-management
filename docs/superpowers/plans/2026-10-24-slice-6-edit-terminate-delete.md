# Slice 6: edit, terminate and delete an employee, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Also follow the project skill `.claude/skills/backend-slice/SKILL.md`.

**Goal:** HR edits a profile, records a termination, and deletes a mistaken record, without breaking the salary timeline. The loader and the seed carry leavers.

**Architecture:** The salary rules take an `EmploymentPeriod` and gain two checks. `EmployeeService` gains `update` and `delete`, both under the employee row lock, comparing the sent `version`. The salary service revalidates the stored timeline against the new period. A `PUT` and a `DELETE` on the employee controller, one new `409` handler. The loader and generator carry the termination date. An Angular edit form and a delete confirmation.

**Spec:** [2026-10-24-slice-6-edit-terminate-delete-design.md](../specs/2026-10-24-slice-6-edit-terminate-delete-design.md).

## How this plan is run

Tasks run one after another. Each is test first and ends with breaking the code to prove each new test, then the full gates. Mutations must compile and their target text must exist. A frontend mutation is run against its single targeted test, because one failing test can leave an open request that fails the next ones. Each task ends with its own commit. Comments only when the reason cannot be seen from the code.

## Global Constraints

- Rule order: `TERMINATION_BEFORE_HIRE_DATE`, `SALARY_BEFORE_HIRE_DATE`, `DUPLICATE_SALARY_DATE`, `NO_CURRENT_SALARY`, `SALARY_AFTER_TERMINATION`. An entry dated on the termination date is allowed.
- Update and delete take `PESSIMISTIC_WRITE`, compare `version`, and answer `409 VERSION_CONFLICT` on a mismatch. A salary add does not bump `version`.
- The unique index judges a duplicate email. A rule violation rolls the profile change back.
- Backend gates: `-Werror`, Error Prone, Checkstyle (120 columns, methods up to 25 lines, up to 5 parameters, complexity up to 10), SpotBugs on tests too. Frontend gates: Prettier, ESLint, `tsc`, Knip, Vitest.

## Review Focus

1. Two updates with the same version leave exactly one success and one conflict. [Task 3]
2. A raise added while the employee is terminated before it must fail. [Task 2]
3. A stale profile form after a concurrent salary change still succeeds, and is revalidated. [Task 3]
4. Moving the hire date after an existing entry is refused and changes nothing. [Task 3]
5. Every seeded leaver satisfies the rules. [Task 5]

## Tasks

1. **The rules (pure).** `EmploymentPeriod`, the two codes, the two checks, `validate(period, timeline, today)`. Update `SalaryTimelineValidatorTest`.
2. **The salary service.** `recordFirstEntry`, `addEntry` take the period, new `revalidate`. `EmployeeSalaryService.add` passes the period. Integration tests.
3. **Update and delete in `EmployeeService`.** `Employee.applyProfile`, `EmployeeProfile` and `CreateEmployeeRequest` carry the termination date, `UpdateEmployeeRequest`, `StaleVersionException`. Integration tests including the races.
4. **The endpoints.** `PUT`, `DELETE`, the `409` handler. Web slice and end to end tests.
5. **The loader and the seed.** `EmployeeRow` and the loader carry the termination date, the generator makes about 8% leavers. Unit and integration tests.
6. **The Angular edit form and delete.** API client, `features/employee-edit`, detail buttons, the delete confirmation, routes. Specs.
7. **Docs, the real run and the final checks.** `docs/design.md`, `docs/tradeoffs.md`, `docs/requirements.md`, `docs/ai-usage.md`. A real run through the API and the browser on a record I create and delete myself.
