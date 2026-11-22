# Debugging a failing test: what other tools offer, and what the Unified reports still lack

Research for [#501](https://github.com/parttio/unified-testing-tool/issues/501), part of the roadmap map [#499](https://github.com/parttio/unified-testing-tool/issues/499). It builds on [web-framework-testing-landscape.md](web-framework-testing-landscape.md) (cited as "landscape H2", "landscape AI3", …) and [agentic-testing-environment-roadmap.md](agentic-testing-environment-roadmap.md) (cited as "roadmap N1", "roadmap C4", …) and does not repeat them: Playwright MCP's tools (roadmap N1), Chrome DevTools MCP as a whole (roadmap N9), Phoenix `open_browser/2` (roadmap N13), Browserless's `TreeOnFailureExtension` (roadmap N7), Playwright Test's `--last-failed` and `--ui` flags (roadmap N2) and the trace viewer's before/after snapshots (landscape H2) are taken as known.

**Question.** Once a UI test has failed, what do other tools give a developer or an agent to find out why, and how do the Unified API's reports compare? Which debugging gaps could only this project close?

Researched 2026-11-22 against `main` at `39411f6`. External sources are pinned to a commit where they live on GitHub (`microsoft/playwright` @ `0492267`, `microsoft/playwright-mcp` @ `2337aff`, `ChromeDevTools/chrome-devtools-mcp` @ `ab911a6`, `cypress-io/cypress-documentation` @ `3110e3a`, `vaadin/flow` @ `2e9af17`); other pages were read on that day and are not pinned.

Labels: a **fact** has a source (a path in this repo, or a URL). **[opinion]** and the sections titled *Inferences* are this note's reasoning.

## Answer in short

1. **For reading a failure after the fact, the Unified reports are already as good as the others, and better in text.** The failure message says what was expected, what was there and what to write instead (Near Misses, `Did you mean`); the Findable View shows the page as lookups; the application's log lines sit under the User Step that caused them; the comparison with another Testing Tool says where two runs part. No other tool checked puts the server's log under the test's step, and none has a text view of the page in the test's own terms. The trace viewer (Playwright only) and per-step snapshots cover "what did the page look like".
2. **What is missing is everything *live*.** The others let a developer or an agent stop the test at the failure or at a chosen step and look at the page while it is still there: Playwright's Inspector and `page.pause()` (Java too), the healer agent's `test_debug` that "pauses on error" so the agent can take a snapshot and try locators, Cypress's `cy.pause()` and its new `cypress tap` CLI that lets an agent pin a past command's snapshot and query the DOM, Pest's `--debug`. The Unified API has none of this: no debug mode, no pause, no hold-on-failure, no way to try a lookup against the failed page. Watch mode and UI mode exist only in the JS Test Runners.
3. **The gaps only this project can close come from running one scenario on Browserless and in a browser.** A browser failure can be reproduced on Browserless in milliseconds, where a JVM debugger steps from the User Step straight into the view's listener; the parting step of two runs can be shown side by side (screenshot next to component tree and Findable View); and the Browserless state can be opened in a browser (Phoenix's `open_browser`, which no Java tool has). Replay.io-style recording is out of reach and not worth it here [opinion].

