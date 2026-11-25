# Configuration

Set these as JUnit configuration parameters, in `src/test/resources/junit-platform.properties`, or as system properties with `-D` from Maven, Gradle or the IDE. What the three Testing Tools share is decided in [2. Test setup](../spec/02-test-setup.md#choosing-the-testing-tool); the rest belong to one Testing Tool or to the reports.

> With TestBench on the test classpath, your own `junit-platform.properties` replaces TestBench's (JUnit reads only one and warns about the other), which turns off TestBench's parallel execution. To keep TestBench's file, pass the settings as Surefire `configurationParameters` instead, as [`unified-test-demo/pom.xml`](../../unified-test-demo/pom.xml) does.

| Setting | Default |
|---|---|
| `vaadin.testing.tool` | `browserless` (or `playwright`, `selenium`) |
| `vaadin.testing.base-url` | from the Spring port, else `http://localhost:8080` |
| `vaadin.testing.timeout` | `10s` |
| `vaadin.testing.viewport` | `1280x720`; `[name:]WIDTHxHEIGHT` such as `phone:375x667`: the exact Viewport of every test without `@Viewport`, in a browser ([Viewports](#viewports)) |
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

The Selenium adapter always runs its browser locally *(unreleased)*: it has no remote Selenium grid setting, and it reads none of TestBench's grid parameters (`com.vaadin.testbench.Parameters.gridBrowsers`, `hubHostname`) or Sauce Labs options. A remote grid is on the roadmap ([#645](https://github.com/parttio/unified-testing-tool/issues/645)). Until then, run browser tests where the browser is installed, or on Playwright.

## Viewports

In a browser, every test runs at an **exact** Viewport *(unreleased)*: 1280 × 720 unless the run's `vaadin.testing.viewport` or the test's `@Viewport` says otherwise. A method's `@Viewport` wins over its class's, and it is inherited from base classes. `@Viewport` implies `@RequiresBrowser`.

```java
@Test
@Viewport(width = 375, height = 667, name = "phone")
@DisplayName("On a phone, the menu is behind the drawer toggle")
void onAPhone(UnifiedPage page) {
    page.navigate(MainView.class);
    ...
}
```

`page.setViewport(768, 1024)` changes this window's Viewport mid-test. It is a User Step, and it waits until the application has heard of the new size: Vaadin sends a window resize 300 ms after the browser's last one, so prefer `@Viewport` for the size a test starts at. `page.viewport()` gives the current one. Windows from `openWindow()` and `openAsUser()` start at the test's Viewport.

Only the Viewport is set, not the screen: `screen.width` and `getScreenWidth()` are whatever the browser says. Don't size the browser window through the Escape Hatch; use `setViewport`.

When the Testing Tool can't set an exact Viewport, a test with `@Viewport`, or any test of a run with `vaadin.testing.viewport`, is skipped with the reason. A test on the default 1280 × 720 runs with a warning in its report, and a mid-test `setViewport` fails its User Step. The Selenium adapter sets exact Viewports from [#490](https://github.com/parttio/unified-testing-tool/issues/490) on.
