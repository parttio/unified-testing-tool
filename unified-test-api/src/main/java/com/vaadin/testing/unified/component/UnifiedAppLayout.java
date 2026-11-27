package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spi.AppLayoutAccess;
import java.time.Duration;

/**
 * An {@link AppLayout}: the application's frame, with a navbar and a drawer (ADR 0011). On a narrow Viewport the
 * drawer becomes an overlay, closed until the user opens it.
 */
@UnifiedComponent(AppLayout.class)
public interface UnifiedAppLayout extends UnifiedWrapper, CommonFilters<UnifiedAppLayout> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedAppLayout withTimeout(Duration timeout);

    // actions (Auto-Wait): clicking the layout's DrawerToggle, as a user would
    default void openDrawer() {
        Wrappers.lazyElementOf(this).act("open the drawer of", e -> {
            AppLayoutAccess layout = e.unwrap(AppLayoutAccess.class);
            if (!layout.drawerOpened()) {
                layout.toggleDrawer();
            }
        });
    }

    default void closeDrawer() {
        Wrappers.lazyElementOf(this).act("close the drawer of", e -> {
            AppLayoutAccess layout = e.unwrap(AppLayoutAccess.class);
            if (layout.drawerOpened()) {
                layout.toggleDrawer();
            }
        });
    }

    default void toggleDrawer() {
        Wrappers.lazyElementOf(this).act("toggle the drawer of", e -> e.unwrap(AppLayoutAccess.class).toggleDrawer());
    }

    // getters (immediate)
    /** Whether the drawer is shown. */
    default boolean isDrawerOpened() { return element().unwrap(AppLayoutAccess.class).drawerOpened(); }

    /**
     * Whether the drawer is an overlay over the content, as on a narrow Viewport, rather than beside it. The browser
     * decides it from the layout: needs {@link com.vaadin.testing.unified.RequiresBrowser}.
     */
    default boolean isOverlay() {
        Wrappers.lazyElementOf(this).run().requireBrowser("isOverlay");
        return Boolean.parseBoolean(element().getProperty("overlay"));
    }
}
