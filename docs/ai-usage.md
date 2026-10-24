# AI usage log

How AI (Claude Code) was used on this project: the prompts, what it got wrong, and what I changed and why. One entry per work session.

## Working agreement

- Claude proposes with trade-offs. I make the decision.
- No code before `docs/design.md` is approved.
- I run the commits myself. Claude stages files and suggests a message.
- Claude does not add features beyond `docs/requirements.md`.

## Session 1: repo setup and architecture decisions (13-14 Oct 2026)

### What we did

Repo housekeeping, the database ADR, the architecture decisions in `docs/design.md`, architecture diagrams, and a rewrite of `CLAUDE.md`. No application code yet.

### Prompts (summarized)

| Prompt | Outcome |
|---|---|
| "fetch claude.md file" | Claude read the file and flagged stray whitespace, the lowercase filename, and an unignored `.DS_Store` and `.idea/` |
| "add a sub folder inside docs for architecture, add git ignore, don't add claude.md to repo for now" | `.gitignore`, `docs/architecture/`, requirements moved to `docs/` |
| "angular 2 only, add an ADR for the database: why not SQLite or others, why not NoSQL?, what is a component library?, gradle" | Database ADR 0001, explanation of component libraries |
| "explain `percentile_cont`" | Short explanation, used to justify Postgres |
| "alternatives to package-by-feature, monorepo, feature folders: why not microservices, how hard to migrate later?" | Alternatives and migration cost recorded in `docs/design.md` |
| "component library: Angular Material, make me understand its uses" | Decision recorded, plus a visual comparison of a UI built with and without a library |
| "lay out hosting options" then "why do we need to host? deploy locally for now" | Hosting deferred. Local `docker compose up`, hosted deploy kept as a fallback |
| "can we keep the frontend out of Spring Boot?" | Separate frontend with nginx locally. Docs updated |
| "review claude.md, add the Angular style guide and JetBrains Java best practices" | `CLAUDE.md` rewritten with a decisions table and coding conventions |

### What the AI got wrong or I had to correct

| What happened | What I did and why |
|---|---|
| The first ADR draft covered MongoDB and NoSQL, and carried a column about ephemeral disks and a line about deployment. | I asked for these to be removed. The brief requires a relational database, so a NoSQL section was noise. The hosting constraint did not belong in a database ADR. |
| The ADR assumed Flyway for migrations without asking. | I chose Liquibase. A default tool choice should have been a question. |
| The ADR referenced `CLAUDE.md`, which was not committed. | I had the reference removed so the repo does not point to a missing file. |
| Claude staged `docs/.obsidian/` when it ran `git add docs`. Obsidian had created those files. | Claude unstaged them and added `.obsidian/` to `.gitignore`. The lesson: stage specific files, not whole directories. |
| The design followed the `CLAUDE.md` leaning of bundling the Angular build into the Spring Boot jar. | Since hosting is local only, I chose a separate frontend. Claude updated the design and diagrams. The original leaning was written before the hosting decision. |
| Claude sometimes asked two questions in one message, against the "one question at a time" rule. | Noted in `CLAUDE.md`. Claude keeps to one question. |
| Claude's commit commands were denied by the permission setting. | I run commits myself. This is now in `CLAUDE.md`. |

### Decisions I made, and the reasoning

- **Postgres over SQLite, H2, and MySQL:** `percentile_cont` gives the median in SQL, and Testcontainers lets tests run on the real engine. See ADR 0001.
- **Liquibase** for migrations: my preference.
- **Modular monolith, not microservices:** 10,000 rows and one user. Salary history needs a transaction, and insights are SQL joins.
- **Angular Material:** official, covers the table, forms, and dialogs we need.
- **Local-first deployment:** the video is recorded locally. Hosting stays an open risk to confirm with Incubyte.
- **Separate frontend:** nothing needs one jar when running locally.

### What I would do differently

