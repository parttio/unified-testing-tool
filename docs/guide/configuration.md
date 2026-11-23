# Configuration

Set these as JUnit configuration parameters, in `src/test/resources/junit-platform.properties`, or as system properties with `-D` from Maven, Gradle or the IDE. What the three Testing Tools share is decided in [2. Test setup](../spec/02-test-setup.md#choosing-the-testing-tool); the rest belong to one Testing Tool or to the reports.

> With TestBench on the test classpath, your own `junit-platform.properties` replaces TestBench's (JUnit reads only one and warns about the other), which turns off TestBench's parallel execution. To keep TestBench's file, pass the settings as Surefire `configurationParameters` instead, as [`unified-test-demo/pom.xml`](../../unified-test-demo/pom.xml) does.

| Setting | Default |
|---|---|
| `vaadin.testing.tool` | `browserless` (or `playwright`, `selenium`) |
| `vaadin.testing.base-url` | from the Spring port, else `http://localhost:8080` |
| `vaadin.testing.timeout` | `10s` |
| `vaadin.testing.evidence-dir` | `target/surefire-reports/unified-evidence` |
| `vaadin.testing.user-steps.log` | `failed` (or `all`, `none`): whose User Steps go to the CI log |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` to the GitHub job summary |
| `vaadin.testing.user-steps.changed-since` | in a pull request, `origin/$GITHUB_BASE_REF`; the base for "Changed in this PR" ([Reading reports and failures](reports-and-failures.md)) |
| `vaadin.testing.user-steps.baseline-dir` | unset; the base branch's evidence directory, such as its downloaded CI artifact: **Changed in this PR** shows how each changed scenario's User Steps differ from it ([Reading reports and failures](reports-and-failures.md)) |
| `vaadin.testing.user-steps.compare-dir` | unset; the evidence directory of a run of the same tests on another Testing Tool: the run summary says which scenarios differ ([Reading reports and failures](reports-and-failures.md#compared-with-another-testing-tool)) |
| `vaadin.testing.user-steps.server-log` | `warn`: the application's warnings and errors under the User Step during which they were logged; `error`, or `off` |
| `vaadin.testing.user-steps.snapshots` | `false`; `true`: a snapshot of the page after every User Step |
| `vaadin.testing.playwright.trace` | `off`; `on` or `on-failure`: keep Playwright's trace, grouped by User Step |
| `vaadin.testing.browserless.view-packages` | in a Spring test, the `@SpringBootApplication`'s packages and the test's package; otherwise the test's package |
| `vaadin.testing.browserless.route-errors-to-session` | `false`; `true` sends an exception thrown by a listener to the session's `ErrorHandler`, as in a browser |
| `vaadin.testing.playwright.browser` | `chromium` (or `firefox`, `webkit`); the run downloads this browser only, when it isn't installed |
| `vaadin.testing.playwright.headless` | `true` |
| `vaadin.testing.playwright.executable` | Playwright's own browser; a path to a browser skips the download of Playwright's browsers |
| `vaadin.testing.selenium.browser` | `chrome` (or `firefox`) |
| `vaadin.testing.selenium.headless` | `true` |
| `vaadin.testing.selenium.executable` | the installed browser |
| `vaadin.testing.selenium.reuse-browser` | `true`: one browser per run, a fresh tab without cookies or storage per test |
