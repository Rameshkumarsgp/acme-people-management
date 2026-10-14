# CLAUDE.md: ACME People Management

Context for Claude Code. Read this, `docs/requirements.md` and `docs/design.md` fully before doing anything.

## What this is

A take-home engineering assessment for Incubyte (a hiring process). Evaluators judge **how I think and how I use AI**, not how much I build. They read the git log before the code. Good engineering judgment beats a large feature set.

**Goal:** employee salary management software for ACME, an org with 10,000 employees across multiple countries, currently managed in Excel. The HR Manager must manage salary data in a web app and **answer questions about how the org pays people**.

**Evaluation signals:** clear structured thinking, pragmatic architecture, maintainable code, meaningful tests (fast, deterministic, easy to understand), intentional AI use, product thinking.

## Hard requirements from the brief

- End-to-end working software: backend and UI.
- Backend: Java (Spring Boot) with a relational database.
- UI: modern Angular (the brief says "AngularJS for Java", which is unconfirmed), with a component library.
- Seed script producing 10,000 employees.
- Deployed, working software, plus a short video demo (I will record the video). Current plan: run locally and record from the local run. The brief lists deployment, so hosting stays an open risk to confirm with Incubyte.
- Unit tests on core functionality. Good structure and readability.
- **Incremental commits** that show how the solution evolved.
- Commit artifacts that explain the thinking: requirements, design notes, architecture diagram, AI prompts, trade-offs, performance notes.
- Requirements doc is written **before** code and is the first commit.

## Product decisions already made (see docs/requirements.md)

- Product name: **ACME People Management**. Tagline: "See how ACME pays people."
- Insights are the core product. Employee CRUD and the directory are the supporting layer.
- Insights slice salary by country, department, job title, title x country, tenure, and show highest/lowest paid individuals. Measures: headcount, total, min, median, mean, max.
- **Every statistic is computed per currency. Never mix currencies.** No FX conversion in this cut.
- Salary history: append-only dated entries. Current salary is the latest entry, and each entry's end date is derived from the next, so overlaps are impossible by design. A new entry's effective date must be later than the latest one.
- Salary is annual base pay in integer minor units. Never a float.
- Titles and departments are standardized reference data.
- Single HR persona, one shared login to protect the app (credentials from environment config, never hardcoded).
- Out of scope: performance reviews, promotions, recruitment, leave, gender/diversity pay equity, payroll, payslips, tax, bank transfers.

## Working assumptions (questions sent to Incubyte, awaiting reply)

Annual base salary only; salary history kept; every employee has a salary; all employees current; per-currency reporting; single HR user; standardized titles and departments with no levels or grades. If a reply arrives, I will tell you and we update `docs/requirements.md` and the design.

## Repository layout

Monorepo:

- `backend/`: Spring Boot API, Gradle. Structure and rules in `backend/CLAUDE.md`.
- `frontend/`: Angular app. Structure and rules in `frontend/CLAUDE.md`.
- `docs/`: requirements, design, `architecture/` (diagrams and ADRs), AI usage log, trade-offs.
- `compose.yaml`: local run of Postgres, API and web (planned).
- `git_hooks/`, `scripts/`, `Makefile`: developer tooling. `make help` lists the commands.

## Decisions made (details in docs/design.md and docs/architecture/)

| Area | Decision |
|---|---|
| Frontend | Modern Angular, standalone components, signals, no NgRx |
| UI library | Angular Material |
| Backend | Spring Boot, Java, Gradle, package `com.acme.peoplemanagement` |
| Database | PostgreSQL (ADR 0001), Liquibase migrations |
| Repo | Monorepo (layout above) |
| Backend pattern | Package-by-feature with light layers inside each feature. Salary rules are plain domain classes |
| Frontend pattern | Feature folders with standalone components |
| Architecture | Modular monolith. No microservices, no hexagonal or DDD layers |
| Frontend and API | Separate apps. Locally nginx serves the Angular build and proxies `/api`, so no CORS |
| Hosting | Local first with `docker compose up`. Hosted deploy deferred (fallback: Render + Neon) |
| Unit tests | JUnit and Mockito. No database or Docker |
| Integration tests | PostgreSQL via Testcontainers: migrations, constraints, queries, percentile calculations |

## Per-app rules

Design rules and coding conventions live next to the code and load when working in that folder:

- [backend/CLAUDE.md](backend/CLAUDE.md): Java conventions and backend design rules.
- [frontend/CLAUDE.md](frontend/CLAUDE.md): Angular conventions and frontend design rules.

Rules that cross the API boundary stay in this file (per-currency statistics, salary as integer minor units).

## How to work with me

- **Do not jump into code.** `docs/design.md` is drafted. The schema, API shape, and insights approach are still to write. Wait for my approval before building.
- **Do not overbuild.** Smallest complete, well-tested product. Do not add features, tables, or screens beyond `docs/requirements.md`.
- **Ask me before major decisions.** Offer a recommendation with trade-offs, one question at a time.
- **ADRs only for hard-to-reverse decisions** (like the database). Smaller choices go in the decisions table in `docs/design.md`.
- **Test first on core logic** (salary history rules, insight calculations, filtering, validation), with red and green commits where practical. Test names describe behavior, for example "returns median salary per country per currency". Unit tests use small fixtures and run in milliseconds.
- **Small, focused commits** with clear messages (`docs:`, `chore:`, `feat:`, `test:`, `fix:`). Never one giant commit. I run the commits myself: stage the files and suggest a message.
- **Keep `docs/ai-usage.md` updated as we go**: the prompts used, what the AI got wrong, what I changed and why. Add an entry at the end of each work session.
- Record trade-offs and performance numbers (real timings on 10k rows) in `docs/tradeoffs.md`.
- Keep the code clean and readable. Prefer simple over clever.

Requirements, design, AI usage, and trade-off docs committed. Seed script runs and produces 10,000 employees. Core logic covered by fast deterministic tests. App runs locally with `docker compose up` and the shared login protects it. README gives run, test, and seed commands. Git history shows clear incremental progress. Hosting is a bonus unless Incubyte confirms it is required.