- Ask for the full list of tool choices (migrations, build, test stack) at the start, not one at a time.
- Keep `CLAUDE.md` in sync with decisions as they are made, not at the end of the session.

## Session 2: the build checks missed what the IDE found (16 Oct 2026)

### What happened

Slice 0 passed every gate: Checkstyle, SpotBugs, Error Prone with `-Werror`, and 31 tests. IntelliJ's own inspections then reported 12 Java warnings, including a `throws Exception` on `SecurityConfig.securityFilterChain` that nothing ever throws. The question was how the analyzers missed it, and how to catch the same class of problem before a commit.

### Prompts (summarized)

| Prompt | Outcome |
|---|---|
| "Exception 'java.lang.Exception' is never thrown in the method" | Fixed in one line. Spring Security 7's `http.build()` no longer declares a checked exception. |
| "how lint or analyser didn't find it?" | Each tool was tested with probe code instead of answered from memory. |
| "fixing is one thing, how come it's missed out? I want to check before committing the code" | Gate first: add checks, show they fail on the unfixed code, then fix. |
| "once done add this in ai_usage" | This entry. |

### What the AI got wrong

| What happened | What it meant |
|---|---|
| The AI wrote `throws Exception` out of habit from older Spring Security versions. | A version-specific habit. The build accepted it, and only the IDE noticed. |
| The AI had reported "Checkstyle, SpotBugs and Error Prone all pass", which sounded like the code was clean. | It only meant the configured checks passed. Nobody had asked what those checks cannot see. |
| The AI first said SpotBugs has no detector for this. | Wrong. SpotBugs has "Method lists Exception in its throws clause, but it could be more specific". It is low confidence, and our `MEDIUM` report level hides it. The AI found this only when it tested it. |
| The AI guessed the four properties warnings were missing configuration metadata. | They were IntelliJ's "Unused property", a false positive for Spring config. |
| The AI proposed fixing first. | The user asked for the analyzer first, which was the better order. |
| The AI said the constant `type` field was "fixed properly" by turning it into a constant method. | The IDE flagged the method instead ("always returns the same value"), so the warning only moved. It was read from a second export, and the real fix was to build the body as a map. |
| The AI's own NullAway setup added two new IntelliJ warnings in `build.gradle`: a wrongly typed `check(...)` call and an unnecessary qualified class name. | The build accepted it, so only the IDE export showed it. The fix was the plugin's plain `error('NullAway')`. A change that adds a gate can add noise too, so the IDE results have to be read again after it. |

### What was verified, not assumed

| Tool | Finding |
|---|---|
| javac with `-Xlint:all` | Silent. Java allows an unneeded `throws`. |
| Checkstyle 14.3.0 | No check for an unneeded `throws`. `IllegalThrows` can forbid chosen types. |
| SpotBugs 4.10.5 | Has a pattern, hidden at our `MEDIUM` level. |
| Error Prone 2.50.0 | `CheckedExceptionNotThrown` is off by default. When on, it flags private and final methods only, because a subclass could override and throw. Our `@Configuration` method is neither. |
| IntelliJ "parameter always has the same value" | No open-source analyzer has an equivalent. |

### What was tried, and what happened to it

Checks and fixes were built and verified in this session: Checkstyle `IllegalThrows` for main code, NullAway with `@NullMarked` on every package, removal of the `throws Exception`, three test helper parameters, and a rebuilt error body. Each gate was shown to fail on the original code before the fix. The IDE export was then read three times, and each pass found something new: a constant method that only moved the warning, and two warnings in `build.gradle` caused by the setup itself.

**The user was not satisfied with the overall solution and reset the code.** None of it is committed. The findings, the verified results and the proposed fixes are kept in [tech-debt.md](tech-debt.md), so they can be redone deliberately and one at a time.

### Lesson

