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
      -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha4 \
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
      -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-alpha4 \
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
        rewrite("org.vaadin.addons:unified-test-rewrite:0.1.0-alpha4")
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

Up to 0.1.0-alpha4 the recipe ignores the options a `rewrite.yml` sets, without a warning, and runs with the defaults ([#353](https://github.com/parttio/unified-testing-tool/issues/353)). With those versions, add the adapter dependencies by hand, and copy the skill from `META-INF/unified/skills/resolve-migration-markers/SKILL.md` in the `unified-test-rewrite` jar to `.claude/skills/resolve-migration-markers/SKILL.md`.

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

*(unreleased)* A TestBench class also lists the application's page objects and custom elements it uses, each `converts with includePageObjects` or `stays TestBench (Escape Hatch)` with the reason, such as `LoginViewElement: stays TestBench (Escape Hatch), a custom element extending LoginOverlayElement: write a Wrapper (ADR 0003)`. A class that uses one staying TestBench is `migrates partly`: its tests that reach it run on Selenium only.

On Gradle, make it the `activeRecipe` and run `gradle rewriteDryRun`: the patch is `build/reports/rewrite/rewrite.patch`.

## What it converts

| TestBench | Unified API |
|---|---|
| `extends BrowserTestBase`, `TestBenchTestCase` or `ParallelTest`; `@BrowserTest` | `@UnifiedSpringBootTest` (from `@SpringBootTest`) or `@UnifiedUITest`; `@Test` with a `UnifiedPage page` parameter |
| `getDriver().get("http://localhost:" + port + "/people")` | `page.navigate(PeopleView.class)` when one view has the route, else `page.navigate("people")` |
| `driver.get(APP_URL + "people")` on any `WebDriver`, with `APP_URL` a constant `"http://localhost:8080/"` (or `127.0.0.1`) *(unreleased)* | the same; the constant goes when nothing else reads it. A base the recipe can't read (not a constant, not a local URL) keeps the call, with a Migration Marker |
| `$(TextFieldElement.class).withLabel("Name").single()` | `page.find(UnifiedTextField.class).withLabel("Name")`: the Wrapper, which checks on every use that exactly one component matches |
| `withClassName("a", "b")`, or one name with a space | `withClassNames("a", "b")`: the Unified `withClassName` takes one class name, as Flow's `addClassName` does |
| `first()`, `waitForFirst()` | the same lookup, with a `was first()` Migration Marker: the lookup fails when several components match |
| `id("save")` *(unreleased)* | `withId("save")` |
| `$(SearchBarElement.class)` or `$("search-bar")`, when a `LitTemplate` in the sources has `@Tag("search-bar")` *(unreleased)* | `page.findTemplate(SearchBar.class)`, and `bar.$(ButtonElement.class).id("new")` on it `bar.find(UnifiedButton.class).withId("new")` ([Templates](guide/finding-components.md#templates)); a text or label filter there gets a Migration Marker |
| `Assertions.assertEquals(x, el.getValue())`, `getText()`, `assertTrue(cb.isChecked())`, ... | retrying `assertThat(el).hasValue(x)`, `hasText(x)`, `isChecked()`, ... |
| a local `TextFieldElement name = ...` | `UnifiedTextField name = ...` |
| `@BeforeEach` and helper methods that use TestBench | helpers taking `UnifiedPage page` (`unifiedPage` when the method has a `page` already), passed by every call, a test's call to another test too; a `@BeforeEach` becomes a call at the start of each test, which declares its `throws` |
| a project base class extending `BrowserTestBase` | a plain base class with `@UnifiedSpringBootTest`; its tests and helpers convert as above, an abstract helper along with its overrides *(unreleased)* |
| an override of `getDriver()`, or another TestBench base method, and calls to it *(unreleased)* | the override without `@Override`, taking the page; it and the calls go through the TestBench Escape Hatch, with a Migration Marker |
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

A custom element, an application's own `TestBenchElement` subclass such as `@Element("order-card") OrderCardElement extends DivElement` or `LoginViewElement extends LoginOverlayElement`, stays TestBench, with or without the option. *(unreleased)* Each statement calling one gets a Migration Marker, and a test that reaches one, itself or through a helper of its class hierarchy, gets `@RequiresTestingTool(SELENIUM)`. So does a test whose only TestBench code is in an inherited helper going through the Escape Hatch.

### The build

With Maven, the recipe imports `unified-test-bom`, adds `unified-test-spring`, `unified-test-browserless`, `unified-test-selenium` and `com.vaadin:browserless-test-spring` as test dependencies (a `@UnifiedSpringBootTest` fails at once on Browserless without `browserless-test-spring`, and the Browserless adapter doesn't bring it; the project's Vaadin BOM manages its version, and the recipe removes a version the project already gave it), and removes direct `vaadin-testbench*` dependencies once no test source uses TestBench after the migration (the Selenium adapter brings TestBench core and some element jars). *(unreleased)* While one still does (a page object left on TestBench, an element class such as `DivElement`, a `TestBenchEscapeHatch`), it keeps them, and `vaadin-testbench-junit5` becomes `vaadin-testbench-junit6`, which brings every element jar. It removes `junit:junit` and `junit-vintage-engine` only when no JUnit 4 test is left after the migration: JUnit 6 runs JUnit 4 tests only through the vintage engine. *(unreleased)* It also gives each `maven-failsafe-plugin` its own evidence directory, `target/failsafe-reports/unified-evidence`, so Failsafe's run doesn't write over Surefire's ([Surefire and Failsafe in one build](guide/ci-setup.md#surefire-and-failsafe-in-one-build)).

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
| `inside a Template, text written in its HTML is empty on Browserless: find it by id to run on every Testing Tool` *(unreleased)* | If the text is set from Java, keep the filter. If the template's HTML writes it, find the component by its `@Id` with `withId(..)` instead, or keep the filter and declare `@RequiresBrowser` |
| `was first(); the lookup fails when several components match` | Run the test. If it fails with "found 2", add the filter that picks the intended one; the failure lists both |
| `was withCaption(..); use withAccessibleName(..) for a button without text` | Check the button has visible text |
| `TestBench page object: new LoginPage(this)` | Convert the page object by hand, or rerun with `includePageObjects` |
| `TestBench page object: LoginViewElement (custom element extending LoginOverlayElement): write a Wrapper or convert the page object` *(unreleased)* | Write a [Wrapper](spec/06-wrappers.md) for the component, or turn the page object into a plain class on the `UnifiedPage` |
| `browser setup removed (@RunLocally, ...)` | Configure the Selenium adapter with `vaadin.testing.*` settings |
| `the Unified API needs Vaadin 25.3 or newer`, at the top of `pom.xml` | Move the application to Vaadin 25.3, then delete the marker |

Once done, a marker is gone, or it reads `// Escape Hatch kept: <reason>`.

Each Spring context a run starts also starts the application's server. Keep the suite on one: a shared base class with `@UnifiedSpringBootTest`, the profile and any `@ServiceConnection` container, rather than per-class `@MockitoBean`s, `@Import`s, properties or containers ([One Spring context](migrating-from-browserless.md#one-spring-context)).

## The agent skill

`resolve-migration-markers` is a [Claude Code](https://claude.com/claude-code) skill that works through the Migration Markers: it resolves each one into Unified API code, or keeps the Escape Hatch and replaces the marker with `// Escape Hatch kept: <reason>`. It runs the tests it changed on Browserless and Playwright, and reports what it did.

Add `addAgentSkill: true` to the recipe's options (see **Run it**): the migration then also writes `.claude/skills/resolve-migration-markers/SKILL.md`, the version matching your recipe's markers. In Claude Code, in the migrated project:

```text
/resolve-migration-markers
```

Only you can start it. The skill is user-invoked (`disable-model-invocation: true`), so it adds nothing to an agent's context in a session that doesn't migrate, but an agent can't start it either: one driving the migration end to end stops at this step. Type the command yourself, or tell the agent to read `.claude/skills/resolve-migration-markers/SKILL.md` and follow its steps.

Every migration, with or without `addAgentSkill`, also runs `org.vaadin.addons.unified.rewrite.AddUnifiedTestingSkill`: it writes `.claude/skills/write-unified-ui-tests/SKILL.md`, the skill agents follow to write and change Unified UI Tests (and which `resolve-migration-markers` defers to for the shape of the code it writes), and points to it from a marked section of `AGENTS.md` (of `CLAUDE.md`, when the project has that and no `AGENTS.md`). It replaces an older copy of the skill, keeps one you edited by hand (it then warns), and can run alone on a project that wasn't migrated ([spec 12, The agent skill](spec/12-readable-tests.md#the-agent-skill)).

Review its changes like any other. The skill's source is in [`unified-test-rewrite`](https://github.com/parttio/unified-testing-tool/blob/main/unified-test-rewrite/src/main/resources/META-INF/unified/skills/resolve-migration-markers/SKILL.md).

### Evaluating the skill

`scripts/eval-migration-skill.sh` runs the skill with Claude Code on the demo's migrated tests in a scratch worktree, runs them on Browserless and Playwright, and scores the result: markers resolved, kept and untouched, Escape Hatches and single-tool tests left, tests passing on each Testing Tool. It compares the score with the committed baseline (`unified-test-rewrite/skill-eval/baseline.json`) and writes an HTML or Markdown report (`--format html|md`).

The comparison is fixture by fixture, over the fixtures in both the baseline and the run. A fixture is a migrated test that still has Migration Markers, so when a recipe improves and a fixture loses its last marker, it leaves the evaluation: the report lists it as "no longer needs the skill" instead of counting the run as worse. A new fixture is reported and compared against nothing. Markers resolved and untouched are compared as rates of the fixture's markers, so a fixture whose markers changed is still compared like with like; Escape Hatches, single-tool tests and tests passed or failed on each Testing Tool are compared as counts. The report shows the totals with their rates (markers resolved / markers before, tests passed / tests run) and a row per fixture.

A run is acceptable when no compared metric is worse, and, when the skill changed, at least one is better, unless the baseline has nothing left to improve (every compared fixture with all its markers resolved, no Escape Hatch, no single-tool test, no failing test); `--accept` then commits it as the new baseline. CI can't run Claude Code, so when the skill changes on `main` without a new baseline, a workflow opens an issue to run it.
