package com.vaadin.testing.unified.internal.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/** A test whose Viewport the Testing Tool can't set is skipped by the JUnit integration, before its body runs. */
class ViewportSkipTest {

    static final String CANT = "this Testing Tool has no exact Viewport";
    static final List<String> RAN = new CopyOnWriteArrayList<>();

    /** Registered for {@code playwright} in this module's tests only: a session that can't set a Viewport. */
    public static final class CantSetViewport implements TestingToolAdapter {
        @Override
        public TestingTool tool() {
            return TestingTool.PLAYWRIGHT;
        }

        @Override
        public AdapterSession open(AdapterContext context) {
            return (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> switch (method.getName()) {
                        case "setViewport" -> throw new UnsupportedOperationException(CANT);
                        case "unwrap", "queryParameter" -> Optional.empty();
                        case "find", "consoleErrors", "testEnded" -> List.of();
                        case "tracesSteps", "isAt" -> false;
                        default -> null;
                    });
        }
    }

    @UnifiedUITest
    @Tag("launched-only")
    static class Samples {
        @Test
        @Viewport(width = 375, height = 667, name = "phone")
        void onAPhone(UnifiedPage page) {
            RAN.add("onAPhone");
        }

        @Test
        void atTheDefault(UnifiedPage page) {
            RAN.add("atTheDefault");
        }
    }

    @Test
    void skipsATestThatNeedsItsViewportAndRunsOneOnTheDefault() {
        RAN.clear();
        Events events = EngineTestKit.engine("junit-jupiter").selectors(selectClass(Samples.class))
                .configurationParameter("vaadin.testing.tool", "playwright")
                .configurationParameter("vaadin.testing.user-steps.log", "none")
                .configurationParameter("vaadin.testing.evidence-dir", "target/viewport-skip-test")
                .execute().testEvents();

        events.assertStatistics(stats -> stats.succeeded(1).aborted(1).failed(0));
        assertEquals(List.of("atTheDefault"), RAN);
        assertEquals("Can't set the Viewport phone 375 × 667 exactly: " + CANT, events.aborted().stream()
                .map(e -> e.getRequiredPayload(org.junit.platform.engine.TestExecutionResult.class).getThrowable()
                        .orElseThrow().getMessage()).findFirst().orElseThrow());
    }
}
