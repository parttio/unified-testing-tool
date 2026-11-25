package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spi.SideNavAccess;
import java.time.Duration;
import java.util.List;

/**
 * A {@code SideNav}: the application's navigation, usually in AppLayout's drawer. A Scope for its
 * {@link UnifiedSideNavItem}s, which tells two navigations apart:
 * {@code page.find(UnifiedSideNav.class).withLabel("Admin").find(UnifiedSideNavItem.class).withLabel("Users")}.
 */
@UnifiedComponent(SideNav.class)
public interface UnifiedSideNav extends UnifiedWrapper, Scope, CommonFilters<UnifiedSideNav> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedSideNav withTimeout(Duration timeout);

    /** Navigations whose label, shown above their items, is this, exactly. */
    default UnifiedSideNav withLabel(String label) {
        return where(e -> label.equals(e.unwrap(SideNavAccess.class).label()));
    }

    // getters (immediate)
    /** The label shown above the items, or null. */
    default String getLabel() { return element().unwrap(SideNavAccess.class).label(); }

    /** The labels of its top-level items, in order. */
    default List<String> getItems() { return element().unwrap(SideNavAccess.class).items(); }
}
