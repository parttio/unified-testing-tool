package com.vaadin.testing.unified.component;

import java.time.Duration;

/**
 * The text-value contract of the fields a user types text into: {@link UnifiedTextField}, {@link UnifiedTextArea},
 * {@link UnifiedEmailField} and {@link UnifiedPasswordField}. The value is the text as the field holds it, the empty
 * string when empty. Not a Wrapper of its own: look up one of those.
 */
public interface UnifiedTextInput extends UnifiedField {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedTextInput withTimeout(Duration timeout);

    // actions (Auto-Wait)
    /** Types the value as a user would, replacing what the field holds. */
    default void setValue(String value) { element().setValueAsUser(value); }
    /** Empties the field as a user would. */
    default void clear() { element().setValueAsUser(""); }

    // getters (immediate)
    default String getValue() { return element().getProperty("value"); }
    default String getPlaceholder() { return element().getProperty("placeholder"); }
}
