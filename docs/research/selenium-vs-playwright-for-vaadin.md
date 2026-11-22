# Selenium or Playwright for Vaadin browser tests, and should Vaadin ship an official Playwright wrapper?

Background research. It does not belong to a single ticket. Vaadin has two Java browser-test libraries: the official **Vaadin TestBench**, built on Selenium WebDriver, and the community **Dramafinder**, built on Playwright. The Unified API already runs on both, through `unified-test-selenium` and `unified-test-playwright`. This note compares the two foundations and asks what Vaadin should do about Playwright. It builds on [web-framework-testing-landscape.md](web-framework-testing-landscape.md) (cited as "landscape B1" and so on) and [agentic-testing-environment-roadmap.md](agentic-testing-environment-roadmap.md), and does not repeat them.

**Question.** (1) What are Playwright's advantages over Selenium, in general and for Vaadin apps in particular, and where does Selenium win? (2) Should Vaadin create an official Playwright-based TestBench, or adopt Dramafinder? What would it gain and cost, and how does a common API like this repo's fit?

Researched 2026-11-22 against `unified-testing-tool` at `7916497`, `vaadin/testbench` at `11908ed` (2026-11-20, `25.4-SNAPSHOT`, Selenium 4.53.0) and `parttio/dramafinder` at `bce0654` (2026-11-21, `2.2.2-SNAPSHOT`, Playwright Java 1.63.0). Both local clones are shallow, so their commit history was not used for activity figures; the GitHub API was used instead. External pages were read on 2026-11-22 from the URLs given and are **not pinned**.

Labels: a **fact** has a source (a file path or a URL). **[opinion]** marks this note's reasoning.

## Answer in short

1. **Playwright's general advantages are real and documented.** Every action waits for the element to be visible, stable, enabled and able to receive events ([actionability](https://playwright.dev/java/docs/actionability)). Locators pierce open shadow roots by default and are strict, so an ambiguous match fails instead of picking the first one ([locators](https://playwright.dev/java/docs/locators)). It also has a trace viewer, codegen for Java, network routing and mocking, and browser contexts that are "fast and cheap to create". Playwright is Apache-2.0, and the browser builds are pinned to each Playwright release.
2. **For Vaadin, the biggest flakiness gap is closed by both libraries in the same way.** Playwright's auto-wait knows nothing about Vaadin's server round trip. Dramafinder polls `window.Vaadin.Flow.clients` after each action (`AutoWait.java`), just as TestBench's `waitForVaadin` does (`TestBenchCommandExecutor.java`). TestBench also searches inside shadow roots in its own query script (`ElementQuery.java`). So for a Vaadin app, Playwright's edge comes from strict locators, waiting for actionability, the tooling (trace, codegen, network) and the licence, not from Vaadin-aware waiting. **[opinion]** This repo's own benchmark runs point the same way on speed: on four Vaadin scenarios, TestBench was slower than Dramafinder in every run, by ×1.2 to ×4.6. The same Unified UI Test was slower on Selenium in 10 of 12 cases. The runs were not designed to compare tools, though (B1–B3, [Speed](#speed-what-this-repos-benchmark-runs-show)).
3. **Selenium wins on standards, reach and Vaadin's install base.** WebDriver is a W3C Recommendation, and its successor, WebDriver BiDi, is being written by the Selenium project together with the browser vendors. Selenium drives real branded browsers, Safari included, which Playwright cannot. It has Selenium Grid and, through it, cloud vendors (TestBench ships Sauce Labs and hub integration). Appium extends the same protocol to mobile. TestBench adds screenshot comparison and has several maintained release lines and a paying user base.
4. **Playwright Java is a second-tier binding.** It is not thread safe. Its JUnit fixtures are marked "experimental". Its Selenium Grid connection is experimental and works only for Chrome and Edge. The richest Playwright features (Playwright Test, the test agents) are documented for Node.js (landscape AI2).
5. **Recommendation [opinion]: yes to official Playwright support, but as a second backend under a Vaadin-owned API, not as a third separate element library.** In the short term, Vaadin should take Dramafinder under its wing (support it, review it, and promise compatibility with each Vaadin release) and keep TestBench Selenium fully supported. In the long term, it should converge on one Vaadin test API with Selenium and Playwright backends. This repo shows that such a layer works today: the Unified API runs one test on Browserless, Playwright (Dramafinder) or Selenium (TestBench). The open business question is licensing. Dramafinder is free, TestBench's browser part needs a commercial subscription, and an official free Playwright option changes what that subscription buys.

