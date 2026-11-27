package com.vaadin.testing.unified.browserless;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.testing.unified.spi.AppLayoutAccess;

/**
 * AppLayout on Browserless (#493): the drawer's state as the server has it. Whether the drawer is an overlay is
 * decided by the browser from the layout, so Browserless has no answer.
 */
final class BrowserlessAppLayout {
    private BrowserlessAppLayout() {}

    private static boolean isOrHoldsToggle(com.vaadin.flow.dom.Element element) {
        return element.getComponent().filter(DrawerToggle.class::isInstance).isPresent()
                || element.getChildren().anyMatch(BrowserlessAppLayout::isOrHoldsToggle);
    }

    record Access(BrowserlessSession session, AppLayout layout) implements AppLayoutAccess {
        @Override
        public boolean drawerOpened() {
            return layout.isDrawerOpened();
        }

        /**
         * The DrawerToggle's click is handled in the browser, which then tells the server; here the server is told
         * directly, as long as the layout has a DrawerToggle for a user to click.
         */
        @Override
        public void toggleDrawer() {
            boolean hasToggle = layout.getElement().getChildren().anyMatch(BrowserlessAppLayout::isOrHoldsToggle);
            if (!hasToggle) {
                throw new IllegalStateException("The app layout has no DrawerToggle to open or close its drawer with");
            }
            layout.setDrawerOpened(!layout.isDrawerOpened());
        }
    }
}
