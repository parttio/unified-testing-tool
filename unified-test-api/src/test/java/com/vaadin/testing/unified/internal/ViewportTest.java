package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.ViewportSize;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Viewport a test runs at (ADR 0011), driven through {@link TestLifecycle} and the {@code UnifiedPage} it opens,
 * as a Test Runner would, with a fake Testing Tool that records what it is asked.
 */
class ViewportTest {

    final TestLifecycleTest.FakeSession session = new TestLifecycleTest.FakeSession();
    final List<AdapterContext> opened = new ArrayList<>();

    @TempDir
    Path evidenceDir;

    private TestLifecycle lifecycle(String tool, Map<String, String> more) {
        Map<String, String> all = new HashMap<>(more);
        all.putAll(Map.of(Settings.TOOL, tool, Settings.EVIDENCE_DIR, evidenceDir.toString(),
                Settings.USER_STEPS_LOG, "none"));
        return new TestLifecycle(Settings.from(ConfigurationSource.of(all)), t -> new TestingToolAdapter() {
            @Override
            public TestingTool tool() {
                return t;
            }

            @Override
            public AdapterSession open(AdapterContext context) {
                opened.add(context);
                return session.proxy();
            }
        });
    }

    // ---------------------------------------------------------------- sample tests (plain classes, never run)

    static class Plain {
        void anything() {}
    }

    @Viewport(width = 375, height = 667, name = "phone")
    static class OnAPhone {
        void anything() {}
    }

    static class ExtendsOnAPhone extends OnAPhone {
        @Override
        void anything() {}
    }

    @Viewport(width = 375, height = 667, name = "phone")
    static class TabletMethodOnAPhone {
        @Viewport(width = 768, height = 1024)
        void anything() {}
    }

    private static TestLifecycleTest.PlainContext context(Class<?> test) throws Exception {
        return new TestLifecycleTest.PlainContext(test, test.getDeclaredMethod("anything"));
    }

