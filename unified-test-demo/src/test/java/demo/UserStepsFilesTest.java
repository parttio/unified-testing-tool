package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs Unified UI Tests through the JUnit Launcher, on this run's Testing Tool, and reads the User Steps they
 * left in the evidence directory: {@code <class>/<method>/user-steps.txt} and {@code user-steps.json}.
 */
class UserStepsFilesTest {

    /** Fails on purpose inside a step; tagged so Surefire doesn't run it on its own. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class FailingInAStep {
        @Test
        @DisplayName("A typo in a label fails inside the step")
        void fails(UnifiedPage page) {
            page.navigate(PeopleView.class);
            page.step("Edit Alan Turing", () -> {
                page.find(UnifiedGrid.class).row().withCellText("Name", "Alan Turing").click();
                page.find(UnifiedTextField.class).withLabel("Nmae").setValue("Alan M. Turing");
            });
            page.find(UnifiedButton.class).withText("Save").click();
        }
    }

    private static TestingTool tool() {
        return TestingTool.parse(System.getProperty("vaadin.testing.tool", "browserless"));
    }

    private static TestExecutionSummary run(Class<?> testClass, Path evidenceDir) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .configurationParameter("vaadin.testing.tool", tool().name().toLowerCase())
                .configurationParameter("vaadin.testing.evidence-dir", evidenceDir.toString())
                // these tests pin the User Steps' text; what the machine's application logs meanwhile is not theirs
                .configurationParameter("vaadin.testing.user-steps.server-log", "off")
                .configurationParameter("vaadin.testing.timeout", "2s")
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        return listener.getSummary();
    }

    @Test
    void writesTheUserStepsOfAPassingTestWithNestedSteps(@TempDir Path dir) throws Exception {
        TestExecutionSummary summary = run(UserStepsTest.class, dir);
        assertEquals(1, summary.getTestsSucceededCount(), () -> summary.getFailures().toString());

        Path test = dir.resolve("UserStepsTest").resolve("editsAPersonInNestedSteps");
        List<String> lines = Files.readString(test.resolve("user-steps.txt")).lines().toList();
        String tool = tool().name().toLowerCase();
        assertTrue(lines.get(0).matches("✔ UserStepsTest › Editing a person in nested steps updates the grid   "
                + tool + " · \\d+\\.\\d s"), lines.get(0));
        assertEquals(List.of(
                "   1  open People",
                "   2  Edit Alan Turing",
                "      2.1  click row where Name is “Alan Turing” in grid",
                "      2.2  Change the name",
                "         2.2.1  fill text field “Name” with “Alan M. Turing”",
                "         2.2.2  fill text field “Email” with “••••”",
                "      2.3  click button “Save”",
                "   3  see notification “Saved Alan M. Turing”",
                "   4  see row where Name is “Alan M. Turing” with Email “••••” in grid"),
                lines.subList(1, lines.size()));

        JsonNode json = new ObjectMapper().readTree(Files.readString(test.resolve("user-steps.json")));
        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals(UserStepsTest.class.getName(), json.get("testClass").asString());
        assertEquals("editsAPersonInNestedSteps", json.get("testMethod").asString());
        assertEquals("Editing a person in nested steps updates the grid", json.get("displayName").asString());
        assertEquals(tool, json.get("tool").asString());
        assertEquals("passed", json.get("status").asString());
        assertEquals(7, json.get("stepCount").asInt());
        assertFalse(json.toString().contains("alan@example.com"), "the secret stays out of the report");

        JsonNode task = json.get("steps").get(1);
        assertEquals("group", task.get("type").asString());
        assertEquals("Edit Alan Turing", task.get("name").asString());
        assertEquals("UserStepsTest.java", task.get("callSite").get("file").asString());
        assertEquals("editPerson", task.get("callSite").get("method").asString());
        JsonNode nested = task.get("steps").get(1);
        assertEquals("2.2", nested.get("index").asString());
        assertEquals("Change the name", nested.get("name").asString());
        JsonNode fill = nested.get("steps").get(0);
        assertEquals("fill", fill.get("verb").asString());
        assertEquals("text field", fill.get("kind").asString());
        assertEquals("Name", fill.get("name").asString());
        assertEquals("Alan M. Turing", fill.get("values").get(0).asString());
        assertEquals("passed", fill.get("status").asString());
        assertEquals("UserStepsTest.java", fill.get("callSite").get("file").asString());
        JsonNode row = task.get("steps").get(0);
        assertEquals("row", row.get("kind").asString());
        assertEquals("grid", row.get("scope").asString());
    }

    @Test
    void marksTheFailedStepAndPointsAtTheStepThenTheInnerLine(@TempDir Path dir) throws Exception {
        TestExecutionSummary summary = run(FailingInAStep.class, dir);
        assertEquals(1, summary.getTotalFailureCount());

        Path test = dir.resolve("UserStepsFilesTest$FailingInAStep").resolve("fails");
        String text = Files.readString(test.resolve("user-steps.txt"));
        List<String> lines = text.lines().toList();
        assertTrue(lines.get(0).startsWith("✘ UserStepsFilesTest.FailingInAStep › A typo in a label fails inside"
                + " the step   " + tool().name().toLowerCase() + " · "), lines.get(0));
        assertEquals(List.of(
                "   1  open People",
                "   2  Edit Alan Turing",
                "      2.1  click row where Name is “Alan Turing” in grid",
                "      2.2  fill text field “Nmae” with “Alan M. Turing”   ✘"), lines.subList(1, 5));
        assertEquals("   ✘ UnifiedAssertionError: Expected exactly one text field labelled “Nmae”, found none",
                lines.get(5), text);
        assertEquals("     Did you mean  find(UnifiedTextField.class).withLabel(\"Name\")", lines.get(6), text);
        assertTrue(lines.get(7).matches("     at UserStepsFilesTest\\.java:\\d+ \\(fails\\)"
                + " ← UserStepsFilesTest\\.java:\\d+ \\(fails\\)"), text);
        assertEquals(8, lines.size(), text);

        Throwable failure = summary.getFailures().get(0).getException();
        assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(s -> s.getMessage().contains(text)),
                "the failure carries the User Steps: " + Arrays.toString(failure.getSuppressed()));

        JsonNode json = new ObjectMapper().readTree(Files.readString(test.resolve("user-steps.json")));
        assertEquals("failed", json.get("status").asString());
        assertEquals("2.2", json.get("failure").get("stepIndex").asString());
        JsonNode codePath = json.get("failure").get("codePath");
        assertEquals(2, codePath.size());
        assertTrue(codePath.get(0).get("line").asInt() < codePath.get(1).get("line").asInt(),
                "the step's call site first, then the line inside it: " + codePath);
        assertEquals("failed", json.get("steps").get(1).get("status").asString());
    }
}
