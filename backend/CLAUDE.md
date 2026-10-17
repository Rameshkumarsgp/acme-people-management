# CLAUDE.md: backend

Spring Boot, Java, Gradle. Applies to everything under `backend/`. Project context, product decisions and working rules are in the root [CLAUDE.md](../CLAUDE.md).

## Structure

Package-by-feature under `com.acme.peoplemanagement`: one package per feature, plus `config` for application wiring. The features are in the [system diagram](../docs/architecture/architecture.md#1-system-overview). Reasoning and alternatives: [design.md](../docs/design.md).

- A feature package holds its own controller, service, repository, entity and DTOs. See [layers inside a feature](../docs/architecture/architecture.md#2-layers-inside-a-feature).
- Salary history rules are plain classes with no Spring or database dependencies.
- Features do not reach into each other's internals. Dependencies between features follow the system diagram and never form cycles.

## Design rules

- Statistics are computed in the database with `percentile_cont`, not in Java.
- One generic "slice by dimension" insights mechanism, not hardcoded queries per view.
- Indexes on country, department, job title. Server-side pagination with a stable sort order.
- Optimistic locking (`@Version`) on employee records.
- Route all salary and title changes through one service so history stays consistent.
- Seed script must be deterministic (fixed random seed) with realistic pay varying by title, country, and tenure.
- Keep hosting easy later: CORS origins from an env var, shared login by token or Basic header (not cookies), a `/actuator/health` endpoint.

## Coding conventions

**Java** (based on the [JetBrains Java best practices](https://blog.jetbrains.com/idea/2024/02/java-best-practices/)):
- Clear over clever. Methods around 10-20 lines. Focused, cohesive classes.
- Name with care: no single-letter names, action-based method names, units in names (`timeoutInMs`).
- Use switch expressions instead of long if-else chains on one value.
- No empty catch blocks: log, rethrow, or return a meaningful fallback.
- Prefer immutability (`final`, records, no setters). Prefer composition over inheritance.
- Use lambdas, enhanced for loops or streams, try-with-resources, and guard clauses to avoid deep nesting.
- Prefer collections over arrays.
- Avoid circular dependencies between feature packages.
- Keep dependencies current and check for vulnerable ones.

**Applying the engineering principles in Java** (the principles are in the root [CLAUDE.md](../CLAUDE.md#engineering-principles)):
- Records are for values and DTOs. A record that holds a secret overrides `toString()` to leave it out.
- Constructor injection with `final` fields. An interface only for a real boundary or a second implementation.
- A method named like a question (`isBlocked`, `find...`) never changes state.
- One class decides each rule that several callers need, such as who counts as the same client, or the shape of an error response.
- Every error uses one problem+json writer, and the title comes from the status.
- A configuration property exists only once something reads it.
- Two kinds of test, separated by a JUnit tag. Fast tests (unit tests, and web slices with `@WebMvcTest`) need no Docker and run in pre-commit. Database tests use `@IntegrationTest`, which starts the app against PostgreSQL in Docker through Testcontainers, and run with `./gradlew integrationTest` and in pre-push. Tests use the main `application.properties` plus `application-test.properties`, so they check the real configuration.
