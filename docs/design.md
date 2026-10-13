# ACME People Management: Design [WIP]

Status: Draft. Only the architecture decisions are written so far. Remaining sections are listed at the end.

## Decisions so far

| Decision | Choice | Why |
|---|---|---|
| Frontend framework | Modern Angular (not AngularJS) | AngularJS is end-of-life |
| Backend | Spring Boot, Java | Required by the brief |
| Build tool | Gradle | Project preference |
| Database | PostgreSQL (see [ADR 0001](architecture/adr/0001-database.md)) | `percentile_cont` for medians, real-engine tests |
| Migrations | Liquibase | Project preference |
| UI component library | Angular Material | Official, accessible, table with server-side sort and pagination |
| Repo structure | Monorepo | See below |
| Backend structure | Package-by-feature with light layers | See below |
| Frontend structure | Feature folders with standalone components | See below |

## Testing approach

- **Unit tests:** JUnit and Mockito. No database or Docker. They cover salary history rules, validation, and service logic, and run in milliseconds.
- **Database integration tests:** PostgreSQL with Testcontainers. They verify migrations, constraints, queries, and percentile calculations.

## Architecture decisions and alternatives

### 1. Repo structure: monorepo

```
acme-people-management/
├── backend/        Spring Boot, Gradle
├── frontend/       Angular
├── docs/           requirements, design, architecture/adr, ai-usage, tradeoffs
├── compose.yaml    local run: Postgres, API, web
└── README.md
```

The frontend is a separate app from the API. Locally, a small nginx container serves the built Angular files and forwards `/api` to Spring Boot, so the browser sees one origin and no CORS setup is needed. During development, `ng serve` proxies `/api` to the API.

| Option | Pros | Cons |
|---|---|---|
| **Monorepo (chosen)** | One commit history. One clone and one README. API and UI change together in one commit. | Gradle and npm toolchains mix. |
| Two repos | Independent release cycles. | Split history. Two deploys and CORS to configure. No benefit for one developer and one team. |
| Monorepo with tooling (Nx, Turborepo) | Caching, affected-only builds. | Overbuilding for two modules. |

**Migration later:** easy. `git subtree split` on `backend/` or `frontend/` produces a separate repo with history. Splitting a monorepo is cheaper than merging two repos.

### 2. Backend structure: package-by-feature

```
com.acme.peoplemanagement
├── employee/     controller, service, repository, entity, dto
├── salary/       history rules (pure domain class), service
├── insights/     generic slice-by-dimension query, controller
├── reference/    titles and departments
├── auth/         shared login
└── config/
```

Each feature keeps `controller → service → repository`. The salary history rules sit in plain classes with no Spring or database dependencies, so unit tests stay fast.

| Option | Pros | Cons |
|---|---|---|
| **Package-by-feature (chosen)** | Code that changes together lives together. Boundaries are visible, so extracting a module later is easy. | Needs discipline so features do not reach into each other's internals. |
| Package-by-layer | Familiar from tutorials. | One feature is spread over several folders. Hides boundaries. |
| Hexagonal / clean / DDD | Strongest isolation of domain logic. | Many extra interfaces and mappers. Heavy for CRUD plus analytics, and we are not swapping infrastructure. |
| Spring Modulith | Verifies module boundaries in tests. | Extra dependency, more than needed. |

**Migration later:** easy in either direction, mostly moving files. Moving toward hexagonal is gradual, and the framework-free salary rules are already close to a domain core.

### 3. Frontend structure: feature folders with standalone components

```
src/app
├── core/       api client, auth guard, interceptor
├── shared/     reusable table, formatters (money, currency)
└── features/   directory, employee-form, insights
```

Standalone components, lazy-loaded routes, and signals for state. No NgRx: plain services over the API are enough.

| Option | Pros | Cons |
|---|---|---|
| **Feature folders with standalone components (chosen)** | Current Angular default. Less boilerplate. Lazy routes by feature. | None significant. |
| NgModules | Older, widely documented. | More boilerplate. Treated as legacy by new Angular versions. |
| Package-by-type (`components/`, `services/`) | Simple at first. | Same scaling problem as package-by-layer. |
| NgRx or micro-frontends | Strict state management, or independent UI teams. | Heavy for about three screens. Micro-frontends solve a team problem we do not have. |

**Migration later:** easy. Standalone components mix with NgModules, and signals-based services can move into NgRx if state grows complicated.

### 4. Why a monolith, not microservices or a complex pattern

- **Scale:** 10,000 rows and one HR user do not need distributed services.
- **Cost:** microservices add network calls, service-to-service auth, per-service deployment, distributed transactions, and eventual consistency.
- **Salary history:** it needs a transaction and a database constraint. Splitting it from employee data would make that consistency hard.
- **Insights:** they are SQL joins and group-bys. These are cheap in one database and expensive across services.
- **Deployment:** the API is one deployable. The UI is static files served by nginx, so nothing else needs to run.
- **Pragmatism:** the simplest design that fits, with the reasoning recorded, is the goal.

**If scale arrives:** a modular monolith with feature packages is the stepping stone. If insights get heavy, the first step is a read-only replica or a reporting module, not a separate service. Extracting a feature package such as `insights` later means moving the package, adding an API boundary, and splitting its tables. That is bounded work, and far easier than merging microservices back together.

### 5. UI component library: Angular Material

A component library is a set of ready-made UI parts (tables, forms, dropdowns, date pickers, dialogs) that also handle keyboard support, accessibility, and a consistent look.

**Why:** the directory needs a sortable, paginated table over 10,000 employees, and the app needs several validated forms. Building these by hand would be overbuilding.

**Why Angular Material:** it is the official Angular library, covers every component we need, and is accessible by default.

| Screen | Angular Material provides | Stays in our code |
|---|---|---|
| Directory | Table, sort, paginator | Sending sort and page to the API |
| Employee form | Fields, select, date picker | Validation rules |
| Salary change | Dialog, date picker | Effective date rule |
| Insights | Tabs, table | Per-currency figures |

It does not call the API, format salaries, or draw charts.

See [architecture/architecture.md](architecture/architecture.md) for the architecture diagrams.

## Still to write

- Schema (employee, salary history, title, department, country and currency)
- API shape
- Insights approach (generic slice-by-dimension, per currency)
- Local deployment plan (see below)

## Deployment

**Current decision:** run locally for now and record the demo video from the local run. Hosted deployment is deferred.

- `docker compose up` starts three containers: PostgreSQL, the Spring Boot API, and `web` (nginx serving the Angular build and proxying `/api` to the API). The 10,000-employee seed is applied on first start.
- For development, run the API and `ng serve` with a proxy to `/api`.
- The shared login credentials come from environment variables.
- The README will list run, test, and seed commands.

**Open risk:** the brief lists deployed software as a requirement. If it must be hosted, the same Docker images can go to a free host (Render with Neon Postgres was the leading option) as a final step. 
