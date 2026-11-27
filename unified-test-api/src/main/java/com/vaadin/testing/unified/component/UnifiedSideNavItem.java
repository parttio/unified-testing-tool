package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.sidenav.SideNavSupport;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spi.SideNavItemAccess;
import java.time.Duration;
import java.util.List;

/**
 * An item of a {@code SideNav}, found by its label. Clicking it goes where a user's click goes:
 *
 * <pre>{@code
 * page.find(UnifiedSideNavItem.class).withLabel("Orders").click();
 * assertThat(page).isAt(OrdersView.class);
 * }</pre>
 *
 * A Scope: a nested item is found inside its parent, which tells apart two items of the same label, and reads as
 * {@code side nav item “Users” in side nav item “Admin”}. A click on a nested item expands the items it is in first,
 * as a user does.
 */
@UnifiedComponent(SideNavItem.class)
public interface UnifiedSideNavItem extends UnifiedWrapper, Scope, CommonFilters<UnifiedSideNavItem> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedSideNavItem withTimeout(Duration timeout);

    /** Items whose label is this, exactly: the item's own text, not its child items'. */
    default UnifiedSideNavItem withLabel(String label) {
        return where(e -> label.equals(e.unwrap(SideNavItemAccess.class).label()));
    }

    /** Items whose path is this, exactly, as the application set it ({@link #getPath()}). */
    default UnifiedSideNavItem withPath(String path) { return withAttribute("path", path); }

    // actions (Auto-Wait)
    /**
     * Clicks the item, as a user does, after expanding the collapsed items it is inside. An item with a path is
     * followed as {@link UnifiedLink#click()} follows a link, and waits for the new page; on Browserless, a path out
     * of the application fails asking for {@code @RequiresBrowser}. An item without a path expands or collapses its
     * child items.
     */
    default void click() { SideNavSupport.click(this); }

    /** Shows its child items, as a user does with its toggle button; nothing when they are shown already. */
    default void expand() { SideNavSupport.expand(this); }

    /** Hides its child items, as a user does with its toggle button; nothing when they are hidden already. */
    default void collapse() { SideNavSupport.collapse(this); }

    // getters (immediate)
    default String getLabel() { return SideNavSupport.access(this).label(); }

    /**
     * The path as the application set it, the same on every Testing Tool: {@code "orders"} for
     * {@code new SideNavItem("Orders", OrdersView.class)}. Null for an item without one.
     */
    default String getPath() { return element().getAttribute("path"); }

    /** Whether it is the page the user is on: its path, or one of its aliases, is the page's. */
    default boolean isCurrent() { return SideNavSupport.access(this).isCurrent(); }

    /** Whether its child items are shown. */
    default boolean isExpanded() { return SideNavSupport.access(this).isExpanded(); }

    /** The labels of its child items, in order. */
    default List<String> getItems() { return SideNavSupport.access(this).items(); }
}