## Comparison

| Dimension | Selenium / TestBench | Playwright / Dramafinder |
|---|---|---|
| Standard | W3C WebDriver Recommendation (2018); WebDriver BiDi is a Working Draft (S1, S2) | its own protocol; release notes 1.60–1.65 do not mention BiDi (P10) |
| Waiting for elements | Selenium: implicit wait defaults to 0, explicit waits are opt-in (S5); TestBench: `waitForFirst()` etc. | auto-waits on actionability before every action (P1) |
| Waiting for Vaadin's server round trip | `waitForVaadin` after each TestBench element action (T1, T2) | Dramafinder's `AutoWait.afterAction` after each element action (D2) |
| Shadow DOM | Selenium: explicit `getShadowRoot()` (S6); TestBench queries search the shadow root in their own script (T3) | locators pierce open shadow roots by default; XPath does not (P2) |
| Ambiguous match | TestBench: `first()` or `single()` chosen by the test | strict: more than one match throws (P2); Dramafinder applies no `.first()` (D3) |
| Debugging | screenshots; TestBench screenshot comparison (T4, V3) | trace viewer with DOM snapshots, network and source per action (P3) |
| Recording | Selenium IDE (landscape) | codegen, run from Java's `CLI`, records role, text and test-id locators (P4) |
| Network interception | BiDi network module, still being rolled out (S3) | `route`, `fulfill`, `abort`, HAR (P5) |
| Isolation and parallel runs | a new driver per session; Grid for spreading out (S4) | cheap browser contexts (P6); Java objects are not thread safe (P7); JUnit fixtures experimental (P8) |
| Browsers | real branded browsers through their vendors' drivers, incl. Safari; Selenium Manager fetches Chrome for Testing (S7) | its own Chromium, Firefox and WebKit builds per release; branded Chrome and Edge; no branded Safari; mobile only emulated (P9) |
| Grid and cloud | Selenium Grid (S4); TestBench `@RunOnHub`, Sauce Labs integration (T5) | Selenium Grid connection is experimental, Chrome and Edge only (P11) |
| Mobile | Appium (S8) | device emulation only (P9) |
| Speed in this repo's benchmark (test body only) | Direct Tests slower in every scenario and run (×1.2–×4.6); Unified slower in 10 of 12 | faster; not a controlled comparison (B1–B3) |
| Licence | Selenium Apache-2.0 (S9); TestBench under Vaadin's commercial licence, Pro tier and above (T6, V1, V2) | Playwright Apache-2.0 (P12); Dramafinder Apache-2.0 (D1) |
| Vendor support for Vaadin | Vaadin, with several release lines (T7) | one main maintainer, community add-on (D1) |

## 1. What Playwright gives

### Verified facts

