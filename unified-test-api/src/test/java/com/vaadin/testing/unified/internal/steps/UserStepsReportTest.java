package com.vaadin.testing.unified.internal.steps;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** {@code user-steps.txt} and {@code user-steps.json} (schema version 1). */
public class UserStepsReportTest {

    private final FakePage fake = new FakePage()
            .add("vaadin-text-field", "label", "Name")
            .add("vaadin-button", "text", "Save");

    /** Runs a failing scenario: open, a step group with two steps, a gap, a failing check in a second window. */
    private AssertionError runFailingScenario() {
        UnifiedPage page = fake.page;
        page.navigate(UserStepWordingTest.PeopleView.class);
        page.step("Edit \"Alan\"\n Turing", () -> {
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
            page.find(UnifiedButton.class).withText("Save").click();
        });
        page.tryUnwrap(Object.class);
        UnifiedPage other = page.openWindow();
        return assertThrows(AssertionError.class, () -> other.step("Check",
                () -> assertThat(other.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing")));
    }

    private UserStepsReport report(AssertionError failure) {
        fake.steps.finish();
        return new UserStepsReport("demo.EditPersonTest", "editsAPerson", "Editing a person updates the grid",
                "browserless", "failed", failure, fake.steps);
    }

    @Test
    void textListsTheStepsAndMarksTheFailedOne() {
        String text = report(runFailingScenario()).text();
        List<String> lines = text.lines().toList();

        assertTrue(lines.get(0).matches(
                "✘ EditPersonTest › Editing a person updates the grid   browserless · \\d+\\.\\d s"), lines.get(0));
        assertEquals(List.of(
                "   1  open People",
                "   2  Edit \"Alan\" Turing",
                "      2.1  fill text field “Name” with “Alan M. Turing”",
                "      2.2  click button “Save”",
                "   … 1 call outside the Unified API (Escape Hatch)",
                "   3  open window 2",
                "   4  [window 2] Check",
                "      4.1  [window 2] see notification “Saved Alan M. Turing”   ✘"), lines.subList(1, 9));
        assertEquals("   ✘ UnifiedAssertionError: Expected exactly one notification, found none", lines.get(9),
                text);
        assertTrue(lines.get(10).matches("     at UserStepsReportTest\\.java:\\d+ \\(runFailingScenario\\)"
                + " ← UserStepsReportTest\\.java:\\d+ \\(runFailingScenario\\)"), lines.get(10));
        assertEquals(11, lines.size(), text);
    }

    @Test
    void aFailureWithASuggestionShowsItsDidYouMeanButNotItsNearMisses() {
        UnifiedPage page = fake.page;
        AssertionError failure = assertThrows(AssertionError.class,
                () -> page.find(UnifiedTextField.class).withLabel("Nmae").setValue("Alan"));

        String text = report(failure).text();
        List<String> lines = text.lines().toList();

        assertEquals(List.of(
                "   1  fill text field “Nmae” with “Alan”   ✘",
                "   ✘ UnifiedAssertionError: Expected exactly one text field labelled “Nmae”, found none",
                "     Did you mean  find(UnifiedTextField.class).withLabel(\"Name\")"), lines.subList(1, 4), text);
        assertTrue(lines.get(4).startsWith("     at UserStepsReportTest.java:"), lines.get(4));
        assertEquals(5, lines.size(), text);
        assertFalse(text.contains("Nearby"), text);
    }

    @Test
    void valuesReadBetweenUserStepsAreCountedAndListedNowhere() throws Exception {
        UnifiedPage page = fake.page;
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
        UnifiedButton save = page.find(UnifiedButton.class).withText("Save");

        name.element().getTag();                     // before the first entry
        page.navigate(UserStepWordingTest.PeopleView.class);
        name.setValue("Alan");
        name.getValue();                             // a Wrapper getter
        name.element().getProperty("value");         // a Unified Element getter
        assertThat(name).hasValue("Alan");           // the getters a check polls are part of its step
        page.step("Save", () -> {
            save.isEnabled();                        // after the step group started
            save.click();
        });

        fake.steps.finish();
        UserStepsReport report = new UserStepsReport("demo.EditPersonTest", "editsAPerson", "Edits", "browserless",
                "passed", null, fake.steps);
        JsonNode json = new ObjectMapper().readTree(report.json());
        assertEquals(1, json.get("readsBeforeFirstEntry").asInt());
        JsonNode steps = json.get("steps");
        assertFalse(steps.get(0).has("readsAfter"), "open People");
        assertEquals(2, steps.get(1).get("readsAfter").asInt(), "after the fill");
        assertFalse(steps.get(2).has("readsAfter"), "the check");
        assertEquals(1, steps.get(3).get("readsAfter").asInt(), "the step group");
        assertFalse(steps.get(3).get("steps").get(0).has("readsAfter"), "the click");
        assertEquals(List.of("1  open People", "2  fill text field “Name” with “Alan”",
                "3  see text field “Name” with value “Alan”", "4  Save", "   4.1  click button “Save”"),
                report.text().lines().skip(1).map(l -> l.substring(3)).toList());

        // the scenario ends on a click nothing checks, until the test reads a value after it
        ScenarioResult scenario = ScenarioResult.parse(report.json(), "EditPersonTest/editsAPerson/");
        assertEquals(java.util.Optional.of(new ScenarioResult.Unchecked(false, List.of("click button “Save”"))),
                scenario.unchecked());
        save.getText();
        assertEquals(java.util.Optional.empty(),
                ScenarioResult.parse(report.json(), "EditPersonTest/editsAPerson/").unchecked());
    }

    @Test
    void jsonNamesTheErrorTheLastAttemptThrew() throws Exception {
        fake.findErrors.put("vaadin-notification",
                new UnsupportedOperationException("Browserless can't read this notification"));
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(fake.page.find(UnifiedNotification.class)).exists());

        JsonNode fail = new ObjectMapper().readTree(report(failure).json()).get("failure");

        assertEquals("UnsupportedOperationException: Browserless can't read this notification",
                fail.get("cause").asString());
    }

    @Test
    void jsonFollowsSchemaVersion1() throws Exception {
        AssertionError failure = runFailingScenario();
        JsonNode json = new ObjectMapper().readTree(report(failure).json());

        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("demo.EditPersonTest", json.get("testClass").asString());
        assertEquals("editsAPerson", json.get("testMethod").asString());
        assertEquals("Editing a person updates the grid", json.get("displayName").asString());
        assertEquals("browserless", json.get("tool").asString());
        assertEquals("failed", json.get("status").asString());
        assertTrue(json.get("durationMs").asLong() >= 0);
        assertEquals(5, json.get("stepCount").asInt());
        assertTrue(json.get("startedAt").asString().endsWith("Z"));

        JsonNode fail = json.get("failure");
        assertEquals("com.vaadin.testing.unified.UnifiedAssertionError", fail.get("type").asString());
        assertEquals("exactly one notification", fail.get("expected").asString());
        assertEquals("none", fail.get("found").asString());
        assertFalse(fail.has("nearMisses"));
        assertFalse(fail.has("suggestion"));
        assertFalse(fail.has("cause"));
        assertEquals("4.1", fail.get("stepIndex").asString());
        assertEquals(5, fail.get("stepNumber").asInt());
        assertEquals(2, fail.get("codePath").size());
        assertEquals("UserStepsReportTest.java", fail.get("codePath").get(0).get("file").asString());

        JsonNode steps = json.get("steps");
        assertEquals(5, steps.size());
        JsonNode open = steps.get(0);
        assertEquals("step", open.get("type").asString());
        assertEquals("1", open.get("index").asString());
        assertEquals("open People", open.get("text").asString());
        assertEquals("open", open.get("verb").asString());
        assertEquals("passed", open.get("status").asString());
        assertFalse(open.has("kind"));

        JsonNode group = steps.get(1);
        assertEquals("group", group.get("type").asString());
        assertEquals("Edit \"Alan\"\n Turing", group.get("name").asString());
        assertEquals("runFailingScenario", group.get("callSite").get("method").asString());
        JsonNode fill = group.get("steps").get(0);
        assertEquals("2.1", fill.get("index").asString());
        assertEquals("fill", fill.get("verb").asString());
        assertEquals("text field", fill.get("kind").asString());
        assertEquals("Name", fill.get("name").asString());
        assertEquals("Alan M. Turing", fill.get("values").get(0).asString());
        assertTrue(fill.get("callSite").get("line").asInt() > 0);

        JsonNode gap = steps.get(2);
        assertEquals("gap", gap.get("type").asString());
        assertEquals("Escape Hatch", gap.get("reason").asString());
        assertEquals(1, gap.get("count").asInt());

        JsonNode check = steps.get(4).get("steps").get(0);
        assertEquals("window 2", check.get("window").asString());
        assertEquals("failed", check.get("status").asString());
        assertEquals("see", check.get("verb").asString());
        assertEquals("UnifiedAssertionError: Expected exactly one notification, found none",
                check.get("error").asString());
        assertEquals("failed", steps.get(4).get("status").asString());
    }

    @Test
    void aPassingTestHasNoFailureAndBothFilesAreWritten(@TempDir Path dir) throws Exception {
        fake.page.find(UnifiedButton.class).withText("Save").click();
        fake.steps.finish();
        UserStepsReport report = new UserStepsReport("demo.SaveTest", "saves", "saves", "playwright", "passed",
                null, fake.steps);

        List<Path> files = report.write(dir.resolve("SaveTest/saves"));

        assertEquals(List.of(dir.resolve("SaveTest/saves/user-steps.txt"), dir.resolve("SaveTest/saves/user-steps.json")),
                files);
        String text = Files.readString(files.get(0));
        assertTrue(text.startsWith("✔ SaveTest › saves   playwright · "), text);
        assertTrue(text.endsWith("\n   1  click button “Save”\n"), text);
        JsonNode json = new ObjectMapper().readTree(Files.readString(files.get(1)));
        assertEquals("passed", json.get("status").asString());
        assertFalse(json.has("failure"));
    }

    @Test
    void aFailureOutsideAnyStepPointsAtItsOwnLine() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> fake.page.step("Plain JUnit", () -> { throw new AssertionError("plain"); }));
        String text = report(failure).text();

        assertTrue(text.contains("   1  Plain JUnit\n   ✘ AssertionError: plain\n     at UserStepsReportTest.java:"),
                text);
        assertEquals(2, report(failure).codePath().size());
    }

    @Test
    void jsonEscapesControlCharacters() {
        UserStepsReport.Json json = new UserStepsReport.Json();
        json.open('{');
        json.field("a", "quote \" backslash \\ tab \t bell \u0007");
        json.key("list").open('[');
        json.close(']');
        json.close('}');

        assertEquals("{\n  \"a\": \"quote \\\" backslash \\\\ tab \\t bell \\u0007\",\n  \"list\": []\n}",
                json.toString());
    }
}