A green build means the configured checks passed, not that the code is clean. When the IDE finds something the build did not, add the missing check first, watch it fail on the existing code, then fix. Whack-a-mole is a sign that the fix was chosen to silence the warning instead of to remove the cause. The open work is in [tech-debt.md](tech-debt.md).


## Session 3: slice 2, create and read an employee (19 to 22 Oct 2026)

### What we did

Slice 2 end to end: the first employee with their first salary entry, through the API and two Angular screens. I had the AI put the design decisions to me one at a time with a recommendation, then write a spec, then an implementation plan of 14 tasks, and then build it task by task. Each task was test first, with the code broken on purpose to prove the test, and ended with the files staged and a commit message for me to use. I made every commit myself.

### Prompts (summarized)

| Prompt | Outcome |
|---|---|
| "Put the design decisions to me one at a time, with a recommendation." | Eleven decisions, and I chose the recommended option (A) each time: a pure timeline validator, one exception handler that maps by type, `salary` owns its rows, the unique index decides a duplicate email, lookups with a field error, Bean Validation, future hire dates rejected, a derived status, three test layers, exact string-based money, one error mapper |
| "I want a clean and explandable dicision?" and "whats clean and explandable?" | The AI re-argued each option against Clean Code and SOLID before I chose |
| "my concern was business logic should be seprate?" | The AI showed which class holds the rules, which holds the HTTP mapping and which holds the orchestration, and wrote down that the exception handler must map by type and decide nothing |
| "do it in phases not in single commit" | One task at a time, stopping after each for my commit |
| "for future commits segregate into folder as well ... for salary do it now" | `salary/rules`, `employee/api`, `employee/errors` and `employee/rules`. `reference` stays flat for now |
| "give me curl" | Commands to call the API by hand. They did not work at first: the API was on 8081, and my terminal had not loaded `.env` |

### What the AI got wrong or I had to correct

| What happened | What I did and why |
|---|---|
| The spec said `Employee` would hold `@ManyToOne` links to the reference entities and call their repositories. Those types are package-private, so that could not compile. | The AI found it only when it wrote the plan from the real code. `Employee` now stores plain keys and `reference` gained a public `ReferenceLookup`. I had it recorded as design decision 12. A spec written without reading the code can be wrong in its details. |
| Several "mutation proofs" proved nothing: the mutated code failed to compile (an unused variable under `-Werror`, a TypeScript null check), so no test ran. | The AI caught these by reading the output, and redid each one as a mutation that compiles and fails the intended test. |
| The first run of the detail page spec failed 3 of 4 tests with the real code. It waited with `whenStable`, which does not wait for promises. | The mutation results from that run were thrown away. The spec now polls with `vi.waitFor`, as the login spec already did. |
| The "lists cannot be loaded" test cascaded into 10 failures. `forkJoin` cancels its other requests when one fails. | Three lists are answered first, then one fails. |
| Two SpotBugs failures reached me late, because only the `integrationTest` task was run for a new file. | Both were fixed. Every task now ends with the full check. |
| The first field-placement tests searched all errors at once, so an error under the wrong field would pass. | Tightened to read the error inside the one field, and proved with a wrong-field mutation. |
| The API silently cut a fraction off a number: `"amountMinor": 100.9` was stored as 100, and `"departmentId": 1.9` used department 1. The Angular form never sends a fraction, so none of the tests had seen it. | Found in the review of the whole slice, by probing the API with odd input. It is a data-loss bug for a salary app. Fixed with one Jackson setting, `accept-float-as-int=false`, and two tests that fail without it. |
| The plan put every class of a feature in one flat folder. | I asked for sub-folders. The plan was updated, and the rule is that a class stays package-private only if everything using it is in the same folder. |

### Decisions I made, and the reasoning

