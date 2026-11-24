package com.vaadin.testing.unified.spi;

import com.vaadin.testing.unified.UnifiedElement;
import java.util.List;
import java.util.Optional;

/** Adapter access to a TabSheet, reached by the core with {@code element.unwrap(TabSheetAccess.class)}. */
public interface TabSheetAccess {

    /** Labels of the tabs, in order. */
    List<String> tabs();

    /** Label of the selected tab, or null. */
    String selectedTab();

    /** Whether the user can click the tab with this label. */
    boolean isEnabled(String label);

    /** Clicks the tab with this label, as a user would. */
    void select(String label);

    /**
     * The link the tab with this label holds, which a user's click on the tab lands on: a {@code RouterLink} or an
     * {@code Anchor}, the tab's only {@code <a>}, at any depth. Empty for a tab without one, or with several. The core
     * then follows it as {@code UnifiedLink.click()} does, instead of {@link #select(String)} (#531). The default,
     * empty, keeps an adapter written before it selecting such a tab without following its link.
     */
    default Optional<UnifiedElement> link(String label) {
        return Optional.empty();
    }

    /** The selected tab's content. */
    UnifiedElement content();
}