**Candidate features** [opinion], detail in [Candidate features](#candidate-features):

| # | Candidate | Who gains most | Benefit for "debug failing tests" | Effort |
|---|---|---|---|---|
| F1 | [Rerun lines and rerun-failed](#f1-rerun-lines-in-the-failure-and-a-rerun-of-the-failed-scenarios) | agent, developer | High | S |
| F2 | [Debug mode: pause on failure or at a User Step](#f2-debug-mode-pause-on-failure-or-at-a-user-step) | developer (any) | High | M |
| F3 | [Hold the failed page for an agent](#f3-hold-the-failed-page-for-an-agent-try-lookups-against-it) (try lookups against it) | agent, developer with AI | High | M on top of #288 (L alone) |
| F4 | [Findable View per User Step, and what each step changed](#f4-findable-view-per-user-step-and-what-each-step-changed) | agent | Medium–High | M |
| F5 | [Parting step side by side](#f5-the-parting-step-side-by-side-on-two-testing-tools) on two Testing Tools | developer, agent | Medium–High | M |
| F6 | ["Reproduce on Browserless, then step into the server"](#f6-reproduce-on-browserless-and-step-into-the-server-guide-and-timeouts-under-a-debugger) guide, and timeouts that don't fire under a debugger | developer without AI | Medium | S |
| F7 | [Open the Browserless state in a browser](#f7-open-the-browserless-state-in-a-browser) | developer without AI | Medium | M–L, prototype first |
| — | Replay-style recording, a Unified UI mode or watch mode | — | dropped or deferred | L |

**Gist for the map** [opinion]: the reports explain a failure well after the fact; what is missing is a live, paused page to look at and try lookups on (for people and for agents), and the cross-tool moves only this project can make: reproduce on Browserless under a debugger, and compare the two tools at the parting step.

---

## 1. What other tools give after a failure

### Verified facts

| # | Fact | Source |
|---|---|---|
| D1 | **Trace viewer, filtered by action.** Besides the before/after DOM snapshots (landscape H2), selecting an action shows its "Action log" and "Source code location"; the Log tab shows "what Playwright is doing behind the scenes such as scrolling into view, waiting for element to be visible, enabled and stable"; double-clicking an action filters the Console and Network tabs "to only show the logs that were made during that action". The Errors tab marks the failure on the timeline with "a red line". Traces open with `show-trace` from Maven (`mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="show-trace trace.zip"`) or at trace.playwright.dev, also from a URL (`?trace=…`). | [playwright `docs/src/trace-viewer.md` @ 0492267](https://github.com/microsoft/playwright/blob/049226728d2da8054db18b04782a9848b755855e/docs/src/trace-viewer.md), "Opening Trace Viewer", "Trace Viewer features" |
| D2 | **UI Mode** (JS Test Runner only, `npx playwright test --ui`): "explore, run, and debug tests with a time travel experience complete with a watch mode"; run a single test from the sidebar; "pop out the DOM snapshot … From there you can open the browser DevTools"; **Pick locator** over a past DOM snapshot with a "locator playground"; "Open in VSCode" at the action's line; **Watch mode**: "re-run the test when you make changes to it". | [playwright `docs/src/test-ui-mode-js.md` @ 0492267](https://github.com/microsoft/playwright/blob/049226728d2da8054db18b04782a9848b755855e/docs/src/test-ui-mode-js.md) |
| D3 | **Inspector and step-through, in Java too.** `PWDEBUG=1` "configures Playwright for debugging and opens the inspector", with "Browsers launch in headed mode" and "Default timeout is set to 0 (= no timeout)"; Java adds `PLAYWRIGHT_JAVA_SRC=<java source dirs>` so the Inspector shows the source. The toolbar can "play, pause or step through each action of your test". `page.pause()` stops at a chosen point ("Run a test from a specific breakpoint"). While paused, a developer can "live edit the locators" and pick one, and read **actionability logs** ("if the element was visible, enabled and stable, if the locator resolved to an element"). `slowMo` slows each operation "to … follow along while debugging". The VS Code extension (JS) sets breakpoints and steps in the editor. | [playwright `docs/src/debug.md` @ 0492267](https://github.com/microsoft/playwright/blob/049226728d2da8054db18b04782a9848b755855e/docs/src/debug.md), "Run in debug mode" (langs csharp, java, python), "Stepping through your tests", "Run a test from a specific breakpoint", "Live editing locators", "Actionability logs" |
| D4 | **The healer agent debugs on a paused page.** Its workflow: run the tests with `test_run`, then "For each failing test run `test_debug`", and "When the test pauses on errors, use available Playwright MCP tools to … Capture page snapshot". Its tools include `browser_snapshot`, `browser_generate_locator`, `browser_console_messages`, `browser_network_requests`, `browser_evaluate`. `test_debug` runs one test with `pauseOnError: true`, `timeout: 0`, `workers: 1`. If confident the test is right, it marks it `test.fixme()` "explaining what is happening instead". The healer's job as documented: "Replays the failing steps", "Inspects the current UI to locate equivalent elements", "Suggests a patch". | [`playwright-test-healer.agent.md`](https://github.com/microsoft/playwright/blob/049226728d2da8054db18b04782a9848b755855e/packages/playwright/src/agents/playwright-test-healer.agent.md), [`mcp/test/testTools.ts` L60–89](https://github.com/microsoft/playwright/blob/049226728d2da8054db18b04782a9848b755855e/packages/playwright/src/mcp/test/testTools.ts#L60-L89), [`docs/src/test-agents-js.md` "Healer"](https://github.com/microsoft/playwright/blob/049226728d2da8054db18b04782a9848b755855e/docs/src/test-agents-js.md) |
| D5 | **Playwright MCP's `devtools` capability** (opt-in, `--caps=devtools`) adds `browser_resume` ("Resume script execution after it was paused. When called with step set to true, execution will pause again before the next action"), `browser_highlight`, `browser_start_tracing`, `browser_start_video`, and `browser_annotate` (the user draws on the page; returns "the annotated screenshot, ARIA snapshot, and the list of annotations"). `--extension` connects "to a running browser instance". | [playwright-mcp `README.md` @ 2337aff](https://github.com/microsoft/playwright-mcp/blob/2337aff0213d28db41d3e3daf48cee19b3d0bd82/README.md), options table and "DevTools (opt-in via --caps=devtools)" |
| D6 | **Chrome DevTools MCP**, beyond roadmap N9: `take_snapshot` ("a text snapshot of the target page based on the a11y tree … Prefer taking a snapshot over taking a screenshot"), `list_console_messages`, `list_network_requests`, `evaluate_script`, `get_css_styles`, `performance_start_trace`, heap-snapshot tools, and `set_logpoints` ("logs expressions to the console every time a line executes, without pausing or changing the source"). It can connect to a running Chrome instead of starting one. Everything is about the browser: no tool reaches the server. | [chrome-devtools-mcp `docs/tool-reference.md` @ ab911a6](https://github.com/ChromeDevTools/chrome-devtools-mcp/blob/ab911a6dd71ed4960c1c679f831c7b5607bc5505/docs/tool-reference.md); [`docs/advanced-usage.md`, "Connecting to a running Chrome instance"](https://github.com/ChromeDevTools/chrome-devtools-mcp/blob/ab911a6dd71ed4960c1c679f831c7b5607bc5505/docs/advanced-usage.md) |
| D7 | **Cypress time travel.** In open mode, "Hover over any command in the Command Log to restore the Application or Component Under Test to the state it had when that command ran. Cypress captures a snapshot for every command"; "Click any command to pin its snapshot"; "action commands capture both before and after". Each entry logs `consoleProps` (inputs and outputs) to DevTools. By default it keeps "**50** tests worth of snapshots". `r` reruns the tests. The **Debug page** shows failed CI runs recorded to Cypress Cloud and can "rerun only the failed tests". | [cypress-documentation `docs/app/core-concepts/open-mode.mdx` @ 3110e3a](https://github.com/cypress-io/cypress-documentation/blob/3110e3a01a6f1618b0855973be9990195dd773b2/docs/app/core-concepts/open-mode.mdx) L13, L95, L141–203, L403, L429 |
| D8 | **`cy.pause()`** stops commands "and allow[s] interaction with the application under test. You can then "resume" running all commands or choose to step through the "next" commands"; `.debug()` sets a JS `debugger`. | [`docs/api/commands/pause.mdx` @ 3110e3a](https://github.com/cypress-io/cypress-documentation/blob/3110e3a01a6f1618b0855973be9990195dd773b2/docs/api/commands/pause.mdx) |
| D9 | **`cypress tap`** (beta, Cypress 15.21+): "An AI coding agent can read everything on this page without the GUI. The `cypress tap` CLI attaches to an open mode session and prints the Command Log, pins a command's snapshot the same way clicking it here does, and reads the resulting DOM and accessibility tree." Commands: `sessions`, `status`, `specs`, `run` (start or rerun a spec), `reporter`, `command` (one entry's DOM snapshots and console properties), `pin`, `dom`, `aria`, `inspect`. | [open-mode.mdx L209](https://github.com/cypress-io/cypress-documentation/blob/3110e3a01a6f1618b0855973be9990195dd773b2/docs/app/core-concepts/open-mode.mdx); [docs.cypress.io/app/tooling/cypress-tap](https://docs.cypress.io/app/tooling/cypress-tap) |
| D10 | **Cypress Test Replay** (Cypress Cloud, all plans): "replay the run and inspect the DOM, network requests, console logs, JavaScript errors, and element rendering as they happened during `cypress run`". Needs recording to Cypress Cloud. | [`docs/cloud/features/test-replay.mdx` @ 3110e3a](https://github.com/cypress-io/cypress-documentation/blob/3110e3a01a6f1618b0855973be9990195dd773b2/docs/cloud/features/test-replay.mdx) |
| D11 | **Replay.io** records the browser runtime ("intercept low level OS library calls and replay them later") so that a run can be inspected "with browser DevTools"; console logs can be added to lines that already ran, and "the messages appear as if they had always been there". Test runs are recorded by running Playwright in "Replay Chromium" through the `@replayio/playwright` npm reporter, which uploads "a recording per test" (the page shows only a JS/TS config). A Replay MCP server "lets an AI agent inspect a recording" (sources, console, network, "Playwright steps", "execution over time"). The pages say nothing about recording the server side. | [docs.replay.io](https://docs.replay.io/), [How time travel works](https://docs.replay.io/reference/time-travel/how-does-time-travel-work), [Record your app](https://docs.replay.io/basics/getting-started/record-your-app) |
| D12 | **Pest browser tests**: `->debug()` "will limit execution to this test", "pause the execution, and open the browser window"; `--debug` "opens the browser window and pauses the execution of the test when it fails"; `->tinker()` opens "a Tinker session in the context of the current page". | [pestphp.com/docs/browser-testing](https://pestphp.com/docs/browser-testing) |
| D13 | **Capybara**, the older form of Phoenix's `open_browser` (roadmap N13): `save_and_open_page` "Save a snapshot of the page and open it in a browser for inspection"; `save_page` injects a `base` tag for `asset_host`; `save_and_open_screenshot`. Phoenix's docs (LiveView 1.2.15) say only "Open the default browser to display current HTML of `view_or_element`", not how assets are handled. | [Capybara::Session](https://rubydoc.info/github/teamcapybara/capybara/master/Capybara/Session); [Phoenix.LiveViewTest](https://phoenix-live-view.hexdocs.pm/Phoenix.LiveViewTest.html) |
| D14 | **Browserless Test (Vaadin)**: `TreeOnFailureExtension` (roadmap N7) publishes the tree "as a JUnit report entry", which "Maven Surefire doesn't display"; a failed component query puts the same tree in its error message, extension or not. The page names no on-demand print and no HTML or browser view: the tree "represents Java component state and element attributes, not a browser screenshot or rendered layout". "A test creates the Vaadin service, sessions, and UIs directly in the JVM that runs your JUnit tests." | [vaadin.com/docs/latest/flow/testing/browserless/snapshots](https://vaadin.com/docs/latest/flow/testing/browserless/snapshots); [vaadin.com/docs/latest/flow/testing/browserless](https://vaadin.com/docs/latest/flow/testing/browserless) |
| D15 | **Flow** can write any element as HTML: `Element.getOuterHTML()` "Gets the outer HTML for the element … recursively", and `Element.toString()` returns it. | [vaadin/flow `Element.java` @ 2e9af17, L1899–1907](https://github.com/vaadin/flow/blob/2e9af173d21b9cbf0e7f915c1522a1388171c8d7/flow-server/src/main/java/com/vaadin/flow/dom/Element.java#L1899-L1907) |
| D16 | **Surefire** runs one method with `mvn -Dtest=TestCircle#mytest test`, and `mvn -Dmaven.surefire.debug test` makes "The tests … automatically pause and await a remote debugger on port 5005". | [Surefire, single test](https://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html); [Surefire, debugging](https://maven.apache.org/surefire/maven-surefire-plugin/examples/debugging.html) |

### Inferences

- **Two families.** *Post-mortem* tools work from a recording: Playwright's trace (D1), Cypress Test Replay (D10), Replay.io (D11). *Live* tools stop the test while the page is still there: Inspector and `page.pause()` (D3), `cy.pause()` (D8), Pest `--debug` (D12), the healer's `test_debug` (D4). The live family is where the agent tools are heading: `test_debug` with `pauseOnError`, `browser_resume` with `step` (D5), `cypress tap pin` (D9) all give an agent a paused page to query.
- **"Try a locator against the failed page" is the shared core.** UI Mode's pick locator on a past snapshot (D2), the Inspector's live-edited locator (D3), the healer's `browser_generate_locator` on a paused page (D4) and `cypress tap dom/aria` (D9) all answer "what should the test have asked for?" by letting someone try.
- **Every recording and every debug tool stops at the browser.** Chrome DevTools MCP (D6), Replay.io (D11) and Playwright's trace (landscape H2) see client code, console and network. The server's state and code are another debugger and another log.
- **Java gets the Inspector but not the Test Runner features.** `PWDEBUG`, `page.pause()` and `show-trace` work from Java (D1, D3); UI Mode, watch mode, `--last-failed` and the test agents are JS Test Runner features (D2, roadmap N2, landscape AI1).

---

## 2. What the Unified reports already cover

All from [`docs/guide/reports-and-failures.md`](../guide/reports-and-failures.md) and [`docs/spec/12-readable-tests.md`](../spec/12-readable-tests.md) at `39411f6`, unless another path is given.

| # | Debugging question | What the Unified API gives today | Where |
|---|---|---|---|
| U1 | What failed, and where in the scenario? | `UnifiedAssertionError` with `Failed at` (User Step *n* of *m*), `In step` (the User Task chain), `Code` (call line, then the line inside the Task). The CI log prints the User Steps with the failed one marked `✘`. | guide "The failure message", "The CI log"; spec "Failures" |
| U2 | Why could the lookup not find it? | `Expected`, `Found` (none, several with their scope, "1, but disabled/hidden/read-only/covered", or the values seen while waiting), Near Misses, `Did you mean`, `Waited` and where the timeout came from. | same |
| U3 | What could the test have found instead? | The Findable View, `<test>.view.txt`, written on failure on every Testing Tool; `page.describe()` on demand. | spec "Findable View"; guide "The Findable View" |
| U4 | How do I fix the lookup? | `ApplyDidYouMean` OpenRewrite recipe writes the suggestion into the test, no model. | guide "Applying it" |
| U5 | Did the server throw? | The application's warnings and errors (Logback or `java.util.logging`) under the User Step during which they were logged, with the exception; browser console errors on Playwright and Selenium-on-Chrome; `assertThat(page).hasNoErrors()`. | spec "Log lines under the User Step" |
| U6 | What did the page look like? | Failure evidence: a screenshot in a browser, the component tree on Browserless. With `vaadin.testing.user-steps.snapshots=true`: a screenshot or tree after every User Step, the Step Target spotlighted, in a viewer in `report.html` (← → between steps). | spec "Evidence per User Step" |
| U7 | What did the browser do, action by action? | `vaadin.testing.playwright.trace=on-failure`: Playwright's trace with a group per User Task and User Step, linked from `report.html`, opened with `show-trace` or trace.playwright.dev. Playwright only. | same |
| U8 | Did the test change, or the application? | *Changed in this PR*, and the User Steps diff against the Base Run (`baseline-dir`). | guide "How a changed scenario's steps changed" |
| U9 | Does it fail on the other Testing Tool too? | *Compared with another Testing Tool* (`compare-dir`, `CompareRuns`): ends differently, or parts at step *n*, and whether both runs are of the same code. | guide "Compared with another Testing Tool"; [`ci-setup.md`](../guide/ci-setup.md) |
| U10 | What failed in the run, for a program? | `summary.json`: every scenario, the failed step, the `failure` object, evidence paths, `differsOn`. | guide "The run as data" |
| U11 | How do I look at it in a browser myself? | `vaadin.testing.playwright.headless=false` and `vaadin.testing.selenium.headless=false`. | [`configuration.md`](../guide/configuration.md) |
| U12 | How do I rerun it? | The guide shows `mvn test -Dtest=EditPersonTest` and the same with `-Dvaadin.testing.tool=playwright`; the failure itself names no command. | [`reviewing-tests.md`](../guide/reviewing-tests.md) L72–73; [`getting-started.md`](../guide/getting-started.md) L109 |

**Not there** (searched `docs/`, `README.md` and the Java sources for `pause`, `PWDEBUG`, `slowMo`, `debug`, `jdwp`, watch and rerun):

| # | Missing |
|---|---|
| G1 | No debug mode, no pause at a User Step, no hold-on-failure: when a test fails, its page is closed before anyone can look at it. |
| G2 | No way to try a lookup against the page of a failed test, for a person (pick locator, D2–D3) or for an agent (`test_debug` + snapshot, D4; `cypress tap`, D9). The Findable View (U3) is a list, not a playground. |
| G3 | No text record of the page per User Step on browser Testing Tools: step snapshots are screenshots there (U6), and the Findable View is written only on failure. An agent cannot "time travel" without reading images or a trace zip. Nothing says what a step changed on the page. |
| G4 | The failure and `summary.json` name no rerun command, and there is no "rerun the failed scenarios" (Playwright `--last-failed`, roadmap N2; Cypress's Debug page, D7). |
| G5 | The comparison with another Testing Tool names the parting step but shows no evidence from it: the two runs' snapshots, trees and Findable Views at that step stay in two directories. |
| G6 | Unified waits (`vaadin.testing.timeout`, 10 s) are not documented to stop under a debugger, unlike `PWDEBUG` (D3) and `test_debug` (D4), which set the timeout to 0. Whether a paused breakpoint makes a Unified wait time out was **not verified** in code. |
| G7 | The Browserless state can be read as a tree (U6) but not seen: no HTML of it, no "open in a browser" (D13, roadmap N13). |
| G8 | No watch mode or UI mode; Java has neither from Playwright (D2). Selenium runs have no trace (U7 is Playwright only). |

---

## 3. Side by side

| Need after a failure | Playwright (Java) | Playwright Test (JS) | Cypress | Pest / Phoenix / Capybara | Unified API today |
|---|---|---|---|---|---|
| Why the lookup failed, in words | error + actionability log in trace (D1) | same | command log | — | **failure with Near Misses, `Did you mean`** (U2) |
| What the page held, as text | trace DOM (D1) | same | `cypress tap dom/aria` (D9) | `open_browser` HTML (D13) | **Findable View on failure** (U3); tree on Browserless |
| Page at every step | trace (D1) | trace, UI Mode (D2) | time travel (D7) | — | screenshots / trees with `snapshots=true` (U6), trace on Playwright (U7) |
| Server log at the step | — | — | — | — | **yes** (U5) |
| Stop at failure, look live | `PWDEBUG` + `page.pause()` (D3) | `--debug`, `test_debug` (D3–D4) | `cy.pause()` (D8) | `--debug`, `->debug()` (D12) | **no** (G1) |
| Try a lookup on the failed page | Inspector pick/edit locator (D3) | UI Mode pick locator (D2) | `cypress tap` (D9) | `->tinker()` (D12) | **no** (G2); `Did you mean` covers the common case |
| Agent debugs a live failure | Playwright MCP + `browser_resume` (D5) | healer agent (D4) | `cypress tap` (D9) | — | **no**; agent reads files (U10) |
| Rerun failed / one test | Surefire `-Dtest` (D16) | `--last-failed` | Debug page rerun (D7) | `->debug()` limits to one | `-Dtest` in the guide only (G4) |
| Watch mode | — | UI Mode (D2) | open mode | — | no (G8) |
| Same scenario on a second tool | — | — | — | — | **yes, unique** (U9) |
| Debugger into server code | — | — | — | — | possible on Browserless (D14), undocumented |

---

## Candidate features

All [opinion]. Effort as in #499: S a day or two for an agent with review, M about a week, L several weeks or a prototype first. Benefit is for the job "debug failing tests" only; the map's ranking ([#504](https://github.com/parttio/unified-testing-tool/issues/504)) weighs the other two jobs.

### F1. Rerun lines in the failure, and a rerun of the failed scenarios

**What.** The failure message gains a `Rerun` line with the exact commands for this scenario: on this Testing Tool, and on Browserless when the run was in a browser (`mvn test -Dtest=EditEmailTest#changesTheEmail -Dvaadin.testing.tool=browserless`). `summary.json` carries the same per failed scenario. A setting such as `-Dvaadin.testing.rerun=failed` reads the last `summary.json` and runs only its failed scenarios (Surefire's `-Dtest` list underneath, D16).
**Who.** Agent: no guessing of class, method, tool or settings; one command from the failure to the repro. Developer: the same, copied from the CI log.
**Benefit.** High: it is the first step of every debugging session, and it points at the cheap Browserless repro that F6 builds on.
**Effort.** S. **Prior art.** Playwright `--last-failed` (roadmap N2), Cypress Debug page "rerun only the failed tests" (D7), `cypress tap run` (D9).

### F2. Debug mode: pause on failure or at a User Step

**What.** `-Dvaadin.testing.debug=true` (or `=step:5`): one test at a time, headed browser, Unified waits without a timeout (as `PWDEBUG` sets it to 0, D3), and on failure (or before User Step 5) the test holds: the browser stays open on Playwright and Selenium; on Browserless the Findable View and tree are printed. The console offers *resume*, *step* (run the next User Step), *describe*. `page.pause()` in a test does the same at a chosen point. On Playwright it can hand over to Playwright's Inspector.
**Who.** Developer with or without AI: the live look every other tool has (G1).
**Benefit.** High for people; the agent form is F3.
**Effort.** M (the pause loop in the core, a headed default per adapter, the timeout switch). **Prior art.** `PWDEBUG`/`page.pause()`/step-through (D3), `cy.pause()` (D8), Pest `--debug` and `->debug()` (D12), `browser_resume` with `step` (D5).

### F3. Hold the failed page for an agent: try lookups against it

**What.** The agent form of F2: a failed test (or one stopped at a step) stays alive and answers, from the command line, `describe`, `find <lookup>` (does this lookup find exactly one? the same failure parts if not), `steps`, `resume`. In User Step words and Unified lookups, not ARIA refs or CSS. This is the live session of [#288](https://github.com/parttio/unified-testing-tool/issues/288) entered from a failure instead of from nothing.
**Who.** Agent, and a developer with an assistant: the healer's `test_debug` loop (D4) for Unified UI Tests, on Browserless without a browser.
**Benefit.** High: closes G2, and it is the case where `Did you mean` has nothing to say.
**Effort.** M if built with #288, L alone. **Prior art.** Playwright healer + `test_debug` + `browser_generate_locator` (D4), `cypress tap` (D9), Pest `->tinker()` (D12). New: the same on Browserless, in the test's own terms. Suggest folding it into #288's scope rather than a separate issue.

### F4. Findable View per User Step, and what each step changed

**What.** With `snapshots=true`, write the Findable View after each User Step next to the screenshot or tree (`steps/<n>.view.txt`), on every Testing Tool, and a short diff per step: what the step made appear, disappear or change state (`+ dialog “Edit person”`, `text field “Name” = “Alan” → “Alan M. Turing”`). `report.html` shows the diff under each step; `user-steps.json` points to it.
**Who.** Agent: time travel as text, without reading screenshots or unzipping a trace (G3). Developer: "the click did nothing" becomes visible in one line.
**Benefit.** Medium–High. **Effort.** M (the view exists; the cost per step in a browser needs measuring, spec 12 says it "runs only on failure or when asked" for that reason). **Prior art.** Trace before/after (D1), Cypress snapshots per command (D7), `cypress tap command` (D9). New: the per-step diff in the test's terms.

### F5. The parting step side by side on two Testing Tools

**What.** Where *Compared with another Testing Tool* names a parting or failing step, `report.html` (and `CompareRuns`) shows both runs' evidence at that step: screenshot next to the Browserless tree, and the two Findable Views diffed, plus each run's log lines there. Extends #286 and pairs with [#310](https://github.com/parttio/unified-testing-tool/issues/310) (is the difference a documented Limitation).
**Who.** Developer and agent on the most Unified-specific failure, "passes on Browserless, fails in the browser".
**Benefit.** Medium–High; only this project can have it. **Effort.** M (needs F4's per-step text on the browser side, or at least the failure's Findable View). **Prior art.** None found: no other tool runs one test on two kinds of Testing Tool (roadmap C2).

### F6. Reproduce on Browserless and step into the server: guide, and timeouts under a debugger

**What.** A guide section "Debugging a failing test": rerun on Browserless (F1), set a breakpoint in the view's listener, run with `-Dmaven.surefire.debug` (D16) or the IDE, and step from the User Step into the server code, since Browserless creates the UI "directly in the JVM that runs your JUnit tests" (D14). Make Unified waits not time out while a debugger is attached, or document `vaadin.testing.timeout` for it (G6; verify first).
**Who.** Developer without AI first; an agent can use the rerun but not the debugger.
**Benefit.** Medium: it uses a strength nobody else has (every browser tool stops at the browser, D6, D11), at the cost of a page and a check.
**Effort.** S. **Prior art.** `PWDEBUG` sets timeouts to 0 (D3); `test_debug` sets `timeout: 0` (D4).

### F7. Open the Browserless state in a browser

**What.** `page.openInBrowser()` and a failure-evidence `.html` on Browserless: the UI's element tree as HTML (`Element.getOuterHTML()`, D15) with the application's frontend bundle linked, so a developer sees roughly what the user would. Prototype first: web components need the bundle, and state that lives only in the client (Lit templates, a combo box's overlay) won't be there.
**Who.** Developer without AI, used to looking at a page rather than a tree.
**Benefit.** Medium, and lower if F2 exists (rerun in a headed browser instead). **Effort.** M–L, prototype first. **Prior art.** Phoenix `open_browser/2` (roadmap N13), Capybara `save_and_open_page` (D13). No Java tool found has it.

### Dropped or deferred

- **Replay-style recording** (D11): records the browser only, through a patched Chromium and a JS reporter; the server, where Vaadin's logic runs, is not covered. Playwright's trace (U7) covers the browser half already. Drop.
- **A Unified UI mode or watch mode** (D2): large, and the IDE plus F1 and F2 cover most of it for Java; watch-on-edit for agents is already #293. Defer to #293.
- **A trace for Selenium** (G8): TestBench users can move to Playwright for a traced run; low value per effort. Drop.

---

## Not verified

- Whether a Unified wait (`vaadin.testing.timeout`) fires while a test thread is stopped at a breakpoint, or while Playwright's Inspector is paused (G6). The adapter code was not read for this.
- Whether `PWDEBUG=1` already works through the Unified Playwright adapter (Dramafinder), and what Unified Auto-Wait does under it.
- Vaadin's "Debug a Failing Browserless Test" page, linked from the snapshots page, was not found (404 at the obvious URL; a web search returned only pages linking to it).
- IntelliJ's and Eclipse's own "rerun failed tests" and debugger features were not checked.
- Replay.io's support for Java Playwright and for Cypress: the recording page shows only a JS/TS Playwright config.
- How Phoenix's `open_browser` handles assets: its docs don't say.
