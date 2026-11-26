package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.Locator;
import com.vaadin.testing.unified.spi.AppLayoutAccess;
import org.vaadin.addons.dramafinder.AutoWait;

/** AppLayout on Playwright (#493): the web component's own state. */
final class PlaywrightAppLayout {
    private PlaywrightAppLayout() {}

    record Access(PlaywrightSession session, Locator locator) implements AppLayoutAccess {
        @Override
        public boolean drawerOpened() {
            return Boolean.TRUE.equals(locator.evaluate("el => el.drawerOpened"));
        }

        @Override
        public void toggleDrawer() {
            Locator toggle = locator.locator("vaadin-drawer-toggle").first();
            if (toggle.count() == 0) {
                throw new IllegalStateException("The app layout has no DrawerToggle to open or close its drawer with");
            }
            if (Boolean.TRUE.equals(locator.evaluate("el => el.overlay && el.drawerOpened"))) {
                session.page().keyboard().press("Escape"); // the opened overlay drawer covers the toggle
            } else {
                toggle.click();
            }
            AutoWait.waitForVaadinIdle(session.page());
        }
    }
}
