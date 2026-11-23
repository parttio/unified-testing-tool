package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
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
 * Runs failing Unified UI Tests through the JUnit Launcher, on this run's Testing Tool, and reads their failures:
 * the same message on every Testing Tool, with Near Misses and a suggestion where one is clearly best (spec chapter
 * 12, "Failures").
 */
class FailureMessagesTest {

    /** Fails on purpose, one way per test; tagged so Surefire doesn't run it on its own. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class Failing {

        @Test
        @DisplayName("A renamed label fails with a suggestion")
        void renamedLabel(UnifiedPage page) {
            page.navigate(ContactView.class);
            page.step("Fill in the contact", () ->
                    page.find(UnifiedTextField.class).withLabel("Email").setValue("alan@bletchley.uk"));
        }

        @Test
        @DisplayName("Two fields of the same label are ambiguous")
        void ambiguous(UnifiedPage page) {
            page.navigate(CheckoutView.class);
            page.find(UnifiedTextField.class).withLabel("City").setValue("Turku");
        }

        @Test
        @DisplayName("A lookup without a filter matches every field of the view")
        void noFilter(UnifiedPage page) {
            page.navigate(ProfileView.class);
            page.find(UnifiedTextField.class).setValue("Alan");
        }

        @Test
        @DisplayName("A text that changes but never matches times out")
        void changingText(UnifiedPage page) {
            page.navigate(ContactView.class);
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan Turing");
            page.find(UnifiedButton.class).withText("Save").click();
            assertThat(page.find(UnifiedSpan.class).withId("status")).hasText("Saved Alan M. Turing");
        }

        @Test
        @DisplayName("A disabled button can't be clicked")
        void disabledButton(UnifiedPage page) {
            page.navigate(PeopleView.class);
            page.find(UnifiedButton.class).withText("Save").click();
        }

        @Test
        @DisplayName("A missing dialog fails with Near Misses for the dialog")
        void missingDialog(UnifiedPage page) {
            page.navigate(ReportView.class);
            page.find(UnifiedButton.class).withText("Export").click();
            UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Export Report");
            dialog.find(UnifiedButton.class).withText("Close").click();
        }

        @Test
        @DisplayName("A grid without the column lists the columns it has")
        void missingColumn(UnifiedPage page) {
            page.navigate(UsersView.class);
            assertThat(page.find(UnifiedGrid.class).withTestId("archived")).hasColumn("Archived on");
        }

        @Test
        @DisplayName("A renamed button lists Near Misses by their own text, not their tooltips")
        void renamedButton(UnifiedPage page) {
            page.navigate(ReportView.class);
            page.find(UnifiedButton.class).withText("Export CSV").click();
        }

        @Test
        @DisplayName("A misspelt combo box item lists the items nearby")
        void missingItem(UnifiedPage page) {
            page.navigate(OrderView.class);
            page.find(UnifiedComboBox.class).withLabel("Country").selectItem("Germny");
        }

        @Test
        @DisplayName("A pick the application turns down fails as a Unified failure")
        void rejectedPick(UnifiedPage page) {
            page.navigate(RejectingPickView.class);
            page.find(UnifiedComboBox.class).withLabel("Size").selectItem("Large");
        }
    }

    private static TestingTool tool;
    @TempDir
    static Path dir;
    private static Map<String, UnifiedAssertionError> failures;

    @BeforeAll
    static void runTheFailingTests() {
        tool = TestingTool.parse(System.getProperty("vaadin.testing.tool", "browserless"));
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Failing.class))
                .configurationParameter("vaadin.testing.tool", tool.name().toLowerCase())
                .configurationParameter("vaadin.testing.evidence-dir", dir.toString())
                .configurationParameter("vaadin.testing.timeout", "2s")
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        assertEquals(10, summary.getTotalFailureCount(), () -> summary.getFailures().toString());
        failures = summary.getFailures().stream().collect(Collectors.toMap(
                f -> f.getTestIdentifier().getDisplayName(),
                f -> assertInstanceOf(UnifiedAssertionError.class, f.getException(), f.getException()::toString)));
        // what the CI log shows, for the reader of this test's own output
        failures.values().forEach(f -> System.out.println("UnifiedAssertionError: " + f.getMessage() + "\n"));
    }

    private static String toolLine() {
        return tool == TestingTool.BROWSERLESS ? "browserless" : tool.name().toLowerCase() + " (";
    }

    @Test
    void aRenamedLabelListsNearMissesAndSuggestsTheClosest() throws Exception {
        UnifiedAssertionError failure = failures.get("A renamed label fails with a suggestion");
        List<String> lines = failure.getMessage().lines().toList();

        assertEquals(List.of(
                "A renamed label fails with a suggestion",
                "  Failed at     User Step 2 of 2: fill text field “Email” with “alan@bletchley.uk”",
                "  In step       Fill in the contact",
                "  Expected      exactly one text field labelled “Email”",
                "  Found         none",
                "  Nearby        text field “E-mail”, text field “Name”",
                "  Did you mean  find(UnifiedTextField.class).withLabel(\"E-mail\")",
                "  Waited        2 s (vaadin.testing.timeout)"), lines.subList(0, 8));
        assertTrue(lines.get(8).startsWith("  Tool          " + toolLine()), lines.get(8));
        assertTrue(lines.get(9).matches("  Code          FailureMessagesTest\\.java:\\d+ \\(renamedLabel\\)"
                + " ← FailureMessagesTest\\.java:\\d+ \\(renamedLabel\\)"), lines.get(9));
        Path evidence = dir.resolve("FailureMessagesTest$Failing").resolve("renamedLabel");
        assertEquals("  Evidence      " + evidence.toString().replace('\\', '/') + "/", lines.get(10));
        assertEquals(11, lines.size(), failure.getMessage());

        // the same parts in user-steps.json
        JsonNode json = new ObjectMapper().readTree(Files.readString(evidence.resolve("user-steps.json")))
                .get("failure");
        assertEquals(UnifiedAssertionError.class.getName(), json.get("type").asString());
        assertEquals("exactly one text field labelled “Email”", json.get("expected").asString());
        assertEquals("none", json.get("found").asString());
        assertEquals("text field “E-mail”", json.get("nearMisses").get(0).asString());
        assertEquals("find(UnifiedTextField.class).withLabel(\"E-mail\")", json.get("suggestion").asString());
        // the lookup as the test wrote it, in the same form: what ApplyDidYouMean changes into the suggestion
        assertEquals("find(UnifiedTextField.class).withLabel(\"Email\")", json.get("lookup").asString());
        assertTrue(Files.readString(evidence.resolve("user-steps.txt")).contains(
                "   ✘ UnifiedAssertionError: Expected exactly one text field labelled “Email”, found none\n"
                        + "     Did you mean  find(UnifiedTextField.class).withLabel(\"E-mail\")\n"));
    }

    /** The quick pick by filter (#403) finds nothing: every item is read for the Near Misses, as before. */
    /** The check after a pick fails in the Unified API's own format, on every Testing Tool (#377). */
    @Test
    void aPickThatDoesNotTakeNamesWhatIsSelected() {
        UnifiedAssertionError failure = failures.get("A pick the application turns down fails as a Unified failure");

        assertEquals("item “Large” selected in combo box “Size”", failure.getExpected(), failure.getMessage());
        assertEquals("none", failure.getFound(), failure.getMessage());
    }

