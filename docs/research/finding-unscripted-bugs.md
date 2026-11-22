# Finding bugs no test scripts: exploration, models, properties, accessibility, screenshots, mutation

Research for [#502](https://github.com/parttio/unified-testing-tool/issues/502), part of the map [#499](https://github.com/parttio/unified-testing-tool/issues/499) ("Roadmap: help developers and agents catch bugs, write tests and debug failing tests").

**Question.** Which techniques find UI bugs that no written scenario checks for? What do the tools that offer them actually do? Which of them could run on the Unified API, using the Findable View and Browserless's speed?

This note builds on [agentic-testing-environment-roadmap.md](agentic-testing-environment-roadmap.md) and does not repeat it. Its sources are cited as "roadmap N4", "roadmap C9" and so on. The note covers what that note left out: exploration, models and properties, and the fit of each technique with the Findable View that has shipped since ([ADR 0009](../adr/0009-findable-view-in-lookups-not-aria-yaml.md)).

The research was done on 2026-11-22 against `main` at `39411f6`. External pages were read that day from the URLs in [Sources](#sources), and versions come from Maven Central and npm on the same day. Labels: a **fact** has a source (a file path, an issue or a URL). **[opinion]** marks this note's own reasoning.

## Answer in short

1. **A technique that finds unscripted bugs needs an oracle no scenario wrote.** The tools differ mostly in that oracle:
   - a crash or uncaught error: monkeys, crawlers, WebExplor;
   - a rule about the page: axe, layout checks;
   - a property the developer states once: jqwik, fast-check, Quickstrom;
   - an earlier picture: screenshot comparison;
   - "the tests would notice": mutation.

   AI explorers add a model's judgement as the oracle. The studies read here measure it as their weak point: misclassified verdicts and "incorrectly identified bugs" (X12, X13).
2. **Browserless already has the cheapest oracle in a form browsers lack.** An exception thrown by an application listener comes back to the test as an exception (`BrowserlessSession.act`). The Findable View already lists every component a Wrapper can act on. A run costs milliseconds. Together these make a **seeded random walk over the Findable View** (a monkey that knows Vaadin components) cheap enough to run thousands of steps per build. It can also shrink a failure to a short Unified UI Test. No tool read offers this for server-side Java UIs: gremlins.js and Crawljax drive a browser, and both are dormant (X1, X3).
3. **Properties and invariants are the step from "it didn't crash" to "it did the right thing".** Quickstrom found bugs in 20 of 43 TodoMVC implementations from one specification (X5). On the JVM, the usual library, jqwik, is in maintenance mode, and since 1.10 it carries an **Anti-AI Usage Clause** that addresses coding agents directly (X6). That rules it out as something the agent skill tells agents to use. A small generator and shrinker of our own, over Wrapper actions, is the better fit [opinion].
4. **Accessibility splits cleanly.** In a browser, axe-core has Java packages for both Playwright and Selenium (X8) and finds "on average 57%" of WCAG issues (X9). On Browserless, axe can't run because there is no DOM. But the Findable View's `(no name)` lines are already the most common class of finding, a control with no accessible name. Turning them into a check costs little.
5. **Screenshot comparison and DOM mutation stay browser-only and noisy.** Their Browserless analogues are new and cheap:
   - instead of pixels, compare the Findable View across a run;
   - instead of mutating the DOM, find **effects no check reads**, that is, Findable View lines an action changed that no later `see` step looks at.

   The second is the Browserless counterpart of MAEWU (roadmap N20). It complements the step-skip reruns of #308.

## What the Unified API has to build on (facts, this repo)

| # | Fact | Source |
|---|---|---|
| H1 | `describe()` returns the Findable View: one line per component a Wrapper can find, with a lookup that is run before it is printed. The Wrapper types covered are 22 built-ins (dialog, notification, every field, checkbox, button, grid, tabs, tab sheet, menu bar, details, upload, message list and input) plus the custom Wrappers the test looked up. | `FindableView.BUILT_IN`, [ADR 0009](../adr/0009-findable-view-in-lookups-not-aria-yaml.md) |
| H2 | Each line is built internally as `record Line(String description, String lookup)`: two strings, not a Wrapper instance. | `unified-test-api/.../internal/FindableView.java` |
| H3 | A field or button with no label, text or accessible name is shown as `(no name)`, `(no name; placeholder …)` or `(no name; test id …)`. The guide says such a line "is a field or button that users of a screen reader can't name either". | `FindableView.java` (state words), [finding-components.md](../guide/finding-components.md#what-is-there-to-find) |
| H4 | On Browserless, "an exception thrown by an app listener" is thrown into the test. In a browser it goes to the session's `ErrorHandler`, which shows the application's error UI. A `route-errors-to-session` setting gives Browserless the browser's behaviour. | `BrowserlessSession.act`, Javadoc |
| H5 | A Browserless scenario takes single-digit milliseconds: the `largeGrid` Direct Test's median is 5.1 ms. | [performance-benchmark.md](../guide/performance-benchmark.md#reading-the-time-benchmark) |
| H6 | In a browser, Layout Findings (sideways scroll, clipped out of reach, overlapping interactive components) are opt-in and report-only by default. They come from one core-owned `measureLayout` script. The Adapter SPI has "no generic JavaScript", because "running scripts is browser-only and can never work on Browserless". The same ADR rules visual regression with pixel diffs out of its scope. | [ADR 0011](../adr/0011-responsive-testing-viewports-layout-findings-viewport-comparison.md) |
| H7 | Passing scenarios that end without a check are listed in the run summary (#287 level 1, merged in #307). Step-skip reruns on Browserless are open as #308. Coverage in user terms (#292) and server logs per User Step with a smoke check (#289) are open. | issues #287, #308, #292, #289 |
| H8 | Open issues contain nothing on exploration, property-based testing, accessibility checks or screenshot comparison beyond the roadmap's C9 and C15. #295 holds the roadmap's lower-ranked candidates. | issue list, 2026-11-22 |

## The techniques

### 1. Monkeys, crawlers and AI explorers

**What the tools do (facts).**
- **gremlins.js** is "a monkey testing library written in JavaScript". Its gremlins (clicker, toucher, formFiller, scroller, typer) act at random, aiming at "triggering JavaScript errors, or making the application fail". Its mogwais only observe: fps, and alert, which stops `alert()` from blocking. Gizmo stops the attack "after 10 errors". A run is repeatable only if "you seed the random number generator". The last npm release, 2.2.0, dates from 2020-07-21 (X1, X2).
- **Android's UI/Application Exerciser Monkey** sends "pseudo-random streams of user events", restricted to the app's packages (`-p`). It stops on a crash, an unhandled exception or an ANR, and the same `-s <seed>` "will generate the same sequence of events" (X4). It is the clearest statement of the classic monkey oracle.
- **Crawljax** crawls "any (even single-page dynamic JavaScript-based) web application" and "produces as output a state-flow graph of the dynamic DOM states and the event-based transitions between them". It is a Java library with plugins. Its last release, `com.crawljax:crawljax-core:5.2.3`, appeared on Maven Central in June 2023 (X3).
- **WebExplor** (ICSE 2021) adds curiosity-driven reinforcement learning to the crawl. It found "12 previously unknown failures" in a commercial app, and 3,466 "exceptions and errors" across the top 50 sites (X11).
- **AI explorers.** Playwright's planner agent "explores your app and produces a test plan" in Markdown. The generator turns that plan into tests (X10). The planner finds flows, not bugs: it does not report a defect. Research agents that judge pass or fail have trouble with the verdict:
  - Chevrot et al. (ISSTA 2025) ran 113 manual test cases on three apps. PinATA reached about 60% correct verdicts, by the arXiv abstract (X12).
  - The LLM "soap opera" tester reports "a significant gap compared to manual execution", "especially under-explored scenario boundaries and incorrectly identified bugs" (X13).

**What it finds.** Crashes and unhandled exceptions, dead ends, and states reachable only by an odd order of actions. With layout and accessibility rules as extra oracles, it also finds those findings on screens no scenario opens.

**Cost.** In a browser, each event is a round trip plus waiting. gremlins' default horde runs for about a minute (X1). On Browserless, an action is a JVM call plus a server round trip simulated in memory (H5). Thousands of steps fit in seconds [opinion; not measured].

**Noise.** It is low with an exception oracle, since an uncaught exception is a bug or an unhandled input. Two cases raise it:
- An application that shows errors as a notification on purpose, for example validation, must not count as a failure.
- Destructive actions (*Delete all*, *Log out*) end the walk early or wreck the fixture.

With a model as the oracle, noise is high (X12, X13).

**Fit.**
- *Browserless:* strong. H1 gives the action surface, H4 gives the oracle and H5 gives the budget. H2 is the gap: the walk needs each line as a Wrapper type and filter, not as a string.
- *Browser:* the same walk, slower. The oracle is the application's error UI or server log, which needs #289. Layout Findings (H6) come for free.
- *Agent skill:* the agent does not explore. It runs the explorer and turns the shrunk failure into a regression test.

### 2. Model-based and property-based UI testing

**What the tools do (facts).**
- **jqwik** is "an alternative test engine for the JUnit 5 platform". Its stateful testing runs an `ActionChain` built with `ActionChain.startWith(..)`. Actions are `Action.Independent` or `Action.Dependent`, with a `precondition(state)` and a `Transformer`. `withInvariant(name, assertion)` is checked after every action. Failing chains are shrunk "to the shortest failing action sequence". By default a chain is 32 actions long at 1000 tries (X6).
  - Version 1.10.2 is current (Maven Central). The README says the project is "in pure maintenance mode".
  - Since 1.10 it carries an **Anti-AI Usage Clause**: "This project is not meant to be used by any 'AI' coding agents at all". Every engine invocation prints to stdout a line addressed to AI agents, which tells them to ignore jqwik's results (X6).
- **jetCheck** (JetBrains, Java 8+) supports "stateful system testing (single-threaded)" with automatic minimisation and "re-running of the minimized test example". It is at version 0.3.0 and describes itself as "by no means supposed to be feature-complete" (X7).
- **fast-check** model-based testing runs `ICommand`s, each with `check(model)`, `run(model, real)` and `toString()`, through `modelRun`, `asyncModelRun` or `scheduledModelRun`. Its `commands` arbitrary shrinks to the commands that actually ran, and `{ seed, path }` replays a failure (X14). The page mentions no UI driver.
- **Quickstrom** checks web apps against a specification in QuickLTL, a linear temporal logic for finite traces. It performs "randomly-chosen interface actions from a list given in the specification" and reads state through CSS selectors (``click!(`#toggle`)``). From one TodoMVC specification it "found bugs or faults in 20 of" the 43 implementations it selected. A safety property with the default subscript 100 took "approx. 42 seconds for passing cases". Its checker "is not WebDriver-specific" (X5).

**What it finds.** Wrong behaviour, not just crashes: a counter that disagrees with the list, a filter that loses an item after *toggle all*. The bugs found are ones nobody wrote a scenario for, because the generator picks the order.

**Cost.** The developer has to state the properties or the model. That is the real cost, and the reason these tools stay niche [opinion]. Each run costs as much as the random walk.

**Noise.** Low, because the oracle is the developer's own invariant. Quickstrom notes the other side: rare faults "are only found infrequently, resulting in flaky tests" (X5). A seed and shrinking turn such a find into a stable regression test.

**Fit.**
- *Browserless:* strong, and a natural extension of the random walk: the developer adds invariants written with ordinary Unified assertions. Quickstrom's actions and queries are selectors; ours would be Wrappers and lookups, which the Findable View already prints.
- *Browser:* the same, at Quickstrom-like run times.
- *Agent skill:* proposing invariants from a view's code is work an agent does well [opinion]. It must not reach for jqwik (X6).

### 3. Accessibility checks, in a browser and without one

**What the tools do (facts).** Roadmap N4 and N10 cover Playwright's Java page and Storybook MCP. New here:
- **axe-core** says "you can find on average 57% of WCAG issues automatically" and aims for "zero false positives (bugs notwithstanding)". Results come in four groups: `violations`, `passes`, `incomplete` ("needs review") and `inapplicable`. It "does not test hidden regions, such as inactive menus or modal windows". JSDOM has only "limited support", and "the `color-contrast` rule is known not to work with JSDOM" (X15, X16).
- Deque's own study gives the 57% figure as "57.38% of issues from first-time audit customers", counted by issue volume over "nearly 300,000 issues". Deque sells the tooling (X9).
- **axe-core-maven-html** has two packages, Selenium and Playwright, both version 4.14.0 on Maven Central. The Playwright one is `com.deque.html.axe-core:playwright`, with `new AxeBuilder(page).withTags(..).include(..).disableRules(..).analyze()` and `getViolations()`, `getPasses()` and `getIncomplete()` (X8).
- **Storybook's accessibility tests** run axe per story in Vitest with Playwright's Chromium. `parameters.a11y.test` is `'off'`, `'todo'` (a warning) or `'error'` (fails "in the Storybook UI and CLI/CI") (X17).

**What it finds.** Missing names and labels, roles, ARIA misuse and contrast, but only on what is rendered at the moment of the scan.

**Cost.** One script injection and one scan per checked page state in a browser. On Browserless the name check is already computed for every failure's Findable View (H3).

**Noise.** axe's rules aim at zero false positives. `incomplete` items need a person. Two things make the scan's timing matter:
- Vaadin overlays (a dialog, a combo box's popup) are scanned only while open, so a scan after each User Step finds more than a scan per page.
- Whether axe's findings on Vaadin's own web components are real, or need `disableRules`, was **not tested**.

**Fit.**
- *Browserless:* only server-checkable rules. The `(no name)` lines (H3) are the largest of them [opinion; the 57% study gives no per-rule split here]. Others are a field whose label is only a placeholder, and an icon-only button. Report them per User Step, like Layout Findings.
- *Browser:* axe through both adapters as an Adapter capability, under `@RequiresBrowser`. It is a second core-owned script beside `measureLayout`, which keeps ADR 0011's "no generic JavaScript".
- *Agent skill:* fix-and-rerun on the Browserless subset is a fast loop. Leave axe for CI.

### 4. Visual and screenshot comparison

**What the tools do (facts).** Covered by roadmap N5 (Playwright Test JS, "rendering can vary based on the host OS") and N6 (TestBench `compareScreen`, default tolerance 0.01). New here: Playwright for Java has `assertThat(locator).matchesAriaSnapshot()` (X18). That is a structural snapshot, which [ADR 0009](../adr/0009-findable-view-in-lookups-not-aria-yaml.md) considered and deferred. ADR 0011 put pixel diffs out of scope (H6).

**What it finds.** Broken styling, theme regressions and overlapping text: bugs nothing else in this note sees.

**Cost.** One screenshot per checked point, plus a reference image per browser and OS that someone keeps current.

**Noise.** The highest here: rendering differences between hosts (roadmap N5), animations, fonts.

**Fit.**
- *Browserless:* none for pixels. The structural analogue is to compare the Findable View of the same User Step against the Base Run. That catches a label renamed, a button gone or a field newly disabled without anyone writing a check. Unlike ARIA YAML, it reads the same on every Testing Tool (ADR 0009) [opinion].
- *Browser:* C15 as is.
- *Agent skill:* none.

### 5. Mutation testing for UI tests

**What the tools do (facts).**
- **PIT** discards "all tests that do not exercise the mutated line" and orders the rest by time. JUnit 5 needs `pitest-junit5-plugin`. "Code in static initializer blocks is not re-run", and slow suites "may not even start running mutations for several hours" (X19). PIT is at 1.30.2.
- **PIT under Spring.** PIT reuses the JVM, so Spring's static context cache survives between test runs. Users report this from both sides: it is what makes the runs fast, and it is what lets state leak. Forcing `@DirtiesContext` everywhere is discouraged as slow (X20; mailing-list and issue reports, not documentation).
- **MAEWU** (roadmap N20) has 16 DOM operators in five categories, such as `TreeDelete` and `TreeMove` on subtrees and content insert/remove/modify. It "clusters equivalent web elements" so a mutant is applied consistently across test runs (X21).

**What it finds.** Tests that pass whatever the application does: the weakness #287 and #308 attack from the test side.

**Cost.** PIT runs one test subset per mutant. Under Browserless this is cheap once the Spring context is cached (H5), but **not tested** (roadmap C3 already says so). DOM mutation needs a browser per mutant.

**Noise.** Equivalent mutants: a change no user could see, such as mutating a log message, survives and looks like a gap.

**Fit.**
- *PIT on Browserless:* a guide page and a measured example, no feature.
- *DOM mutation:* browser-only and slow.
- *Browserless analogue:* the Findable View before and after each action step shows what that step changed. A changed line that no later `see` step targets is an **effect no check reads**, for example `click button 'Save' changed text field 'Total' from 0 to 42; nothing checks it`. That is the cheap, deterministic half of what DOM mutants measure, with no mutant and no rerun. #308 asks whether a step matters. This asks whether a visible effect is checked.

## Comparison

| Technique | Oracle | Browserless | Browser | Noise | Prior art for server-side Java UIs |
|---|---|---|---|---|---|
| Random walk (monkey) | exception, error UI, Layout Findings | strong (H1, H4, H5) | yes, slower; needs #289 for the oracle | low | none found |
| Crawler with a state graph | same, plus dead ends | possible; the "state" is the Findable View | Crawljax (dormant) | low | none found |
| AI explorer | the model's judgement | through #288's live session | Playwright planner (plans only) | high (X12, X13) | none |
| Invariants and models | the developer's properties | strong | yes | low; rare faults flaky without a seed | jqwik (agent-hostile), jetCheck |
| Accessibility | rules | the name subset only | axe via both adapters | low; `incomplete` needs review | axe for Java |
| Screenshot | an earlier image | no (Findable View diff instead) | C15 | high | TestBench |
| Mutation | the tests themselves | PIT (untested), effects no check reads | DOM mutants | equivalent mutants | PIT |

## Candidate features [opinion]

Benefit means benefit for "catch bugs", on the map's scale. Effort is a guess: S is days, M about two weeks, L more.

| # | Candidate | What | Who gains | Benefit | Effort | Prior art | Noise risk |
|---|---|---|---|---|---|---|---|
| U1 | **Explorer on Browserless** | `@UnifiedExplore(route = PeopleView.class, steps = 2000, seed = …)` (name to settle). It does a seeded random walk: each step picks a line of the Findable View and a default action for its Wrapper kind (click, fill a generated value, toggle, choose an item, open a tab, close a dialog). It stops on an exception from the application (H4), shrinks the walk to the shortest failing sequence, and writes that sequence as a Unified UI Test with User Steps. Needs H2's lines as Wrapper type and filter, not strings. | All: developers get crashes no scenario reached; agents get a reproducible failing test instead of a hunch | High | M–L | Android Monkey (X4) and gremlins.js (X1) for the walk; jqwik and fast-check for shrinking (X6, X14). New: over a server-side component tree, emitting a test | Low with the exception oracle. Risks: destructive actions and logout (a per-run lookup skip-list); validation notifications are not failures |
| U2 | **Invariants for the explorer** | `explore.invariant("rows match the counter", page -> …)`, checked after every step with Unified assertions; failures shrink as in U1. Own generator and shrinker, no jqwik (X6). | Developers who can say what must always hold; agents proposing invariants from a view's code | High where used | M on U1 | Quickstrom (X5), jqwik `withInvariant` (X6), fast-check (X14) | Low; the developer writes the oracle |
| U3 | **Unnamed controls as findings (Browserless and browser)** | The Findable View's `(no name)` lines, plus "label only a placeholder", reported per User Step like Layout Findings (`vaadin.testing.a11y-checks=on`, report-only, `=fail` to fail), and `assertThat(page).hasNoUnnamedComponents()`. Every Testing Tool, the same words. | All; especially Browserless-only projects, which have no accessibility check at all | Medium | S | axe's naming rules (X15); new on a server-side tree | Very low |
| U4 | **axe in a browser** | `assertThat(page).hasNoAccessibilityViolations()` and an opt-in per-step scan, through a core-owned axe script that both browser adapters inject (Playwright and Selenium packages exist, X8). `incomplete` goes to the report, not a failure. Measure first how many findings Vaadin's own components raise. | Developers and auditors | Medium | M | roadmap C9, Storybook (X17) | Low per axe; unknown on Vaadin components (not tested) |
| U5 | **Effects no check reads** | Diff the Findable View before and after each action User Step (Browserless: cheap). Report changed lines that no later `see` step targets: "`click button 'Save'` changed text field 'Total'; nothing checks it". | Agents and reviewers: catches tests that act without checking the result, beyond #307's "no check at all" | Medium–high | M | MAEWU (roadmap N20) by idea; none found at this level | Medium: clocks and counters change every step; report, don't fail |
| U6 | **Findable View diff against the Base Run** | For each User Step of a changed scenario, the view's lines that appeared, vanished or changed state since the Base Run. The structural counterpart of screenshot comparison, on every Testing Tool. | Reviewers of a pull request | Medium | M | Playwright ARIA snapshots (X18); TestBench screenshots (roadmap N6) | Medium: data-driven text changes; ignore values, compare kinds, names and states |
| U7 | **Guide: PIT with Unified UI Tests on Browserless** | A measured example in `unified-test-demo` (PIT, `pitest-junit5-plugin`, `@UnifiedSpringBootTest`), with run time and the Spring context caveat. | Teams wanting a mutation score for their UI tests | Medium | S | PIT (X19, X20) | Equivalent mutants; long runs |
| U8 | **Agent exploratory session, reproduce before reporting** | A skill section for #288's live session: explore a view, and report a bug **only** as a failing Unified UI Test that runs on Browserless. A suspicion that can't be reproduced stays a question. | Agents | Medium | S on #288 | Playwright planner (X10); research agents (X12, X13) | High without the "reproduce first" rule |

Screenshot comparison stays as roadmap C15. Nothing here moves it up: U6 covers the structural half on every Testing Tool, and the pixel half keeps its host noise.

**Order.** U3 (small, uses what exists), then U1 (the largest gain, and the only one nobody ships for this stack), then U2 on top of it. U5 pairs naturally with #308, and U6 with the Base Run diff. U4 waits for a measurement of axe on Vaadin components. U7 and U8 are documentation.

## Not verified

- No candidate was prototyped. Browserless step costs for a random walk are extrapolated from the benchmark (H5), not measured.
- PIT under `@UnifiedSpringBootTest` was not run. The Spring caching notes (X20) come from a mailing list and an issue thread, not from documentation.
- axe was not run against Vaadin components, so the noise on Vaadin's shadow DOM is unknown.
- The figures from the Quickstrom and MAEWU papers come from text extracted from their PDFs. The ISSTA 2025 figures come from the arXiv abstract, and the search summary noted that the abstract and the conclusion differ between versions on sensitivity and specificity.
- Maintenance claims for gremlins.js and Crawljax rest on their last release dates (npm, Maven Central), not on statements from the projects. The GitHub repositories could not be read from this session.
- "None found" for U1, U3 (Browserless), U5 and U6 rests on the sources here and a few searches each. It is not exhaustive.

## Sources

| # | Source |
|---|---|
| X1 | gremlins.js README, [github.com/marmelab/gremlins.js](https://github.com/marmelab/gremlins.js/blob/master/README.md) |
| X2 | npm registry, `gremlins.js` 2.2.0 published 2020-07-21, [registry.npmjs.org/gremlins.js](https://registry.npmjs.org/gremlins.js) |
| X3 | Crawljax README, [github.com/crawljax/crawljax](https://github.com/crawljax/crawljax/blob/master/README.md); Maven Central metadata, [`crawljax-core` 5.2.3, lastUpdated 2023-06-01](https://repo1.maven.org/maven2/com/crawljax/crawljax-core/maven-metadata.xml) |
| X4 | Android UI/Application Exerciser Monkey, [developer.android.com](https://developer.android.com/studio/test/other-testing-tools/monkey) |
| X5 | O'Connor and Wickström, *Quickstrom: Property-based Acceptance Testing with LTL Specifications*, [arXiv 2203.11532](https://arxiv.org/abs/2203.11532); [quickstrom.io](https://quickstrom.io/) |
| X6 | jqwik 1.10.2 User Guide, Stateful Testing and Anti-AI Usage Clause, [jqwik.net/docs/current/user-guide.html](https://jqwik.net/docs/current/user-guide.html#anti-ai-usage-clause); [jqwik README](https://github.com/jqwik-team/jqwik/blob/main/README.md) (maintenance mode) |
| X7 | jetCheck README, [github.com/JetBrains/jetCheck](https://github.com/JetBrains/jetCheck/blob/master/README.md) |
| X8 | axe-core-maven-html README and Playwright README, [github.com/dequelabs/axe-core-maven-html](https://github.com/dequelabs/axe-core-maven-html/blob/develop/playwright/README.md); Maven Central `com.deque.html.axe-core:{playwright,selenium}` 4.14.0 |
| X9 | Deque, *Automated Accessibility Coverage Report*, [deque.com](https://www.deque.com/automated-accessibility-coverage-report/) |
| X10 | Playwright Test Agents, [playwright.dev/docs/test-agents](https://playwright.dev/docs/test-agents) |
| X11 | Zheng et al., *Automatic Web Testing Using Curiosity-Driven Reinforcement Learning* (WebExplor), ICSE 2021, [arXiv 2103.06018](https://arxiv.org/abs/2103.06018) |
| X12 | Chevrot et al., *Are Autonomous Web Agents Good Testers?*, ISSTA 2025, [arXiv 2504.01495](https://arxiv.org/abs/2504.01495) |
| X13 | *Automated Soap Opera Testing Directed by LLMs and Scenario Knowledge*, [arXiv 2412.08581](https://arxiv.org/abs/2412.08581) |
| X14 | fast-check, Model based testing, [fast-check.dev](https://fast-check.dev/docs/advanced/model-based-testing/) (fast-check 4.11.0 on npm) |
| X15 | axe-core README, [github.com/dequelabs/axe-core](https://github.com/dequelabs/axe-core/blob/develop/README.md) |
| X16 | axe-core API documentation, [doc/API.md](https://github.com/dequelabs/axe-core/blob/develop/doc/API.md) |
| X17 | Storybook, Accessibility tests, [storybook.js.org](https://storybook.js.org/docs/writing-tests/accessibility-testing) |
| X18 | Playwright for Java, ARIA snapshots, [playwright.dev/java/docs/aria-snapshots](https://playwright.dev/java/docs/aria-snapshots) |
| X19 | PIT FAQ, [pitest.org/faq](https://pitest.org/faq/); PIT 1.30.2 on Maven Central |
| X20 | Spring Framework issue on PIT and `@DirtiesContext`, [spring-framework#26196](https://github.com/spring-projects/spring-framework/issues/26196); pitusers thread on Spring context caching, [groups.google.com/g/pitusers](https://groups.google.com/g/pitusers/c/Yu0mKWHdOHE) |
| X21 | Yandrapally and Mesbah, *Mutation Analysis for Assessing End-to-End Web Tests* (MAEWU), ICSME 2021, [PDF](https://people.ece.ubc.ca/amesbah/resources/papers/icsme21.pdf) |
