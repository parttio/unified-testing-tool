package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedTextField;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@code summary.md} from the run's {@code user-steps.json}, and which scenarios a change touches. */
class RunSummaryTest {

    /** Editing a person: a User Task in a shared Tasks class, then a check in the test. */
    static final String EDIT_PERSON = """
            {
              "schemaVersion": 1,
              "testClass": "demo.EditPersonTest",
              "testMethod": "editsAPerson",
              "displayName": "Editing a person updates the grid",
              "tool": "playwright",
              "status": "passed",
              "startedAt": "2026-11-16T10:00:01Z",
              "durationMs": 1200,
              "stepCount": 3,
              "futureField": {"ignored": [1, 2.5, true, null]},
              "steps": [
                {"type": "step", "index": "1", "number": 1, "text": "open People", "verb": "open",
                 "status": "passed", "durationMs": 10,
                 "callSite": {"file": "EditPersonTest.java", "line": 12, "className": "demo.EditPersonTest", "method": "editsAPerson"}},
                {"type": "group", "index": "2", "name": "Edit Alan Turing", "status": "passed", "durationMs": 30,
                 "callSite": {"file": "PeopleTasks.java", "line": 8, "className": "demo.tasks.PeopleTasks", "method": "editPerson"},
                 "steps": [
                   {"type": "step", "index": "2.1", "number": 2, "text": "fill text field \\u201cName\\u201d with \\u201cAlan M. Turing\\u201d",
                    "verb": "fill", "status": "passed", "durationMs": 5,
                    "callSite": {"file": "PeopleTasks.java", "line": 9, "className": "demo.tasks.PeopleTasks", "method": "editPerson"}}
                 ]},
                {"type": "gap", "text": "… 1 call outside the Unified API (Escape Hatch)", "reason": "Escape Hatch", "count": 1,
                 "status": "passed"},
                {"type": "step", "index": "3", "number": 3, "text": "see notification “Saved”", "verb": "see",
                 "window": "window 2", "status": "passed", "durationMs": 3,
                 "callSite": {"file": "EditPersonTest.java", "line": 14, "className": "demo.EditPersonTest", "method": "editsAPerson"}}
              ]
            }
            """;

    /** A failure in User Step 2, inside a step group of a nested class. */
    static final String CHECKOUT_FAILED = """
            {
              "schemaVersion": 1,
              "testClass": "demo.CheckoutTest$AsGuest",
              "testMethod": "paysByCard",
              "displayName": "A guest pays by card",
              "tool": "playwright",
              "status": "failed",
              "startedAt": "2026-11-16T10:00:02Z",
              "durationMs": 2000,
              "stepCount": 2,
              "failure": {
                "type": "com.vaadin.testing.unified.UnifiedAssertionError",
                "message": "A guest pays by card\\n  Failed at     User Step 2 of 2",
                "expected": "exactly one button with text “Pay now”",
                "found": "none",
                "nearMisses": ["button “Pay_now”", "button “Cancel”"],
                "suggestion": "find(UnifiedButton.class).withText(\\\"Pay_now\\\")",
                "stepIndex": "1.2",
                "stepNumber": 2,
                "codePath": [
                  {"file": "CheckoutTest.java", "line": 30, "className": "demo.CheckoutTest$AsGuest", "method": "paysByCard"}
                ]
              },
              "steps": [
                {"type": "group", "index": "1", "name": "Pay", "status": "failed", "durationMs": 1900,
                 "callSite": {"file": "CheckoutTest.java", "line": 30, "className": "demo.CheckoutTest$AsGuest", "method": "paysByCard"},
                 "steps": [
                   {"type": "step", "index": "1.1", "number": 1, "text": "open Checkout", "verb": "open", "status": "passed",
                    "durationMs": 10,
                    "callSite": {"file": "CheckoutTest.java", "line": 31, "className": "demo.CheckoutTest$AsGuest", "method": "paysByCard"}},
                   {"type": "step", "index": "1.2", "number": 2, "text": "click button “Pay `now`”", "verb": "click",
                    "status": "failed", "durationMs": 1800, "error": "AssertionError: no button",
                    "callSite": {"file": "CheckoutTest.java", "line": 32, "className": "demo.CheckoutTest$AsGuest", "method": "paysByCard"}}
                 ]}
              ]
            }
            """;

