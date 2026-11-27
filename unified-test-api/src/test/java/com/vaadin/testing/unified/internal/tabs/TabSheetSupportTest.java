package com.vaadin.testing.unified.internal.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.vaadin.testing.unified.spi.LinkAccess;
import com.vaadin.testing.unified.spi.TabSheetAccess;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The check after selectTab when reading the selection back fails (#554), as a select's after #377; a tab holding a
 * link on a Testing Tool without a browser (#531).
 */
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

    @Test
    void withoutABrowserSelectsTheTabThenFollowsItsLink() {
        List<String> calls = new CopyOnWriteArrayList<>();

        menu(calls, Optional.empty()).find(UnifiedTabs.class).selectTab("Users");

        assertEquals(List.of("select Users", "follow"), calls);
    }

    @Test
    void withoutABrowserALinkItCantFollowFailsAskingForOneAndSelectsNothing() {
        List<String> calls = new CopyOnWriteArrayList<>();
        UnifiedTabs tabs = menu(calls, Optional.of("leaves the application (“https://vaadin.com”)"))
                .find(UnifiedTabs.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> tabs.selectTab("Users"));

        assertTrue(e.getMessage().contains("the link of tab “Users” in"), e.getMessage());
        assertTrue(e.getMessage().contains("@RequiresBrowser"), e.getMessage());
        assertEquals(List.of(), calls);
    }

    /**
     * A menu whose "Users" tab holds a link, on a Testing Tool without a browser (LinkAccess): following it changes
     * the path, unless it is {@code unfollowable}.
     */
    private UnifiedPageImpl menu(List<String> calls, Optional<String> unfollowable) {
        String[] path = { "orders" };
        LinkAccess linkAccess = new LinkAccess() {
            @Override
            public Optional<String> unfollowable() {
                return unfollowable;
            }

            @Override
            public void follow() {
                calls.add("follow");
                path[0] = "users";
            }
        };
        UnifiedElement link = (UnifiedElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { UnifiedElement.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "tryUnwrap" -> args[0] == LinkAccess.class ? Optional.of(linkAccess) : Optional.empty();
                    case "click" -> throw new AssertionError("a Testing Tool without a browser follows the link");
                    default -> null;
                });
        TabSheetAccess access = (TabSheetAccess) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { TabSheetAccess.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "tabs" -> List.of("Orders", "Users");
                    case "isEnabled" -> true;
                    case "select" -> {
                        calls.add("select " + args[0]);
                        yield null;
                    }
                    case "selectedTab" -> "Orders"; // unchecked: the page navigated
                    case "link" -> "Users".equals(args[0]) ? Optional.of(link) : Optional.empty();
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
                new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "find" -> List.of(element);
                    case "currentPath" -> path[0];
                    default -> null;
                });
        return new UnifiedPageImpl(new TestRun(TestingTool.BROWSERLESS, session, Duration.ofSeconds(1)));
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
                    case "link" -> Optional.empty();
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
