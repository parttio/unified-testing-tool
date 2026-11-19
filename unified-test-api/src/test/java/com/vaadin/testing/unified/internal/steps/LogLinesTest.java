package com.vaadin.testing.unified.internal.steps;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.internal.logs.ServerLogs;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The application's log lines and the console's errors, under the User Step during which they were logged. */
class LogLinesTest {

    private static final Logger SERVICE = LoggerFactory.getLogger("demo.PersonService");

    private final FakePage fake = new FakePage()
            .add("vaadin-text-field", "label", "Name")
            .add("vaadin-button", "text", "Save");

    private UserStepsReport report() {
        fake.steps.finish();
        return new UserStepsReport("demo.EditPersonTest", "editsAPerson", "Edits", "browserless", "passed", null,
                fake.steps);
    }

    @Test
    void aLineLoggedDuringAStepShowsUnderItAndOneLoggedBetweenStepsUnderTheStepBefore() throws Exception {
        UnifiedPage page = fake.page;
        fake.onClick = () -> SERVICE.error("Could not save {}", "Alan", new IllegalStateException("no id\nat …"));
        try (ServerLogs.Listening listening = ServerLogs.start(fake.steps, ServerLogs.Level.WARN)) {
            SERVICE.warn("Slow start");                                   // before the first entry
            page.navigate(UserStepWordingTest.PeopleView.class);
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan");
            SERVICE.warn("Cache\n  miss");                                // a late effect of the fill
            SERVICE.info("not a warning");
            page.step("Save", () -> page.find(UnifiedButton.class).withText("Save").click());
        }
        SERVICE.error("after the test");                                  // nobody listens any more

        UserStepsReport report = report();
        assertEquals(List.of(
                "⚠ server WARN PersonService: Slow start",
                "1  open People",
                "2  fill text field “Name” with “Alan”",
                "   ⚠ server WARN PersonService: Cache miss",
                "3  Save",
                "   3.1  click button “Save”",
                "      ⚠ server ERROR PersonService: Could not save Alan (IllegalStateException: no id)"),
                report.text().lines().skip(1).map(l -> l.substring(3)).toList());

        JsonNode json = new ObjectMapper().readTree(report.json());
        assertEquals("Slow start", json.get("logsBeforeFirstEntry").get(0).get("message").asString());
        JsonNode click = json.get("steps").get(2).get("steps").get(0).get("logs").get(0);
        assertEquals("server", click.get("source").asString());
        assertEquals("ERROR", click.get("level").asString());
        assertEquals("demo.PersonService", click.get("logger").asString());
        assertEquals("Could not save Alan", click.get("message").asString());
        assertEquals("IllegalStateException: no id", click.get("error").asString());
        assertEquals("server ERROR PersonService: Could not save Alan (IllegalStateException: no id)",
                click.get("text").asString());
        assertFalse(json.get("steps").get(0).has("logs"));
    }

    @Test
    void theLevelSettingLeavesWarningsOut() {
        try (ServerLogs.Listening listening = ServerLogs.start(fake.steps, ServerLogs.Level.ERROR)) {
            fake.page.navigate(UserStepWordingTest.PeopleView.class);
            SERVICE.warn("a warning");
            SERVICE.error("an error");
            LoggerFactory.getLogger("com.vaadin.browserless.TesterRegistry").error("a Testing Tool's own line");
            java.util.logging.Logger.getLogger("demo.Jul").severe("through java.util.logging: not Logback's");
        }

        assertEquals(List.of("server ERROR PersonService: an error"),
                fake.steps.logs(fake.steps.entries().get(0)).stream().map(StepRecorder.LogLine::text).toList());
    }

    @Test
    void consoleErrorsAreReadAfterEachStepOfTheirWindow() {
        UnifiedPage page = fake.page;
        page.navigate(UserStepWordingTest.PeopleView.class);
        fake.onClick = () -> fake.console.add("Uncaught TypeError: x is undefined");
        page.find(UnifiedButton.class).withText("Save").click();

        assertEquals(List.of("   1  open People", "   2  click button “Save”",
                "      ⚠ console ERROR: Uncaught TypeError: x is undefined"), report().text().lines().skip(1).toList());
    }

    @Test
    void hasNoErrorsFailsWithWhatWasLogged() {
        UnifiedPage page = fake.page;
        try (ServerLogs.Listening listening = ServerLogs.start(fake.steps, ServerLogs.Level.WARN)) {
            page.navigate(UserStepWordingTest.PeopleView.class);
            SERVICE.warn("a warning is not an error");
            assertThat(page).hasNoErrors();

            fake.onClick = () -> SERVICE.error("Could not save");
            page.find(UnifiedButton.class).withText("Save").click();
            fake.console.add("Failed to load resource: 500");

            UnifiedAssertionError failure = assertThrows(UnifiedAssertionError.class, () -> assertThat(page).hasNoErrors());
            assertTrue(failure.getMessage().contains("server ERROR PersonService: Could not save"), failure.getMessage());
            assertTrue(failure.getMessage().contains("console ERROR: Failed to load resource: 500"), failure.getMessage());
        }
        List<String> lines = report().text().lines().toList();
        assertTrue(lines.contains("   2  see no errors"), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("   4  see no errors") && l.endsWith("✘")), lines.toString());
    }

    @Test
    void anApplicationThatLogsInALoopFillsNoReport() {
        fake.page.navigate(UserStepWordingTest.PeopleView.class);
        for (int i = 0; i < StepRecorder.MAX_LOG_LINES + 7; i++) {
            fake.steps.logged(new StepRecorder.LogLine(StepRecorder.LogLine.SERVER, "WARN", "demo.Loop", "again", null));
        }

        String text = report().text();
        assertEquals(StepRecorder.MAX_LOG_LINES, text.lines().filter(l -> l.contains("server WARN Loop: again")).count());
        assertTrue(text.endsWith("   ⚠ … 7 more log lines left out\n"), text);
    }
}
