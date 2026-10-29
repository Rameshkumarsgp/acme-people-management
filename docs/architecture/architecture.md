# Architecture

High-level view of ACME People Management as built. See [design.md](../design.md) for the reasoning behind each choice.

## 1. System overview

The Angular UI is a separate app from the API. The API is a Spring Boot modular monolith, one package per feature, that talks to PostgreSQL. There are no microservices.

```mermaid
flowchart LR
    HR([HR Manager])
    UI["Angular UI"]

    subgraph App["Spring Boot API: one package per feature"]
        direction TB
        Auth["auth<br/>shared login, failed-attempt throttle"]
        Employee["employee<br/>employees, salary entries, change log"]
        Salary["salary<br/>timeline rules, entries, change log"]
        Reference["reference<br/>countries, departments, job titles, currencies"]
        Directory["directory<br/>read model: search, filter, sort, page"]
        Insights["insights<br/>read model: statistics per currency"]
        Readmodel["readmodel<br/>current-salary SQL, shared filters"]
        Dataload["dataload<br/>CSV loader and seed, command line"]
    end

    DB[("PostgreSQL<br/>Liquibase migrations")]

    HR -->|browser| UI
    UI -->|REST / JSON| Auth
    UI -->|REST / JSON| Employee
    UI -->|REST / JSON| Directory
    UI -->|REST / JSON| Insights
    UI -->|REST / JSON| Reference
    Employee --> Salary
    Employee --> Reference
    Directory --> Readmodel
    Insights --> Readmodel
    Dataload --> Employee
    Dataload --> Salary
    Dataload --> Reference
    Employee -->|JPA| DB
    Salary -->|JPA| DB
    Reference -->|JPA| DB
    Directory -->|"SQL, read only"| DB
    Insights -->|"SQL, percentile_cont, read only"| DB
```

- Every write to a salary entry goes through the `salary` feature, which is the only writer of salary rows. It checks the whole resulting timeline and writes the change log in the same transaction.
- `directory` and `insights` are read models. They join other features' tables in SQL, never write, and import no other feature's classes, only `readmodel`. This is the one exception to the rule that features reach each other through public services.
- Insights are computed in the database, always per currency. Currencies are never mixed or converted.
- The login is HTTP Basic from the environment. Every endpoint except health needs it.

## 2. Layers inside a feature

Each backend feature follows the same light layering, with a sub-folder per role: `api/` (controller and DTOs), `rules/` (plain classes) and `errors/`. The rules have no Spring or database dependencies, so unit tests run in milliseconds. One exception handler turns each exception type into a `problem+json` answer.

```mermaid
flowchart LR
    C["Controller<br/>REST, validation"] --> S["Service<br/>use cases, transactions"]
    S --> D["Rules<br/>plain Java"]
    S --> R["Repository<br/>JPA"]
    R --> DB[("PostgreSQL")]
```

A read model has no repository. Its SQL is built from fixed fragments and every request value is a bound parameter.

```mermaid
flowchart LR
    C["Controller<br/>REST"] --> Q["Query rules<br/>checked parameters"]
    Q --> S["Service<br/>read-only transaction"]
    S --> B["SQL builder<br/>fixed fragments, named parameters"]
    B --> J["JdbcTemplate"]
    J --> DB[("PostgreSQL")]
```

## 3. Frontend structure

Standalone components with signals and Angular Material. The address holds the state of the dashboard and the directory.

```mermaid
flowchart TB
    subgraph Features["features/ (lazy-loaded routes)"]
        Login["login"]
        Home["home<br/>insights dashboard"]
        Dir["directory"]
        Form["employee-form"]
        Edit["employee-edit"]
        Detail["employee-detail<br/>salary history, change log"]
    end
    subgraph Shared["shared/"]
        Money["money<br/>format, parse, validate"]
    end
    subgraph Core["core/"]
        EmpApi["employees API client"]
        InsApi["insights API client"]
        RefApi["reference API client"]
        AuthSvc["auth service, guard, interceptor"]
        Http["api: base URL, timeout, error mapping"]
    end

    Login --> AuthSvc
    Home --> InsApi
    Home --> RefApi
    Home --> Money
    Dir --> EmpApi
    Dir --> RefApi
    Dir --> Money
    Form --> EmpApi
    Form --> RefApi
    Form --> Money
    Edit --> EmpApi
    Edit --> RefApi
    Detail --> EmpApi
    Detail --> Money
    EmpApi --> Http
    InsApi --> Http
    RefApi --> Http
```

Features do not import each other. `core/` and `shared/` never import from `features/`. The route guard keeps every page except the login behind the shared login, and the interceptor adds the login header to every API call. The login is kept in memory, so a page reload signs the user out.

## 4. Local deployment

```mermaid
flowchart LR
    Dev([Developer / Reviewer])
    subgraph Host["Your machine"]
        Web["ng serve<br/>Angular dev server :4200"]
        Api["Spring Boot API :8080<br/>./gradlew bootRun"]
    end
    subgraph Compose["docker compose up -d postgres"]
        PG[("postgres container :5432")]
    end
    Dev -->|http://localhost:4200| Web
    Web -->|"/api proxied"| Api
    Api -->|JDBC| PG
    Env[".env<br/>login credentials and database"] -.-> Api
    Seed["make seed<br/>10,000 employees"] -.->|loads| PG
```

Only PostgreSQL runs in a container. The API and the dev server run from the repository, as the README describes. The dev server forwards `/api` to the API, so the browser uses one origin and no CORS is needed. Credentials come from environment variables and are never hardcoded. The seed (10,000 employees, deterministic) is loaded once with `make seed`. Containers for the API and the web app, and a hosted deployment, are not built. See [design.md](../design.md#deployment).
