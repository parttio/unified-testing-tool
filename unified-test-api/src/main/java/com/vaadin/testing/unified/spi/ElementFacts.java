package com.vaadin.testing.unified.spi;

import com.vaadin.testing.unified.UnifiedElement;

/**
 * What a component shows a user, read for a failure message only: the values the naming filters match on, and the
 * nearest ancestor with a test id or an id, which tells several matches apart. Any part can be null.
 *
 * @param label the {@code label} property ({@link Filter.Kind#LABEL})
 * @param text the text, as {@link UnifiedElement#getText()} ({@link Filter.Kind#TEXT})
 * @param placeholder the {@code placeholder} property ({@link Filter.Kind#PLACEHOLDER})
 * @param testId the {@code data-testid} attribute ({@link Filter.Kind#TEST_ID})
 * @param accessibleName as {@link UnifiedElement#getAccessibleName()} ({@link Filter.Kind#ACCESSIBLE_NAME})
 * @param ancestorTestId the {@code data-testid} of the nearest ancestor that has a test id or an id
 * @param ancestorId the {@code id} of that ancestor
 */
public record ElementFacts(String label, String text, String placeholder, String testId, String accessibleName,
        String ancestorTestId, String ancestorId) {

    /** The value a filter of this kind matches on, or null for kinds without one here. */
    public String value(Filter.Kind kind) {
        return switch (kind) {
            case LABEL -> label;
            case TEXT -> text;
            case PLACEHOLDER -> placeholder;
            case TEST_ID -> testId;
            case ACCESSIBLE_NAME -> accessibleName;
            default -> null;
        };
    }

    /** One read per value, through the Unified Element; no ancestor. */
    public static ElementFacts read(UnifiedElement e) {
        return new ElementFacts(e.getProperty("label"), e.getText(), e.getProperty("placeholder"),
                e.getAttribute("data-testid"), e.getAccessibleName(), null, null);
    }
}
