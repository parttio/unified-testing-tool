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
        assertTrue(lines.get(9).startsWith("   ✘ AssertionError: No component matches UnifiedNotification"),
                lines.get(9));
        assertTrue(lines.get(10).matches("     at UserStepsReportTest\\.java:\\d+ \\(runFailingScenario\\)"
                + " ← UserStepsReportTest\\.java:\\d+ \\(runFailingScenario\\)"), lines.get(10));
        assertEquals(11, lines.size(), text);
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
        assertEquals("java.lang.AssertionError", fail.get("type").asString());
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
        assertTrue(check.get("error").asString().startsWith("AssertionError: No component matches"));
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
