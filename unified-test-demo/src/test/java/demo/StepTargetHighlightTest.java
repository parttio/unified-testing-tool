package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * Runs a Unified UI Test with a snapshot per User Step and checks each browser screenshot names its Step Target's
 * box in {@code user-steps.json} (issue #472).
 */
class StepTargetHighlightTest {

    /** The scenario; tagged so Surefire doesn't run it on its own. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class Scenario {
        @Test
        void editProfile(UnifiedPage page) {
            page.navigate(ProfileView.class);
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Ada");
            page.find(UnifiedTextField.class).withLabel("Email").setValue("ada@example.com");
            assertThat(page.find(UnifiedTextField.class).withLabel("Name")).hasValue("Ada");
            page.find(UnifiedButton.class).withText("Change email").click();
            UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Change email");
            dialog.find(UnifiedButton.class).withText("Save").click(); // closes the dialog: its Save is gone
        }
    }

    /** Fails on a check of a field it found: the failure's screenshot marks that field. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class FailingCheck {
        @Test
        void checksTheWrongName(UnifiedPage page) {
            page.navigate(ProfileView.class);
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Ada");
            assertThat(page.find(UnifiedTextField.class).withLabel("Name")).hasValue("Grace");
        }
    }

    private static final TestingTool TOOL = TestingTool
            .parse(System.getProperty("vaadin.testing.tool", "browserless"));

    @Test
    void anActionStepsScreenshotHasItsTargetsBox(@TempDir Path dir) throws Exception {
        assumeTrue(TOOL.hasRealBrowser(), "screenshots only in a browser");
        String json = run(dir, Scenario.class, "editProfile");

        double[] name = box(json, "fill text field “Name” with “Ada”");
        double[] email = box(json, "fill text field “Email” with “ada@example.com”");
        for (double[] b : new double[][] { name, email }) {
            for (double v : b) {
                assertTrue(v >= 0 && v <= 1, "a fraction of the image: " + v);
            }
            assertTrue(b[2] > 0 && b[3] > 0, "a box with a size");
        }
        // the view lays its fields out top-down: Name, then Email
        assertTrue(name[1] + name[3] <= email[1], "Name's box is above Email's");
        assertEquals(name[0], email[0], 0.01, "both fields start at the same x");
    }

    @Test
    void aCheckStepsScreenshotHasTheBoxOfTheComponentItChecked(@TempDir Path dir) throws Exception {
        assumeTrue(TOOL.hasRealBrowser(), "screenshots only in a browser");
        String json = run(dir, Scenario.class, "editProfile");

        double[] filled = box(json, "fill text field “Name” with “Ada”");
        double[] checked = box(json, "see text field “Name”");
        assertArrayEquals(filled, checked, 0.001, "the check marks the field it checked");
    }

    @Test
    void aStepWhoseTargetIsGoneAfterItSaysSo(@TempDir Path dir) throws Exception {
        assumeTrue(TOOL.hasRealBrowser(), "screenshots only in a browser");
        String json = run(dir, Scenario.class, "editProfile");

        String save = step(json, "click button “Save” in dialog “Change email”");
        assertTrue(save.matches("(?s).*\"highlight\": \\{\\s*\"missing\": \"gone\"\\s*}.*"), save);
    }

    @Test
    void onBrowserlessACheckStepsTreeMarksTheComponentItChecked(@TempDir Path dir) throws Exception {
        assumeTrue(!TOOL.hasRealBrowser(), "component trees only on Browserless");
        String json = run(dir, Scenario.class, "editProfile");

        Matcher snapshot = Pattern.compile("\"snapshot\": \"([^\"]+)\"")
                .matcher(step(json, "see text field “Name”"));
        assertTrue(snapshot.find(), "the check has a snapshot");
        String tree = Files.readString(dir.resolve("StepTargetHighlightTest$Scenario").resolve("editProfile")
                .resolve(snapshot.group(1)));
        assertTrue(tree.contains("label='Name'") && tree.lines().filter(l -> l.endsWith("  ← this step"))
                .allMatch(l -> l.contains("label='Name'")) && tree.contains("  ← this step"),
                "the Name field's line, and only it, is marked: " + tree);
    }

    @Test
    void theFailuresScreenshotHasTheFailedStepsTargetsBox(@TempDir Path dir) throws Exception {
        assumeTrue(TOOL.hasRealBrowser(), "screenshots only in a browser");
        String json = run(dir, FailingCheck.class, "checksTheWrongName");

        double[] filled = box(json, "fill text field “Name” with “Ada”");
        String failure = json.substring(json.indexOf("\"failure\": {"), json.indexOf("\"steps\":"));
        double[] failed = box(failure);
        // the failure shot is the whole page, the step's the viewport: the same field, so the same left edge
        assertEquals(filled[0], failed[0], 0.01, failure);
        assertTrue(failed[2] > 0 && failed[3] > 0, failure);
    }

    /** Runs the scenario's test with snapshots on; its {@code user-steps.json}. */
    static String run(Path dir, Class<?> scenario, String method) throws Exception {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(scenario))
                .configurationParameter("vaadin.testing.tool", TOOL.name().toLowerCase())
                .configurationParameter("vaadin.testing.evidence-dir", dir.toString())
                .configurationParameter("vaadin.testing.user-steps.snapshots", "true")
                .configurationParameter("vaadin.testing.user-steps.server-log", "off")
                .configurationParameter("vaadin.testing.timeout", "2s")
                .build();
        LauncherFactory.create().execute(request, new SummaryGeneratingListener());
        String outer = scenario.getEnclosingClass().getSimpleName() + "$" + scenario.getSimpleName();
        return Files.readString(dir.resolve(outer).resolve(method).resolve("user-steps.json"));
    }

    /** The {@code highlight} box of the step with this text: x, y, width, height. */
    static double[] box(String json, String stepText) {
        return box(step(json, stepText));
    }

    /** The first {@code highlight} box in this JSON: x, y, width, height. */
    static double[] box(String step) {
        Matcher m = Pattern.compile("\"highlight\": \\{\\s*\"x\": ([\\d.]+),\\s*\"y\": ([\\d.]+),\\s*"
                + "\"width\": ([\\d.]+),\\s*\"height\": ([\\d.]+)\\s*}").matcher(step);
        assertTrue(m.find(), "a highlight box in: " + step);
        return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
    }

    /** The JSON object of the step with this text, up to the next step. */
    static String step(String json, String stepText) {
        int at = json.indexOf("\"text\": \"" + stepText);
        assertTrue(at >= 0, "a step “" + stepText + "” in " + json);
        int next = json.indexOf("\"type\":", at);
        return json.substring(at, next < 0 ? json.length() : next);
    }
}
