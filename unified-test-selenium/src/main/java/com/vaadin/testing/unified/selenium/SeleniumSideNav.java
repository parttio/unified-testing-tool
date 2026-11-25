package com.vaadin.testing.unified.selenium;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.spi.SideNavAccess;
import com.vaadin.testing.unified.spi.SideNavItemAccess;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * SideNav and SideNavItem on Selenium (#658), as on Playwright: read in one round trip each
 * ({@link BrowserQueries#SIDE_NAV_ITEM}), expanded and collapsed by the item's toggle button, clicked on the item's own
 * {@code <a>} in its shadow root. TestBench's {@code SideNavItemElement} clicks both with a script, not as a user.
 */
final class SeleniumSideNav {
    private SeleniumSideNav() {}

    /** The items an item is in, outermost first. */
    private static final String PARENTS = """
            const parents = [];
            for (let p = arguments[0].parentElement; p && p.localName === 'vaadin-side-nav-item'; p = p.parentElement) {
              parents.unshift(p);
            }
            return parents;""";

    record NavAccess(SeleniumSession session, WebElement element) implements SideNavAccess {
        private Map<?, ?> read() {
            return (Map<?, ?>) session.js("return (" + BrowserQueries.SIDE_NAV + ")(arguments[0]);", element);
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

    record ItemAccess(SeleniumSession session, WebElement element) implements SideNavItemAccess {
        private Map<?, ?> read() {
            return (Map<?, ?>) session.js("return (" + BrowserQueries.SIDE_NAV_ITEM + ")(arguments[0]);", element);
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

        @Override
        public void expandParents() {
            for (Object parent : (List<?>) session.js(PARENTS, element)) {
                WebElement item = (WebElement) parent;
                if (!Boolean.TRUE.equals(session.js("return !!arguments[0].expanded;", item))) {
                    toggleOf(item).click();
                    session.waitForVaadin();
                }
            }
        }

        @Override
        public void toggle() {
            toggleOf(element).click();
            session.waitForVaadin();
        }

        @Override
        public UnifiedElement link() {
            WebElement link = SeleniumSession.stale(() -> element.getShadowRoot().findElement(By.cssSelector("#link")));
            return new SeleniumElement(session, link, SeleniumComponents.DEFAULT);
        }

        private static WebElement toggleOf(WebElement item) {
            return SeleniumSession.stale(() -> item.getShadowRoot()
                    .findElement(By.cssSelector("button[part='toggle-button']")));
        }
    }

    private static List<String> strings(Object list) {
        return ((List<?>) list).stream().map(o -> o == null ? "" : o.toString()).toList();
    }
}
