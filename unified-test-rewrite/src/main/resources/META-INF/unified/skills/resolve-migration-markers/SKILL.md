---
name: resolve-migration-markers
description: Work through the Migration Markers (// TODO unified:) the TestBench, Browserless or Dramafinder Migration Recipe left, until each is resolved into Unified API code or kept as an explained Escape Hatch.
disable-model-invocation: true
---

# Resolve Migration Markers

A Migration Recipe rewrote this project's TestBench (Selenium) tests, Browserless tests, Dramafinder (Playwright) tests, or several of them, into Unified UI Tests. Where it could not, or where a person has to check its choice, it left a **Migration Marker**: a `// TODO unified: ...` comment above the statement, method or class. Your job is to give every marker one of two outcomes:

- **Resolved**: the code now says it with the Unified API, the test passes, and the marker is gone.
- **Kept**: the code stays as it is, and the marker becomes `// Escape Hatch kept: <reason>`, one line saying what the Unified API lacks.

A test is better the less it depends on an Escape Hatch: it then runs on every Testing Tool (Browserless, Playwright, Selenium) instead of the one its Escape Hatch needs.

## Steps

1. **List the markers.** Search the test sources for `TODO unified:`. Write the list down: file, line, marker text. Done when the search and your list agree.
2. **Resolve or keep each marker,** using the reference below for its kind. Work one test class at a time, and run that class's tests (step 3) before the next. Done when no `TODO unified:` is left.
3. **Prove it.** For every test class you changed, run it on Browserless and on Playwright:
   `mvn test -Dtest=<Class>` and `mvn test -Dtest=<Class> -Dvaadin.testing.tool=playwright`.
   A test passes, or is skipped because it still declares `@RequiresTestingTool`. When one fails, fix it or put the Escape Hatch back with an explanation. Remove a `@RequiresTestingTool(TestingTool.PLAYWRIGHT)` only once the test passes on both. Done when every changed class passes on both.
   Last, run the whole test suite on both: `mvn test` and `mvn test -Dvaadin.testing.tool=playwright`. A per-class run can't show test data one class leaves behind for the next. Done when both pass.
4. **Report** a table: marker, outcome (resolved or kept), what you changed.

## Reference: the Unified API

Write the Unified code the way the `write-unified-ui-tests` skill (`.claude/skills/write-unified-ui-tests/SKILL.md`)
says: its canonical forms for lookups, grid rows and retrying assertions, and how to read a failure. Read it before
the first marker. Read a Wrapper's methods in the `unified-test-api` sources before writing a call.

## Reference: where a literal conversion changes meaning

Check every converted test for these, marker or not; each passed before migration and fails after, on one Testing Tool or both:

- **An absence check.** Lookups include hidden components, on every Testing Tool; the old Browserless locators didn't. Where the view hides a component instead of removing it, `assertFalse(findButton().withText("Approve").exists())` becomes `assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist()`. Text the same way: `getText()` and `hasText(..)` include hidden content, so for "the dialog doesn't say X", look up the component that holds X with `.visible()` and check `doesNotExist()`.
- **A checkbox click.** `element().click()` toggles a checkbox on Browserless only. Set it: `check()`, `uncheck()` or `setChecked(..)` on `UnifiedCheckbox`, with the state the test expects after the click.
- **A navigation that was expected to throw** (`assertThrows(.., () -> navigate(..))`, a denied or missing route). `page.navigate(..)` doesn't throw: where the router sends the user is the result. Assert what the user sees: `assertThat(page).isAt(LoginView.class)` or the not-found view, and `doesNotExist()` on what the denied view would show (its grid, its form).
- **A test of the application's error handling** (an error dialog after an action fails). On Browserless an exception from a listener goes into the test by default. Set `vaadin.testing.browserless.route-errors-to-session=true` (in `junit-platform.properties`) to send it to the session's `ErrorHandler`, as a browser does, and assert the error dialog on every Testing Tool.
- **Several Spring contexts.** Each class with its own context setup (a `@ServiceConnection` container field, `@MockitoBean`, `@Import`, properties, profiles) starts another Spring context, each with an embedded server on every Testing Tool. When migrated classes each declare the same container field, move it, `@UnifiedSpringBootTest` and the profile to one shared base class they all extend, so the suite runs on one context.

## Reference: the Escape Hatches

Each gives a Testing Tool's own test base API on the test's page, and needs `@RequiresTestingTool` for its tool:

| Escape Hatch | Testing Tool | Annotation |
|---|---|---|
| `TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);` | Selenium | `@RequiresTestingTool(TestingTool.SELENIUM)` |
| `BrowserlessEscapeHatch browserless = BrowserlessEscapeHatch.of(page);` | Browserless | `@RequiresTestingTool(TestingTool.BROWSERLESS)` |
| `BrowserlessUIContext window = page.unwrap(BrowserlessUIContext.class);` (a context-API test's window, under its old name) | Browserless | `@RequiresTestingTool(TestingTool.BROWSERLESS)` |
| `Page playwright = page.unwrap(Page.class);` (Dramafinder elements are built on it) | Playwright | `@RequiresTestingTool(TestingTool.PLAYWRIGHT)` |

When a method no longer uses its Escape Hatch variable, delete that line. When a test, and the helpers it calls, no longer use one, delete its `@RequiresTestingTool` too: the test then runs everywhere.

## Reference: marker kinds of all recipes

**`was first(); the lookup fails when several components match`** (also `waitForFirst()`, `last()`, `atIndex(n)`, `getCell(..)`). Run the test. When it passes, delete the marker. When it fails with "several components match", add the filter that picks the intended one (`withLabel`, `withText`, `withId`, ...), then delete the marker.

**`was withCaption(..)` / `was withLabel(..)` on a button.** Read the view: when the button shows that text, delete the marker. When it is an icon-only button, the text is its accessible name: use `withAccessibleName(..)`, then delete the marker.

**`..., X has a Core Set Wrapper: use UnifiedX` / `..., X extends Y: use UnifiedY with a filter (...)`.** The Wrapper exists: replace the lookup with `page.find(UnifiedX.class)` (for a subclass, `page.find(UnifiedY.class)` plus the filters that pick its components, such as `withClassName(..)`), translate each call the test makes, then delete the Escape Hatch. Keep it when the call has no Unified form.

**`..., no Wrapper for X: write one (ADR 0003)` / `..., a custom element: write a Wrapper for it (ADR 0003)`.** Look for an existing Wrapper first, for X and then for each of its superclasses (a TestBench element's `@Element` tag is the component's `@Tag`):
- the Core Set: the library's Wrappers are in a jar, where a source search can't see them. Each is listed with the Flow class it finds ("Flow's `Dialog`") in `META-INF/unified/api-reference.md` inside the `unified-test-api` jar (the same page is [on the site](https://parttio.github.io/unified-testing-tool/api-reference.md));
- the project: search the test sources for `@UnifiedComponent(` naming the class.

Use the Wrapper found for X. For a superclass's Wrapper, add the filter that tells X apart: `RowActionMenu extends MenuBar` is `page.find(UnifiedMenuBar.class).withClassName("row-action-menu")`, `ErrorDialog extends Dialog` is `page.find(UnifiedDialog.class).withHeaderTitle(..)`. A Flow class with no Wrapper on its chain is `page.findComponent(X.class)` with a filter when the test only finds, clicks or reads its text. Otherwise write one next to the tests: an interface with `@UnifiedComponent(TheComponent.class)`, extending `UnifiedWrapper` and the filter mixins it needs with itself as the type argument (`CommonFilters<UnifiedX>`, and `LabelFilter<UnifiedX>`, `TextFilter<UnifiedX>` or `PlaceholderFilter<UnifiedX>`), with `default` methods written on `element()` (`getProperty`, `getText`, `findChildren(..)`, `click()`). Translate each call the test makes, then replace the lookup and delete the Escape Hatch. Keep it when the component's state lives only on the server (Browserless) or needs real browser rendering (Selenium).

## Reference: marker kinds of the Dramafinder recipe

**`Dramafinder Escape Hatch: <call>`.** Try to say it with the Unified API, then run the test on Browserless and Playwright:
- a Dramafinder lookup, `XElement.getByLabel(playwright, ..)` or `find(playwright, XElement.class)...`, becomes `page.find(UnifiedX.class).withLabel(..)`; an element call becomes the Wrapper's method, an `assert...` a retrying `assertThat(wrapper)...`;
- a raw Playwright locator names a component: `getByRole(AriaRole.BUTTON, ..setName("Save"))` is `page.find(UnifiedButton.class).withText("Save")` (or `withAccessibleName` for an icon button), `getByRole(AriaRole.TEXTBOX, ..setName("Name"))` and `getByLabel("Name")` are `page.find(UnifiedTextField.class).withLabel("Name")` (read the view for the field's type), `locator("vaadin-x")` is the Wrapper for that tag; `fill(..)` is `setValue(..)`, `click()` is `click()`, `PlaywrightAssertions.assertThat(locator).hasText(..)` is `assertThat(wrapper).hasText(..)`;
- a row of a grid, `findRow`, `getCell`, sorting: `UnifiedGrid`'s `row()` with its filters, `row(i)`, `column(..)`.

Keep it, `// Escape Hatch kept: <reason>`, only for what has no Unified meaning: `route(..)` and network mocking, tracing, `evaluate(..)` and other JavaScript, CSS selectors into a component's shadow DOM. When no statement uses `playwright` any more, delete its line.

**`extended <Copy>, whose source the recipe didn't see: copy its class annotations here (...)`** (on the class). The test extended the project's copy of `SpringPlaywrightIT`, which lives outside the migrated sources (another module, a jar). Find its source and copy onto the class each annotation the marker names, with its arguments: `@SpringBootTest`'s `properties`, `classes` or `args` go on `@UnifiedSpringBootTest`, its `webEnvironment` doesn't. Then delete the marker.

**`not migrated: <member> of <BaseClass>, ...; convert them by hand, then run the migration again`** (on the class). The recipe left the whole class as Dramafinder: it uses base class members no rule converts. For each one named: `loginAs(..)`/`newPageAs(..)` become `page.openAsUser(name, roles...)`; `... in <helper> of <Copy>` is in a member of the project's copy of `SpringPlaywrightIT` the test needs: convert it there, in the copy; `uses <field> of <Copy>, which uses the test base` is a field, often a `@RegisterExtension` `TestWatcher` taking screenshots from `page`: drop it when the Unified API's own failure reports cover it (the reason then ends `; the Unified API writes failure evidence itself: remove it`), else rewrite it without the Dramafinder page; a `setupTest()` override with code after `super.setupTest()` moves that code to the start of each test, after its navigation; `getUrl()` in a test body becomes a relative path for `page.navigate(..)`. Once nothing names a base class member but `page`, `navigate(..)` and the `assert...` checks, delete the marker and run the migration again on the class; else convert the class by hand the way the recipe converts the others in this project.

**`Dramafinder Escape Hatch: new X(..), a custom element: write a Wrapper for it (ADR 0003)`.** `X` extends Dramafinder's `VaadinElement`. Turn it into a Wrapper (see the custom element kind above): each method of `X` the tests call becomes a `default` method on `element()`, written from what `X` does with its `Locator` (a `locator("span").nth(i).click()` is `element().findChildren(..)`, a `hasJSProperty("value", ..)` check reads `getProperty("value")` in a retrying assertion or `page.waitUntil(..)`). Replace the construction with `page.find(..)`, the calls with the Wrapper's, and delete `X` when nothing uses it.

**`was 1.x getByLabel(..), a case-insensitive substring and first match; tighten it to withLabel(..) once the test passes`** (also `getByText`, `withText`). Dramafinder 1.x matched a substring of the label, `aria-label` or placeholder. Replace `withAccessibleNameContaining(s)` with `withLabel(s)` (or `withTextContaining` with `withText`) and run the test: keep the exact filter when it passes, else use the filter that names the component exactly (read the view). Then delete the marker.

**`was findRowIndexesWithColumnText(n, ..).get(0): any column matches now; ...`.** Dramafinder matched the text in column `n` only. Read the view for that column's header and write `row().withCellText("<header>", text)`; run the test, then delete the marker.

**`was getCell(..).click(); a row's click() clicks the row, not that cell`.** Read the view: when the cell holds nothing clickable of its own, the row click does the same; delete the marker. When it holds a component (a button, a checkbox), find it in the cell: `row.cell("<header>").find(UnifiedButton.class)...`.

**`was locator("#x"): no view in the sources sets this id; UnifiedContainer finds any component, ...`** (also `getByTestId(..)`, `[data-testid=..]`, and `the sources set this id on components of different classes`). The recipe found no component class for the id, so it used `UnifiedContainer`, which finds any component. Find where the application sets the id or test id (a builder, a constant, another module) and, when that component has a Wrapper, replace `UnifiedContainer` with it (`UnifiedButton`, `UnifiedTextField`, ...); keep `UnifiedContainer` for a layout or a component without one. Run the test, then delete the marker.

**`was getByText(..), a substring and first match; ...`** (a notification or badge). As `was first()`: run the test, add a filter when it finds several, then delete the marker.

## Reference: marker kinds of the TestBench recipe

**`TestBench Escape Hatch: <call>`.** Say the same thing with the Unified API when it can: a lookup becomes `page.find(..)`, a read becomes a Wrapper method or a retrying assertion, a script that checks the URL becomes `assertThat(page).hasPath(..)`. Keep it when it needs what only a real browser driver gives (arbitrary JavaScript, raw WebDriver, Selenium-only APIs).

**`TestBench page object: new X(this)`.** Convert the page object when every call in it has a Unified form: its TestBench field and constructor parameter become `UnifiedPage`, its `$(XElement.class)` lookups become `find(UnifiedX.class)`, its element return types become Wrappers. Then pass `page` instead of `testBench`. Keep it otherwise.

**`browser setup removed (...)`.** The Unified API's Selenium adapter chooses the browser. Move what the removed setup did to the `vaadin.testing.*` settings (`junit-platform.properties`, or system properties) when one covers it; otherwise there is nothing to carry over. Delete the marker either way and name the removed setup in your report.

## Reference: marker kinds of the Browserless recipe

**`Browserless Escape Hatch: <call>`** (also on a window, `window.findGrid(..)`). Say the same thing with the Unified API when it can: a locator such as `window.findTextField().withLabel(..)` becomes `page.find(UnifiedTextField.class).withLabel(..)`, `navigate(View.class)` becomes `page.navigate(View.class)` (when the test reads the view it returns, find what it reads with a locator instead), a tester call becomes the Wrapper's method, a component getter becomes a Wrapper getter or a retrying assertion. Keep it when it needs the server-side Java objects only Browserless has (a view's fields, beans, `UI` or `VaadinSession` state).

**`Component Reference: <call>: replace it with a locator that finds the same component`.** The test holds a component's Java object (a view field, `view.getChildren()...`) instead of finding it. Read the view to see how it builds that component, write the locator that finds it and nothing else (`withLabel`, `withText`, `withTestId`, ...), and use the Wrapper. Prove the locator is unique by running the test. Delete the local variable or view lookup that only served the reference.

**`filter runs Java on the component: <call>: replace withCondition(..) with a locator`** (also `withPropertyValue(..)`). The filter calls a Java method on each component. Read the view to find what sets that state, and pick a locator that matches the same component (its label, text or test id); run the test to prove it.

**`was fireShortcut(..); pressKey also reaches the focused component and an open dialog`.** `page.pressKey(..)` dispatches the key from the focused component, or from an open dialog, where `fireShortcut` only fired the shortcut on the UI. Run the test on Browserless and Playwright: when it passes, delete the marker. When a dialog now reacts to the key (Escape closes it), check what the test meant and adjust it.

**`was a scan of column <col>; withCellText(text) matches any column: name the column, withCellText(header, text), if another column can hold the text`.** The test scanned one column's cells for a text; `row().withCellText(text)` looks in every column. Read the view: when no other column can hold that text, delete the marker. Otherwise find column `<col>`'s header (the view's `addColumn(..).setHeader(..)`, or `grid.getColumnHeaders()`) and write `row().withCellText("<header>", text)`, then delete the marker. Run the test to prove the row is found.

**`Browserless-only pattern: @Transactional ...`.** The test's transaction reaches the views only on Browserless. Keep it, with `// Escape Hatch kept: test data rolled back by @Transactional`, unless the test can commit its data and clean up after itself; then remove `@Transactional` and `@RequiresTestingTool(TestingTool.BROWSERLESS)`. Cleaning up:
- Clean every table the test writes to, through the view and through services the test or its fixtures call. A missed table passes per class and breaks later classes, and with a reused Testcontainers database, later runs too.
- A fixture that loads an entity and calls a method on it relied on the test's open session: run it in a transaction of its own (`TransactionTemplate`).
- For a base class whose subclasses all write to the same tables, snapshot those tables once and restore them after each test, instead of each test cleaning up its own rows.
- Step 3's full-suite run on both Testing Tools proves the cleanup.

**`Browserless-only pattern: a Spring context without @SpringBootTest ...`.** A narrow `@ContextConfiguration` has no web server for the browser Testing Tools. To run everywhere: replace `@UnifiedUITest`, `@ContextConfiguration` and `@ExtendWith(SpringExtension.class)` with `@UnifiedSpringBootTest`, mock the beans the narrow context left out with `@MockitoBean`, and remove `@RequiresTestingTool(TestingTool.BROWSERLESS)`. Keep it when the full application context can't start in the test.

**`one Spring context per class: N test classes (...) declare their own @ServiceConnection field ...`** (on the field). Each container field is part of Spring's context cache key, so each listed class starts its own Spring context and embedded server, on Browserless too. Write one abstract base class (`UiTestBase`) holding `@UnifiedSpringBootTest`, the profile (`@ActiveProfiles`) and one container field; make the listed classes extend it, delete their own copies of those, and delete the marker. A class whose `@MockitoBean`s, `@Import`s or properties differ still starts its own context: move what the classes share to the base, and leave such a class out only when its setup must differ. Run the listed classes on Browserless: `Tomcat started on port` should appear once.

**`not migrated: this test uses the Browserless application or user context ...`.** The recipe left the whole class as it is: a test opens a second user or window from the context API. Use `page.openWindow()` for another window of the same user and `page.openAsUser(name, roles...)` for another user, remove the context fields and their `@BeforeEach`/`@AfterEach`, then convert the rest by hand the way the recipe converts the others in this project.

**`not migrated: this class passes itself (this) to a page object or helper ...`.** The recipe left the whole class as Browserless Test. Make the page object or helper take a `UnifiedPage` and use the Unified API inside it, then convert the test class by hand the way the recipe converts the others in this project (compare a migrated class), passing `page` instead of `this`.
