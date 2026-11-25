package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
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
 * The automatic layout checks end to end (ADR 0011): a test launched with {@code vaadin.testing.layout-checks=on} at a
 * phone's Viewport, and the Layout Findings its User Steps carry into {@code user-steps.json} and {@code report.html}.
 */
class LayoutChecksFilesTest {

    /** Opens the AppLayout fixture on a phone; tagged so Surefire doesn't run it on its own. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    @Viewport(width = 375, height = 667, name = "phone")
    static class OnAPhone {
        @Test
        @DisplayName("Opening the AppLayout on a phone")
        void opensTheAppLayout(UnifiedPage page) {
            page.navigate(LayoutAppLayoutView.class);
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
                .configurationParameter("vaadin.testing.user-steps.server-log", "off")
                .configurationParameter("vaadin.testing.layout-checks", "on")
                .configurationParameter("vaadin.testing.layout-checks.screenshots", "on")
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        return listener.getSummary();
    }

    @Test
    void theFindingsOfANavigationLandOnItsUserStepWithAScreenshot(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(tool().hasRealBrowser(), "Layout checks need a real browser");
        TestExecutionSummary summary = run(OnAPhone.class, dir);
        assertEquals(1, summary.getTestsSucceededCount(), () -> summary.getFailures().toString());

        Path test = dir.resolve("LayoutChecksFilesTest$OnAPhone").resolve("opensTheAppLayout");
        JsonNode open = new ObjectMapper().readTree(Files.readString(test.resolve("user-steps.json"))).get("steps").get(0);
        JsonNode layout = open.get("layout");
        assertEquals(1, layout.get("point").asInt(), open.toString());
        assertTrue(layout.get("findings").get(0).get("text").asString()
                .startsWith("vaadin-app-layout [part=content] scrolls sideways by "), layout.toString());
        assertTrue(Files.isRegularFile(test.resolve(layout.get("screenshot").asString())), layout.toString());
    }
}
