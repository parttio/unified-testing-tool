# Prior art and evidence for the layout script

Research for [#653](https://github.com/parttio/unified-testing-tool/issues/653), which blocks the layout script ([#491](https://github.com/parttio/unified-testing-tool/issues/491), `LayoutScript.SOURCE` run by `AdapterSession.measureLayout(selectors)`), part of [ADR 0011](../adr/0011-responsive-testing-viewports-layout-findings-viewport-comparison.md). It builds on [responsive-evaluation.md](https://github.com/parttio/unified-testing-tool/blob/research/responsive-evaluation/docs/research/responsive-evaluation.md) (#475) and does not repeat it. Decisions taken as given: #478 (three Layout Findings, Grid exempt, no allow-lists), #485 (`String measureLayout(List<String>)`, no generic JavaScript), #487 (Reach words `(clipped)` / `(behind …)`, results matched to `find()` matches in document order).

Researched 2026-11-25. Sources were read at these commits: Lighthouse `v11.7.1` (last with the audits) and `ed5eb55` (13.6.0), axe-core `32d9bd5` (4.14.1), Playwright `b9cff8a` (1.66.0-next), WebdriverIO `cfd6c6b`, Cypress `5a30d97`, Siteimprove Alfa `5bf5ece`, IBM Equal Access `9c48551`, Galen `6c7dc1f`, Chrome DevTools frontend `2049f23`, TestBench `2bdd910` (25.5-SNAPSHOT), Dramafinder `2.2.3` (Maven Central sources jar), `vaadin/web-components` `4be8af7` (main, 25.4.0-beta2) and the npm packages `@vaadin/*@25.0.0`, `25.2.0` and `25.3.5` (this repo uses Vaadin 25.3.5, `pom.xml` L55). Measurements were run on Chromium 158.0.8127.36 (headless, Playwright's bundled build) in this container. The prototype and fixtures are throwaway and are not committed. Their contents are described in §4 and §5.

**Verified** means read in the source or reproduced in the prototype. **Inference** is marked as such.

## Answer in short

- **Write our own script and adapt ideas. Take no dependency and vendor no library.** No tool returns these three Layout Findings plus per-element Reach in one injected script:
  - axe-core, IBM Equal Access and Siteimprove Alfa have **no automated 1.4.10 Reflow overflow check**;
  - Lighthouse removed both candidate audits in 12.0;
  - test-runner visibility helpers answer "is this one element clickable", one element and one round trip at a time, and some of them scroll the page to find out.

  The pieces worth copying are small and well understood:
  - **Playwright's `expectHitTarget`** (Apache-2.0, about 80 lines) for hit-testing through shadow roots. Vendor it with attribution only if we want its WebKit and `display: contents` workarounds; otherwise re-implement it.
  - **Alfa's `isClipped`** (MIT; ideas only, since it runs on a serialised page model): clip along the *containing-block* chain, not the DOM chain. Content left of or above a scroll container's origin is unreachable; content right of or below it is reachable.
  - **Cypress's `canClipContent`** (MIT; ideas): an absolutely positioned element escapes `overflow: hidden` on ancestors between it and its offset parent.
  - **Vaadin's own state attributes**, read the way Vaadin's own tests read them: AppLayout `overlay` and `drawer-opened`; MenuBar's collapsed buttons with `visibility: hidden; position: absolute`; Grid's scroller living in the `vaadin-grid` shadow root.
- **Evidence it works:** a 205-line prototype returned exactly the expected findings and Reach on six fixture pages, including a real Vaadin 25 AppLayout at 375 px:
  - the content part scrolls sideways while the document doesn't;
  - Grid's own scroll is exempt;
  - drawer items are hidden "in the closed AppLayout overlay drawer";
  - collapsed menu items are hidden "in the MenuBar overflow menu";
  - with the drawer open, every content component is "behind the AppLayout drawer" and no overlap is reported.

  It also surfaced four pitfalls that need a rule in the real script (§2): sub-pixel 1 px overflow, `overflow-x: hidden` on `body`, `pointer-events: none`, and components matched inside other components' shadow roots.
- **Cost is low on realistic pages.** On a Vaadin AppLayout page with 4,058 composed-tree nodes, 311 matched components and 20 selectors, the script takes **13–21 ms in the page** and **19–25 ms as a Playwright round trip** (median of 11). That compares with about 1 ms for a no-op `evaluate`. It grows roughly linearly: 26,000 nodes and 5,000 matches take 134–141 ms in the page. **Return a JSON string, not an object:** Playwright's object serialisation more than doubled the round trip (43–53 ms instead of 19–25 ms on the same page). This fits `measureLayout` returning `String`.
- **Fixtures for #491** (§5): `clean`, `sideways-document`, `sideways-container` (including a Grid that must stay silent), `clipped`, `overlap` and `applayout-375` (closed drawer, opened drawer, MenuBar overflow). Each has listed tricky cases.
- **Three decisions for #491 surfaced here** (§6):
  1. Vaadin **Tabs** scroll sideways by design at 375 px, so should Tabs be exempt like Grid?
  2. Which components count as **interactive** for overlap?
  3. Whether selectors match **inside** other components' shadow roots, which must mirror `find()` on each Testing Tool.

---

## 1. Prior art

### Summary table

| Candidate | What it checks | Algorithm | Shadow DOM | Maintained | Licence | Size | One injected script? |
|---|---|---|---|---|---|---|---|
| **Lighthouse `content-width`** | `innerWidth === outerWidth` | Compares two numbers (LH1) | n/a | **Removed** with the PWA category in 12.0 (LH3) | Apache-2.0 | trivial | Yes, but useless here |
| **Lighthouse `tap-targets`** | Tap targets < 48 px that overlap a neighbour's "finger" area | Selector list, `getClientRects`, `elementFromPoint` at the centre after **scrolling** and after **disabling pointer events on all fixed/sticky elements**, then finger-rect overlap ratio ≥ 0.25 (LH2) | Collects through open shadow roots (LH4); hit test with `document.elementFromPoint` + `contains` | **Removed** in 12.0, replaced by axe `target-size` (LH3) | Apache-2.0 | ~390 + 350 lines | No: mutates the page (scroll, class injection) and runs over the DevTools protocol |
| **axe-core** | No 1.4.10 rule (earlier research X1). `target-size`/`target-offset` (disabled by default) | Own spatial grid + emulated paint order (`createGrid`, `visuallySort`, `getRectStack`), not `elementsFromPoint` (AX1). `shadowElementsFromPoint` deprecated since 4.4 (AX2). `isModalOpen` guesses a modal by a ≥ 75 % viewport element at sample points (AX3) | Yes, via its virtual tree | Active | **MPL-2.0** (file-level copyleft) | `axe.min.js` 4.14.1 = 653,780 bytes | Injectable, but no rule answers our questions |
| **IBM Equal Access** | Maps only `style_viewport_resizable` (font size in `vw/vh`) to 1.4.10 | Regex over style rules (IBM1) | n/a | Active | Apache-2.0 | n/a | No overflow check |
| **Siteimprove Alfa** | `isClipped`, `isScrolledBehind` predicates; SIA-R83 "text clipped" (1.4.4) **deprecated** as too strict (AL1–AL3) | Containing-block ("positioning parent") chain; `hidden`/`clip` clips both ways, `auto`/`scroll` only before the scroll origin (AL1) | Flat tree | Active | MIT | Library of packages | **No**: evaluates a serialised page model with boxes (inference: gathered by a separate scraper) |
| **W3C ACT 59br37** | Text clipped by `overflow: hidden/clip` at 640×512 (1.4.4) | Ancestor overflow + exceptions for `text-overflow`, `line-height` (W1) | flat tree | W3C | W3C | spec | Rule, not code |
| **Playwright** | Actionability: visible, stable, enabled, **receives events**; `toBeInViewport` | `expectHitTarget`: collects the shadow roots from target to document, calls `root.elementsFromPoint` in each, descends, then walks up `assignedSlot`/host to see if the hit is the target (PW1); visibility = `checkVisibility()` + `visibility: visible` + non-empty box (PW2); `viewportRatio` via `IntersectionObserver` (PW3); click point = content quads ∩ viewport, area > 0.99 (PW4) | **Yes**, including closed roots; works around WebKit and `display: contents` bugs | Active | Apache-2.0 | `expectHitTarget` ≈ 80 lines | Hit test yes; `viewportRatio` is async (needs a frame), so it can't be in a synchronous Selenium `executeScript` |
| **WebdriverIO** `isClickable` / `isDisplayed({withinViewport})` | Centre not covered; any pixel in viewport | `elementFromPoint` at the centre (uses `clientWidth/2`, not the rect width) and at the first client rect, recursing into shadow roots; **scrolls the element to centre** and back if not clickable (WD1–WD2) | Partly | Active | MIT | ~170 lines | Yes, but mutates scroll |
| **Cypress** visibility | Hidden by CSS, by ancestor overflow, or covered | `canClipContent` (offset-parent rules for `position: absolute`), `elIsOutOfBoundsOfAncestorsOverflow` (box fully outside a clipping ancestor), `elementFromPoint` at the centre with a `pointer-events: none` escape (CY1–CY3); new `visibilityStrategy: 'modern'` = `checkVisibility()` + non-empty box (CY4) | Parent walk via composed parent (inference from `getParentNode`) | Active | MIT | ~680 lines (jQuery-based) | Tied to jQuery and Cypress internals |
| **TestBench** `isElementVisibleInViewport` | In viewport and not fully outside any `auto/scroll/hidden` ancestor | Walks `parentElement`, so it **stops at shadow roots** and never sees AppLayout's content part (TB1) | No | Active | **Vaadin Commercial License** (TB2) | ~30 lines | Yes, but can't be copied |
| **Dramafinder** `AppLayoutElement`, `MenuBarElement` | Drawer state and mode; MenuBar items | Reads reflected `drawer-opened` and `overlay` attributes, "rather than measured geometry: the drawer animates" (DF1); skips the hidden overflow button (DF2) | n/a | Active | Apache-2.0 | n/a | No layout measuring |
| **Galen** | Relative layout specs | One WebDriver round trip **per object** (`getLocation`/`getSize` or a `getBoundingClientRect` script) (GA1) | No | Last commit 2019-03-10 | Apache-2.0 | n/a | No |
| **Chrome DevTools** "scroll" badge | Element is a scroll container | `DOM.Node.isScrollable` from CDP, computed by Blink (DT1) | n/a | Active | BSD-3 | n/a | No: CDP, Chromium only |
| **"Unintended body overflow" snippet** | Elements wider than the document | `el.offsetWidth > documentElement.offsetWidth` over `querySelectorAll('*')` (CT1) | No | Blog, 2014 | n/a | 6 lines | Yes, but misses containers, transforms and offset elements |

WCAG's Understanding 1.4.10 lists only one Test Rule, "Meta viewport allows for zoom" (W2). We found no maintained automated Reflow overflow checker, bookmarklet or axe plugin in the primary sources we read. (Searching third-party plugin registries exhaustively was out of scope.)

### What each one teaches us

- **Hit-test, don't intersect.** Lighthouse, WebdriverIO, Cypress and Playwright all confirm "covered" with `elementFromPoint`/`elementsFromPoint` at a point inside the target, and so does the earlier research's C3. Box intersection alone over-reports: siblings in a flex row touch at fractional widths, and badges deliberately overlap.
- **Don't scroll.** Lighthouse and WebdriverIO scroll the page to hit-test off-screen targets. A test's page state must not change, and Reach words already say "visible after scroll" without testing it. So the script hit-tests only points inside the viewport. Off-screen elements get "after scroll", which #487 leaves unprinted.
- **Clip along the containing block, not the DOM parent.** Cypress (CY1) and Alfa (AL1) both learned that `position: absolute`/`fixed` escapes `overflow: hidden` on ancestors that aren't its containing block. TestBench's DOM-parent walk (TB1) gets this wrong and also stops at shadow roots.
- **Scroll origin matters.** Alfa: "Scrollbars are only created to scroll to the bottom or right of the content, thus stuff on the top or left cannot be scrolled to" (AL1). Alfa also notes it can't tell "left because scrolled" from "left of the origin" once a container has scrolled (AL2). We can, because we read `scrollLeft`.
- **Vaadin states by attribute, not by geometry.** Dramafinder and Vaadin's own tests read `overlay`, `drawer-opened` and the collapsed buttons' inline style (V6, V7, DF1). Geometry is mid-animation for about 200 ms after a toggle.
- **Licences:**
  - Playwright, Lighthouse and IBM are Apache-2.0: we may vendor them with NOTICE attribution.
  - Alfa, Cypress and WebdriverIO are MIT: we may vendor them with the copyright notice.
  - axe-core is MPL-2.0: vendoring keeps those files under MPL, which is possible but awkward for one embedded script.
  - TestBench is under the Vaadin Commercial License: **do not copy**.

---

## 2. Evidence that the approach works, and pitfalls

### Published accuracy

No candidate publishes an accuracy figure for overflow, clipping or overlap detection.
- The axe-core team deprecated its own `shadowElementsFromPoint` in favour of a spatial grid, which is evidence that composed hit-testing is fiddly (AX2).
- Alfa deprecated SIA-R83 "due to a too-strict interpretation of WCAG", which is evidence that a clipping check over-reports when it ignores intent (AL3).
- Lighthouse's tap-target audit took a dedicated high-DPI fix (LH3, changelog "fix seo-tap-targets in high DPI" #14866).
- Playwright's code carries named browser bugs: Chromium `display: contents` in `elementsFromPoint` (crbug 1342092), WebKit putting the outer host first, and the cross-browser differences of [csswg-drafts#556](https://github.com/w3c/csswg-drafts/issues/556) (PW1).

The positive evidence is our own prototype on fixtures (§4, §5).

### Pitfalls, with what we measured or read

| Pitfall | Evidence | Rule for the script |
|---|---|---|
| **Sub-pixel rounding** | `scrollWidth`/`clientWidth` are `long` (CSSOM View IDL, S1). A child of 375.6 px in a 375 px viewport gives `scrollWidth` 376 vs `clientWidth` 375, a 1 px "overflow". 375.4 px gives 375. The same applies inside containers (200.6 px gives 201 vs 200). Same at DPR 1, 2 and 3 (measured). Vaadin's own `OverflowController` compares `Math.ceil(scrollLeft) < Math.ceil(scrollWidth − clientWidth)` (V5). MenuBar measures with rects because "unlike `offsetWidth` and `scrollWidth`, the rect keeps the fraction that browser zoom leaves" (V7). | Sideways scroll only when `scrollWidth > clientWidth + 1`. Overlap only when the intersection is > 1 px on both axes. |
| **`scrollWidth` vs `getBoundingClientRect`** | `getBoundingClientRect` returns doubles **with transforms**; `clientWidth` ignores transforms (S1). A `translateX(200px)` 300 px box made the root `scrollWidth` 500 while the element's own `scrollWidth` stayed 300 (measured). | Use `scrollWidth > clientWidth` for "scrolls sideways" (it includes transformed overflow) and rects for boxes, clipping and hit points. |
| **Fixed elements** | A `position: fixed` box running off the right edge does **not** add to `scrollWidth` (root stayed 375 with a box to 460, measured), so it is unreachable, not scrollable. Fixed boxes escape every `overflow` ancestor unless one has a transform, filter or `contain` (containing-block rules; Cypress and Alfa encode the same, CY1, AL1). | Fixed: skip overflow ancestors without a containing block. A fixed box whose centre is outside the viewport is clipped (prototype gap: add). |
| **Sticky/fixed headers covering content** | Lighthouse disables pointer events on all fixed/sticky elements because coverage "depends on the scroll position" (LH2). WCAG advises un-fixing sticky bars at small sizes (W2, C34). | Report Reach "behind" at the *current* scroll position only, which is what a user sees. Don't mutate styles. |
| **`overflow-x: hidden` on `html`/`body`** | The root `scrollWidth` stays 600 > 375 while the user cannot scroll (measured). | Read the viewport's propagated `overflow-x` (html's, else body's). If `hidden`/`clip`, don't report sideways scroll: report content beyond it as **clipped by the page**. The prototype did the second but not the first, so it needs a fix. |
| **Transforms** | Hit-testing applies transforms (S2: "when applying the transforms that apply to the descendants of the viewport"). `transform: scale(0.9)` on a neighbour produced no false overlap (measured). An ancestor transform makes it the containing block for fixed descendants. | Use rects and hit tests; treat a transformed ancestor as a containing block. |
| **`visibility` / `opacity`** | `checkVisibility()` options cover `visibility` and `opacity` (S3). Playwright treats `opacity: 0` as visible (PW2), and so does the Unified API's `isVisible()` on Playwright. Hit testing still hits `opacity: 0` boxes (inference from S2: paint order, not paint result). | "hidden" = `checkVisibility({visibilityProperty: true})` false or an empty box, matching Playwright. Report `opacity: 0` as visible so the Findable View never contradicts `isVisible()`. |
| **`pointer-events: none`** | Such boxes are not hit-test targets (S2 note). Prototype v1 reported a `pointer-events: none` badge over a button as an overlap and as "behind button Inbox". Both were wrong. Cypress special-cases it (CY2). | Skip hit tests and overlap candidacy for elements with computed `pointer-events: none`. A covering element with `pointer-events: none` is invisible to the hit test, which is right: clicks pass through it. |
| **Scroll bars taking width** | `clientWidth` excludes a rendered scroll bar (S1). In headless Chromium, a 2000 px tall page gave `innerWidth = clientWidth = 375`: the scroll bar took no width (measured). `100vw` therefore caused no overflow there, but would on headed or classic-scrollbar browsers (inference). | Always use `clientWidth`, never `innerWidth`. Expect a `100vw` + scroll bar case to differ between headless and headed. Keep the 1 px tolerance. |
| **Zoom / device pixel ratio** | DPR 1, 2 and 3 gave identical CSS-px results (measured). `elementsFromPoint` returns nothing for points outside the viewport (S2). | Clamp hit points into `[0, clientWidth−1] × [0, clientHeight−1]`. Report boxes in CSS px. Browser zoom is not settable by Playwright or WebDriver, so it is out of scope. |
| **Lazy rendering and virtual scrolling (Grid)** | Grid scrolls its own `#table` inside its shadow root (`new OverflowController(this, this.$.table)`, V4). Rows outside the rendered window don't exist, and cells are reused (inference from Grid's virtualizer). At 375 px a 10×120 px-column Grid gave **no** finding (measured). | Exempt scrollers whose root node's host is `vaadin-grid`. Never infer "lost" components from Grid rows: they are absent, not clipped. |
| **Animations** | AppLayout's drawer animates `transform, visibility` (V2). Dramafinder reads attributes because geometry animates (DF1). | Measure after the core's settle and debounce wait (ADR 0011). Explain disclosures by attribute, not by geometry. |
| **Duplicate matches inside shadow roots** | With selector `button`, the prototype matched both `x-button` and the `<button>` inside its shadow root, and reported the same overlap twice (measured). | Match the way the core's `find()` does on that Testing Tool (open question 3). Pair only outermost matched elements for overlap. |

---

## 3. Shadow DOM and Vaadin specifics

### Hit testing and styles across shadow roots

- **`elementsFromPoint` on a `ShadowRoot` is not in any current spec.** CSSOM View declares `elementFromPoint`/`elementsFromPoint` on `partial interface Document` only (S2). The DOM standard's `DocumentOrShadowRoot` mixin now holds only `customElementRegistry` (S4). Browsers still ship it: Chromium 158 has `ShadowRoot.prototype.elementsFromPoint` (measured). Playwright relies on it in all three engines, with workarounds (PW1).
- **`document.elementsFromPoint` retargets to hosts.** At a point over a button inside `x-button`'s shadow root, it returned `x-button, x-button, div, body, html` (measured). So one must descend through `host.shadowRoot.elementFromPoint` to find the inner hit. Walking up from the hit needs `assignedSlot` first, then `parentElement`, then the shadow root's `host`. Playwright uses this flat-tree walk because "browser performs hit testing on the composed tree" (PW1).
- **`getComputedStyle` works on shadow-tree elements unchanged.** Styles inherit along the flat tree, so a slotted drawer item inherits `visibility: hidden` from the closed drawer part. `checkVisibility()` returned false for drawer items at 375 px (measured).
- **Overflow inside shadow parts** is invisible to light-DOM walks: TestBench's `parentElement` loop (TB1), and any `document.querySelectorAll('*')` sweep such as CT1. The prototype's sweep walks the document plus every open shadow root with one `TreeWalker` each (Lighthouse's `getElementsInDocument` does the same, LH4). It found AppLayout's content scroller and Tabs' scroller (measured). Closed shadow roots are unreachable by any page script. Vaadin uses open roots.

### AppLayout

- **The content part's markup changed:** `<div content>` in 25.0.0 and 25.1.0, `<div part="content">` from 25.2.0, including 25.3.5 (V1). The earlier research's `[part~=content]` holds for this repo's 25.3.5 but not for 25.0/25.1. **Don't hard-code the selector.** Sweep every `overflow-x: auto|scroll` container, as the prototype does; it found `vaadin-app-layout [content]` on 25.0.0.
- **Overlay state:**
  - `overlay` is a read-only property reflected to an attribute and driven by `--vaadin-app-layout-drawer-overlay` (V2), which the base styles set under `(max-width: 800px), (max-height: 600px)`.
  - In overlay mode the drawer gets `role="dialog"` and `aria-modal="true"` (V2).
  - A closed drawer is `visibility: hidden` and translated off-screen. An opened overlay drawer shows `[part=backdrop]` with `position: fixed; inset: 0; pointer-events: auto; z-index: 2` (V2).
  - Vaadin's own test asserts `layout.overlay` and `layout.drawerOpened` (V6).
- **What the prototype reports at 375 px:**
  - **Drawer closed:** `overlay=true, drawerOpened=false`. The drawer's buttons are `hidden (in the closed AppLayout overlay drawer)`, explained by the light-DOM `slot="drawer"` child of a `vaadin-app-layout` without `drawer-opened`. Note that `assignedSlot` points into the shadow root, so use `parentElement` for this check.
  - **Drawer opened:** all 19 navbar and content components are `behind the AppLayout drawer`. The hit lands on the `backdrop` part inside the AppLayout shadow root, which the script names "the AppLayout drawer" (#487's wording). No overlap is reported: the drawer's own items sit under `[aria-modal="true"]`, so they are treated as an open overlay.
- **Touch bottom navbar** needs `pointer: coarse` (earlier research A2), so it is out of reach by size alone.

### MenuBar

- **Collapsed buttons** get `style.visibility = 'hidden'` and `style.position = 'absolute'`. This is on 25.0.0, 25.3.5 and main alike (V3).
- **The overflow button** is `vaadin-menu-bar-button[slot=overflow]`, whose `hidden` attribute is toggled (V3). The host's `has-overflow` attribute exists only on main (25.4) (V3), so on 25.3.5 read the overflow button's `hidden` instead.
- **Vaadin's own tests** assert exactly `visibility: hidden` + `position: absolute` for collapsed buttons, and `has-overflow` on main (V7).
- **Measured at 375 px:** 3 of 7 items were collapsed (`Format`, `Tools`, `Help`) and reported `hidden (in the MenuBar overflow menu)`. At 1280 px nothing was collapsed. The overflow button itself is `hidden` without explanation, which is correct.

### Grid, Tabs and other scrollers

- **Grid:** `OverflowController` sets `overflow="… start end"` on scrollable hosts. It is used by Grid, Scroller, VirtualList and TabSheet (V4). Grid's horizontal scroller is `#table` in its shadow root. The exemption "root node's host is `vaadin-grid`" worked.
- **Tabs scroll sideways by design.** `:host([overflow]) [part='tabs'] { overflow: auto }`, with back and forward buttons shown on `[overflow~=start|end]` (V5). At 375 px five tabs gave a finding, `vaadin-tabs [part~=tabs]` by 94 px (measured). Per #478 only Grid is exempt, so this is a **true finding under the current rule but likely noise**: open question 1.
- **Open overlays:** Vaadin 25 overlays are `popover="manual"` and shown with `showPopover()` (V8), so `:popover-open` catches dialogs, menus, combo-box dropdowns and notifications generically. Add `dialog[open]` and `[aria-modal="true"]` (the AppLayout drawer). axe's `isModalOpen` instead guesses from an element covering ≥ 75 % of the viewport at sample points (AX3). We don't need that guess.

### Vaadin TestBench, Dramafinder, web-components: anything to reuse?

- **TestBench:** a 30-line in-viewport check that doesn't cross shadow roots, under a commercial licence (TB1, TB2). Nothing to reuse.
- **Dramafinder** (Apache-2.0): the attribute-based reading of `overlay` and `drawer-opened`, and skipping the hidden overflow button (DF1, DF2). Reuse the *rule*, not code. There is no geometry code to reuse.
- **web-components:** the `Math.ceil` comparison of `OverflowController` (V5) and the test helpers' definition of a collapsed button (V7). Copy the conditions, not the code.

---

## 4. Cost

The prototype was a 205-line function expression. It:
1. sweeps the composed tree with one `TreeWalker` per root;
2. reads `scrollWidth`/`clientWidth` for every element and `getComputedStyle` only for overflowing ones;
3. for each matched element: takes the rect and `checkVisibility`, walks the containing-block chain with cached computed styles, and hit-tests the clamped centre through shadow roots;
4. intersects pairs of interactive candidates and confirms each with a hit test;
5. returns findings, elements and Vaadin state.

Method: `page.evaluate` from Node with Playwright on Chromium 158, at viewports 375×667 and 1280×720, with 20 selectors. Each figure is the median of 11 runs. "Dirty layout" means the root width was changed just before the call, so the script pays the layout flush, as it would right after `setViewport`. RT is the wall-clock round trip seen by Node.

| Page | Composed-tree nodes | Matched | JSON | In-page (warm / dirty) | Round trip (warm / dirty) |
|---|---|---|---|---|---|
| **Vaadin AppLayout page** (drawer, MenuBar, 20 sections × 5 TextFields + 5 Buttons + Tabs, Grid 8 × 500) | 4,058 | 311 | 50 KiB | 16 / 18 ms (375); 21 / 19 ms (1280) | **19 / 24 ms** (375); **24 / 25 ms** (1280) |
| Synthetic, 100 cards (shadow root each, nested shadow buttons) | 2,607 | 500 | 71 KiB | 12–13 / 16–19 ms | 16–17 / 23 ms |
| Synthetic, 300 cards | 7,807 | 1,500 | 215 KiB | 38–43 / 45 ms | 48–57 / 57–62 ms |
| Synthetic, 1,000 cards | 26,007 | 5,000 | 720 KiB | 139–141 / 134–139 ms | 163–170 / 193–207 ms |
| No-op `page.evaluate('1')` | | | | | 0.8–1.4 ms |

- **Where the time goes** (Vaadin page, one run): composed-tree sweep 0.5–0.8 ms; sideways scan 2.5–4.7 ms; per-element reach 8.6–10.9 ms; overlap 0.0–0.5 ms. Reach dominates. Its cost follows `selectors × nodes` `matches()` calls, plus one ancestor walk and one hit test per match. Using `querySelectorAll` per root instead of `matches()` per node is the obvious optimisation (not measured).
- **Returning an object instead of a string** made the round trip 43–53 ms on the Vaadin page and 53 ms on 100 cards, against 19–25 ms and 16–17 ms with in-page `JSON.stringify`. At 1,000 cards it was 474 ms against 170 ms. Playwright's value serialiser is the cost (measured; the cause is an inference). **Return `JSON.stringify(result)`.**
- **Does a `TreeWalker` over the selectors stay well under a round trip?** The sweep alone is under 1 ms at 4,000 nodes and 3 ms at 26,000. It is negligible against the round trip. The whole script costs about 20 no-op round trips on a realistic Vaadin page, still far below the 300 ms resize debounce that ADR 0011 already waits after each `setViewport`. On pages with tens of thousands of nodes it reaches 150–200 ms, which matters only when layout checks are on (they are off by default).
- **Selenium was not measured.** The container's ChromeDriver is 147.0.7727.24 while the Chromium binary is 158, and ChromeDriver refuses mismatched majors. Inference: `executeScript` runs the same in-page code, so in-page time is the same. The WebDriver HTTP round trip and the JSON string transfer add a few milliseconds.

---

## 5. Recommendation and fixtures

### Recommendation: write our own and adapt ideas

**Don't take a dependency.** No candidate computes these findings:
- axe-core is 650 KB of MPL code without a Reflow rule;
- Lighthouse's audits are gone, and it runs over CDP;
- Alfa needs its own page model;
- TestBench's code is commercial;
- Galen is dead.

**Don't vendor a library.** The useful parts are each under 100 lines and need to be bent to our rules anyway: no scrolling, our Reach words, Vaadin explanations, a 1 px tolerance.

**Write `LayoutScript.SOURCE` ourselves** (about 250–300 lines), adapting these ideas with a credit comment:

1. **Sweep** the document and each open shadow root with `TreeWalker` (Lighthouse LH4). Match selectors the way `find()` does on that Testing Tool. Return results grouped per selector in document order (#487).
2. **Sideways scroll:**
   - the document: `scrollingElement.scrollWidth > clientWidth + 1`, unless the viewport's propagated `overflow-x` is `hidden`/`clip`;
   - every element: `scrollWidth > clientWidth + 1`, computed `overflow-x` `auto|scroll`, `checkVisibility()`, and the root's host isn't `vaadin-grid` (plus Tabs if decided);
   - report the container by host tag and part, not by a hard-coded AppLayout selector.
3. **Clipped:**
   - negative document coordinates (`rect.right + scrollX ≤ 0` or `rect.bottom + scrollY ≤ 0`) for non-fixed boxes;
   - a fixed box whose centre is outside the viewport;
   - else walk the **containing-block chain** (Alfa AL1, Cypress CY1): skip `display: contents`; fixed skips to the first transform/filter/contain ancestor; absolute skips static ancestors;
   - `hidden|clip` clips when the centre is outside the padding box;
   - `auto|scroll` clips only before the scroll origin, compensating `scrollLeft`/`scrollTop`. RTL needs the mirrored origin: not done in the prototype.
4. **Behind:** hit-test the element's centre, clamped into the viewport and to its visible part, by descending `elementFromPoint` through shadow roots and walking up `assignedSlot`/host (Playwright PW1).
   - Either vendor `expectHitTarget` under Apache-2.0 with a NOTICE line, or keep the 15-line Chromium-sufficient version plus Playwright's two workarounds, credited.
   - Skip elements with `pointer-events: none`.
   - Name the cover: the nearest matched component, or a known Vaadin part ("the AppLayout drawer" for `backdrop`/`drawer`), else tag and text.
   - Off-screen elements get "after scroll" and no hit test.
5. **Overlap:**
   - pairs of outermost matched interactive elements, not in an open overlay (`:popover-open, dialog[open], [aria-modal="true"]`) and without `pointer-events: none`;
   - the intersection must be > 1 px on both axes and neither may contain the other;
   - confirm by hit test at the intersection's centre: the hit must belong to one of the pair; report which one is covered.
6. **Explain hidden components** by Vaadin attributes:
   - closed drawer: light parent `vaadin-app-layout` without `drawer-opened`, plus `overlay`;
   - MenuBar overflow: collapsed `vaadin-menu-bar-button` with inline `visibility: hidden`, or the overflow button not `[hidden]`; `has-overflow` only from 25.4.

   Also return `appLayouts[{overlay, drawerOpened}]` and `menuBars[{hasOverflow, collapsed}]` for the explicit assertions in ADR 0011.
7. **Never mutate** scroll, styles or classes (unlike LH2, WD2). Return a **JSON string**.

### Fixture pages for #491

Each fixture is a Flow view at 375×667 in the demo app, run on Playwright and Selenium (Chrome). The prototype's HTML versions gave the outcomes shown, except where marked *expected* (a gap the real script must close).

| Fixture | Content | Expected findings | Tricky cases to include |
|---|---|---|---|
| **clean** | Flex row of buttons that wraps; full-width fields; a long URL with `overflow-wrap: anywhere`; content below the fold inside a scrolling shell | **None**; "Below the fold" Reach = after scroll (not printed) | Two buttons of 100.4 px side by side (touching, no overlap); a 0.4 px overflow child (no scroll); a `transform: scale(0.9)` neighbour |
| **sideways-document** | A 700 px wide block in a plain view without AppLayout | Document sideways scroll, by 325 px | 0.6 px overflow gives none (*expected*: needs the +1 tolerance); `translateX` pushing past the edge gives a finding; `position: fixed` box off the right edge gives no scroll but **clipped** (*expected*); `overflow-x: hidden` on body: **no** sideways finding, wide content reported clipped by the page (*expected*) |
| **sideways-container** | AppLayout with a 560 px fixed-width row in content; an explicit `overflow-x: auto` div | Container sideways scroll on AppLayout's content part (by 193 px on the prototype); document clean | **A Grid with many fixed-width columns: no finding** (#491 acceptance); an `overflow-x: hidden` container gives no sideways finding; Tabs with five tabs (finding today, see open question 1) |
| **clipped** | A button pushed out of an `overflow: hidden` box; a button below an `overflow: clip` box; a button at `left: -300px` | Clipped ×3, by the box ×2 and "negative coordinates" | **Not** clipped: an absolute child escaping a non-positioned `overflow: hidden` ancestor; a `position: fixed` child of a 10 px `overflow: hidden` box; a button in an `overflow: auto` box reachable by scrolling right. Clipped: content left of the origin of a container that was then scrolled (*expected*); RTL container (*expected*) |
| **overlap** | Two absolutely positioned buttons overlapping; two shadow-DOM components (e.g. two `vaadin-button`s) overlapping | Overlap ×2, naming the covered one; Reach `(behind button "Delete")` | No finding: a `pointer-events: none` badge over a button; a button under an open Dialog or popover (Reach behind, no overlap: overlay excluded); a button under a non-interactive fixed footer (Reach `behind`, not an overlap) |
| **applayout-375** | AppLayout + DrawerToggle + MenuBar (7 items) in the navbar; 3 drawer items; TextField, Tabs, Grid, 4 Buttons in content | Drawer closed: content part sideways (if the row is wide), no clipping; drawer items **hidden, in the closed AppLayout overlay drawer**; 3 menu items **hidden, in the MenuBar overflow menu**; `overlay=true`. Drawer opened: every content and navbar component **behind the AppLayout drawer**, **no overlap**, drawer items visible | Same view at 1280: `overlay=false`, nothing collapsed, no findings; measure after the drawer's transition (about 200 ms) or read attributes only; MenuBar on 25.3.5 has no `has-overflow` |

---

## 6. Open questions

1. **Tabs' own horizontal scroll** gave a finding at 375 px. Exempt `vaadin-tabs` (and `vaadin-scroller`, `vaadin-tabsheet`, `vaadin-virtual-list`, which use `OverflowController`) like Grid, or keep Grid-only as #478 says and let Tabs report? We recommend exempting components that render their own scroll affordance (Tabs' arrows).
2. **What is "interactive" for overlap?** The script only gets Wrapper selectors. The options:
   - a fixed list of interactive tags and roles plus `tabIndex ≥ 0` (prototype);
   - the core passes a flag per selector.

   The second fits ADR 0011's "core owns everything else".
3. **Matching inside shadow roots.** Playwright's CSS engine pierces open shadow roots; Selenium's `By.cssSelector` does not. `measureLayout` results are matched to `find()` matches by position (#487), so the script must enumerate exactly what `find()` enumerates on each adapter. If not, positions disagree and Reach is dropped.
4. **Partial coverage.** A centre-point hit test misses a button half under a footer, and Lighthouse accepted the same limitation (LH2 comment). Sample more points (centre plus four inset corners) at five times the hit tests? Not measured.
5. **The AppLayout drawer while animating:** the opened drawer's items are reported only after the transition. Is the core's settle enough, or should the script report `transitioning` when `getAnimations()` is non-empty on the drawer part?

---

## Sources

### Specifications and W3C

- **S1** CSSOM View, `scrollWidth` / `clientWidth` algorithms and `readonly attribute long` IDL: <https://drafts.csswg.org/cssom-view/#dom-element-scrollwidth>, <https://drafts.csswg.org/cssom-view/#dom-element-clientwidth>, `getBoundingClientRect`/`getClientRects` "applying the transforms": <https://drafts.csswg.org/cssom-view/#dom-element-getclientrects>
- **S2** CSSOM View, `partial interface Document { elementFromPoint; elementsFromPoint }`, the steps (empty outside the viewport, paint order, transforms) and the `pointer-events` note: <https://drafts.csswg.org/cssom-view/#dom-document-elementsfrompoint>
- **S3** CSSOM View `checkVisibility` options (`opacityProperty`, `visibilityProperty`, `contentVisibilityAuto`): <https://drafts.csswg.org/cssom-view/#dom-element-checkvisibility>
- **S4** DOM Standard, `interface mixin DocumentOrShadowRoot { customElementRegistry }`: <https://dom.spec.whatwg.org/#documentorshadowroot>; cross-browser hit-test differences: <https://github.com/w3c/csswg-drafts/issues/556>
- **W1** ACT rule 59br37 "Zoomed text node is not clipped with CSS overflow": <https://www.w3.org/WAI/standards-guidelines/act/rules/59br37/>
- **W2** Understanding SC 1.4.10 Reflow (techniques C31–C38, F102; only test rule b4f0c3; sticky content advice): <https://www.w3.org/WAI/WCAG22/Understanding/reflow.html>

### Lighthouse (Apache-2.0)

- **LH1** `content-width`: <https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/audits/content-width.js#L50-L69>
- **LH2** tap-targets gatherer:
  - target selectors: <https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/gather/gatherers/seo/tap-targets.js#L13-L31>
  - centre hit test after scrolling: <https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/gather/gatherers/seo/tap-targets.js#L149-L162>
  - disabling pointer events on fixed/sticky elements: <https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/gather/gatherers/seo/tap-targets.js#L172-L185>
  - main loop and comments: <https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/gather/gatherers/seo/tap-targets.js#L194-L283>
  - audit with 48 px finger and 0.25 ratio: <https://github.com/GoogleChrome/lighthouse/blob/v11.7.1/core/audits/seo/tap-targets.js#L45-L48>
- **LH3** 12.0.0 notes: PWA category removed; `tap-targets` replaced by `target-size`: <https://github.com/GoogleChrome/lighthouse/blob/ed5eb559f5bb2422df28ec5f80766909acc3a460/changelog.md#L1386-L1399>; high-DPI fix #14866: same file L2388
- **LH4** `getElementsInDocument` descends into `shadowRoot`: <https://github.com/GoogleChrome/lighthouse/blob/ed5eb559f5bb2422df28ec5f80766909acc3a460/core/lib/page-functions.js#L56-L80>

### axe-core (MPL-2.0)

- **AX1** `getRectStack` over its own grid: <https://github.com/dequelabs/axe-core/blob/32d9bd503128d4b52d818ca89140c1debe5d70d5/lib/commons/dom/get-rect-stack.js#L4-L48>; grid into shadow roots: <https://github.com/dequelabs/axe-core/blob/32d9bd503128d4b52d818ca89140c1debe5d70d5/lib/commons/dom/create-grid.js#L127>
- **AX2** `shadowElementsFromPoint` "@deprecated As of axe-core 4.4": <https://github.com/dequelabs/axe-core/blob/32d9bd503128d4b52d818ca89140c1debe5d70d5/lib/commons/dom/shadow-elements-from-point.js#L5-L25>
- **AX3** `isModalOpen` (75 % heuristic, `elementsFromPoint`): <https://github.com/dequelabs/axe-core/blob/32d9bd503128d4b52d818ca89140c1debe5d70d5/lib/commons/dom/is-modal-open.js#L13-L77>
- Size and licence: `axe.min.js` 4.14.1 from <https://cdn.jsdelivr.net/npm/axe-core@4.14.1/axe.min.js> (653,780 bytes); npm `license: MPL-2.0`

### IBM Equal Access (Apache-2.0)

- **IBM1** `style_viewport_resizable` mapped to 1.4.4 and 1.4.10, with a `vw|vh` regex: <https://github.com/IBMa/equal-access/blob/9c48551b80eba9fe35d917057ad5684a91c2b990/accessibility-checker-engine/src/v4/rules/style_viewport_resizable.ts#L44-L56>

### Siteimprove Alfa (MIT)

- **AL1** `isClipped`: <https://github.com/Siteimprove/alfa/blob/5bf5ecec0d3dfcd8f26b676ab36ba5b025505350/packages/alfa-style/src/node/predicate/is-clipped.ts#L34-L66>; `isClippedByMovingAway` / `isClipping` with the scroll-origin remark: <https://github.com/Siteimprove/alfa/blob/5bf5ecec0d3dfcd8f26b676ab36ba5b025505350/packages/alfa-style/src/node/predicate/is-clipped.ts#L254-L310>; positioning parent: <https://github.com/Siteimprove/alfa/blob/5bf5ecec0d3dfcd8f26b676ab36ba5b025505350/packages/alfa-style/src/element/helpers/get-positioning-parent.ts>
- **AL2** `isScrolledBehind` limitation remark: <https://github.com/Siteimprove/alfa/blob/5bf5ecec0d3dfcd8f26b676ab36ba5b025505350/packages/alfa-style/src/node/predicate/is-scrolled-behind.ts>
- **AL3** SIA-R83 deprecated "due to a too-strict interpretation of WCAG": <https://github.com/Siteimprove/alfa/blob/5bf5ecec0d3dfcd8f26b676ab36ba5b025505350/packages/alfa-rules/src/sia-dr83/rule.ts#L53-L57>

### Playwright (Apache-2.0)

- **PW1** `expectHitTarget`: <https://github.com/microsoft/playwright/blob/b9cff8a68a090c27d29d856747265732468acff0/packages/injected/src/injectedScript.ts#L1037-L1117>
- **PW2** `computeElementStyleVisibilityVisible`, `computeBox`, `isElementVisible`: <https://github.com/microsoft/playwright/blob/b9cff8a68a090c27d29d856747265732468acff0/packages/injected/src/domUtils.ts#L136-L192>
- **PW3** `viewportRatio` (IntersectionObserver plus rAF): <https://github.com/microsoft/playwright/blob/b9cff8a68a090c27d29d856747265732468acff0/packages/injected/src/injectedScript.ts#L639-L650>
- **PW4** `_clickablePoint` (quads ∩ viewport, area > 0.99): <https://github.com/microsoft/playwright/blob/b9cff8a68a090c27d29d856747265732468acff0/packages/playwright-core/src/server/dom.ts#L256-L286>; "intercepts pointer events": same file L369

### WebdriverIO and Cypress (MIT)

- **WD1** `isElementClickable` (centre and first-rect `elementFromPoint`, shadow recursion): <https://github.com/webdriverio/webdriverio/blob/cfd6c6b2957b3bc92f65cdaa32cc635ee89f8173/packages/webdriverio/src/scripts/isElementClickable.ts#L17-L100>
- **WD2** scrolling to the centre and back: <https://github.com/webdriverio/webdriverio/blob/cfd6c6b2957b3bc92f65cdaa32cc635ee89f8173/packages/webdriverio/src/scripts/isElementClickable.ts#L145-L160>; `isElementInViewport`: <https://github.com/webdriverio/webdriverio/blob/cfd6c6b2957b3bc92f65cdaa32cc635ee89f8173/packages/webdriverio/src/scripts/isElementInViewport.ts>
- **CY1** `canClipContent`: <https://github.com/cypress-io/cypress/blob/5a30d970af1b90446eb618c85453c29696fd8ad9/packages/driver/src/dom/visibility.ts#L250-L292>
- **CY2** `elIsNotElementFromPoint` (`pointer-events: none` case): <https://github.com/cypress-io/cypress/blob/5a30d970af1b90446eb618c85453c29696fd8ad9/packages/driver/src/dom/visibility.ts#L337-L362>
- **CY3** `elIsOutOfBoundsOfAncestorsOverflow`: <https://github.com/cypress-io/cypress/blob/5a30d970af1b90446eb618c85453c29696fd8ad9/packages/driver/src/dom/visibility.ts#L364-L408>
- **CY4** `modernIsHidden` and the `visibilityStrategy` switch: <https://github.com/cypress-io/cypress/blob/5a30d970af1b90446eb618c85453c29696fd8ad9/packages/driver/src/dom/visibility/modernIsHidden.ts>, `visibility.ts` L26

### Vaadin

- **TB1** TestBench `isElementVisibleInViewport` (a `parentElement` walk): <https://github.com/vaadin/testbench/blob/2bdd9106bb187ecf3c02932cb9209b29e9463bcf/vaadin-testbench-shared/src/main/java/com/vaadin/testbench/TestBenchElement.java#L651-L680>
- **TB2** TestBench licence, "Vaadin Commercial License and Service Terms": <https://github.com/vaadin/testbench/blob/2bdd9106bb187ecf3c02932cb9209b29e9463bcf/LICENSE>
- **DF1** Dramafinder `AppLayoutElement` reads `drawer-opened`/`overlay` "rather than from measured geometry": `org/vaadin/addons/dramafinder/element/AppLayoutElement.java` L22–30, L166–167, L265–266 in <https://repo1.maven.org/maven2/org/vaadin/addons/dramafinder/2.2.3/dramafinder-2.2.3-sources.jar> (POM licence "Apache 2")
- **DF2** Dramafinder `MenuBarElement` leaves out the hidden overflow button: same jar, `MenuBarElement.java` L40–65
- **V1** AppLayout content markup:
  - `<div content>` in `@vaadin/app-layout@25.0.0` `src/vaadin-app-layout.js` L154 and 25.1.0: <https://unpkg.com/@vaadin/app-layout@25.1.0/src/vaadin-app-layout.js>
  - `<div part="content">` in 25.2.0 and 25.3.5 (L153): <https://unpkg.com/@vaadin/app-layout@25.3.5/src/vaadin-app-layout.js>
  - main: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/app-layout/src/vaadin-app-layout.js#L149-L153>
- **V2** AppLayout behaviour:
  - `overlay` property, read-only and reflected: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/app-layout/src/vaadin-app-layout-mixin.js#L56-L68>
  - `role="dialog"`, `aria-modal`: same file L383–392
  - base styles: content `overflow: auto` L45–46; drawer `visibility: hidden` and translate L110–128; opened L138–142; backdrop L154–160 and L174–177; 800 px media query L187–192: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/app-layout/src/styles/vaadin-app-layout-base-styles.js>
- **V3** MenuBar:
  - `_buttons`, `_hasOverflow` toggling the overflow button's `hidden`: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/menu-bar/src/vaadin-menu-bar-mixin.js#L284-L296>
  - the overflow button created `hidden`: L320–334
  - `has-overflow`: L500–504
  - collapse writes `visibility: hidden; position: absolute`: L642–646
  - 25.3.5 has the same collapse (L641) and no `has-overflow`: <https://unpkg.com/@vaadin/menu-bar@25.3.5/src/vaadin-menu-bar-mixin.js>
- **V4** `OverflowController` users:
  - Grid on `this.$.table`: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/grid/src/vaadin-grid-scroll-mixin.js#L133>
  - Scroller, VirtualList, TabSheet: `packages/{scroller,virtual-list,tabsheet}/src` at the same commit
- **V5** `OverflowController#readState` with `Math.ceil`: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/component-base/src/overflow-controller.js#L108-L131>; Tabs `:host([overflow]) [part='tabs'] { overflow: auto }` and arrow buttons: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/tabs/src/styles/vaadin-tabs-base-styles.js#L41-L49>
- **V6** AppLayout's own test asserts `layout.overlay`: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/app-layout/test/responsive.test.js#L30-L37>
- **V7** MenuBar test helpers: `assertHidden` is `visibility: hidden` + `position: absolute`: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/menu-bar/test/helpers.js#L8-L19>; `has-overflow` test: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/menu-bar/test/overflow.test.js#L58-L67>; the rect-vs-`scrollWidth` zoom comment: `vaadin-menu-bar-mixin.js` above `__getInlineEnd`, at the same commit
- **V8** Overlays use `popover = 'manual'` and `showPopover()`: <https://github.com/vaadin/web-components/blob/4be8af7af417c3877a518a7e1331c570405d95c7/packages/overlay/src/vaadin-overlay-mixin.js#L125-L126>

### Others

- **GA1** Galen `AreaFinder`, one WebDriver call per object: <https://github.com/galenframework/galen/blob/6c7dc1f11d097e6aa49c45d6a77ee688741657a4/galen-core/src/main/java/com/galenframework/page/selenium/AreaFinder.java#L30-L95> (last commit 2019-03-10; `LICENSE-2.0.txt`)
- **DT1** DevTools scroll adorner from `isScrollable`: <https://github.com/ChromeDevTools/devtools-frontend/blob/2049f233b14e9be6f00c30322f880dba32b5302a/front_end/panels/elements/DOMTreeElementWidget.ts#L1671-L1672>; `scrollableFlagUpdated` from CDP: <https://github.com/ChromeDevTools/devtools-frontend/blob/2049f233b14e9be6f00c30322f880dba32b5302a/front_end/core/sdk/DOMModel.ts#L2058-L2063>
- **CT1** Chris Coyier, "Finding/Fixing Unintended Body Overflow" (2014): <https://css-tricks.com/findingfixing-unintended-body-overflow/>