    /** Failed outside any User Step: a plain JUnit assertion. */
    static final String ORDER_FAILED = """
            {"schemaVersion": 1, "testClass": "demo.OrderTest", "testMethod": "totals", "displayName": "totals",
             "tool": "playwright", "status": "failed", "startedAt": "2026-11-16T10:00:03Z", "durationMs": 5, "stepCount": 0,
             "failure": {"type": "org.opentest4j.AssertionFailedError", "message": "expected: <3> but was: <4>",
                         "codePath": [{"file": "OrderTest.java", "line": 21, "className": "demo.OrderTest", "method": "totals"}]},
             "steps": []}
            """;

    static final String ABORTED = """
            {"schemaVersion": 1, "testClass": "demo.OrderTest", "testMethod": "onlyOnFriday", "displayName": "onlyOnFriday",
             "tool": "playwright", "status": "aborted", "startedAt": "2026-11-16T10:00:04Z", "durationMs": 1, "stepCount": 0,
             "steps": []}
            """;

    private static ScenarioResult scenario(String json, String evidence) {
        return ScenarioResult.parse(json, "target/surefire-reports/unified-evidence/" + evidence + "/");
    }

    private static List<ScenarioResult> run() {
        return List.of(scenario(ORDER_FAILED, "OrderTest/totals"), scenario(EDIT_PERSON, "EditPersonTest/editsAPerson"),
                scenario(CHECKOUT_FAILED, "CheckoutTest$AsGuest/paysByCard"), scenario(ABORTED, "OrderTest/onlyOnFriday"));
    }

    @Test
    void summaryWithoutABaseRef() {
        String md = new RunSummary("Playwright", run(), 2, null).markdown();

        assertEquals("""
                ### Unified UI Tests · Playwright · 1 passed, 2 failed, 3 skipped

                #### Failed scenarios

                **✘ A guest pays by card (CheckoutTest.AsGuest)**

                - Failed at: User Step 2: ``click button “Pay `now`”``
                - Error: `UnifiedAssertionError: Expected exactly one button with text “Pay now”, found none`
                - Nearby: button “Pay\\_now”, button “Cancel”
                - Did you mean: `find(UnifiedButton.class).withText("Pay_now")`
                - Evidence: `target/surefire-reports/unified-evidence/CheckoutTest$AsGuest/paysByCard/`

                **✘ totals (OrderTest)**

                - Failed at: outside any User Step, `OrderTest.java:21 (totals)`
                - Error: `AssertionFailedError: expected: <3> but was: <4>`
                - Evidence: `target/surefire-reports/unified-evidence/OrderTest/totals/`
                """, md);
    }

    @Test
    void changedInThisPrListsTheScenariosCallingChangedFiles() {
        ChangedFiles changes = ChangedFiles.of("origin/main",
                List.of("unified-test-demo/src/test/java/demo/tasks/PeopleTasks.java",
                        "unified-test-demo/src/test/java/demo/CheckoutTest.java"),
                List.of("README.md"),
                Map.of("unified-test-demo/src/test/java/demo/CheckoutTest.java", "class CheckoutTest { void other() {} }"));

        String md = new RunSummary("Playwright", run(), 0, changes).markdown();

        String changed = md.substring(md.indexOf("#### Changed in this PR"), md.indexOf("#### Failed scenarios"));
        assertEquals("""
                #### Changed in this PR

                2 scenarios call code changed since `origin/main`.

                **✘ A guest pays by card (CheckoutTest.AsGuest)** · new

                ```text
                1  Pay
                   1.1  open Checkout
                   1.2  click button “Pay `now`”   ✘
                ```

                **✔ Editing a person updates the grid (EditPersonTest)** · changed

                Called from changed files: `PeopleTasks.java` (editPerson)

                ```text
                1  open People
                2  Edit Alan Turing
                   2.1  fill text field “Name” with “Alan M. Turing”
                … 1 call outside the Unified API (Escape Hatch)
                3  [window 2] see notification “Saved”
                ```

                """, changed);
        assertTrue(md.startsWith("### Unified UI Tests · Playwright · 1 passed, 2 failed, 1 skipped\n\n#### Changed"), md);
    }

