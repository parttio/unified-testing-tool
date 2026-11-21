# How does each browser Testing Tool control the viewport size?

Research for [#476](https://github.com/parttio/unified-testing-tool/issues/476), on the map [#399](https://github.com/parttio/unified-testing-tool/issues/399) "Responsive: run Unified UI Tests at chosen viewport sizes and check the app is responsive". Browserless is skipped (no layout). Device emulation beyond width × height is out of scope.

Researched 2026-11-21 against `origin/main` at `111cbb0`: Vaadin **25.3.5** (`pom.xml` L55), Dramafinder **2.2.1** (`unified-test-bom/pom.xml` L34), Playwright **1.63.0** (Node typings, which carry the same API docs as Playwright Java), Chromium **153** (headed) / headless shell **157**, ChromeDriver **147** (run with `--disable-build-check` against Chrome 153). Firefox was not available, so no Firefox measurements were made. All paths are relative to the repo root. `pw/…` stands for `unified-test-playwright/src/main/java/com/vaadin/testing/unified/playwright/…`, and `se/…` for `unified-test-selenium/src/main/java/com/vaadin/testing/unified/selenium/…`.

Experiments: [`pw.js`](#appendix-experiments) (Playwright) and [`wd.js`, `cdp.js`](#appendix-experiments) (raw W3C WebDriver against ChromeDriver). Headless runs were plain; headed runs used `xvfb-run`, which has **no window manager**, so headed results show no OS window decorations.

## Answer in short

- **Playwright sets the viewport itself, exactly.** `Page.setViewportSize(w, h)` takes one call and works on one page. `Browser.NewContextOptions.setViewportSize(w, h)` sets the size for every page of a new context. There is **no `BrowserContext.setViewportSize`** after creation. The size is exact both headless and headed, including below Chrome's 500 px minimum window width, because it is emulated (CDP `Emulation.setDeviceMetricsOverride`). Layout, `innerWidth` and media queries are already updated when the call returns. It also sets `screen.width/height` to the viewport. Other pages and contexts keep their size. The default today is **1280 × 720** for every page the adapter opens.
- **Selenium sets the OS window (outer size), not the viewport.** W3C Set Window Rect sizes the window "including any browser chrome", and the spec "does not guarantee" the result. Measured on Chrome 153: the **width is exact** (no side chrome), but the **height is 143 px short** (tab strip and toolbar), both in `--headless=new` and in headed mode without a window manager. **Headed Chrome clamps the window to at least 500 px wide**, so 375 or 320 widths are impossible with window sizing. TestBench's `resizeViewPortTo` loop then fails after 5 tries with `UnsupportedOperationException`. With a real window manager, title bars and borders add more (not measured here).
- **Selenium on Chrome can get an exact viewport the Playwright way:** `HasCdp.executeCdpCommand("Emulation.setDeviceMetricsOverride", …)` on the wrapped `ChromeDriver`. It is exact below 500 px, headless and headed. It is **per tab**, survives navigation in that tab, takes one command, and the adapter already uses `HasCdp` for cleanup. Firefox has no such route, so it needs window sizing plus measure-and-correct.
- **Vaadin's server learns the new size only through a debounced resize event, and only if the app listens.** Flow forwards `window` `resize` as `document.documentElement.clientWidth/clientHeight`, **debounced 300 ms (trailing)**, and only when the app uses `Page.addBrowserWindowResizeListener` or `Page.windowSizeSignal()`. `ExtendedClientDetails`' body size is read once at bootstrap and is stale until `refresh(..)`. Important for Auto-Wait: during the 300 ms debounce no request is in flight, so **`flow.clients…isActive()` reports idle before the server has heard about the resize**. A resize step must wait out the debounce, or set the size before navigating so the bootstrap carries it.
- **Effect on other windows:** In Playwright, `openWindow()` pages and `openAsUser()` contexts are separate, each starting at 1280 × 720. In Selenium, `openWindow()` is a **tab of the same OS window**, so resizing the window resizes it too, while a CDP override stays on its own tab. `openAsUser()` is a separate browser.
- **A leak to fix in the Selenium adapter:** the run-scoped shared browser (`reuse-browser`, default on) keeps its OS window size between tests, and `SharedBrowser.clean()` does not restore it. CDP overrides do not leak, because `clean()` opens a fresh tab and closes the old ones.

---

## 1. Today's defaults in this repo

| # | Fact | Source |
|---|---|---|
| D1 | The Playwright adapter creates each test's context with `browser.newContext()` and no options, so it gets Playwright's default viewport. Its first page is `browserContext.newPage()`. | `pw/PlaywrightAdapter.java` L52, L60 |
| D2 | Playwright's default: "Emulates consistent viewport for each page. Defaults to an 1280x720 viewport. Use `null` to disable…" Measured: `innerWidth × innerHeight` = 1280 × 720, and `screen` = 1280 × 720 too. | `playwright-core/types/types.d.ts` L11708 (`NewContextOptions.viewport`); experiment P1 |
| D3 | `openWindow()` calls `context.newPage()`, a new page in the **same** context. `openAsUser()` calls `context.browser().newContext()`, a **new** context with defaults. Both start at 1280 × 720. | `pw/PlaywrightSession.java` L126–127, L136 |
| D4 | The Selenium adapter on Chrome passes `--headless=new` (by default) and `--window-size=1280,1024`. That is an OS-window size. Measured viewport: **1280 × 881**, both headless-new and headed under Xvfb. | `se/SeleniumAdapter.java` L84–87; experiment S1 |
| D5 | The Selenium adapter on Firefox passes only `-headless`, with no size. Firefox's headless screen defaults to 1366 × 768 (`MOZ_HEADLESS_WIDTH/HEIGHT`). The resulting window and viewport were **not measured** (Firefox not installed). | `se/SeleniumAdapter.java` L75–81; Mozilla [`HeadlessScreenHelper.cpp`](https://sources.debian.org/src/firefox/148.0-1/widget/headless/HeadlessScreenHelper.cpp) |
| D6 | `openWindow()` in Selenium runs `switchTo().newWindow(WindowType.TAB)`: a tab in the same OS window. `openAsUser()` starts a new browser from the same `browsers` supplier, so it gets the same options and window size. | `se/SeleniumSession.java` L338–352 |
| D7 | With `vaadin.testing.selenium.reuse-browser` (default `true`), one browser serves the run's tests. `SharedBrowser.clean()` opens a fresh tab, closes the old ones, and clears cookies and storage. It does **not** reset the window size. | `se/SeleniumAdapter.java` L55–60, L139–163 |
| D8 | ChromeDriver with no `--window-size` (headless-new) starts at 780 × 580 (viewport 780 × 437). Headed under Xvfb it starts at 1050 × 1004. This is the baseline the adapter's flag overrides. | experiment S3 |

## 2. Playwright (and Dramafinder)

### Verified facts

| # | Fact | Source |
|---|---|---|
| P1 | `Page.setViewportSize`: "In the case of multiple pages in a single browser, each page can have its own viewport size. However, `browser.newContext([options])` allows to set viewport size (and more) for all pages in the context at once." It "will resize the page. A lot of websites don't expect phones to change size, so you should set the viewport size before navigating to the page." It "will also reset `screen` size". | `types.d.ts` L4931–4943 ([docs](https://playwright.dev/java/docs/api/class-page#page-set-viewport-size)) |
| P2 | Size can be set at context creation (`NewContextOptions.viewport`, also accepted by `Browser.newPage`) or per page afterwards. `BrowserContext` has no method to change it after creation. | `types.d.ts` L11708–11725, L18017–18035 (`newContext`/`newPage` options); no `setViewportSize` on `BrowserContext` |
| P3 | Measured headless (shell 157): after `setViewportSize(375, 667)`, the very next `evaluate` sees `innerWidth/innerHeight` 375 × 667, `documentElement.clientWidth` 375, a 100%-wide div's box 375, `matchMedia('(max-width: 600px)')` true, `screen` 375 × 667, and the `resize` event **already fired once**. The call took 1 ms. | experiment P-headless |
| P4 | Measured headed (Chromium 153, Xvfb): the same layout values right after the call. The OS window grows to 500 × 752, but the viewport is an exact 375 × 667. The `resize` event had **not** fired when the call returned. It had fired within 300 ms. The call took 5–9 ms. | experiment P-headed |
| P5 | A second page in the same context, and a page in another context, stay at 1280 × 720 after another page was resized. | experiment P-headless/headed |
| P6 | A context created with `viewport: {200, 300}` gives exactly 200 × 300, headed too, which is below Chrome's 500 px minimum window. With `viewport: null` the size depends on the host: 800 × 600 headless and 500 × 298 headed under Xvfb. That is why `null` is documented as "non-deterministic". | experiment P-headless/headed; `types.d.ts` L11711 |
| P7 | Dramafinder 2.2.1 has no viewport API. Its elements take a `Page`/`Locator` and leave sizing to the test. Its own ITs call `page.setViewportSize(..)` directly and then rely on auto-retrying assertions (`appLayout.assertOverlayMode()`). Its agent base class `VisualVerificationTest` calls `page.setViewportSize(1920, 1080)`. | [`AppLayoutViewIT`](https://github.com/parttio/dramafinder/blob/HEAD/src/test/java/org/vaadin/addons/dramafinder/tests/it/AppLayoutViewIT.java); [`VisualVerificationTest`](https://github.com/parttio/dramafinder/blob/HEAD/src/main/java/org/vaadin/addons/dramafinder/agent/VisualVerificationTest.java) (commit `dd085f1`) |
| P8 | `AppLayoutElement.isOverlayMode()` reads the reflected `overlay` attribute, "which the component enables on small viewports". Responsive Vaadin components react on the client (media queries, attributes), with no server round trip. | [`AppLayoutElement`](https://github.com/parttio/dramafinder/blob/HEAD/src/main/java/org/vaadin/addons/dramafinder/element/AppLayoutElement.java) |

### Inferences

- **Round trips:** one Playwright call per resize (Java → driver → CDP), which returns once the browser has applied it. No measuring or correction is needed.
- **Synchronous relayout:** yes, for anything the test reads next. Layout reads force a reflow, and media queries already match. App code that runs on `resize` (Flow's listener included) may run after the call returns when headed (P4), so it is not synchronous with the call.
- **Which call to use:** `Page.setViewportSize` on the session's page fits "resize this window now". A per-session default fits "run this test at size X" and is best set as `NewContextOptions.setViewportSize` in `PlaywrightAdapter.open` and in `openAsUser`, so the very first navigation already has that size (P1's "before navigating"). `openWindow()` pages inherit the context's creation-time size, not a later `page.setViewportSize` on a sibling page.
- **Dramafinder gap:** none. The adapter owns context and page creation, and Dramafinder neither assumes nor fixes a size.

## 3. Selenium / TestBench

### Verified facts

| # | Fact | Source |
|---|---|---|
| S0 | W3C Set Window Rect sets "the width, in CSS pixels, of window, including any browser chrome and externally drawn window decorations" (likewise the height). The aim is that `outerWidth` be "as close as possible". "The specification does not guarantee that the resulting window size will exactly match that which was requested". Implementations clamp to the screen and the minimum window size. It returns the resulting window rect. | [W3C WebDriver 2, §11.8.2](https://www.w3.org/TR/webdriver2/#set-window-rect) |
| S1 | Chrome 153 `--headless=new`, window 1280 × 1024 → viewport 1280 × 881. Set Window Rect to 375×667 → 375 × 524. 768×1024 → 768 × 881. 1920×1080 → 1920 × 937. 320×480 → 320 × 337. 200×300 → 200 × 157. So **width is exact and height is always 143 px short**. One HTTP command, 15–17 ms, with `resize` already fired on return. | experiment S1 |
| S2 | Chrome 153 headed under Xvfb (no window manager): same 143 px height gap, but the **window width is clamped to ≥ 500**. Requests for 375, 320 and 200 all give viewport width 500, and those commands took ~320 ms instead of ~17 ms. | experiment S2 |
| S3 | ChromeDriver without `--window-size`: 780 × 580 window headless-new, and 1050 × 1004 headed under Xvfb (D8). | experiment S3 |
| S4 | TestBench `resizeViewPortTo(w, h)`: moves the window to (0, 0) and sets the window size to (w, h). It then repeats up to `MAX_RESIZE_ATTEMPTS = 5` times: measure `innerWidth`/`innerHeight` with two scripts, return on an **exact** match, otherwise read the window size and set it to window + (wanted − actual). After the loop it measures again and throws `UnsupportedOperationException("Viewport size couldn't be set to the desired …")`. It has no tolerance. | [`TestBenchCommandExecutor.java`](https://github.com/vaadin/testbench/blob/main/vaadin-testbench-shared/src/main/java/com/vaadin/testbench/commands/TestBenchCommandExecutor.java) |
| S5 | Replaying S4's loop: headless-new reaches 375 × 667 after **1 correction**. Headed stays 500 × 667 after 5 corrections, so TestBench would throw. | experiment S1/S2 |
| S6 | Chrome CDP through ChromeDriver (`Emulation.setDeviceMetricsOverride {width:375, height:667, deviceScaleFactor:0, mobile:false}`) gives an exact 375 × 667, headless and headed. `resize` has fired on return. It **survives navigation** in that tab. A **new tab** is not affected (1280 × 881). `Emulation.clearDeviceMetricsOverride` restores the window's size. | experiment C1 (`cdp.js`); Selenium Java `HasCdp.executeCdpCommand` is already used in `se/SeleniumAdapter.java` L148–152 |
| S7 | A tab opened with `newWindow(TAB)` after a window resize has the resized window's viewport. Tabs share the OS window. | experiment S1/S2 ("new tab") |
| S8 | The adapter waits for Vaadin with TestBench's `waitForVaadin()`. | `se/SeleniumSession.java` L158–160 |

### Inferences

- **Can the exact viewport be guaranteed?** On Chrome, yes, through CDP emulation (S6), in one command, for any size, headed or headless. With window sizing alone: in headless-new, yes, after one measure-and-correct (the gap is a constant 143 px of height). In headed mode, not below 500 px wide, and window-manager decorations add an unknown offset that must be measured. On Firefox (window sizing only), it needs measure-and-correct. The size Firefox can reach and its minimum width are **unverified here**.
- **How far off:** width 0 px and height −143 px (Chrome 153, headless-new or headed without a window manager). Headed with a window manager adds the title bar and borders on top. Below 500 px wide in headed mode, the width is off by up to 500 − w.
- **Round trips per resize:** Set Window Rect is 1 command. A guaranteed viewport by window costs ≥ 3 (set, then measure in one script, then correct) or TestBench's ≥ 4 (setPosition, setSize, 2 measuring scripts), plus 4 per correction. CDP costs 1 (plus 1 to verify if wanted). Caching the chrome delta per browser makes later resizes 1–2 commands.
- **Effect on other windows:** window sizing changes every tab of that browser, including `openWindow()` sessions (S7, D6), but not `openAsUser()` (a separate browser). A CDP override changes only the tab it is sent to, so per-session sizing needs the adapter to send it after `driver()` has switched to that session's handle.
- **Shared browser leak:** window sizing must be undone in `SharedBrowser.clean()` (back to 1280 × 1024) or the next test inherits it (D7). CDP overrides are dropped with the closed tab.

## 4. When and whether Vaadin's server learns the size

| # | Fact | Source |
|---|---|---|
| V1 | `Page.addBrowserWindowResizeListener` and `Page.windowSizeSignal()` both call `ensureResizeListener()`. It registers `forwardWindowResize()` on the UI element and listens to `"window-resize"` with `.debounce(300)`, reading `event.w` and `event.h`. | Flow [`Page.java`](https://github.com/vaadin/flow/blob/main/flow-server/src/main/java/com/vaadin/flow/component/page/Page.java) (main) |
| V2 | `forwardWindowResize` JS: `window.addEventListener('resize', …)` and dispatch `window-resize` with `w = document.documentElement.clientWidth` and `h = …clientHeight` ("the viewport size, excluding scrollbars"). | Flow [`Element.java`](https://github.com/vaadin/flow/blob/main/flow-server/src/main/java/com/vaadin/flow/dom/Element.java) (main) |
| V3 | `ExtendedClientDetails` is "automatically collected and sent during UI initialization" (bootstrap parameters `v-ww/v-wh` for the window and `v-bw/v-bh` for the body). `getWindowInnerWidth/Height` delegate to the window size signal (`peek()`). The body sizes are plain fields that change only through `refresh(..)`, which runs `Vaadin.Flow.getBrowserDetailsParameters()` in a round trip. | Flow [`ExtendedClientDetails.java`](https://github.com/vaadin/flow/blob/main/flow-server/src/main/java/com/vaadin/flow/component/page/ExtendedClientDetails.java) (main) |
| V4 | The Playwright adapter's idle check is `!Object.values(flow.clients).some(c => c.isActive())`. TestBench's `waitForVaadin` checks the same client state. | `pw/PlaywrightSession.java` L490–505; S8 |

### Inferences

- **Is the server told?** Only if the app (or a component's server side) has registered a resize listener or the size signal. Otherwise it never hears of a resize after bootstrap, and `ExtendedClientDetails` keeps the bootstrap size (body size) until refreshed.
- **When:** at the `resize` event (on return, or up to a few ms later when headed, per P4) plus a 300 ms trailing debounce plus one ordinary server round trip. Each resize event within 300 ms restarts the debounce.
- **The Auto-Wait trap:** during those 300 ms no request is pending, so the idle check (V4) returns at once. A resize step that waits only for Vaadin idle can check server-driven layout before the server has reacted. A responsive resize step should wait ≥ 300 ms (plus a margin for when headed) and then for idle, or the adapter should poll until idle *after* the debounced request has been sent. The cheaper alternative for a whole-test size is to set it **before the first navigation**, so `v-ww/v-wh` already carry it and no event is needed.
- **`screen` differs per tool:** Playwright sets `screen.*` to the viewport (P1/P3). Selenium leaves it at the host's screen (800 × 600 headless, the Xvfb screen headed). Apps that read `ExtendedClientDetails.getScreenWidth()` see different values per Testing Tool.

## 5. Per-tool summary

| | Playwright (+ Dramafinder) | Selenium / TestBench, Chrome | Selenium, Firefox |
|---|---|---|---|
| How to set | `page.setViewportSize(w,h)`; `NewContextOptions.setViewportSize` at creation | Set Window Rect (`window().setSize`), TestBench `resizeViewPortTo`, or CDP `Emulation.setDeviceMetricsOverride` | Set Window Rect plus measure-and-correct |
| Exact viewport? | Yes, any size, headless and headed | CDP: yes. Window: width exact, height −143 px; headed min width 500 | Unverified; needs correction |
| Commands per resize | 1 | CDP 1; window 1 (≥ 3 to guarantee) | ≥ 3 |
| Relayout visible on return | Yes | Yes | Not measured |
| `resize` fired on return | Headless yes; headed shortly after | Yes | Not measured |
| Scope | One page (or the context at creation) | Window: all tabs of that browser. CDP: one tab | All tabs of that browser |
| Default today | 1280 × 720 viewport | Window 1280 × 1024 → viewport 1280 × 881 | Not set (headless screen 1366 × 768) |
| Leaks to next test | No (new context per test) | Window size: yes with `reuse-browser`. CDP: no | Yes with `reuse-browser` |

## Gaps per Testing Tool

- **Dramafinder:** none for viewport control. The adapter calls Playwright directly.
- **TestBench `resizeViewPortTo`:** unusable below 500 px wide in headed Chrome (throws after 5 tries). It also does not reach `openAsUser` browsers. The adapter should not delegate to it for small sizes.
- **Unified Selenium adapter:** `SharedBrowser.clean()` does not reset the window size (D7).

## Open questions found

- Firefox: the minimum window width and the chrome offset in headless and headed mode, and whether geckodriver's Set Window Rect is exact in headless. Firefox was not installed here.
- Headed Chrome under a real window manager (CI desktop runners, developer machines): the size of the extra decoration offset. Measure-and-correct handles it, but costs a round trip.
- Whether Selenium's Chrome path should use CDP emulation (exact, per tab, Chrome-only) or window sizing (all tabs, matches what TestBench users do today). This choice also decides whether `openWindow()` sessions share a size.

## Appendix: experiments

The scripts lived in the session's scratchpad and are summarised here.

- **P (`pw.js`):** Playwright 1.63 Node, `chromium.launch({headless})`. Measured `innerWidth/innerHeight`, `outerWidth/outerHeight`, `documentElement.clientWidth`, a 100%-wide div's box, `screen`, a `(max-width: 600px)` media query and a `resize` counter: at default; right after `setViewportSize(375, 667)`; 300 ms later; in a second page of the same context; in another context; with context `viewport {200,300}`; and with `viewport null`.
- **S (`wd.js`):** ChromeDriver 147 `--disable-build-check` with Chrome for Testing 153 over raw W3C HTTP, args as in `SeleniumAdapter` (`--no-sandbox --disable-dev-shm-usage --window-size=1280,1024 [--headless=new]`). Ran Set Window Rect to 375×667, 768×1024, 1920×1080, 320×480 and 200×300, measuring right away and 300 ms later. Then the TestBench-style correction loop for 375×667, then a new tab. S3 is the same without `--window-size`.
- **C (`cdp.js`):** the same driver setup, then `POST /session/{id}/goog/cdp/execute` with `Emulation.setDeviceMetricsOverride` for 375×667, re-navigation in the same tab, a new tab, and `clearDeviceMetricsOverride`.
