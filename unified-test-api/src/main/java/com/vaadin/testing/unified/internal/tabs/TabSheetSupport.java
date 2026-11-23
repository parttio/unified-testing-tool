package com.vaadin.testing.unified.internal.tabs;

import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.ElementResolver;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.LazyScope;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.spi.StaleElementException;
import com.vaadin.testing.unified.spi.TabSheetAccess;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.failures.ItemFailures;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import java.util.List;

/** TabSheet behaviour: Auto-Wait and verified tab selection, the selected content as a lazy Scope. */
public final class TabSheetSupport {
    private TabSheetSupport() {}

    public static TabSheetAccess access(UnifiedWrapper tabSheet) {
        return tabSheet.element().unwrap(TabSheetAccess.class);
    }

    public static void select(UnifiedWrapper tabSheet, String label) {
        LazyElement element = Wrappers.lazyElementOf(tabSheet);
        element.act("select tab \"" + label + "\" in", e -> {
            TabSheetAccess access = e.unwrap(TabSheetAccess.class);
            List<String> tabs = access.tabs();
            String sheet = StepTarget.of(element).renderWithScope();
            if (!tabs.contains(label)) {
                throw ItemFailures.missing(element.run(), "tab", label, sheet, tabs, "selectTab");
            }
            // Auto-Wait for the tab itself
            element.run().pollFor(() -> e.unwrap(TabSheetAccess.class).isEnabled(label) ? Boolean.TRUE : null,
                    () -> FailureParts.of("an enabled tab “" + label + "” in " + sheet, "1, but the tab is disabled"));
            access.select(label);
            // the tab's listener may navigate away (Page.setLocation, #554): when the check can't read the selection,
            // tabs the lookup no longer finds have nothing to check, as a select's field the app replaced (#377)
            String selected;
            try {
                selected = access.selectedTab();
            } catch (RuntimeException readFailed) {
                if (!element.stillFound()) {
                    return;
                }
                if (readFailed instanceof StaleElementException stale) {
                    throw stale;
                }
                // e.g. Playwright's own timeout: the Unified failure, with it as the cause
                throw notSelected(element, label, null, readFailed);
            }
            if (!label.equals(selected) && element.stillFound()) {
                throw notSelected(element, label, selected, null);
            }
        });
    }

    /** {@code Expected  tab “Security” selected in tab sheet “Account”}, {@code Found  “Profile”}. */
    private static UnifiedAssertionError notSelected(LazyElement element, String label, String selected,
            Throwable cause) {
        String found = cause != null ? null : selected == null ? "none" : "“" + selected + "”";
        return element.run().failure(FailureParts.of("tab “" + label + "” selected in "
                + StepTarget.of(element).renderWithScope(), found), cause);
    }

    /** The selected tab's content, resolved again on every use: it follows tab switches. */
    public static Scope content(UnifiedWrapper tabSheet) {
        LazyElement sheet = Wrappers.lazyElementOf(tabSheet);
        ElementResolver resolver = new ElementResolver() {
            @Override
            public List<UnifiedElement> resolve() {
                return List.of(access(tabSheet).content());
            }

            @Override
            public String describe() {
                return sheet.describe() + ".content()";
            }

            @Override
            public com.vaadin.testing.unified.internal.steps.StepTarget userTarget() {
                return com.vaadin.testing.unified.internal.steps.StepTarget.of(sheet);
            }
        };
        return new LazyScope(new LazyElement(resolver, -1, sheet.run()));
    }
}
