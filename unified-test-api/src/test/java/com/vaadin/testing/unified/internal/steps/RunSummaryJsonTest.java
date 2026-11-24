package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code summary.json}: the run's verdict as data, from the same run as {@code summary.md}. */
class RunSummaryJsonTest {

    private static final String DIR = "target/surefire-reports/unified-evidence/";

    private static ScenarioResult scenario(String json, String evidence) {
        return ScenarioResult.parse(json, DIR + evidence + "/");
    }

    private static List<ScenarioResult> run() {
        return List.of(scenario(RunSummaryTest.ORDER_FAILED, "OrderTest/totals"),
                scenario(RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson"),
                scenario(RunSummaryTest.CHECKOUT_FAILED, "CheckoutTest$AsGuest/paysByCard"),
                scenario(RunSummaryTest.ABORTED, "OrderTest/onlyOnFriday"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(RunSummary summary, Map<String, List<String>> evidence) {
        String json = new RunSummaryJson(summary, s -> evidence.getOrDefault(s.testMethod(), List.of())).json();
        return (Map<String, Object>) JsonReader.parse(json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scenarioOf(Map<String, Object> run, String method) {
        return ((List<Map<String, Object>>) run.get("scenarios")).stream()
                .filter(s -> method.equals(s.get("testMethod"))).findFirst().orElseThrow();
    }

    @Test
    void aPassingRunIsShort() {
        RunSummary summary = new RunSummary("Browserless",
                List.of(scenario(RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson")), 0, null);

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "tool": "browserless",
                  "status": "passed",
                  "startedAt": "2026-11-16T10:00:01Z",
                  "durationMs": 1200,
                  "passed": 1,
                  "failed": 0,
                  "skipped": 0,
                  "scenarios": [
                    {
                      "testClass": "demo.EditPersonTest",
                      "testMethod": "editsAPerson",
                      "displayName": "Editing a person updates the grid",
                      "status": "passed",
                      "startedAt": "2026-11-16T10:00:01Z",
                      "durationMs": 1200,
                      "evidenceDir": "EditPersonTest/editsAPerson/",
                      "userSteps": "EditPersonTest/editsAPerson/user-steps.json"
                    }
                  ]
                }
                """, new RunSummaryJson(summary, s -> List.of()).json());
    }

    @Test
    void theHeaderHasTheVerdictTheCountsAndTheRunsTime() {
        Map<String, Object> run = read(new RunSummary("Playwright", run(), 2, null), Map.of());

        assertEquals(1L, run.get("schemaVersion"));
        assertEquals("playwright", run.get("tool"));
        assertEquals("failed", run.get("status"));
        assertEquals(1L, run.get("passed"));
        assertEquals(2L, run.get("failed"));
        assertEquals(3L, run.get("skipped"), "two skipped without running, one aborted");
        assertEquals("2026-11-16T10:00:01Z", run.get("startedAt"));
        assertEquals(3_001L, run.get("durationMs"), "to the end of the scenario that ended last");
        assertFalse(run.containsKey("changedSince"));
        assertEquals(4, ((List<?>) run.get("scenarios")).size());
    }

    @Test
    void aFailedScenarioCarriesItsFailureAsInUserStepsJson() {
        Map<String, Object> run = read(new RunSummary("Playwright", run(), 0, null),
                Map.of("paysByCard", List.of("demo.CheckoutTest$AsGuest.paysByCard.png",
                        "CheckoutTest$AsGuest/paysByCard/trace.zip")));

        Map<String, Object> failed = scenarioOf(run, "paysByCard");
        assertEquals("failed", failed.get("status"));
        assertEquals("CheckoutTest$AsGuest/paysByCard/", failed.get("evidenceDir"));
        assertEquals("click button “Pay `now`”", failed.get("failedStep"));
        assertEquals(scenario(RunSummaryTest.CHECKOUT_FAILED, "x/y").failure(), failed.get("failure"));
        assertEquals(List.of("demo.CheckoutTest$AsGuest.paysByCard.png", "CheckoutTest$AsGuest/paysByCard/trace.zip"),
                failed.get("evidence"));

        Map<String, Object> outsideAStep = scenarioOf(run, "totals");
        assertFalse(outsideAStep.containsKey("failedStep"));
        assertFalse(outsideAStep.containsKey("evidence"));
        assertEquals("expected: <3> but was: <4>", ((Map<?, ?>) outsideAStep.get("failure")).get("message"));

        Map<String, Object> passed = scenarioOf(run, "editsAPerson");
        assertNull(passed.get("failure"));
        assertEquals("aborted", scenarioOf(run, "onlyOnFriday").get("status"));
    }

    @Test
    void inAPullRequestChangedScenariosSayWhyAndHowTheirStepsDiffer() {
        String checkout = "unified-test-demo/src/test/java/demo/CheckoutTest.java";
        ChangedFiles changes = ChangedFiles.of("origin/main",
                List.of(checkout, "unified-test-demo/src/test/java/demo/tasks/PeopleTasks.java"), List.of(),
                Map.of(checkout, "class CheckoutTest { void other() {} }"));
        BaseRun base = BaseRun.of(List.of(scenario(RunSummaryTest.EDIT_PERSON.replace("open People", "open Home"),
                "EditPersonTest/editsAPerson")), "playwright");

        Map<String, Object> run = read(new RunSummary("Playwright", run(), 0, changes, base), Map.of());

        assertEquals("origin/main", run.get("changedSince"));
        Map<String, Object> changed = scenarioOf(run, "editsAPerson");
        assertEquals("changed", changed.get("change"));
        assertEquals(List.of(Map.of("file", "PeopleTasks.java", "methods", List.of("editPerson"))),
                changed.get("calledFromChangedFiles"));
        assertEquals(Map.of("added", 1L, "removed", 1L), changed.get("stepsDiff"));

        Map<String, Object> added = scenarioOf(run, "paysByCard");
        assertEquals("new", added.get("change"));
        assertFalse(added.containsKey("stepsDiff"), "a new scenario has nothing to compare with");

        Map<String, Object> untouched = scenarioOf(run, "totals");
        assertFalse(untouched.containsKey("change"));
        assertFalse(untouched.containsKey("calledFromChangedFiles"));
    }

    @Test
    void aPassingScenarioWithoutACheckSaysWhichStepsNothingChecks() {
        String readsOnly = RunSummaryTest.EDIT_PERSON.replace("\"verb\": \"see\"", "\"verb\": \"click\"");
        String endsOnAnAction = RunSummaryTest.EDIT_PERSON.replace("editsAPerson", "savesLast")
                .replace("\"verb\": \"open\"", "\"verb\": \"see\"").replace("\"verb\": \"fill\"", "\"verb\": \"see\"");

        Map<String, Object> run = read(new RunSummary("Playwright", List.of(
                scenario(readsOnly, "EditPersonTest/editsAPerson"), scenario(endsOnAnAction, "EditPersonTest/savesLast"),
                scenario(RunSummaryTest.CHECKOUT_FAILED, "CheckoutTest$AsGuest/paysByCard")), 0, null), Map.of());

        // the Escape Hatch gap of EDIT_PERSON may hold a check: only the steps after it are unchecked
        assertEquals("end", scenarioOf(run, "editsAPerson").get("unchecked"));
        assertEquals(List.of("see notification “Saved”"), scenarioOf(run, "editsAPerson").get("uncheckedSteps"));
        assertEquals(true, scenarioOf(run, "editsAPerson").get("escapeHatch"), "it recorded an Escape Hatch gap");
        assertFalse(scenarioOf(run, "savesLast").containsKey("escapeHatch"), "only with unchecked");
        assertFalse(scenarioOf(run, "savesLast").containsKey("unchecked"), "it ends on a check");
        assertFalse(scenarioOf(run, "paysByCard").containsKey("unchecked"), "a failed scenario is not judged");
    }
}
