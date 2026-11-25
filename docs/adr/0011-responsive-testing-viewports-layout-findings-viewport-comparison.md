---
status: accepted
---

# Responsive testing: exact Viewports, opt-in Layout Findings, and a Viewport Comparison of separate runs

A Unified UI Test had no way to run at a chosen size or to check that the application is responsive. We decide on three pieces:
- a test runs at an **exact Viewport** on every browser Testing Tool, or is skipped, and is never approximately sized;
- **Layout Findings** are off by default, so normal runs stay as fast as today;
- responsiveness is a **Viewport Comparison**: an occasional run of the same tests once per Viewport, compared afterwards by `CompareRuns`, like the comparison between Testing Tools.

Charted on the map [Responsive: run Unified UI Tests at chosen viewport sizes and check the app is responsive](https://github.com/parttio/unified-testing-tool/issues/399). Each decision below links the ticket that holds its detail.

## Decisions

- **Setting the size** ([How does a Unified UI Test set the viewport?](https://github.com/parttio/unified-testing-tool/issues/477)):
  - `@Viewport(width, height, name)` on a class or method gives one size, set before the page opens. It is not repeatable, and it implies the browser Capability.
  - `page.setViewport(w, h)` resizes one page mid-test. It is a User Step, and it Auto-Waits Vaadin's 300 ms resize debounce, then server idle. `page.viewport()` reads the size.
  - Sizes are values of the public record `ViewportSize(name, width, height)`, returned by `page.viewport()`. The annotation keeps the glossary word `@Viewport`, and Java can't have both in one package. _Amended 2026-11-25 in the Viewport issue (#489)._
  - The size is exactly 1280 × 720 by default on every browser Testing Tool, or the run-wide `vaadin.testing.viewport=[name:]WxH`. Precedence: method `@Viewport` > class `@Viewport` > `vaadin.testing.viewport` > 1280 × 720.
  - New windows start at the declared size, and every test starts at its own size.
  - Pixels only, with no shipped presets. The screen size is not promised.
- **Exact or skipped** ([How does the Selenium adapter set an exact viewport?](https://github.com/parttio/unified-testing-tool/issues/479)):
  - The page sees an emulated Viewport inside a larger window, as in Playwright. Playwright uses `Page.setViewportSize`. Selenium uses WebDriver BiDi `browsingContext.setViewport` per tab on Chrome and Firefox ≥ 132, with `webSocketUrl` always on; it was measured exact and one command, like CDP.
  - An adapter that can't set a size exactly says why. Then a test that needs a size is skipped with that reason; a test on the default size continues with a warning; and a mid-test `setViewport` fails its User Step.
- **Layout Findings** ([Which responsive checks does the Unified API offer, and which run automatically?](https://github.com/parttio/unified-testing-tool/issues/478)):
  - They cover a page that scrolls sideways (the page or a scrolling container such as AppLayout's content; Grid exempt), a component clipped out of reach, and interactive components overlapping.
  - They are **off by default** (`vaadin.testing.layout-checks=on`). When on, they are measured after each navigation, each `setViewport` and each `checkLayout()`.
  - They are report-only; `vaadin.testing.layout-findings=fail` makes them fail the test. Screenshots per point are optional.
  - Explicit assertions work in any test: `hasNoHorizontalScroll()`, `hasNoLayoutFindings()`, `isInViewport()`, `isNotClipped()`, AppLayout's overlay drawer, and MenuBar's overflow.
  - There are no allow-lists.
- **The Viewport Comparison** ([same ticket](https://github.com/parttio/unified-testing-tool/issues/478); [How does the report show a Viewport Comparison?](https://github.com/parttio/unified-testing-tool/issues/482)):
  - The same tests run once per Viewport on one browser Testing Tool (for example a weekly CI matrix of phone, tablet and desktop). `CompareRuns` compares the runs against the widest one, matching measurement points by their place in the User Steps.
  - It reports lost components: known disclosures such as the AppLayout overlay drawer and MenuBar overflow are explained, and the others need review. It also reports Layout Findings and pass/fail per size, and where the runs part.
  - It writes `viewport-comparison.html`: a matrix of scenarios against Viewports, a detail panel per cell, and a User Steps toggle. It also writes `viewport-comparison.json`, and prints the matrix as Markdown for the GitHub job summary. All of it is report-only.
  - A test with its own `@Viewport` is not compared.
- **Adapter SPI** ([What does the adapter SPI gain for Viewports and layout checks?](https://github.com/parttio/unified-testing-tool/issues/485)):
  - `AdapterSession` gains only `setViewport(ViewportSize)` and `String measureLayout(List<String> selectors)`. The second runs the one layout script the core ships, given the Wrapper types' selectors.
  - Both default to throwing. There is **no generic JavaScript through the SPI**: running scripts is browser-only and can never work on Browserless.
  - The core owns everything else: which size and when (right after every page opens), the debounce wait, skip/warn/fail, turning the script's facts into Findable View lines, and `page.viewport()`.
- **Reach** ([Does the Findable View show each component's reach at the current Viewport?](https://github.com/parttio/unified-testing-tool/issues/487)):
  - In a browser, the Findable View is headed by its Viewport, and its lines gain `(clipped)` and `(behind …)` (amending [ADR 0009](0009-findable-view-in-lookups-not-aria-yaml.md)).
  - A failure on a covered or clipped Step Target says so in the same words on every browser.
- **Migration** ([How do Migration Recipes convert existing viewport resize calls?](https://github.com/parttio/unified-testing-tool/issues/481)):
  - Resize calls are always converted. Behind the Escape Hatch, the emulated Viewport would silently ignore them.
  - Calls that mean "viewport" become `@Viewport` before the first navigation, else `page.setViewport`.
  - Window sizes keep their numbers, with a Migration Marker. `maximize()` / `fullscreen()` are removed, with a Migration Marker.

## Considered Options

- **Several sizes inside one run**: a repeatable `@Viewport` as a JUnit template, or a scoped "at this size" block. Rejected: it adds work and machinery to every run, and a resize inside one test confuses state. Separate runs reuse the Testing Tool comparison's model and keep normal runs untouched.
- **Automatic layout checks in every run.** Rejected: they cost a round trip per navigation in tests that don't care.
- **Window sizing with measure-and-correct on Selenium** (TestBench's way). Rejected: it is ≥ 3 commands, resizes every tab of the window, clamps at 500 px (Chrome) or ≥ 450 px (Firefox), and leaks into the reused browser. **CDP on Chrome only**: as exact as BiDi, but Firefox removed CDP in 141.
- **A generic `evaluate`/`executeJs` in the SPI.** Rejected for now: it invites browser-only logic that Browserless can never share.
- **Allow-lists, visual regression (pixel diffs), device emulation beyond size, Galen-style relative layout specs, target-size checks**: out of scope for this effort.

## Consequences

- Normal runs do no extra work: the default size is one command per page opened, with no layout checks.
- The Selenium adapter always enables BiDi: about 60–90 ms per browser start, and ChromeDriver's Mapper INFO line is filtered from the browser log.
- An Escape Hatch call that sizes the window no longer changes the page, and on Selenium it makes screenshots take the window's size. The docs say to use `setViewport`.
- `CompareRuns` now writes an HTML page for one kind of comparison. The Testing Tool comparison stays Markdown plus the in-run "Compared with" section.
