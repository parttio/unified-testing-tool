package com.vaadin.testing.unified.internal;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The layout assertions (ADR 0011) through {@link UnifiedPage}, with a fake Testing Tool whose layout script returns
 * canned results: what passes, what fails and how the failure reads.
 */
class LayoutAssertionsTest {

    final TestLifecycleTest.FakeSession session = new TestLifecycleTest.FakeSession();

    @TempDir
    Path evidenceDir;

    static final String NOTHING = "{\"findings\":[],\"matches\":[],\"appLayouts\":[],\"menuBars\":[]}";

    private UnifiedPage page(String tool) throws Exception {
        Map<String, String> settings = new HashMap<>(Map.of(Settings.TOOL, tool, Settings.EVIDENCE_DIR,
                evidenceDir.toString(), Settings.USER_STEPS_LOG, "none", Settings.TIMEOUT, "300ms"));
        TestLifecycle lifecycle = new TestLifecycle(Settings.from(ConfigurationSource.of(settings)),
                t -> new TestingToolAdapter() {
                    @Override public TestingTool tool() { return t; }
                    @Override public AdapterSession open(AdapterContext context) { return session.proxy(); }
                });
        return lifecycle.open(new TestLifecycleTest.PlainContext(ViewportTest.Plain.class,
                ViewportTest.Plain.class.getDeclaredMethod("anything")), new StepRecorder()).page();
    }

    @Test
    void aPageWithoutSidewaysScrollPassesAndOneWithItFailsSayingWhatScrollsAndHowFar() throws Exception {
        UnifiedPage page = page("playwright");
        session.layout = NOTHING;
        assertThat(page).hasNoHorizontalScroll();

        session.layout = "{\"findings\":[{\"kind\":\"scroll\",\"container\":\"the page\",\"by\":325},"
                + "{\"kind\":\"scroll\",\"container\":\"vaadin-app-layout [part=content]\",\"by\":193}],"
                + "\"matches\":[],\"appLayouts\":[],\"menuBars\":[]}";
        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> assertThat(page).hasNoHorizontalScroll());

        assertEquals("the page scrolls sideways by 325 px; vaadin-app-layout [part=content] scrolls sideways by 193 px",
                e.getFound());
        assertTrue(e.getMessage().contains("to have no sideways scroll"), e.getMessage());
    }
}
