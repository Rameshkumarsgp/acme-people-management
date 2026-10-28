# ACME People Management

*See how ACME pays people.*

Salary management for an organization of 10,000 employees across several countries, replacing an Excel sheet. The HR Manager maintains employees and their salary history, and answers how the organization pays people: headcount, total, minimum, median, mean and maximum salary by country, department, job title, job title in a country and tenure, plus the highest and lowest paid. **Every figure is per currency. Currencies are never mixed or converted.**

Spring Boot (Java 25) and PostgreSQL on the back, Angular with Angular Material on the front. The thinking behind it is in [docs/](docs/): [requirements](docs/requirements.md), [design and decisions](docs/design.md), [architecture diagrams](docs/architecture/architecture.md), [trade-offs and measured numbers](docs/tradeoffs.md), [how AI was used](docs/ai-usage.md) and [known debt](docs/tech-debt.md).

## Run it

Needs Java 25, Node 24 and Docker (for PostgreSQL).

```sh
make setup                          # once: git hooks and frontend dependencies
cp .env.example .env                # then fill in the values: a login for the app and a database password
docker compose up -d --wait postgres # PostgreSQL 17, on 127.0.0.1:5432 (ACME_DB_PORT)
make seed                           # generates and loads 10,000 employees. Run on an empty database
set -a; . ./.env; set +a
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:${ACME_DB_PORT:-5432}/$ACME_DB_NAME \
       SPRING_DATASOURCE_USERNAME=$ACME_DB_USER SPRING_DATASOURCE_PASSWORD=$ACME_DB_PASSWORD
(cd backend && ./gradlew bootRun)   # API on :8080
(cd frontend && npm start)          # app on :4200, forwards /api to :8080
```

Open <http://localhost:4200> and sign in with the `ACME_AUTH_USERNAME` and `ACME_AUTH_PASSWORD` you put in `.env`. To point the dev server at an API on another port: `API_TARGET=http://localhost:8081 npm start`. To start over with an empty database: `docker compose down -v`.

## Test

```sh
make test              # fast checks for both apps, no Docker (what pre-commit runs)
make test-integration  # database tests against PostgreSQL in Docker (Testcontainers)
make test-e2e          # real-browser tests of the screens (installs Chromium once)
make check             # everything pre-push runs: fast checks, database tests, typecheck, Knip, build
```

`make help` lists every command. The backend gate runs Checkstyle, SpotBugs and Error Prone. The frontend gate runs Prettier, ESLint, `tsc`, Knip and the Vitest specs.

## Seed

```sh
make seed    # generates seed.csv (10,000 employees, deterministic) and loads it. Run on an empty database
```

Loading the same file twice rejects every row, so run it once per database. The generator and the loader are modes of the API jar (`--acme.seed-csv=FILE` and `--acme.load=FILE`). The loader validates every row, rejects the bad ones with a reason and loads the rest. It is also the path for moving data from Excel: save the sheet as CSV in the same format.

## What is in the app

- **Insights (home page):** slice by overall, country, department, job title, job title in a country or tenure. Filter by country, department, job title, currency and status. The highest and lowest paid in a currency. The view is in the address, so Back returns to it.
- **Employee directory:** search, filter, sort and paginate on the server, with the current salary in its own currency.
- **Employees:** create, edit, terminate (a termination date) and delete a record entered by mistake.
- **Salary history:** dated entries, each with its own currency. The current salary is the latest entry dated on or before today, so a raise can be entered in advance. Entries can be edited and deleted as long as the resulting timeline is valid, and every change is logged.

Salary is annual base pay, stored as integer minor units, never a float. Out of scope: performance reviews, promotions, recruitment, leave, pay equity, payroll and tax.

## Known limits

- Hosting: it runs locally, with PostgreSQL in Docker and the API and the dev server started by hand. There is no container for the API or the web app, and no hosted deployment. Whether hosting is required is a question to Incubyte.
- One shared login protects the app. The browser keeps it in memory, so a page reload signs you out. The failed-login lockout counts per connection address, so behind a reverse proxy it would be shared by everyone.
- Statistics cover each person's current salary as of today. There are no historical snapshots and no currency conversion.
- The median is exact up to about 4.5 quadrillion minor units and can be one unit low above that. See [trade-offs](docs/tradeoffs.md).
- Departments and job titles are reference data changed through migrations, not in the app.
