package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.Locator;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.spi.SideNavAccess;
import com.vaadin.testing.unified.spi.SideNavItemAccess;
import java.util.List;
import java.util.Map;
import org.vaadin.addons.dramafinder.AutoWait;

/**
 * SideNav and SideNavItem on Playwright (#658): read in one round trip each ({@link BrowserQueries#SIDE_NAV_ITEM}),
 * expanded and collapsed by the item's toggle button, clicked on the item's own {@code <a>} in its shadow root.
 */
final class PlaywrightSideNav {
    private PlaywrightSideNav() {}

    record NavAccess(PlaywrightSession session, Locator locator) implements SideNavAccess {
        private Map<?, ?> read() {
            return (Map<?, ?>) locator.evaluate(BrowserQueries.SIDE_NAV);
        }

        @Override
        public String label() {
            return (String) read().get("label");
        }

        @Override
        public List<String> items() {
            return strings(read().get("items"));
        }
    }

    record ItemAccess(PlaywrightSession session, Locator locator) implements SideNavItemAccess {
        private Map<?, ?> read() {
            return (Map<?, ?>) locator.evaluate(BrowserQueries.SIDE_NAV_ITEM);
        }

        @Override
        public String label() {
            return (String) read().get("label");
        }

        @Override
        public List<String> items() {
            return strings(read().get("items"));
        }

        @Override
        public boolean isCurrent() {
            return Boolean.TRUE.equals(read().get("current"));
        }

        @Override
        public boolean isExpanded() {
            return Boolean.TRUE.equals(read().get("expanded"));
        }

        @Override
        public boolean inCollapsedItem() {
            return Boolean.TRUE.equals(read().get("inCollapsedItem"));
        }

        /** The items it is in, outermost first: XPath's ancestors, in document order. */
        @Override
        public void expandParents() {
            Locator parents = locator.locator("xpath=ancestor::vaadin-side-nav-item");
            for (int i = 0; i < parents.count(); i++) {
                Locator parent = parents.nth(i);
                if (!Boolean.TRUE.equals(parent.evaluate("e => !!e.expanded"))) {
                    toggleOf(parent).click();
                    AutoWait.waitForVaadinIdle(session.page());
                }
            }
        }

        @Override
        public void toggle() {
            toggleOf(locator).click();
            AutoWait.waitForVaadinIdle(session.page());
        }

        /**
         * The item's own {@code <a>}: the first match, as Playwright searches an element's shadow root before its
         * child items' ones.
         */
        @Override
        public UnifiedElement link() {
            return new PlaywrightElement(session, locator.locator("#link").first(), null, PlaywrightComponents.DEFAULT);
        }

        /** The item's own toggle button, found before its child items' ones, as {@link #link()}. */
        private static Locator toggleOf(Locator item) {
            return item.locator("button[part='toggle-button']").first();
        }
    }

    private static List<String> strings(Object list) {
        return ((List<?>) list).stream().map(o -> o == null ? "" : o.toString()).toList();
    }
}