    /** {@link #EDIT_PERSON} on the base branch: another name, and a click and a check instead of the notification. */
    static final String EDIT_PERSON_BASE = EDIT_PERSON
            .replace("\\u201cAlan M. Turing\\u201d", "\\u201cAlan Turing\\u201d")
            .replace("{\"type\": \"step\", \"index\": \"3\", \"number\": 3, \"text\": \"see notification “Saved”\",",
                    "{\"type\": \"step\", \"index\": \"3\", \"number\": 3, \"text\": \"click button “Save”\","
                            + " \"status\": \"passed\"}, {\"type\": \"step\", \"index\": \"4\", \"number\": 4,"
                            + " \"text\": \"see grid “People”\",");

    private static ChangedFiles peopleTasksChanged() {
        return ChangedFiles.of("origin/main", List.of("unified-test-demo/src/test/java/demo/tasks/PeopleTasks.java"),
                List.of(), Map.of());
    }

    private static String changedSection(String md) {
        int failed = md.indexOf("#### Failed scenarios");
        return md.substring(md.indexOf("#### Changed in this PR"), failed < 0 ? md.length() : failed);
    }

    @Test
    void aChangedScenarioShowsHowItsStepsDifferFromTheBaseRun() {
        BaseRun base = BaseRun.of(List.of(scenario(EDIT_PERSON_BASE, "EditPersonTest/editsAPerson")), "playwright");

        String md = new RunSummary("Playwright", run(), 0, peopleTasksChanged(), base).markdown();

        assertEquals("""
                #### Changed in this PR

                1 scenario calls code changed since `origin/main`.

                **✔ Editing a person updates the grid (EditPersonTest)** · changed · User Steps +2 −3 against the base run

                Called from changed files: `PeopleTasks.java` (editPerson)

                ```diff
                  open People
                  Edit Alan Turing
                -    fill text field “Name” with “Alan Turing”
                +    fill text field “Name” with “Alan M. Turing”
                  … 1 call outside the Unified API (Escape Hatch)
                - click button “Save”
                - [window 2] see grid “People”
                + [window 2] see notification “Saved”
                ```

                """, changedSection(md));
    }

    @Test
    void sameStepsAsTheBaseRunTakeOneLine() {
        BaseRun base = BaseRun.of(List.of(scenario(EDIT_PERSON, "EditPersonTest/editsAPerson")), "playwright");

        String md = new RunSummary("Playwright", run(), 0, peopleTasksChanged(), base).markdown();

        assertEquals("""
                #### Changed in this PR

                1 scenario calls code changed since `origin/main`.

                **✔ Editing a person updates the grid (EditPersonTest)** · changed · same User Steps as the base run

                Called from changed files: `PeopleTasks.java` (editPerson)

                """, changedSection(md));
    }

    @Test
    void withoutTheScenarioInTheBaseRunItsStepsShowInFull() {
        BaseRun base = BaseRun.of(List.of(scenario(ORDER_FAILED, "OrderTest/totals")), "playwright");

        String md = new RunSummary("Playwright", run(), 0, peopleTasksChanged(), base).markdown();

        assertTrue(changedSection(md).contains("""
                **✔ Editing a person updates the grid (EditPersonTest)** · changed · not in the base run

                Called from changed files: `PeopleTasks.java` (editPerson)

                ```text
                1  open People
                """), md);
    }

    @Test
    void noBaseRunSaysSoAndShowsStepsInFull() {
        String withNone = new RunSummary("Playwright", run(), 0, peopleTasksChanged(), BaseRun.NONE).markdown();
        String withoutBaseline = new RunSummary("Playwright", run(), 0, peopleTasksChanged()).markdown();

        assertEquals(changedSection(withoutBaseline).replace("since `origin/main`.\n",
                "since `origin/main`.\nNo base run found to compare their User Steps with.\n"),
                changedSection(withNone));
    }