| # | Fact | Source |
|---|---|---|
| P1 | "Playwright performs a range of actionability checks on the elements before making actions … It auto-waits for all the relevant checks to pass and only then performs the requested action." For a click: resolves to exactly one element, Visible, Stable, Receives Events, Enabled, Not Inert. | https://playwright.dev/java/docs/actionability |
| P2 | "All locators in Playwright by default work with elements in Shadow DOM." Exceptions: "Locating by XPath does not pierce shadow roots", and closed-mode shadow roots are not supported. "Locators are strict": an operation that implies one target throws if more than one element matches. `first()`, `last()` and `nth()` are "not recommended". | https://playwright.dev/java/docs/locators |
| P3 | Tracing in Java: `context.tracing().start(..)` with screenshots, snapshots and sources, then `stop(..setPath(..))`. You view the trace with the CLI's `show-trace` or at trace.playwright.dev. It shows each action with the page state before and after, plus the log, the source and the network. | https://playwright.dev/java/docs/trace-viewer-intro |
| P4 | Codegen for Java is run through `com.microsoft.playwright.CLI codegen`. It prefers "role, text and test id locators", refines a locator until it is unique, and can add assertions for visibility, text and value. | https://playwright.dev/java/docs/codegen |
| P5 | `BrowserContext.route()` and `Page.route()` intercept requests, which can then be fulfilled (mocked), modified or aborted. HAR files can be used to mock requests. | https://playwright.dev/java/docs/network |
| P6 | Browser contexts are "equivalent to incognito-like profiles". They are "fast and cheap to create and are completely isolated, even when running in a single browser". | https://playwright.dev/java/docs/browser-contexts |
| P7 | "Playwright Java is not thread safe … it's okay to create multiple Playwright instances each on its own thread." | https://playwright.dev/java/docs/multithreading |
| P8 | `@UsePlaywright` gives JUnit fixtures (`page`, `browserContext`, `browser`, …). The page is titled "(experimental)". Parallel runs need one Playwright instance per thread. The Java docs cover JUnit and TestNG integration and recommend one `BrowserContext` per test. | https://playwright.dev/java/docs/junit, https://playwright.dev/java/docs/test-runners |
| P9 | Playwright runs Chromium, WebKit and Firefox. "With every release, Playwright updates the versions of the browsers it supports", downloaded by default from Microsoft's CDN. It can drive installed branded Chrome and Edge (Stable and Beta). It "doesn't work with the branded version of Safari since it relies on patches". Mobile is emulated only. | https://playwright.dev/java/docs/browsers |
| P10 | Playwright Java 1.65 is the latest release, with Chromium 158.0.8127.18, Firefox 159.0 and WebKit 27.2. The release notes for 1.60–1.65 mention CDP but not WebDriver BiDi (only the first 100,000 characters of the page were read). | https://playwright.dev/java/docs/release-notes, https://github.com/microsoft/playwright-java (README) |
| P11 | Connecting to Selenium Grid 4 through `SELENIUM_REMOTE_URL` "is experimental … There is a risk of Playwright integration with Selenium Grid Hub breaking in the future". It "only works for Google Chrome and Microsoft Edge". | https://playwright.dev/java/docs/selenium-grid |
| P12 | `microsoft/playwright` is licensed Apache-2.0. Playwright Java needs Java 8+ and is "distributed as a set of Maven modules". | https://github.com/microsoft/playwright/blob/main/LICENSE, https://playwright.dev/java/docs/intro |

### Dramafinder (local source)

| # | Fact | Source |
|---|---|---|
| D1 | Apache-2.0. The repository was created 2025-09-12 and has 5 stars and 16 open issues. Releases 1.2.4 (2026-11-03), 2.0.0 and 2.0.1 (2026-11-09), 2.1.0 and 2.2.0 (2026-11-17), and 2.2.1 (2026-11-20). Contributions: `jcgueriaud1` 260, then 4 or fewer for each other account. | `dramafinder/LICENSE`; GitHub API `repos/parttio/dramafinder`, `/releases`, `/contributors` (read 2026-11-22) |
| D2 | Each element action calls `AutoWait.afterAction(page)`, which runs one in-page script. It polls until Flow's clients are idle and any pending `window.Vaadin.Flow.navigation` has settled, with a 30-second limit and a check one task later. It can be turned off with `-Ddramafinder.autoWait=false` or `withoutWaiting(..)`. Actions on a raw `Locator` do not wait. | `src/main/java/org/vaadin/addons/dramafinder/AutoWait.java` L20–100, L147–195; `AbstractBasePlaywrightIT.java` L49 |
| D3 | Finding by label goes through Playwright's locators, with "No `.first()` … two components with that label fail with a strict-mode error". An ADR commits to "Playwright's and Vaadin's standard APIs instead of reimplementing them". | `element/TextFieldElement.java` L155–166; `docs/adr/0002-use-standard-apis-instead-of-reimplementing-them.md` |
| D4 | Built on Vaadin 25.3.0 and Playwright 1.63.0, two minors behind P10. 64 element classes in about 24,000 lines of main code. It ships agent skills (`vaadin-playwright-test`, `vaadin-playwright-screenshot`) and documents tracing in its debugging guide. | `pom.xml` L24, L92–94; `src/main/java`; `tile.json`; `docs/guide/debugging.md` L50–76 |

