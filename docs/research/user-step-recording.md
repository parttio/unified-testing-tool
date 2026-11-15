# How are User Steps recorded today, and where would a report hook in?

Research for [#209](https://github.com/parttio/unified-testing-tool/issues/209), on the map [#193](https://github.com/parttio/unified-testing-tool/issues/193) "Make Unified UI Tests read like what the user does". The map chose style B (lazy Wrappers without `single()`) and a **User Step** report. The prototype's draft glossary entry defines a User Step as "one action or check a Unified UI Test performs, stated the way a user would describe it ("fill text field 'City' with 'Turku'"), as the Unified API reports it" (`GLOSSARY.md` L63–65 on `prototype/readable-styles`).

Researched 2026-11-15 against `origin/main` at `1359651`, JUnit Jupiter **6.0.3**, Surefire **3.5.4** (`pom.xml` L89–90) and Playwright Java **1.63.0**. All paths are relative to the repo root. `api/…` stands for `unified-test-api/src/main/java/com/vaadin/testing/unified/…`.

## Answer in short

- **Nothing records User Steps today.** The only per-test output is the failure evidence that `UnifiedExtension.afterEach` writes once, after a failed test: a full-page screenshot on the browsers and the component tree on Browserless. The report entry the extension publishes for it **never reaches Surefire's XML**, because Surefire 3.5.4's JUnit Platform listener ignores report entries.
- **Almost all of it can be recorded in the core, in two places, with no adapter changes.** The first place is the Wrapper proxy's `WrapperHandler.invoke` (`api/internal/Wrappers.java` L200–246). It sees the Wrapper type, the method, the arguments and, through `LazyElement → ElementSource`, the component type, the filters and the parent scope. The second is `UnifiedAssertions.check` (`api/assertion/UnifiedAssertions.java` L72), the one funnel every retrying assertion goes through. Navigation and page-level keys need small hooks in `UnifiedPageImpl`. Every timeout runs through `TestRun.poll` (`api/internal/TestRun.java` L21–35), so that is the one place where a failure can be stamped with "User Step n of m".
- **Missed by those hooks:** grid rows and columns (plain records that call `GridAccess` directly), `UnifiedGrid.selectAll()`/`deselectAll()` (no `act()`), raw Unified Element calls that skip `act()` (`setProperty`, `blur`, `dispatchEvent`), everything after an Escape Hatch `unwrap`, `waitUntil(lambda)` (opaque), `where(predicate)` filters (opaque), and plain JUnit assertions on getters. `findComponent(X.class)` *is* covered, because it goes through the same proxies, but it reports a Flow class name rather than a user word.
- **Evidence per User Step is cheap on Browserless** (an in-memory tree print) **and on Playwright** (`Tracing` with `group(name)` per User Step, or `page.ariaSnapshot()` as text). On Selenium a screenshot costs a WebDriver round trip and an image per step, so it suits failures, not every step.
- **Near misses can be computed in the core for every Testing Tool.** Re-run the failed `QuerySpec` without its filters (same type, tag and parent scope) and read the property the failed filter matched on: `label`, text, `placeholder`, `data-testid`. Accessible name is the exception: `UnifiedElement` has no method for it, so it needs a small SPI addition. Selection, tab and grid-column lookups already list their near misses in today's messages.

---

## 1. What the Unified API knows at run time

### Verified facts

| # | Fact | Source |
|---|---|---|
| K1 | A Wrapper is a `java.lang.reflect.Proxy`. `WrapperHandler(Class<?> wrapperType, LazyElement element)` handles `element`, `unwrap`, `tryUnwrap`, `withTimeout`, `find`, `findComponent` and `toString` itself. Every other method is a default method run with `InvocationHandler.invokeDefault(proxy, method, args)`. | `api/internal/Wrappers.java` L200–246; ADR 0003 |
| K2 | A query is a proxy too (`QueryHandler`). Each `withX(..)` call becomes a `spi.Filter(kind, name, value, exact)` on an immutable `ElementSource`. `withProperty`, `visible()` and `where(..)` become core predicates, kept with their display names (`"where(..)"`). | `Wrappers.java` L106–167 |
| K3 | `ElementSource` carries `run`, `wrapperName` (the Wrapper's simple name, or `"findComponent(Button)"`), `componentType`, `tag`, `filters`, `coreFilterNames` and `parent` (a `LazyElement`, or null for the page). `describe()` renders API terms, e.g. `UnifiedTextField.withLabel("Email") inside UnifiedDialog…`. | `api/internal/ElementSource.java` L15–55; `Wrappers.java` L48, L57 |
| K4 | `@UnifiedComponent(selector = …)` adds its filters to every query of that Wrapper (`.class`, `#id`, `[attr="v"]`, `[theme~="v"]`). In a report they are noise ("how the Wrapper tells itself apart"), not something the user named. | `api/internal/WrapperSelector.java`; `Wrappers.java` L46–47 |
| K5 | `LazyElement.act(action, body)` is the Auto-Wait path for user actions. It waits for exactly one match that is visible and enabled, runs `body`, and retries on `StaleElementException`. `click`, `setValueAsUser`, `focus` and `pressKey` use it. `setProperty`, `blur` and `dispatchEvent` go through `read(..)` instead, without Auto-Wait. The action strings are already close to user words ("click", "select \"X\" in", "toggle"), but `setValueAsUser` says "set the value of" and leaves out the value. | `api/internal/LazyElement.java` L66–76, L99–105 |
| K6 | Wrapper actions that are not a plain `element().x()` call `act` directly: selection (`SelectionSupport` L19, L35, L49), tabs (`TabSheetSupport` L23), menu bar (`UnifiedMenuBar` L31), details (`UnifiedDetails` L34), upload (`UnifiedUpload` L23) and date-time picker typing (`UnifiedDateTimePicker` L33). | `grep -rn '\.act('` over `unified-test-api` |
| K7 | Default methods call each other through the proxy. `UnifiedCheckbox.check()` calls `setChecked(true)`. `UnifiedDetails.open()` calls `isOpen()` and then `toggle()`. `UnifiedMultiSelectComboBox.selectItems(a, b)` calls `SelectionSupport.select` once per label. So a single user call reaches the handler several times, nested. | `component/UnifiedCheckbox.java` L16–18, `UnifiedDetails.java` L32–34, `UnifiedMultiSelectComboBox.java` L22–26 |
| K8 | Grid rows and columns are **not** proxies. `GridSupport.Row` and `GridSupport.Column` are records whose `click`, `select`, `toggleDetails` and `sort` call `GridAccess` directly, with no `act()` and so no Auto-Wait for visibility. A row's `description` is only `rows().withCellText("Name", "Alan Turing").single()`, without the grid. `UnifiedGrid.selectAll()`/`deselectAll()` go through the proxy but call `GridAccess` without `act()`. | `api/internal/grid/GridSupport.java` L189–223 (Row), L226–280 (Column), L159–167 (`single()`); `component/UnifiedGrid.java` L40–41 |
| K9 | Every retrying assertion ends in `UnifiedAssertions.check(run, subject, expectation, expected, actual, ok)`. For Wrappers, `subject` is `element.describe()` (API terms) and `expectation` is in words ("to have value", "to be checked"). `WrapperAssert` holds both the `wrapper` and its `LazyElement`. While polling, the assertion calls the Wrapper's getters, which go through the proxy (K1). | `api/assertion/UnifiedAssertions.java` L72–81, L111–128 |
| K10 | `TestRun(tool, session, timeout)` is an immutable record. `poll` is the single place where every timeout becomes an `AssertionError("… (waited N ms)")`, for lookups, Auto-Wait, assertions, `waitUntil` and grid `single()`. `withTimeout`, `openWindow` and `openAsUser` create new `TestRun`s. | `api/internal/TestRun.java` L9–49; `UnifiedPageImpl.java` L75–81; `Wrappers.java` L212 |
| K11 | `UnifiedPageImpl.navigate/reload/pressKey` call the `AdapterSession` directly, without any proxy. `waitUntil(BooleanSupplier)` polls an opaque lambda. | `api/internal/UnifiedPageImpl.java` L37–72 |
| K12 | The Escape Hatch is `unwrap/tryUnwrap` on a Wrapper, a Unified Element or the page. It returns the tool's own object (Playwright `Page`, a TestBench element, `BrowserlessUIContext`, a Flow component). From then on no Unified API code runs. `@ToolImplementation` overrides can use it inside a default method (demo: `UnifiedBadgeBrowserless.getCount()` → `unwrap(Badge.class)`). | `api/HasEscapeHatch.java`; `Wrappers.java` L207–210; `unified-test-demo/src/main/java/demo/addon/testing/UnifiedBadgeBrowserless.java` |
| K13 | The extension makes one `AdapterSession` per test in `resolveParameter`. It keeps the session in the JUnit `ExtensionContext.Store` and closes it in `afterEach`. Nothing else (no step list, no timing) is kept per test. | `api/internal/UnifiedExtension.java` L71–95 |

### Inferences

- **User-term rendering is mechanical for the Core Set.** The component kind is the Wrapper's simple name without `Unified`, split on capitals: `UnifiedTextField` → "text field", `UnifiedMultiSelectComboBox` → "multi select combo box". For `findComponent(Button)` it is the Flow class's simple name. Add-on Wrappers get the same treatment ("rating"). The name comes from the first naming filter: `LABEL`, `TEXT` and `ACCESSIBLE_NAME` → `"Email"`, `PLACEHOLDER` → `with placeholder "…"`, `TEST_ID` → `test id "billing"`. Filters from `@UnifiedComponent(selector)` (K4) are left out. The scope comes from `parent` → `in dialog "Edit person"`.
- **The verb needs a table, plus a fallback.** Wrapper methods carry no verb metadata. A core table covers the Core Set: `setValue` → "fill … with", `click` → "click", `selectItem` → "choose … in", `check`/`uncheck`, `selectTab`, `clickItem` → "choose File › Export in menu bar", `upload`, `toggle`/`open`/`close`, `clear`. For add-on methods the fallback is the camel-case split of the method name plus its arguments (`setRating(4)` → "set rating 4 on rating …"). An optional annotation such as `@UserStep("rate {0} stars")` would let add-on authors do better. That is new API and belongs to the map, not this note.
- **Values are the arguments.** They are in `args` at the proxy (K1). The value must be masked for fields that hold secrets: a login test's password should not land in a CI log. No Core Set Wrapper exists for `PasswordField` today, so the rule has to be by component type (`com.vaadin.flow.component.textfield.PasswordField`) once one does.

## 2. Can it be recorded in one place?

**Nearly.** Two core recording points, plus thin hooks, cover the Core Set:

1. **Actions: `WrapperHandler.invoke`, outermost call only.** Open a *frame* on entry when no frame is open on this thread for this test, run the method, and close the frame on exit. Nested proxy calls (K7) and getters called while an assertion polls (K9) then fall inside the outer frame. The frame becomes a User Step if either (a) a `LazyElement.act` ran inside it, or (b) the method is a `void` default method that is not a getter (`get*`/`is*`/`has*`). Rule (a) catches every Core Set action (K5, K6). Rule (b) catches `UnifiedGrid.selectAll()` and `@ToolImplementation` overrides that act through the Escape Hatch. A frame that matches neither, which is a plain getter, is discarded. Getters run immediately and are not User Steps.
2. **Checks: `UnifiedAssertions.check`.** It opens its own frame, so the getter calls during polling (K9) are swallowed, and records "see text field "Name" with value "Alan M. Turing"" from the `WrapperAssert`'s Wrapper and `LazyElement` (K9). `PageAssert` records "see page People".
3. **Thin hooks:** `UnifiedPageImpl.navigate` ("open People", from the view's simple name or route), `reload`, `pressKey`, `openWindow` and `openAsUser` ("open a window as user 'bob'"). Grid `Row`/`Column` actions in `GridSupport` (K8) need explicit calls to the recorder, because they are not proxies.
4. **Failure stamping: `TestRun.poll`.** When it builds the `AssertionError` (K10), it prefixes "User Step n of m: …" and adds near misses (§4). One place covers lookups, Auto-Wait, assertions and grid `single()`.

The recorder can live on `TestRun`, as one more record component, so it travels through `withTimeout`, `openWindow` and `openAsUser` (K10). That keeps one step log per test across windows, with the window or user as a step attribute. The extension creates the recorder in `resolveParameter` and also puts it in the `Store`, so `afterEach` can publish it (K13). Style B changes nothing here: with no `single()`, the query proxy *is* the Wrapper, and its actions still end in `act` on the same `ElementSource`.

### What the hooks above miss, by call kind

| Call | Recorded? | Why / fix |
|---|---|---|
| Wrapper action (Core Set and add-on) | yes | Proxy frame (point 1). |
| Retrying assertion `assertThat(..)` | yes | `check` (point 2). |
| `findComponent(X.class)…click()` | yes, as "click Button "Save"" | Same proxies (K1, K2). The kind is the Flow class name, not a Wrapper word. |
| `all().get(i)` | yes | `describe()` says `.all().get(i)`. Render it as "the 2nd text field". |
| Unified Element via `wrapper.element().click()` | partly | `act` runs without an outer frame. Record from `act` itself as a fallback, with `ElementSource.wrapperName` and the filters. `setProperty`, `blur` and `dispatchEvent` skip `act` (K5), so they need their own hook or stay unrecorded. |
| Grid row/column (`click`, `select`, `sort`, `toggleDetails`) | **no** | Plain records calling `GridAccess` (K8). Add recorder calls in `GridSupport.Row`/`Column`, and give `Row.description` the grid's description. |
| `UnifiedGrid.selectAll()/deselectAll()` | via rule (b) only | No `act()` (K8). Better: route through `act`, which also adds Auto-Wait. |
| `where(predicate)` filter | opaque | Reported as `where(..)` (K2). Nothing better is knowable. |
| `page.waitUntil(() -> …)` | opaque | Lambda (K11). Record "wait until a condition holds" with its duration. |
| Escape Hatch (`unwrap`, `BrowserlessEscapeHatch`, `TestBenchEscapeHatch`) | **no** after the call | Record one marker step, "leave the Unified API (Playwright Page)". Nothing inside is visible. |
| `assertEquals(…, field.getValue())` | **no** | A getter, then JUnit. Only the failure's stack trace shows it. |

## 3. Evidence per Testing Tool

### Verified facts

| # | Fact | Source |
|---|---|---|
| E1 | `AdapterSession.writeFailureEvidence(directory, name)` is the only evidence SPI. It is called once, from `afterEach`, only when the test failed. | `api/spi/AdapterSession.java` L36–42; `UnifiedExtension.java` L85–113 |
| E2 | Browserless writes `<name>.tree.txt`: `Location: /…`, then `PrettyPrintTreeKt.toPrettyTree(ui)`, the same as Browserless's own `TreeOnFailureExtension`. | `unified-test-browserless/…/BrowserlessSession.java` L316–326 |
| E3 | Playwright writes a full-page `<name>.png` (`page.screenshot(…setFullPage(true))`). | `unified-test-playwright/…/PlaywrightSession.java` L293–300 |
| E4 | Selenium writes `<name>.png` from `driver.getScreenshotAs(OutputType.BYTES)`. That is the viewport only. | `unified-test-selenium/…/SeleniumSession.java` L318–323 |
| E5 | The directory is `vaadin.testing.evidence-dir`, by default `target/surefire-reports/unified-evidence`, so CI's `**/target/surefire-reports/` upload includes it. | `api/internal/Settings.java` L17–22; `.github/workflows/tests.yml` (each job's "Upload test reports") |
| E6 | `FailureEvidenceTest` runs a failing test through the JUnit Launcher and checks two things: the failure carries a suppressed `FailureEvidence` naming the files, and the files exist (PNG magic bytes in a browser, `Location: /profile` and the field's value in the tree on Browserless). | `unified-test-demo/src/test/java/demo/FailureEvidenceTest.java` L40–67 |
| E7 | Playwright Java 1.63.0 has `Tracing.start/stop`, `Tracing.group(String)` (`AutoCloseable`) and `groupEnd()`, `Page.content()` and `Page.ariaSnapshot()`/`Locator.ariaSnapshot()`. | `javap` on `com.microsoft.playwright:playwright:1.63.0` |

### Per-step cost (inferences)

| Testing Tool | Evidence available | Per User Step? |
|---|---|---|
| Browserless | Component tree (E2), in-JVM, no I/O until written | **Yes, cheap.** Keep the tree text per step in memory and write it only on failure, or for the step that failed plus the one before. |
| Playwright | Screenshot (E3), `page.content()` HTML, `ariaSnapshot()` text, Playwright trace (E7) | **Yes.** Start `context.tracing()` with screenshots and snapshots and wrap each User Step in `tracing.group("fill text field "Email" …")`. The trace viewer then shows our steps, with the DOM snapshots Playwright takes for every locator action anyway. Save the trace only on failure. `ariaSnapshot()` is a cheap text form for an agent. |
| Selenium | Screenshot (E4), `getPageSource()` (serialized DOM, light DOM only) | **Screenshot: failure only** (a WebDriver round trip and a PNG each). `getPageSource()` per step is affordable. Vaadin field labels are light-DOM `<label slot="label">` (`docs/research/copilot-recorder.md`, F8), so labels survive in it. |

The run report mock-up draws "the component tree at that moment, drawn the same way" on Browserless and a screenshot in a browser. That fits: per-step evidence becomes a new optional SPI method next to E1, e.g. `Optional<StepSnapshot> snapshot()`, defaulting to empty, so Selenium can return nothing per step and keep failure-only screenshots.

## 4. Reaching JUnit, Surefire and the GitHub job summary

### Verified facts

| # | Fact | Source |
|---|---|---|
| R1 | JUnit Jupiter 6.0.3 `ExtensionContext` has `publishReportEntry(Map)`, `publishReportEntry(key, value)`, `publishFile(name, MediaType, ThrowingConsumer<Path>)` and `publishDirectory(name, ThrowingConsumer<Path>)`. `TestReporter` has the same, as `publishEntry`/`publishFile`/`publishDirectory`. | `javap` on `junit-jupiter-api-6.0.3.jar` |
| R2 | The extension already calls `context.publishReportEntry("unified.failure-evidence", list)`. It also adds a suppressed `FailureEvidence` exception, whose message is the file list. | `UnifiedExtension.java` L104–109 |
| R3 | Surefire 3.5.4's `RunListenerAdapter` (its JUnit Platform `TestExecutionListener`) overrides `executionStarted/Finished/Skipped` and `testPlanExecutionStarted/Finished`, but **not** `reportingEntryPublished` or `fileEntryPublished`. Report entries and published files therefore never reach Surefire's XML or console. The suppressed exception does, inside the stack trace. | `javap -p` on `surefire-junit-platform-3.5.4.jar` |
| R4 | `junit-platform-reporting` 6.0.3 registers `OpenTestReportGeneratingListener` through `META-INF/services/org.junit.platform.launcher.TestExecutionListener`. It is enabled by `junit.platform.reporting.open.xml.enabled` and implements both `reportingEntryPublished` and `fileEntryPublished`, writing them into the Open Test Reporting XML under the platform's output directory. | `junit-platform-reporting-6.0.3.jar` from Maven Central: service file, `javap`, constant pool |
| R5 | The repo's CI uploads `**/target/surefire-reports/` only `if: failure()`, and writes nothing to the job summary. | `.github/workflows/tests.yml` |

### Inferences: three outputs, one source

1. **Files under `target/` are the dependable channel.** At `afterEach`, write `<evidence-dir>/<Class>/<method>/user-steps.txt`, the plain-text failure block, and the step evidence. Also write a machine-readable `user-steps.json` that the PR summary and living docs are generated from. The prototype's failure block already names this layout (`…/unified-evidence/EditPersonTest/editsAPersonInTheDialog/ screenshot.png page.html user-steps.txt`). It is uploaded with the Surefire reports (E5), with no build change.
2. **JUnit report entries and `publishFile`** for tools that read them: IDEs, the Console Launcher, and Open Test Reporting when a project adds `junit-platform-reporting` and sets `junit.platform.reporting.open.xml.enabled=true` (R4). That is cheap to add next to (1). But under plain Surefire it is invisible (R3), so it cannot be the only channel, and the existing `unified.failure-evidence` entry is already a dead letter there.
3. **The CI log** needs stdout. Surefire captures each test's stdout into `<testcase><system-out>`, and with `redirectTestOutputToFile=false` (the default) it also shows on the console for failing tests. Printing the User Step list for a failed test, or for every test behind a setting such as `vaadin.testing.steps=print`, gives the "CI log reads as a script" output from the prototype.
4. **The GitHub job summary** is a file named by `$GITHUB_STEP_SUMMARY`. Two routes. (a) The Unified API appends Markdown itself when that variable is set, from a JUnit `LauncherSessionListener` or `TestExecutionListener` registered through `ServiceLoader`, so it runs once per run, not per test. (b) A workflow step runs `cat target/unified-steps/summary.md >> "$GITHUB_STEP_SUMMARY"` with `if: always()`. Route (b) keeps CI knowledge out of the library and works on any CI that can show a Markdown file. Either way the summary is built from the per-test `user-steps.json`, not from Surefire's XML.

## 5. Near misses when a lookup finds nothing

### Verified facts

| # | Fact | Source |
|---|---|---|
| N1 | Today's lookup failure says only "No component matches `<describe()>`" or "N components match …, expected exactly one", then "(waited N ms)". It lists neither near misses nor the matches. | `LazyElement.java` L41–55; `TestRun.java` L35 |
| N2 | Some lookups already list near misses. Selection: "No item "X" in … ; items: [..]". Tabs: "No tab "X" … ; tabs: [..]". Grid columns: "No column with header "X"; headers are [..]". | `SelectionSupport.java` L22–24; `TabSheetSupport.java` L26–27; `GridSupport.java` L69–75 |
| N3 | Every adapter's `find(QuerySpec)` takes type, tag, filters and parent and returns what matches *now*, without waiting. Browserless walks the component tree (`BrowserlessSession` L131–157). Playwright uses Dramafinder for the types it has, otherwise CSS plus one in-page `MATCH` script (`PlaywrightSession` L91–167). Selenium uses CSS plus one in-page `MATCH` script per root (`SeleniumSession` L137ff). | adapter sources |
| N4 | `UnifiedElement` exposes `getProperty`, `getAttribute`, `getText` and `isVisible` on every Testing Tool, but **no accessible name**. Each adapter computes one privately for the `ACCESSIBLE_NAME` filter: `BrowserlessElement.accessibleName(Component)` (package-private, L309), and `BrowserQueries.ACCESSIBLE_NAME` (JS) on Playwright and Selenium. | `api/UnifiedElement.java`; `BrowserlessElement.java` L308–320; `api/internal/BrowserQueries.java` L145–160 |
| N5 | On the browsers, every `getProperty` on a `PlaywrightElement`/`SeleniumElement` is one round trip to the browser. | `PlaywrightElement.java` L87; `SeleniumElement.java` L128 |

### What each Testing Tool can list (inferences)

The core can compute near misses on failure only, so they cost nothing on passing runs. Take the failed `ElementSource`, drop the user-facing filters (keep the `@UnifiedComponent(selector)` ones, K4), resolve once (N3), and read for each candidate the value the dropped filter matched on: `label` property for `LABEL`, `getText()` for `TEXT`, `placeholder` for `PLACEHOLDER`, `data-testid` for `TEST_ID`. Mark hidden candidates as hidden. For "N match, expected one", list the matches the same way, with their parent scope.

| | Same type, same scope | Accessible name | Other kinds nearby ("combo box "Role"") | Grid rows |
|---|---|---|---|---|
| Browserless | Yes, in-JVM tree walk. Free. | Yes, if `accessibleName` is exposed through the SPI (N4). | Yes. Walk again for a fixed list of field Wrapper types, or print the tree (E2). | Yes, `GridAccess.columnTexts` |
| Playwright | Yes. One `find` plus one `getProperty` per candidate (N5), or one `evaluateAll` if the adapter offers a batch read. | Same SPI gap; JS already exists (N4). | Yes. Same as Browserless, or `ariaSnapshot()` of the scope (E7) as a text dump. | Yes, `columnTexts` (adapters may batch it) |
| Selenium | Yes. One `find` plus one JS call per candidate, or one batched `executeScript`. | Same SPI gap; JS already exists. | Yes, by repeated `find`. No aria snapshot. | Yes, `columnTexts` |

If the *parent* scope is the thing missing, `ElementSource.resolve` fails in `parent.concrete()` (`ElementSource.java` L38). The near misses should then be computed one level up, at the scope ("no container with test id "billing"; containers there: …"), never for the child. The suggested call ("Did you mean `dialog.find(UnifiedTextField.class).withLabel("E-mail")`") follows from the best near miss. Render it from the same `ElementSource`, with one filter value replaced, through `Filter.toString()` (`api/spi/Filter.java` L20–35), which already prints `withLabel("…")`.

## 6. Sketch of the recording point

```
api/internal/StepRecorder.java      new: per-test list of UserStep(index, verb, kind, name, scope, value,
                                    window, status, duration, evidence); thread-confined frame stack
api/internal/UserStepText.java      new: ElementSource + Method + args -> "fill text field "Email" with "…""
                                    (kind from wrapperName/componentType, name from first naming Filter,
                                    scope from parent, verb table + camel-case fallback, value masking)
api/internal/TestRun.java           + StepRecorder component; poll() prefixes "User Step n of m" and
                                    asks NearMisses on lookup failures
api/internal/Wrappers.java          WrapperHandler.invoke: open/close outermost frame around invokeDefault
api/internal/LazyElement.java       act(): mark the open frame as an action (or record a bare
                                    Unified Element step when no frame is open); pass the value through
api/assertion/UnifiedAssertions.java check(): one "see …" step per assertion, frame around the poll
api/internal/UnifiedPageImpl.java   navigate/reload/pressKey/openWindow/openAsUser/waitUntil: page steps
api/internal/grid/GridSupport.java  Row/Column actions: explicit recorder calls; Row description names the grid
api/internal/NearMisses.java        new: re-resolve ElementSource without user filters, read the matched value
api/internal/UnifiedExtension.java  create the recorder in resolveParameter, store it; afterEach writes
                                    user-steps.txt/json, prints on failure, publishReportEntry + publishFile
api/spi/AdapterSession.java         + default Optional<StepSnapshot> snapshot() (per-step evidence)
api/UnifiedElement.java or spi      + accessible name read (for near misses on ACCESSIBLE_NAME)
```

## 7. Gaps per Testing Tool

**All (core):**
- Grid row/column actions and `selectAll`/`deselectAll` are invisible to the proxy hook (K8).
- `setProperty`, `blur` and `dispatchEvent` skip `act` (K5).
- The Escape Hatch, `where(..)`, `waitUntil(..)` and JUnit assertions on getters are opaque (§2 table).
- There is no verb metadata on Wrapper methods, and no secret masking.
- Lookup failures list no near misses or matches (N1).
- No accessible-name read on `UnifiedElement` (N4).
- The `unified.failure-evidence` report entry is lost under Surefire (R3).
- CI uploads reports only on failure and writes no job summary (R5).

**Browserless:**
- Nothing tool-specific blocks recording.
- Per-step evidence is a tree print (cheap).
- `accessibleName` is package-private in `BrowserlessElement` and needs exposing through the SPI.
- Text values are server-side: a Badge whose text is set by client JS reads empty (the reason `UnifiedBadgeBrowserless` exists), so a report can show a different value than a browser would.

**Playwright:**
- Failure evidence is a screenshot only. `page.content()` and `ariaSnapshot()` would add text evidence an agent can read, and tracing with `group(..)` per User Step gives the per-step run report almost for free (E7).
- Each per-candidate near-miss read is a round trip unless batched (N5).
- For types it covers, finding goes through Dramafinder: a filter-less re-query works, but its "Containing" variants and theme or attribute handling differ (`PlaywrightSession` L121–145). Near misses should therefore read values through `UnifiedElement`, not through Dramafinder filters.

**Selenium:**
- The screenshot is the viewport only (E4), and costs too much per step. Use `getPageSource()` (light DOM) for per-step text evidence and keep screenshots for the failed step.
- There is no aria snapshot.
- Near-miss reads are one `executeScript` per candidate unless batched.