- **A pure `SalaryTimelineValidator`:** the rules sit in one plain class with no Spring or database, so the tests run in milliseconds and every later slice adds one rule and one test to the same class.
- **One exception handler that maps by type:** the business rules stay out of the HTTP layer.
- **`salary` owns its rows, and `employee` calls it in one transaction:** one salary write path, and no half-created employee.
- **The unique index decides a duplicate email:** it is the only option that is also correct for two requests at the same moment.
- **Future hire dates are rejected:** recruitment is out of scope, and every employee keeps a current salary. Recorded as a limit in `docs/requirements.md`.
- **Exact money as text:** no floating point anywhere between the typed amount and the stored minor units.
- **Phases and folders:** I asked for both after the first tasks, so each commit stays small and the structure stays readable.

### What was verified, not assumed

| Question | Finding |
|---|---|
| Does Hibernate expose the violated constraint's name on PostgreSQL? | Yes. The case-insensitive duplicate email test passes through it. |
| Is the Spring `handleMethodArgumentNotValid` override signature right for this version? | Yes. It compiled and worked as written. |
| Do the malformed-body and bad-id errors come back as problem+json? | Yes, from the base `ResponseEntityExceptionHandler`. Removing it fails exactly those tests. |
| Does the slice work, not just the tests? | A headless browser drove login, create (INR and JPY), the duplicate email, a future hire date and an unknown id against the live API and database. All passed. |

### What I would do differently

- Say at the start that I want small commits and feature sub-folders. I had to ask for both after the work began.
- Have the AI read the real code before it writes the spec. One decision in the spec could not compile.

### Lesson

A test only counts when it was seen failing for the right reason. Three times the evidence looked fine and was not: a mutation that did not compile, a spec that waited on the wrong thing, and assertions too loose to notice a misplaced message. Reading the actual failure output, and asking whether it fails for the reason intended, caught each of them.


## Session 4: slice 3, the salary history (22 to 23 Oct 2026)

### What we did

Slice 3 end to end: an employee's salary history on the detail page, and adding a salary entry, through the API and Angular. I had the AI put seven design decisions to me one at a time with a recommendation, then write a spec, then an implementation plan of 11 tasks, and then build it one task at a time. Each task was test first, with the code broken on purpose to prove each test, and ended with the files staged and a message for me to commit.

### Prompts (summarized)

| Prompt | Outcome |
|---|---|
| "start slice 3" | Classified as architectural, then seven decisions one at a time |
| "go ahead with A", "A" (seven times) | The endpoint lives in `employee` and takes the row lock, the change log waits for slice 7, a pure `SalaryHistory` with the end date as the last day of the period, the duplicate date is a validator rule under the lock, the currency defaults to the entry before the date, `POST` returns the created entry with a `Location`, the dialog always sends the currency |
| "yes, write the spec", "approved, write the implementation plan", "approved, start" | The spec, the plan and the build, in that order and each approved first |
| "move on", "next" | One task at a time, stopping after each for my commit |

### What the AI got wrong or I had to correct

| What happened | What I did and why |
|---|---|
| The plan said one mutation would catch "an entry dated today counts as scheduled". It survived: no test checked it. | The AI found it by running the mutation. A test now asserts that an entry dated today is not scheduled. This is the boundary the slice is about. |
| The default-currency test had only one earlier entry, so "take the earliest entry's currency" passed as well. | The test now has two earlier entries in different currencies and one later, so earliest, latest and later all give different answers. |
| The first race test failed without the lock in only 2 of 6 runs. A later change that dropped the lock would usually have passed CI. | It now repeats the race 25 times on fresh dates. It fails 6 of 6 runs without the lock and passes with it. |
| Several mutations did not compile (Error Prone rejected swapped arguments and an unused parameter), so they proved nothing. One was in the plan itself. | The AI caught them by reading the output and redid each as a mutation that compiles. |
| A duplicate date sent without a currency answered "currency required", because the currency was filled in before the dates were checked. | Found by the end-to-end test. The date rules now come first. Recorded as design decision 15. |
| The plan said 13 web tests and there are 12. A line of 122 characters was found only by the full gate, after partial runs. | The count was corrected. Every task now ends with the full gate. |
| The real-browser script waited for `<option>` elements to be visible, which Playwright never allows, and read the select before its options rendered. | These were script bugs, not app bugs. After fixing them all 9 steps passed. |