    @Test
    void theBaseRunMatchesByEvidenceDirectoryThenDisplayNameOnTheSameTool() {
        ScenarioResult edit = scenario(EDIT_PERSON, "EditPersonTest/editsAPerson");
        ScenarioResult renamed = scenario(EDIT_PERSON.replace("\"editsAPerson\"", "\"editsSomeone\""),
                "EditPersonTest/editsSomeone");
        ScenarioResult secondInvocation = scenario(EDIT_PERSON, "EditPersonTest/editsAPerson[2]");

        assertEquals("EditPersonTest/editsAPerson[2]", secondInvocation.evidenceKey());
        assertEquals(Optional.of(edit), BaseRun.of(List.of(secondInvocation, edit), "playwright").find(edit));
        assertEquals(Optional.of(renamed), BaseRun.of(List.of(renamed), "playwright").find(edit), "same display name");
        assertEquals(Optional.empty(), BaseRun.of(List.of(renamed, secondInvocation), "playwright").find(edit),
                "two with the display name");
        assertTrue(BaseRun.of(List.of(edit), "selenium").isEmpty(), "another Testing Tool");
    }

    @Test
    void whyNamesEachChangedFileAndTheMethodsCallingFromIt() {
        ChangedFiles changes = ChangedFiles.of("origin/main", List.of("src/test/java/demo/tasks/PeopleTasks.java",
                "src/test/java/demo/EditPersonTest.java"), List.of(), Map.of("src/test/java/demo/EditPersonTest.java",
                "void editsAPerson() {}"));
        RunSummary summary = new RunSummary("Playwright", run(), 0, changes);

        assertEquals(List.of(new RunSummary.Why("EditPersonTest.java", List.of("editsAPerson")),
                new RunSummary.Why("PeopleTasks.java", List.of("editPerson"))),
                summary.why(scenario(EDIT_PERSON, "x")));
        assertTrue(summary.markdown().contains("Called from changed files: `EditPersonTest.java` (editsAPerson), "
                + "`PeopleTasks.java` (editPerson)\n"), summary.markdown());
        assertEquals(List.of(), new RunSummary("Playwright", run(), 0, null).why(scenario(EDIT_PERSON, "x")));
    }

    @Test
    void stepsDiffPutsRemovedLinesBeforeTheAddedOnes() {
        StepsDiff diff = StepsDiff.of(List.of("a", "b", "c"), List.of("a", "x", "c", "d"));

        assertEquals("  a\n- b\n+ x\n  c\n+ d\n", diff.text());
        assertEquals(2, diff.added());
        assertEquals(1, diff.removed());
        assertTrue(StepsDiff.of(List.of("a"), List.of("a")).isEmpty());
    }

    @Test
    void noScenarioTouchedSaysSo() {
        ChangedFiles changes = ChangedFiles.of("origin/main", List.of("docs/guide.md"), List.of(), Map.of());

        String md = new RunSummary("Browserless", run(), 0, changes).markdown();

        assertTrue(md.contains("#### Changed in this PR\n\nNo scenario calls code changed since `origin/main`.\n"), md);
    }

    @Test
    void callSitesMatchChangedFilesByPackagePath() {
        ChangedFiles changes = ChangedFiles.of("origin/main",
                List.of("app/src/test/java/demo/tasks/PeopleTasks.java", "other/src/test/java/elsewhere/OrderTest.java",
                        "app/src/test/java/demo/MyOrderTest.java"),
                List.of(), Map.of());

        assertEquals("demo/tasks/PeopleTasks.java", ChangedFiles.sourcePath("demo.tasks.PeopleTasks", "PeopleTasks.java"));
        assertEquals("demo/UsersTest.java", ChangedFiles.sourcePath("demo.UsersTest$AsAlice", null));
        assertEquals("demo/Steps.kt", ChangedFiles.sourcePath("demo.StepsKt", "Steps.kt"));
        assertEquals("Plain.java", ChangedFiles.sourcePath("Plain", "Plain.java"));

        assertTrue(changes.changedFile("demo.tasks.PeopleTasks", "PeopleTasks.java").isPresent());
        // same file name in another package, and a file whose name ends with it
        assertFalse(changes.changedFile("demo.OrderTest", "OrderTest.java").isPresent());

        assertTrue(changes.touches(scenario(EDIT_PERSON, "x")));
        assertFalse(changes.touches(scenario(ORDER_FAILED, "x")));
    }

