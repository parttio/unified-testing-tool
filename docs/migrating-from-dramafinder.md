# Migrating from Dramafinder

The `unified-test-rewrite` module turns Dramafinder 1.x and 2.x tests into Unified UI Tests with [OpenRewrite](https://docs.openrewrite.org/). It rewrites your test sources and `pom.xml` in place, so review the result with `git diff`. Code it can't convert keeps running on Playwright through the Escape Hatch, with a Migration Marker (`// TODO unified: ...`) on each statement that needs a person, or an agent, to look at it. Decided in [ADR 0006](adr/0006-dramafinder-migration-recipes.md), which follows the TestBench and Browserless migrations ([ADR 0004](adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md), [ADR 0005](adr/0005-browserless-migration-recipes.md)).

!!! note "Alpha"
    The Migration Recipes are published to Maven Central with each alpha. A recipe migrates to the Unified API of its own version, and that API can still change between alphas, so use the recipe and the Unified API from the same release.

## Before and after

The demo's `EditPersonDramafinderTest`, and what the recipe makes of it. CI checks that the recipe still produces exactly this, and runs the result on Browserless, Playwright and Selenium: nothing is left behind the Escape Hatch, so the test now runs on every Testing Tool. The Migration Markers left in it are checks for a person: they don't stop the test from running.

=== "Before (Dramafinder)"

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/EditPersonDramafinderTest.java:test"
    ```

=== "After (Unified API)"

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/EditPersonDramafinderMigratedTest.java:test"
    ```

## Run it

!!! warning "Vaadin 25.3 first"
    The migrated tests need Java 21, Vaadin 25.3 and, for `@UnifiedSpringBootTest`, Spring Boot 4.1. On an older Vaadin the recipe migrates the build all the same and leaves a Migration Marker at the top of `pom.xml`; the tests fail when they run. Move the application to Vaadin 25.3 before you run the recipe.

Commit or stash your work first: the recipe changes files in place.

=== "Maven"

    ```bash
    mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
      -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha6 \
      -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.MigrateDramafinderToUnified
    ```

    The plugin version is the one whose OpenRewrite the recipes are built and tested against.

    To set options, declare your own recipe in a `rewrite.yml` at the project root and activate it instead:

    ```yaml
    type: specs.openrewrite.org/v1beta/recipe
    name: com.example.MigrateDramafinder
    recipeList:
      - org.vaadin.addons.unified.rewrite.MigrateDramafinderToUnified:
          addBrowserless: true
          addAgentSkill: true
    ```

    ```bash
    mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
      -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha6 \
      -Drewrite.activeRecipes=com.example.MigrateDramafinder
    ```

=== "Gradle"

    ```kotlin
    plugins {
        id("org.openrewrite.rewrite") version "7.39.0"
    }
    rewrite {
        activeRecipe("org.vaadin.addons.unified.rewrite.MigrateDramafinderToUnified")
    }
    dependencies {
        rewrite("org.vaadin.addons:unified-test-rewrite:0.1.0-alpha6")
    }
    ```

    ```bash
    gradle rewriteRun
    ```

    As with Maven, the plugin version is the one whose OpenRewrite the recipes are built and tested against.

    The build changes are Maven only: on Gradle, add the dependencies by hand (see **The build** below). Without a Maven build the recipe can't read the Dramafinder version from it, and finds it from the classpath instead (see **Dramafinder 1.x**).

| Option | Default | Effect |
|---|---|---|
| `addBrowserless` | `false` | Also adds the Browserless adapter, `unified-test-browserless`, and `browserless-test-spring` in Spring Boot projects |
| `addSelenium` | `false` | Also adds the Selenium adapter, `unified-test-selenium` |
| `addAgentSkill` | `false` | Also writes the agent skill that resolves Migration Markers (see below) |

Releases up to alpha3 ignore the options a `rewrite.yml` sets, without a warning, and run the recipe with the defaults ([#353](https://github.com/parttio/unified-testing-tool/issues/353)); alpha4 fixed it. With those releases, add the adapter dependencies by hand, and copy the skill from `META-INF/unified/skills/resolve-migration-markers/SKILL.md` in the `unified-test-rewrite` jar to `.claude/skills/resolve-migration-markers/SKILL.md`.

Without another adapter the migrated suite keeps running on Playwright only, as before; adding one is the step that makes it run elsewhere (see **Running on the other Testing Tools**).

### Which classes it skips

The recipe leaves a test class it can't convert entirely as it is (see **What it leaves for you**). To see every such class and why in one pass, before migrating, run the search recipe `FindTestClassesToMigrate` as a dry run; it changes no file:

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:<version> \
  -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.FindTestClassesToMigrate \
  -Drewrite.exportDatatables=true
```

`target/rewrite/rewrite.patch` (one per module) marks every Dramafinder, Browserless and TestBench test class: `/*~~(migrates)~~>*/`, or `/*~~(skipped: overrides setupTest() of AbstractBasePlaywrightIT, calls getUrl() of SpringPlaywrightIT)~~>*/` naming the base class members that keep it from migrating, and with `exportDatatables` the file `FindTestClassesToMigrate$TestClasses.csv`, in `target/rewrite/datatables/<date-time>/` under the directory you ran Maven in, lists them one row per class with their reasons. Fix what blocks the skipped ones, run it again until none is left, then run the migration.

A common blocker is a `@RegisterExtension` `TestWatcher` in your copy of `SpringPlaywrightIT` that takes a screenshot or saves a trace from `page` when a test fails: `skipped: uses screenshotOnFailure of MyPlaywrightIT, which uses the test base; the Unified API writes failure evidence itself: remove it`. Delete that field: every failed test already gets a screenshot (browser) or component tree (Browserless), `user-steps.txt` and the Findable View. The recipe doesn't delete it for you. An extension that uses `page` for anything else keeps the plain reason: rewrite it without the Dramafinder page, or convert the class by hand.

On Gradle, make it the `activeRecipe` and run `gradle rewriteDryRun`: the patch is `build/reports/rewrite/rewrite.patch`.

## What it converts

| Dramafinder | Unified API |
|---|---|
| `extends SpringPlaywrightIT` (with or without `@SpringBootTest`) | `@UnifiedSpringBootTest` |
| `extends AbstractBasePlaywrightIT` | `@UnifiedUITest`; `getUrl()` goes: the base URL is the `vaadin.testing.base-url` setting ([test setup](spec/02-test-setup.md)) |
| `getView()` returning `"people"` | each test that doesn't navigate first starts with `page.navigate(PeopleView.class)`, when exactly one `@Route("people")` is among the migrated sources; else, with no such view, several, or one under a layout with a `@RoutePrefix`, `page.navigate("people")` |
| `@EnabledIfSystemProperty(named = "vaadin.testing.tool", ..)` | removed: the run's Testing Tool decides |
| test methods, `@BeforeEach` and helper methods that use the base class | a `UnifiedPage page` parameter; a `@BeforeEach` becomes a call at the start of each test |
| `page.setViewportSize(w, h)`, `page.viewportSize().width` | the size a test starts at becomes `@Viewport(width = w, height = h)`, on the class from a `@BeforeEach`; a later resize `page.setViewport(w, h)`; `viewportSize().width` `page.viewport().width()` ([Viewports](guide/configuration.md#viewports)) |
| `navigate(View.class)`, `assertAt(View.class)`, `assertPath(..)`, `assertQueryParameter(..)` | `page.navigate(View.class)`, `assertThat(page).isAt(View.class)`, `hasPath(..)`, `hasQueryParameter(..)` |
| `page.navigate(getUrl() + "/plan")`, `page.navigate(getUrl() + "/plan?week=" + monday)` | `page.navigate(PlanView.class)`, `page.navigate(PlanView.class, QueryParameters.of("week", String.valueOf(monday)))` (no `String.valueOf` for a `String`), when exactly one `@Route("plan")` is among the migrated sources, as for `getView()`; else, with no such view, several, a path computed at run time, or more than one query parameter, the path after the base URL: `page.navigate("plan?week=" + monday)` |
| `TextFieldElement.getByLabel(page, "Name")`, `getByText`, `getById`, `getByTestId`, `…Containing` | `page.find(UnifiedTextField.class).withLabel("Name")`, `withText`, `withId`, `withTestId`, `…Containing` |
| `find(page, TextFieldElement.class).withLabel("Name").single()` | `page.find(UnifiedTextField.class).withLabel("Name")`: the Wrapper, which checks on every use that exactly one component matches |
| query `first()` / `get(i)` | the same lookup, with a `was first()` Migration Marker / `all().get(i)` |
| `page.locator("#next").click()`, `locator("[data-testid=card]")`, `getByTestId("card")` | `page.find(UnifiedButton.class).withId("next").click()`, `withTestId("card")`: the Wrapper of the component the application's sources give that id (see below) |
| `assertThat(page.locator("#x"))`'s `containsText(s)`, `hasText(s)`, `hasAttribute("disabled", "")`, `not().hasAttribute("disabled", ..)` or `not().hasAttribute("aria-disabled", "true")`, `isAttached()` | `assertThat(page.find(W.class).withId("x"))`'s `hasTextContaining(s)`, `hasText(s)`, `isDisabled()`, `isEnabled()`, `exists()` |
| `assertThat(locator).hasClass("c")` or `hasClass(Pattern.compile(".*c.*"))`, `assertThat(locator).not().isVisible()` | `assertThat(w.withClassName("c")).exists()`, `assertThat(w.visible()).doesNotExist()` |
| `NotificationElement.getByText(page, s)` (a substring, the first match) | `withTextContaining(s)`, with a Migration Marker |
| element actions: `setValue`, `click`, `check`, `closeWithEscape`, ... | the Wrapper's: `setValue`, `click`, `check`, `close`, ... |
| element assertions: `assertValue`, `assertOpen`, `assertEnabled`, `assertChecked`, `assertContent`, ... | retrying `assertThat(w).hasValue(..)`, `isOpen()`, `isEnabled()`, `isChecked()`, `hasTextContaining(..)`, ... |
| `EmailFieldElement`, `PasswordFieldElement` | `UnifiedEmailField`, `UnifiedPasswordField`, converted as a `TextFieldElement` is |
| `ConfirmDialogElement`: `getByHeaderText(page, s)`, `confirm()`, `reject()`, `cancel()`, `getMessage()`, `assertMessage(s)` | `page.find(UnifiedConfirmDialog.class).withHeaderTitle(s)`, `confirm()`, `reject()`, `cancel()`, `getText()`, `assertThat(d).hasText(s)`; the rest as a `DialogElement` |
| `LoginFormElement.get(page)`, `LoginOverlayElement.get(page)`, `getByTitle(page, s)`: `login(u, p)`, `isOpen()`, `isErrorVisible()`, `assertOpen()`, `assertClosed()`, `assertErrorVisible()`, `assertNoError()`, `assertHeaderTitle(s)` | `page.find(UnifiedLogin.class)`, `withTitle(s)`: `login(u, p)`, `isOpen()`, `isError()`, `assertThat(l).isOpen()`, `isClosed()`, `hasError()`, `hasNoError()`, `hasTitle(s)` |
| `SideNavigationItemElement.getByLabel(page, s)`: `navigate()`, `isExpanded()`, `assertExpanded()`, `assertCollapsed()`, `assertCurrent()`, `assertNotCurrent()` | `page.find(UnifiedSideNavItem.class).withLabel(s)`: `click()`, `isExpanded()`, `assertThat(i).isExpanded()`, `isCollapsed()`, `isCurrent()`, `isNotCurrent()`. `toggle()` stays Dramafinder: write `expand()` or `collapse()` by hand ([Side navigation](guide/components.md#side-navigation)) |
| `AppLayoutElement.get(page)`: `isDrawerOpened()`, `openDrawer()`, `closeDrawer()`, `toggleDrawer()`, `closeDrawerWithEscape()`, `setDrawerOpened(true)` / `(false)`, `assertDrawerOpened()`, `assertDrawerClosed()`, `isOverlayMode()`, `assertOverlayMode()`, `assertNotOverlayMode()` | `page.find(UnifiedAppLayout.class)`: the same names, `closeDrawer()` for `closeDrawerWithEscape()`, `openDrawer()` / `closeDrawer()`, `assertThat(l).isDrawerOpened()`, `isDrawerClosed()`, `isOverlay()`, `assertThat(l).isOverlay()`, `isNotOverlay()`; an overlay check adds `@RequiresBrowser` to the test. `setDrawerOpened(b)` with a variable, `clickBackdrop()` and the locator getters stay Dramafinder ([App layout](guide/components.md#app-layout)) |
| a local `TextFieldElement name = ...` | `UnifiedTextField name = page.find(..)...` |
| `GridElement.get(page)` | `page.find(UnifiedGrid.class)` |
| `grid.findRow(i).orElseThrow()` | `grid.row(i)` |
| `grid.findRowIndexesWithColumnText(0, s).get(0)` | `grid.row().withCellText(s).getIndex()`, with a Migration Marker: any column matches now |
| `row.getCell(col).getText()`, `row.getCell("Header").assertText(s)`, `row.getCell(col).click()` | `row.getCellText(col)`, `assertThat(row).hasCellText("Header", s)`, `row.click()` with a Migration Marker |
| `MessageInputElement.get(page).typeAndSubmit(s)` | `page.find(UnifiedMessageInput.class).send(s)` |
| `MessageListElement.get(page)`, `assertMessageCount(n)`, `assertEmpty()` | `page.find(UnifiedMessageList.class)`, `assertThat(list).hasMessageCount(n)`, `isEmpty()` |
| `MessageInputElement.get(page.getByTestId("dock"))`, `new MessageListElement(page.locator("#chat"))` | `page.find(UnifiedContainer.class).withTestId("dock").find(UnifiedMessageInput.class)`: the first one inside the component; `page.find(UnifiedMessageList.class).withId("chat")`: the component itself |
| `input.focus()` | `input.element().focus()` |
| `assertThat(list.getMessage(i)).containsText(s)`, `hasText(s)` | `assertThat(list.message(i)).hasTextContaining(s)`, `hasText(s)`: both count from 0 |
| `AutoWait.waitForVaadinIdle(page)`, `page.waitForLoadState()`, `waitForGridToStopLoading()`, `scrollToRow(i)` | removed: Auto-Wait |

A lookup converts only when its Wrapper has every call the test makes on what it finds, and only on the test's page (a lookup inside a `Locator` or another element stays, except a chat element's `get(..)` inside an id or test id locator). The Wrappers the recipe knows today: TextField, TextArea, Button, Notification, Checkbox, Dialog, ConfirmDialog, LoginForm and LoginOverlay, Grid, MessageInput and MessageList, SideNavigation and SideNavigationItem, AppLayout; other components stay behind the Escape Hatch. The full list of what the recipe writes is in [Appendix: mapping](spec/appendix-mapping.md#dramafinder-calls-the-migration-recipe-converts).

A test class that uses a base class member the recipe doesn't convert (`loginAs(..)`, `newPageAs(..)`, `getBrowser()`, an override of `prepareBrowserContext(..)`, a `getView()` that computes its view) is left as it is, with a Migration Marker above the class naming those members (`// TODO unified: not migrated: calls loginAs(..) of SpringPlaywrightIT; ...`): convert those by hand (`openAsUser(..)` for another user, [users](spec/11-users.md)), then run the migration again. `getUrl()` is one of them only where the test does something else with it than `page.navigate(getUrl() + ..)`, such as keep it in a variable. A `setupTest()` override that seeds data and then calls `super.setupTest()` is not one of them: it becomes a plain `@BeforeEach` without the `super.setupTest()`, since each test now navigates itself.

A raw Playwright locator on an id or a test id, `locator("#x")`, `locator("[data-testid=x]")` or `getByTestId("x")`, becomes a lookup `withId("x")` or `withTestId("x")`. The recipe reads its Wrapper from the application's sources, where a view calls `setId("x")` or `getElement().setAttribute("data-testid", "x")` with a literal on a component: a `Button` becomes `UnifiedButton` (`click()`), a `TextField`, `TextArea`, `EmailField` or `PasswordField` `UnifiedTextField`, `UnifiedTextArea`, `UnifiedEmailField` or `UnifiedPasswordField` (`fill(s)` is `setValue(s)`, `inputValue()` `getValue()`), a `Checkbox` `UnifiedCheckbox` (`check()`, `uncheck()`), and any other component `UnifiedContainer` (`click()`). `PlaywrightAssertions.assertThat(locator)`'s `isVisible()`, `isEnabled()`, `isDisabled()`, `hasValue(..)` and `isChecked()` become the retrying assertions of the same name. Its text, attribute, class and presence checks become Unified ones too: `containsText(s)` is `hasTextContaining(s)`, `hasText(s)` `hasText(s)`, `hasAttribute("disabled", "")` `isDisabled()`, `not().hasAttribute("disabled", ..)` and `not().hasAttribute("aria-disabled", "true")` `isEnabled()`, `isAttached()` `exists()`; `hasClass("c")` or `hasClass(Pattern.compile(".*c.*"))` adds `withClassName("c")` to the lookup and checks `exists()`, and `not().isVisible()` adds `visible()` and checks `doesNotExist()`. A text check with a `Pattern`, any other `hasClass` pattern and other `not()` checks keep their Migration Marker. When no source sets the id, or several set it on components of different classes, the lookup is `UnifiedContainer`, which finds any component, with a Migration Marker to name the component's own Wrapper. The recipe doesn't guess a Wrapper from the call (`fill` on a text field): a wrong guess fails the test. Other selectors, other calls (`press(..)`, `isHidden()`) and a locator the test hands on stay behind the Escape Hatch.

### Dramafinder 1.x

Dramafinder 1.x and 2.x share their class and method names, but not what a lookup finds: a 1.x `getByLabel` or `getByText` matched a case-insensitive substring of the label, `aria-label` or placeholder and took the first match, where 2.x matches the whole value and fails on several. So the recipe first finds the version: the one your Maven build resolves; else the API on the classpath; else the API of the base class your tests were compiled against. A version it can't find counts as 1.x, since a wrong 1.x guess only adds Migration Markers.

In a 1.x project, `getByLabel(page, s)` becomes `withAccessibleNameContaining(s)` and `getByText(page, s)` `withTextContaining(s)`, each with a Migration Marker to tighten it to `withLabel`/`withText` once the test passes. A 1.x `getByHeaderText` stays behind the Escape Hatch: the Unified API has no substring header filter.

Before 2.1, a Dramafinder lookup also searched the shadow roots of the application's own templates, so a test could find a `LitTemplate`'s button without saying it was in one. The converted lookup doesn't: no Unified lookup enters a Template by itself ([Templates](guide/finding-components.md#templates)). Nothing in the test shows which lookups did this, so the recipe can't mark them. Run the migrated tests: such a lookup fails with `Found none`, and its `Nearby` line names the Template and the lookup that enters it.

1.x projects copied `SpringPlaywrightIT` from Dramafinder's template into their own package. The recipe recognises such a copy (an abstract class extending `AbstractBasePlaywrightIT` with `@SpringBootTest`), migrates the tests that extend it as if they extended Dramafinder's, gives each of them the copy's class annotations (`@ActiveProfiles`, `@Import`, `@SpringBootTest`'s `properties`, ...) and the copy's members it needs as its own (the fields and helpers it uses, the copy's `@BeforeEach`, `@AfterEach` and `@RegisterExtension` members), and deletes the copy once nothing extends it any more. When the copy isn't among the migrated sources (another module, a jar), the recipe can't read those annotations' arguments: it leaves a Migration Marker on each test naming them instead.

=== "Before (Dramafinder 1.x)"

    ```java
    class EditPersonDramafinder1Test extends SpringPlaywrightIT { // the project's copy

        @Override
        public String getView() {
            return "people";
        }

        @Test
        void editsAPersonFromTheGrid() {
            // ...
            TextFieldElement.getByLabel(page, "Name").setValue("Alan M. Turing");
            ButtonElement.getByText(page, "Save").click();
            // ...
        }
    }
    ```

=== "After (Unified API)"

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/EditPersonDramafinder1MigratedTest.java"
    ```

### The build

With Maven, the recipe imports `unified-test-bom`, adds `unified-test-playwright` as a test dependency, and `unified-test-spring` when the project has a direct Spring Boot dependency. With `addBrowserless` it also adds `unified-test-browserless`, and `com.vaadin:browserless-test-spring` in a Spring Boot project: a `@UnifiedSpringBootTest` fails at once on Browserless without it, and the adapter doesn't bring it (the project's Vaadin BOM manages its version). It removes the direct `dramafinder` dependency (the Playwright adapter brings Dramafinder), and the version of a `browserless-test-spring` the project already has, when its Vaadin BOM manages one. It also gives each `maven-failsafe-plugin` its own evidence directory, `target/failsafe-reports/unified-evidence`, so Failsafe's run doesn't write over Surefire's ([Surefire and Failsafe in one build](guide/ci-setup.md#surefire-and-failsafe-in-one-build)).

So the migrated project runs on Dramafinder 2.2.4, the version `unified-test-bom` manages, whichever version it had. For a 1.x project that is a major version: the class and method names are the same, so the code the recipe leaves behind the Escape Hatch still compiles, but its lookups now follow 2.x rules ([Dramafinder 1.x](#dramafinder-1x)). A `getByLabel` or `getByText` that matched a substring, or took the first of several matches, now fails; so does a 1.x `getByHeaderText`, which the recipe always leaves behind the Escape Hatch. Run the Playwright tests once after the migration, and fix such a lookup, ideally by converting it to a Wrapper.

## What it leaves for you

Code with no Unified equivalent keeps working on Playwright: the base class's `page` becomes the Playwright `Page` from `page.unwrap(Page.class)`, and Dramafinder elements and raw Playwright calls keep using it:

```java
@Test
@RequiresTestingTool(TestingTool.PLAYWRIGHT)
void savesTheProfile(UnifiedPage page) {
    Page playwright = page.unwrap(Page.class);
    page.navigate(ProfileView.class);
    // TODO unified: Dramafinder Escape Hatch: playwright.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click()
    playwright.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
}
```

`@RequiresTestingTool(PLAYWRIGHT)` skips such a test on the other Testing Tools. A fully converted test has no Capability and runs everywhere.

| Migration Marker | What to do |
|---|---|
| `Dramafinder Escape Hatch: <call>` | Rewrite the call with the Unified API: a Dramafinder lookup or a raw Playwright locator (`getByRole`, `getByLabel`, `locator("vaadin-…")`) names a component a Wrapper finds. Keep it for routes, tracing, JavaScript and CSS into a component's shadow DOM |
| `Dramafinder Escape Hatch: new X(..), a custom element: write a Wrapper for it (ADR 0003)` | `X` extends Dramafinder's `VaadinElement`: write a [Wrapper](guide/custom-wrappers.md) with its methods |
| `was 1.x getByLabel(..), ...; tighten it to withLabel(..) once the test passes` | Replace the `…Containing` filter with the exact one and run the test |
| `was findRowIndexesWithColumnText(n, ..).get(0): any column matches now; ...` | Name the column: `withCellText("Header", text)` |
| `was locator("#x"): no view in the sources sets this id; UnifiedContainer finds any component, ...` | Find the component that has the id and, when it has a Wrapper, use it instead of `UnifiedContainer` |
| `was getCell(..).click(); a row's click() clicks the row, not that cell` | Fine when the cell holds nothing clickable; else find the component in the cell, `row.cell("Header").find(..)` |
| `was first(); the lookup fails when several components match`, `was getByText(..), a substring and first match; ...` | Run the test. If it fails with "found 2", add the filter that picks the intended one; the failure lists both |
| `the Unified API needs Vaadin 25.3 or newer`, at the top of `pom.xml` | Move the application to Vaadin 25.3, then delete the marker |

Once done, a marker is gone, or it reads `// Escape Hatch kept: <reason>`.

## Running on the other Testing Tools

A migrated test that needs nothing Playwright-only can run on Browserless and Selenium too. Add the adapter (`addBrowserless`, `addSelenium`, or the dependency by hand; for Browserless in a Spring Boot project, by hand also add `com.vaadin:browserless-test-spring` in test scope) and run with `-Dvaadin.testing.tool=browserless` or `selenium`. Selenium is a real browser too, so tests that pass on Playwright usually pass there. Browserless runs in the test JVM without a browser ([limitations](spec/09-capabilities.md#limitations)); expect a few failures on the first run:

- "Visible" means `setVisible(true)` on the server: a component hidden by CSS counts as visible. So an action on
  it passes on Browserless and rightly fails in a browser: `setValue(..)` on a date picker whose input CSS hides
  (one that opens as a calendar overlay only) fails on Playwright with `Found 1, but hidden`.
- The page starts at the `""` route, where a browser starts blank, and `navigate(..)` to the view already shown
  keeps that view: its `beforeEnter` (or `setParameter`) runs again on the same instance, where a browser loads a
  fresh page with new views. A test whose first step is `navigate(..)` to the view at `""`, or to where `""`
  forwards, re-enters it. A view that isn't ready for that fails there and not in a browser; that is a bug a user
  can hit too, by navigating to the view they are on.
- No CSS, layout or client-side JavaScript: a test that depends on them belongs on a real browser, `@RequiresBrowser`.
- A substring lookup carried over from Dramafinder 1.x can find a second component the browser didn't show yet; tighten it as its Migration Marker says.

## The agent skill

The same `resolve-migration-markers` skill works through the Dramafinder Migration Markers: it converts raw Playwright calls and Dramafinder lookups into Wrappers, writes Wrappers for custom elements, tightens 1.x substring lookups, or keeps the Escape Hatch and replaces the marker with `// Escape Hatch kept: <reason>`. It removes `@RequiresTestingTool(PLAYWRIGHT)` only once the test passes on both Playwright and Browserless. Add `addAgentSkill: true` to the recipe's options, then, in Claude Code, in the migrated project:

```text
/resolve-migration-markers
```

Only you can start it. The skill is user-invoked (`disable-model-invocation: true`), so it adds nothing to an agent's context in a session that doesn't migrate, but an agent can't start it either: one driving the migration end to end stops at this step. Type the command yourself, or tell the agent to read `.claude/skills/resolve-migration-markers/SKILL.md` and follow its steps.

Every migration, with or without `addAgentSkill`, also runs `org.vaadin.addons.unified.rewrite.AddUnifiedTestingSkill`: it writes `.claude/skills/write-unified-ui-tests/SKILL.md`, the skill agents follow to write and change Unified UI Tests (and which `resolve-migration-markers` defers to for the shape of the code it writes), and points to it from a marked section of `AGENTS.md` (of `CLAUDE.md`, when the project has that and no `AGENTS.md`). It replaces an older copy of the skill, keeps one you edited by hand (it then warns), installs nothing but a warning on the module's build file when the build root has no build file, `AGENTS.md` or `CLAUDE.md`, and can run alone on a project that wasn't migrated ([spec 12, The agent skill](spec/12-readable-tests.md#the-agent-skill)).

How it is evaluated, and what it scores: [Migrating from TestBench](migrating-from-testbench.md#the-agent-skill).
