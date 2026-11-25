package com.vaadin.testing.unified.spi;

import com.vaadin.testing.unified.UnifiedElement;
import java.util.List;

/**
 * Adapter access to a SideNavItem, reached by the core with {@code element.unwrap(SideNavItemAccess.class)}. Its
 * path is its {@code path} attribute, which the core reads itself.
 */
public interface SideNavItemAccess {

    /** The item's own text ({@code new SideNavItem("Users", ..)}), whitespace collapsed: not its child items'. */
    String label();

    /** The labels of its child items, in order. */
    List<String> items();

    /**
     * Whether it is the page the user is on: its path, or one of its path aliases, matches the page's location, as
     * the web component's {@code current} says in a browser.
     */
    boolean isCurrent();

    /** Whether its child items are shown. */
    boolean isExpanded();

    /** Whether it is inside an item whose child items aren't shown, so a user expands that item first. */
    boolean inCollapsedItem();

    /** Expands the collapsed items it is inside, outermost first, as a user does by clicking their toggles. */
    void expandParents();

    /** Clicks its toggle button, as a user would: shows or hides its child items. */
    void toggle();

    /**
     * What a user's click on an item with a path lands on, which the core follows as a link: the item's own
     * {@code <a>} in a browser. A Testing Tool without a browser gives an element that follows the item's path
     * through {@link LinkAccess}. An item without a path isn't followed: a user's click on it toggles it.
     */
    UnifiedElement link();
}
