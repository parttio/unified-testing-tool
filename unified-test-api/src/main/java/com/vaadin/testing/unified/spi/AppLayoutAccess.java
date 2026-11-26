package com.vaadin.testing.unified.spi;

/** Adapter access to an AppLayout, reached by the core with {@code element.unwrap(AppLayoutAccess.class)}. */
public interface AppLayoutAccess {

    /** Whether the drawer is shown: the {@code drawerOpened} property, as the server reads it on Browserless. */
    boolean drawerOpened();

    /**
     * Opens or closes the drawer as a user does, and returns once the server has the new state: clicks the layout's
     * DrawerToggle, or presses Escape when the drawer is an opened overlay, which covers the toggle. Fails when the
     * layout has no DrawerToggle.
     */
    void toggleDrawer();
}
