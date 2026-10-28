# ACME People Management: Requirements [WIP]

*See how ACME pays people.*

## Goal

Replace spreadsheet-based salary management for ACME's 10,000 employees across multiple countries with a web app where the HR Manager can maintain salary records and **answer questions about how the org pays people**.

## User

The HR Manager (single persona). They need accurate salary data, quick updates, and compensation insights without pivot tables.

## Core question and its variations

*"How does the org pay people?"* is answered by slicing salary by each attribute we hold:

| Slice | Question HR will ask |
|---|---|
| Overall | How many people do we pay, and what is the total payroll? |
| Country | How does pay differ across countries? |
| Department | How does pay differ across departments? |
| Job title | What is the typical pay (min, median, mean, max) for a role? |
| Title × country | How is the same role paid in different countries? |
| Tenure | How does pay change with years at the company? |
| Individuals | Who are the highest and lowest paid within any of the above? |

Every view can be filtered by country, department, and job title. Measures are headcount, total, min, median, mean, and max, **always computed per currency**. Insights cover active employees by default, using each employee's salary as of today. Historical snapshots are not in the MVP.

## Scope (MVP)

1. **Compensation insights (core):** the questions above.
2. **Employee directory:** server-side search, filter, sort, and pagination. Shows employee ID, name, department, title, country, currency, and current salary.
3. **Employee management:** create, edit, and delete employees (name, email, department, title, country, hire date, and an optional termination date). Delete is for records entered by mistake and removes the employee with their history. An employee who left is given a termination date, the last day they were employed, and stays in the data.
4. **Salary history:** each salary change is a dated entry with its own currency. The current salary is the latest entry dated on or before today, so a raise can be entered in advance. Each entry's end date is derived from the next one, so overlapping periods cannot occur. Every change is checked against the resulting timeline: dates are unique, the earliest entry is on or after the hire date, at least one entry is effective on or before today, and no entry is dated after the termination date. HR can add, edit or delete any entry as long as the resulting timeline is valid. Every add, edit and delete is recorded in a change log showing what changed and when. A new employee's hire date and first salary date cannot be in the future, because a new employee must have a current salary. Entering an accepted offer before the start date belongs to recruitment, which is out of scope.
5. **Data integrity:** stable employee IDs, database constraints, transactional updates, and input validation.
6. **Usability:** clear loading, empty, validation, and error states.
7. **Data loading:** a command-line CSV loader that validates each row and loads employees with their current salary, which is the migration path from Excel. A deterministic generator produces 10,000 employees with realistic pay by title, country, and tenure in the loader's format, and is the seed. Only the current salary is migrated, so migrated employees have a one-entry salary history that starts on the given effective date. It is not a reconstruction of past pay.
8. **Delivery:** unit tests on core rules, integration tests on insight queries against a real database, setup docs, a live deployment, and a short demo video.

## Deliberately left out, and why

- **Performance reviews, promotions, recruitment, and leave:** separate capabilities. Promotions can build on salary history later, and performance data is more sensitive.
- **Gender/diversity pay equity:** we hold no such data, and it needs sensitivity handling.
- **Payroll, payslips, tax, and bank transfers:** need financial and regulatory workflows.
- **Upload screen, `.xlsx` parsing and export:** migration is a one-time CSV load from the command line. An upload screen with column mapping and row-level error review is a feature of its own, and exporting salary data widens the surface where it can leak.
- **Limits of the CSV load:** dates must be ISO and amounts plain numbers, so a sheet saved from Excel with local dates needs reformatting first. Loading the same file twice rejects every row.
- **Terminating an employee who has a future-dated raise:** refused until HR deletes that entry, because no entry may be dated after the termination date.
- **Maintaining departments and job titles in the app:** these are standardized reference data managed through migrations. HR cannot add a title without a code change.
- **Bonuses and other pay components:** only annual base salary is held. Bonuses, allowances, equity and benefits are separate pay types with their own rules, and mixing them into one figure would make the statistics hard to read. They can be added as a pay-component table later.
- **Employees without a salary (interns, unpaid):** every employee has a salary, so they are not modelled. If they were, they would count in the headcount and be left out of the salary statistics.
- **Currency conversion:** salaries are shown per currency and never converted. Conversion needs documented exchange rates and a rate date.
- **Several users and permissions:** one shared login protects the app. How to add user accounts, roles and a "who changed it" trail is under "How multiple users would be added" below.

## Decisions on the open questions

We asked Incubyte seven questions. They replied that these are product decisions for us to make, to document with our reasoning, and that either choice is acceptable on salary history, former employees and currency. A single HR manager is sufficient, and the answers below stand as our decisions. The last column is what changes if a decision is reversed. The reasons for decisions 2 to 5 are in the table.

| # | Question asked | Decision and reason | If it changes later |
|---|---|---|---|
| 1 | Annual base salary only, or bonuses and other pay too? | Annual base salary only, stored as integer minor units. Incubyte agreed. Bonuses and other pay are listed under "Deliberately left out". | Add a pay-component table (type, amount). Insights gain a "pay type" filter. |
| 2 | Should previous salary records be kept when a salary is updated? | Yes. Salary history is a dated list of entries. It lets HR correct a typo, enter a raise in advance, and see what changed and when, and a single current figure could do none of that. | If no, drop the history table and keep a single current salary, which simplifies the model |
| 3 | Do we handle employees without a salary (interns, unpaid)? | No. Every employee has a salary, so every employee is counted in the headcount and in every statistic. An unpaid intern is not modelled, and HR would record them with a salary only if they are paid. | Allow a nullable salary. Headcount would count everyone, and the salary statistics (total, min, median, mean, max) would exclude people without one, with the number excluded shown beside them. |
| 4 | Include former employees along with current ones? | Yes. A leaver gets a termination date and stays in the data. **Insights and the directory cover active employees by default**, and a status filter widens them to former employees or to all. The screen shows which is selected. | If former employees are not wanted, drop the termination date and the status filter |
| 5 | Show salaries per currency, or convert to a common currency? | Per currency, never mixed. Converting needs exchange rates with a rate date, and a total in the wrong rate is worse than no total. | Add an FX rate table with a documented rate date and a "converted view", kept separate from native figures |
| 6 | Single HR Manager, or multiple users with roles? | Single persona with one shared login for protection. Incubyte agreed that a single HR manager is sufficient. | See "How multiple users would be added" below. |
| 7 | Are job titles and departments a standardized list, or free text? | Standardized lists, stored as reference data. No levels or grades. Incubyte did not reply to this one, so it is our decision. | If free text, normalize on entry (trim, case-fold) and warn that title comparisons may be unreliable. If a level or grade structure exists, add a level field so "same role" comparisons can be made per level |

### How multiple users would be added

The shared login is one HTTP Basic credential from the environment. To support several users:

1. Add a `user` table with a hashed password and a role (for example HR manager, read-only viewer), and sign in against it.
2. Replace the shared credential check with that lookup. The security filter chain and the 401 behavior stay as they are.
3. Add `changed_by` to `salary_entry_change` and record the user on every add, edit and delete. Today the change log records what and when, never who.
4. Restrict writes to the HR role and let the viewer role read the insights and the directory. The endpoints already separate reads from writes.
5. Replace the in-memory login with a token or a session, so a page reload does not sign the user out.
