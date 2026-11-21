# How can a test, and an agent, see and evaluate responsive design?

Research for [#475](https://github.com/parttio/unified-testing-tool/issues/475), on the map [#399](https://github.com/parttio/unified-testing-tool/issues/399) "Responsive: run Unified UI Tests at chosen viewport sizes and check the app is responsive". Scope follows the map: Browserless is skipped (it has no layout), the core is width × height, and visual regression and device emulation beyond size are out of scope.

Researched 2026-11-21 against `origin/main` at `111cbb0` (Vaadin `25.3.5`, `pom.xml` L55). Vaadin component facts are from `vaadin/web-components` tag `v25.0.0` and `main` at `1783558` (`25.4.0-beta1`); where they differ, the difference is noted. Flow facts are from `vaadin/flow` `main`. axe-core facts are from `dequelabs/axe-core` `develop` (`4.14.1`). Playwright facts are from `microsoft/playwright` `main` docs sources. `api/…` stands for `unified-test-api/src/main/java/com/vaadin/testing/unified/…`.

## Answer in short

- **One measuring script per size does almost all of it.** Page overflow, scroll containers, clipped or off-screen components, overlaps, truncated text and target sizes can all be read in **one `evaluate`/`executeScript` round trip** per size on Playwright and Selenium (V1, V2). A screenshot is one more round trip per size. Setting the size is one round trip on Playwright and on Selenium with BiDi. Plain WebDriver needs about three: it sets the *outer* window, so the adapter must read `innerWidth` and correct (T3, T4).
- **Run four checks automatically at every size, and report them as findings rather than failures by default:**
  1. **No horizontal scrolling,** of the page *and* of AppLayout's content part. In a Vaadin app the document often doesn't scroll at all, so `documentElement.scrollWidth > clientWidth` alone misses the usual case (C1, A3).
  2. **No component lost:** a component the Findable View finds at the reference width must still be findable, or reachable through a known disclosure, at this width. This is exactly WCAG's own Reflow failure test, F102 (W2).
  3. **No component clipped beyond reach,** that is, cut off by an `overflow: hidden/clip` ancestor or placed at negative coordinates.
  4. **No two interactive components overlapping,** confirmed by hit-testing.

  Report **truncated text** and **target size** (WCAG 2.5.8, axe `target-size`) as facts with a warning, not as failures. Truncation is often deliberate (Grid cells), and axe ships `target-size` disabled by default (W3, X1).
- **"Hidden on purpose" can mostly be read from Vaadin's own attributes, not guessed:**
  - AppLayout reflects `overlay` and `drawer-opened`, and marks the drawer `role="dialog"` in overlay mode (A1, A2).
  - MenuBar hides collapsed buttons with `visibility: hidden` and shows its overflow button (`has-overflow` on `main` only) (M1, M2).
  - Grid sets `overflow="start end"` when its own content scrolls sideways (G1). That is legitimate, and WCAG exempts data tables (W1).
  - FormLayout re-flows on its **container's** width, not the viewport's (F1).
  - Grid has no built-in column hiding: any responsive Grid behaviour is application code, and so only the test author can state it (G2).
- **Only the test author can state the intended design at a size:** "the drawer is an overlay at 400 px", "the form has 2 columns at 1024 px", "the Email column is hidden on phones", "these components may disappear", "this container may scroll sideways". Galen's layout specs (`near`, `inside`, `above`, `@on mobile`) are the prior art for relative-position assertions. They are heavyweight and Galen is unmaintained since 2018 (P1). Recommend a small set of state assertions on Wrappers plus one generic "is fully in viewport" check (Playwright's `toBeInViewport` is the model, P3). Relative geometry assertions can wait.
- **The evidence an agent needs is a geometry-annotated Findable View per size, with a screenshot per size as an option.** A text view that says, per component, *on screen / reachable by scrolling / clipped / off-screen / hidden by design*, with its box, works on both browser tools. It is cheap, can be diffed between sizes, and needs no vision model. Playwright MCP chose exactly this, an accessibility snapshot with optional `[box=x,y,w,h]` (P4). Screenshots add what geometry can't: crowding, alignment, whitespace, "looks broken". Agents should get them per size **in addition to** the text view, never instead of it. Without screenshots an agent can still judge reflow, loss, clipping and overlap. It cannot judge visual quality.
- **Resizing is asynchronous on both sides.** FormLayout re-lays out in a `ResizeObserver` callback deferred with `setTimeout` (F2). Flow's server-side `window-resize` event, which feeds `addBrowserWindowResizeListener` and `windowSizeSignal`, is **debounced 300 ms** and then needs a server round trip (L1). Measuring right after a resize therefore reads a half-updated page. The size-setting call must settle the same way an action does (`AdapterSession.settle()`), plus that debounce.