    @Test
    void newMeansAddedFileOrMethodMissingOnTheBase() {
        String path = "src/test/java/demo/EditPersonTest.java";
        ScenarioResult edit = scenario(EDIT_PERSON, "x");

        assertTrue(ChangedFiles.of("b", List.of(), List.of(path), Map.of()).isNew(edit), "file added");
        assertTrue(ChangedFiles.of("b", List.of(path), List.of(), Map.of(path, "void editsAPersonLater() {}"))
                .isNew(edit), "method not on the base");
        assertFalse(ChangedFiles.of("b", List.of(path), List.of(), Map.of(path, "void editsAPerson(UnifiedPage page) {}"))
                .isNew(edit), "method on the base");
        assertFalse(ChangedFiles.of("b", List.of("src/test/java/demo/tasks/PeopleTasks.java"), List.of(), Map.of())
                .isNew(edit), "only a User Task changed");
    }

    private static ScenarioResult passing(String method, String steps) {
        return ScenarioResult.parse("""
                {"schemaVersion": 1, "testClass": "demo.SaveTest", "testMethod": "%s", "displayName": "%s",
                 "tool": "playwright", "status": "passed", "startedAt": "2026-11-16T10:00:09Z", "durationMs": 5,
                 "steps": [%s]}
                """.formatted(method, method, steps), "evidence/SaveTest/" + method + "/");
    }

    private static String step(String verb, String text) {
        return "{\"type\": \"step\", \"index\": \"1\", \"number\": 1, \"text\": \"" + text + "\", \"verb\": \""
                + verb + "\", \"status\": \"passed\"}";
    }

    private static String gap(String reason) {
        return "{\"type\": \"gap\", \"text\": \"… 1 call outside the Unified API (" + reason + ")\", \"reason\": \""
                + reason + "\", \"count\": 1, \"status\": \"passed\"}";
    }

    @Test
    void aPassedScenarioIsUncheckedFromItsLastCheckOn() {
        String open = step("open", "open People");
        String click = step("click", "click button “Save”");
        String see = step("see", "see notification “Saved”");
        String task = "{\"type\": \"group\", \"index\": \"2\", \"name\": \"Save\", \"status\": \"passed\", \"steps\": ["
                + click + "]}";

        assertEquals(Optional.of(new ScenarioResult.Unchecked(true, List.of("open People", "click button “Save”"))),
                passing("noCheck", open + "," + task).unchecked());
        assertEquals(Optional.of(new ScenarioResult.Unchecked(false, List.of("click button “Save”"))),
                passing("endsOnAnAction", open + "," + see + "," + task).unchecked());
        assertEquals(Optional.of(new ScenarioResult.Unchecked(true, List.of())), passing("nothing", "").unchecked());
        assertEquals(Optional.empty(), passing("endsOnACheck", open + "," + click + "," + see).unchecked());
        assertEquals(Optional.empty(), passing("seesNothing", open + "," + step("see no", "see no dialog")).unchecked());
    }