## 2. What Selenium and TestBench give

### Verified facts

| # | Fact | Source |
|---|---|---|
| S1 | WebDriver (Level 1) is a W3C Recommendation, dated 5 June 2018. WebDriver Level 2 is a Working Draft, dated 9 November 2026. | https://www.w3.org/TR/webdriver1/, https://www.w3.org/TR/webdriver2/ |
| S2 | WebDriver BiDi is a W3C Working Draft (13 November 2026), "a mechanism for remote control of user agents". Its modules are session, browser, browsingContext, emulation, network, script, storage, log, input and webExtension. | https://www.w3.org/TR/webdriver-bidi/ |
| S3 | Selenium: BiDi was "created by the Selenium project together with the browser vendors" and is "the cross-browser replacement for the Chrome DevTools Protocol". "CDP support is temporary until WebDriver BiDi has been implemented." Selenium is moving "its entire implementation from WebDriver Classic to WebDriver BiDi". The documented high-level areas are logging, network and script. | https://www.selenium.dev/documentation/webdriver/, https://www.selenium.dev/documentation/webdriver/bidi/ |
| S4 | Selenium Grid: "Want to run tests in parallel across multiple machines? Then, Grid is for you." | https://www.selenium.dev/documentation/grid/ |
| S5 | Race conditions are "one of the primary causes of flaky tests". The implicit wait defaults to `0`, and explicit waits are loops the test adds. | https://www.selenium.dev/documentation/webdriver/waits/ |
| S6 | Shadow DOM is reached with shadow root methods (`getShadowRoot()` in Java), Selenium 4+ and Chromium 96+. The examples find the host first. | https://www.selenium.dev/documentation/webdriver/elements/finders/ |
| S7 | Selenium Manager gives "automated driver and browser management" and downloads Chrome for Testing when Chrome is missing. | https://www.selenium.dev/documentation/selenium_manager/ |
| S8 | Appium "aims to support UI automation of many different platforms" (mobile, web, desktop) through drivers, and documents WebDriver and WebDriver BiDi endpoints. | https://appium.io/docs/en/latest/intro/ |
| S9 | `SeleniumHQ/selenium` is licensed Apache-2.0. | https://github.com/SeleniumHQ/selenium/blob/trunk/LICENSE |

### TestBench (local source) and Vaadin's pages

| # | Fact | Source |
|---|---|---|
| T1 | `waitForVaadin()` polls a script for up to 40 seconds. The script returns false until `document.readyState` is `complete`, while the dev server is not loaded, and while any `window.Vaadin.Flow.clients[..].isActive()`. | `vaadin-testbench-shared/src/main/java/com/vaadin/testbench/commands/TestBenchCommandExecutor.java` L48–68, L113–137 |
| T2 | `TestBenchElement` calls `waitForVaadin()` after its actions (click, sendKeys, clear, …). | `vaadin-testbench-shared/.../TestBenchElement.java` L144–146, L210–332 |
| T3 | `ElementQuery` runs its own script that concatenates `shadowRoot.querySelectorAll(..)` and light-DOM `querySelectorAll(..)` results. | `vaadin-testbench-shared/.../ElementQuery.java` L1514–1528 |
| T4 | `compareScreen(..)` compares screenshots with reference images. | `TestBenchCommandExecutor.java` L139–151 |
| T5 | `@RunOnHub`, `@RunLocally`, `@BrowserConfiguration`, `ParallelTest` and `SauceLabsIntegration` support runs on a hub and on Sauce Labs. | `vaadin-testbench-shared/.../annotations/`, `.../parallel/SauceLabsIntegration.java`; `README.md` "Running Integration Tests" |
| T6 | The licence file says: "This program is available under Vaadin Commercial License and Service Terms." `TestBench` checks the licence in a static block (`LicenseChecker.checkLicenseFromStaticBlock("vaadin-testbench", ..)`). | `LICENSE`; `vaadin-testbench-shared/.../TestBench.java` L35–38 |
| T7 | Releases on 2026-11-19: 25.3.3, 25.4.0-beta2, 9.6.7, 9.5.12 and 9.3.17, which means several maintained lines. | GitHub API `repos/vaadin/testbench/releases` (read 2026-11-22) |
| T8 | Vaadin's own TestBench repository already uses Playwright: the load-test kit has `demo-web-app-playwright` modules and a `PlaywrightHelper`, with an optional, provided `com.microsoft.playwright:playwright:1.65.0` dependency. | `vaadin-testbench-loadtest/README.md` L13–17; `testbench-loadtest-support/pom.xml` L59–65 |
| V1 | "Browserless testing is free for all users. End-to-end testing requires a commercial TestBench subscription." "The end-to-end testing part in TestBench is based on Selenium." "Playwright is similar in nature to Selenium, with no special Vaadin support." | https://vaadin.com/docs/latest/flow/testing |
| V2 | Pricing: Pro lists "TestBench for automated, cross-browser end-to-end application testing". Enterprise includes everything in Pro. Free has "Browserless UI testing" and no TestBench. | https://vaadin.com/pricing |
| V3 | The E2E page lists the "Vaadin Component API", "Automatic screen comparison", "Test grid support … in parallel on multiple browsers" and "All features available in Selenium". "A commercial Vaadin subscription is required to use TestBench in your project." | https://vaadin.com/docs/latest/flow/testing/end-to-end |

