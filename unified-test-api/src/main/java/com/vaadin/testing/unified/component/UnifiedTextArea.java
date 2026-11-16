package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;

/** A {@link TextArea}: multi-line text. */
@UnifiedComponent(TextArea.class)
public interface UnifiedTextArea extends UnifiedField,
        CommonFilters<UnifiedTextArea>, LabelFilter<UnifiedTextArea>, PlaceholderFilter<UnifiedTextArea> {

    // actions (Auto-Wait)
    default void setValue(String value) { element().setValueAsUser(value); }
    default void clear() { element().setValueAsUser(""); }

    // getters (immediate)
    default String getValue() { return element().getProperty("value"); }
    default String getPlaceholder() { return element().getProperty("placeholder"); }
}