    private List<String> sizesAsked() {
        return session.calls.stream().filter(c -> c.startsWith("setViewport")).toList();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void aTestWithAViewportIsSkippedWithoutABrowser() throws Exception {
        TestLifecycle.Decision decision = lifecycle("browserless", Map.of())
                .decide(OnAPhone.class.getDeclaredMethod("anything"), OnAPhone.class);

        assertEquals(new TestLifecycle.Decision(false, "Requires a real browser, this run uses BROWSERLESS"), decision);
    }

    @Test
    void aNewPageGetsTheDefaultViewportBeforeAnythingElse() throws Exception {
        lifecycle("playwright", Map.of()).open(context(Plain.class), new StepRecorder());

        assertEquals("setViewport[1280 × 720]", session.calls.get(0));
    }

    @Test
    void browserlessIsNeverAskedForAViewport() throws Exception {
        lifecycle("browserless", Map.of()).open(context(Plain.class), new StepRecorder());

        assertEquals(List.of(), sizesAsked());
    }

    @Test
    void theRunsViewportReplacesTheDefault() throws Exception {
        lifecycle("selenium", Map.of(Settings.VIEWPORT, "desktop:1440x900")).open(context(Plain.class), new StepRecorder());

        assertEquals(List.of("setViewport[desktop 1440 × 900]"), sizesAsked());
    }

    @Test
    void aTestsViewportWinsOverTheRunsAndIsInherited() throws Exception {
        lifecycle("playwright", Map.of(Settings.VIEWPORT, "1440x900")).open(context(ExtendsOnAPhone.class),
                new StepRecorder());

        assertEquals(List.of("setViewport[phone 375 × 667]"), sizesAsked());
    }

    @Test
    void aMethodsViewportWinsOverItsClasses() throws Exception {
        lifecycle("playwright", Map.of()).open(context(TabletMethodOnAPhone.class), new StepRecorder());

        assertEquals(List.of("setViewport[768 × 1024]"), sizesAsked());
    }

    @Test
    void anotherWindowOrUserStartsAtTheTestsViewport() throws Exception {
        com.vaadin.testing.unified.UnifiedPage page = lifecycle("playwright", Map.of())
                .open(context(OnAPhone.class), new StepRecorder()).page();

        page.openWindow();
        page.openAsUser("alice", "ADMIN");

        assertEquals(List.of("setViewport[phone 375 × 667]", "setViewport[phone 375 × 667]",
                "setViewport[phone 375 × 667]"), sizesAsked());
    }

    @Test
    void settingTheViewportIsAUserStepThatWaitsForTheServer() throws Exception {
        StepRecorder steps = new StepRecorder();
        com.vaadin.testing.unified.UnifiedPage page = lifecycle("playwright", Map.of())
                .open(context(OnAPhone.class), steps).page();
        assertEquals(new ViewportSize("phone", 375, 667), page.viewport());

        long start = System.nanoTime();
        page.setViewport(768, 1024);

        assertEquals(ViewportSize.of(768, 1024), page.viewport());
        assertEquals(List.of("set viewport to 768 × 1024"), steps.entries().stream()
                .map(e -> ((StepRecorder.Step) e).phrase().render()).toList());
        // Vaadin hears a resize 300 ms after the browser's last resize event, then handles it (ADR 0011)
        List<String> calls = session.calls;
        int set = calls.indexOf("setViewport[768 × 1024]");
        assertEquals("settle", calls.get(set + 1));
        assertTrue(System.nanoTime() - start >= 300_000_000L);
    }

    // ---------------------------------------------------------------- a Testing Tool that can't

    static final String CANT = "Viewport control needs Firefox 132 or later, this is 128";

    @Test
    void onTheDefaultViewportATestThatCantHaveItRunsWithAWarning() throws Exception {
        session.cannotSetViewport = CANT;
        StepRecorder steps = new StepRecorder();

        TestLifecycle.OpenTest test = lifecycle("selenium", Map.of()).open(context(Plain.class), steps);

        assertEquals(java.util.Optional.empty(), test.skipReason());
        assertEquals(List.of("unified WARN: Runs at the browser's own size, not at 1280 × 720: " + CANT),
                steps.logsBeforeFirstEntry().stream().map(StepRecorder.LogLine::text).toList());
    }

    @Test
    void aTestThatNeedsItsViewportIsSkippedWhenTheTestingToolCantSetIt() throws Exception {
        session.cannotSetViewport = CANT;

        TestLifecycle.OpenTest onAPhone = lifecycle("selenium", Map.of()).open(context(OnAPhone.class),
                new StepRecorder());

        assertEquals(java.util.Optional.of("Can't set the Viewport phone 375 × 667 exactly: " + CANT),
                onAPhone.skipReason());
    }

    @Test
    void aRunsViewportIsNeededTooSoAViewportComparisonNeverComparesTheWrongSize() throws Exception {
        session.cannotSetViewport = CANT;

        TestLifecycle.OpenTest test = lifecycle("selenium", Map.of(Settings.VIEWPORT, "phone:375x667"))
                .open(context(Plain.class), new StepRecorder());

        assertEquals(java.util.Optional.of("Can't set the Viewport phone 375 × 667 exactly: " + CANT),
                test.skipReason());
    }

    @Test
    void settingAViewportTheTestingToolCantSetFailsThatUserStep() throws Exception {
        StepRecorder steps = new StepRecorder();
        com.vaadin.testing.unified.UnifiedPage page = lifecycle("selenium", Map.of()).open(context(Plain.class), steps)
                .page();
        session.cannotSetViewport = CANT;

        UnsupportedOperationException e = org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> page.setViewport(375, 667));

        assertEquals(CANT, e.getMessage());
        assertEquals(StepRecorder.Status.FAILED, ((StepRecorder.Step) steps.entries().get(0)).status());
    }
}
