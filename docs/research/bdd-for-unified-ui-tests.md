# Does BDD deliver for Java web app UI tests?

Research for [#195](https://github.com/parttio/unified-testing-tool/issues/195), part of the map
[#193](https://github.com/parttio/unified-testing-tool/issues/193) "Make Unified UI Tests read like what the user does".
Written 2026-11-15 against `main` at `f42bb61` (Spring Boot 4.1.1, JUnit 6.0.3 from the Spring Boot BOM, Java 21).

**Question.** Would BDD (Gherkin feature files, or a Java-native given/when/then style) make Unified UI Tests
more readable, and for whom?

## Recommendation

**Use Java-native given/when/then. Do not adopt Gherkin, and do not tie the Unified API to any BDD framework.**

1. **The default: plain JUnit 6.** Name scenarios with `@DisplayName`, `@DisplayNameGeneration(IndicativeSentences)`
   and `@Nested`. Put the user's actions in small, app-level step methods that call the Unified API. It costs nothing
   to integrate, and `UnifiedPage` injection, Auto-Wait, Capabilities and failure evidence all keep working unchanged.
2. **Optional: JGiven.** It is the only Java-native BDD library that worked unchanged with `@UnifiedSpringBootTest`
   in a prototype, on both Browserless and Playwright, with Capabilities honoured. It also gives the step-by-step
   report that #193 lists as "not yet specified". Document it as a choice for users. Don't depend on it. A
   failure-evidence gap (below) has to be fixed before it can be recommended without caveats.
3. **Gherkin/Cucumber: no.** Cucumber runs on its own JUnit Platform engine. None of `UnifiedExtension` applies there:
   no `UnifiedPage` injection, no `@RequiresBrowser`/`@RequiresTestingTool` skipping, no failure evidence. The adapter
   SPI also needs a Jupiter `ExtensionContext`. The evidence that non-developers read or write the scenarios is weak.
   Cucumber's own docs warn against the click-by-click UI steps that a Unified UI Test is made of.
4. **Reports in the user's terms.** #193's real need, a report of what the user did, is better met inside the Unified
   API than by a BDD tool. Wrappers already know "clicked button 'Save'". JGiven and Serenity can only report the
   names of the step methods that wrap them.

Who benefits: developers and reviewers, and people who read failure reports. All three gain from scenario names that
read as sentences and from step methods with domain names. QA and product owners gain only if a team really holds the
collaborative discovery sessions (Three Amigos, Example Mapping). The evidence below shows that most teams don't. When
they do, it happens above the UI layer, so it is not a reason for the Unified API to carry Gherkin.

## How a Unified UI Test runs today

What any BDD layer has to keep working is in
[`UnifiedExtension`](../../unified-test-api/src/main/java/com/vaadin/testing/unified/internal/junit/UnifiedExtension.java),
a JUnit Jupiter extension:

| Mechanism | Jupiter callback | What a BDD layer needs |
|---|---|---|
| Capabilities: `@RequiresBrowser`, `@RequiresTestingTool` skip the test ([spec 09](../spec/09-capabilities.md)) | `ExecutionCondition` | annotations on a Jupiter test class or method |
| `UnifiedPage` injected as a method or constructor parameter ([spec 02](../spec/02-test-setup.md)) | `ParameterResolver` | a Jupiter-invoked method or constructor |
| Session closed and failure evidence written | `AfterEachCallback`, reads `context.getExecutionException()` | the failure must reach Jupiter as the test's exception |
| Adapters open a session | `AdapterContext.extensionContext()` ([`AdapterContext`](../../unified-test-api/src/main/java/com/vaadin/testing/unified/spi/AdapterContext.java)) | a Jupiter `ExtensionContext` |

The adapters really use that `ExtensionContext`. Browserless calls `getRequiredTestClass()` for view packages and finds
the Spring context through it. Playwright and Selenium read the Spring port through
`SpringExtension.getApplicationContext(context)`. Selenium keeps its shared browser in `context.getRoot()`'s store.
Auto-Wait lives inside `UnifiedPage` and the Wrappers, so it survives in any layer that holds a `UnifiedPage`.

## The candidates

| | Runs on JUnit 6 + Spring Boot 4 | Step gets `UnifiedPage` | Capabilities | Failure evidence | Report |
|---|---|---|---|---|---|
| **Plain JUnit** given/when/then | yes | yes, as today | yes | yes | IDE/Surefire tree with sentence names |
| **JGiven 3.0.0** | yes (`jgiven-junit6`, Java 21+); **prototyped** | yes, by constructor injection into a `@ProvidedScenarioState` field | yes (prototyped) | **no**: JGiven defers the step's exception, so it reaches Jupiter too late (prototyped) | console Given/When/Then text, JSON, HTML5, AsciiDoc |
| **Serenity BDD 5.4.2** | JUnit 6.1 yes; Spring support built against Spring 6.2, Spring 7 not verified | not built in; possible through its `StepParameterProvider` SPI or by passing the page | yes on JUnit 5 tests (Jupiter conditions still run), not verified | not verified | rich HTML living documentation; screenshots only through its own WebDriver/Playwright integration |
| **Cucumber-JVM 8.0.4** | engine yes (JUnit Platform 6.1); cucumber-spring works with Spring Boot 4.1.1 (**prototyped**) | **no**: separate engine, no Jupiter extensions; needs a new non-Jupiter entry point | no; would be re-implemented as tag-scoped hooks that throw `TestAbortedException` | no; would be re-implemented | pretty console, HTML, JSON, JUnit XML, messages |
| **Gauge (gauge-java 1.0.5)** | its own runner (`gauge run`), not the JUnit Platform; Spring only through a hand-written `ClassInitializer` | no; same problem as Cucumber, and no Spring TestContext | no | no | Gauge HTML report plugin |

### Plain JUnit given/when/then naming

JUnit Jupiter ships display-name generators, including `IndicativeSentences`, which "generates complete sentences
by concatenating the names of the test and the enclosing classes". `@DisplayName` values always take precedence
([JUnit user guide: Display Names](https://docs.junit.org/current/writing-tests/display-names.html); source
[`display-names.adoc`](https://github.com/junit-team/junit-framework/blob/main/documentation/modules/ROOT/pages/writing-tests/display-names.adoc)).
Together with `@Nested` classes ("Given a filled checkout form" → "when the user saves" → "then …"), and step methods
in the test or in a small app-level helper class, a test reads as a scenario. Nothing changes for `UnifiedExtension`.
The report is the JUnit tree in the IDE and in Surefire XML. It has no per-step lines, so a failure shows the assertion
message (already in user terms: `Expected UnifiedNotification.single() to have text <…> but was <…> (waited 2000 ms)`)
plus the evidence file.

Cost: none. Limit: no generated living documentation and no step-by-step report. Both could come from the Unified API
itself (see Implications).

### JGiven

- **Versions.** JGiven 3.0.0 (Maven Central, 2026-10-09) adds `jgiven-junit6`, "with support for both JUnit 5 and
  JUnit 6", and `jgiven-spring-junit6` "that supports Spring 7.x". It drops Java 17 and requires Java 21
  ([CHANGELOG](https://github.com/TNG/JGiven/blob/master/CHANGELOG.md)). The project builds on Java 21, so that fits.
- **Model.** Scenarios are Java methods calling `given()`/`when()`/`then()` on Stage classes, whose method names become
  the report text. Stages share state through `@ScenarioState`, `@ProvidedScenarioState` and `@ExpectedScenarioState`
  fields, "resolved by its type" by default ([stages.adoc](https://github.com/TNG/JGiven/blob/master/docs/stages.adoc)).
  JGiven needs the per-method test instance lifecycle
  ([junit5.adoc](https://github.com/TNG/JGiven/blob/master/docs/junit5.adoc)).
- **Getting `UnifiedPage` into a stage.** `JGivenExtension.postProcessTestInstance` calls
  `readScenarioState(testInstance)` ([source](https://github.com/TNG/JGiven/blob/master/jgiven-junit6/src/main/java/com/tngtech/jgiven/junit6/JGivenExtension.java)).
  A `UnifiedPage` received by the test's constructor, which `UnifiedExtension` resolves, and stored in a
  `@ProvidedScenarioState` field is therefore handed to every stage. Stage methods themselves are called by JGiven's
  ByteBuddy proxy, not by Jupiter, so they cannot take `UnifiedPage` as a parameter.
- **Selenium/web.** "There is no additional integration module needed in order to use JGiven with Selenium". The docs
  advise keeping page objects (here: Wrappers) separate from stages
  ([selenium.adoc](https://github.com/TNG/JGiven/blob/master/docs/selenium.adoc)).
- **Reports.** Plain text to the console by default, plus JSON, from which the HTML5 report and (new in 3.0.0) AsciiDoc
  are generated ([report_generation.adoc](https://github.com/TNG/JGiven/blob/master/docs/report_generation.adoc)).
- **Maintenance.** Active: 3.0.0 released October 2026, last commit 2026-11-12. The 3.0.0 release notes on GitHub warn
  that the published artifacts were built from an earlier commit than the tag
  ([releases](https://github.com/TNG/JGiven/releases)).

**Prototype (throwaway, not committed).** `CheckoutTest.picksTheFieldThroughItsContainer` was rewritten as a JGiven
`ScenarioTest` with `@UnifiedSpringBootTest`, `jgiven-junit6:3.0.0`, the `UnifiedPage` taken in the constructor and
put in a `@ProvidedScenarioState` field, and one `@RequiresBrowser` scenario.

```java
@UnifiedSpringBootTest
class CheckoutJGivenTest extends ScenarioTest<Steps, Steps, Steps> {
    @ProvidedScenarioState UnifiedPage page;
    CheckoutJGivenTest(UnifiedPage page) { this.page = page; }

    @Test
    void billing_and_shipping_cities_are_saved_separately() {
        given().the_checkout_view_is_open();
        when().the_billing_city_is_set_to("Turku")
            .and().the_shipping_city_is_set_to("Berlin")
            .and().the_user_saves();
        then().a_notification_says("Billing: Turku, shipping: Berlin");
    }
}
```

Results:

- Browserless: passes. The `@RequiresBrowser` scenario is skipped ("Requires a real browser, this run uses
  BROWSERLESS"). Console report:
  ```
   Billing and shipping cities are saved separately
     Given the checkout view is open
      When the billing city is set to "Turku"
       And the shipping city is set to "Berlin"
       And the user saves
      Then a notification says "Billing: Oulu" (failed)
  FAILED: java.lang.AssertionError: Expected UnifiedNotification.single() to have text <Billing: Oulu> but was <Billing: Turku, shipping: Berlin> (waited 2000 ms)
  ```
  (shown with a deliberately wrong expectation; Auto-Wait's retrying assertion waited the configured 2 s).
- Playwright (installed Chrome): both scenarios pass, including the `@RequiresBrowser` one.
- **Gap:** on that failure Surefire reports an *error* (`RuntimeException` wrapping the `AssertionError`, thrown from
  `JGivenExtension.afterTestExecution`) instead of a *failure*, and **no failure evidence was written**. A plain
  `@UnifiedSpringBootTest` test failing the same way wrote `…tree.txt` and named it in a suppressed exception. JGiven
  catches the step's exception and rethrows it after the test method. `UnifiedExtension.afterEach` reads
  `getExecutionException()`, which did not show the rethrown failure in this run. This can be fixed on our side, for
  example by capturing the evidence from a callback that sees JGiven's rethrow, or by checking JGiven's scenario state,
  but it is work.

### Serenity BDD

- **Versions.** 5.4.2 (Maven Central, 2026-11-02). It builds against JUnit Jupiter 6.1.3 and Cucumber 8.0.4, but Spring
  6.2.11 and Spring Boot 3.5.0 ([pom.xml](https://github.com/serenity-bdd/serenity-core/blob/main/pom.xml),
  [5.4.0 release notes](https://github.com/serenity-bdd/serenity-core/blob/main/docs/release-notes/5.4.0.md)). Java 17
  baseline. **Spring 7 / Spring Boot 4 support for `serenity-spring` is not stated anywhere I found.**
- **Model.** A JUnit 5 test with `SerenityJUnit5Extension` (a `TestInstancePostProcessor`, `BeforeEach` and `AfterEach`
  callback, [source](https://github.com/serenity-bdd/serenity-core/blob/main/serenity-junit5/src/main/java/net/serenitybdd/junit5/SerenityJUnit5Extension.java))
  injects `@Steps` libraries whose `@Step` methods become report lines. The alternatives are Screenplay or
  Serenity-with-Cucumber. Because the test is still a Jupiter test, `UnifiedExtension`'s condition and parameter
  resolution still apply to the test method. Step libraries are built by Serenity, so they need the page passed in.
  Since 5.3.3 a `StepParameterProvider` SPI lets modules supply step-library constructor arguments; Serenity uses it to
  inject Playwright's `Page` ([5.3.3 notes](https://github.com/serenity-bdd/serenity-core/blob/main/docs/release-notes/5.3.3.md)).
  A `UnifiedPage` provider would be the same idea, but that is not verified.
- **Reports.** The strongest of all the candidates: aggregated HTML "living documentation" with requirements, features
  and per-step screenshots. The screenshots come from Serenity's own WebDriver/Playwright integration
  ([5.1.0](https://github.com/serenity-bdd/serenity-core/blob/main/docs/release-notes/5.1.0.md),
  [5.2.0](https://github.com/serenity-bdd/serenity-core/blob/main/docs/release-notes/5.2.0.md)). Serenity cannot see the
  browser behind a `UnifiedPage`, nor Browserless at all, so a Unified UI Test would lose the screenshots that are its
  main draw.
- **Fit.** Serenity is a whole test framework with its own browser management, configuration (`serenity.conf`) and
  report build step. That overlaps the Unified API's job of owning the Testing Tool, and it is heavy to add for
  readability alone.

### Cucumber-JVM

- **Versions.** 8.0.0 (2026-09-24) moves the engine to "JUnit Platform 6.1.2 (JUnit Jupiter 6.1.2)", sets a Java 17
  baseline and deprecates `cucumber-junit` (JUnit 4) and `cucumber-testng` in favour of the platform engine
  ([CHANGELOG](https://github.com/cucumber/cucumber-jvm/blob/main/CHANGELOG.md),
  [v8.0.0 notes](https://github.com/cucumber/cucumber-jvm/blob/main/release-notes/v8.0.0.md)). `cucumber-spring` on
  `main` builds against Spring 7.0.10
  ([pom](https://github.com/cucumber/cucumber-jvm/blob/main/cucumber-spring/pom.xml)). Latest is 8.0.4 (2026-10-02).
- **How it runs.** Cucumber is its own engine (`@IncludeEngines("cucumber")`). The JUnit Platform forwards to it beside
  the Jupiter engine, not through it. Maven Surefire cannot discover `.feature` files directly, so a `@Suite` class is
  the usual workaround, plus `cucumber.junit-platform.naming-strategy=long` so Surefire names read well
  ([engine README](https://github.com/cucumber/cucumber-jvm/blob/main/cucumber-junit-platform-engine/README.md)).
  **Prototyped:** Cucumber 8.0.4 with a `@CucumberContextConfiguration @SpringBootTest(RANDOM_PORT)` glue class ran
  green in `unified-test-demo` on Spring Boot 4.1.1 through a `@Suite` class.
- **Dependency injection.** Step-definition methods receive only Gherkin arguments (Cucumber Expressions or regular
  expressions, DataTables, DocStrings). Everything else comes through the DI container's constructor or field
  injection. With cucumber-spring, glue classes are Spring beans in a `cucumber-glue` scope that "starts prior to a
  scenario and ends after a scenario". Mock beans and other test-execution-listener features work only on the
  `@CucumberContextConfiguration` class, and there is one application context per run
  ([cucumber-spring README](https://github.com/cucumber/cucumber-jvm/blob/main/cucumber-spring/README.md)).
- **`UnifiedPage`, Capabilities, evidence.** Jupiter extensions do not run for scenarios, so `UnifiedExtension` never
  sees them. A Cucumber integration would need:
  (a) an adapter SPI entry point without `ExtensionContext`, because today's adapters need it for the test class, the
  Spring context and Selenium's shared browser;
  (b) a scenario-scoped `UnifiedPage` bean or holder opened in a `@Before` hook;
  (c) Capabilities re-implemented as tags. The engine supports aborting with OpenTest4J `TestAbortedException`, so a
  hook such as `@Before("@RequiresBrowser")` with `Assumptions.assumeTrue(...)` gives "skipped"
  ([engine README, Aborting Tests](https://github.com/cucumber/cucumber-jvm/blob/main/cucumber-junit-platform-engine/README.md#aborting-tests));
  (d) failure evidence in an `@After` hook.
  All of this is a second integration to maintain beside the Jupiter one.
- **Reports.** Pretty console, HTML, JSON, JUnit XML and Cucumber Messages. Mature, and readable to non-developers.

### Gauge

- **Status.** "Gauge's official sponsorship ended in 2021 … please consider using Gauge only if you're prepared to
  adopt and support it fully on your own" ([gauge README](https://github.com/getgauge/gauge#important-note)). Thoughtworks'
  Radar entry from 2015–2016 is no longer on the current Radar
  ([Radar: Gauge](https://www.thoughtworks.com/radar/tools/gauge)).
- **How it runs.** Markdown specs, executed by the `gauge` CLI through a Java language runner (gauge-java 1.0.5,
  Java 17 minimum, [README](https://github.com/getgauge/gauge-java)), not on the JUnit Platform. Spring comes only
  through a custom `ClassInitializer` ([interface](https://github.com/getgauge/gauge-java/blob/master/src/main/java/com/thoughtworks/gauge/ClassInitializer.java);
  sample [getgauge-examples/ioc-spring](https://github.com/getgauge-examples/ioc-spring)), with no Spring TestContext,
  so `@SpringBootTest` and `@MockitoBean` are out.
- **Fit.** It has the Cucumber problems and more: no Jupiter, no Spring test support, and a project that is in
  community maintenance. Ruled out.

## How BDD works out in practice

### Do non-developers read or write the scenarios?

- **The tool's own authors say Cucumber is not mainly a test tool.** Cucumber's docs: "Many people use Cucumber to do
  test automation, to check for bugs after the code has already been implemented. That's a perfectly reasonable way to
  do test automation, but it's not BDD." And: "Just because you're using Cucumber, doesn't mean you're doing BDD."
  They quote Liz Keogh: "having conversations is more important than capturing conversations is more important than
  automating conversations"
  ([Myths about BDD](https://github.com/cucumber/docs/blob/main/content/docs/bdd/myths.md)). On who writes: start
  with the whole team; later a developer and tester pair "as long as their output is actively reviewed by the product
  owner" ([Who does what](https://github.com/cucumber/docs/blob/main/content/docs/bdd/who-does-what.md)). Aslak
  Hellesøy's "The world's most misunderstood collaboration tool" (cucumber.io blog, 2014) makes the same point
  ([post](https://cucumber.io/blog/the-worlds-most-misunderstood-collaboration-tool); I could not reach cucumber.io
  from this session, so this summary rests on search results and a copy on the
  [cukes mailing list](https://groups.google.com/g/cukes/c/FylDSDLLrio/m/LgcKnHjqkCoJ)).
- **Research: the tools are used, the practice often isn't.** Zampetti et al., *Information and Software Technology*
  2020: about 27% of roughly 50,000 open-source projects in five languages contained BDD-framework artifacts, but many
  used them for other things, such as unit testing, rather than BDD. Scenarios co-evolved with production code in only
  about 37% of cases ([record](https://mdipenta.github.io/journals/2020-01-01-Demystifying-the-adoption-of-behavior-driven-development-in-open-source-projects);
  figures from the abstract, full text not read). Binamungu, Embury and Konstantinou, SANER 2018, surveyed BDD
  practitioners (75, by a later paper's count). They found that BDD specifications "suffer the same maintenance
  challenges found in automated test suites more generally"
  ([DOI 10.1109/SANER.2018.8330207](https://doi.org/10.1109/SANER.2018.8330207); abstract only). Pereira et al., XP 2018,
  interviewed 24 practitioners on benefits and challenges ([record](https://oro.open.ac.uk/62555); not read in full).
  I found no study measuring how often product owners actually read the scenarios.
- **Anecdotal (practitioner blogs, not data).** Several say stakeholders stopped reading the feature files and that
  suites turned into "integration tests in Cucumber syntax" once collaborative sessions stopped. Examples:
  [8th Light, "A Case Against Cucumber"](https://8thlight.com/insights/a-case-against-cucumber),
  [SEP, "Cucumber isn't human readable"](https://sep.com/blog/cucumber-isnt-human-readable/),
  [dev.to, "When Cucumber grows too big"](https://dev.to/luiinge/when-cucumber-grows-too-big-pain-points-lessons-learned-and-alternatives-21pm).
  On the other side, Dan North has argued on the BDD mailing list that product owners "can read DSLs just fine"
  ([thread](https://groups.google.com/g/behaviordrivendevelopment/c/uDbHODwqUPk)). Several "Cucumber is dead" posts
  come from vendors selling replacements (testRigor) and should be discounted.

### Known failure modes

- **Step-definition sprawl and feature-coupled steps.** Cucumber's own anti-patterns page names "feature-coupled step
  definitions" that "can't be reused across features or scenarios", leading to "an explosion of step definitions, code
  duplication, and high maintenance costs", plus "conjunction steps"
  ([Anti-patterns](https://github.com/cucumber/docs/blob/main/content/docs/guides/anti-patterns.md)).
- **Imperative UI steps.** Cucumber's guidance is to write `When "Bob" logs in`, not "I enter … in the 'user name'
  field … I press the 'login' button". Imperative steps "are so closely tied to the mechanics of the current UI, they
  often require more work to maintain"
  ([Writing better Gherkin](https://github.com/cucumber/docs/blob/main/content/docs/bdd/better-gherkin.md)). A
  Unified UI Test is exactly the "how" layer (find this field, set this value, click this button). A good Gherkin
  scenario would hide it behind domain steps. The readable part would then live in the step-definition layer, which can
  just as well be plain Java methods.
- **Glue and regex steps.** Steps match by Cucumber Expression or regular expression
  ([Step definitions](https://github.com/cucumber/docs/blob/main/content/docs/cucumber/step-definitions.md)). Renaming
  or rewording a step is a text-matching change with no compiler help. Cucumber's FAQ has its own entry for steps
  reported as undefined because the glue path is wrong
  ([FAQ](https://github.com/cucumber/docs/blob/main/content/docs/community/faq.md)). The Unified API's design leans
  the other way, toward compile-time checks: ADR 0001 checks
  filters at compile time, and ADR 0002 uses class-literal lookups.
- **The wrong test level.** Thoughtworks' Radar warned as early as 2012 that BDD tools paired with Selenium push teams
  into "heavy browser-level acceptance testing"
  ([Radar: Test at the appropriate level](https://thoughtworks.com/radar/techniques/test-at-the-appropriate-level)).
  This matters less here, because Browserless makes the UI layer cheap, but it is the reason teams that do BDD usually
  automate their scenarios below the UI.

### Vaadin community use

- **Official docs.** Vaadin's only BDD page, "Behavior-Driven Development" (Flow testing → end-to-end), shows a
  **JBehave** example with TestBench, not Cucumber
  ([vaadin/docs source](https://github.com/vaadin/docs/blob/latest/articles/flow/testing/end-to-end/bdd.adoc); published at
  [vaadin.com/docs/latest/testing/end-to-end/bdd](https://vaadin.com/docs/latest/testing/end-to-end/bdd)). The example
  lives in [vaadin/testbench-demo](https://github.com/vaadin/testbench-demo/tree/master/src/test/java/com/vaadin/testbenchexample/bdd),
  a calculator story on `jbehave-core` 5.2.0. The page is a short pointer and makes no recommendation.
- **GitHub.** A code search for `pom.xml` files with both `cucumber-java` and `vaadin-spring-boot-starter` returned
  17 hits. Most are student projects from one university web-engineering course (University of Cádiz, `iw2023-2024-*`,
  `iw2024-2025-*`) built on a teaching template, [ruizrube/iw-fullstack](https://github.com/ruizrube/iw-fullstack). Its
  feature file drives a hand-made ChromeDriver from cucumber-spring glue with no Vaadin-specific testing library. A
  repository search for "vaadin cucumber" found 2 repositories, the newer last pushed in 2019. "vaadin jgiven" found 1,
  and "vaadin serenity" found 0. This is indicative, not exhaustive (private code is invisible).
- **Forum and Directory.** vaadin.com was not reachable from this session. Web searches surfaced no Vaadin forum thread
  and no Vaadin Directory add-on for Cucumber, JGiven or Serenity. The one Cucumber/Vaadin Q&A found is a Vaadin 7-era
  Stack Overflow answer saying the integration is "in fact an integration between vaadin and selenium … and selenium and
  cucumber". **Gap:** a direct search of the Vaadin forum and Directory should confirm this before the map closes.

## Implications for #193

- **Step reports in user terms belong in the Unified API.** Every Wrapper action already knows its user-level meaning
  (`UnifiedButton` "Save" → clicked). If the Unified API recorded those actions per test, it could print or attach them
  on failure, and optionally publish them with `publishReportEntry`. That gives the "what the user did" report on every
  Testing Tool, with or without a BDD library. JGiven and Serenity can only report the names of the step methods that
  wrap these calls.
- **Readable structure is a guide topic, not an API.** `@Nested` plus `IndicativeSentences` plus app-level step methods
  (`checkout.fillBilling("Turku")`) cover what JGiven's stages give, apart from the report.
- **If JGiven is documented as an option,** fix failure evidence for exceptions that JGiven defers, and add a JGiven
  version of one demo test so a JGiven upgrade that breaks it is caught.
- **If Gherkin is ever wanted,** the prerequisite is an adapter SPI that does not require a Jupiter `ExtensionContext`
  (test class, Spring application context and run-wide store passed in explicitly). That would also help any other
  non-Jupiter runner. It is not worth doing for readability alone.

## Not verified

- Serenity with Spring 7 / Spring Boot 4, and Serenity with `UnifiedPage` (not prototyped).
- JGiven on the Selenium Testing Tool (prototyped on Browserless and Playwright only), and JGiven's HTML5 report with
  this setup (JSON was written; the HTML report was not generated).
- The full texts of the three academic papers (only their abstracts and records were reachable).
- jgiven.org, cucumber.io, vaadin.com and docs.junit.org could not be reached from this session. The same documentation
  was read from the projects' GitHub sources linked above.
