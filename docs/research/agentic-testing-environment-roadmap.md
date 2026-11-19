# Testing a Vaadin application with agents: what is here, what others have, what to build next

Background research for the roadmap. It does not belong to a single ticket. It builds on [web-framework-testing-landscape.md](web-framework-testing-landscape.md) (facts cited below as "landscape AI3", "landscape AG12" and so on), [copilot-recorder.md](copilot-recorder.md), [user-step-recording.md](user-step-recording.md) and [readable-web-ui-tests.md](readable-web-ui-tests.md), and does not repeat them.

**Question.** What does the Unified API already give someone testing a Vaadin application with an agent, with an AI assistant, or with neither? What do other solutions have that it lacks? Which features belong on the roadmap, including ones nobody ships?

Researched 2026-11-17 against `main` at `37c7b00` and the issue tracker of `parttio/unified-testing-tool` as of that day (42 open issues). External pages were read on 2026-11-17 from the URLs given; unlike the landscape note, most are **not pinned to a commit**. This field changes monthly: treat every "nobody has this" below as true on that date for the sources listed, no further.

Labels: a **fact** has a source (a file path, an issue or a URL). **[opinion]** and the sections titled *Inferences* are this note's reasoning.

## Answer in short

1. **For the agent-era basics, the Unified API is level with or ahead of the tools it sits on.** It has what the landscape note found to be the new standard: lookups in user terms, a failure message that says what to do next (Near Misses, `Did you mean`), the same failure as JSON, a text record of what every test did (User Steps), a pull-request view of changed behaviour (the diff against the Base Run), style rules as a build check, an agent skill installed by a recipe, and a harness that scores the skill. No other tool checked ships the last three together, and none runs one test both in the JVM and in a real browser.
2. **Where it is behind is the agent's *loop*, not the test API.** Others give an agent a way to look at a running application and act on it (Playwright MCP and CLI, Chrome DevTools MCP, Storybook MCP), turn that session into test code (Playwright's `--codegen java`, the generator agent), repair a failing test (Playwright's healer), check accessibility (axe, Pest, Storybook), compare screenshots (Playwright Test, TestBench), detect flaky tests (Surefire, Playwright, Cypress Cloud) and select tests by change (Quarkus, Develocity, Playwright). The Unified API has none of these today. Four are already open issues: the accessibility snapshot ([#204](https://github.com/parttio/unified-testing-tool/issues/204)), docs for agents ([#205](https://github.com/parttio/unified-testing-tool/issues/205)), repair agents ([#206](https://github.com/parttio/unified-testing-tool/issues/206)) and the Unified Recorder ([#215](https://github.com/parttio/unified-testing-tool/issues/215)).
3. **The features nobody ships come from what only this project has:** the same scenario on Browserless and in a browser, the server-side component tree, and User Steps as data. The strongest are a **cross-tool divergence report**, a **check that an agent-written test fails when the application is broken** (run on Browserless, where a rerun costs milliseconds), a **page view whose lines are lookups to paste**, and **server log lines attached to the User Step that caused them**.

**Ranked shortlist** [opinion]; details in [Roadmap candidates](#roadmap-candidates):

| # | Candidate | Kind | Effort |
|---|---|---|---|
| 1 | [Findable view](#c1-findable-view-the-page-as-lookups-to-paste): the page as a list of Wrappers, each with its lookup; the format for #204 | new format on a planned feature | M |
| 2 | [Cross-tool divergence report](#c2-cross-tool-divergence-report) | new | S–M |
| 3 | [Weak-test checks](#c3-weak-test-checks-does-the-test-fail-when-the-feature-is-broken): a scenario with no check, then step-skip reruns on Browserless | new at this level | S, then M |
| 4 | [Live session](#c4-live-session-an-agent-drives-the-application-through-the-unified-api): an agent drives the application through the Unified API from a CLI and gets a Unified UI Test | new; prototype first | L |
| 5 | [Server logs and browser console per User Step](#c5-server-logs-and-browser-console-per-user-step-and-a-smoke-check), and a smoke check | catch-up + new | S–M |
| 6 | [Apply `Did you mean`](#c6-apply-did-you-mean-repair-as-a-diff-without-a-model) as an OpenRewrite fix: repair as a diff, no model | new; narrows #206 | S |
| 7 | [The loop, packaged](#c7-the-loop-packaged-run-verdict-hooks-llmstxt): a run-level JSON verdict, an edit hook, `llms.txt` | catch-up; part is #205 | S |
| 8 | [Coverage in user terms](#c8-coverage-in-user-terms): routes and components no scenario touches | catch-up in idea, new without a cloud | M |

---

## 1. What is already there

### Verified facts (this repo)

| # | What | Where |
|---|---|---|
| H1 | One Unified UI Test runs on Browserless (in the JVM, the default), Playwright through Dramafinder, or Selenium through TestBench, chosen per run with `vaadin.testing.tool`. | `README.md` (top, Settings) |
| H2 | Lookups are in user terms and typed: `page.find(UnifiedTextField.class).withLabel("Name")`. A Wrapper is lazy and re-finds its component on every use. | `docs/adr/0007-wrappers-carry-their-filters.md`, `docs/spec/04-locating.md` |
| H3 | Auto-Wait on every action; assertions retry until the timeout. There is no wait to write. | `docs/spec/05-waiting.md` |
| H4 | Every Wrapper action and retrying assertion is recorded as a User Step, always, on every Testing Tool. Each test writes `user-steps.txt` and `user-steps.json` (schema version 1: verb, kind, name, filters, scope, values, status, duration, call site); the run writes `summary.md` and `report.html`; on GitHub Actions the summary goes to the job page. | `docs/guide/reports-and-failures.md`, `docs/spec/12-readable-tests.md` ("Reports", "`user-steps.json`"); [#236](https://github.com/parttio/unified-testing-tool/issues/236), [#237](https://github.com/parttio/unified-testing-tool/issues/237), [#263](https://github.com/parttio/unified-testing-tool/issues/263) |
| H5 | **Changed in this PR**: the scenarios whose User Steps or User Tasks are called from a file changed against the base branch, and, given the Base Run's evidence, a diff of each changed scenario's User Steps. The guide names the case it is for: "Did an agent drop the check that was failing?" | `docs/guide/reports-and-failures.md` ("How a changed scenario's steps changed"); commits `48b7c4e`, `c675d40` ([#264](https://github.com/parttio/unified-testing-tool/issues/264)) |
| H6 | "Changed" is decided from the **test side**: a scenario is changed "when any of its User Steps or User Tasks is called from a file changed against the base". A change to the view alone does not mark a scenario. | same file, "The run summary" |
| H7 | Every Unified failure is a `UnifiedAssertionError` with fixed lines: `Failed at`, `In step`, `Expected`, `Found`, `Nearby` (the Near Misses), `Did you mean` (a lookup to paste), `Waited`, `Tool`, `Code`, `Evidence`. The same parts are getters and a `failure` object in `user-steps.json`. | `docs/guide/reports-and-failures.md` ("The failure message"); [#238](https://github.com/parttio/unified-testing-tool/issues/238); closing comment of [#208](https://github.com/parttio/unified-testing-tool/issues/208) |
| H8 | Failure evidence: a screenshot in a browser, the component tree as text on Browserless. Optional: a snapshot after every User Step; Playwright's trace grouped by User Step. | `docs/spec/09-capabilities.md` ("Failure evidence"); commit `675faff` ([#207](https://github.com/parttio/unified-testing-tool/issues/207)) |
| H9 | What the recorder cannot see shows as a gap line (Escape Hatch, `waitUntil`, `where(..)`), never silently. Secrets are masked through `page.secret(..)`. | `docs/guide/reports-and-failures.md` ("Reading a User Step") |
| H10 | Capabilities (`@RequiresBrowser`, `@RequiresTestingTool`) skip a test on a Testing Tool that lacks them; the Escape Hatch reaches the tool itself. | `docs/spec/09-capabilities.md`, `docs/spec/08-escape-hatch.md` |
| H11 | Three style rules as ArchUnit checks: `SCENARIO_SENTENCE`, `NO_RAW_WAITS`, `TOOL_CODE_NEEDS_CAPABILITY`. The guide lists what they don't check, including "whether the sentence is a good one" and "no plain JUnit assertions on what the user sees". No rule looks at whether a test checks anything. | `docs/guide/style-rules.md` ("What the rules don't check"); [#239](https://github.com/parttio/unified-testing-tool/issues/239) |
| H12 | Two agent skills ship in the `unified-test-rewrite` jar: `write-unified-ui-tests` and `resolve-migration-markers`. The recipe `AddUnifiedTestingSkill` installs the first into `.claude/skills/` and keeps a marked section in the user's `AGENTS.md`. The writing skill tells the agent to read the view's source, prove the test on Browserless and Playwright, run the style rules, read `user-steps.txt`, and decide "who is wrong" before changing a failing test. | `unified-test-rewrite/src/main/resources/META-INF/unified/skills/*/SKILL.md`; `docs/spec/12-readable-tests.md` ("The agent skill"); [#241](https://github.com/parttio/unified-testing-tool/issues/241) |
| H13 | Both skills are scored by a harness: `scripts/eval-migration-skill.sh` (43 Migration Markers over 17 fixtures) and `scripts/eval-writing-skill.sh` (6 prose scenarios; compiles, passes on two Testing Tools, style rules, intent share, no Escape Hatch), each against a committed baseline. All six writing fixtures are scenarios on a working application. | `unified-test-rewrite/skill-eval/README.md`, `unified-test-rewrite/skill-eval/writing/`; commit `59cf512` |
| H14 | Migration Recipes from TestBench, Browserless and Dramafinder, leaving Migration Markers. | ADRs 0004–0006 in `docs/adr/` |
| H15 | The adapter SPI and the test lifecycle are Test Runner-neutral; JUnit is one integration on top. | `docs/adr/0008-adapter-spi-is-test-runner-neutral.md` |
| H16 | Users and windows: `@WithMockUser`, `openWindow`, a test-only login endpoint for browsers. | `docs/spec/11-users.md`, `README.md` (Modules) |
| H17 | Network interception, tracing and browser contexts are "Escape Hatch only". The Unified API has `page.screenshot(..)` but no screenshot comparison, no accessibility check, no clock control. | `docs/spec/09-capabilities.md` ("Browser-only features", "Limitations") |
| H18 | Each run uses one Testing Tool and writes one evidence directory; the reports name the tool but never compare two tools' runs. Differences between tools are found by hand and filed as bugs: [#84](https://github.com/parttio/unified-testing-tool/issues/84), [#88](https://github.com/parttio/unified-testing-tool/issues/88), [#259](https://github.com/parttio/unified-testing-tool/issues/259), [#97](https://github.com/parttio/unified-testing-tool/issues/97), [#267](https://github.com/parttio/unified-testing-tool/issues/267). | `docs/guide/reports-and-failures.md`; the issues |
| H19 | The reports are per test (`user-steps.json`) and per run for people (`summary.md`, `report.html`). No run-level JSON is specified. | `docs/spec/12-readable-tests.md` ("Reports") |
| H20 | The demo runs its tests sequentially. Nothing in the spec or guide covers flaky tests, reruns, test selection or watch mode (searched `docs/spec`, `docs/guide`, `README.md`). | `unified-test-demo/pom.xml` L125–126 |

### Already planned (open issues)

| Issue | What it would add | State |
|---|---|---|
| [#204](https://github.com/parttio/unified-testing-tool/issues/204) | A text view of the page by role and name on every Testing Tool, on failure, perhaps with an assert API | open, `needs-triage`; format undecided |
| [#205](https://github.com/parttio/unified-testing-tool/issues/205) | `llms.txt`, an API reference in the jar, the Vaadin MCP question (the skill and `AGENTS.md` parts shipped) | open, narrowed |
| [#206](https://github.com/parttio/unified-testing-tool/issues/206) | Whether Playwright's test agents can write or repair Unified UI Tests; repair as a reviewed diff | open, `needs-triage` |
| [#215](https://github.com/parttio/unified-testing-tool/issues/215), [#201](https://github.com/parttio/unified-testing-tool/issues/201) | The Unified Recorder as a Copilot plugin, rule-based, no AI | open, prototype scoped |
| [#227](https://github.com/parttio/unified-testing-tool/issues/227) | A Browserless Test artifact without JUnit | open, upstream ask |

### Inferences

- **For three readers, the same output.** The agent reads `user-steps.txt` and the failure's fixed lines; the developer reads the same in the IDE and the CI log; the reviewer reads `summary.md`. This is deliberate (`docs/spec/12-readable-tests.md`) and is the project's main asset for work with agents: there is no separate "agent mode" to keep in step.
- **The project treats agent guidance as code under test** (H13). That is rare. The landscape note found frameworks shipping skills and `AGENTS.md` blocks (landscape AI14–AI21, AG8, AG11); it found one claim of measuring them, Next.js's, and could not verify it.
- **The weak side is everything before the test exists and after it fails.** The skill tells the agent to read the view's Java source and guess the lookups (H12, step 1). Nothing lets it *look* at the running view in Unified terms, and nothing tells it whether the passing test it wrote would catch a bug.

---

## 2. What others have

Sources for Playwright, Cypress, Selenium, Angular, Next.js, Laravel Boost, Svelte, Dramafinder and Vaadin's MCP server and Skills are in the landscape note, §3–§5, and are cited by their ids. New facts:

### Verified facts

| # | Fact | Source |
|---|---|---|
| N1 | **Playwright MCP** has opt-in tool groups. `--caps=testing` adds `browser_generate_locator` ("Generate locator for the given element to use in tests") and `browser_verify_element_visible`, `browser_verify_text_visible`, `browser_verify_value`. `--codegen <lang>` sets "the language to use for code generation": typescript, python, **java**, csharp. `browser_start_recording` records "actions that the user performs in the browser as Playwright code". `browser_console_messages` and `browser_network_requests` are in the core group. | [microsoft/playwright-mcp `README.md`](https://github.com/microsoft/playwright-mcp/blob/main/README.md), `main` |
| N2 | **Playwright Test** (the JS Test Runner) options: `--only-changed [ref]` "Only run test files that have been changed between 'HEAD' and 'ref'"; `--last-failed`; `--ui` "interactive UI mode"; `--repeat-each <N>`; `--retries`; `--fail-on-flaky-tests`; `--shard`. | [playwright `docs/src/test-cli-js.md`](https://github.com/microsoft/playwright/blob/main/docs/src/test-cli-js.md), `main` |
| N3 | Playwright's **Clock API** (`install`, `setFixedTime`, `fastForward`, `pauseAt`, …) has Java examples. | [playwright `docs/src/clock.md`](https://github.com/microsoft/playwright/blob/main/docs/src/clock.md) |
| N4 | Playwright **accessibility testing for Java** runs "the axe accessibility testing engine" through the Maven package `com.deque.html.axe-core/playwright`. "Automated accessibility tests can detect some common accessibility problems … But many accessibility problems can only be discovered through manual testing." | [playwright `docs/src/accessibility-testing-java.md`](https://github.com/microsoft/playwright/blob/main/docs/src/accessibility-testing-java.md) |
| N5 | Playwright **visual comparisons** (`expect(page).toHaveScreenshot()`) are documented for Playwright Test with JS and TS examples only. "Browser rendering can vary based on the host OS, version, settings, hardware". | [playwright `docs/src/test-snapshots-js.md`](https://github.com/microsoft/playwright/blob/main/docs/src/test-snapshots-js.md) |
| N6 | **TestBench** compares screenshots: "Screenshots taken with the `compareScreen()` method are compared to a reference image"; on a difference it stores the screenshot in the error folder and writes an HTML file marking the regions; default tolerance 0.01. | [vaadin.com/docs/latest/flow/testing/end-to-end/screenshots](https://vaadin.com/docs/latest/flow/testing/end-to-end/screenshots) |
| N7 | **Browserless Test** has `TreeOnFailureExtension`, which "publishes a text representation of the server-side UI component tree when a test fails": "every component currently in the UI, with its properties and attributes". It is "Java component state and element attributes, not a browser screenshot". The page names no API to assert on a snapshot. | [vaadin.com/docs/next/flow/testing/browserless/snapshots](https://vaadin.com/docs/next/flow/testing/browserless/snapshots) (25.4 pre-release docs) |
| N8 | The **Vaadin dev-loop CLI** (`.vaadin/vaadin-dev`: `status`, `start`, `apply`, `stop`, `restart`) "owns your application's edit-to-running-app loop". "Exit code `0` means the change is live, `1` it failed". It does not run tests or show the UI: for a visual change "nothing else proves the UI renders what you intended", so its skills expect "a browser automation tool such as a Playwright MCP server" and the Vaadin MCP server, neither of which it installs; non-visual changes are left to "the project's own tests". | [vaadin/docs `dev-loop-cli.adoc` @ f0394e7](https://github.com/vaadin/docs/blob/f0394e71abf829838c3d2b68056f8c44b695ebc5/articles/flow/configuration/live-reload/dev-loop-cli.adoc) |
| N9 | **Chrome DevTools MCP** "lets your coding agent … control and inspect a live Chrome browser": automation, performance traces, network requests, console messages "with source-mapped stack traces". | [ChromeDevTools/chrome-devtools-mcp `README.md`](https://github.com/ChromeDevTools/chrome-devtools-mcp/blob/main/README.md) |
| N10 | **Storybook MCP** (preview: "The API may change") has three toolsets, "development, docs, and testing". `test-run` "Runs tests for specific stories and returns results, including any accessibility issues (if configured)"; `docs-list` and `docs-show` return a component index and one component's documentation and props. | [storybook.js.org/docs/ai/mcp/overview](https://storybook.js.org/docs/ai/mcp/overview) |
| N11 | **Stagehand** is "the SDK for browser agents" with `act`, `observe` and `extract` in natural language, for TypeScript, Python and Go; `act()` self-heals when the site changes. **Browser Use** is "The open-source browser agent" (Python, TypeScript). Neither README describes emitting a test, and neither supports Java. | [browserbase/stagehand `README.md`](https://github.com/browserbase/stagehand/blob/main/README.md); [browser-use/browser-use `README.md`](https://github.com/browser-use/browser-use/blob/main/README.md) |
| N12 | **Testing Library** has an experimental `throwSuggestions`: "if better queries are available, the test will fail and provide a suggested query to use instead" ("A better query is available, try this:"). | [testing-library-docs `dom-testing-library/api-configuration.mdx`](https://github.com/testing-library/testing-library-docs/blob/main/docs/dom-testing-library/api-configuration.mdx) |
| N13 | **Phoenix LiveViewTest** `open_browser/2`: "Open the default browser to display current HTML of `view_or_element`", from a test that runs without a browser. | [Phoenix.LiveViewTest](https://phoenix-live-view.hexdocs.pm/Phoenix.LiveViewTest.html) |
| N14 | **Quarkus continuous testing**: "tests run immediately after code changes have been saved"; "Quarkus detects which tests cover which code, and uses this information to only run the relevant tests when code is changed." | [quarkus.io/guides/continuous-testing](https://quarkus.io/guides/continuous-testing) |
| N15 | **Quarkus Dev MCP**: in dev mode "you can expose a Model Context Protocol (MCP) server"; tools come from extensions (the examples change a log level and a configuration property); "This feature is currently experimental." The page lists no test tool. | [quarkus.io/guides/dev-mcp](https://quarkus.io/guides/dev-mcp) |
| N16 | **Develocity Predictive Test Selection** "identifies the tests relevant to a code change and runs only those tests", with a model trained "on code changes and test outcomes from your Build Scan data"; Gradle and Maven; "Tests must be run via JUnit Platform"; "an extension and may not be enabled for your installation". **Launchable** describes the same idea: "uses machine learning to select the right tests to run for a specific code change". | [docs.develocity.ai/predictive-test-selection](https://docs.develocity.ai/predictive-test-selection/); [help.launchableinc.com](https://help.launchableinc.com/features/predictive-test-selection/) (page dated three years ago) |
| N17 | **Surefire** `rerunFailingTestsCount`: "Tests will be run until they pass or the number of reruns has been exhausted"; a test that passes on a rerun "will be counted as a flaky test" (`Flakes: 1`, `flakyFailure` in the XML); `failOnFlakeCount` fails the build. | [maven.apache.org/surefire … rerun-failing-tests](https://maven.apache.org/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html) |
| N18 | **Cypress Cloud**: a flaky test "passes and fails across retries without any code change", with analytics, severity and merge gates. AI features include "Error Summaries … plain-language explanations of what went wrong" and "UI Coverage Test Generation generates targeted Cypress tests directly from coverage gaps". **UI Coverage** is "a visual map of which interactive elements your tests cover, and which they miss": "tested ÷ total", computed in Cypress Cloud from recorded runs. | [cypress-documentation `flaky-test-management.mdx`](https://github.com/cypress-io/cypress-documentation/blob/main/docs/cloud/features/flaky-test-management.mdx), [`cypress-ai-features.mdx`](https://github.com/cypress-io/cypress-documentation/blob/main/docs/cloud/features/cypress-ai-features.mdx), [`ui-coverage/get-started/introduction.mdx`](https://github.com/cypress-io/cypress-documentation/blob/main/docs/ui-coverage/get-started/introduction.mdx) |
| N19 | **PIT** (Java mutation testing): "Faults (or mutations) are automatically seeded into your code, then your tests are run. If your tests fail then the mutation is killed, if your tests pass then the mutation lived." Line coverage "does not check that your tests are actually able to detect faults in the executed code". | [pitest.org](https://pitest.org/) |
| N20 | **MAEWU** (research, ICSME 2021): "a mutation analysis framework for assessing web UI test suites, which … mutates the dynamic DOM in the browser instead of the source code", with 16 operators. Its premise: "there exists no reliable method to ascertain the fault-finding capabilities for UI test suite of any given web app." | [Yandrapally and Mesbah, *Mutation Analysis for Assessing End-to-End Web Tests*](https://people.ece.ubc.ca/amesbah/resources/papers/icsme21.pdf) |
| N21 | **Meta's ACH** generates tests with a model, guided by mutants: "it focuses on generating currently undetected faults that are specific to an issue of concern". Unit level. | [arXiv 2501.12862, *Mutation-Guided LLM-based Test Generation at Meta*](https://arxiv.org/abs/2501.12862) |
| N22 | **AI-native products**, only what the pages read say. Meticulous: "monitors your and your agents' daily interactions with your application", "evolves a suite of visual end-to-end tests", "Never write, fix or maintain a test again". QA Wolf's docs: with a coding agent connected, "the agent explores your app, writes the test, and runs it", with an MCP setup. Momentic: "Investigate failures and repair tests as your app changes", with a "plugin, skills, and MCP servers". | [meticulous.ai](https://www.meticulous.ai/); [docs.qawolf.com](https://docs.qawolf.com/); [momentic.ai/docs](https://momentic.ai/docs/) |
| N23 | **Claude Code hooks** are "user-defined shell commands, HTTP endpoints, MCP tool calls, LLM prompts, or subagents that execute automatically at specific points". After `PostToolUse` a hook's stderr is "shown to Claude"; a `Stop` hook exiting 2 "Prevents stopping; Claude continues". | [code.claude.com/docs/en/hooks](https://code.claude.com/docs/en/hooks) |

### Inferences

- **Everything an agent can use to look at a running Vaadin application today goes through a browser** (Playwright MCP or CLI, Chrome DevTools MCP; N1, N9, landscape AI3–AI5), and Vaadin's own dev loop sends the agent there (N8). What comes back is an ARIA snapshot or Playwright code. Neither is in Unified terms, and Playwright's Java code generation writes raw Playwright, not Dramafinder or the Unified API.
- **The JS Test Runners carry the loop features** (N2, N5): change-based selection, UI mode, repeat runs, flaky marking, screenshot comparison belong to Playwright Test, which Java does not have. On the JVM the pieces are scattered: Surefire for reruns (N17), Quarkus for change-aware reruns (N14), Develocity for selection (N16), PIT for mutation (N19). None knows what a view or a User Step is.
- **Nobody checked asks whether a generated UI test can fail.** Playwright's generator "verifies selectors and assertions live" (landscape AI1), which shows the test passes. The products in N22 promise maintenance, not fault detection. Mutation of UI tests exists as research (N20) and at unit level (N19, N21).

---

## 3. Already there against the others

"Here" is the Unified API on `main`. *Ahead*, *level*, *behind* and *nobody* are this note's reading of the facts cited [opinion].

| Dimension | Here | Others | Reading |
|---|---|---|---|
| Same test without and with a browser | yes, three Testing Tools (H1) | Angular CDK harnesses: one harness on TestBed and WebDriver (landscape A3–A4); no web framework checked runs the same *test* on both by choice | ahead |
| In-JVM speed for the inner loop | Browserless by default (H1) | Vaadin Browserless, Karibu, LiveViewTest, bUnit (landscape §1) | level (inherited) |
| Lookups in user terms, typed | yes (H2) | Playwright `getByRole`, Testing Library (landscape AG2–AG3); Dramafinder | level |
| No waits to write | Auto-Wait, retrying assertions (H3) | Playwright; Dramafinder 2.0; not TestBench or Browserless Test (`docs/spec/appendix-gaps.md`) | level with Playwright |
| Failure that says what to do next | Near Misses, `Did you mean`, fixed lines (H7) | Testing Library `throwSuggestions` (N12, experimental); Playwright "Copy prompt" (landscape AI8); Cypress Error Summaries by a model (N18) | ahead: deterministic, every Testing Tool |
| Failure and run as data | `user-steps.json` per test (H4, H7); no run-level file (H19) | Playwright JSON reporter and trace CLI (landscape AI7); Vaadin dev loop `--json` (N8); Cypress Cloud MCP (landscape AI11) | level per test, behind per run |
| Step record of passing tests | User Steps, always on (H4) | Playwright trace, Cypress Command Log (landscape H2, H5) | level; in text and tool-neutral, which the others are not |
| Behaviour diff for the reviewer | Changed in this PR, diff against the Base Run (H5) | Meticulous PR comment on visual changes (N22); Cypress "Self-healed" tags (landscape H5). No step-level diff of a test's behaviour found | ahead |
| Style checks for agent-written tests | three ArchUnit rules (H11) | Selenium's review checklist, prose (landscape H7); linters such as `eslint-plugin-playwright` (not verified here) | level |
| Guidance shipped and installed | skill in the jar, managed `AGENTS.md` section (H12) | Next.js, Laravel Boost, Phoenix, Angular, Dramafinder, Vaadin dev loop (landscape AG8, AG11, AI14–AI21) | level |
| Guidance measured | two eval suites with baselines (H13) | Next.js evals (not verified, landscape "Open questions") | ahead |
| Migration from older tests | recipes plus a skill (H14) | none comparable found for Vaadin | ahead |
| A text view of the running page | Browserless tree on failure only (H8); #204 open | Playwright ARIA snapshot and MCP (landscape AG1, AI3); Browserless tree (N7); Storybook `docs-show` (N10) | behind; planned |
| An agent acts on the running application | no | Playwright MCP and CLI, Chrome DevTools MCP, Stagehand (N1, N9, N11) | behind |
| Session or recording to test code | no; #215 open | Playwright codegen and `--codegen java` (N1); Copilot recorder to TestBench ([copilot-recorder.md](copilot-recorder.md)); QA Wolf (N22) | behind; planned |
| Repair of a failing test | `Did you mean` to paste by hand; #206 open | Playwright healer, `cy.prompt` (landscape AI1, AI10); Momentic (N22) | behind; half the input exists |
| Does the test catch a bug | no (H11) | PIT at code level (N19); research at UI level (N20) | nobody at this level |
| Two Testing Tools disagree | found by hand (H18) | nothing found | nobody |
| Accessibility checks | `withAccessibleName` lookups only | axe for Playwright Java (N4); Pest `assertNoAccessibilityIssues` (landscape L2); Storybook (N10) | behind |
| Screenshot comparison | `page.screenshot(..)` only (H17) | TestBench `compareScreen` (N6); Playwright Test, JS (N5) | behind; reachable through the Escape Hatch |
| Console errors, server logs | no | Pest `assertNoSmoke` (landscape L2); Next.js console forwarding (landscape AG12); Playwright MCP console tool (N1) | behind |
| Clock, network | Escape Hatch (H17) | Playwright Clock and routing (N3) | behind; low need for server-driven Flow views [opinion] |
| Flaky tests | no (H20) | Surefire (N17); Playwright (N2); Cypress Cloud (N18) | behind; Surefire covers the basics |
| Test selection by change | marks changed scenarios in the report, test side only (H5, H6) | Quarkus (N14); Develocity (N16); Playwright `--only-changed` (N2) | behind |
| Coverage in user terms | no | Cypress UI Coverage, cloud (N18) | behind |
| Docs for agents | skill; no `llms.txt` (#205) | most framework sites (landscape AG7) | behind; planned |

---

## Roadmap candidates

Effort is a guess: **S** about a day or two for an agent with review, **M** about a week, **L** several weeks or a prototype first. "Agent", "with AI" and "without AI" are the three readers of the question. Everything in this section is [opinion] except the cited prior art.

### C1. Findable view: the page as lookups to paste

**What.** A text view of the current page in which each line is one component a Unified UI Test can find: its kind in User Step words, its name, its state, and the exact lookup.

```
text field “Name” = “Alan Turing”          page.find(UnifiedTextField.class).withLabel("Name")
button “Save” (disabled)                   page.find(UnifiedButton.class).withText("Save")
grid, 12 rows: Name, Email                 page.find(UnifiedGrid.class)
button (no name; test id “close”)          page.find(UnifiedButton.class).withTestId("close")
```

Written on failure next to the evidence on every Testing Tool, available as `page.describe()`, and the observation format of C4. A lookup is listed only when it matches exactly one component; an ambiguous one is shown with the container that tells it apart, as the failure message already does.

**Who.** Agent: writes lookups from what is there instead of guessing from source (H12 step 1, landscape AG13 "verify, don't infer"). With and without AI: a view's testable surface at a glance. The "(no name)" lines are an accessibility finding for free.

**Prior art.** Catch-up in idea, new in form. Playwright's ARIA snapshot with refs and `generate-locator` (landscape AG1, AI5), `browser_generate_locator` (N1), Testing Library's suggested query (N12) and Browserless's tree (N7) each give half: a view, or a lookup for one element. None found prints a typed lookup that is valid without and with a browser. Checked: the sources of N1, N7, N12 and landscape AG1, AI5.

**Fit.** This is a proposed answer to #204's open question ("Is the snapshot format ours, or … Playwright's ARIA snapshot YAML?"): ours, in the words User Steps already use. The Near Miss code already re-runs a `QuerySpec` without its filters and reads label, text, placeholder, test id and accessible name of each match (`docs/spec/12-readable-tests.md`, "Requirements on Testing Tool adapters"); the view is that for every `@UnifiedComponent` type, plus a uniqueness count. Core only, no SPI change expected. #215 needs the same "class to Wrapper, best unique filter" logic (its decisions 6–8), so build it once.

**Effort.** M. **Risks.** Large views (cap and scope it: `dialog.describe()`); cost in a browser, where each type is a query (on failure and on demand only); custom components without a Wrapper show as `findComponent(X.class)` only on Browserless.

### C2. Cross-tool divergence report

**What.** Given the evidence directories of two runs of the same commit on different Testing Tools, list the scenarios that differ: passed on one and failed on the other, the first User Step where they part, the `Found` values on each side, and whether the difference is a documented Limitation (`docs/spec/09-capabilities.md`) or unexplained. Shown as a section of `summary.md` and `report.html`, as the Base Run diff is.

**Who.** Agent: the most useful sentence it can read after "passes on Browserless, fails on Playwright" is whether that is a known difference or its own mistake; the skill asks for both runs (H12) and gives no help when they disagree. With and without AI: the same, and confidence that the fast run means something. The maintainer: adapter bugs such as #84, #88, #259 and #267 found by a report instead of by hand (H18).

**Prior art.** None found. Playwright's report shows results per browser project side by side; it does not compare what two runs observed. Angular's harness docs (landscape A3–A4) describe no such report. Checked: Playwright's CLI and reporter docs (N2), the Cypress Cloud pages of N18, Vaadin's testing docs (landscape B1–B4). No other tool runs one test on two kinds of Testing Tool, so there was nothing to compare.

**Fit.** `user-steps.json` already carries `tool`, step text, values and the failure. The matching and the diff exist for the Base Run (`vaadin.testing.user-steps.baseline-dir`, scenario matching by class, method and display name). A second directory setting, such as `vaadin.testing.user-steps.compare-dir`, and one more report section. No SPI change.

**Effort.** S–M. **Risks.** Noise from values that differ by design (`getText()` as rendered); needs a table of known differences, which is the Limitations table as data. Two runs must come from the same commit.

### C3. Weak-test checks: does the test fail when the feature is broken?

**What.** Two levels.

1. *No check* (S). From `user-steps.json`: a scenario with no `see` or `see no` step, or whose last User Steps are actions with no check after them, is reported in `summary.md` and can fail a build. This is true at run time, where the ArchUnit rules read bytecode and cannot tell (H11).
2. *Step-skip reruns* (M). On Browserless, rerun a scenario once per action User Step with that step turned into a no-op. If the scenario still passes, either the step is not needed or no later check depends on it. Report: `skipping “click button ‘Save’” still passes: nothing checks the save`. One rerun is milliseconds to tens of milliseconds in the JVM once the application context is up, so a scenario of ten steps is cheap; in a browser the same idea would be too slow to run by default.

A third level, mutating the application's code under the same tests with PIT (N19), needs no feature here, only a guide page; whether PIT works under `@UnifiedSpringBootTest` on Browserless was **not tested**.

**Who.** Agent: the missing half of "prove it". An agent can make a test pass by weakening it; H5 shows the reviewer that after the fact, this stops it before. With AI: the reviewer no longer has to judge assertion strength by reading. Without AI: the same check on hand-written tests.

**Prior art.** New at this level. PIT mutates code and is unit-oriented (N19). MAEWU mutates the DOM in a browser, as research (N20). ACH is unit level and model-driven (N21). Playwright's generator checks that assertions pass (landscape AI1), not that they can fail. A search also returned a community `playwright-mutation-gate` and a tool named Mutta; neither was read (see "Not verified"). Checked besides: Playwright test agents docs, Cypress AI pages (N18), the product pages of N22.

**Fit.** Level 1: the report writer. Level 2: the Wrapper proxy is the one place every action passes (`WrapperHandler.invoke`, [user-step-recording.md](user-step-recording.md)); a run setting names the step number to skip; a small launcher reruns the test (JUnit Platform Launcher, or the neutral lifecycle of H15). Results go to `summary.md`.

**Effort.** S, then M. **Risks.** A skipped step often breaks the *next action* rather than a check (fill skipped, Save fails validation): that counts as detected, which is right. Steps that are needed only for set-up read as weak; report, don't fail, by default. Grid row actions bypass the proxy ([user-step-recording.md](user-step-recording.md)); check that this is still so.

### C4. Live session: an agent drives the application through the Unified API

**What.** A command-line session over a running application: `open PeopleView`, `describe` (C1), `click button "Save"`, `fill text field "Name" "Alan"`, `expect notification "Saved Alan"`. Every command is a Unified API call, so each is recorded as a User Step; `export` writes the session as a Unified UI Test in the canonical style, with `Did you mean` on a miss. Browserless by default, where there is no browser to start; `--tool playwright` for the same session in a browser. A CLI with a skill first; an MCP server only if asked for, since Playwright itself now steers coding agents to CLI plus skills (landscape AI4).

**Who.** Agent: the explore, act and emit loop that Playwright MCP gives for raw Playwright (N1), in the terms the test is written in, and without a browser. With AI: the same through the assistant. Without AI: a REPL to try lookups.

**Prior art.** The loop is catch-up: Playwright MCP and `--codegen java` (N1), `playwright-cli generate-locator` (landscape AI5), the generator agent (JS only, landscape AI1–AI2), Copilot's recorder writing TestBench ([copilot-recorder.md](copilot-recorder.md)), QA Wolf (N22). New: no browser needed, and the output is a portable Unified UI Test. No tool found drives a Vaadin application in the JVM for an agent: Vaadin's MCP server serves documentation (landscape AI19), its dev loop sends agents to Playwright MCP (N8), Quarkus Dev MCP lists no UI tool (N15). Checked: those sources and a web search on 2026-11-17 for a Vaadin or Browserless MCP that exposes the component tree, which found none.

**Fit.** ADR 0008 (H15) made the lifecycle Test Runner-neutral so that other integrations are "possible without an SPI break": a session is a Test Runner with no test method. The code writer is the one #215 needs; the two should share a module (`unified-test-recorder` in #215's decision 2). #227 (Browserless without JUnit) removes a dependency but does not block.

**Effort.** L; prototype first, as #215 does. **Risks.** Start-up: the Spring context takes seconds, so the session must stay alive between commands (a daemon, as Vaadin's dev loop is, N8). Test data and login for a session outside a test. A Browserless session is not the user's running application but a second instance in a test JVM: say so. Overlap with #215: decide whether the Unified Recorder is a second front end on the same core.

### C5. Server logs and browser console per User Step, and a smoke check

**What.** Attach to each User Step the server log lines (WARN and above by default) and uncaught exceptions that occurred while it ran, and in a browser the console errors. Show them under the step in `user-steps.txt`, `user-steps.json` and `report.html`. Add `assertThat(page).hasNoErrors()` or a run setting that fails a scenario on a server exception or console error.

**Who.** Agent: "click button 'Save'" followed by the `NullPointerException` it caused, in one file, instead of a screenshot and a separate log. With and without AI: the same in the HTML report.

**Prior art.** The smoke check is catch-up: Pest `assertNoSmoke()` (landscape L2), console forwarding in Next.js (landscape AG12), console tools in Playwright MCP and Chrome DevTools MCP (N1, N9). Server logs tied to the test's step are new for the tools checked: Playwright's trace holds the browser's console and network only (landscape H2). It is possible here because the application runs in the test's JVM under `@UnifiedSpringBootTest`, on every Testing Tool.

**Fit.** A log appender active between a step's start and end (the recorder has both); `AdapterSession` gains an optional "console messages since" for browsers. Related to `vaadin.testing.browserless.route-errors-to-session` and #101.

**Effort.** S–M. **Risks.** Logging frameworks differ (Logback first; JUL as fallback). Background threads log outside the step that caused them: attribute by time and say so. Secrets in logs: mask `page.secret(..)` values there too.

### C6. Apply `Did you mean`: repair as a diff, without a model

**What.** An OpenRewrite recipe that reads the failures in an evidence directory and, where a failure has a `suggestion` and a `codePath`, rewrites the lookup at that line. It proposes a diff; it does not run at test time.

**Who.** Without AI: one command after a label rename broke forty tests. Agent and with AI: a cheap, deterministic first pass before a model looks at what is left.

**Prior art.** New in form. Playwright's healer and `cy.prompt` repair with a model (landscape AI1, AI10); Momentic says it repairs tests (N22). #206 already decides the direction, "follow the healer model (a reviewed diff)". A repair that needs no model because the failure already carries the fix was not found. Checked: the sources of landscape AI1, AI10 and N12, N22.

**Fit.** `suggestion` and `codePath` are in schema version 1 (H7); the module with OpenRewrite is `unified-test-rewrite`. `Did you mean` appears "only when one Near Miss is clearly what the test meant", so the recipe inherits that caution.

**Effort.** S. **Risks.** It fixes the test when the application may be wrong (the label change was a bug). The skill's rule "decide who is wrong" (H12) applies to people too: the recipe's output should list each rename so a reviewer sees "Email → E-mail in 40 lookups" as one decision.

### C7. The loop, packaged: run verdict, hooks, `llms.txt`

**What.** (a) A run-level `summary.json` next to `summary.md`: counts, the failed scenarios with their failure objects, paths. (b) An optional Claude Code hook, installed by `AddUnifiedTestingSkill` on request: after an edit to a test or a view, run the matching test class on Browserless and return the failure's fixed lines. (c) `llms.txt` for the docs site and the API reference in the jar (#205).

**Who.** Agent, mostly. (a) also serves CI tooling.

**Prior art.** Catch-up. Vaadin's dev loop returns a verdict by exit code and `--json` (N8); Angular's MCP has `run_target` (landscape AI14); Laravel Boost installs guidelines and skills (landscape AI18); hooks can return output to the agent (N23); `llms.txt` is common (landscape AG7).

**Fit.** The report writer for (a) (H19); the existing recipe for (b); #205 for (c). A hook is specific to one agent product: keep it optional and separate from the skill.

**Effort.** S each. **Risks.** A hook that runs Maven on every edit is slow unless the test JVM stays warm; that ties (b) to C4's daemon or to Quarkus-style continuous testing (N14).

### C8. Coverage in user terms

**What.** From the run's `user-steps.json` files and, on Browserless, the route registry and the findable view (C1): which routes no scenario opens, and which findable components on visited views no User Step touched. `people: button “Delete” never clicked; text field “Phone” never filled or checked`.

**Who.** Agent: a work list for "add tests for this view", better than line coverage because it is in the words tests are written in. With and without AI: a review aid.

**Prior art.** Catch-up in idea: Cypress UI Coverage maps "which interactive elements your tests cover" and generates tests from gaps, in Cypress Cloud (N18); Meticulous tracks "code branches executed" (N22). New: computed locally, from the server-side tree, with no cloud. Line coverage of view classes by Browserless tests through JaCoCo already works in principle and needs no feature here (not tested).

**Fit.** Needs C1. A report section, plus a Browserless-only walk of visited views at the end of each scenario.

**Effort.** M. **Risks.** Components that appear only after an action (dialogs) are seen only if some scenario opens them; the number is a floor, not a score. Avoid a percentage target: it invites tests that touch without checking, which C3 then has to catch.

### Further candidates, not in the shortlist

| Candidate | What | Kind and prior art | Fit | Effort | Why lower |
|---|---|---|---|---|---|
| C9. Accessibility checks | `assertThat(page).hasNoAccessibilityViolations()` with axe in a browser (`@RequiresBrowser`); on Browserless a small set of server-checkable rules (field without label or accessible name, icon-only button without one) | Catch-up for the browser: axe for Playwright Java (N4), Pest (landscape L2), Storybook (N10). The Browserless subset is new; N7 and the Browserless docs name no such check | Adapter capability; the Browserless subset falls out of C1's "(no name)" lines | M | axe finds only "some common accessibility problems" (N4); C1 gives the cheapest part |
| C10. Committed User Steps | An expected `…steps.txt` per scenario in the repository; the run compares and `-D…update` rewrites, so a behaviour change shows in the pull request's own diff on any forge | Snapshot testing applied to behaviour. Playwright's `toMatchAriaSnapshot` asserts page state (landscape AG1), not the steps of a test; Playwright's generated tests carry their plan as comments, unchecked (landscape H3). No step-level equivalent found | Report writer; the diff exists (H5) | S–M | The Base Run diff covers the review case already; snapshot files invite blind updates |
| C11. Scenarios for a changed view | Record the routes each scenario opens in `user-steps.json`; given changed view classes, list or run the scenarios that open them | Catch-up: Quarkus (N14), Develocity (N16), Playwright `--only-changed` (N2). A route-based map is specific to Vaadin | `user-steps.json` gains a field; a JUnit discovery filter | M | Unsafe as a CI gate (services, layouts, shared components); useful for the agent's local loop and for ordering. Closes the gap of H6 |
| C12. "The application is wrong" eval fixtures | Writing-skill fixtures where the demo view has a planted bug and the right outcome is to stop and report, per the skill's own rule | Extends H13; all six fixtures today are on a working application. No prior art sought beyond landscape "Open questions" | `unified-test-rewrite/skill-eval/writing/` | S–M | Not a user feature, but it measures the costliest agent mistake; do it with C3 |
| C13. Flaky check in "prove it" | The skill runs a new test several times in a browser; `summary.md` marks a scenario that passed on a rerun | Catch-up: Surefire flakes (N17), Playwright `--repeat-each` (N2), Cypress Cloud (N18) | Skill text; read Surefire's `flakyFailure` | S | Surefire does most of it; Auto-Wait removes the main cause |
| C14. Unified Recorder | Rule-based recording in Copilot | Planned: #215. Catch-up: Copilot recorder, Playwright codegen | Shares C1 and C4's writer | L | Already scoped; sequence after C1 |
| C15. Screenshot comparison | `assertThat(page).matchesScreenshot(..)` under `@RequiresBrowser` | Catch-up: TestBench (N6), Playwright Test JS (N5) | Adapter capability | M | Rendering "can vary based on the host OS" (N5); reachable through the Escape Hatch on Selenium today |
| C16. Clock and network | A portable clock; request mocking | Catch-up: Playwright (N3) | A Spring `Clock` bean covers Browserless without any API | — | Document the pattern in the guide rather than add API; Flow views rarely call the network from the browser |
| C17. Vaadin MCP and Skills know the Unified API | Ask Vaadin | Planned: #205, third point | — | — | Not this project's to build |

### Order [opinion]

C1 first: #204 is already open, and C4, C8, C9 and #215 build on it. C2 and C3 level 1 are small, need nothing else, and are the two that make the project's unique position visible. C6 and C7(a) are a day each and close #206's cheap half. C5 next. C4 is the largest bet and the one most likely to change how agents use the project; prototype it once C1 exists, and decide then whether #215 is a separate tool or a second front end.

---

## Not verified

- **Mabl, Testim, Octomind, Applitools:** their documentation could not be read from this session (HTTP 403, 522, a blocked host, 404). Nothing is claimed about them. Self-healing locators and visual AI are commonly attributed to them; that was not confirmed here.
- **Meticulous, QA Wolf, Momentic, Launchable:** only the one page cited for each was read; the first is a product home page, and the Launchable page is three years old (its login link points to "cloudbees-smart-tests"; the current product name was not confirmed).
- **WebdriverIO MCP (`@wdio/mcp`), Mutta, `playwright-mutation-gate`:** seen in search results only, not read.
- **Chrome DevTools MCP's tool list:** the README refers to a separate tool reference that was not read; N9 is from the README's description.
- **Vaadin Skills and Dramafinder's README:** not re-read (the URLs tried returned 404); the landscape note's AI19 and AI21, two days old, are relied on. Whether Vaadin has added a testing skill since `vaadin/docs@f0394e7` is unknown.
- **Playwright for Java lacking Playwright Test's features** (N2, N5): inferred from those pages being JS-only; the Java docs were not searched for equivalents.
- **PIT and JaCoCo with `@UnifiedSpringBootTest` on Browserless:** not run.
- **Nothing was run end to end**: no MCP server, agent, product or candidate above. Effort figures are guesses.
- **"Nobody has this"** for C2, C3, C5 (server logs per step), C6 and the Browserless part of C4 rests on the sources listed under each and one web search each; it is not an exhaustive search, and AI test tooling ships monthly.
- Except the Vaadin dev-loop page, external pages were read at `main` or live on 2026-11-17 and are not pinned.