### Decisions I made, and the reasoning

- **The lock and the endpoint live in `employee`:** `salary` never imports `employee`, so there is no cycle.
- **The change log waits for slice 7:** nothing could read it before then, and the seed does not log either.
- **A pure `SalaryHistory`, with the end date as the last day of the period:** one definition, in milliseconds, matching how the termination date works.
- **The validator rejects a duplicate date, under the row lock:** the lock admits one writer at a time, so the check cannot be raced.
- **The currency defaults to the entry before the new date:** it follows the timeline, and handles a backdated entry.
- **`POST` returns the created entry:** the page re-reads from the server, so every derived value comes from one place.
- **The dialog always sends the currency:** it needs the decimals before it can check the amount.

### What was verified, not assumed

| Question | Finding |
|---|---|
| Does the row lock stop two adds on one date? | Yes. Without the lock the race test fails 6 of 6 runs, with it 0 of 4. Over real HTTP, 6 rounds gave one `201`, one `422` and one row each time. |
| Does Material's dialog work with a small `MatDialogRef` stub in jsdom? | Yes. |
| Does it work in a real browser? | Yes. Nine steps against the live API and database passed: history, a raise, a future raise, a used date, a date before the hire date, JPY decimals, Cancel, and a raise in another currency. |
| Does odd input cause a server error? | No. Fractions, huge numbers, bad dates, wrong types, overflowing ids, empty and non-JSON bodies, and wrong methods all gave a clear `4xx`. |

### What I would do differently

- Have the plan's mutation lists checked against the tests before building. Two of them were wrong.
- Run the full gate after every change, not only the test task I was working on.

### Lesson

A test counts only if a plausible break fails it. Three times in this slice a mutation survived or never ran: one boundary no test checked, one test too narrow to tell earliest from latest, and a race test that caught a missing lock only a third of the time. Running the break, and reading why it did or did not fail, found each of them.

## Session 5: slice 4, the CSV loader and the seed, and a hand test of slice 3 (23 Oct 2026)

### What we did

I tested slice 3 by hand in the browser, then built slice 4: a CSV loader and the deterministic 10,000-employee seed, as two command-line modes of the backend jar. I had the AI put six design decisions to me one at a time with a recommendation, write a spec, then an implementation plan of 8 tasks, and build it one task at a time. Each task was test first, with the code broken on purpose to prove each test, and ended with the files staged and a message for me to commit.

### Prompts (summarized)

| Prompt | Outcome |
|---|---|
| "lets test out slice 3" | Steps to run it by hand. My own run found a real bug: the first field's label in the Add salary dialog was cut off |
| "commit message and move on with pending slices" | The AI started slice 4 with decision 1 |
| "A" (five times) | The loader is a mode of the jar, the generator is Java in the same jar, pay is a multiplicative model, each salary is dated at the last work anniversary, and `make seed` runs two commands |
| "A" (leavers) | Leavers wait for slice 6, after the AI found the create path cannot set a termination date |
| "approved, write the implementation plan", "approved start" | The plan, then the build |
| "next" | One task at a time, stopping after each for my commit |
| "from now onwards add comment only if necessary" | Saved as a standing preference |

### What the AI got wrong or I had to correct