---

## 1. Verified facts

### Standards and checkers

| # | Fact | Source |
|---|---|---|
| W1 | SC 1.4.10 Reflow (AA): content is presented "without loss of information or functionality" and without two-dimensional scrolling at "a width equivalent to 320 CSS pixels" (vertical content) or a height of 256 CSS px (horizontal content), "except for parts of the content which require two-dimensional layout". Data tables are such a part, but their cells must still reflow. "320 CSS pixels is equivalent to a starting viewport width of 1280 CSS pixels wide at 400% zoom." | [Understanding 1.4.10](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html) |
| W2 | The only listed failure, F102 ("content disappearing … when content has reflowed"), is tested by: check content at about 1280 px, set the viewport to 320 px, then "for each content element that is not provided at the viewport width of 320px, check that there is a way to reach the same or equivalent content via disclosure widgets, pop-ups, or links to other views." | [F102](https://www.w3.org/WAI/WCAG22/Techniques/failures/F102) |
| W3 | SC 2.5.8 Target Size (Minimum): targets are at least 24×24 CSS px, unless a 24 px circle centred on each undersized target doesn't intersect another target or circle (spacing). Further exceptions are Equivalent, Inline, User agent control and Essential. | [Understanding 2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) |
| X1 | axe-core has **no rule for 1.4.10 Reflow**: no rule is tagged `wcag1410`. `target-size` (wcag22aa, wcag258) is `"enabled": false` by default. It applies to focusable widget-role elements outside text blocks (`widget-not-inline-matches`), passes on `target-size` (minSize 24) *or* `target-offset`, and returns "incomplete" for obscured or overflowing cases. `css-orientation-lock` is experimental. `meta-viewport` (1.4.4) checks only that zoom isn't disabled. | [rule-descriptions.md](https://github.com/dequelabs/axe-core/blob/develop/doc/rule-descriptions.md); `lib/rules/target-size.json`; `lib/checks/mobile/target-size.json`; `lib/rules/widget-not-inline-matches.js` |
| X2 | Lighthouse's `content-width` audit fails when `window.innerWidth !== window.outerWidth`. That is a proxy for mobile layout, not an overflow check, and it sits in the deprecated PWA category. | [content-width](https://developer.chrome.com/docs/lighthouse/pwa/content-width) |

### Browser measuring primitives (CSSOM View)

| # | Fact | Source |
|---|---|---|
| C1 | For the root element (no quirks mode), `scrollWidth` returns `max(viewport scrolling area width, viewport width)`, and `clientWidth` returns the viewport width without the scroll bar. For other elements, `clientWidth` is the padding box without the scroll bar. So `scrollWidth > clientWidth` is the page-overflow test, and the same test works per element. | [CSSOM View](https://drafts.csswg.org/cssom-view/) §scrollWidth, §clientWidth |
| C2 | `checkVisibility(options)` returns false without a box, under `content-visibility: hidden`, and optionally for `opacity: 0` or `visibility: hidden`. | CSSOM View §checkVisibility |
| C3 | `elementsFromPoint(x, y)` returns the boxes that hit-test at a point, topmost first, and nothing for a point outside the viewport. This is how to tell an overlap that covers a component from a box that merely intersects. | CSSOM View §elementsFromPoint |

### Testing Tools

| # | Fact | Source |
|---|---|---|
| T1 | `Page.setViewportSize(w, h)` resizes the page. Playwright advises setting it before navigating ("a lot of websites don't expect phones to change size"), and it also resets `screen` size. `Browser.newContext` sets it for all pages in a context. | `docs/src/api/class-page.md` L4377–4399 |
| T2 | Playwright's "visible" means a non-empty bounding box and not `visibility: hidden`. So an element clipped by an ancestor or scrolled off-screen is still "visible". `toBeInViewport` (`isInViewport` in Java, optional `ratio`) uses IntersectionObserver. `Locator.boundingBox()` returns null when not visible. | `docs/src/actionability.md` L82–88; `class-locatorassertions.md` L904–920; `class-locator.md` L311–321 |
| T3 | WebDriver Set Window Rect sets the window's size "including any browser chrome and externally drawn window decorations" (`outerWidth/outerHeight`). It is not guaranteed exact and may be clamped. Take Screenshot captures "the top-level browsing context's visual viewport". | [WebDriver](https://w3c.github.io/webdriver/) §Set Window Rect, §Screen capture |
| T4 | WebDriver BiDi `browsingContext.setViewport` sets viewport width and height (and optionally `devicePixelRatio`) directly. Selenium Java exposes it as `BrowsingContext.setViewport(int, int)`. CDP's `Emulation.setDeviceMetricsOverride` overrides `innerWidth/innerHeight` and media queries, Chromium only. | [WebDriver BiDi](https://w3c.github.io/webdriver-bidi/); Selenium `java/src/org/openqa/selenium/bidi/browsingcontext/BrowsingContext.java` L294–311; CDP `browser_protocol.json` |
| T5 | TestBench's `resizeViewPortTo(w, h)` "tries to resize the browsers window so that the space available for actual web content (aka viewport) is of given size", and may throw `UnsupportedOperationException`. | [TestBenchCommands javadoc](https://javadoc.io/static/com.vaadin/vaadin-platform-javadoc/24.3.10/com/vaadin/testbench/commands/TestBenchCommands.html) |
| T6 | Cypress: `cy.viewport` defaults to 1000×660 and resets between tests. It has named presets (`iphone-6` = 375×667), and its docs loop a test over `['iphone-6', 'ipad-2', [1024, 768]]`. | [cy.viewport](https://docs.cypress.io/api/commands/viewport) |

### Vaadin components and Flow

| # | Fact | Source (`packages/…` in `vaadin/web-components`) |
|---|---|---|
| A1 | AppLayout becomes an overlay when CSS sets `--vaadin-app-layout-drawer-overlay: true`. Base styles and Lumo do so under `@media (max-width: 800px), (max-height: 600px)`. Aura aligns its inset with the same breakpoint. The read-only `overlay` property is reflected to an attribute, and so is `drawerOpened`. | `app-layout/src/styles/vaadin-app-layout-base-styles.js` (L204 at v25.0.0, L187 on main); `vaadin-lumo-styles/src/components/app-layout.css` L169; `aura/src/components/app-layout.css` L11; `app-layout/src/vaadin-app-layout-mixin.js` L47–68 |
| A2 | In overlay mode the drawer gets `role="dialog"`, `aria-modal="true"` and an `aria-label`. Escape closes it. The touch-optimized bottom navbar needs `(pointer: coarse) and (max-width: 800px) and (min-height: 500px)`. | `vaadin-app-layout-mixin.js` L375–392, L451–457; base styles L62–64 |
| A3 | AppLayout's `[part~='content']` has `height: 100%` and `overflow: auto` (unless `no-scroll`). The drawer is `position: fixed; overflow: auto`. | base styles L44–50, L110–115 |
| F1 | FormLayout has two modes. In `responsiveSteps` mode (default `[{minWidth:0, columns:1, labelsPosition:'top'}, {minWidth:'20em', columns:1}, {minWidth:'40em', columns:2}]`), `colspan` never exceeds the column count. In `autoResponsive` mode (`columnWidth`, `maxColumns`, `minColumns`, `labelsAside`, `expandColumns`), `responsiveSteps` is ignored. | `form-layout/src/vaadin-form-layout-mixin.js` L70–125; `layouts/responsive-steps-layout.js` L146–151 |
| F2 | Both FormLayout modes measure the layout's own size with `ResizeObserver(… => setTimeout(() => this._onResize(…)))`: they follow the container, not the viewport, and update one task later. The rendered column count is only in private styles (`--_grid-rendered-column-count`), and in responsive-steps mode as `label-position="top"` on items. | `layouts/abstract-layout.js` L24; `layouts/auto-responsive-layout.js` L152; `responsive-steps-layout.js` L191 |
| M1 | MenuBar collapses the buttons that don't fit into an overflow button. Collapsed buttons get `style.visibility = 'hidden'`, and the overflow button's `hidden` attribute is toggled. `reverseCollapse` collapses from the start. | `menu-bar/src/vaadin-menu-bar-mixin.js` (v25.0.0 L161, L263–270, L497) |
| M2 | On `main` (25.4) the host also gets a `has-overflow` attribute; 25.0 doesn't have it. | `vaadin-menu-bar-mixin.js` (main L499–504) |
| G1 | Grid uses `OverflowController`, which sets `overflow="top bottom start end"` (as applicable) on the host when its table scrolls in that direction. | `grid/src/vaadin-grid-scroll-mixin.js` L133; `component-base/src/overflow-controller.js` L9–11, L112–135 |
| G2 | Grid's sources contain no width media queries or `matchMedia`: hiding columns at narrow widths is application code (for example, a resize listener calling `setVisible(false)`). | `grep '@media\|matchMedia' packages/grid/src` |
| U1 | Lumo utility classes have breakpoints at 640 (`sm:`), 768 (`md:`), 1024 (`lg:`), 1280 (`xl:`) and 1536 px. Overlays (date picker, menus) go full-screen under `(max-width: 450px), (max-height: 450px)`. | `vaadin-lumo-styles/src/utilities/layout.css` L355–520; `src/components/date-picker.css` L28; `src/mixins/menu-overlay-ext.css` |
| L1 | Flow's `Page.addBrowserWindowResizeListener` and `windowSizeSignal()` (since 25.1) listen to a `window-resize` DOM event with `.debounce(300)`. | `flow-server/…/component/page/Page.java` L650–736 |

### This repository

| # | Fact | Source |
|---|---|---|
| R1 | Nothing sets a viewport. Selenium starts Chrome with `--window-size=1280,1024` (an *outer* size), and Playwright uses its default. | `unified-test-selenium/…/SeleniumAdapter.java` L87; `grep viewport` |
| R2 | `AdapterSession` has `screenshot(Path)`, `writeStepSnapshot(dir, name, target)` and `writeFailureEvidence(…)`. The last two return `Evidence` with a `Highlight` (a box in fractions of the image, or `gone`/`outside`). | `api/spi/AdapterSession.java` L62–86; `api/spi/Highlight.java` |
| R3 | Both browser adapters already measure the Step Target box in one script (`TARGET_BOX`): Playwright with `locator.evaluateAll`, Selenium with `executeScript`. Step snapshots are viewport shots on both. Playwright's failure shot is full-page (`setFullPage(true)`). Selenium's is the window's viewport. | `PlaywrightSession.java` L513–575; `SeleniumSession.java` L391–455 |
| R4 | The Findable View is built in the core from `find` plus `facts`, about 20 queries per run, and only on failure or on `describe()` (ADR 0009). | `docs/adr/0009-…md` Consequences |

### Prior art

| # | Fact | Source |
|---|---|---|
| P1 | Galen's spec language checks `near`, `inside` (and `inside partly`), `width/height` (px or % of another object, screen or viewport), `above/below/left-of/right-of`, `aligned`, `absent/visible` and `count`, grouped by `@on mobile, desktop` tags. Its last release news is 2.4 (2018-11-03). | [Galen spec guide](https://galenframework.com/docs/reference-galen-spec-language-guide/) |
| P3 | Playwright's `toBeInViewport({ratio})` is an "intersects the viewport" assertion (T2). | as T2 |
| P4 | Playwright MCP gives LLMs "structured accessibility snapshots, bypassing the need for screenshots or visually-tuned models". `--snapshot-boxes` adds `[box=x,y,width,height]` per element (viewport-relative CSS px, `getBoundingClientRect`), `--viewport-size` and `browser_resize` set the size, and vision is an opt-in capability. | [playwright-mcp README](https://github.com/microsoft/playwright-mcp) L3, L15–17, L415, L455, L464, L1124–1168 |

---

## 2. Candidate checks

Cost is per size, in WebDriver/Playwright round trips, assuming the core already knows the components to look at (the Findable View's matches, R4). "1 script" means it shares the single measuring `evaluate`/`executeScript` with the other rows.

| Check | Catches | How detected | False positives | Cost (PW / Se) | Runs |
|---|---|---|---|---|---|
| **Page horizontal scroll** | Reflow failure (W1): a fixed-width form or a wide table pushing the page sideways | `scrollingElement.scrollWidth > clientWidth` (C1), **plus** the same on AppLayout's `[part~=content]` and every element with computed `overflow-x: auto/scroll` that is wider than the viewport (A3) | Grid and other data tables scrolling inside themselves (W1 exemption; G1's `overflow` attribute names them); deliberate carousels | 1 script | **Auto** |
| **Component lost at this size** (F102) | A component reachable on desktop and gone on phone, with no way to it | Diff the findable components at the reference width (1280, W1–W2) and at this width. For each missing one, look for a known disclosure: the AppLayout drawer when `overlay` (A1), a MenuBar overflow (M1), a closed Details or Tab | Content moved behind an app-specific disclosure the core doesn't know; app-level column hiding (G2) | Findable View twice (~20 queries each, R4), or one script that runs the same queries | **Auto**, as "needs review" when unexplained |
| **Clipped beyond reach** | A button cut off by `overflow: hidden`; content at negative x; a field under a fixed navbar | Per component: box vs viewport, and vs each ancestor with `overflow` ≠ `visible` (`scroll`/`auto` = reachable, `hidden`/`clip` = clipped). Negative left/top = unreachable | MenuBar's collapsed buttons (`visibility: hidden`, M1), the closed overlay drawer (A1), Grid's virtualised rows and columns outside its scroll area | 1 script | **Auto** |
| **Overlap of interactive components** | Two buttons stacked after a flex wrap failure; a field under a sticky footer | Box intersection between the findable interactive components, confirmed by `elementsFromPoint` at the covered one's centre (C3) | Open overlays (dialogs, menus, drawer `role=dialog`, A2), notifications, floating labels, badges on purpose; ignore components inside an open overlay vs. outside it | 1 script | **Auto** |
| **Truncated or wrapped text** | Label "Save chan…", wrapped button text | `scrollWidth > clientWidth` on a box with `text-overflow: ellipsis` or `overflow: hidden`; wrapped = line count > 1 from `getClientRects()` on the text | Grid cells and Tabs truncate by design; wrapping is often fine | 1 script | **Auto, report only** |
| **Target size and spacing** (2.5.8) | Tiny icon buttons crowding at phone widths | 24 px box or 24 px-circle spacing (W3); or inject axe-core and run only `target-size` (X1) | Inline links, user-agent controls; axe itself says "incomplete" for obscured ones | 1 script own; axe adds an injection (~1) + run (1) | **Auto, report only** |
| **Meta viewport allows zoom** | `user-scalable=no` | axe `meta-viewport`, or read the tag | none to speak of | 1 script, once per page, not per size | **Auto**, once |
| **Vaadin state at a size** | "drawer is an overlay at 600 px", "menu bar shows the overflow button", "form has 2 columns" | Read `overlay`/`drawer-opened` (A1), the MenuBar overflow button's `hidden` (M1), FormLayout columns (private, F2: compute from item boxes instead) | none: the author states it | 1 read each (Wrapper getter) | **Author** |
| **Component visible / hidden / in viewport at a size** | "Email column hidden on phones", "Save button above the fold" | Existing visibility + an in-viewport ratio (T2, P3) | none: the author states it | 1 read | **Author** |
| **Relative layout** (Galen-like: `below`, `left-of`, `inside`, `aligned`) | "labels beside fields on desktop, above on phone" | Boxes of two components | Off-by-pixel brittleness (Galen adds tolerances) | 1 read | **Author**, defer |
| Lighthouse `content-width` (X2) | — | `innerWidth !== outerWidth` | — | — | **Reject**: it is a mobile-emulation proxy, subsumed by page horizontal scroll |
| LLM reading a screenshot | Crowding, misalignment, ugly whitespace, overlapping text that boxes don't show | Vision model on the per-size screenshot | Hallucinated defects, missed small ones; not repeatable | 1 screenshot per size; model cost outside the run | **Agent**, evidence only, never a pass/fail of the test |

### Which run automatically, which the author must state

- **Automatic at every size, no author input:** page and container horizontal scroll, component lost (F102 diff), clipped beyond reach, interactive overlap; truncation, target size and meta viewport as report-only warnings. All of them need an **allow-list the author can extend**: "this container may scroll sideways", "these components may disappear at this size".
- **Author-stated:** which sizes to run (with defaults that hit Vaadin's own breakpoints: 320 for W1, a width ≤ 800 for the AppLayout overlay A1, 1280 as the F102 reference), the intended Vaadin state at a size, components expected hidden or in view, and relative layout if it is ever added.
- **Recommendation for failure vs report:** automatic checks write **findings** into `user-steps.json` and the report per size. A test opts into failing on them (or the run does, by configuration). F102's "there is a way to reach it" is a judgement the core can only partly automate (W2), and false positives in the first release would teach users to switch the whole thing off.

---

## 3. Evidence an agent needs

| Evidence | What an agent can judge from it | Cost | Tools |
|---|---|---|---|
| **Geometry-annotated Findable View per size**: the ADR 0009 lines plus `[box=x,y,w,h]` and a status (`on screen`, `below the fold`, `scroll to reach in <container>`, `clipped by <container>`, `off-screen`, `hidden: drawer overlay closed`, `hidden: in menu bar overflow`), plus the page's overflow line | Reflow, lost content (diff two sizes), clipping, overlap, what to look up next; works with any model, diffs cleanly between sizes and runs | Findable View queries (~20) + 1 script | Playwright, Selenium |
| **Automatic findings per size** (§2) | The checks' verdicts with the component's lookup, so an agent can explain or fix them | in the same script | both |
| **Viewport screenshot per size**, with the Step Target highlight (ADR 0010) | Visual quality: spacing, alignment, crowding, text over images, colour; what a user sees above the fold | 1 round trip + a PNG per size and step | both |
| Full-page screenshot | Usually *misleading* in a Vaadin app: with AppLayout the document doesn't scroll, the content part does (A3), so the "full page" is the viewport (inference, not measured) | 1 | Playwright; Selenium only by stitching |

- **Without screenshots** an agent can still tell: does the page reflow, what disappeared and whether a known disclosure explains it, what is clipped, covered or truncated, and whether the stated Vaadin states hold. This is the Playwright MCP position (P4): structured snapshot first, boxes when layout matters.
- **With screenshots** it can also judge appearance. It shouldn't be the only evidence: a model reading a phone-width PNG can't tell whether a button is reachable by scrolling, or which lookup names it.
- So the per-size evidence is **the text view always, screenshots per size optionally** (the same switch as step snapshots today, R2). The report shows them per size.

---

## 4. Cost per Testing Tool, per size

| Step | Playwright | Selenium (WebDriver classic) | Selenium + BiDi |
|---|---|---|---|
| Set size | 1 (`setViewportSize`, T1) | ~3: `setSize`, read `innerWidth/innerHeight`, correct by the chrome's difference (T3; TestBench does the same, T5); may be clamped by the screen when headed | 1 (`browsingContext.setViewport`, T4); needs the session's `webSocketUrl` capability, not enabled today (R1) |
| Settle after resize | `settle()` + ≥ 300 ms when the app listens to window resize (L1); FormLayout one task later (F2) | same | same |
| Measure (all automatic checks) | 1 `evaluate` | 1 `executeScript` | 1 |
| Findable View with boxes | ~20 queries + 1 (R4) | ~20 + 1 | same |
| Screenshot | 1 | 1 | 1 |

---

## 5. Inferences and open edges

- **The page-level overflow check alone gives false negatives in AppLayout apps.** Because `[part~=content]` is `height: 100%; overflow: auto` (A3), sideways overflow scrolls the content part, not the document. This is inferred from the styles and should be confirmed on the demo app at 320 px.
- **`overflow-x: hidden` on `html`/`body` hides the problem from users, not from `scrollWidth`.** Per C1, the root's `scrollWidth` reports the scrolling area, so the check still fires. Whether that is a true positive (the content is lost) or noise depends on what was cut off, which is the "clipped beyond reach" row.
- **Touch-optimized navigation can't be reached by size alone.** AppLayout's bottom navbar needs `pointer: coarse` (A2), so it stays out of scope with device emulation. It is worth stating in the map so nobody expects it at 375×667.
- **Setting the size before navigating** (T1) suggests the API should allow a size per test (or per scenario), applied before `navigate`, besides resizing mid-test.

## Sources

- W3C: [Understanding 1.4.10](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html), [F102](https://www.w3.org/WAI/WCAG22/Techniques/failures/F102), [Understanding 2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html), [CSSOM View](https://drafts.csswg.org/cssom-view/), [WebDriver](https://w3c.github.io/webdriver/), [WebDriver BiDi](https://w3c.github.io/webdriver-bidi/)
- [axe-core rule descriptions](https://github.com/dequelabs/axe-core/blob/develop/doc/rule-descriptions.md) and `lib/rules/target-size.json`, `lib/checks/mobile/target-size.json`, `lib/rules/widget-not-inline-matches.js`
- `vaadin/web-components` `v25.0.0` and `main`: `app-layout`, `form-layout`, `menu-bar`, `grid`, `component-base`, `vaadin-lumo-styles`, `aura`
- `vaadin/flow` `main`: `flow-server/src/main/java/com/vaadin/flow/component/page/Page.java`
- `microsoft/playwright` `docs/src/api/class-page.md`, `class-locator.md`, `class-locatorassertions.md`, `docs/src/actionability.md`; [playwright-mcp README](https://github.com/microsoft/playwright-mcp)
- Selenium `java/src/org/openqa/selenium/bidi/browsingcontext/BrowsingContext.java`; Chrome DevTools Protocol `Emulation.setDeviceMetricsOverride`
- [TestBenchCommands javadoc](https://javadoc.io/static/com.vaadin/vaadin-platform-javadoc/24.3.10/com/vaadin/testbench/commands/TestBenchCommands.html), [Cypress `cy.viewport`](https://docs.cypress.io/api/commands/viewport), [Galen spec language](https://galenframework.com/docs/reference-galen-spec-language-guide/), [Lighthouse content-width](https://developer.chrome.com/docs/lighthouse/pwa/content-width)
