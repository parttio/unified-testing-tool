# Troubleshooting

Find what your run says, then its likely cause and the fix. For how to read a whole failure, see [Reading reports and failures](reports-and-failures.md); for code that fails now and then, see [Common mistakes](common-mistakes.md).

## A lookup or a check failed

Every Unified failure is a `UnifiedAssertionError`. Its `Found` line says what was there ([The failure message](reports-and-failures.md#the-failure-message)).

### `Found none`

No component matched the lookup.

| Likely cause | Fix |
|---|---|
| The test names it differently from the view: a label, a text, a header title. `Nearby` lists what is there, closest first. | Apply `Did you mean`, by hand or with the [`ApplyDidYouMean` recipe](reports-and-failures.md#applying-it). Write lookups from the view's code, and from the translation files when the view calls `getTranslation(..)`. |
| The application changed by mistake, and the test was right. | Fix the view, not the test. |
| The step that shows it didn't happen: the dialog isn't open, the tab isn't selected. `Nearby` then lists the Near Misses of the missing scope. | Add the step the user takes first, such as the click that opens the dialog. |
| It is in another scope than the lookup searches. | Search from the right container: `dialog.find(..)`, `tabs.content().find(..)` ([Scopes](finding-components.md#scopes)). |
| It is hidden. `Nearby` marks it `(hidden)`. | Do what makes it visible to the user. |
| It is inside a Template (a `LitTemplate`'s `@Id` component, or a web component's shadow root), which no lookup enters by itself. `Nearby` names the Template, such as `in Template <search-bar>`, with the lookup that enters it. | Enter the Template: `page.findTemplate(SearchBar.class).find(..)`, and find by `withId(..)` ([Templates](finding-components.md#templates)). |

When `Nearby` doesn't help, look at the Findable View: what the page held at the failure, as lookups ([The Findable View](reports-and-failures.md#the-findable-view)). In a test, `page.describe()` prints the same.

### `Found 2: …`

Several components matched where the test meant one. Each is listed with where it is.

Add a filter or a scope that names the one the user means, such as the container it's in ([Several matches](finding-components.md#several-matches)). Don't pick one with `all().get(..)`: the order is not something the user sees.

### `Found 1, but disabled`

The component is there, but the user can't use it yet: it is disabled, hidden, read-only, or covered by an open dialog.

Do the step that makes it usable, as the user would: fill the required field, close the dialog. A test that forces the action through the Escape Hatch tests something no user can do.

### `Found` with values seen while waiting

A check timed out. The values are what the component showed while the check waited, such as `“Saving…”, then “Saved Alan Turing”`.

| Likely cause | Fix |
|---|---|
| The application shows something else than the scenario expects. | Decide who is wrong. Change the test only when the new behaviour is intended. |
| The value is right but arrives after the timeout, for work that is slow on purpose. | Give that one Wrapper a longer timeout with `withTimeout(..)`, and say why in a comment ([Timeouts](assertions-and-waiting.md#timeouts)). |

### It passes on Browserless and fails in a browser

| Likely cause | Fix |
|---|---|
| A getter with `assertEquals`: it reads once, and in a browser the value arrives a moment later. | `assertThat(..)` ([Checking with a getter](common-mistakes.md#checking-with-a-getter)). |
| The test relies on client-side behaviour that Browserless simulates or doesn't have. | See [Known limitations](#known-limitations-by-testing-tool). |
| The two runs take different steps. | Run the browser run with the Browserless run's evidence, and read its *Compared with another Testing Tool* section ([Compared with another Testing Tool](reports-and-failures.md#compared-with-another-testing-tool)). |

## The test fails before its first step

These fail every test of a run the same way, with a message that names the cause.

| The message starts with | Cause | Fix |
|---|---|---|
| `No adapter for vaadin.testing.tool=…` | The run asks for a Testing Tool whose adapter isn't on the test classpath. | Add the test dependency `org.vaadin.addons:unified-test-<tool>`. Its version is in `unified-test-bom` ([Getting started](getting-started.md#1-add-the-dependencies)). |
| `This is a Spring test, but com.vaadin:browserless-test-spring is not on the test classpath` | A `@UnifiedSpringBootTest` on Browserless needs it, and the Browserless adapter doesn't bring it. | Add the test dependency `com.vaadin:browserless-test-spring`; the BOM sets its version. |
| `The Unified API needs Vaadin 25.3 or newer` | The application runs on an older Vaadin. | Move the application to Vaadin 25.3. |
| `Could not install Playwright's …` | Playwright couldn't download its browser, for example behind a proxy or on an offline CI agent. | Install it with the command in the message, or point `vaadin.testing.playwright.executable` at an installed browser ([Configuration](configuration.md)). |
| `openAsUser needs …` | `openAsUser(..)` logs in through Spring Security. | Use `@UnifiedSpringBootTest`, with Spring Security in the application. Without it, use `openNewSession()` and sign in through the application's own login ([A second user without Spring Security](navigation-and-users.md#a-second-user-without-spring-security)). |

Two more, without a message of the Unified API:

- **A Selenium run fails on TestBench's license.** TestBench checks its license when it loads. Give the run a TestBench license, such as the `VAADIN_OFFLINE_KEY` environment variable on CI. Browserless and Playwright runs never load TestBench, even with the Selenium adapter on the classpath.
- **A browser run of an application without Spring Boot can't connect.** The Unified API never starts a server. Start the application before the tests, and set `vaadin.testing.base-url` if it isn't at `http://localhost:8080` ([Getting started](getting-started.md#without-spring-boot)).

## A test is skipped

| The reason says | Why | To run it |
|---|---|---|
| `Requires [PLAYWRIGHT], this run uses …` | The test has `@RequiresTestingTool`, for another Testing Tool than the run's. | Run with `-Dvaadin.testing.tool=playwright`, or the tool it names. |
| `Requires a real browser, this run uses …` | The test has `@RequiresBrowser`, and the run is on Browserless. | Run on Playwright or Selenium. |

A skip is not a failure: the test needs something this Testing Tool doesn't have ([When a test needs one Testing Tool](tool-specific.md)).

## A call fails on one Testing Tool only

| The message starts with | Fix |
|---|---|
| `Browserless has no CSS; use @RequiresBrowser` | A CSS check needs a browser: add `@RequiresBrowser`. |
| `Browserless has no rendering, so no visible text` | Use `getText()`, or add `@RequiresBrowser`. |
| `Browserless has no screenshots` | Add `@RequiresBrowser`. |
| `Searching inside row details is not available on Browserless` | Toggling and checking details works everywhere. For a search inside them, add `@RequiresBrowser`. |

## A style rule fails

| Rule | Fix |
|---|---|
| Scenario sentence | `@DisplayName("<what the user does>")` on the test method, or `@DisplayNameGeneration` on the class. |
| No raw waits | Remove the sleep or the Testing Tool's wait: `assertThat(..)` retries, `page.waitUntil(..)` waits for anything else. In Escape Hatch code, a Testing Tool's own wait passes under `@RequiresTestingTool` for that Testing Tool. |
| Testing Tool code needs its Capability | `@RequiresTestingTool(..)` on the test or its class, `tryUnwrap(..)` for an extra check, or the Unified API. |

Each failure names the test, its line and the fix. For a suite with many existing violations, freeze the rules and fix them over time ([Checking the style](style-rules.md#suites-with-existing-violations)).

## A setting has no effect

With TestBench on the test classpath, your own `junit-platform.properties` replaces TestBench's (JUnit reads only one), or the other way round. Pass the settings as Surefire `configurationParameters` or system properties instead ([Configuration](configuration.md)).

## Known limitations by Testing Tool

What doesn't work the same on every Testing Tool yet. The full list, with the gaps found in each Testing Tool, is the specification's [Appendix: gaps](../spec/appendix-gaps.md).

| Area | State |
|---|---|
| Browserless: searching inside Grid row details (`row.details()`) | throws; toggling and checking details works |
| Browserless: `page.reload()`, `pressKey`, `dispatchEvent` | simulated: events bubble from the target to the UI; `page.pressKey` goes to the focused component or the open dialog; client-side behaviour (e.g. typing text, Tab moving focus) doesn't happen ([#66](https://github.com/parttio/unified-testing-tool/issues/66)) |
| Browserless: signing in through a login form with an `action` (a form post, such as Spring Security's `/login`) | `UnifiedLogin.login(..)` fails: Browserless fires the component's `LoginEvent` and can't submit a form post. Add `@RequiresBrowser`, or sign in with `@WithMockUser` ([#582](https://github.com/parttio/unified-testing-tool/issues/582)) |
| Browserless: `Page.setLocation(..)` | not followed: the UI stays on the view, so a step after a tab or button that sends the user to another page this way sees the old one. Add `@RequiresBrowser` ([#554](https://github.com/parttio/unified-testing-tool/issues/554)) |
| Browserless: deselecting one item of a MultiSelectComboBox | done on the server, not as from the client ([#61](https://github.com/parttio/unified-testing-tool/issues/61)) |
| Browser Testing Tools: a lookup for a component class that shares its tag with another and renders nothing that tells them apart (`Grid` and `TreeGrid`, `Div` and `FlexLayout`, `ListBox` and `MultiSelectListBox`, …) | also finds the other class's components, where Browserless finds the class only. Narrow the lookup with a filter or a scope. `RouterLink` and `Anchor` are told apart ([full list](../spec/appendix-gaps.md#outside-the-testing-tools); [#562](https://github.com/parttio/unified-testing-tool/issues/562)) |
| Selenium: a combo box's `getItems()` | the drop-down's first page only (50 items by default; [#127](https://github.com/parttio/unified-testing-tool/issues/127)) |
| The browser's console errors (under each User Step, and in `hasNoErrors()`) | Playwright, and Selenium with Chrome; Firefox on Selenium has no browser log ([#316](https://github.com/parttio/unified-testing-tool/issues/316)) |
