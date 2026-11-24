package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.tabs.TabSheetSupport;
import com.vaadin.testing.unified.query.CommonFilters;
import java.time.Duration;
import java.util.List;

/**
 * A {@link TabSheet}. Tabs are identified by their label. Searching inside the selected tab:
 *
 * <pre>{@code
 * tabs.selectTab("Security");
 * tabs.content().find(UnifiedButton.class).withText("Change password").click();
 * }</pre>
 */
@UnifiedComponent(TabSheet.class)
public interface UnifiedTabSheet extends UnifiedWrapper, CommonFilters<UnifiedTabSheet> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedTabSheet withTimeout(Duration timeout);

    /** Tab sheets having a tab with this label. A custom filter built from {@code where(..)}. */
    default UnifiedTabSheet withTab(String label) {
        return where(e -> e.unwrap(com.vaadin.testing.unified.spi.TabSheetAccess.class).tabs().contains(label));
    }

    // actions (Auto-Wait); fail when the tab doesn't exist or isn't selected afterwards
    /**
     * Selects the tab as a user's click does. A tab holding a link ({@code RouterLink}, {@code Anchor}) follows it,
     * as {@link UnifiedLink#click()} does, and waits for the new page; one that navigated isn't checked for its
     * selection. On Browserless, a link out of the application fails asking for {@code @RequiresBrowser}.
     */
    default void selectTab(String label) { TabSheetSupport.select(this, label); }

    // getters (immediate)
    default String getSelectedTab() { return TabSheetSupport.access(this).selectedTab(); }
    default List<String> getTabs() { return TabSheetSupport.access(this).tabs(); }

    /** The selected tab's content as a Scope; it follows tab switches. */
    default Scope content() { return TabSheetSupport.content(this); }
}
