package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.Viewport;
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
}
