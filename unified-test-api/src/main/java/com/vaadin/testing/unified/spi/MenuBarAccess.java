package com.vaadin.testing.unified.spi;

import java.util.List;

/**
 * Adapter access to a MenuBar, reached by the core with {@code element.unwrap(MenuBarAccess.class)}. A path
 * is the items' Item Names from the top level down: {@code ["File", "Export", "PDF"]}. An item's name is its
 * text, or its {@code aria-label} when it shows no text (an icon-only "⋮" item).
 */
public interface MenuBarAccess {

    /** Item Names of the items under {@code path} (the top-level items for an empty path), in order. */
    List<String> items(List<String> path);

    /**
     * Opens the menus along the path and clicks its last item, as a user would. A top-level item the menu bar has
     * folded into its overflow menu is reached through that menu when {@code throughOverflow}, else refused with
     * {@link #folded}.
     */
    void click(List<String> path, boolean throughOverflow);

    /**
     * Whether the user can click the last item of the path; opens the menus along it in a browser. A folded top-level
     * item as in {@link #click}.
     */
    boolean isEnabled(List<String> path, boolean throughOverflow);

    /**
     * The refusal of a path that starts at a folded item, the same on every Testing Tool: a user sees it only in the
     * overflow menu, and the test says it opens that menu with {@code unfoldingOverflow()}.
     */
    static IllegalStateException folded(List<String> path, String call) {
        String args = String.join(", ", path.stream().map(n -> "\"" + n + "\"").toList());
        return new IllegalStateException("“" + path.get(0) + "” is in the menu bar's overflow menu (\"More options\")"
                + " at this Viewport, not on the menu bar: a user opens that menu first. Write"
                + " unfoldingOverflow()." + call + "(" + args + ") to do so.");
    }

    /**
     * Item Names of the top-level items the menu bar has moved into its overflow menu ("More options") for lack of
     * room, in order; empty when they all fit. Only a browser lays the menu bar out: the core asks a browser only.
     */
    default List<String> overflowItems() {
        throw new UnsupportedOperationException("This Testing Tool can't tell which menu items overflow");
    }
}
