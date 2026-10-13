# Architecture [WIP]

High-level view of ACME People Management. See [design.md](../design.md) for the reasoning behind each choice.

## 1. System overview

The Angular UI is a separate app from the API. The API is a Spring Boot app that talks to PostgreSQL.

```mermaid
flowchart LR
    HR([HR Manager])
    UI["Angular UI<br/>(static files)"]

    subgraph App["Spring Boot API"]
        direction TB
        Auth["auth<br/>shared login"]
        Employee["employee<br/>CRUD, directory search"]
        Salary["salary<br/>history rules"]
        Insights["insights<br/>slice by dimension"]
        Reference["reference<br/>titles, departments"]
    end

    DB[("PostgreSQL<br/>Liquibase migrations")]

    HR -->|browser| UI
    UI -->|REST / JSON| Auth
    UI -->|REST / JSON| Employee
    UI -->|REST / JSON| Insights
    Employee --> Salary
    Employee --> Reference
    Employee -->|JPA| DB
    Salary -->|JPA| DB
    Reference -->|JPA| DB
    Insights -->|SQL, percentile_cont| DB
```

- All salary and title changes go through the `salary` feature, so history stays consistent.
- Insights are computed in the database, always per currency.

## 2. Layers inside a feature

Each backend feature follows the same light layering. The salary rules are plain classes with no Spring or database dependencies, so unit tests run in milliseconds.

```mermaid
flowchart LR
    C["Controller<br/>REST, validation"] --> S["Service<br/>use cases, transactions"]
    S --> D["Domain rules<br/>plain Java"]
    S --> R["Repository<br/>JPA / SQL"]
    R --> DB[("PostgreSQL")]
```

## 3. Frontend structure

```mermaid
flowchart TB
    subgraph Features["features/ (lazy-loaded routes)"]
        Dir["directory"]
        Form["employee-form"]
        Ins["insights"]
    end
    subgraph Shared["shared/"]
        Tbl["reusable table"]
        Fmt["money and currency formatters"]
    end
    subgraph Core["core/"]
        Api["API client"]
        Guard["auth guard + interceptor"]
    end

    Dir --> Tbl
    Ins --> Tbl
    Dir --> Fmt
    Ins --> Fmt
    Dir --> Api
    Form --> Api
    Ins --> Api
    Api --> Guard
```

Angular Material supplies the table, forms, dialogs, and theme inside these features.

## 4. Local deployment

```mermaid
flowchart LR
    Dev([Developer / Reviewer])
    subgraph Compose["docker compose up"]
        Web["web container<br/>nginx: Angular build"]
        Api["api container<br/>Spring Boot"]
        PG[("postgres container")]
    end
    Dev -->|http://localhost:4200| Web
    Web -->|"/api proxied"| Api
    Api -->|JDBC| PG
    Env[".env<br/>login credentials"] -.-> Api
```

nginx serves the UI and forwards `/api` to the API, so the browser uses one origin and no CORS is needed. Credentials come from environment variables and are never hardcoded. The seed (10,000 employees, deterministic) runs on first start.
