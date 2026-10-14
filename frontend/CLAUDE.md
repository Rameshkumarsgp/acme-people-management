# CLAUDE.md: frontend

Modern Angular with Angular Material. Applies to everything under `frontend/`. Project context, product decisions and working rules are in the root [CLAUDE.md](../CLAUDE.md).

## Structure

Feature folders with standalone components under `src/app`. Dependency diagram: [frontend structure](../docs/architecture/architecture.md#3-frontend-structure). Reasoning and alternatives: [design.md](../docs/design.md).

- `core/`: app-wide singletons: API client, auth guard, interceptor.
- `shared/`: reusable presentational pieces: the table, money and currency formatters.
- `features/`: one folder per screen group (directory, employee-form, insights), each a lazy-loaded route.

Dependency direction:
- `features/` may use `core/` and `shared/`.
- `core/` and `shared/` never import from `features/`.
- Features do not import from each other. Anything two features need moves to `shared/`.

## Design rules

- Keep hosting easy later: API base URL from Angular environment config.

## Coding conventions

**Angular** (based on the [Angular style guide](https://angular.dev/style-guide)). When a file's existing style conflicts, keep consistency:
- File names are hyphenated and match the main class (`employee-form.ts`). No generic `utils.ts`. Specs are `*.spec.ts` next to the code.
- A component's files share one base name.
- App code in `src`, bootstrap in `src/main.ts`. Organize by feature, not by type. One concept per file.
- Use `inject()` instead of constructor injection.
- Keep components presentational: move validation and data transforms to separate files. Keep template logic simple, use `computed` for derived values.
- Order class members: injected dependencies, inputs, outputs, queries, then methods.
- Use `protected` for members only the template uses, and `readonly` for Angular-initialized properties.
- Prefer `class` and `style` bindings over `NgClass` and `NgStyle`.
- Name handlers by what they do (`saveEmployee()`), not by the event (`handleClick()`).
- Keep lifecycle hooks thin and implement the lifecycle interfaces.