    @Test
    void whatMayHoldACheckCountsAsOne() {
        String open = step("open", "open People");

        // the reports can't see into these, so they don't call the scenario unchecked
        assertEquals(Optional.empty(), passing("waits", open + "," + gap("waitUntil")).unchecked());
        assertEquals(Optional.empty(), passing("escapes", open + "," + gap("Escape Hatch")).unchecked());
        // a passing test with a failed step expected the failure (assertThrows)
        assertEquals(Optional.empty(), passing("expectsAFailure",
                open + "," + step("choose", "choose “Mars” in combo box “Country”").replace("passed", "failed")).unchecked());
        // a where(..) condition narrows a lookup: it checks nothing
        assertEquals(Optional.of(new ScenarioResult.Unchecked(true, List.of("open People"))),
                passing("filters", open + "," + gap("where(..) condition")).unchecked());
        // a value read after the last action: the test looked at the result, and asserts on it in plain Java
        assertEquals(Optional.empty(), passing("readsAfterTheClick",
                open + "," + step("click", "click b").replace("}", ", \"readsAfter\": 2}")).unchecked());
        assertEquals(Optional.of(new ScenarioResult.Unchecked(false, List.of("click b"))), passing("readsBeforeTheClick",
                open.replace("}", ", \"readsAfter\": 1}") + "," + step("click", "click b")).unchecked());
        assertEquals(Optional.empty(), ScenarioResult.parse("""
                {"schemaVersion": 1, "testClass": "demo.T", "testMethod": "onlyReads", "status": "passed",
                 "readsBeforeFirstEntry": 1, "steps": []}""", "T/onlyReads/").unchecked());
        // failed and aborted scenarios are not judged
        assertEquals(Optional.empty(), scenario(ORDER_FAILED, "OrderTest/totals").unchecked());
        assertEquals(Optional.empty(), scenario(ABORTED, "OrderTest/onlyOnFriday").unchecked());
    }

    @Test
    void aBackendStepCountsAsACheckAfterTheUserStepsInsideIt() {
        String open = step("open", "open People");
        String click = step("click", "click button “Save”");
        String backend = "{\"type\": \"backend\", \"index\": \"3\", \"name\": \"order is stored\", "
                + "\"text\": \"backend: order is stored\", \"status\": \"passed\", \"steps\": [" + click + "]}";

        assertEquals(Optional.empty(), passing("checksTheDatabase", open + "," + click + "," + backend).unchecked());
        assertEquals(Optional.of(new ScenarioResult.Unchecked(false, List.of("click button “Save”"))),
                passing("seedsThenClicks", backend + "," + click).unchecked());
    }

    @Test
    void theSummaryListsPassingScenariosWithoutACheck() {
        String open = step("open", "open People");
        String see = step("see", "see grid “People”");
        List<ScenarioResult> run = List.of(scenario(EDIT_PERSON, "EditPersonTest/editsAPerson"),
                passing("readsTheGrid", open),
                passing("savesFourTimes", see + "," + step("click", "click button “A`”") + "," + step("click", "click b")
                        + "," + step("click", "click c") + "," + step("click", "click d")));

        String md = new RunSummary("Playwright", run, 0, null).markdown();

        assertEquals("""
                ### Unified UI Tests · Playwright · 3 passed, 0 failed, 0 skipped

                #### Scenarios without a check

                2 passing scenarios end on User Steps that nothing checks: no `see` step follows them, \
                and the test reads no value after them.

                - **readsTheGrid (SaveTest)** · no check
                - **savesFourTimes (SaveTest)** · no check after: ``click button “A`”``, `click b`, `click c` and 1 more
                """, md);
        assertFalse(new RunSummary("Playwright", List.of(scenario(EDIT_PERSON, "x/y")), 0, null).markdown()
                .contains("without a check"));
    }

    @Test
    void aScenarioThatRecordsAnEscapeHatchGapIsAnEscapeHatchScenario() {
        String open = step("open", "open People");
        String inAGroup = "{\"type\": \"group\", \"index\": \"2\", \"name\": \"Save\", \"status\": \"passed\", \"steps\": ["
                + gap("Escape Hatch") + "]}";

        assertTrue(passing("escapes", open + "," + gap("Escape Hatch")).escapeHatch());
        assertTrue(passing("escapesInATask", open + "," + inAGroup).escapeHatch());
        assertFalse(passing("waits", open + "," + gap("waitUntil")).escapeHatch());
        assertFalse(passing("onlyUnified", open).escapeHatch());
    }

