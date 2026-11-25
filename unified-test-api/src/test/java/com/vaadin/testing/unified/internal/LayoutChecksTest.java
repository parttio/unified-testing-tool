package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.internal.steps.UserStepsReport;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The automatic layout checks (ADR 0011): where the core measures the layout when {@code vaadin.testing.layout-checks}
 * is on, and what the measured User Steps carry into {@code user-steps.json}. A fake Testing Tool returns canned
 * results of the layout script.
 */
class LayoutChecksTest {

    final TestLifecycleTest.FakeSession session = new TestLifecycleTest.FakeSession();
    final StepRecorder steps = new StepRecorder();

    @TempDir
    Path evidenceDir;

    static final String SCROLLS = "{\"findings\":[{\"kind\":\"scroll\",\"container\":\"the page\",\"by\":40,"
            + "\"box\":[0,0,415,667]}],\"matches\":[],\"appLayouts\":[],\"menuBars\":[]}";

    private UnifiedPage page(Map<String, String> more) throws Exception {
        Map<String, String> settings = new HashMap<>(more);
        settings.putAll(Map.of(Settings.TOOL, "playwright", Settings.EVIDENCE_DIR, evidenceDir.toString(),
                Settings.USER_STEPS_LOG, "none", Settings.TIMEOUT, "300ms"));
        TestLifecycle lifecycle = new TestLifecycle(Settings.from(ConfigurationSource.of(settings)),
                t -> new TestingToolAdapter() {
                    @Override public TestingTool tool() { return t; }
                    @Override public AdapterSession open(AdapterContext context) { return session.proxy(); }
                });
        return lifecycle.open(new TestLifecycleTest.PlainContext(ViewportTest.Plain.class,
                ViewportTest.Plain.class.getDeclaredMethod("anything")), steps).page();
    }

    private long measurements() {
        return session.calls.stream().filter(c -> c.startsWith("measureLayout")).count();
    }

    private JsonNode json() throws Exception {
        steps.finish();
        return new ObjectMapper().readTree(new UserStepsReport("demo.ProfileTest", "saves", "saves", "playwright",
                "passed", null, steps).json());
    }

    @Test
    void withChecksOnANavigationIsMeasuredAndItsUserStepCarriesTheFindings() throws Exception {
        UnifiedPage page = page(Map.of(Settings.LAYOUT_CHECKS, "on"));
        session.layout = SCROLLS;

        page.navigate("profile");

        assertEquals(1, measurements());
        JsonNode layout = json().get("steps").get(0).get("layout");
        assertEquals(1, layout.get("point").asInt());
        assertEquals("[{\"kind\":\"scroll\",\"text\":\"the page scrolls sideways by 40 px\",\"box\":[0,0,415,667]}]",
                layout.get("findings").toString());
    }

    @Test
    void withChecksOffNothingIsMeasured() throws Exception {
        UnifiedPage page = page(Map.of());
        session.layout = SCROLLS;

        page.navigate("profile");
        page.reload();
        page.setViewport(375, 667);

        assertEquals(0, measurements());
    }

    @Test
    void aReloadAndAViewportChangeAreMeasurementPointsToo() throws Exception {
        UnifiedPage page = page(Map.of(Settings.LAYOUT_CHECKS, "on"));
        session.layout = SCROLLS;

        page.navigate("profile");
        page.reload();
        page.setViewport(375, 667);

        assertEquals(3, measurements());
        JsonNode steps = json().get("steps");
        assertEquals("[1,2,3]", "[" + steps.get(0).get("layout").get("point") + "," + steps.get(1).get("layout").get("point")
                + "," + steps.get(2).get("layout").get("point") + "]");
    }

    @Test
    void checkLayoutIsAUserStepThatMeasuresEvenWithTheAutomaticChecksOff() throws Exception {
        UnifiedPage page = page(Map.of());
        session.layout = SCROLLS;

        page.checkLayout();

        assertEquals(1, measurements());
        JsonNode step = json().get("steps").get(0);
        assertEquals("check the layout", step.get("text").asString());
        assertEquals(1, step.get("layout").get("point").asInt());
        assertEquals(1, step.get("layout").get("findings").size());
    }

    @Test
    void withFailAFindingFailsTheUserStepThatMeasuredIt() throws Exception {
        UnifiedPage page = page(Map.of(Settings.LAYOUT_CHECKS, "on", Settings.LAYOUT_FINDINGS, "fail"));
        session.layout = SCROLLS;

        com.vaadin.testing.unified.UnifiedAssertionError e = org.junit.jupiter.api.Assertions.assertThrows(
                com.vaadin.testing.unified.UnifiedAssertionError.class, () -> page.navigate("profile"));

        assertEquals("the page scrolls sideways by 40 px", e.getFound());
        assertEquals("the page to have no Layout Findings", e.getExpected());
        JsonNode step = json().get("steps").get(0);
        assertEquals("failed", step.get("status").asString());
        assertEquals(1, step.get("layout").get("findings").size());
    }
}