| What happened | What I did and why |
|---|---|
| The design said the CSV could carry leavers, but `EmployeeService.create` has no termination date, and the two rules for it belong to slice 6. | The AI found it by reading the real code before writing the spec, and asked me. I chose to defer leavers, and the loader rejects a termination date with a clear reason. |
| The AI said the generator would read the reference lists so it could not name something that does not exist. It needs its own pay table by title anyway. | The claim was corrected in the spec, and an integration test now checks the table against the migrations in both directions. |
| The plan's first design switched the web server off for batch runs. The security configuration expects a web application. | Runs use `--server.port=0`, which also avoids clashing with the running API. A real run proved the exit statuses. |
| The CSV library allows duplicate headers by default and its older flag is deprecated, so `-Werror` would fail on it. Row numbers count records, not lines. | Found by probing the library in a throwaway before the plan was written. The plan uses the current setting and defines the row number. |
| The tenure test passed even when the generator ignored tenure, because noise beat the average half the time by chance. | It now requires seniors to earn at least 15% more. It fails when tenure is ignored. |
| Four mutations did not compile (Error Prone rejected an unused field, an unused parameter, an unused variable and a comparison against 1), and a fifth failed every test. | The AI read the output and redid each as a mutation that compiles and fails only the intended tests. |
| The full gate found problems the plan missed: a record exposing a mutable map (SpotBugs), over-long lines, and an invisible byte-order-mark character stored where the code should have the visible escape. | Fixed in the task where each appeared, the map with its own test and proof. |
| The AI told me Postgres was already running, and told me to "open an existing employee", which has no screen yet. | Both were wrong. The database had stopped, and the directory is a later slice. I tested with a new employee, and the AI now checks first. |
| The first-field label in the Add salary dialog was clipped. | I found it by testing by hand. The body of the dialog scrolls, so the label needed more room above it. Fixed with one value. |

### Decisions I made, and the reasoning

- **The loader and the generator are modes of the jar:** imported rows go through the same service and rules as typed ones, with no second write path to salary rows.
- **Pay is a small multiplicative model:** a few constants give insights real differences by country, title and tenure, and a unit test can pin the shape.
- **Each salary is dated at the last work anniversary:** the date agrees with the tenure uplift it contains.
- **Leavers wait for slice 6:** slice 4 adds no rule that nothing in the UI can reach.
- **A bad row is rejected, anything else aborts:** a broken database is not a bad row, and counting it as one would hide a failing run.
- **No batching:** the load took 33 seconds, under the one-minute line I set before measuring.

### What was verified, not assumed

| Question | Finding |
|---|---|
| How long does loading 10,000 employees take? | 10,000 loaded, 0 rejected, in 32,958 ms (about 3.3 ms a row). |
| What does loading the same file twice do? | 10,000 rejected as `EMAIL_ALREADY_EXISTS`, nothing created, exit status 1, and it took 75,870 ms. Rejections cost about twice a load. |
| Does a batch run exit with the right status? | Yes, in a real process: seed mode exits 0, and load mode on a bad header exits 1. No test can show this, because it is `main`'s `System.exit`. |
| Is the seed deterministic? | Yes. Two separate processes on the same day wrote byte-identical files. A different day shifts the dates, and the tradeoffs note says so. |
| Does the generator's table match the reference data? | Yes, for titles, departments, countries and currency codes. It cannot check which currency a country uses, because the schema does not say. |

### What I would do differently

- Probe a library before writing a plan against it. Two plan details were wrong until the probe.
- Compile each planned mutation before trusting the plan's list. Four did not compile.

### Lesson

A test passing is not a proof. The tenure test passed with the feature switched off, half the time, by chance. Breaking the code and asking why it did not fail found it, and the fix was a margin the noise cannot fake.

## Session 6: slice 5, the employee directory (24 Oct 2026)

### What we did

Slice 5 end to end: a directory at `/employees` with search, filters, sort and paging over the 10,000 seeded employees, through the API and Angular. I told the AI to build it without asking me anything once the spec was approved, so after five design decisions it wrote a spec and a plan and then ran every task on its own, test first, breaking the code on purpose to prove each test. It could not commit, so it handed me one command per task. Before building it also measured the two ways of computing the current salary and the search on my real data.

### Prompts (summarized)