## 3. What this means for Vaadin apps (inferences)

All of this section is **[opinion]**.

- **Shadow DOM.** For Vaadin components, both libraries already hide the shadow DOM: TestBench through its query script (T3), Dramafinder through Playwright's locators (P2, D3). Playwright's advantage shows when a test drops below the wrapper, for example with a raw `page.getByRole(..)` against a third-party web component. Plain Selenium would need `getShadowRoot()` chains there (S6).
- **Waiting and flakiness.** The flakiness that is specific to Vaadin, acting before the server has answered, is handled by the same Flow-client check in both libraries (T1, D2). The general advantage, waiting for an element to be visible, stable, enabled and not covered, is Playwright's (P1); in TestBench the test does that with `waitForFirst()` and `waitUntil`. Strict locators (P2) also remove a quiet source of wrong-element bugs that `first()` hides. Fewer flaky tests on Playwright is a reasonable expectation, but no primary source measures it for Vaadin apps.
- **Speed.** No external primary source compares the two on a Vaadin app. This repo's benchmark runs do measure both, and Playwright was faster in every scenario of every run (B1–B3, next section). The runs were not built for that comparison, so the size of the gap is indicative, not measured. Cheap contexts (P6) and fewer polling calls (D2 runs one in-page script; T1 polls from Java) are plausible reasons.
- **Debugging.** The trace viewer (P3) is the clearest gain for a developer looking at a red CI run. TestBench has screenshots and screenshot comparison, but no trace equivalent.
- **Infrastructure.** Teams that run on Selenium Grid or a cloud grid, need real Safari, or test on real mobile devices are served by Selenium (S4, S8, T5) and only partly by Playwright (P9, P11).
- **Long-term stability.** WebDriver is a standard that browser vendors implement, and BiDi is closing the gap with CDP-style features (S1–S3). Playwright depends on its own patched browser builds (P9) and one vendor's release train. Each release moves the browsers, which gives reproducible runs but means upgrading often.
- **Java maturity.** Playwright Java works, but parts of it are marked experimental (P8, P11) and it is not thread safe (P7). Selenium's Java binding is the reference binding of a standard.

## Speed: what this repo's benchmark runs show

The time benchmark (`docs/spec/13-performance.md`, `docs/guide/performance-benchmark.md`) times the Performance Pairs: one Unified UI Test and one Direct Test per scenario, on each Testing Tool. The Direct Tests are `DramafinderPerfTest` and `TestBenchPerfTest`, written "the way a skilled user of its tool would write it" (spec 13, fairness rule). Each run gives the median test-body time over 30 ABBA rounds after 5 warm-up rounds. The figures below are read from the runs' `benchmark-playwright` and `benchmark-selenium` artifacts (`<tool>.md`).

### Verified facts

