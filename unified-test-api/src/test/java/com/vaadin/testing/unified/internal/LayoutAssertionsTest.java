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

    @Test
    void everyLayoutFindingIsListedInTheFailure() throws Exception {
        UnifiedPage page = page("selenium");
        session.layout = NOTHING;
        assertThat(page).hasNoLayoutFindings();

        // the script's targets are the Findable View's built-in Wrapper types, in that order: the 18th is the button
        int button = LayoutMeasurement.TYPES.indexOf(com.vaadin.testing.unified.component.UnifiedButton.class);
        session.layout = "{\"findings\":[{\"kind\":\"scroll\",\"container\":\"the page\",\"by\":40},"
                + "{\"kind\":\"clipped\",\"target\":" + button + ",\"index\":0,\"by\":\"div#box\"},"
                + "{\"kind\":\"overlap\",\"target\":" + button + ",\"index\":1,\"byTarget\":" + button
                + ",\"byIndex\":2}],\"matches\":[],\"appLayouts\":[],\"menuBars\":[]}";
        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> assertThat(page).hasNoLayoutFindings());

        assertEquals("the page scrolls sideways by 40 px; the 1st button is clipped by div#box; "
                + "the 2nd button is overlapped by the 3rd button", e.getFound());
    }

    @Test
    void withoutABrowserTheLayoutCantBeCheckedAndSaysSo() throws Exception {
        UnifiedPage page = page("browserless");

        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> assertThat(page).hasNoLayoutFindings());

        assertTrue(e.getMessage().startsWith("hasNoLayoutFindings needs a real browser"), e.getMessage());
    }

    /** A button the fake Testing Tool finds: every read answers as a visible, enabled button would. */
    private static com.vaadin.testing.unified.UnifiedElement aButton() {
        return (com.vaadin.testing.unified.UnifiedElement) java.lang.reflect.Proxy.newProxyInstance(
                LayoutAssertionsTest.class.getClassLoader(), new Class<?>[] { com.vaadin.testing.unified.UnifiedElement.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "isVisible", "isEnabled" -> true;
                    case "getTagName" -> "vaadin-button";
                    case "toString" -> "a button";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    case "tryUnwrap" -> java.util.Optional.empty();
                    case "findDescendants", "getChildren" -> java.util.List.of();
                    default -> null;
                });
    }

    private static String withSubject(String reach, String by) {
        return "{\"findings\":[],\"matches\":[],\"appLayouts\":[],\"menuBars\":[],\"subject\":{\"box\":[0,0,10,10],"
                + "\"reach\":\"" + reach + "\"" + (by == null ? "" : ",\"by\":\"" + by + "\"") + "}}";
    }

    @Test
    void aComponentIsInTheViewportOnlyWhenAUserSeesItWithoutScrolling() throws Exception {
        UnifiedPage page = page("playwright");
        session.found = java.util.List.of(aButton());
        com.vaadin.testing.unified.component.UnifiedButton save = page.find(com.vaadin.testing.unified.component.UnifiedButton.class);

        session.layout = withSubject("visible", null);
        assertThat(save).isInViewport();

        session.layout = withSubject("after scroll", null);
        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> assertThat(save).isInViewport());
        assertEquals("after scroll", e.getFound());
    }

    @Test
    void aComponentIsNotClippedUnlessABoxOrThePageCutsItOff() throws Exception {
        UnifiedPage page = page("playwright");
        session.found = java.util.List.of(aButton());
        com.vaadin.testing.unified.component.UnifiedButton save = page.find(com.vaadin.testing.unified.component.UnifiedButton.class);

        session.layout = withSubject("after scroll", null);
        assertThat(save).isNotClipped();

        session.layout = withSubject("clipped", "div#box");
        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> assertThat(save).isNotClipped());
        assertEquals("clipped by div#box", e.getFound());
    }
}
