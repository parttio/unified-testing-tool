package com.vaadin.testing.unified.internal.tabs;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.UnifiedPageImpl;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TabSheetAccess;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** The check after selectTab when reading the selection back fails (#554), as a select's after #377. */
class TabSheetSupportTest {

    private final RuntimeException readFailed = new RuntimeException("Timeout 10000ms exceeded");
    private final AtomicBoolean selected = new AtomicBoolean();

    @Test
    void failsInTheUnifiedFormatWhenTheReadFailsAndTheTabsAreStillThere() {
        UnifiedTabs tabs = page(true).find(UnifiedTabs.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> tabs.selectTab("Logout"));

        assertSame(readFailed, e.getCause());
        assertTrue(e.getMessage().contains("tab “Logout” selected in"), e.getMessage());
    }

    @Test
    void passesWhenTheReadFailsAndTheTabsAreGone() {
        page(false).find(UnifiedTabs.class).selectTab("Logout");

        assertTrue(selected.get());
    }

    /** Tabs whose selected tab can't be read after the click; still on the page then, or not. */
    private UnifiedPageImpl page(boolean stillThereAfterSelect) {
        TabSheetAccess access = (TabSheetAccess) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { TabSheetAccess.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "tabs" -> List.of("Orders", "Logout");
                    case "isEnabled" -> true;
                    case "select" -> {
                        selected.set(true);
                        yield null;
                    }
                    case "selectedTab" -> throw readFailed;
                    default -> null;
                });
        UnifiedElement element = (UnifiedElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { UnifiedElement.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "isVisible", "isEnabled" -> true;
                    case "unwrap" -> args[0] == TabSheetAccess.class ? access : null;
                    case "getTag" -> "vaadin-tabs";
                    default -> null;
                });
        AdapterSession session = (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> {
                    if (method.getName().equals("find")) {
                        return selected.get() && !stillThereAfterSelect ? List.of() : List.of(element);
                    }
                    return null;
                });
        return new UnifiedPageImpl(new TestRun(TestingTool.PLAYWRIGHT, session, Duration.ofSeconds(1)));
    }
}
