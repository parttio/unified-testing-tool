package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.router.Route;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.spi.AdapterSession;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** Issue #530: navigate(view) on a route that needs a parameter fails in the Unified API, not on the server. */
class NavigateWithoutParametersTest {

    @Tag("div") @Route("order/:id/:tab?") static class OrderView extends Component {}
    @Tag("div") @Route("week/:owner?") static class WeekView extends Component {}

    private final List<Class<?>> navigated = new CopyOnWriteArrayList<>();

    private UnifiedPageImpl page() {
        AdapterSession session = (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> {
                    if (method.getName().equals("navigate") && args.length == 1 && args[0] instanceof Class<?> c) {
                        navigated.add(c);
                    }
                    return null;
                });
        return new UnifiedPageImpl(new TestRun(TestingTool.SELENIUM, session, Duration.ofMillis(300)));
    }

    @Test
    void requiredParameterFailsWithoutReachingTheTestingTool() {
        UnifiedAssertionError failure = assertThrows(UnifiedAssertionError.class,
                () -> page().navigate(OrderView.class));

        assertTrue(failure.getSummary().contains("route parameter “id”"), failure.getSummary());
        assertTrue(failure.getSummary().contains("order/:id/:tab?"), failure.getSummary());
        assertEquals(List.of(), navigated);
    }

    @Test
    void optionalParameterNavigates() {
        page().navigate(WeekView.class);

        assertEquals(List.of(WeekView.class), navigated);
    }
}