    @Test
    void aMissingComboBoxItemListsTheItemsNearby() {
        UnifiedAssertionError failure = failures.get("A misspelt combo box item lists the items nearby");

        assertEquals("item “Germny” in combo box “Country”", failure.getExpected(), failure.getMessage());
        assertEquals("none", failure.getFound(), failure.getMessage());
        assertTrue(failure.getNearMisses().contains("item “Germany”"), failure.getMessage());
        assertEquals(Optional.of("selectItem(\"Germany\")"), failure.getSuggestion(), failure.getMessage());
    }

    @Test
    void severalMatchesAreListedWithTheirScopeAndNoSuggestion() {
        UnifiedAssertionError failure = failures.get("Two fields of the same label are ambiguous");

        assertEquals("exactly one text field labelled “City”", failure.getExpected());
        assertEquals("2: text field “City” in container (test id “billing”), text field “City” in container"
                + " (id “shipping”)", failure.getFound(), failure.getMessage());
        assertEquals(List.of(), failure.getNearMisses());
        assertEquals(Optional.empty(), failure.getSuggestion());
    }

    @Test
    void severalMatchesOutsideAnyContainerNameNoContainer() {
        UnifiedAssertionError failure = failures.get("A lookup without a filter matches every field of the view");

        // in a browser, the nearest ancestor with an id is Flow's own root, ROOT-<number>: no container to name
        assertEquals("3: text field “Name”, text field “Email”, text field", failure.getFound(), failure.getMessage());
    }

