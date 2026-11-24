package com.vaadin.testing.unified.component;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * The contract of the dialogs a user sees in an overlay: {@link UnifiedDialog} and {@link UnifiedConfirmDialog}.
 * Found while open, by the header the user reads; closed with Escape. Not a Wrapper of its own: look up one of
 * those.
 */
public interface UnifiedOverlayDialog<SELF> extends UnifiedWrapper, CommonFilters<SELF> {

    /** The dialog's header, exactly. A custom filter built from {@code where(..)}. */
    default SELF withHeaderTitle(String title) {
        return where(e -> title.equals(headerTitleOf(e)));
    }

    /** The header the dialog shows as text: a Dialog's {@code headerTitle}, a ConfirmDialog's {@code header}; null if none. */
    default String getHeaderTitle() { return headerTitleOf(element()); }
    default boolean isOpen() { return Boolean.TRUE.equals(element().getPropertyBoolean("opened")); }
    /** Like pressing Escape. */
    default void close() { element().pressKey("Escape"); }

    /**
     * A Dialog keeps its header text in {@code headerTitle}, a ConfirmDialog in {@code header}. Null when it has
     * none, on every Testing Tool: a browser's web component holds an empty string.
     */
    private static String headerTitleOf(UnifiedElement e) {
        String title = e.getProperty("headerTitle");
        if (title == null || title.isEmpty()) {
            title = e.getProperty("header");
        }
        return title == null || title.isEmpty() ? null : title;
    }
}
