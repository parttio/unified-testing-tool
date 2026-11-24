package com.vaadin.testing.unified.spi;

import com.vaadin.testing.unified.UnifiedElement;

/**
 * What a component shows a user, read for a failure message only: the values the naming filters match on, its own
 * id, and the nearest ancestor with a test id or an id, which tells several matches apart. Any part can be null.
 *
 * @param label the {@code label} property ({@link Filter.Kind#LABEL})
 * @param text the text, as {@link UnifiedElement#getText()} ({@link Filter.Kind#TEXT})
 * @param placeholder the {@code placeholder} property ({@link Filter.Kind#PLACEHOLDER})
 * @param testId the {@code data-testid} attribute ({@link Filter.Kind#TEST_ID})
 * @param accessibleName as {@link UnifiedElement#getAccessibleName()} ({@link Filter.Kind#ACCESSIBLE_NAME})
 * @param ancestorTestId the {@code data-testid} of the nearest ancestor that has a test id or an id
 * @param ancestorId the {@code id} of that ancestor
 * @param internalPart whether the element is an internal part of another component, which that component renders
 *        itself and the application did not create: in a browser, the {@code <vaadin-text-area>} a
 *        {@code <vaadin-message-input>} puts in its light DOM. Browserless finds only the application's components,
 *        so none of its elements is one.
 * @param data the element's own {@code data-*} attributes but {@code data-testid}, by name: what tells alike
 *        components apart when nothing else does (#547); empty when the adapter doesn't read them
 * @param id the element's own {@code id}, as a browser reads it ({@link Filter.Kind#ID}): on Browserless also the id
 *        an {@code @Id} binding gave it in its Template (#609); null when it has none or the adapter doesn't read it
 */
public record ElementFacts(String label, String text, String placeholder, String testId, String accessibleName,
        String ancestorTestId, String ancestorId, boolean internalPart, java.util.Map<String, String> data,
        String id) {

    public ElementFacts {
        data = data == null ? java.util.Map.of() : java.util.Map.copyOf(data);
    }

    /** Facts without the element's own id. */
    public ElementFacts(String label, String text, String placeholder, String testId, String accessibleName,
            String ancestorTestId, String ancestorId, boolean internalPart, java.util.Map<String, String> data) {
        this(label, text, placeholder, testId, accessibleName, ancestorTestId, ancestorId, internalPart, data, null);
    }

    /** Facts without {@code data-*} attributes. */
    public ElementFacts(String label, String text, String placeholder, String testId, String accessibleName,
            String ancestorTestId, String ancestorId, boolean internalPart) {
        this(label, text, placeholder, testId, accessibleName, ancestorTestId, ancestorId, internalPart, null);
    }

    /** Facts of an element the application created. */
    public ElementFacts(String label, String text, String placeholder, String testId, String accessibleName,
            String ancestorTestId, String ancestorId) {
        this(label, text, placeholder, testId, accessibleName, ancestorTestId, ancestorId, false);
    }

    /** The value a filter of this kind matches on, or null for kinds without one here. */
    public String value(Filter.Kind kind) {
        return switch (kind) {
            case LABEL -> label;
            case TEXT -> text;
            case PLACEHOLDER -> placeholder;
            case TEST_ID -> testId;
            case ACCESSIBLE_NAME -> accessibleName;
            case ID -> id;
            default -> null;
        };
    }

    /** One read per value, through the Unified Element; no ancestor, and not an internal part. */
    public static ElementFacts read(UnifiedElement e) {
        return new ElementFacts(e.getProperty("label"), e.getText(), e.getProperty("placeholder"),
                e.getAttribute("data-testid"), e.getAccessibleName(), null, null);
    }
}