| Prompt | Outcome |
|---|---|
| "start slice 5" | Classified as architectural, then five decisions one at a time |
| "A" (twice), "A", "A", "B" | A read-side `directory` package that joins in SQL, a lateral join for the current salary, a case-insensitive contains search with escaping, filters and sort kept in the URL, and the directory at its own route with home linking to it |
| "approved, dont ask anything implemnent and let me know the plan" | The plan, then all seven tasks in one run, ending in a real run on my database and a real browser |

### What the AI got wrong or I had to correct

| What happened | What I did and why |
|---|---|
| The AI expected the lateral join to need only 20 salary lookups on the default view. The measurement showed all 10,014, because sorting by name has no index. | The decision was argued from clarity and scaling, not speed. The measured numbers went into the trade-offs, with the claim that was wrong. |
| A test of "no where clause" failed because the salary subquery has its own `where`. A country sort order I expected was wrong, and it also showed that several sorts gave the same order, so swapping two sort expressions would have passed. | Both were fixed in the tests. Each sort field now has a different order in the test data. |
| The web slice test failed everywhere because the shared login needs a `Clock` bean. | It was a setup error, found by reading the real error. The fix copies what the other web slices do. |
| A mutation survived: a search clause with an extra `or` in front. Another survived on trimming a country, and on an id of zero. | Each got a stronger assertion or a new test, and was proved again. |
| Many frontend mutations made 13 of 18 tests fail for a one-line change. A failed test left an open request, and the next tests failed on the leftover. | It only happens after a real failure, but it makes a whole-file run a poor proof. Each mutation was proved against its single targeted test instead. |
| The first real-browser run failed step 10: after Back, the first row was not the one the script had recorded. | The AI checked before blaming the app. The script read page 1's rows while page 2 was still loading. The script now waits for each request, and all 12 steps passed. |
| The directory ran flush against the left edge of the screen, unlike every other page. | The AI looked at the screenshot. It now has a centered container with a 16px gutter, and the table scrolls inside its own box on a phone. |

### Decisions I made, and the reasoning

- **A read-side package, not row-by-row calls:** the directory has to filter and sort by the current salary in the database. The boundary rule now has one stated exception, for read models.
- **A lateral join for the current salary:** it costs one index probe per employee. The window function was a millisecond faster, which is noise.
- **Search escapes `%` and `_`:** underscores are common in real emails, and a typed `%` would otherwise match every row.
- **The state lives in the URL:** Back and Forward work, and a link can be shared. A reload still logs you out, so returning to the requested page after login is a separate change I left out.
- **The "Back to directory" link goes to the default view:** the browser's Back button restores your filters, and the link does not.
- **No new index and no migration:** the numbers did not call for one.

### What was verified, not assumed

| Question | Finding |
|---|---|
| How fast is the directory on 10,014 employees? | 7 to 35 ms through the real endpoint. The default view is 30.6 ms at the median, the deep page 34.5 ms. |
| Does it hide anyone? | No. `status=ALL` returns 10,014, the same as the table. |
| Is the search right? | `rao` finds 487, the same as the plain SQL count. A literal `%` matches nothing, and a hostile text returns 200 with no matches and leaves the table intact. |
| Does the currency filter follow the current salary? | Yes. 2,982 are in India but 2,979 are paid in INR, because three test employees now have a salary in another currency. |
| Does it work in a real browser? | Yes. Twelve steps passed: open, search, filter, a sort that needs a currency, paging, opening an employee, browser Back restoring the view, and the empty state. Zero console errors. |
| Does it fit a phone? | Yes. The filters stack, and the page does not scroll sideways. |

### What I would do differently

- Put the "wait for the request" helper in the browser script from the start. I lost a run to a script race.
- Give each sort field a different order in the test data from the first draft.

### Lesson

When many tests fail for a one-line change, the first failure is the signal and the rest may be noise. Reading the actual error showed a leftover request, and running each mutation against its one test gave a clean proof.
