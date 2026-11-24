package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;

/**
 * An {@link EmailField}: text, as a {@link UnifiedTextField}. Whether the address is well formed is the field's
 * validation, read with {@link #isInvalid()} and {@link #getErrorMessage()}.
 */
@UnifiedComponent(EmailField.class)
public interface UnifiedEmailField extends UnifiedTextInput,
        CommonFilters<UnifiedEmailField>, LabelFilter<UnifiedEmailField>, PlaceholderFilter<UnifiedEmailField> {
}
