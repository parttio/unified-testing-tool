# What Browserless can't test, and when a test needs a real browser

Background research for users choosing between Browserless and a browser Testing Tool, and for the Browserless gaps listed in [Appendix B](../spec/appendix-gaps.md). It does not belong to a single ticket.

**Question.** Which parts of a Vaadin Flow application can't be tested on Browserless (Vaadin Browserless Test), and when does a test absolutely need a browser? For example, LitTemplate or LitRenderer content. How much of an application's test suite can Browserless carry, and which use cases does it leave out? For each one: is it impossible without a browser engine, or a gap that could be closed later?

Researched 2026-11-28 against `parttio/unified-testing-tool@91e94fe` (Vaadin 25.3.5, Browserless Test 25.3.x through the Vaadin BOM), `vaadin/browserless-test@e6d33d0` (`main`, 25.5-SNAPSHOT), `vaadin/flow-components@1071941`, `vaadin/docs@b1e668f` and the issue trackers of both test repositories as of that day. Flow pull requests were read through GitHub search on the same day, not from a checkout.

**The two checkouts are on different Vaadin versions.** The Unified API runs on Browserless Test 25.3.x (`pom.xml` L55 `vaadin.version` 25.3.5; `unified-test-rewrite/pom.xml` L25). The Browserless Test facts below are from `main`, which targets 25.5 ([`guidelines/flow-version.md`](https://github.com/vaadin/browserless-test/blob/e6d33d0/guidelines/flow-version.md)). Some features, such as the JavaScript capture and focus simulation, are newer than 25.3. The Unified API can't use them yet. Each such row says so.

Labels: a **fact** has a source (a file path at the commit above, an issue or a URL). **[opinion]** and the sections titled *Inferences* are this note's reasoning. Classes used throughout:

- **(a) testable** on Browserless today;
- **(b) partly testable**: the row says which part;
- **(c) gap**: could be covered later, and the row says how (a tester, a simulator, a hook added upstream in Flow or flow-components);
- **(d) impossible without a browser engine**, and the row says why.

## Answer in short

1. **Browserless tests what the server knows, and nothing that only exists in the browser.** It walks the server-side component tree, fires events the way the Flow client would send them, and plays the client for some browser behaviour (focus, shortcuts, uploads, reload, geolocation, modality). Nothing in it renders HTML, applies CSS, lays out a page or runs JavaScript (F1–F4). Vaadin's own guide says it plainly: Browserless tests "prove behavior, not rendering". They can't show that the theme and CSS look right, that a component's client-side JavaScript works, that the layout works at different screen sizes, or that add-ons render (F5).
2. **You need a real browser when the thing under test is rendered or runs in the browser**:
   - CSS, the theme, visibility set by CSS, sizes, overflow, responsive layout and screenshots;
   - the DOM and event handlers of a LitTemplate or `LitRenderer` that no `@Id` or server-side component maps;
   - the behaviour of a web component that lives in its JavaScript: date parsing in the user's locale, Crud's Save/Cancel/Delete, overlay positioning, keyboard navigation inside a component, Tab focus order;
   - client-side add-ons (`@JsModule`) and the result of your own `executeJs`;
   - React/Hilla views, cross-browser differences, and the real HTTP, servlet and security filter chain (CSRF, a form-post login, real Push transport, service workers).
   These are class (d). The full list is in [the table](#capabilities-and-their-class).
3. **Much of what looks browser-only is a gap rather than impossible: class (c).** Browserless Test closes these one by one, by simulating what the server observes, usually behind a hook added in Flow first. Geolocation shipped this way. Clipboard is in open pull requests (F40). Examples still open:
   - typing unparsable or locale-formatted text into a date picker;
   - generic drag and drop;
   - the browser's back button;
   - session expiry;
   - `ExtendedClientDetails` (time zone, screen);
   - a file served by `Download.onClick`;
   - Grid editor, row details on click and the text of `LitRenderer`/`TextRenderer` cells;
   - testers for Crud, RichTextEditor, CustomField, Map and other components.
   Each comes with a ticket in [the table](#capabilities-and-their-class).
4. **The JavaScript the server sends is now checkable, not runnable.** On Browserless Test `main`, a test can capture every `executeJs`/`callJsFunction` call and its arguments, answer a call's return value, and call a return channel as the browser would (F12, F13). A test of *what the application asks the browser to do* is (a). A test of *what that JavaScript then does in the page* stays (d). The Unified API is on 25.3, so it doesn't have this yet.
5. **Coverage.** Vaadin says Browserless "can cover most of your application, including its use cases from start to finish" (F5). This repo's migrations measured how much:
   - 71 of 75 UI tests of a Dramafinder suite moved to Browserless (F50);
   - only 4 of 17 tests of Bakery, which is built from LitTemplates and Crud (F51);
   - about 90 % of this repo's own demo tests (358 of 399; F52).

   **[opinion]** For a typical Flow application built from server-side components, Browserless can carry most of the use-case tests (business rules, validation, navigation, access control, multi-user flows), in the range of 80–95 %. A browser layer remains: smoke, rendering, client-side-component and responsive tests. Templates, client-rendered add-ons and React views move that share down sharply.
6. **The Unified API already works around many gaps** by running the same test on Browserless and in a browser. `@RequiresBrowser` (and `@Viewport`, which implies it) skips a browser-only test on Browserless. CSS, visible text, screenshot and layout checks fail on Browserless with a message that names `@RequiresBrowser`. The adapter simulates typing into date pickers, DOM clicks on plain elements and the browser's accessible-name computation. The comparison between Testing Tools catches tests that pass only on Browserless (F53–F60). Running both tools is what makes the Browserless speed safe to rely on (F51).

**Decision guide: you need a browser when the test…**

| …checks or depends on | because | Example |
|---|---|---|
| how something looks, or whether CSS hides it | no CSS engine (d) | a field hidden with `display:none` from a class name; theme variants; truncation |
| sizes, positions, scrolling, the viewport | no layout engine (d) | AppLayout drawer as overlay, MenuBar overflow, MasterDetailLayout overlay, `isInViewport` |
| markup that no server component maps | it exists only in the browser DOM (d) | id-less template HTML, `LitRenderer` buttons, `Html`/`innerHTML` children, Markdown output |
| what a web component's JavaScript does | no JavaScript engine (d), until someone simulates it (c) | date parsing in the user's format, Crud's Save button, combo box keyboard navigation |
| the result of the application's own `executeJs`, or an add-on's client code | the script is captured, never run (d) | a chart library, a map, a canvas, a JS-driven scroll |
| real HTTP, the servlet container, the security filter chain | no container, no requests (d, by design) | Spring Security form login, CSRF, redirects, headers, cookies over HTTP, WebSocket reconnect |
| a React/Hilla view, or client-side routing | runs only in the browser (d) | a Hilla view; a React layout around Flow views |
| differences between browsers | there is no browser (d) | Safari-only bugs, IME input, mobile touch |
| a browser API that Browserless doesn't simulate yet | gap (c) | back button, clipboard before the simulator lands, session timeout |

Everything else (component state, values, events from the user, validation, navigation and route parameters, access control, dialogs, notifications, grids and their data, uploads and anchor downloads, shortcuts, focus, several users and windows, signals, background tasks via `UI.access`) is (a) or (b).

---

## How Browserless works, and why that sets the limits

### Verified facts

| # | Fact | Source |
|---|---|---|
| F1 | "There is no servlet container and no browser." `MockVaadin.setup(…)` builds the objects Flow expects (a mocked `VaadinServletService`, `HttpSession`, request, response, `UI`) and installs them as thread-locals. | [`guidelines/architecture.md` L6–L28](https://github.com/vaadin/browserless-test/blob/e6d33d0/guidelines/architecture.md) |
| F2 | "The browser is what normally makes Flow flush the state tree, run `UI.access()` tasks and deliver `beforeClientResponse` callbacks. Browserless Test does it explicitly", at round trips, before every component lookup and at the end of tester actions. | `guidelines/architecture.md` L50–L84 |
| F3 | `ComponentQuery` "walks the tree from a root … and filters by type, id, test id, class name, attribute, text, label, value, theme or an arbitrary predicate". It walks the **server-side component tree**. A component another component renders per item, and an overlay's content while it is closed, are not in that tree. | `guidelines/architecture.md` L86–L94; [`README.md` L407–L415](https://github.com/vaadin/browserless-test/blob/e6d33d0/README.md#what-find-can-and-cannot-see) |
| F4 | "A tester interaction has to be indistinguishable from the real one": events must report `isFromClient() == true`. Testers model user actions, and a tester refuses a value only when "a user sitting in front of the component could [not] hand the field that value at all" (slider range and step; text `maxLength` and `allowedCharPattern`). `pattern`, `minLength`, required, `min`/`max` and `step` are validation only: the value is committed and leaves the field invalid. | [`guidelines/testers.md` L5–L40, L66–L100](https://github.com/vaadin/browserless-test/blob/e6d33d0/guidelines/testers.md) |
| F5 | "Browserless tests prove behavior, not rendering. They can't show that: the theme and the CSS look right, the client-side JavaScript of a component works, the layout works in real browsers and at different screen sizes, or add-ons and custom components render." Suggested browser smoke tests: the main views open, custom components and add-ons render, no JavaScript errors in the console, the login flow works, each kind of gesture reaches the server. "Service and browserless tests … can cover most of your application, including its use cases from start to finish." | [vaadin/docs `articles/building-apps/testing/index.adoc` L45, L98–L118 @ b1e668f](https://github.com/vaadin/docs/blob/b1e668f94093aa7dec1811859e4513f7cf39042c/articles/building-apps/testing/index.adoc#L98-L118) |
| F6 | "End-to-end testing enables you to test the client-side functionality … such as JavaScript code or the parts of the UI that are implemented using the Polymer or Lit libraries … end-to-end testing is necessary for cross-browser testing … These kinds of tests are impossible with browserless testing." | [vaadin/docs `articles/flow/testing/index.adoc` L65–L69](https://github.com/vaadin/docs/blob/b1e668f94093aa7dec1811859e4513f7cf39042c/articles/flow/testing/index.adoc#L65-L69) |
| F7 | Vaadin's per-feature table: signals, shared signals, geolocation (with the simulator), sliders, Message List attachments and AI chats (with a scripted LLM provider) are tested on Browserless. Master-Detail Layout needs end-to-end tests "for whether the layout shows the detail area as an overlay, which depends on the space in the browser". Clipboard needs end-to-end tests: "browserless tests have no clipboard simulator". | `articles/building-apps/testing/index.adoc` L52–L94 |
| F8 | "Browserless tests can't test React views, because React views run in the browser." | `articles/building-apps/testing/index.adoc` L47 |
| F9 | Browserless Test models some browser behaviour on purpose, as "the framework's contract with its users": `isFromClient()`, external navigation (`Page.setLocation()`/`Page.open()` are *recorded*, not followed), shortcuts, modality and inertness. | `guidelines/architecture.md` L170–L184 |
| F10 | The mocks fail loudly for what they don't model. `MockResponse` has no body (`getOutputStream()`, `getWriter()` and `sendRedirect()` throw `UnsupportedOperationException`). `MockContext` throws for "servlet, filter and listener registration, request dispatch, session tracking configuration". `MockHttpSession` "is never expired on its own". | [`shared/…/mocks/MockResponse.java` L33–L46](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/mocks/MockResponse.java), `MockContext.java` L60–L76, `MockHttpSession.java` L43–L46 |
| F11 | The mock UI's `ExtendedClientDetails` carries only a window name: "the mock does not fabricate a screen or viewport geometry it has no way to know". vaadin/browserless-test#301 (open) lists the placeholder values: `getTimeZoneId()` is `null`, screen and window sizes `-1`/`0`, `isTouchDevice()` `false`. | [`shared/…/internal/MockVaadin.java` L706–L724](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/internal/MockVaadin.java); [vaadin/browserless-test#301](https://github.com/vaadin/browserless-test/issues/301) |

### Inferences

- **[opinion]** The line between (c) and (d) is "does the server observe it?". Whatever the browser eventually tells the server (an event, a property, a return value, a request) can be simulated by a tester or a simulator. That puts it in (c), and often already in (a). Whatever the browser does *without* telling the server (rendering, layout, CSS, a component's internal JS state, a script's side effects in the page) can only be reproduced by re-implementing the browser, which is (d).
- **[opinion]** A second line is "who owns the behaviour?". When Flow or flow-components owns it in Java (a value change listener, a fallback parser, `NavigationAccessControl`), Browserless runs the real code. When the web component owns it in TypeScript (`vaadin-date-picker`'s parser, `vaadin-crud`'s buttons), Browserless can only copy it into a tester. The testers guideline asks to avoid such copies, because they drift ([`guidelines/flow-version.md`](https://github.com/vaadin/browserless-test/blob/e6d33d0/guidelines/flow-version.md), "copying component logic into a tester … drifts").

---

## Capabilities and their class

The ids in the Source column refer to the facts in the sections below.

| Capability | Class | What Browserless can and can't do | Source | Unified API |
|---|---|---|---|---|
| **Client-side rendering** | | | | |
| LitTemplate `@Id`-mapped components | (b) | Reachable as server components. A lookup walks only `getChildren()` of a template, not its `@Id` virtual children. Getters return only what the server set: text, attributes with bindings and template-only children are empty. | F14, F15, F16 | The adapter walks `@Id` children of a Template the test enters (`findTemplate`); `withId` is the portable lookup (ADR 0012) |
| LitTemplate HTML with no `@Id` (template-only DOM) | (d) | Does not exist on the server, so Browserless has no signal to fail on. | F15, F16 | No Unified call pierces the shadow root (ADR 0012 amendment, #617); Escape Hatch in a browser |
| PolymerTemplate | (d) for its content | A failed lookup warns that Browserless "is not able to look up components from inside of PolymerTemplate". | F17 | — |
| `ComponentRenderer` in Grid, VirtualList | (a) | `getCellComponent` returns the component the grid rendered, scrolling the row into the server-side viewport first. A page-wide `find()` doesn't see it (vaadin/browserless-test#351 open). The tester reaches it through reflection, because Flow has no API for it. | F18, F19 | Adapter reaches cells through internal `RenderedComponentSupport` (#99) |
| `LitRenderer` properties and functions | (b) | `getLitRendererPropertyValue` reads a property. `invokeLitRendererFunction` calls a `withFunction` callback directly, which is not a user action. The markup and its `@click` handlers don't exist. | F20, F21 | Lookup in such a cell fails naming `@RequiresBrowser`; functions only via the Escape Hatch (ADR 0015) |
| `LitRenderer` markup (buttons, cards in a cell) | (d) | Rendered by Lit in the browser. | F20, F22 | `@RequiresBrowser`, tag Wrapper in a browser (ADR 0015) |
| Cell text of `LitRenderer`, `TextRenderer`, `LocalDateRenderer`, `NumberRenderer`, custom renderers | (c) | `GridTester.getCellText` returns `null` for anything but `ComponentRenderer` and `ColumnPathRenderer`. Browserless Test already has an internal approximation (`Renderers._getPresentationValue`) for basic, text and Lit renderers. | F23, F24 | #692 (open): asks to fail with a message instead of `null` |
| ComboBox items, suggestions, filtering | (b) | Suggestions come from the server-side data communicator through reflection. With 50 items or fewer the browser filters by itself (`_clientSideFilter`); the tester applies the server filter instead. | F25, F26 | — |
| Markdown, `Html`, `innerHTML` content | (b) | The source or text is readable. The rendered elements are not components. | F27; appendix-gaps #691 | Reads the text; can't find elements inside (#691) |
| **JavaScript** | | | | |
| `executeJs`/`callJsFunction`/`Page.executeJs`: that it was called, with which arguments | (a) on `main`; (c) on 25.3 | `ExecutedJavaScript.capture()` records every call at each client boundary. | F12 | Not available on 25.3 |
| Their return values (`then(…)`, `toCompletableFuture`) | (a) on `main`, by hand | `Execution.resolve(…)`/`reject(…)` answer as the browser would. A `JavaScriptHandler` answers automatically for a whole test environment. | F12, F13 | Not available on 25.3 |
| What the script *does* in the page | (d) | "JavaScript the application schedules itself … is not interpreted; it is only sent." | F12 | `@RequiresBrowser` |
| `@ClientCallable` invoked from JS; return channels | (b) | The JavaScript that would call it never runs. A test can call the Java method directly, and on `main` it can call a `ReturnChannelRegistration` "as the JavaScript would call it". | F12 | — |
| DOM listener filters and event data (`setFilter`, `addEventData`, `preventDefault`) | (b) | `ClientDomEvent` evaluates a JavaScript subset (property reads, literals, `!`, `&&`, `||`, comparisons). A declared function runs only if Browserless Test has a Java simulation of it (shortcut and click filters today). Many testers still fire component events without the filters (vaadin/browserless-test#325 open). | F28, F29 | — |
| Web component behaviour in its JS (date parsing in the i18n format, Crud buttons, i18n defaults, keyboard navigation inside a component) | (d) as such; (c) per component | Needs a tester that reproduces the server-observable outcome. Examples below. | F30–F33 | Adapter types into date pickers (ISO only); Crud is out of scope |
| Text field `allowedCharPattern`, `maxLength` | (a) | The tester refuses what the browser physically prevents. | F4 | — |
| `pattern`, `minLength`, required, `min`/`max`/`step` validation | (a) | Committed as in the browser, and the field is left invalid. | F4 | — |
| Typing key by key, `ValueChangeMode` (`LAZY`, `TIMEOUT`), key-up listeners on a field | (c) | Testers set the whole value at once. vaadin/browserless-test#265 (key-up listener) is open. | F34 | — |
| `@JsModule`/`@NpmPackage` add-ons, custom web components | (d) for the client part; (a) for the server API | Read the Java state. Set values as the user with `setValueAsUser` in a small tester or locator. | F35 | Client-rendered add-on: `@RequiresBrowser` (ADR 0012) |
| **Layout and visuals** | | | | |
| CSS, theme, Lumo/Aura variants, computed styles | (d) | No CSS engine. The theme attribute and inline style can be read. | F5 | `getCssValue`/`hasCssValue` fail: "Browserless has no CSS; use @RequiresBrowser" |
| Visibility through CSS | (d) | Visible means the server-side `visible` flag of the whole parent chain. A field hidden by CSS stays usable, which gives false passes. | F36, F51 | Comparison between tools flags Browserless-only passes |
| Rendered text (`innerText`) | (d) | `textContent` only. | — | `getVisibleText()` fails naming `@RequiresBrowser` |
| Sizes, overflow, clipping, overlap, responsive behaviour, viewport | (d) | No layout. `ExtendedClientDetails` has no geometry. | F11 | `@Viewport`, `setViewport`, Layout Findings, `isInViewport`, `isNotClipped`, overlay drawer and MenuBar overflow all need `@RequiresBrowser` (ADR 0011) |
| A view that reads screen size, touch, time zone from `ExtendedClientDetails` | (c) | Placeholders today; a test can't describe the browser (vaadin/browserless-test#301 open). A `JavaScriptHandler` can answer the time zone when it is read with `executeJs` (`main`). | F11, F13 | — |
| Screenshots, visual regression | (d) | — | — | `screenshot()` throws "Browserless has no screenshots"; the report shows the component tree instead |
| Scrolling; data paging driven by the viewport | (b) | Grid sets its own viewport range on the server (page 0, then about 40 rows around a `scrollToIndex`), not from a real viewport height. Paging logic runs; how many rows a real browser fetches doesn't. | F37 | — |
| Focus order with Tab, focus rings | (c) for Tab order; (d) for focus rings | Focus moves on tester interactions and on `Focusable.focus()`/`blur()` (25.4+). Tab traversal doesn't happen. | F38 | Troubleshooting: "client-side behaviour (e.g. typing text, Tab moving focus) doesn't happen" |
| **Browser APIs and events** | | | | |
| Keyboard shortcuts | (a), with one gap | Fires key events with modifiers; the shortcut filter is simulated. Scope of a non-modal Dialog or Popover is ignored (vaadin/browserless-test#331 open). | F9, F29 | `pressKey` bubbles from the target to the UI |
| Generic drag and drop (`DragSource`/`DropTarget`, Grid row DnD) | (c) | "Browserless tests don't simulate gestures such as drag and drop." Vaadin recommends routing the gesture through a public Java method that tests call. | F39 | Smoke test the gesture in a browser |
| Drag-like gestures with a tester | (a) | `DashboardTester.moveWidget/resizeWidget`, `SplitLayoutTester` (splitter drag end), sliders, `UploadDropZoneTester.drop`. | F39 | — |
| File upload | (a) | `UploadTester` checks `maxFiles`, `maxFileSize` and accepted types "the way the web component does" and fires `FileRejectedEvent`. | F41 | `UnifiedUpload.upload(..)` |
| Download through `Anchor` | (a) | `AnchorTester.download(OutputStream)` streams the handler's bytes. | F42 | Link out of the app fails naming `@RequiresBrowser` |
| Download through `Download.onClick` on a button | (c) | No tester reaches it (vaadin/browserless-test#383 open). | F42 | — |
| Clipboard | (c), in progress | No simulator yet. Seams in open Flow pull requests (vaadin/flow#24656, #25045); simulators in open vaadin/browserless-test pull requests (#146, #147, #338). | F7, F40 | — |
| Geolocation | (a) | `GeolocationSimulator`: permission and sensor reading. | F43 | — |
| Push, `UI.access` from background threads | (a) for the application logic; (d) for the transport | Queued tasks run at each round trip. WebSocket, long polling, reconnect and proxies are not involved. | F2, F44 | `waitUntil` flushes `UI.access` tasks and signals between polls |
| Browser back/forward, history | (c) | No API for back/forward. Flow handles `popstate` on the server, so a simulation is possible. | F45 | — |
| Page reload (F5), `@PreserveOnRefresh` | (a) | `reload()` recreates the UI; the window name is kept. | F44 | `page.reload()` |
| `Page.setLocation`, `Page.open`, external links | (b) | Recorded, not followed. | F9 | Fails naming `@RequiresBrowser` (#554, #574) |
| Session expiry, heartbeat, idle UI cleanup | (c) | The session is never expired on its own; `invalidate()` behaves as in a container. | F10 | — |
| Several tabs, users, sessions | (a) | Users and windows share one application context; session locks are handed over as in a server. | F46 | `openWindow`, `openAsUser`, `openNewSession` |
| Notification auto-close | (a) | `NotificationTester.autoClose()` simulates the timeout; there is no real timer. | F47 | — |
| Dialog, Popover, ConfirmDialog, ContextMenu overlays | (a) for behaviour; (d) for positioning | Open/close, Escape, click outside, hover/focus on target, modality and inertness. Overlay position and stacking are layout. | F9, F47 | `UnifiedOverlayDialog.close()` presses Escape |
| Accessibility tree, ARIA computed names, roles | (b) | The Unified adapter computes the browser's accessible name from server-side attributes. A name that only shadow DOM renders is lost. Roles, focusability and the real accessibility tree need a browser. | F48 | ADR 0016 |
| Page visibility, wake lock and similar `Page` facades | (b) | Their `@JsDefinition` calls are captured and can be answered (`WakeLockJs::request` in the README) on `main`. No state simulator. | F12 | — |
| **Integration** | | | | |
| Hilla/React views; client-side routing | (d) | — | F8 | — |
| Flow views inside a React layout | (d) for the React part | **[opinion]** The Flow view is testable alone; the React shell isn't. | F8 | — |
| Service workers, PWA, offline | (d) | No browser and no HTTP. | F10 | — |
| Spring Security: view access control | (a) | `NavigationAccessControl` runs on navigation; users are set with `@WithMockUser` or per user context. | F49 | `@WithMockUser`, `openAsUser` |
| Spring Security: form-post login, CSRF, filter chain, redirects, headers | (d) on Browserless | No filters, no request dispatch, no response body. | F10, F49 | `UnifiedLogin.login(..)` checks the form `action` and fails naming `@RequiresBrowser` (#582) |
| Real servlet container behaviour, deployment, static resources over HTTP | (d), by design | Resources resolve from the classpath as a war would, but nothing is served. | F10 | `@UnifiedSpringBootTest` starts the app for browser runs |
| Cross-browser differences | (d) | — | F6 | Playwright `chromium`/`firefox`/`webkit`; Selenium Chrome/Firefox |
| Components without a tester (Crud, RichTextEditor, CustomField, Map, Board, Spreadsheet, PdfViewer, ProgressBar, Badge, Icons, FormLayout) | (b)/(c) | Readable through Java. User actions need a tester (vaadin/browserless-test#193, #194, #195, #371 open). Map, Spreadsheet and RichTextEditor do most of their work in JS. | F31, F32 | Crud: `@RequiresBrowser` (`.out-of-scope/crud-wrapper.md`) |
| Grid editor, row details on click, header-click sort, context menu by right-click | (c) | vaadin/browserless-test#262, #29, #302 open. `GridTester.sortByColumn` exists. | F24 | Row details: toggling works, searching inside throws |

---

## 1. Client-side rendering: templates and renderers

### Verified facts

| # | Fact | Source |
|---|---|---|
| F14 | Lookups leave out a template's `@Id` components: for a Lit or Polymer template, the default traversal returns `component.getChildren()` only. Virtual children "are only 'shallow shells' of components constructed server-side - almost none of their properties are transferred to the server-side … let's leave the virtual children out for now". | [`shared/…/internal/TestingLifecycleHook.java` L102–L117](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/internal/TestingLifecycleHook.java) |
| F15 | Flow's template limitations: "Server-side `Component` or `Element` read methods aren't always in sync with the client side … Getter methods return values that are set from the server side only." An `@Id` `Div` has no server-side child even when the template has an `<a>` in it, and `getText()` of an `@Id("header")` div returns an empty string. Attributes with bindings (`${…}`) aren't parsed on the server, and "only attributes are parsed". | [vaadin/docs `articles/flow/component-internals/templates/limitations.adoc` L12–L16, L62–L76](https://github.com/vaadin/docs/blob/b1e668f94093aa7dec1811859e4513f7cf39042c/articles/flow/component-internals/templates/limitations.adoc) |
| F16 | Unified API: "Only what the server knows is findable inside a LitTemplate, on every Testing Tool: `@Id`-bound components and what is under them … Text, labels and attributes written in the template's HTML are empty on the server." A browser-only call that pierces a Template's shadow root was declined: "Browserless can't match it, and … it has no signal to fail on, so an empty result would let `doesNotExist()` pass." | [`docs/adr/0012-lookups-enter-templates-explicitly.md`](../adr/0012-lookups-enter-templates-explicitly.md); [#347](https://github.com/parttio/unified-testing-tool/issues/347), [#617](https://github.com/parttio/unified-testing-tool/issues/617) |
| F17 | A failed lookup under a `PolymerTemplate` adds: "Warning: Browserless Testing is not able to look up components from inside of PolymerTemplate." | [`shared/…/internal/Locator.java` L192–L208](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/internal/Locator.java) |
| F18 | `GridTester.getCellComponent` returns "the very component the browser shows". When "the client has not asked for the row yet", it calls `scrollToIndex(row)` and round-trips, "as a user scrolling down to the row would". | [`shared/…/grid/GridTester.java` L336–L350, L479–L507](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/flow/component/grid/GridTester.java) |
| F19 | "Flow exposes no API for it": `RenderedComponentSupport` reads `AbstractComponentDataGenerator.renderedComponents` reflectively. "The accessor belongs upstream, on `Grid.Column` in `vaadin/flow-components` or on `AbstractComponentDataGenerator` in `vaadin/flow`." | [`shared/…/internal/RenderedComponentSupport.java`](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/internal/RenderedComponentSupport.java) |
| F20 | `LitRenderer` "uses a Lit-based template literal to render given model objects in the components that support the JS renderer functions API. Mainly … `Grid`, `ComboBox` and `VirtualList`". Functions are registered with `withFunction(name, handler)`. Lit's event listeners are template expressions (`@click=${…}`) that run in the browser. | [flow-components `vaadin-renderer-flow/…/LitRenderer.java` L52–L67, L456 @ 1071941](https://github.com/vaadin/flow-components/blob/1071941ca3cb6d57e7ed5cbfd809780e8fa3922f/vaadin-renderer-flow-parent/vaadin-renderer-flow/src/main/java/com/vaadin/flow/data/renderer/LitRenderer.java); [lit.dev, Event listener expressions](https://lit.dev/docs/templates/expressions/#event-listener-expressions) |
| F21 | `LitRendererTestUtil` offers `getProperties`, `getPropertyValue`, `getFunctionNames` and `invokeFunction` for a `LitRenderer`. `GridTester` and `VirtualListTester` expose them as `getLitRendererPropertyValue` and `invokeLitRendererFunction`, "for `LitRenderer` columns, which have no server-side component at all". | [`shared/…/LitRendererTestUtil.java` L54–L198](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/LitRendererTestUtil.java); `README.md` L448–L449 |
| F22 | Unified API: renderer markup is found by tag Wrappers in a browser. On Browserless, a lookup in a cell whose renderer isn't a component renderer fails, rather than returning empty. "Running a renderer function for a row is not a Unified call. It isn't a user action, and a browser runs it only through the template's own event handler." Error text: "The cell at index … is drawn by a LitRenderer, whose markup exists only in a browser, so Browserless can't search inside it." | [`docs/adr/0015-…`](../adr/0015-renderer-markup-is-found-by-tag-wrappers-row-click-avoids-it.md); `unified-test-browserless/…/BrowserlessGridAccess.java` L203–L206 |
| F23 | `GridTester.getCellText` handles `ComponentRenderer` (recursive text of the rendered component) and `ColumnPathRenderer`, and "return null" for any other renderer. | `GridTester.java` L323–L334 |
| F24 | Browserless Test has an internal `Renderers._getPresentationValue` that formats `BasicRenderer`, `TextRenderer`, `ComponentRenderer` and `LitRenderer` output "as close as possible to the client-side output". For a `LitRenderer` it substitutes `${item.key}` with the value providers' output and takes the text with Jsoup, so expressions beyond plain property reads aren't evaluated. Open tickets: Grid editor (vaadin/browserless-test#262), row details on click (#29), right-click context menu (#302), row lookup by text (#300). `GridTester` has `sortByColumn` and `getSortDirection` at this commit. | [`shared/…/internal/Renderers.java` L76–L120, L160–L190](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/internal/Renderers.java); [#262](https://github.com/vaadin/browserless-test/issues/262), [#29](https://github.com/vaadin/browserless-test/issues/29), [#302](https://github.com/vaadin/browserless-test/issues/302), [#300](https://github.com/vaadin/browserless-test/issues/300) |
| F25 | `ComboBoxTester.setFilter` calls the data controller's private `filterSlot` and `getSuggestionItems` calls the private `DataCommunicator.fetchFromProvider`, both by reflection. | [`shared/…/combobox/ComboBoxTester.java` L64–L79, L181–L200](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/flow/component/combobox/ComboBoxTester.java) |
| F26 | ComboBox filters in the browser (`_clientSideFilter`) when server-side filtering isn't forced and the item count is no larger than the page size. | [flow-components `ComboBoxDataController.java` L609–L620](https://github.com/vaadin/flow-components/blob/1071941ca3cb6d57e7ed5cbfd809780e8fa3922f/vaadin-combo-box-flow-parent/vaadin-combo-box-flow/src/main/java/com/vaadin/flow/component/combobox/ComboBoxDataController.java#L609-L620) |
| F27 | `Markdown` is `@Tag("vaadin-markdown")` with `@JsModule("@vaadin/markdown/src/vaadin-markdown.js")`: the server holds the Markdown source and the web component renders the HTML. `MarkdownTester` has `getContent()` only. | [flow-components `Markdown.java` L31–L39](https://github.com/vaadin/flow-components/blob/1071941ca3cb6d57e7ed5cbfd809780e8fa3922f/vaadin-markdown-flow-parent/vaadin-markdown-flow/src/main/java/com/vaadin/flow/component/markdown/Markdown.java); `shared/…/markdown/MarkdownTester.java` |

### Inferences

- **[opinion]** `@Id`-mapped template components are (b), not (a): the lookup works only after the test enters the Template (Unified) or holds the `@Id` field (plain Browserless), and filters on text or labels written in the HTML can never match (F15). Id-less template HTML is (d). Flow doesn't send it to the server, and the server can't parse Lit's `render()` into a tree, so there is nothing to simulate.
- **[opinion]** Clicking `LitRenderer` markup on Browserless is (c) only in a narrow sense. ADR 0015 considered parsing the template's `@click=${…}` binding and running the bound function. It rejected the idea because that "parses template strings through reflection". The honest classification is (d) for "the markup exists and is clickable" and (a) for "the server function does the right thing", through `invokeLitRendererFunction`.
- **[opinion]** `getCellText` returning `null` for basic and text renderers (F23) is the clearest cheap gap: the formatting code exists in `Renderers` (F24). The Unified report #692 asks first for a failure message instead of a silent `null`.
- **[opinion]** The ComboBox difference (F26) only matters when the application's filter differs from the web component's default client-side match. That is unlikely with the default `ItemFilter`, but possible. Not measured.

---

## 2. JavaScript

### Verified facts

| # | Fact | Source |
|---|---|---|
| F12 | "There is no browser, so JavaScript scheduled with `executeJs`, `callJsFunction` or a declared JavaScript definition does not run. Browserless Test plays the client instead": at every client boundary it takes the pending JavaScript as a response would and "simulates what it can (server-side `Focusable.focus()` and `blur()`, for example)". A capture lists the executions. A call whose result the application waits for "can be answered with `Execution.resolve(Object)` or `Execution.reject(String)`". A `ReturnChannelRegistration` "can be called with `Execution.invokeReturnChannel(Object...)`". JavaScript for an invisible element "is not sent, as in a browser". The README adds: "JavaScript the application schedules itself, such as `getElement().executeJs("this.focus()")`, is not interpreted; it is only sent". Landed on `main` with vaadin/browserless-test#329 (2026-11-21). | [`shared/…/ExecutedJavaScript.java` L45–L103](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/ExecutedJavaScript.java); [`README.md` L753–L757, L759–L860](https://github.com/vaadin/browserless-test/blob/e6d33d0/README.md#javascript-sent-to-the-browser) |
| F13 | A `JavaScriptHandler` "plays the browser for JavaScript that an application sends and that Browserless Test does not simulate on its own, such as reading a value that only the browser knows". It is registered per test environment, and every UI gets its own instance. The Javadoc example answers `resolvedOptions().timeZone` with `"Europe/Helsinki"`. | [`shared/…/JavaScriptHandler.java`](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/JavaScriptHandler.java) |
| F28 | `ClientDomEvent` evaluates the expressions the server registered for a DOM event (event data, filters, `preventDefault()`). "Only the subset of JavaScript that such expressions use is supported: property reads and the `preventDefault()`, `stopPropagation()` and `getModifierState()` calls on `event`, literals, `!`, `&&`, `||`, comparisons and parentheses." Outside that subset, a value is treated as undefined. A filter declared as a function without a Java simulation "does not match". Simulations exist for `ShortcutFilterJs` and `ClickFiltersJs`. | [`shared/…/internal/ClientDomEvent.java` L43–L82](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/browserless/internal/ClientDomEvent.java) |
| F29 | Flow can now declare JavaScript as Java interfaces (`@JsDefinition`/`@JsExpression`), and "the build collects that JavaScript into the frontend bundle" (vaadin/flow#25749, merged). Browserless Test runs such declared filters through `DeclaredFunctionSimulation`, a Java re-implementation per function. Open: `Clickable.click()` and several testers fire a `ClickEvent` through `ComponentUtil.fireEvent`, which "skips the DOM filters a browser click would go through" (vaadin/browserless-test#325). | [vaadin/flow#25749](https://github.com/vaadin/flow/pull/25749); `shared/…/internal/DeclaredFunctionSimulation.java`; [vaadin/browserless-test#325](https://github.com/vaadin/browserless-test/issues/325) |
| F30 | `DatePickerTester.setValue(LocalDate)` sets a date, not typed text. Parsing typed text with the i18n format happens in the web component. The Java side listens for `unparsable-change` and synchronizes `_inputElementValue`. A server-side `setFallbackParser` handles input "that cannot be parsed using the i18n date formats". vaadin/browserless-test#348 asks for `typeText`. | [`shared/…/datepicker/DatePickerTester.java` L57](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/flow/component/datepicker/DatePickerTester.java); [flow-components `DatePicker.java` L297–L306, L1082–L1125](https://github.com/vaadin/flow-components/blob/1071941ca3cb6d57e7ed5cbfd809780e8fa3922f/vaadin-date-picker-flow-parent/vaadin-date-picker-flow/src/main/java/com/vaadin/flow/component/datepicker/DatePicker.java#L297-L306); [#348](https://github.com/vaadin/browserless-test/issues/348) |
| F31 | Crud: "Its Save, Cancel, Delete and Discard buttons get their text from client-side i18n. On Browserless they have no name … The `vaadin-crud` web component handles Save, Cancel and Delete on the client … a click on one of these buttons does nothing: it raises no error, but the Crud's save, cancel and delete listeners never run. The Discard confirmation … is client-side too." A `CrudTester` is vaadin/browserless-test#195 (open). | [`.out-of-scope/crud-wrapper.md`](../../.out-of-scope/crud-wrapper.md); [#195](https://github.com/vaadin/browserless-test/issues/195) |
| F32 | flow-components at `1071941` has 54 component modules. Browserless Test has testers for most. No tester exists for: app layout (its `DrawerToggle` is a button), badge, board, crud, custom field, field highlighter, form layout, icons, map, PDF viewer, progress bar, rich text editor, spreadsheet. Open tickets: RichTextEditor (#193), CustomField (#194), Crud (#195), Media and PdfViewer (#371). | `git ls-tree` of flow-components `1071941`; `find shared -name '*Tester.java'` at `e6d33d0`; [vaadin/browserless-test issues](https://github.com/vaadin/browserless-test/issues?q=is%3Aissue+is%3Aopen+tester) |
| F33 | The Unified adapter types into a date picker by setting `_inputElementValue` and firing `unparsable-change` through Browserless Test internals. It parses **ISO dates only**: "ISO: the only date text `typeText` parses". `DateTimePicker` halves get the same treatment. | `unified-test-browserless/…/BrowserlessCoreSetAccess.java` L268–L300; [#100](https://github.com/parttio/unified-testing-tool/issues/100) |
| F34 | `TextFieldTester` has `setValue`, `clear` and `clickClearButton` only: no keystrokes, no `ValueChangeMode` timing. A key-up listener on a text field is vaadin/browserless-test#265 (open). | `shared/…/textfield/TextFieldTester.java`; [#265](https://github.com/vaadin/browserless-test/issues/265) |
| F35 | For an add-on without a tester, "a test can read the component's state through its Java API". Setting a value through it isn't acting as the user (`isFromClient()` is false), so the docs show a small locator that calls the protected `setValueAsUser`. The example's Javadoc says: "Doesn't test the map itself." | [vaadin/docs `articles/building-apps/testing/browserless/test-custom-components.adoc`, "Set a Value as the User Would"](https://github.com/vaadin/docs/blob/b1e668f94093aa7dec1811859e4513f7cf39042c/articles/building-apps/testing/browserless/test-custom-components.adoc) |

### Inferences

- **[opinion]** Since #329, what the application *sends* to the browser is (a) on Browserless Test `main`: which function, which arguments, in which order. What the browser *answers* is (a) by hand, or automatic with a `JavaScriptHandler`. What the script *does* is (d). That is the right split: a test can prove that the app asked the chart to zoom, but only a browser shows the zoom.
- **[opinion]** Web component behaviour that lives in TypeScript is (c) only per component, and each one costs a copy of client logic. Locale date parsing is a good example. The tester would need the same format rules as `vaadin-date-picker`. The ISO-only workaround (F33) shows the cheap half: unparsable input and the server-side fallback parser are testable, while correct parsing of `28.11.2026` in a Finnish locale is not.
- **[opinion]** `@JsDefinition` (F29) changes the economics. Once Flow declares a piece of client JavaScript as a typed Java interface, Browserless Test can simulate it by interface, without parsing strings. More of today's (d) "a filter or a call we can't read" becomes (c).

---

## 3. Layout and visuals

### Verified facts

| # | Fact | Source |
|---|---|---|
| F36 | A component is usable when it is "enabled, attached, effectively visible (the whole parent chain is visible), not inert, and — for a `HasValue` — not read-only". Visible here is the server-side flag. | [`guidelines/testers.md` L49–L55](https://github.com/vaadin/browserless-test/blob/e6d33d0/guidelines/testers.md) |
| F37 | Grid sets its viewport range on the server: `setViewportRange(0, getPageSize())` initially. `scrollToIndex` uses "a rough approximation of the viewport size in rows", `viewportSizeEstimate = 40`, before calling the client's `scrollToIndex`. In a browser the client then requests the ranges its real viewport shows. | [flow-components `Grid.java` L1528, L3267, L4168–L4181, L5385–L5415](https://github.com/vaadin/flow-components/blob/1071941ca3cb6d57e7ed5cbfd809780e8fa3922f/vaadin-grid-flow-parent/vaadin-grid-flow/src/main/java/com/vaadin/flow/component/grid/Grid.java#L5385-L5415) |
| F38 | Focus moves on tester clicks and value changes, and on explicit `focus()`/`blur()` (since 25.4). Server-side `Focusable.focus()`/`blur()` are simulated. Some testers (radio button, menu bar, context menu item) don't move focus. Unified's known limitations: "client-side behaviour (e.g. typing text, Tab moving focus) doesn't happen". | [`README.md` L690–L757](https://github.com/vaadin/browserless-test/blob/e6d33d0/README.md); `shared/…/ComponentTester.java` L325–L375; [`docs/guide/troubleshooting.md` L106–L108](../guide/troubleshooting.md#known-limitations-by-testing-tool) |
| F54 | Unified API: `getVisibleText()` and `getCssValue()` "Needs a real browser". On Browserless they throw "Browserless has no rendering, so no visible text; use getText() or @RequiresBrowser" and "Browserless has no CSS; use @RequiresBrowser". `screenshot()` throws "Browserless has no screenshots". `setViewport`, `checkLayout`, `hasNoHorizontalScroll`, `hasNoLayoutFindings`, `isInViewport`, `isNotClipped`, AppLayout's `isOverlay` and MenuBar's overflow checks call `run.requireBrowser(..)`. | `unified-test-api/…/UnifiedPage.java` L32–L66; `UnifiedElement.java` L24–L55; `assertion/UnifiedAssertions.java` L190–L215, L355–L405, L905–L980; `unified-test-browserless/…/BrowserlessElement.java` L102, L181; `BrowserlessSession.java` L545 |
| F55 | `@Viewport` "Implies `RequiresBrowser`". A test runs at an exact viewport on every browser Testing Tool, or is skipped. Layout Findings cover sideways scroll, clipping and overlap, and are off by default. | `unified-test-api/…/Viewport.java` L12–L25; [`docs/adr/0011-…`](../adr/0011-responsive-testing-viewports-layout-findings-viewport-comparison.md) |

### Inferences

- **[opinion]** Everything in this section except paging and focus is (d). The browser computes it from CSS and fonts, and the server never hears of it. The only exception would be a full layout engine in the JVM, which this note doesn't consider realistic.
- **[opinion]** Visibility set by CSS is the most dangerous (d), because it fails *open*. A test acts on a field a user can't see and passes. Project B hit exactly this (F51). It is why the Unified API runs the same test on both tools and compares them.
- **[opinion]** `ExtendedClientDetails` (F11) is (c), not (d). The values are data the browser sends once; a test could state "this user is on a phone in Helsinki", as #301 proposes. That would test the *server's reaction* to a narrow screen, not the layout itself.

---

## 4. Browser APIs and events

### Verified facts

| # | Fact | Source |
|---|---|---|
| F39 | "Browserless tests don't simulate gestures such as drag and drop, drawing on a canvas, or clicking a map, because those happen in the browser." The advice: make "the user's intent … a public Java method, and route every way of doing it through that method: the gesture, and an alternative such as a context menu". Smoke test "each kind of gesture … reaches the server-side code". Testers that simulate gestures where the component defines the server event: `DashboardTester.moveWidget/resizeWidget/removeWidget`, `SplitLayoutTester` (the `splitter-dragend` event), `UploadDropZoneTester.drop(..)`, slider testers. | [vaadin/docs `test-custom-components.adoc` L359–L366](https://github.com/vaadin/docs/blob/b1e668f94093aa7dec1811859e4513f7cf39042c/articles/building-apps/testing/browserless/test-custom-components.adoc#L359-L366); `shared/…/dashboard/DashboardTester.java` L71–L148; `splitlayout/SplitLayoutTester.java` L30–L81; `upload/UploadDropZoneTester.java` L82–L121 |
| F40 | Clipboard: Flow 25 has a `com.vaadin.flow.component.clipboard` API. Browserless seams are open pull requests: vaadin/flow#24656 ("browserless-testing seam (ClipboardClient port)", "modeled 1:1 on the Geolocation seam") and #25045 ("test-support seam for simulating triggers and actions"). Open Browserless Test pull requests #146, #147 and #338 simulate copy, paste and file paste. vaadin/browserless-test#64 sets the non-goal: "replicating browser-side click-trusted-event guarantees in tests". | `flow-server/src/main/java/com/vaadin/flow/component/clipboard/` (GitHub code search, 2026-11-28); [vaadin/flow#24656](https://github.com/vaadin/flow/pull/24656), [#25045](https://github.com/vaadin/flow/pull/25045); [vaadin/browserless-test#64](https://github.com/vaadin/browserless-test/issues/64) |
| F41 | `UploadTester` checks `maxFiles`, `maxFileSize` and the accepted file types before "anything [is sent] to the server", and fires `FileRejectedEvent` "exactly as it would be in a browser". The accepted types are checked "the way the web component does". | [`shared/…/upload/UploadTester.java` L52–L100](https://github.com/vaadin/browserless-test/blob/e6d33d0/shared/src/main/java/com/vaadin/flow/component/upload/UploadTester.java) |
| F42 | `AnchorTester.download(OutputStream)` "Download[s] the stream resource linked by the anchor". The same handler served by 25.4's `Download.onClick(button, handler)` can't be fetched: "`ButtonTester` has nothing that reaches it" (vaadin/browserless-test#383, open). | `shared/…/html/AnchorTester.java` L142–L190; [#383](https://github.com/vaadin/browserless-test/issues/383) |
| F43 | `GeolocationSimulator` "provides a state-based simulation of the browser's geolocation: the test describes the world — the user's permission decision and the sensor's reading". It is installed whenever browserless mocking is active. | [vaadin/docs `articles/flow/browser-apis/geolocation-api.adoc` L253–L297](https://github.com/vaadin/docs/blob/b1e668f94093aa7dec1811859e4513f7cf39042c/articles/flow/browser-apis/geolocation-api.adoc#L253); `shared/…/geolocation/GeolocationSimulator.java` |
| F44 | Page reload: `MockPage` "recreates the UI on `reload()`, to simulate the browser's F5". The window name survives a reload so that `@PreserveOnRefresh` works. Push mode is forced to `AUTOMATIC` "to make sure that `UI.getCurrent().push()` can be called". Background `UI.access` tasks run at round trips. | `shared/…/internal/MockPage.java`; `MockVaadin.java` L710–L724, L754–L756; `guidelines/architecture.md` L56–L66 |
| F45 | `BrowserlessUIContext` offers `navigate`, `reload`, `roundTrip`, `fireShortcut`, `getExternalNavigationURL` and `getOpenedWindows`. There is no back/forward or history call, and no reference to `NavigationTrigger.HISTORY` in `shared/src/main`. | `shared/…/BrowserlessUIContext.java` L145–L735; `grep` at `e6d33d0` |
| F46 | Users and windows map to a shared `VaadinServletService`, a `VaadinSession` and a `UI`. "Only the active window's session is locked, so cross-session access without `UI.access()` fails as in a running application." | `guidelines/architecture.md` L118–L152 |
| F47 | `NotificationTester.autoClose()` "Simulates auto-close of the notification, because of show duration timeout". `DialogTester` has `pressEscape()` and `clickOutside()`. `PopoverTester` has `clickTarget()`, `hoverTarget()`, `focusTarget()`, `pressEscape()` and `clickOutside()`. Modality "generates the client-side change, and an inert node is not usable". | `shared/…/notification/NotificationTester.java` L28–L80; `dialog/DialogTester.java` L58–L126; `popover/PopoverTester.java` L53–L183; `guidelines/architecture.md` L182–L184 |
| F48 | Unified ADR 0016: "Browserless computes the same [accessible name] from the server-side elements, with the ARIA attributes Vaadin's web components render in the browser". "A component named only by what renders in its shadow DOM (a Grid's `setAriaLabel(..)`) has no name." | [`docs/adr/0016-accessible-name-follows-the-browser.md`](../adr/0016-accessible-name-follows-the-browser.md) |

### Inferences

- **[opinion]** Generic drag and drop is (c). Flow's `DragSource`/`DropTarget` keep the drag data on the server and receive `dragstart`/`drop` as DOM events, so a tester could fire both, as `DashboardTester` does for its own gesture. That a user can physically perform the gesture (hit areas, drop feedback) stays (d). Not verified against Flow's DnD source.
- **[opinion]** Back/forward is (c). Flow's router handles history navigation on the server, so `MockPage` could keep a history stack and navigate with the history trigger. No ticket asks for it in either tracker as of 2026-11-28.
- **[opinion]** Push is (a) for what applications get wrong most often: forgetting `UI.access`, and races between users, which the session-lock handover surfaces (F46). It is (d) for transport problems (proxies, WebSocket fallback, reconnect), which need a real server more than a real browser.
- **[opinion]** Accessibility is (b). The Unified adapter can approximate the accessible *name*, and has done so on every tool since ADR 0016. A real accessibility tree (roles, states, what a screen reader announces, contrast, focus visibility) needs a browser engine. Checks like axe are browser-only.

---

## 5. Integration, security and the container

### Verified facts

| # | Fact | Source |
|---|---|---|
| F49 | Spring Security on Browserless: "`NavigationAccessControl` must be registered as a UI `BeforeEnterListener` for view protection to apply." `@WithMockUser`, `@WithAnonymousUser` and `@WithUserDetails` establish authentication before the initial navigation. Unified: `AbstractLoginTester.login(..)` "fires the `LoginEvent` only: a login form with an `action` (a form post, Spring Security's `/login`) is neither posted nor reported". `UnifiedLogin.login(..)` "checks the `action` first and fails asking for `@RequiresBrowser` or `@WithMockUser`". | [vaadin/docs `articles/flow/testing/browserless/spring-security.adoc` L16–L20](https://github.com/vaadin/docs/blob/b1e668f94093aa7dec1811859e4513f7cf39042c/articles/flow/testing/browserless/spring-security.adoc); `articles/flow/testing/browserless/environment-differences.adoc`; [`docs/spec/appendix-gaps.md`](../spec/appendix-gaps.md) (#582) |
| F56 | Unified known limitations on Browserless: `Page.setLocation(..)` "not followed: the UI stays on the view" (#554). A link out of the application, or one with `target="_blank"`, "fails on Browserless, saying the test needs `@RequiresBrowser`". Searching inside Grid row details throws; toggling them works. | [`docs/guide/troubleshooting.md` L102–L115](../guide/troubleshooting.md#known-limitations-by-testing-tool); [`docs/guide/components.md` "Links"](../guide/components.md#links) |

### Inferences

- **[opinion]** The servlet, HTTP and security filter chain (CSRF, form login, redirects, response headers, session cookies) is (d) *for Browserless by design*, not for lack of a browser. A server-only tool (MockMvc, or the app started on a port with an HTTP client) can test it without a browser. The Unified API starts the application with `@UnifiedSpringBootTest` for browser runs ([ADR 0013](../adr/0013-migrated-spring-boot-tests-start-the-app.md)).
- **[opinion]** React/Hilla views, service workers and offline mode are (d) with no prospect of change. Browserless never loads the frontend bundle.

---

## 6. How much Browserless can carry

### Verified facts

| # | Fact | Source |
|---|---|---|
| F50 | Dramafinder migration (project B): "71 of 75 tests also run without a browser, 4× faster per test". Of the 4 that can't: "1 needs CSS, 1 scroll JavaScript, 2 are repository-level and take no page". | [`docs/feedback/2026-11-dramafinder-migration-project-b.md` L23, L121–L131](../feedback/2026-11-dramafinder-migration-project-b.md) |
| F51 | Same project: "Browserless alone isn't enough. It has no CSS, so it can act on fields a user can't see. The date-picker test passed on Browserless and failed on Playwright (G12). Running both tools is what makes the Browserless speed safe to rely on." Also: Browserless's same-route navigation exposed a latent bug that a full page load in a browser hides. | same file L129–L131, L144–L150 |
| F52 | This repo's demo suite at `91e94fe`: 399 test methods (`@Test`, `@ParameterizedTest`, `@UnifiedTest`), of which 41 carry `@RequiresBrowser` or `@Viewport` on the method or class. The suite exercises the tool itself, so it overrepresents browser features. | counted with a script over `unified-test-demo/src/test/java` |
| F53 | Bakery (TestBench migration): "`@RequiresBrowser` on 11 tests, `@RequiresTestingTool(SELENIUM)` on 1." Browserless runs "4 of 17" tests, "though it covers only part of the suite". Bakery's UI is built from 7 Lit templates and Crud. | [`docs/feedback/2026-11-testbench-migration-bakery.md` L40–L75](../feedback/2026-11-testbench-migration-bakery.md) |
| F57 | Plain Playwright migration (project A, 62 end-to-end workflows): one spike ran on Browserless, "2.5 times faster per scenario", but needed "four tool-specific branches and setup the tool has no hook for". Several Browserless failures were silent, such as "a field shows 'Kilo' while the app reads `null`" (#689). | [`docs/feedback/2026-11-plain-playwright-migration-project-a.md` L31, L101–L118](../feedback/2026-11-plain-playwright-migration-project-a.md) |

### Inferences

- **[opinion]** The share depends on how the UI is built, not on what it does:

  | Application style | Browserless share of UI tests (estimate) | What stays in a browser |
  |---|---|---|
  | Server-side Flow components, standard Vaadin components | 80–95 % (F50, F52) | smoke tests, CSS-dependent visibility, responsive checks, login form post |
  | Heavy LitTemplates, Crud, `LitRenderer` cards | 20–40 % (F53) | anything clicking template HTML or renderer markup |
  | Client-rendered add-ons (maps, charts beyond the tester, canvas, rich text) | low for those views | everything inside the add-on |
  | Hilla/React views | 0 % for those views (F8) | all of it; services tested as services |

- **[opinion]** What Browserless carries well is the *behavioural* layer: business rules behind the UI, validation and error messages, navigation and route parameters, access control by role, dialogs, notifications, grid data, selection and sorting, uploads, multi-user and multi-window flows, signals, and exceptions surfacing in the test. It runs these in milliseconds with the real server code, including code paths a browser hides (F51).
- **[opinion]** What it leaves out falls in three groups, and only the first is hard to close:
  1. **rendering**: CSS, layout, client DOM, scripts (d);
  2. **client logic of web components**: (c) per component, at the cost of copying logic;
  3. **browser APIs and gestures that report to the server**: (c), and Browserless Test is closing them in order (focus 25.4, JavaScript capture 25.5, geolocation shipped, clipboard in review).

---

## 7. What the Unified API already does about it

### Verified facts

| # | Fact | Source |
|---|---|---|
| F58 | `@RequiresBrowser`: "On another Testing Tool, a test with `@RequiresBrowser` or `@RequiresTestingTool` is **skipped**, not failed". `page.hasRealBrowser()` guards an optional browser-only step. | [`docs/guide/tool-specific.md` L1–L30](../guide/tool-specific.md); `unified-test-api/…/internal/TestLifecycle.java` L84–L96 |
| F59 | Adapter workarounds listed in Appendix B: it walks `@Id` children of an entered Template (#347); it sets text inputs, number fields and date pickers "as the element's property update from the client" so that `setModelValue(.., fromClient)` overrides run (#689); it fires DOM clicks on plain elements (#70); it simulates reload, `pressKey` and `dispatchEvent` with bubbling (#66); it re-implements filters on the whole text and includes hidden components (#102). | [`docs/spec/appendix-gaps.md`](../spec/appendix-gaps.md); `unified-test-browserless/…/BrowserlessClientValues.java` L1–L80 |
| F60 | The comparison between Testing Tools "names tests that end differently, and the step where the two runs part". In project B it flagged the Browserless-only pass on the first run. | `docs/feedback/2026-11-dramafinder-migration-project-b.md` L139, L150, L201 |

### Inferences

- **[opinion]** Two gaps would help Unified users most. First, upgrading to a Browserless Test with the JavaScript capture and `JavaScriptHandler` (25.5): it turns "the app reads the time zone and crashes on Browserless" into a one-class handler. Second, making silent `null`s and no-ops fail loudly (#692 for renderer text, the Crud buttons). A silent pass on Browserless costs more than a skipped test.

---

## Not verified

- Whether `ExecutedJavaScript`, `JavaScriptHandler` and `ClientDomEvent` are in a released 25.4.x of Browserless Test, or only on `main` (25.5-SNAPSHOT). The local checkout is shallow (50 commits). The capture's last change is vaadin/browserless-test#329 (2026-11-21); `focus()`/`blur()` say `@since 25.4`.
- That Flow's `DragSource`/`DropTarget` can be driven entirely by simulated `dragstart`/`drop` DOM events (the basis for classing generic drag and drop as (c)). Flow's DnD source was not read.
- That a simulated back/forward is feasible through Flow's router with `NavigationTrigger.HISTORY`. Flow's router source was not read for this note.
- Whether the web component's client-side ComboBox filter matches Flow's default server-side `ItemFilter` in every case (F26).
- Heartbeat and `closeIdleSessions` behaviour on Browserless. Only `MockHttpSession`'s "never expired on its own" was read.
- The open Flow pull requests (vaadin/flow#24656, #25045, #25366) were read from GitHub search results, not from a checkout. Their final shape can change.
- How exactly `GridTester` and the data communicator decide which rows count as "asked for by the client" beyond `Grid.setViewportRange` (F37).
- The 80–95 % and 20–40 % shares are this note's estimates from three migrations and the demo suite (F50–F53). No survey of real applications was done.
- Vaadin's TestBench/end-to-end docs were not checked for further browser-only features (for example, which visual comparison TestBench offers) beyond what is cited.
- PolymerTemplate support in Vaadin 25 (which module ships it, and on what licence) was not checked; only Browserless Test's warning was read.