    @Test
    void escapeHatchScenariosAreCountedApartFromTheScenariosWithoutACheck() {
        String open = step("open", "open People");
        String click = step("click", "click button “Save”");
        String see = step("see", "see notification “Saved”");
        List<ScenarioResult> run = List.of(
                passing("escapesThenClicks", open + "," + gap("Escape Hatch") + "," + click),
                passing("onlyClicks", open + "," + click),
                passing("seesAfterTheClick", gap("Escape Hatch") + "," + click + "," + see));

        RunSummary summary = new RunSummary("Playwright", run, 0, null);

        assertEquals(List.of("onlyClicks"), summary.unchecked().stream().map(ScenarioResult::testMethod).toList());
        assertEquals(List.of("escapesThenClicks"),
                summary.uncheckedEscapeHatch().stream().map(ScenarioResult::testMethod).toList());
        assertEquals("""
                ### Unified UI Tests · Playwright · 3 passed, 0 failed, 0 skipped

                #### Scenarios without a check

                1 passing scenario ends on User Steps that nothing checks: no `see` step follows them, \
                and the test reads no value after them.

                - **onlyClicks (SaveTest)** · no check

                1 more used the Escape Hatch and ends on User Steps the report sees no check for: escapesThenClicks. \
                Their checks may be assertions the report can't see: move checks of the UI to `assertThat(..)`, \
                and declare the others with `page.backendStep(..)`.
                """, summary.markdown());
    }

    @Test
    void onlyEscapeHatchScenariosUncheckedGiveTheSectionWithJustTheirCount() {
        String click = step("click", "click button “Save”");
        List<ScenarioResult> run = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> passing("escapes" + i, gap("Escape Hatch") + "," + click)).toList();

        String md = new RunSummary("Playwright", run, 0, null).markdown();

        assertEquals("""
                ### Unified UI Tests · Playwright · 4 passed, 0 failed, 0 skipped

                #### Scenarios without a check

                4 passing scenarios used the Escape Hatch and end on User Steps the report sees no check for: \
                escapes0, escapes1, escapes2, … Their checks may be assertions the report can't see: move checks of the UI to `assertThat(..)`, \
                and declare the others with `page.backendStep(..)`.
                """, md);
        assertEquals("""
                ### Unified UI Tests · Playwright · 1 passed, 0 failed, 0 skipped

                #### Scenarios without a check

                1 passing scenario used the Escape Hatch and ends on User Steps the report sees no check for: \
                escapes0. Their checks may be assertions the report can't see: move checks of the UI to `assertThat(..)`, \
                and declare the others with `page.backendStep(..)`.
                """, new RunSummary("Playwright", run.subList(0, 1), 0, null).markdown());
        assertFalse(new RunSummary("Playwright",
                List.of(passing("escapesAndSees", gap("Escape Hatch") + "," + click + "," + step("see", "see b"))), 0, null)
                .markdown().contains("without a check"));
    }

    @Test
    void aLongListOfScenariosWithoutACheckIsCutWithACount() {
        List<ScenarioResult> run = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> passing("reads" + i, step("open", "open People"))).toList();

        String md = new RunSummary("Playwright", run, 0, null).markdown();

        assertTrue(md.contains("12 passing scenarios end on"), md);
        assertEquals(10, md.lines().filter(l -> l.endsWith("· no check")).count(), md);
        assertTrue(md.endsWith("- … and 2 more, in `report.html`\n"), md);
    }

    @Test
    void stepsReadBackFromJsonReadLikeUserStepsTxt() {
        FakePage fake = new FakePage().add("vaadin-text-field", "label", "Name").add("vaadin-button", "text", "Save");
        UnifiedPage page = fake.page;
        page.navigate(UserStepWordingTest.PeopleView.class);
        page.step("Edit \"Alan\"\n Turing", () -> {
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
            page.find(UnifiedButton.class).withText("Save").click();
        });
        page.tryUnwrap(Object.class);
        page.openWindow().find(UnifiedButton.class).withText("Save").click();
        fake.steps.finish();
        UserStepsReport report = new UserStepsReport("demo.EditPersonTest", "editsAPerson", "Editing", "browserless",
                "passed", null, fake.steps);

        ScenarioResult read = ScenarioResult.parse(report.json(), "x/");

        String txtSteps = report.text().lines().skip(1).map(l -> l.substring(3)).reduce("", (a, l) -> a + l + "\n");
        assertEquals(txtSteps, read.stepsText());
        assertEquals("passed", read.status());
        assertTrue(read.callSites().stream().allMatch(s -> RunSummaryTest.class.getName().equals(s.get("className"))), read.callSites().toString());
    }
}
