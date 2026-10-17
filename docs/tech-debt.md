# Tech debt

Known gaps and shortcuts that we carry on purpose, to be paid back later and not as part of a slice. Remove an item when it is paid.

## Static analysis, backend and frontend

The build gates passed while IntelliJ's inspections still reported real findings: an unneeded `throws Exception`, null-annotation mismatches and constant parameters. The rule from that session is: when the IDE finds something the build did not, add the missing check first, watch it fail on the existing code, then fix. Nothing below is done yet.

### Backend

- [ ] **Fail the build on `throws Exception` in main code.** Checkstyle `IllegalThrows` with `Exception`, `Throwable`, `Error` and `RuntimeException`, and a suppression for `src/test` (JUnit and MockMvc need it). A trial failed on `SecurityConfig.java:64` as wanted. Then drop the `throws Exception` there: Spring Security 7's `http.build()` no longer declares a checked exception.
- [ ] **Null-safety gate.** `@NullMarked` (JSpecify, already on the classpath through Spring) in a `package-info.java` for every package, including the root, plus NullAway. A trial with NullAway 0.14.2, Error Prone 2.50 and Java 25 worked: options `NullAway:OnlyNullMarked` and `NullAway:JSpecifyMode`, switched on with the Gradle plugin's `error('NullAway')`. It rejected `return null` from a non-null method and our own code passed. It clears the 7 IntelliJ nullability warnings. Known limit: unannotated third-party returns, such as `HttpServletRequest.getHeader`, are not flagged.
- [ ] **Test helper parameters that never vary** (an IntelliJ-only finding): `AuthEndpointTest.callMe` (`username`), `AuthEndpointTest.failLogin` (`attempts`) and `LoginAttemptThrottleTest.recordFailures` (`clientKey`).
- [ ] **`ProblemResponseWriter` error body.** The record's `type` is always `about:blank`. Turning it into a constant method only moves the IDE warning. Build the body as an ordered map and keep a test that pins `type`.
- [ ] Decide on `public static void main`: IntelliJ calls `public` redundant on Java 25. It works on older launchers, so keep it and suppress the hint with a comment, or accept the hint.
- [ ] Optional: Error Prone `CheckedExceptionNotThrown`. It is off by default and flags only private and final methods, so `@Configuration` classes would need to be `final` with `proxyBeanMethods = false`.
- [ ] Optional: SpotBugs at `reportLevel = 'LOW'` also reports a broad `throws`, but adds noise. The Checkstyle rule is the better gate.

### Frontend

- [ ] **Run IntelliJ's Inspect Code on `frontend/` and triage it the way the backend was.** This has not been done. Only ESLint, Prettier, tsc and Knip have run, so any IDE-only findings are unknown.
- [ ] Evaluate stricter checks, adopting each only after it fails on the existing code: type-aware ESLint (`@typescript-eslint/no-floating-promises` and the strict rule sets), `noUnusedLocals` and `noUnusedParameters` in tsconfig, more angular-eslint template rules.
- [ ] Confirm that Knip covers `e2e/` and `playwright.config.ts`. It passes today, but it is not proven that it would fail on an unused export there.

### Both

- [ ] **An IDE parity gate.** A way to run IntelliJ's inspections outside the IDE, such as an on-demand `make inspect` with Qodana, before a push and not in pre-commit. Some checks exist only in the IDE, for example "method parameter always has the same value".
- [ ] **Remove IDE-only noise for everyone.** A shared project dictionary and an inspection profile that turns off "Unused property" for Spring config. `.idea/` is git-ignored today, so this needs a decision to share a little IDE configuration. Until then: untick "Unused property", save dictionary words, fetch the Checkstyle DTD.
- [ ] After any new check, export the IDE inspection results again and read them. The NullAway setup added two warnings in `build.gradle` that the build never reported.
