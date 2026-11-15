# Could a Vaadin Copilot plugin record Unified UI Tests?

Research for [#201](https://github.com/parttio/unified-testing-tool/issues/201). Input to, but out of scope of, the readability map [#193](https://github.com/parttio/unified-testing-tool/issues/193).

Researched 2026-11-15 against Vaadin/Copilot **25.3.5** (the version this repo's `pom.xml` pins, `vaadin.version` 25.3.5, `pom.xml:54`).

## Answer in short

**Feasible, and there are two routes.** First, a correction to the issue's premise: Copilot's **recorder** does not emit Playwright. The *UI Test Recorder* records clicks, keys and value changes and writes a **TestBench** JUnit test. The Playwright feature is a separate *UI Test Generation* panel, which sends the view's HTML and Java sources to Vaadin's AI server and gets a generated test back. Nothing is recorded there. Both are marked experimental.

1. **No plugin (works today, mostly):** record with Copilot's UI Test Recorder, then run this repo's existing `MigrateTestBenchToUnified` Migration Recipe on the output. The recorder already locates by component type plus label or text first, which is what the recipe converts cleanly. It also falls back to `withAttribute("data-test-id", …)`, class names and raw CSS paths, which the recipe can't convert. Those would end up as Migration Markers or Escape Hatch code.
2. **Our own Copilot plugin (cleaner output, real work):** a dev-mode-only add-on can add its own panel (`window.Vaadin.copilot.plugins.push`, the same way Vaadin's AppSec Kit does) and its own server handler (`DevToolsMessageHandler` through `ServiceLoader`). In dev mode, Flow's client exposes each element's Flow **Java class**. The `@UnifiedComponent(X.class)` annotation already maps a Wrapper to that class. So a recorder can choose the Wrapper exactly, and the server-side component can supply label, accessible name, text and test id to pick the filter. The cost is that Copilot's recorder internals (`copilotTree`, its event capture, its JavaParser writer) are **not** part of the plugin API, so the capture and code-writing logic has to be rebuilt.

Recommendation: prototype route 1 first to measure how much of a recorded test converts cleanly. Then decide between building route 2 and asking Vaadin for a Unified output target, which is route 3 below. The emitted *style* should wait for #193.

---

## 1. The existing recorder(s)

### Verified facts

| # | Fact | Source |
|---|---|---|
| F1 | Copilot's docs list two separate Test mode panels: **"UI Test Generation: Generates Playwright UI tests for the current Vaadin view. _Experimental feature_."** and **"UI Test Recorder: Records user interactions to generate end-to-end Vaadin TestBench tests automatically. _Experimental feature_."** | [vaadin/docs `articles/tools/copilot/index.adoc` L124–130 @ f0394e7](https://github.com/vaadin/docs/blob/f0394e71abf829838c3d2b68056f8c44b695ebc5/articles/tools/copilot/index.adoc#L124-L130) |
| F2 | Both sit behind experimental feature flags `testbench-test-recorder` ("TestBench Test Recorder", `requiresReload: true`) and `ui-test-generator` ("Generate Playwright UI Test for your application views."). | `com.vaadin:copilot:25.3.5` jar, `META-INF/frontend/copilot/copilot-experimental-features-*.js` |
| F3 | The recorder panel is **not in the Maven jar**. Copilot loads "private plugins" at runtime from `https://cdn.vaadin.com/copilot/<version>/copilot-plugins.js` (overridable with `window.Vaadin.copilot._localPluginsUrl`), and public plugins from the jar. Inside the jar, the code that loads plugins is in `frontend/copilot/plugins/copilot-plugins.ts`. | copilot 25.3.5 jar, `META-INF/frontend/copilot/copilot-unsaved-operation-coordinator-*.js` L18–33 |
| F4 | Copilot's own source is in a private repo, `vaadin/copilot-internal`: GitHub code search lists file paths from it, but the raw files return 404. The Maven pom declares Apache 2. The jar's `copilot-plugin-support.d.ts` has `isPublicPanel(tag)`, documented as "a public (Apache 2.0) panel", which implies the CDN panels are not covered by it. | `gh search code`, `raw.githubusercontent.com/vaadin/copilot-internal/...` → 404; `copilot-25.3.5.pom` `<licenses>`; jar `META-INF/frontend/copilot/shared/copilot-plugin-support.d.ts` L124–129 |
| F5 | **What the recorder captures** (CDN bundle `copilot-plugins.js` for 25.3.5, region `TestBenchTestGenerator`, ~L97807–98284): capture-phase `click` and `contextmenu`, `keydown`, optional `mouseover`; a `MutationObserver` on `opened` and `disabled` attributes plus added and removed nodes. Event handlers run in this order: grid row selection → item selection (select, combo box, multi-select combo box, radio, checkbox group, tabs, list box) → value change between animation frames (`setValue`, with the keys pressed) → fallback raw event. | `cdn.vaadin.com/copilot/25.3.5/copilot-plugins.js` (sha1 `a678502e…`), L97821–98000 |
| F6 | **Target choice:** from `document.elementsFromPoint`, it picks the first element that is in Copilot's component tree (`copilotTree.getNodeOfElement`) *and* has known TestBench metadata (`getKnownTestBenchElement` → `testBenchInfo.qualifiedClassName`, e.g. `com.vaadin.flow.component.textfield.testbench.TextFieldElement`). Otherwise it falls back to the first `vaadin-*` element in the tree. It re-targets items in an overlay to their owner (for example a `vaadin-combo-box-item` to its `vaadin-combo-box`). | same file L97854–97871, L98113–98173; metadata registry L8186ff |
| F7 | **Locator choice (`getPossibleSelectors`)**, in this order, and only if the result is unique among elements with the same tag: (1) text of a direct child `<label>` → `label`; (2) a direct child text node → `text`; (3) `id`; (4) `data-test-id` attribute; (5) unique `className`; (6) the only element with that tag → `firstElementInDocument`; (7) `cssPath`. Accessible name, `aria-label` and placeholder are **not** considered. | same file L98203–98222 |
| F8 | Vaadin field labels are light-DOM `<label slot="label">` children of the host, which is why rule (1) works for fields. | [vaadin/web-components `field-base/src/label-controller.js` L11–14](https://github.com/vaadin/web-components/blob/a65b319e8ed4b9ebcc66eb4e243341f5bddb7565/packages/field-base/src/label-controller.js#L11-L14), [`component-base/src/slot-controller.js` L108–119](https://github.com/vaadin/web-components/blob/a65b319e8ed4b9ebcc66eb4e243341f5bddb7565/packages/component-base/src/slot-controller.js#L108-L119) |
| F9 | **Assertions:** recorded automatically are notification presence plus its text (`textEquals`), dialog presence and absence, and enabled/disabled changes. Added by hand: "No javascript error present" and "Page Title". Navigation is recorded as `pageChangeEvent {href}`. | CDN file L97808–97818, L97987–98060, L98262–98282 |
| F10 | **Emitted code:** the server-side `com.vaadin.copilot.testbenchgenerator.TestBenchJavaTestGenerator` writes Java with the shaded JavaParser into a test class that extends `com.vaadin.testbench.BrowserTest`/`BrowserTestBase`. Its string constants include `$`, `withLabel`, `withText`, `withId`, `withAttribute`, `withAttributeContaining`, `withClassName`, `data-test-id`, `single`, `all`, `click`, `contextClick`, `sendKeys`, `org.openqa.selenium.Keys`, `select`/`selectByText`/`deselectByText`, `setSelectedTabIndex`, `isOpen`, `isEnabled`, `getText`, `waitUntil`, `navigate`/`to`, `getTitle`, `LogEntries`, and the fallback templates `TestBench.wrap((TestBenchElement) getDriver().findElement(By.cssSelector("%s")), %s.class)` and `getDriver().findElements(By.cssSelector("%s"))`. Class names default to `…IT` / `TestBenchIT.java`. | `javap -p` / `javap -v` (signatures and constant pool only) on copilot 25.3.5 classes `TestBenchJavaTestGenerator`, `TestBenchRecordHandler`, `PossibleSelectors` |
| F11 | The browser↔server protocol uses the commands `copilot-create-test-bench-tests` (sending a `scenario` with `items` and `imports`, plus the class and method name and the superclass), `copilot-get-test-bench-qualified-class-name`, `copilot-get-super-class-candidates` and `copilot-get-current-view-info` (view path → view's qualified class name). | CDN file L98440–98548; `TestBenchRecordHandler` constant pool |
| F12 | **UI Test Generation (AI):** the panel sends `{uiid, path, base, sources, html: document.body.outerHTML}` as `copilot-generate-tests`. `GenerateTestsHandler` reads the view's Java sources and calls `TestGenerationServerClient.generateTests(ProKey, …)`, which talks to the Copilot server (fields `proKey`, `machineId`, `aiProvider`). `TestFramework` is `TEST_BENCH`, `PLAYWRIGHT_JAVA` or `PLAYWRIGHT_NODE`. The panel says: "Might require manual adjustments. Tests may not compile or pass without intervention." | CDN file L95900–95975; `javap` on `GenerateTestsHandler`, `TestGenerationServerClient`, `TestFramework` |
| F13 | **Requirements:** "Copilot functionality that makes changes to code … requires you to log in using Vaadin account"; "Code manipulation features do not work with Vaadin offline license"; an Internet connection is required "to provide AI features and validate license". | [copilot/index.adoc L12–13, L425–434](https://github.com/vaadin/docs/blob/f0394e71abf829838c3d2b68056f8c44b695ebc5/articles/tools/copilot/index.adoc#L12-L13) |
| F14 | "End-to-end testing requires a commercial TestBench subscription." Browserless is free. | [vaadin/docs `articles/flow/testing/index.adoc` L15](https://github.com/vaadin/docs/blob/f0394e71abf829838c3d2b68056f8c44b695ebc5/articles/flow/testing/index.adoc#L15) |

### Inferences

- Writing the recorded test is a code change, so the recorder most likely needs a Vaadin account login (F13), and running its output needs a TestBench subscription (F14). A Unified UI Test made from it would run on Browserless or Playwright without a TestBench subscription.
- The AI generator is not a recorder. It can't be hooked for recording, and its output isn't deterministic, so it isn't a basis for this ticket.

## 2. Extension points

### Verified facts

| # | Fact | Source |
|---|---|---|
| E1 | The docs say: "Copilot uses a plugin architecture which allows additional functionality to appear as panels. This includes tools such as Vaadin AppSec Kit, as well as third-party plugins." There is no further documentation of the API. | [copilot/index.adoc L139–141](https://github.com/vaadin/docs/blob/f0394e71abf829838c3d2b68056f8c44b695ebc5/articles/tools/copilot/index.adoc#L139-L141); searching vaadin/docs for `copilot.plugins` / `DevToolsMessageHandler` finds nothing |
| E2 | **Browser plugin API:** `CopilotPlugin { init(copilotInterface) }`, where `CopilotInterface` has only `send(command, data)` and `addPanel(PanelConfiguration)`. A panel element can implement `MessageHandler.handleMessage(ServerMessage)`. `PanelConfiguration.tag` is typed as the closed union `CopilotPanelTag` of Copilot's own panels. | jar `META-INF/frontend/copilot/shared/copilot-plugin-support.d.ts` L9–35, L56–125 |
| E3 | Registration: plugins `push` onto `window.Vaadin.copilot.plugins`, and Copilot replaces `push` so that later plugins initialise immediately. | jar `copilot-unsaved-operation-coordinator-*.js` L8–17, L34–44 |
| E4 | **Reference third-party plugin, AppSec Kit:** the TS panel calls `window.Vaadin.copilot.plugins.push(copilotPlugin)` and is built with Vite into `META-INF/frontend/appsec-kit`. The Java side is `@JsModule(value = "./appsec-kit/appsec-kit-plugin.js", developmentOnly = true)` on a class that `implements DevToolsMessageHandler`, registered in `META-INF/services/com.vaadin.base.devserver.DevToolsMessageHandler`. AppSec Kit is under the Vaadin Commercial License. | [vaadin/appsec-kit @ de11e57: `appsec-kit-plugin.ts`](https://github.com/vaadin/appsec-kit/blob/de11e5761d4cbd1c66616141e87587f238295609/appsec-kit-starter/frontend/appsec-kit/appsec-kit-plugin.ts), [`AppSecDevToolsPlugin.java` L25–26](https://github.com/vaadin/appsec-kit/blob/de11e5761d4cbd1c66616141e87587f238295609/appsec-kit-starter/src/main/java/com/vaadin/appsec/service/AppSecDevToolsPlugin.java#L25-L26), [`vite.config.ts`](https://github.com/vaadin/appsec-kit/blob/de11e5761d4cbd1c66616141e87587f238295609/appsec-kit-starter/vite.config.ts), [services file](https://github.com/vaadin/appsec-kit/blob/de11e5761d4cbd1c66616141e87587f238295609/appsec-kit-starter/src/main/resources/META-INF/services/com.vaadin.base.devserver.DevToolsMessageHandler) |
| E5 | **Server plugin API (Flow, Apache 2.0):** `DevToolsMessageHandler { handleConnect, handleMessage(command, JsonNode, DevToolsInterface), handleDisconnect }`, loaded with `ServiceLoader` by `DebugWindowConnection`. Messages go to each handler until one returns `true`. `DevToolsInterface` only has `send(command, data)`. Copilot registers itself the same way (`com.vaadin.copilot.Copilot`). | [flow `DevToolsMessageHandler.java` @ 28a611f](https://github.com/vaadin/flow/blob/28a611f969c2daebbcf97086297149d605e7f71e/vaadin-dev-server/src/main/java/com/vaadin/base/devserver/DevToolsMessageHandler.java), [`DebugWindowConnection.java` L107–111, L340–355](https://github.com/vaadin/flow/blob/28a611f969c2daebbcf97086297149d605e7f71e/vaadin-dev-server/src/main/java/com/vaadin/base/devserver/DebugWindowConnection.java#L107-L111); copilot jar `META-INF/services/com.vaadin.base.devserver.DevToolsMessageHandler` |
| E6 | `@JsModule(developmentOnly = true)` loads the module only in development mode (since 24.2). | [flow `JsModule.java` L120–128](https://github.com/vaadin/flow/blob/28a611f969c2daebbcf97086297149d605e7f71e/flow-server/src/main/java/com/vaadin/flow/component/dependency/JsModule.java#L120-L128) |
| E7 | **Element → server component (dev mode only):** Flow's client publishes `getNodeId(element)` and `getUIId()`, and outside production mode also `getNodeInfo(nodeId) → {element, javaClass, hiddenByServer, styles}`. Copilot's `getFlowComponent` uses exactly this. | [flow-client `publishClient.ts` L53–87](https://github.com/vaadin/flow/blob/28a611f969c2daebbcf97086297149d605e7f71e/flow-client/src/main/frontend/internal/client/publishClient.ts#L53-L87); jar `copilot/icons-*.js` L2683–2705 (`frontend/copilot/shared/flow-utils.ts`) |
| E8 | On the server, `uiId` and `nodeId` resolve with public Flow API: `UI.getInternals().getStateTree().getNodeById(id)` → `Element.get(StateNode)` → `element.getComponent()`. | flow `UI.java` L1092, `UIInternals.java` L422, `StateTree.java` L274, `Element.java` L155, L1627 (same commit) |
| E9 | Copilot's own server commands (`CopilotCommand`) receive the `VaadinSession` (`CopilotCommand.currentSession`, `CopilotSession(VaadinSession, …)`), and `ComponentSourceFinder.findTypeAndSourceLocation(uiId, nodeId)` maps a component to its creating source line. These are Copilot classes and not a documented API. | `javap -p` on `com.vaadin.copilot.CopilotCommand`, `CopilotSession`, `ComponentSourceFinder` |
| E10 | The recorder's state (`testBenchGeneratorState`, `testBenchTestGenerator`) is module-internal in the CDN bundle, and is not exposed on `window` or on `CopilotInterface`. The recorder dispatches no DOM events; the only observable signals are the eventbus events `testbench-action-start-btn-clicked` / `-stop-btn-clicked`. **Correction:** Copilot's component tree *is* reachable as `window.Vaadin.copilot.tree` (`root`, `allNodesFlat`, `currentTree`), next to `window.Vaadin.copilot.eventbus` (`on`/`once`/`off`/`emit`/`send`) and `window.Vaadin.copilot.comm(command, data, cb)`. | CDN file L97748–98290, L2355; jar `copilot.js` L16–80, L822; `copilot-global-vars-later-*.js` L349 |

### Inferences

- **The recorder's output can't be hooked or replaced through a supported API.** `CopilotInterface` offers no event subscription, the recorder's state is private (E2, E10), and the generator is a closed Copilot command (F11). The only interception points are unsupported ones: overriding `_localPluginsUrl`, or claiming `copilot-create-test-bench-tests` with our own `DevToolsMessageHandler` loaded before Copilot's. Neither should be relied on.
- A Unified recorder plugin therefore **ships its own** panel, DOM capture and code writer. It can reuse the *design* of F5–F7 but not the code, which is a closed CDN bundle.
- Getting the `VaadinSession` from a plain `DevToolsMessageHandler` isn't shown by any public example; see the open questions. Copilot does it internally (E9).

## 3. Mapping recorded actions to the Unified API

What the Unified API needs per lookup (spec [04-locating.md](../spec/04-locating.md), ADR [0001](../adr/0001-locator-filters-follow-component-type.md), [0002](../adr/0002-unified-wrapper-names-and-class-literal-lookups.md)) is a Wrapper type, then a filter from its own filter set, preferring `withLabel`, `withAccessibleName`, `withText` or `withPlaceholder`, and using `withTestId` or `withId` only as a fallback.

| Needed | What Copilot's recorder has | What a Unified plugin can add |
|---|---|---|
| **Wrapper** | The TestBench element class from tag metadata (F6). This maps one-to-one onto Wrappers for the Core Set, and the recipe already holds that map (`unified-test-rewrite/.../Wrappers.java` `BY_ELEMENT`, L66ff). | The **Flow Java class** from `getNodeInfo().javaClass` (E7), matched against `@UnifiedComponent(X.class)` on Wrapper interfaces (`unified-test-api/.../UnifiedComponent.java`; e.g. `UnifiedButton.java` L11). This covers add-on Wrappers (ADR 0003) and subclasses, by walking the class hierarchy on the server, which tag metadata can't do. A class with no Wrapper falls back to `page.findComponent(X.class)`. |
| **Label** | A direct `<label>` child, if unique per tag (F7, F8). | `HasLabel.getLabel()` on the server component (E8). It could also cover FormLayout item labels, which the spec counts as labels. |
| **Accessible name** | Not captured (F7). | The server component's `aria-label` / `accessibleName` in the spec's order (04-locating "Accessible name"), or the browser's computed name. |
| **Text** | A direct text-node child (F7). | `getText()` on the component. |
| **Placeholder** | Not captured. | The `placeholder` property. |
| **Test id** | The **`data-test-id`** attribute (F7, F10). | `Component.getTestId()`, which reads **`data-testid`** ([flow `Component.java` L485–510](https://github.com/vaadin/flow/blob/28a611f969c2daebbcf97086297149d605e7f71e/flow-server/src/main/java/com/vaadin/flow/component/Component.java#L485-L510)), the attribute `withTestId` matches. |
| **Uniqueness** | Checked against the DOM, per tag and per attribute (F7). | Should be checked with Unified semantics (hidden components included, open overlays only, see 04-locating "What a query sees"). This could be done by evaluating the candidate query against the live UI. Open question. |
| **Navigation** | `pageChangeEvent {href}`; the view class is available via `get-current-view-info` (F11). | `page.navigate(View.class)`, as the TestBench recipe already does (`TestBenchSetupToUnified.java` L208–223). |
| **Item selection, grid** | `selectedItemText` / `selectedItemIndex` / `selectedRowIndexes` with the owner (F5, F6). | The Item Name (glossary) and the owning-wrapper rule (04-locating "Scoping", [07-grid.md](../spec/07-grid.md)). Row *indexes* should become `rows().withCellText(..)` where possible. |
| **Assertions** | Notification text, dialog open/closed, enabled (F9). | Same, plus value and text assertions picked from the component, written as the retrying `assertThat(..)`. |

### Verified: how far route 1 gets today

- The TestBench Migration Recipe converts `$(XElement.class).withLabel/withText(..).single()` into `page.find(UnifiedX.class)…`, `getDriver().get(url)` into `page.navigate(..)`, `BrowserTestBase` into `@UnifiedSpringBootTest`/`@UnifiedUITest`, and selection calls (`selectByText` → `selectItem`, TabSheet tab selection). [docs/migrating-from-testbench.md "What it converts"](../migrating-from-testbench.md)
- The recipe's common filters are `withId`, `withClassName` and `withTheme` (`unified-test-rewrite/src/main/java/org/vaadin/addons/unified/rewrite/Wrappers.java` L62). It has no `withAttribute` → `withTestId` rule, and the recorder uses `data-test-id` rather than `data-testid` in any case (F7 vs `Component.setTestId`).

### Inferences for route 1

- Recorded lookups that use the label or text rule should convert cleanly.
- `withAttribute("data-test-id", ..)`, `cssPath` (`TestBench.wrap(… By.cssSelector …)`), raw `sendKeys(Keys…)`, `getDriver().navigate().to(..)` (only `getDriver().get(url)` is documented as converted), `assertNoJsError` via `LogEntries`, and `setSelectedTabIndex(int)` will probably become Escape Hatch code with Migration Markers.
- Icon-only buttons are the clear gap: F7 never tries the accessible name.

## 4. Prior art: how other recorders choose a locator

| Tool | Strategy | Source |
|---|---|---|
| **Playwright codegen** | Scores every candidate and keeps the lowest total, tie-breaking for uniqueness with `nth`. Scores: test id 1, other `data-test*` 2, role+name 100, placeholder 120, label 140, alt text 160, text 180, title 200, CSS id 500, role without name 510, `input[name/type]` 520, tag 530, nth 10000, CSS fallback 10,000,000. Exact matches get a small penalty compared with substring matches. Before choosing, it re-targets to the closest interactive ancestor (`button, input, select, [role=button] …`). | [microsoft/playwright v1.65.0 `packages/injected/src/selectorGenerator.ts` L37–66, L98–104](https://github.com/microsoft/playwright/blob/v1.65.0/packages/injected/src/selectorGenerator.ts#L37-L66) |
| **Cypress Studio** | "Studio automatically picks a unique selector for each element … We use a priority strategy". The default `Cypress.ElementSelector` priority is `data-cy`, `data-test`, `data-testid`, `data-qa`, `name`, `id`, `class`, `tag`, `attributes`, `nth-child`. Projects can configure it. | [cypress-documentation `docs/app/ai/cypress-studio.mdx` L152–158 @ 31105b4](https://github.com/cypress-io/cypress-documentation/blob/31105b4c87aa8e8898e73a30507ca9e95bed9b90/docs/app/ai/cypress-studio.mdx#L152-L158); [cypress `packages/driver/src/cypress/element_selector.ts` L10–21 @ 454bf47](https://github.com/cypress-io/cypress/blob/454bf4704d5c914de0de0a3120352a7b984d9dfe/packages/driver/src/cypress/element_selector.ts#L10-L21) |
| **Selenium IDE** | Builds **all** locators with named builders (`css:data-test-id`, `id`, `linkText`, `name`, `css:data-attr`, `css:finder`, `xpath:link`, `xpath:img`, `xpath:attributes`, `xpath:idRelative`, `xpath:href`, `xpath:position`, `xpath:innerText`). It stores them as alternative targets and uses the first in a user-reorderable preferred order. | [SeleniumHQ/selenium-ide @ f2ddd58 `…/PlaybackWindow/preload/locator-builders.ts` L40–105, L297–562](https://github.com/SeleniumHQ/selenium-ide/blob/f2ddd5841c3bd618f41d48185b6392c265c89918/packages/selenium-ide/src/browser/windows/PlaybackWindow/preload/locator-builders.ts#L40-L105) |
| **Copilot UI Test Recorder** | Type first (the TestBench element from tag metadata), then label → text → id → `data-test-id` → class → only-of-tag → CSS path (F6, F7). | F6, F7 |

Inferences:

- The Unified API's "Wrapper type is the role" rule (04-locating) makes the Playwright approach the closest fit: type plus user-visible name ranks first, with test id and id as fallbacks. One difference: Playwright ranks test id *first*, whereas the spec ranks it as a fallback.
- Playwright's re-targeting to a native `input`/`button` would *lose* the Vaadin component. A Unified recorder must re-target to the Flow component (E7), as Copilot does (F6).
- Selenium IDE's "keep all alternatives" approach is worth copying for the review step: show the alternatives and let the user pick, as the Copilot recorder hints with `selectedAttributePairByUser` in `PossibleSelectors` (F10, `javap`).

## 5. Feasibility verdict

**Feasible.**

- **Route 1** (Copilot recorder plus the TestBench Migration Recipe) needs no new tooling. It gives decent results where the app has labels and text, but it needs a TestBench subscription and a Vaadin login, and it leaves markers for the fallbacks.
- **Route 2** (our own dev-mode Copilot plugin) can produce Unified UI Tests that are *better* than what Copilot's recorder makes for TestBench, because the dev-mode Flow class and the server-side component give the Wrapper and the label, accessible name, text and test id exactly.
- **Route 3** is to ask Vaadin to add a Unified target to the recorder. It already has a framework enum for the AI generator (F12) and a pluggable scenario model (F11). This is the lowest-effort outcome if Vaadin agrees.

### Main risks

1. **No supported hook into Copilot's recorder** (E2, E10). Route 2 duplicates capture logic, and route 1 depends on the recorder's output format, which is experimental (F1, F2) and can change between versions.
2. **The plugin API is undocumented and minimal** (E1, E2). `PanelConfiguration.tag` is a closed union, and the only third-party reference is Vaadin's own AppSec Kit. It could change between Copilot versions.
3. **Dev mode only.** `getNodeInfo().javaClass` and `@JsModule(developmentOnly)` exist only outside production mode (E6, E7). That's acceptable for a recorder.
4. **Classpath:** Wrappers live in the test-scoped Unified API, or as an `optional` dependency in add-on jars (ADR 0003). They may not be on the running app's classpath, so mapping a Flow class to a Wrapper needs the plugin to bring the Unified API, or a generated index, onto the dev runtime classpath. This is inferred from Maven scope rules and not tested.
5. **Uniqueness semantics differ.** A DOM-unique selector may not be unique for a Unified query, which includes hidden components and differs on Browserless. Using the wrong rule gives `single()` failures at replay.
6. **Licensing and login** for route 1 (F13, F14). Route 2 avoids both if it doesn't depend on Copilot's private CDN plugins.
7. **The output style isn't settled.** #193 may change the emitted shape (action-first verbs, no `.single()`, steps), so any code writer built before #193 settles would be rewritten.

## 6. Copilot 25.4 snapshot

Checked `com.vaadin:copilot:25.4-SNAPSHOT` build `25.4-20261112.070010-84` (`maven.vaadin.com/vaadin-prereleases`; latest milestone `25.4.0-alpha5`) against 25.3.5. **Nothing the findings rely on changed.**

| Part | 25.4 vs 25.3.5 | How checked |
|---|---|---|
| `com.vaadin.copilot.testbenchgenerator` (25 classes) and `GenerateTestsHandler`, `TestGenerationServerClient`, `TestFramework` | Identical | Class lists plus `javap -p -c -constants`, with constant-pool numbers and branch offsets stripped |
| Recorder browser code, `cdn.vaadin.com/copilot/25.4-SNAPSHOT/copilot-plugins-5910.js` (build suffix from jar `consts.ts`) and `…/25.4.0-alpha5/copilot-plugins.js` | Identical | `TestBenchTestGenerator` region diffed after replacing minified names of 1–3 characters |
| `copilot-plugin-support.d.ts`, `copilot-experimental-features-*.js` | Byte-identical | `diff` |
| Flow `main` (25.4-SNAPSHOT): `publishClient.ts` `getNodeInfo().javaClass`, `DevToolsMessageHandler` | Still present | raw files on `vaadin/flow` `main`, 2026-11-15 |

The only new Copilot classes are a Kotlin source rewriter (`kotlinrewriter.KotlinTemplateWriter`, `KotlinAttachmentLocator`, `rewriter.TemplatePolicy`), unrelated to recording.

## 7. Does route 2 need AI?

No. Copilot's recorder is rule-based end to end: DOM event capture (F5), component choice from metadata (F6), a fixed locator order (F7) and JavaParser code writing (F10). AI is only in the separate UI Test Generation panel (F12). Route 2 rebuilds that deterministic pipeline, and can be more exact than Copilot's because the dev-mode server component supplies the Java class and the label, accessible name, text and test id (E7, E8). Not reusing Copilot's private recorder also keeps Vaadin's AI server and CDN bundle out of the loop. Whether a third-party Copilot panel needs a Vaadin login is untested.

## Open questions

Depend on #193's readable style:

- Which shape does a recorded step emit? `page.find(UnifiedTextField.class).withLabel("City").single().setValue("Turku")`, `page.fillIn("City", "Turku")`, or a `step(..)` block? The recorded events (fill, click, select, expect notification) map directly onto #196's top intents.
- Do recorded steps become named steps for the "what the user did" report #195 wants?
- If #193 reopens ADR 0002 (for example generated view models), should the recorder target view-model methods instead of lookups?

Technical:

- How does a third-party `DevToolsMessageHandler` get the `VaadinSession` and `UI` for a `uiId`? Copilot does it internally (E9). Is there a public path, or must the plugin go through Copilot?
- Can the plugin evaluate a candidate Unified query on the live dev-mode UI to check uniqueness with Unified semantics (a Browserless-style evaluation over the session's component tree)?
- Since which Copilot version has the UI Test Recorder existed, and is it planned to leave experimental status? Only 25.3.5 was inspected.
- Will Vaadin accept a Unified output target in the recorder (route 3), or a documented recorder event hook?
- Does the recorder's output need a Vaadin login? This is inferred from F13 and not tested.
- How much of a real recorded test converts cleanly with the current recipe? A spike would answer it: record `EditPersonUnifiedTest`'s scenario in the demo and run `MigrateTestBenchToUnified`.

## Not checked

- The Vaadin forum and vaadin.com/docs: `vaadin.com` didn't resolve from this research environment, so the docs were read from the `vaadin/docs` sources instead.
- Copilot's server-side method bodies: only signatures and constant pools were read (`javap`), not decompiled code.
- No recording was run end to end.