    @Test
    void anAssertionTimeoutListsTheValuesSeenWhileWaiting() {
        UnifiedAssertionError failure = failures.get("A text that changes but never matches times out");

        assertEquals("span (id “status”) to have text “Saved Alan M. Turing”", failure.getExpected());
        assertEquals("“Saving…”, then “Saved Alan Turing”", failure.getFound(), failure.getMessage());
        assertEquals(Optional.of("see span “Saved Alan M. Turing” (id “status”)"), failure.getFailedStep());
        assertTrue(failure.getMessage().contains("\n  Found         “Saving…”, then “Saved Alan Turing”\n"),
                failure.getMessage());
    }

    @Test
    void aDisabledButtonIsFoundButNotUsable() {
        UnifiedAssertionError failure = failures.get("A disabled button can't be clicked");

        assertEquals("a visible and enabled button with text “Save”", failure.getExpected());
        assertEquals("1, but disabled", failure.getFound(), failure.getMessage());
        assertEquals(Optional.of("click button “Save”"), failure.getFailedStep());
    }

    @Test
    void aMissingScopeGetsNearMissesForTheScope() {
        UnifiedAssertionError failure = failures.get("A missing dialog fails with Near Misses for the dialog");

        assertEquals(Optional.of("click button “Close” in dialog “Export Report”"), failure.getFailedStep());
        assertEquals("exactly one dialog with header title “Export Report”", failure.getExpected());
        assertEquals("none", failure.getFound());
        assertEquals("dialog “Export report”", failure.getNearMisses().get(0), failure.getMessage());
        // the closed "Help" dialog, where the Testing Tool finds closed dialogs at all, is marked hidden
        failure.getNearMisses().stream().skip(1).forEach(n -> assertEquals("dialog “Help” (hidden)", n));
        assertEquals(Optional.of("find(UnifiedDialog.class).withHeaderTitle(\"Export report\")"),
                failure.getSuggestion());
    }

    @Test
    void nearMissesLeaveOutATooltip() {
        UnifiedAssertionError failure = failures.get(
                "A renamed button lists Near Misses by their own text, not their tooltips");

        assertEquals("exactly one button with text “Export CSV”", failure.getExpected());
        assertTrue(failure.getNearMisses().contains("button “Export”"), failure.getMessage());
        failure.getNearMisses().forEach(n -> assertFalse(n.contains("report"), failure.getMessage()));
    }

    @Test
    void aMissingColumnListsTheGridsColumns() {
        UnifiedAssertionError failure = failures.get("A grid without the column lists the columns it has");

        assertEquals("grid (test id “archived”) to have column “Archived on”", failure.getExpected());
        assertEquals("[“Name”]", failure.getFound(), failure.getMessage());
        assertEquals(Optional.of("see grid (test id “archived”) with column “Archived on”"), failure.getFailedStep());
    }
}
