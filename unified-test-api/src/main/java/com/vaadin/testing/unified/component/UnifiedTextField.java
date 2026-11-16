package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;

@UnifiedComponent(TextField.class)
public interface UnifiedTextField extends UnifiedField,
        CommonFilters<UnifiedTextField>, LabelFilter<UnifiedTextField>, PlaceholderFilter<UnifiedTextField> {

    // actions (Auto-Wait)
    default void setValue(String value) { element().setValueAsUser(value); }
    default void clear() { element().setValueAsUser(""); }

    // getters (immediate)
    default String getValue() { return element().getProperty("value"); }
    default String getPlaceholder() { return element().getProperty("placeholder"); }
}
