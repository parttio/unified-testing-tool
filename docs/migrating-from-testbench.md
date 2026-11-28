# Migrating from TestBench

The `unified-test-rewrite` module turns TestBench (Selenium) tests into Unified UI Tests with [OpenRewrite](https://docs.openrewrite.org/). It rewrites your test sources and `pom.xml` in place, so review the result with `git diff`. Code it can't convert keeps running on Selenium through the Escape Hatch, with a Migration Marker (`// TODO unified: ...`) on each statement that needs a person, or an agent, to look at it. Decided in [ADR 0004](adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md).

!!! note "Alpha"
    The Migration Recipes are published to Maven Central with each alpha. A recipe migrates to the Unified API of its own version, and that API can still change between alphas, so use the recipe and the Unified API from the same release.

## Before and after

The demo's `EditPersonTestBenchTest`, and what the recipe makes of it. CI checks that the recipe still produces exactly this, and runs the result on Browserless, Playwright and Selenium.

=== "Before (TestBench)"

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/EditPersonTestBenchTest.java:test"
    ```

=== "After (Unified API)"

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/EditPersonMigratedTest.java:test"
    ```

## Run it

!!! warning "Vaadin 25.3 first"
    The migrated tests need Java 21, Vaadin 25.3 and, for `@UnifiedSpringBootTest`, Spring Boot 4.1. On an older Vaadin the recipe migrates the build all the same and leaves a Migration Marker at the top of `pom.xml`; the tests fail when they run. Move the application to Vaadin 25.3 before you run the recipe.

Commit or stash your work first: the recipe changes files in place.

=== "Maven"

    ```bash
    mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
      -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha6 \
      -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified
    ```

    The plugin version is the one whose OpenRewrite the recipes are built and tested against.

    To set options, declare your own recipe in a `rewrite.yml` at the project root and activate it instead:

    ```yaml
    type: specs.openrewrite.org/v1beta/recipe
    name: com.example.MigrateTestBench
    recipeList:
      - org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified:
          addPlaywright: true
          includePageObjects: true
          addAgentSkill: true
    ```

    ```bash
    mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
      -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha6 \
      -Drewrite.activeRecipes=com.example.MigrateTestBench
    ```

=== "Gradle"

    ```kotlin
    plugins {
        id("org.openrewrite.rewrite") version "7.39.0"
    }
    rewrite {
        activeRecipe("org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified")
    }
    dependencies {
        rewrite("org.vaadin.addons:unified-test-rewrite:0.1.0-alpha6")
    }
    ```

    ```bash
    gradle rewriteRun
    ```

    As with Maven, the plugin version is the one whose OpenRewrite the recipes are built and tested against.

    The build changes are Maven only: on Gradle, add the dependencies by hand (see **The build** below).

| Option | Default | Effect |
|---|---|---|
| `addPlaywright` | `false` | Also adds the Playwright adapter, `unified-test-playwright` |
| `includePageObjects` | `false` | Also converts page objects (see below) |
| `addAgentSkill` | `false` | Also writes the agent skill that resolves Migration Markers (see below) |
| `springBootTest` | `true` | In a Spring Boot app, a test class without `@SpringBootTest` starts the app itself (`@UnifiedSpringBootTest`) instead of testing a server the build starts; `false` keeps it on that server (see [A Spring Boot app tested against a server the build starts](#a-spring-boot-app-tested-against-a-server-the-build-starts)) |

Releases up to alpha3 ignore the options a `rewrite.yml` sets, without a warning, and run the recipe with the defaults ([#353](https://github.com/parttio/unified-testing-tool/issues/353)); alpha4 fixed it. With those releases, add the adapter dependencies by hand, and copy the skill from `META-INF/unified/skills/resolve-migration-markers/SKILL.md` in the `unified-test-rewrite` jar to `.claude/skills/resolve-migration-markers/SKILL.md`.

Then run your tests on each Testing Tool (`-Dvaadin.testing.tool=browserless`, `playwright` or `selenium`) and work through the Migration Markers, by hand or with the agent skill.

### Which classes it converts

The recipe converts every class that is or extends `BrowserTestBase`; what it can't convert inside a class gets a Migration Marker instead. To list those classes before migrating, run the search recipe `FindTestClassesToMigrate` as a dry run; it changes no file:

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:<version> \
  -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.FindTestClassesToMigrate \
  -Drewrite.exportDatatables=true
```

`target/rewrite/rewrite.patch` (one per module) marks every TestBench, Browserless and Dramafinder test class `/*~~(migrates)~~>*/`, and with `exportDatatables` the file `FindTestClassesToMigrate$TestClasses.csv`, in `target/rewrite/datatables/<date-time>/` under the directory you ran Maven in, lists them one row per class. A project with Browserless or Dramafinder tests too sees which of those their migrations would skip.

A TestBench class also lists the application's page objects and custom elements it uses, each `converts with includePageObjects` or `stays TestBench (Escape Hatch)` with the reason, such as `LoginViewElement: stays TestBench (Escape Hatch), a custom element extending LoginOverlayElement: write a Wrapper (ADR 0003)`. A class that uses one staying TestBench is `migrates partly`: its tests that reach it run on Selenium only. In a Spring Boot app, the class that takes the test annotation without having `@SpringBootTest` names it in the `Annotation` column (and the patch): `@UnifiedSpringBootTest`, decided by the option `springBootTest`, `false` keeping `@UnifiedUITest`.

On Gradle, make it the `activeRecipe` and run `gradle rewriteDryRun`: the patch is `build/reports/rewrite/rewrite.patch`.

## What it converts

| TestBench | Unified API |
|---|---|
| `extends BrowserTestBase`, `TestBenchTestCase` or `ParallelTest`; `@BrowserTest` | `@UnifiedSpringBootTest` (from `@SpringBootTest`, or, in a module with a `@SpringBootApplication`, without it) or `@UnifiedUITest`; `@Test` with a `UnifiedPage page` parameter |
| `getDriver().get("http://localhost:" + port + "/people")` | `page.navigate(PeopleView.class)` when one view has the route, else `page.navigate("people")` |
| `driver.get(APP_URL + "people")` on any `WebDriver`, with `APP_URL` a constant `"http://localhost:8080/"` (or `127.0.0.1`) | the same; the constant goes when nothing else reads it. A URL the recipe can't read keeps the call, with a Migration Marker, whatever its form: a parameter, a local, a method call, a field that isn't a constant |
| `$(TextFieldElement.class).withLabel("Name").single()` | `page.find(UnifiedTextField.class).withLabel("Name")`: the Wrapper, which checks on every use that exactly one component matches |
| `$(EmailFieldElement.class)`, `$(PasswordFieldElement.class)` | `page.find(UnifiedEmailField.class)`, `page.find(UnifiedPasswordField.class)`, with the same filters and calls as a text field |
| `$(ConfirmDialogElement.class).single()`, with `getConfirmButton().click()`, `getRejectButton().click()`, `getCancelButton().click()`, `getHeaderText()`, `getMessageText()` | `page.find(UnifiedConfirmDialog.class)`, with `confirm()`, `reject()`, `cancel()`, `getHeaderTitle()`, `getText()`: each button by its role, whatever its text ([Dialogs and notifications](guide/components.md#dialogs-and-notifications)) |
| `$(LoginOverlayElement.class)`, `$(LoginFormElement.class)`, with `isOpened()`, `getTitle()`, `getDescription()`, `forgotPassword()` | `page.find(UnifiedLogin.class)`, with `isOpen()`, `getTitle()`, `getDescription()`, `forgotPassword()`. Signing in through `getUsernameField()`, `getPasswordField()` and `submit()` stays TestBench: write `login(username, password)` by hand ([Login](guide/components.md#login)) |
| `$(AnchorElement.class).withText("Users").single()` | `page.find(UnifiedLink.class).withText("Users")`, which finds a `RouterLink` or an `Anchor`; its `click()` follows the link on every Testing Tool ([Links](guide/components.md#links)) |
| `$(SideNavItemElement.class)`, with `click()`, `navigate()`, `getLabel()`, `getPath()`, `isExpanded()`, `isCurrent()` | `page.find(UnifiedSideNavItem.class)`, with `click()` for both, `getLabel()`, `getPath()`, `isExpanded()`, `isCurrent()`. Find an item by its label with `withLabel(..)`, which the recipe can't write from TestBench's `getItemByLabel(..)` ([Side navigation](guide/components.md#side-navigation)) |
| `$(AppLayoutElement.class)`, with `isDrawerOpened()`, `isOverlay()`, `setDrawerOpened(true)` / `(false)`, `getDrawerToggle().click()`; `$(DrawerToggleElement.class)...click()` | `page.find(UnifiedAppLayout.class)`, with `isDrawerOpened()`, `isOverlay()` and `@RequiresBrowser` on the test, `openDrawer()` / `closeDrawer()` with a Migration Marker (TestBench set the property; these click the DrawerToggle, or press Escape on an opened overlay, as a user does), `toggleDrawer()`. `getContent()`, `isDrawerFirst()` and `setDrawerFirst(..)` stay TestBench ([App layout](guide/components.md#app-layout)) |
| `testBench().resizeViewPortTo(w, h)`, `window().setSize(new Dimension(w, h))`, `maximize()`, `window().getSize()` | the size a test starts at becomes `@Viewport(width = w, height = h)`, on the class from a `@BeforeEach`; a later resize `page.setViewport(w, h)`; `getSize().getWidth()` `page.viewport().width()`. A window size, `maximize()` and a driver's `--window-size` get a Migration Marker ([Viewports](guide/configuration.md#viewports)) |
| `withClassName("a", "b")`, or one name with a space | `withClassNames("a", "b")`: the Unified `withClassName` takes one class name, as Flow's `addClassName` does |
| `first()`, `waitForFirst()` | the same lookup, with a `was first()` Migration Marker: the lookup fails when several components match |
| `id("save")` | `withId("save")` |
| `$(SearchBarElement.class)` or `$("search-bar")`, when a `LitTemplate` in the sources has `@Tag("search-bar")` | `page.findTemplate(SearchBar.class)`, and `bar.$(ButtonElement.class).id("new")` on it `bar.find(UnifiedButton.class).withId("new")` ([Templates](guide/finding-components.md#templates)); a text or label filter there gets a Migration Marker |
| `Assertions.assertEquals(x, el.getValue())`, `getText()`, `assertTrue(cb.isChecked())`, ... | retrying `assertThat(el).hasValue(x)`, `hasText(x)`, `isChecked()`, ... |
| a local `TextFieldElement name = ...` | `UnifiedTextField name = ...` |
| `@BeforeEach` and helper methods that use TestBench | helpers taking `UnifiedPage page` (`unifiedPage` when the method has a `page` already), passed by every call, a test's call to another test too; a `@BeforeEach` becomes a call at the start of each test, which declares its `throws` |
| a project base class extending `BrowserTestBase` | a plain base class with `@UnifiedSpringBootTest`; its tests and helpers convert as above, an abstract helper along with its overrides |
| an override of `getDriver()`, or another TestBench base method, and calls to it | the override without `@Override`, taking the page; it and the calls go through the TestBench Escape Hatch, with a Migration Marker |
| `getRunLocallyBrowser()`, `getRunLocallyBrowserVersion()` | `testBench.getRunLocallyBrowser()`, `testBench.getRunLocallyBrowserVersion()` through the TestBench Escape Hatch, with a Migration Marker: the browser `vaadin.testing.selenium.browser` sets (`CHROME`, `FIREFOX`), or `null` without the setting, as without `@RunLocally`; the version is always `""` |
| Grid `getCell("text")`, `getCell(row, col).getText()` | `row().withCellText("text")`, `row(row).getCellText(col)` |
| ComboBox / RadioButtonGroup / MultiSelectComboBox `selectByText`, `getSelectedText`, ... | `selectItem`, `getSelectedItem`, ... |
| DatePicker `setDate`, `getDate` | `setValue`, `getValue` |
| NumberField `setValue("42")` | `setValue(42.0)` (numeric string literals only) |
| `dialog.$(X.class)`, `tabs.getContent().$(X.class)` | `dialog.find(UnifiedX.class)`, `tabs.content().find(UnifiedX.class)` |
| TabSheet `setSelectedTabIndex(t.getTab("Security"))` | `selectTab("Security")` |
| MessageInput `submit(s)`, MessageList `getMessageElements().size()` | `send(s)`, `getMessageCount()` |
| Button `withLabel(..)`, `withCaption(..)` | `withText(..)`, with a Migration Marker suggesting `withAccessibleName(..)` for a button without text |
| JUnit 4: `@Before`, `@Test`, `Assert`, `@Ignore`, `ExpectedException` | JUnit 6 |
| `setDriver(..)`, `getDriver().quit()`, `ScreenshotOnFailureRule`, `@RunWith(SpringRunner.class)` | removed: the Unified API owns the browser and writes failure evidence |

The full mapping is in [Appendix: mapping](spec/appendix-mapping.md).

### Page objects

With `includePageObjects`, a page object that holds the TestBench test (`HasElementQuery`, `TestBenchTestCase`, ...) takes the `UnifiedPage` instead: its lookups become `find(..)`, its methods return Wrappers, and tests pass `page` where they passed `this`. It converts only when every lookup in it, and every use its callers make of what it returns, converts. Otherwise, and without the option, `new LoginPage(this)` becomes `new LoginPage(testBench)` behind the Escape Hatch.

A custom element, an application's own `TestBenchElement` subclass such as `@Element("order-card") OrderCardElement extends DivElement` or `LoginViewElement extends LoginOverlayElement`, stays TestBench, with or without the option. It is custom in any package, `com.vaadin.*` included: only `com.vaadin.testbench.*` and the component elements in `com.vaadin.flow.component.**.testbench` are TestBench's own. Each statement calling one gets a Migration Marker, even one already marked for something else, such as signing in, and a test that reaches one, itself or through a helper of its class hierarchy, gets `@RequiresTestingTool(SELENIUM)`. So does a test whose only TestBench code is in an inherited helper going through the Escape Hatch.

### A Spring Boot app tested against a server the build starts

Vaadin's starters test the app as a server the build starts: an `it` profile runs `spring-boot:start` and `spring-boot:stop` around Failsafe, the `*IT` classes have no `@SpringBootTest`, and each test opens `http://localhost:8080/` and signs in through the login form. On Browserless such a test has no Spring context: the app's Spring views can't be built and `@WithMockUser` has no effect. So in a module with a `@SpringBootApplication` (main or test sources), a TestBench test class without `@SpringBootTest` becomes a `@UnifiedSpringBootTest` ([ADR 0013](adr/0013-migrated-spring-boot-tests-start-the-app.md)): each run starts the app from its Spring context on a random port, on every Testing Tool. The annotation goes on the base class that extended `BrowserTestBase`; its subclasses inherit it. A Migration Marker on it says the tests now start the app themselves.

- The app now runs with its test configuration, not the configuration of the server the build started; a difference between the two can make a test behave differently.
- A field holding a local application URL with a fixed port, such as `APP_URL = "http://localhost:8080/"`, that the recipe kept gets a Migration Marker: navigation through it reaches the server the build starts, not the app the test starts on a random port. So does each `driver.get(url)` the recipe can't turn into `page.navigate(..)`, such as one in a helper taking the URL as a parameter.
- The recipe leaves the pom alone. The `spring-boot:start`/`stop` executions, and their profile, now start a second server nobody tests: remove them by hand.
- A Failsafe profile that ran the tests against a server or browser outside the test gets a Migration Marker, an XML comment after its `<id>` ([ADR 0020](adr/0020-failsafe-profiles-that-test-elsewhere-get-a-marker-no-remote-grid.md)): a profile that starts the app as a native executable or with `exec`, one that gives Failsafe a server address (a property named `*url*` or `*host*`), and one with TestBench grid or Sauce Labs settings. Such a profile still runs green after the migration, but against the JVM app the test starts and a local browser. The recipe changes nothing else in it.
- `*IT` classes still run only through Failsafe. Keep Failsafe, or rename them `*Test` for Surefire.
- In a Spring Security app, the build gets Spring Security's test support (see **The build**), and each statement that calls a TestBench login element (`LoginOverlayElement`, `LoginFormElement`, or a page object extending one) gets a Migration Marker suggesting [`@WithMockUser`](guide/navigation-and-users.md): it signs in without the login form, on every Testing Tool. The recipe doesn't rewrite the login: a test that checks the login itself keeps it. A test method that asserts on a call to a login element, such as `assertEquals("Email", loginView.getUsernameLabel())`, checks the login itself and gets no such marker.

Set the option `springBootTest: false` when the tests must run against the prod-like server the build starts: its profile, its configuration or an external database. The class then becomes a `@UnifiedUITest`, as before, with a Migration Marker saying that Browserless can't build its Spring views; the login statements get no marker. A module without a `@SpringBootApplication` gets `@UnifiedUITest` whatever the option.

### The build

With Maven, the recipe imports `unified-test-bom`, adds `unified-test-spring`, `unified-test-browserless`, `unified-test-selenium` and `com.vaadin:browserless-test-spring` as test dependencies (a `@UnifiedSpringBootTest` fails at once on Browserless without `browserless-test-spring`, and the Browserless adapter doesn't bring it; the project's Vaadin BOM manages its version, and the recipe removes a version the project already gave it), adds Spring Security's test support when the project depends on Spring Security and has none (`spring-boot-starter-security-test`, or `org.springframework.security:spring-security-test` on a Spring Boot without that starter), and removes direct `vaadin-testbench*` dependencies once no test source uses TestBench after the migration (the Selenium adapter brings TestBench core and some element jars). While one still does (a page object left on TestBench, an element class such as `DivElement`, a `TestBenchEscapeHatch`), it keeps them, and `vaadin-testbench-junit5` becomes `vaadin-testbench-junit6`, which brings every element jar. It removes `junit:junit` and `junit-vintage-engine` only when no JUnit 4 test is left after the migration: JUnit 6 runs JUnit 4 tests only through the vintage engine. It also gives each `maven-failsafe-plugin` its own evidence directory, `target/failsafe-reports/unified-evidence`, so Failsafe's run doesn't write over Surefire's ([Surefire and Failsafe in one build](guide/ci-setup.md#surefire-and-failsafe-in-one-build)), and marks each profile that tested a server or browser outside the test (see [A Spring Boot app tested against a server the build starts](#a-spring-boot-app-tested-against-a-server-the-build-starts)).

## What it leaves for you

Code with no Unified equivalent keeps working on Selenium through `TestBenchEscapeHatch`, TestBench's test base API on the Unified Page's browser:

```java
@Test
@RequiresTestingTool(TestingTool.SELENIUM)
void knowsTheArchiveButtonIsDisabled(UnifiedPage page) {
    TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
    // TODO unified: TestBench Escape Hatch: $(ButtonElement.class)
    Assertions.assertTrue(testBench.$(ButtonElement.class).withText("Archive").single().hasAttribute("disabled"));
}
```

`@RequiresTestingTool(SELENIUM)` skips such a test on the other Testing Tools. A fully converted test has no Capability and runs everywhere.

| Migration Marker | What to do |
|---|---|
| `TestBench Escape Hatch: <call>` | Rewrite the call with the Unified API if it can express it, then drop the Escape Hatch and `@RequiresTestingTool` |
| `..., a custom element: write a Wrapper for it (ADR 0003)` | Write a [Wrapper](spec/06-wrappers.md) for the component |
| `inside a Template, text written in its HTML is empty on Browserless: find it by id to run on every Testing Tool` | If the text is set from Java, keep the filter. If the template's HTML writes it, find the component by its `@Id` with `withId(..)` instead, or keep the filter and declare `@RequiresBrowser` |
| `was first(); the lookup fails when several components match` | Run the test. If it fails with "found 2", add the filter that picks the intended one; the failure lists both |
| `was withCaption(..); use withAccessibleName(..) for a button without text` | Check the button has visible text |
| `TestBench page object: new LoginPage(this)` | Convert the page object by hand, or rerun with `includePageObjects` |
| `TestBench page object: LoginViewElement (custom element extending LoginOverlayElement): write a Wrapper or convert the page object` | Write a [Wrapper](spec/06-wrappers.md) for the component, or turn the page object into a plain class on the `UnifiedPage` |
| `the tests ran against a server started outside the test; they now start the app themselves, ...` | Run the tests; fix what differs between the app's test configuration and the server's. Remove the build's `spring-boot:start`/`stop` executions, then delete the marker |
| `a Spring Boot app tested against a server the build starts: ...; consider @UnifiedSpringBootTest` | Keep `@UnifiedUITest` if the tests need that server, and run them on browsers only; else switch to `@UnifiedSpringBootTest` |
| `navigation to <url>: not a local application URL the recipe can read; use page.navigate("<path>")` | Find the path the URL leads to, following a helper's parameter to its callers, and write `page.navigate("<path>")`; a helper takes the path instead. With `@UnifiedSpringBootTest`, a URL such as `http://localhost:8080/` reaches the server the build starts, not the app the test starts, until you do |
| `a local application URL with a fixed port: the app now runs on a random port ...`, on a field | Navigate through `page.navigate("<path>")` instead of the field, then delete the field when nothing reads it |
| `signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)` | Put `@WithMockUser` (or `@WithUserDetails`) on the test or class and drop the login steps, unless the test checks the login itself; then sign in with `page.find(UnifiedLogin.class).login(username, password)`, and give a form that posts (Spring Security's `/login`) `@RequiresBrowser` |
| `browser setup removed (@RunLocally, ...)` | Configure the Selenium adapter with `vaadin.testing.*` settings |
| `the Unified API needs Vaadin 25.3 or newer`, at the top of `pom.xml` | Move the application to Vaadin 25.3, then delete the marker |
| `this profile ran the tests against a server it starts outside the test (...)`, in a `pom.xml` profile | Decide whether that server (often the native image) still needs testing. If not, remove the profile's start and stop; if so, keep the profile, migrate its tests with `springBootTest: false` and set `vaadin.testing.base-url` to the server. Then delete the marker |
| `this profile gives the tests the address of a server outside the test (...)`, in a `pom.xml` profile | A `@UnifiedSpringBootTest` ignores it. Remove it, or, for a `@UnifiedUITest`, rename it `vaadin.testing.base-url`. Then delete the marker |
| `this profile sets TestBench grid or Sauce Labs settings (...)`, in a `pom.xml` profile | The Selenium adapter has no remote grid ([#645](https://github.com/parttio/unified-testing-tool/issues/645)): the profile runs a local browser. Remove the profile or its grid settings, then delete the marker |

Once done, a marker is gone, or it reads `// Escape Hatch kept: <reason>`.

Each Spring context a run starts also starts the application's server. Keep the suite on one: a shared base class with `@UnifiedSpringBootTest`, the profile and any `@ServiceConnection` container, rather than per-class `@MockitoBean`s, `@Import`s, properties or containers ([One Spring context](migrating-from-browserless.md#one-spring-context)).

## The agent skill

`resolve-migration-markers` is a [Claude Code](https://claude.com/claude-code) skill that works through the Migration Markers: it resolves each one into Unified API code, or keeps the Escape Hatch and replaces the marker with `// Escape Hatch kept: <reason>`. It proves each change on Browserless and Playwright, and on Selenium while a test still declares `@RequiresTestingTool(SELENIUM)` or uses a `TestBenchEscapeHatch`, then runs the whole suite on the same Testing Tools and the style rules (`UnifiedStyleRules.ALL`). Last, when no test source still uses TestBench (a `com.vaadin.testbench.*` or `*.testbench.*` class, or `TestBenchEscapeHatch`, as for the migration's own check), it removes the direct `vaadin-testbench-*` dependencies, compiles and runs the suite again, and puts them back if that fails. It reports what it did, including the dependencies it removed. It runs the `*IT` classes the way the project does, through Failsafe: `mvn verify -Pit -Dit.test=<Class>` for one class, `mvn verify -Pit` for the suite (without `-Pit` when no profile binds Failsafe), each with `-Dvaadin.testing.tool=playwright` or `selenium` for those Testing Tools. A run that counted 0 UI tests fails the proof: `mvn test` alone runs none of the `*IT` classes.

Add `addAgentSkill: true` to the recipe's options (see **Run it**): the migration then also writes `.claude/skills/resolve-migration-markers/SKILL.md`, the version matching your recipe's markers. In Claude Code, in the migrated project:

```text
/resolve-migration-markers
```

Only you can start it. The skill is user-invoked (`disable-model-invocation: true`), so it adds nothing to an agent's context in a session that doesn't migrate, but an agent can't start it either: one driving the migration end to end stops at this step. Type the command yourself, or tell the agent to read `.claude/skills/resolve-migration-markers/SKILL.md` and follow its steps.

Every migration, with or without `addAgentSkill`, also runs `org.vaadin.addons.unified.rewrite.AddUnifiedTestingSkill`: it writes `.claude/skills/write-unified-ui-tests/SKILL.md`, the skill agents follow to write and change Unified UI Tests (and which `resolve-migration-markers` defers to for the shape of the code it writes), and points to it from a marked section of `AGENTS.md` (of `CLAUDE.md`, when the project has that and no `AGENTS.md`; with neither file, it also creates a `CLAUDE.md` that imports `AGENTS.md`). It replaces an older copy of the skill, keeps one you edited by hand (it then warns), installs nothing but a warning on the module's build file when the build root has no build file, `AGENTS.md` or `CLAUDE.md`, and can run alone on a project that wasn't migrated ([spec 12, The agent skill](spec/12-readable-tests.md#the-agent-skill)).

Review its changes like any other. The skill's source is in [`unified-test-rewrite`](https://github.com/parttio/unified-testing-tool/blob/main/unified-test-rewrite/src/main/resources/META-INF/unified/skills/resolve-migration-markers/SKILL.md).

### Evaluating the skill

`scripts/eval-migration-skill.sh` runs the skill with Claude Code on the demo's migrated tests in a scratch worktree, runs them on Browserless and Playwright, and scores the result: markers resolved, kept and untouched, Escape Hatches and single-tool tests left, tests passing on each Testing Tool. It compares the score with the committed baseline (`unified-test-rewrite/skill-eval/baseline.json`) and writes an HTML or Markdown report (`--format html|md`).

The comparison is fixture by fixture, over the fixtures in both the baseline and the run. A fixture is a migrated test that still has Migration Markers, so when a recipe improves and a fixture loses its last marker, it leaves the evaluation: the report lists it as "no longer needs the skill" instead of counting the run as worse. A new fixture is reported and compared against nothing. Markers resolved and untouched are compared as rates of the fixture's markers, so a fixture whose markers changed is still compared like with like; Escape Hatches, single-tool tests and tests passed or failed on each Testing Tool are compared as counts. The report shows the totals with their rates (markers resolved / markers before, tests passed / tests run) and a row per fixture.

A run is acceptable when no compared metric is worse, and, when the skill changed, at least one is better, unless the baseline has nothing left to improve (every compared fixture with all its markers resolved, no Escape Hatch, no single-tool test, no failing test); `--accept` then commits it as the new baseline. CI can't run Claude Code, so when the skill changes on `main` without a new baseline, a workflow opens an issue to run it.
