package com.vaadin.testing.unified.archunit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.vaadin.testing.unified.archunit.fixtures.DeclaredToolWaitsFixture;
import com.vaadin.testing.unified.archunit.fixtures.GeneratedNamesFixture;
import com.vaadin.testing.unified.archunit.fixtures.LifecycleToolCodeFixture;
import com.vaadin.testing.unified.archunit.fixtures.MissingSentenceFixture;
import com.vaadin.testing.unified.archunit.fixtures.NotUnifiedFixture;
import com.vaadin.testing.unified.archunit.fixtures.OptionalCheckHelpersFixture;
import com.vaadin.testing.unified.archunit.fixtures.PageParameterFixture;
import com.vaadin.testing.unified.archunit.fixtures.RawWaitsFixture;
import com.vaadin.testing.unified.archunit.fixtures.SentencesFixture;
import com.vaadin.testing.unified.archunit.fixtures.ToolCodeDeclaredFixture;
import com.vaadin.testing.unified.archunit.fixtures.ToolCodeFixture;
import com.vaadin.testing.unified.archunit.fixtures.UndeclaredToolWaitsFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnifiedStyleRulesTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter()
            .importPackages("com.vaadin.testing.unified.archunit.fixtures");

    /** The fixture and its nested and anonymous classes. */
    private static JavaClasses fixture(Class<?> type) {
        return FIXTURES.that(DescribedPredicate.describe(type.getSimpleName(), (JavaClass c) ->
                c.getName().equals(type.getName()) || c.getName().startsWith(type.getName() + "$")));
    }

    private static List<String> violations(ArchRule rule, Class<?> fixture) {
        return rule.evaluate(fixture(fixture)).getFailureReport().getDetails();
    }

    @Test
    @DisplayName("A fixture that follows the style passes every rule")
    void sentencesPassEveryRule() {
        UnifiedStyleRules.ALL.check(fixture(SentencesFixture.class));
        UnifiedStyleRules.ALL.check(fixture(GeneratedNamesFixture.class));
        UnifiedStyleRules.ALL.check(fixture(ToolCodeDeclaredFixture.class));
        UnifiedStyleRules.ALL.check(fixture(DeclaredToolWaitsFixture.class));
    }

    @Test
    @DisplayName("A plain test is no Unified UI Test, so no rule applies to it")
    void plainTestsAreNotChecked() {
        UnifiedStyleRules.ALL.check(fixture(NotUnifiedFixture.class));
        assertFalse(UnifiedStyleRules.isUnifiedUITest(FIXTURES.get(NotUnifiedFixture.class)));
    }

    @Test
    @DisplayName("No Unified UI Test at all is no failure")
    void emptySelectionPasses() {
        UnifiedStyleRules.ALL.check(new ClassFileImporter().importClasses(UnifiedStyleRulesTest.class));
    }

    @Test
    @DisplayName("A test without @DisplayName, or with a one-word one, has no scenario sentence")
    void missingSentences() {
        List<String> violations = violations(UnifiedStyleRules.SCENARIO_SENTENCE, MissingSentenceFixture.class);

        assertEquals(4, violations.size(), String.join("\n", violations));
        assertTrue(violations.stream().allMatch(v -> v.startsWith("Scenario sentence: test ")), violations::toString);
        assertTrue(violations.stream().anyMatch(v -> v.contains("MissingSentenceFixture.savesName(UnifiedPage)")
                && v.contains("add @DisplayName(\"<what the user does>\") to it, or @DisplayNameGeneration on the class")));
        assertTrue(violations.stream().anyMatch(v -> v.contains(".savesTwice(UnifiedPage)")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("MissingSentenceFixture$WhenLoggedIn.seesTheDashboard")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("@DisplayName(\"savesEmail\"), which is no sentence")));
    }

    @Test
    @DisplayName("A UnifiedPage parameter makes a class a Unified UI Test without its annotation")
    void unifiedPageParameter() {
        assertTrue(UnifiedStyleRules.isUnifiedUITest(FIXTURES.get(PageParameterFixture.class)));
        assertEquals(1, violations(UnifiedStyleRules.SCENARIO_SENTENCE, PageParameterFixture.class).size());
        assertEquals(1, violations(UnifiedStyleRules.NO_RAW_WAITS, PageParameterFixture.class).size());
    }

    @Test
    @DisplayName("Thread.sleep and every Testing Tool's wait API are raw waits")
    void rawWaits() {
        List<String> violations = violations(UnifiedStyleRules.NO_RAW_WAITS, RawWaitsFixture.class);
        String all = String.join("\n", violations);

        assertTrue(violations.stream().allMatch(v -> v.startsWith("No raw waits: ")), all);
        assertTrue(violations.stream().allMatch(v -> v.contains("let the Unified API wait")), all);
        for (String wait : List.of("RawWaitsFixture.sleeps(UnifiedPage)", "Thread.sleep(..)",
                "Page.waitForTimeout(..)", "Page.waitForSelector(..)", "Locator.waitFor(..)", "WebDriverWait",
                "Awaitility (Awaitility.await)", "RawWaitsFixture$1.run()")) {
            assertTrue(all.contains(wait), wait + " in\n" + all);
        }
        assertEquals(0, violations(UnifiedStyleRules.TOOL_CODE_NEEDS_CAPABILITY, RawWaitsFixture.class).size());
    }

    @Test
    @DisplayName("A Testing Tool's own waits pass in code that declares that Testing Tool")
    void declaredToolWaits() {
        List<String> violations = violations(UnifiedStyleRules.NO_RAW_WAITS, DeclaredToolWaitsFixture.class);

        assertEquals(List.of(), violations);
        UnifiedStyleRules.ALL.check(fixture(DeclaredToolWaitsFixture.class));
    }

    @Test
    @DisplayName("A Testing Tool's waits fail where the code declares another Testing Tool or none, and sleeps fail anywhere")
    void undeclaredToolWaits() {
        List<String> violations = violations(UnifiedStyleRules.NO_RAW_WAITS, UndeclaredToolWaitsFixture.class);
        String all = String.join("\n", violations);

        assertEquals(6, violations.size(), all);
        // no @RequiresTestingTool: the message stays as it is
        String fixture = UndeclaredToolWaitsFixture.class.getName();
        for (String wait : List.of(".waitsUndeclared(UnifiedPage) waits with TestBenchEscapeHatch.waitUntil(..) (",
                ".waitsUndeclared(UnifiedPage) waits with ElementQuery.waitForFirst(..) (",
                ".waitForTheButton(UnifiedPage) waits with ElementQuery.waitForFirst(..) (")) {
            assertTrue(violations.stream().anyMatch(v -> v.startsWith("No raw waits: " + fixture + wait)
                    && v.contains("); let the Unified API wait")), wait + " in\n" + all);
        }
        // another Testing Tool declared: the message names the one the wait belongs to
        assertTrue(violations.stream().anyMatch(v -> v.contains(".waitsInAnotherTestingTool(UnifiedPage) waits with "
                + "TestBenchEscapeHatch.waitUntil(..)") && v.contains("a SELENIUM wait in code that declares "
                        + "@RequiresTestingTool(PLAYWRIGHT)")), all);
        assertTrue(all.contains(".sleepsInTestBench(UnifiedPage) waits with Thread.sleep(..)"), all);
        // a helper reached from a SELENIUM test and an undeclared one
        assertTrue(all.contains(".waitUntilSaved(UnifiedPage) waits with TestBenchEscapeHatch.waitUntil(..)"), all);
    }

    @Test
    @DisplayName("Tests that reach Testing Tool code without @RequiresTestingTool fail, naming where")
    void toolCodeWithoutCapability() {
        List<String> violations = violations(UnifiedStyleRules.TOOL_CODE_NEEDS_CAPABILITY, ToolCodeFixture.class);
        String all = String.join("\n", violations);

        assertEquals(5, violations.size(), all);
        assertTrue(violations.stream().allMatch(v -> v.startsWith("Testing Tool code needs its Capability: test ")),
                all);
        // the Testing Tool to name comes from the class literal unwrapped
        assertTrue(violations.stream().anyMatch(v -> v.contains(".unwraps(UnifiedPage) (ToolCodeFixture.java:")
                && v.contains("calls the Escape Hatch UnifiedButton.unwrap(..)")
                && v.contains("add @RequiresTestingTool(TestingTool.PLAYWRIGHT)")), all);
        assertTrue(all.contains(".unwrapsInAHelper(UnifiedPage)")
                && all.contains("calls the Escape Hatch UnifiedPage.unwrap(..) in "
                        + ToolCodeFixture.class.getName() + ".reload(UnifiedPage)"), all);
        assertTrue(all.contains(".dependsOnPlaywrightInAHelper(UnifiedPage)")
                && all.contains("uses com.microsoft.playwright.Locator in " + ToolCodeFixture.class.getName()
                        + ".selector()")
                && all.contains("add @RequiresTestingTool(TestingTool.PLAYWRIGHT) on the test method or the class"),
                all);
        assertTrue(all.contains("enters the Escape Hatch BrowserlessFakeEscapeHatch.of(..)")
                && all.contains("@RequiresTestingTool(TestingTool.BROWSERLESS)"), all);
        assertTrue(all.contains(".unwrapsInALambda(UnifiedPage)"), all);
        assertFalse(all.contains("staysInTheUnifiedApi"), all);
    }

    @Test
    @DisplayName("A helper called from a tryUnwrap(..) check is part of that optional check")
    void helpersOfAnOptionalCheck() {
        List<String> violations = violations(UnifiedStyleRules.TOOL_CODE_NEEDS_CAPABILITY,
                OptionalCheckHelpersFixture.class);
        String all = String.join("\n", violations);

        assertEquals(2, violations.size(), all);
        assertFalse(all.contains(".checksInAHelper(UnifiedPage)"), all);
        assertFalse(all.contains(".checksInAHelperOfAHelper(UnifiedPage)"), all);
        // the helper is no optional check where a test calls it outside one
        assertTrue(violations.stream().anyMatch(v -> v.contains(".callsTheHelperOutsideTheCheck(UnifiedPage)")
                && v.contains("has com.microsoft.playwright.Locator in " + OptionalCheckHelpersFixture.class.getName()
                        + ".previewShown(Locator)")), all);
        // unwrap(..) fails on other Testing Tools, also inside an optional check
        assertTrue(violations.stream().anyMatch(v -> v.contains(".unwrapsInAHelperOfTheCheck(UnifiedPage)")
                && v.contains("calls the Escape Hatch UnifiedPage.unwrap(..) in "
                        + OptionalCheckHelpersFixture.class.getName() + ".reload(UnifiedPage)")), all);
    }

    @Test
    @DisplayName("Testing Tool code in a @BeforeEach needs the annotation on the class")
    void toolCodeInLifecycleMethods() {
        List<String> violations = violations(UnifiedStyleRules.TOOL_CODE_NEEDS_CAPABILITY,
                LifecycleToolCodeFixture.class);

        assertEquals(1, violations.size(), violations::toString);
        assertTrue(violations.get(0).contains(".openInPlaywright(UnifiedPage)")
                && violations.get(0).contains("runs for every test")
                && violations.get(0).contains("on the class"), violations.get(0));
    }

    @Test
    @DisplayName("ALL fails with each rule's name and its fix")
    void allNamesTheRules() {
        JavaClasses broken = FIXTURES.that(DescribedPredicate.describe("broken fixtures", (JavaClass c) ->
                c.getName().contains("MissingSentenceFixture") || c.getName().contains("RawWaitsFixture")
                        || c.getName().contains("ToolCodeFixture")));

        AssertionError failure = assertThrows(AssertionError.class, () -> UnifiedStyleRules.ALL.check(broken));

        String message = failure.getMessage();
        assertTrue(message.contains("Scenario sentence: every test in a Unified UI Test reads as a sentence"), message);
        assertTrue(message.contains("No raw waits: Unified UI Tests wait through the Unified API only"), message);
        assertTrue(message.contains("Testing Tool code needs its Capability: a test that leaves the Unified API "
                + "has @RequiresTestingTool"), message);
    }
}
