# Checking the style

`unified-test-archunit` checks three rules of the Unified UI Test style in a plain test, with [ArchUnit](https://www.archunit.org/). Spec: [12. Readable tests, Style rules](../spec/12-readable-tests.md#style-rules).

## Opting in

Add the module at test scope. The BOM manages its version, and it brings ArchUnit's core library (not ArchUnit's JUnit engine):

```xml
<dependency>
    <groupId>org.vaadin.addons</groupId>
    <artifactId>unified-test-archunit</artifactId>
    <scope>test</scope>
</dependency>
```

Then check the rules over your test classes from an ordinary test:

```java
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.vaadin.testing.unified.archunit.UnifiedStyleRules;

class UnifiedStyleTest {

    @Test
    @DisplayName("Every Unified UI Test follows the style rules")
    void followsTheUnifiedStyle() {
        UnifiedStyleRules.ALL.check(new ClassFileImporter().importPackages("com.example"));
    }
}
```

The rules check Unified UI Tests only: classes with `@UnifiedUITest` or `@UnifiedSpringBootTest` (on the class, a superclass or an enclosing class, directly or through your own annotation), and classes with a test method that takes a `UnifiedPage`. Other tests and the application code are skipped, and a package without Unified UI Tests passes.

## The rules

`UnifiedStyleRules.ALL` checks all three. Each one is also a constant of its own, to check or [freeze](#suites-with-existing-violations) separately.

| Rule | Fails when | Fix |
|---|---|---|
| `SCENARIO_SENTENCE` | a test method (`@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate`) has no `@DisplayName`, or a one-word one, and neither its class nor an enclosing class has `@DisplayNameGeneration` | `@DisplayName("<what the user does>")`: a sentence about the user, not the method name in words |
| `NO_RAW_WAITS` | the class calls `Thread.sleep(..)`, Playwright's `waitForTimeout`, `waitForSelector` or `Locator.waitFor`, TestBench's `waitUntil` or `waitForFirst` (also through `TestBenchEscapeHatch`), Selenium's `WebDriverWait` or `FluentWait`, or Awaitility | `assertThat(..)`, which retries until the timeout, or `page.waitUntil(..)` ([Assertions and waiting](assertions-and-waiting.md)) |
| `TOOL_CODE_NEEDS_CAPABILITY` | a test reaches a Testing Tool's own code without `@RequiresTestingTool` on the test method, its class or an enclosing class | `@RequiresTestingTool(..)` ([When a test needs one Testing Tool](tool-specific.md)), `tryUnwrap(..)` for a check that runs everywhere, or the Unified API |

Testing Tool code, for `TOOL_CODE_NEEDS_CAPABILITY`, is a call to `unwrap(..)` on the page, a Wrapper or a Unified Element, a call to an `…EscapeHatch.of(..)`, or any use of a class of Playwright, Dramafinder, TestBench, Selenium or Browserless Test. The rule follows the test into the methods of its own class (private helpers, lambdas, anonymous classes), so a helper that unwraps makes every test that calls it need the annotation. A `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@AfterAll` or constructor runs for every test, so Testing Tool code there needs the annotation on the class. A method that calls `tryUnwrap(..)` is an optional extra check and may use the types it unwraps without the annotation.

A failure names the rule, the test and its line, what it did, and the fix:

```
Scenario sentence: test com.example.ProfileTest.savesName(UnifiedPage) (ProfileTest.java:16) has no @DisplayName;
add @DisplayName("<what the user does>") to it, or @DisplayNameGeneration on the class

No raw waits: com.example.ProfileTest.savesName(UnifiedPage) waits with Thread.sleep(..) (ProfileTest.java:18);
let the Unified API wait: assertThat(..) retries until the timeout, page.waitUntil(..) waits for anything else

Testing Tool code needs its Capability: test com.example.ProfileTest.reloads(UnifiedPage) (ProfileTest.java:24)
calls the Escape Hatch UnifiedPage.unwrap(..) in com.example.ProfileTest.reload(UnifiedPage) (ProfileTest.java:53)
without @RequiresTestingTool; add @RequiresTestingTool(TestingTool.PLAYWRIGHT) on the test method or the class,
use tryUnwrap(..) for a check that runs everywhere, or stay in the Unified API
```

## Suites with existing violations

To adopt the rules in a suite that breaks them today, wrap them in ArchUnit's `FreezingArchRule`. The first run records the current violations in a store; later runs fail only on new ones, and the store shrinks as you fix old ones:

```java
FreezingArchRule.freeze(UnifiedStyleRules.SCENARIO_SENTENCE)
        .check(new ClassFileImporter().importPackages("com.example"));
```

The store is a directory, `archunit_store` by default; commit it. ArchUnit's [user guide](https://www.archunit.org/userguide/html/000_Index.html#_freezing_arch_rules) has the settings.

## Excluding classes

Leave classes out when you import them. The demo leaves out its golden migration outputs, which `unified-test-rewrite` compares with what the Migration Recipes write:

```java
--8<-- "unified-test-demo/src/test/java/demo/UnifiedStyleTest.java:excludingClasses"
```

For one violation that is the point of a test, list a regular expression matching its message in `archunit_ignore_patterns.txt` at the root of the test class path (`src/test/resources`). The demo's [`ProfileViewTest`](https://github.com/parttio/unified-testing-tool/blob/main/unified-test-demo/src/test/java/demo/ProfileViewTest.java) checks that `unwrap(..)` without the annotation fails, so its file has:

```
Testing Tool code needs its Capability: test demo\.ProfileViewTest\.unwrapNamesTheMissingAnnotation\(UnifiedPage\) .*
```

## What the rules don't check

The rules read bytecode. They don't see:

- display names set for the whole run (`junit.jupiter.displayname.generator.default`): use `@DisplayNameGeneration` on the classes, or leave `SCENARIO_SENTENCE` out of the check;
- whether the sentence is a good one: only that it has more than one word;
- Testing Tool code reached through another class, such as a shared `…Tasks` class or a page object: the rule follows the test class's own methods only;
- whether the Testing Tool in `@RequiresTestingTool` is the one the code needs;
- the rest of the style, which stays in this guide: `all()` only in loops ([Finding components](finding-components.md#several-matches)), no plain JUnit assertions on what the user sees, shared User Tasks rather than copies ([Structuring a scenario](structuring-a-scenario.md#where-user-tasks-live)).