| # | Fact | Source |
|---|---|---|
| B1 | Run 9, 2026-11-20, branch `perf/issue-447-selenium-small-form` at `4b55808`. Medians, Playwright vs Selenium. Direct: smallForm 691 vs 1,039 ms, longForm 1,714 vs 7,066 ms, largeGrid 2,389 vs 3,772 ms, comboBox 1,162 vs 1,423 ms. Unified: smallForm 610 vs 985 ms, longForm 1,448 vs 3,715 ms, largeGrid 1,110 vs 3,361 ms, comboBox 1,149 vs 1,378 ms. | [Actions run 44702862629](https://github.com/parttio/unified-testing-tool/actions/runs/44702862629), artifacts `benchmark-playwright`, `benchmark-selenium` |
| B2 | Run 1, 2026-11-19, `main` at `99d645e`. Direct: 538 vs 1,055, 1,603 vs 7,328, 2,272 vs 3,755, 1,072 vs 1,429 ms. Unified: 766 vs 1,560, 1,847 vs 3,793, 2,594 vs 3,362, 1,910 vs 1,382 ms (same scenario order). | [Actions run 44585608178](https://github.com/parttio/unified-testing-tool/actions/runs/44585608178) |
| B3 | Run 4, 2026-11-20, at `eb09a7e`. Direct: 537 vs 922, 1,726 vs 6,167, 2,287 vs 3,591, 1,120 vs 1,316 ms. Unified: 768 vs 1,135, 1,886 vs 3,228, 2,609 vs 3,203, 1,915 vs 1,263 ms. | [Actions run 44698570653](https://github.com/parttio/unified-testing-tool/actions/runs/44698570653) |
| B4 | Each Testing Tool runs in its own job of a matrix on `ubuntu-latest`. Playwright downloads its own Chromium; Selenium uses the runner's browser. Only the test body is timed, not the browser's launch. | `.github/workflows/benchmark.yml` L29–70; spec 13 "How time is measured" |

Selenium ÷ Playwright, from the medians above:

| Scenario | User actions | Direct (TestBench ÷ Dramafinder), runs 1 / 4 / 9 | Unified (Selenium ÷ Playwright), runs 1 / 4 / 9 |
|---|---|---|---|
| smallForm | 3 | 1.96 / 1.72 / 1.50 | 2.04 / 1.48 / 1.62 |
| longForm | 30 | 4.57 / 3.57 / 4.12 | 2.05 / 1.71 / 2.57 |
| largeGrid | find a row in 500, click | 1.65 / 1.57 / 1.58 | 1.30 / 1.23 / 3.03 |
| comboBox | pick from 1,000 lazy items | 1.33 / 1.17 / 1.22 | 0.72 / 0.66 / 1.20 |

### Reading it

All of this subsection is **[opinion]**.

- **The Direct Tests are the cleaner tool-against-tool comparison.** They contain no Unified layer. TestBench was slower in all 12 cells, from ×1.17 to ×4.57, with the largest gap on the form with 30 actions. That fits a cost per action: TestBench's `waitForVaadin` polls from Java after each action (T1, T2), while Dramafinder waits with one in-page script (D2).
- **The Unified columns also measure the adapters, which changed between runs.** Run 1 has none of the performance fixes of 2026-11-20. Run 4 has the Playwright combo-box fix (#445) and a Selenium notification fix (#447). Run 9 also has the Playwright grid-column fix (#446) and the final #447 change (branch history of `perf/issue-447-selenium-small-form`). Unified on Playwright was slower than on Selenium for comboBox in runs 1 and 4, and faster in run 9. So the Unified columns show what this repo's adapters cost on a given day, not what each tool can do.
- **Why these are not yet controlled numbers:**
  - The two sides ran on different runners at the same time (B4), so machine noise does not cancel out as it does within a pair.
  - The browser builds differ (B4).
  - Browser launch and context creation are not timed (B4). That is where Playwright's cheap contexts (P6) would show.
  - There are three runs over two days.
  - A Direct Test also carries its library's own choices: before #447, TestBench waited 500 ms per notification read.
- **Conclusion:** on these Vaadin scenarios, Playwright with Dramafinder was consistently faster than Selenium with TestBench, by roughly ×1.2 to ×4.6 per test body. A controlled cross-tool run now exists: `scripts/bench-unified.sh --compare playwright,selenium`, run weekly by the Benchmark workflow on the runner's Chrome ([#497](https://github.com/parttio/unified-testing-tool/issues/497)). Its first results should replace these figures.

## Should Vaadin ship an official Playwright wrapper?

### Options

| Option | What it means |
|---|---|
| A. Keep Selenium only | TestBench stays the only official browser tool; Dramafinder remains a community add-on. |
| B. Official Playwright wrapper beside TestBench | Vaadin adopts or rewrites Dramafinder as "TestBench for Playwright": a second element library with its own API. |
| C. Playwright backend under a common API | One Vaadin test API (TestBench's element API, or a Unified-API-like layer) with Selenium and Playwright backends, chosen per run. |
| B→C. Adopt now, converge later | Bring Dramafinder under Vaadin support now, and evolve it and TestBench towards one API with two backends. |

### Benefits of official Playwright support

- It meets users where the field is going. Playwright is the first-listed or default E2E tool in Angular, Vue, Svelte, Next.js, Blazor and Laravel (landscape item 1), and Playwright has the most AI and agent tooling (landscape item 3).
- Developers get the trace viewer, codegen and network mocking (P3–P5) on Vaadin apps, with Vaadin-aware waiting.
- It turns a single-maintainer add-on (D1) into something companies can depend on.
- Vaadin's own repo already uses Playwright (T8), and its docs already mention Playwright as an option (V1), so some of the knowledge is in-house.

### Costs and risks

- **Two element libraries to keep in step** with every component change. Dramafinder alone has 64 element classes in about 24,000 lines (D4). Option B doubles this surface for good. **[opinion]**
- **Licensing and revenue.** TestBench's browser part is a Pro feature (V2). An official Playwright library has to be either commercial, which competes with the free Dramafinder and with plain Playwright, or free, which removes one reason to buy Pro. This is a business decision this note cannot settle.
- **Dependency on Microsoft's release train:** a new browser build with each release (P9), and a Java binding that is partly experimental (P7, P8, P11).
- **Gaps against current TestBench users:** grid and cloud runs, real Safari, mobile (P9, P11), and screenshot comparison (T4) would need a Playwright equivalent or stay Selenium-only.
- **Migration cost for existing TestBench suites** if Selenium is ever de-emphasised. This repo's TestBench Migration Recipes (ADR 0004) show it can be automated, but not fully: they leave Migration Markers.

### How this repo fits

This repo is a working prototype of option C. A Unified UI Test is written once and run on Browserless, Playwright through Dramafinder 2.2.1, or Selenium through TestBench 25.3, chosen per run with `vaadin.testing.tool` (`README.md`, `unified-test-bom/pom.xml` L34). Each adapter is about 2,200–2,500 lines (`unified-test-playwright/src/main/java`, `unified-test-selenium/src/main/java`), because it sits on top of the tool's own element library. Auto-Wait is a guarantee of the API, not of the tool. The Selenium adapter calls TestBench's `waitForVaadin` (`SeleniumSession.java` L158–160), and the Playwright adapter calls Dramafinder's `AutoWait` (`PlaywrightSession.java` L360–370, `PlaywrightElement.java` L243). What only one tool has goes through an Escape Hatch, and a test that needs it declares a Capability. Network interception and tracing are Escape Hatch only and Playwright-only (`docs/spec/09-capabilities.md`, "Limitations"). The adapter SPI does not depend on any Test Runner (ADR 0008).

It also shows option C's cost. The common API covers what all tools share, so the features that make Playwright attractive (trace, routing, contexts) stay outside it. The work of option C is deciding which of them to lift into the API, for example a trace grouped by User Step, as `docs/spec/09-capabilities.md` already does for failure evidence (roadmap H8). **[opinion]**

### Recommendation

**[opinion]** Take **B→C**.

1. **Now:** keep TestBench Selenium as it is, with full support for existing users, grids, Safari and mobile. Bring Dramafinder under Vaadin's care: co-maintain it, test it in Vaadin's component CI, and promise it works with each Vaadin release. It is already Apache-2.0 and builds on Playwright's standard APIs (D1, D3), which makes adopting it cheaper than writing a new library.
2. **Next:** do not grow a second, permanently separate element API. Converge on one Vaadin test API with Selenium and Playwright backends, plus Browserless as a third. The Unified API in this repo is evidence that this works, and its adapters show the integration points (waiting, locating, Escape Hatch, Capabilities). Whether the shared API is TestBench's `$(..)` style or the Unified API's `find(..)` style is a design question for Vaadin.
3. **Decide licensing explicitly** before announcing anything. Settle what stays in Pro (screenshot comparison, grid and cloud integration, load testing, migration tooling) and what is free.
4. **Do not drop Selenium.** The standard track (WebDriver, BiDi), real branded browsers and the existing customer suites are reasons to keep it as a supported backend for the foreseeable future.

Option A is the cheapest, but it leaves Vaadin's official tool out of step with where most web frameworks now point their users. Option B on its own is the most expensive in the long run.

## Open questions

- Does Playwright's Firefox move to WebDriver BiDi in a coming release? The release notes for 1.60–1.65 do not say (P10); the rest of the page was not read.
- How large is the speed gap in a controlled run: same runner, same browser build, launch included? See [#497](https://github.com/parttio/unified-testing-tool/issues/497). How do the tools compare in flakiness? Nothing in this repo measures that yet.
- Would Dramafinder's maintainer and Vaadin agree on governance, a namespace (`org.vaadin.addons` vs `com.vaadin`) and a release cadence tied to Vaadin versions?

## Not checked

- Cloud vendors' Playwright offerings, and Playwright's own service offerings, were not read.
- Vaadin's commercial licence terms beyond the pricing page and the docs pages listed were not read.
- No external benchmark or blog comparison was used. Speed figures come only from this repo's own benchmark runs (B1–B3).
- The Selenium finders page and the WebDriver specs were read only up to the first 100,000 characters.

## Sources

- Playwright: https://playwright.dev/java/docs/actionability, https://playwright.dev/java/docs/locators, https://playwright.dev/java/docs/trace-viewer-intro, https://playwright.dev/java/docs/codegen, https://playwright.dev/java/docs/network, https://playwright.dev/java/docs/browser-contexts, https://playwright.dev/java/docs/multithreading, https://playwright.dev/java/docs/junit, https://playwright.dev/java/docs/test-runners, https://playwright.dev/java/docs/browsers, https://playwright.dev/java/docs/selenium-grid, https://playwright.dev/java/docs/release-notes, https://playwright.dev/java/docs/intro, https://github.com/microsoft/playwright-java, https://github.com/microsoft/playwright/blob/main/LICENSE
- Selenium and W3C: https://www.selenium.dev/documentation/webdriver/, https://www.selenium.dev/documentation/webdriver/bidi/, https://www.selenium.dev/documentation/webdriver/waits/, https://www.selenium.dev/documentation/webdriver/elements/finders/, https://www.selenium.dev/documentation/grid/, https://www.selenium.dev/documentation/selenium_manager/, https://github.com/SeleniumHQ/selenium/blob/trunk/LICENSE, https://www.w3.org/TR/webdriver1/, https://www.w3.org/TR/webdriver2/, https://www.w3.org/TR/webdriver-bidi/, https://appium.io/docs/en/latest/intro/
- Vaadin: https://vaadin.com/docs/latest/flow/testing, https://vaadin.com/docs/latest/flow/testing/end-to-end, https://vaadin.com/pricing
- Benchmark runs: https://github.com/parttio/unified-testing-tool/actions/runs/44585608178, https://github.com/parttio/unified-testing-tool/actions/runs/44698570653, https://github.com/parttio/unified-testing-tool/actions/runs/44702862629 (artifacts read 2026-11-22; they expire in February 2027).
- Local source: `vaadin/testbench@11908ed`, `parttio/dramafinder@bce0654`, `parttio/unified-testing-tool@7916497` (files cited inline); GitHub API for `parttio/dramafinder` and `vaadin/testbench` metadata, read 2026-11-22.
- Related notes: [web-framework-testing-landscape.md](web-framework-testing-landscape.md), [agentic-testing-environment-roadmap.md](agentic-testing-environment-roadmap.md).
