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

Every view can be filtered by country, department, and job title. Measures are headcount, total, min, median, mean, and max, **always computed per currency**.

## Scope (MVP)

1. **Compensation insights (core):** the questions above.
2. **Employee directory:** server-side search, filter, sort, and pagination. Shows employee ID, name, department, title, country, currency, and current salary.
3. **Employee management:** create, edit, and delete employees (name, email, department, title, country, currency, hire date).
4. **Salary history:** each salary change is a new dated entry. The current salary is the latest entry, and each entry's end date is derived from the next one, so overlapping periods cannot occur. A new entry's effective date must be later than the latest existing one.
5. **Data integrity:** stable employee IDs, database constraints, transactional updates, and input validation.
6. **Usability:** clear loading, empty, validation, and error states.
7. **Seed script:** deterministic generation of 10,000 employees with realistic pay by title, country, and tenure.
8. **Delivery:** unit tests on core rules, integration tests on insight queries against a real database, setup docs, a live deployment, and a short demo video.

## Deliberately left out, and why

- **Performance reviews, promotions, recruitment, and leave:** separate capabilities. Promotions can build on salary history later, and performance data is more sensitive.
- **Gender/diversity pay equity:** we hold no such data, and it needs sensitivity handling.
- **Payroll, payslips, tax, and bank transfers:** need financial and regulatory workflows.

## Open questions and working assumptions

Questions sent. Until they reply, we proceed with the defaults below.

| # | Question asked | Working assumption | If the answer differs |
|---|---|---|---|
| 1 | Annual base salary only, or bonuses and other pay too? | Annual base salary only, stored as integer minor units | Add a pay-component table (type, amount). Insights gain a "pay type" filter. |
| 2 | Should previous salary records be kept when a salary is updated? | Yes. Salary history is in the MVP as an append-only dated list | If no, drop the history table and keep a single current salary, which simplifies the model |
| 3 | Do we handle employees without a salary (interns, unpaid)? | No. Every employee has a salary | Allow a nullable salary. Headcount counts everyone, and salary statistics exclude people without one |
| 4 | Include former employees along with current ones? | No. All employees are current | Add an active flag and termination date. Insights default to active only, with a toggle |
| 5 | Show salaries per currency, or convert to a common currency? | Per currency, never mixed | Add an FX rate table with a documented rate date and a "converted view", kept separate from native figures |
| 6 | Single HR Manager, or multiple users with roles? | Single persona with one shared login for protection | If multiple users, add user accounts and roles, which also enables a "who changed it" trail |
| 7 | Are job titles and departments a standardized list, or free text? | Standardized lists, stored as reference data. No levels or grades | If free text, normalize on entry (trim, case-fold) and warn that title comparisons may be unreliable. If a level or grade structure exists, add a level field so "same role" comparisons can be made per level |
