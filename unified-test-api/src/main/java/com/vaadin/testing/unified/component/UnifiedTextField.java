package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;

/** A {@link TextField}: single-line text. */
@UnifiedComponent(TextField.class)
public interface UnifiedTextField extends UnifiedTextInput,
        CommonFilters<UnifiedTextField>, LabelFilter<UnifiedTextField>, PlaceholderFilter<UnifiedTextField> {
}
