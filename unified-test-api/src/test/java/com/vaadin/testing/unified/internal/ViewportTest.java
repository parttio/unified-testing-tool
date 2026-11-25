package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.Viewport;
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

    @Viewport(width = 375, height = 667, name = "phone")
    static class OnAPhone {
        void anything() {}
    }

    // ---------------------------------------------------------------- tests

    @Test
    void aTestWithAViewportIsSkippedWithoutABrowser() throws Exception {
        TestLifecycle.Decision decision = lifecycle("browserless", Map.of())
                .decide(OnAPhone.class.getDeclaredMethod("anything"), OnAPhone.class);

        assertEquals(new TestLifecycle.Decision(false, "Requires a real browser, this run uses BROWSERLESS"), decision);
    }
}
