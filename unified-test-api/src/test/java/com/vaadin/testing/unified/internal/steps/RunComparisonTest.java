package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** This run against a run of the same Unified UI Tests on another Testing Tool. */
class RunComparisonTest {

    private static ScenarioResult here(String json, String evidence) {
        return ScenarioResult.parse(json, "target/surefire-reports/unified-evidence/" + evidence + "/");
    }

    /** The same scenario as a CI artifact of another Testing Tool's job has it. */
    private static ScenarioResult on(String tool, String json, String evidence) {
        return ScenarioResult.parse(json.replace("\"tool\": \"playwright\"", "\"tool\": \"" + tool + "\""),
                "downloaded/" + tool + "/app/target/surefire-reports/unified-evidence/" + evidence + "/");
    }

    /** {@link RunSummaryTest#CHECKOUT_FAILED} when it passes: no failure, every step passed. */
    private static final String CHECKOUT_PASSED = RunSummaryTest.CHECKOUT_FAILED
            .replaceFirst("(?s)\"failure\": \\{.*?\\]\\s*\\},", "").replace("\"failed\"", "\"passed\"")
            .replace(", \"error\": \"AssertionError: no button\"", "");

    private static List<ScenarioResult> thisRun() {
        return List.of(here(RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson"),
                here(RunSummaryTest.CHECKOUT_FAILED, "CheckoutTest$AsGuest/paysByCard"),
                here(RunSummaryTest.ORDER_FAILED, "OrderTest/totals"),
                here(RunSummaryTest.ABORTED, "OrderTest/onlyOnFriday"));
    }

    @Test
    void scenariosThatEndDifferentlyComeFirstThenThoseWithOtherSteps() {
        List<ScenarioResult> browserless = List.of(
                on("browserless", RunSummaryTest.EDIT_PERSON.replace("see notification “Saved”", "see notification “Saved Alan”"),
                        "EditPersonTest/editsAPerson"),
                on("browserless", CHECKOUT_PASSED, "CheckoutTest$AsGuest/paysByCard"),
                on("browserless", RunSummaryTest.ORDER_FAILED, "OrderTest/totals"),
                on("browserless", RunSummaryTest.ABORTED, "OrderTest/onlyOnFriday"),
                on("browserless", RunSummaryTest.EDIT_PERSON.replace("editsAPerson", "browserlessOnly"),
                        "EditPersonTest/browserlessOnly"),
                // this run's own Testing Tool in the directory is not another run
                on("playwright", CHECKOUT_PASSED, "CheckoutTest$AsGuest/paysByCard"));

        List<RunComparison> comparisons = RunComparison.of("PLAYWRIGHT", thisRun(), browserless);

        assertEquals(1, comparisons.size());
        RunComparison c = comparisons.get(0);
        assertEquals("Browserless", c.otherTool());
        assertEquals(3, c.both(), "the aborted scenario ran on neither");
        assertEquals(0, c.onlyHere());
        assertEquals(1, c.onlyThere());
        assertEquals(List.of("paysByCard", "editsAPerson"),
                c.differences().stream().map(d -> d.here().testMethod()).toList());
        assertEquals(1, c.endDifferently());
        assertEquals(1, c.takeDifferentSteps());

        RunComparison.Difference pays = c.differences().get(0);
        assertTrue(pays.endsDifferently());
        assertEquals(Optional.empty(), pays.parting(), "the same steps; the last one failed here");
        RunComparison.Difference edits = c.differences().get(1);
        assertFalse(edits.endsDifferently());
        assertEquals(Optional.of(new RunComparison.Parting(5, "[window 2] see notification “Saved”",
                "[window 2] see notification “Saved Alan”")), edits.parting());
    }

    @Test
    void stepsPartWhereTheFirstLineDiffersOrOneSideEnds() {
        assertEquals(Optional.empty(), RunComparison.parting(List.of("a", "   b"), List.of("a", "   b")));
        assertEquals(Optional.of(new RunComparison.Parting(2, "b", "c")),
                RunComparison.parting(List.of("a", "   b"), List.of("a", "   c", "d")));
        assertEquals(Optional.of(new RunComparison.Parting(2, null, "b")),
                RunComparison.parting(List.of("a"), List.of("a", "b")));
        assertEquals(Optional.of(new RunComparison.Parting(1, "a", null)), RunComparison.parting(List.of("a"), List.of()));
    }

    @Test
    void oneComparisonPerOtherTestingTool() {
        List<RunComparison> comparisons = RunComparison.of("playwright", thisRun(), List.of(
                on("selenium", RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson"),
                on("browserless", RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson")));

        assertEquals(List.of("Browserless", "Selenium"), comparisons.stream().map(RunComparison::otherTool).toList());
        assertEquals(List.of(), comparisons.get(0).differences());
        assertEquals(2, comparisons.get(0).onlyHere(), "two more of this run's scenarios ran, here only");
    }

    @Test
    void theSummarySaysWhichScenariosDifferAndWhere() {
        List<RunComparison> comparisons = RunComparison.of("playwright", thisRun(), List.of(
                on("browserless", RunSummaryTest.EDIT_PERSON.replace("see notification “Saved”", "see notification “Saved Alan”"),
                        "EditPersonTest/editsAPerson"),
                on("browserless", CHECKOUT_PASSED, "CheckoutTest$AsGuest/paysByCard")));

        String md = new RunSummary("Playwright", thisRun(), 0, null, null, comparisons).markdown();

        String section = md.substring(md.indexOf("#### Compared with Browserless"));
        assertEquals("""
                #### Compared with Browserless

                2 scenarios ran on both Testing Tools: 1 ends differently, 1 takes different User Steps. \
                1 ran on Playwright only, 0 on Browserless only.

                Whether both runs are of the same commit is not known: neither run recorded one.

                **A guest pays by card (CheckoutTest.AsGuest)** · failed on Playwright, passed on Browserless

                - On Playwright: `UnifiedAssertionError: Expected exactly one button with text “Pay now”, found none`

                **Editing a person updates the grid (EditPersonTest)** · passed on both, different User Steps

                - Parts at step 5: `[window 2] see notification “Saved”` on Playwright, \
                `[window 2] see notification “Saved Alan”` on Browserless
                """, section);
        assertTrue(md.indexOf("#### Failed scenarios") < md.indexOf("#### Compared with Browserless"), md);
    }

    @Test
    void theSameOnBothTakesOneLine() {
        List<ScenarioResult> run = List.of(here(RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson"));
        List<RunComparison> comparisons = RunComparison.of("playwright", run,
                List.of(on("selenium", RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson")));

        String md = new RunSummary("Playwright", run, 0, null, null, comparisons).markdown();

        assertTrue(md.endsWith("#### Compared with Selenium\n\n1 scenario ran on both Testing Tools: same result and "
                + "same User Steps on each.\n\nWhether both runs are of the same commit is not known: neither run "
                + "recorded one.\n"), md);
    }

    @Test
    void theJsonMarksEachScenarioThatDiffersWithTheOtherRunsResult() {
        List<RunComparison> comparisons = RunComparison.of("browserless",
                List.of(on("browserless", CHECKOUT_PASSED, "CheckoutTest$AsGuest/paysByCard"),
                        on("browserless", RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson")),
                thisRun());
        RunSummary summary = new RunSummary("Browserless",
                List.of(on("browserless", CHECKOUT_PASSED, "CheckoutTest$AsGuest/paysByCard"),
                        on("browserless", RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson")), 0, null, null,
                RunComparison.of("browserless", List.of(), List.of()));
        // compared from the summary's own scenarios, so each difference names one of them
        summary = new RunSummary("Browserless", summary.scenarios(), 0, null, null,
                RunComparison.of("browserless", summary.scenarios(), thisRun()));

        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) JsonReader.parse(new RunSummaryJson(summary, s -> List.of()).json());

        assertEquals(1, comparisons.size());
        assertEquals(List.of(Map.of("tool", "playwright", "both", 2L, "endDifferently", 1L, "takeDifferentSteps", 0L,
                "onlyHere", 0L, "onlyThere", 1L)), json.get("comparedWith"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) json.get("scenarios");
        Map<String, Object> pays = scenarios.stream().filter(s -> "paysByCard".equals(s.get("testMethod"))).findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> differsOn = (List<Map<String, Object>>) pays.get("differsOn");
        assertEquals(1, differsOn.size());
        assertEquals("playwright", differsOn.get(0).get("tool"));
        assertEquals("failed", differsOn.get(0).get("status"));
        assertEquals("none", ((Map<?, ?>) differsOn.get(0).get("failure")).get("found"));
        assertFalse(differsOn.get(0).containsKey("partsAt"), "the same steps");
        assertFalse(scenarios.stream().filter(s -> "editsAPerson".equals(s.get("testMethod"))).findFirst().orElseThrow()
                .containsKey("differsOn"));
    }

    @Test
    void evidenceOfSeveralCommitsForOneTestingToolIsNotTheSameCode() {
        List<ScenarioResult> run = List.of(here(RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson"));
        Revision mine = new Revision("a".repeat(40), null);
        List<RunComparison> comparisons = RunComparison.of("playwright", run,
                List.of(on("selenium", RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson")),
                List.of(new Revision.OfRun("selenium", mine), new Revision.OfRun("selenium", new Revision("b".repeat(40), null)),
                        new Revision.OfRun("browserless", new Revision("c".repeat(40), null)),
                        new Revision.OfRun("selenium", null)));

        assertEquals(Optional.of(false), comparisons.get(0).sameCode(mine));
        String md = new RunSummary("Playwright", run, 0, null, null, comparisons, mine).markdown();
        assertTrue(md.contains("**Not the same code.** This run is of commit `aaaaaaa`, the Selenium run of several commits "
                + "(`aaaaaaa`, `bbbbbbb`)."), md);
    }

    @Test
    void aSummaryJsonSaysWhichRunItIsOf() {
        assertEquals(Optional.of(new Revision.OfRun("playwright", new Revision("a".repeat(40), "123456789abc"))),
                Revision.read("{\"schemaVersion\": 1, \"tool\": \"playwright\", \"commit\": \"" + "a".repeat(40)
                        + "\", \"uncommittedChanges\": \"123456789abc\", \"scenarios\": []}"));
        assertEquals(Optional.of(new Revision.OfRun("playwright", null)),
                Revision.read("{\"tool\": \"playwright\", \"scenarios\": []}"));
        assertEquals(Optional.empty(), Revision.read("{\"tool\": \"playwright\"}"), "another tool's summary.json");
        assertEquals(Optional.empty(), Revision.read("not JSON"));
        assertEquals(12, Revision.fingerprint("diff --git a/A.java b/A.java").length());
    }
}
