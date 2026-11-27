package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spi.MenuBarAccess;
import java.time.Duration;
import java.util.List;

/**
 * A {@link MenuBar}. Items are identified by their Item Name, from the top level down:
 * {@code menu.clickItem("File", "Export", "PDF")} opens File, then Export, and clicks PDF. An item's name is
 * its text, or its {@code aria-label} when it shows no text: an icon-only "⋮" item labelled "Actions for
 * Taxi" is {@code clickItem("Actions for Taxi", "Edit")}.
 */
@UnifiedComponent(MenuBar.class)
public interface UnifiedMenuBar extends UnifiedWrapper, CommonFilters<UnifiedMenuBar> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedMenuBar withTimeout(Duration timeout);

    /** Menu bars with a top-level item of this Item Name. */
    default UnifiedMenuBar withItem(String text) {
        return where(e -> e.unwrap(MenuBarAccess.class).items(List.of()).contains(text));
    }

    // actions (Auto-Wait): opens the menus along the path, as a user would
    /**
     * Fails at once on an item the menu bar has folded into its overflow menu, which the user doesn't see on it: use
     * {@link #unfoldingOverflow()}.
     */
    default void clickItem(String topLevel, String... nested) {
        click(this, path(topLevel, nested), false);
    }

    /**
     * This menu bar, reaching an item it has folded into its overflow menu ("More options") through that menu, as a
     * user does when they open it: {@code menu.unfoldingOverflow().clickItem("Print")} works at every Viewport. Without
     * it, a click on a folded item fails (ADR 0011).
     */
    default Unfolding unfoldingOverflow() {
        return new Unfolding(this);
    }

    /** A menu bar whose folded items are reached through its overflow menu: {@link #unfoldingOverflow()}. */
    record Unfolding(UnifiedMenuBar menuBar) {
        /** {@link UnifiedMenuBar#clickItem}, opening "More options" first for a folded item. */
        public void clickItem(String topLevel, String... nested) {
            click(menuBar, path(topLevel, nested), true);
        }

        /** {@link UnifiedMenuBar#isItemEnabled}, opening "More options" first for a folded item. */
        public boolean isItemEnabled(String topLevel, String... nested) {
            return menuBar.element().unwrap(MenuBarAccess.class).isEnabled(path(topLevel, nested), true);
        }
    }

    // getters (immediate)
    /** The top-level items' Item Names, those in the overflow menu included. */
    default List<String> getItems() { return element().unwrap(MenuBarAccess.class).items(List.of()); }

    /**
     * The Item Names of the top-level items moved into the overflow menu ("More options") because the menu bar is too
     * narrow for them, in order; empty when they all fit (ADR 0011). Needs
     * {@link com.vaadin.testing.unified.RequiresBrowser}.
     */
    default List<String> getOverflowItems() {
        Wrappers.lazyElementOf(this).run().requireBrowser("getOverflowItems");
        return element().unwrap(MenuBarAccess.class).overflowItems();
    }

    /** The Item Names of the items in the sub-menu at this path; opens the menus in a browser. */
    default List<String> getItems(String topLevel, String... nested) {
        return element().unwrap(MenuBarAccess.class).items(path(topLevel, nested));
    }

    /**
     * Whether the user can click the item at this path: a disabled item is shown but can't be clicked. Opens
     * the menus along the path in a browser.
     */
    default boolean isItemEnabled(String topLevel, String... nested) {
        return element().unwrap(MenuBarAccess.class).isEnabled(path(topLevel, nested), false);
    }

    private static void click(UnifiedMenuBar menuBar, List<String> path, boolean throughOverflow) {
        Wrappers.lazyElementOf(menuBar).act("click " + path + " in",
                e -> e.unwrap(MenuBarAccess.class).click(path, throughOverflow));
    }

    private static List<String> path(String first, String... rest) {
        java.util.List<String> path = new java.util.ArrayList<>();
        path.add(first);
        path.addAll(List.of(rest));
        return List.copyOf(path);
    }
}
