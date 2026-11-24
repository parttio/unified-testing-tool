package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;

/**
 * A {@link PasswordField}: text, as a {@link UnifiedTextField}, that the reports never show. What is typed into it
 * reads {@code ••••} in the User Steps, and its values read {@code “••••”} in a failed check.
 */
@UnifiedComponent(PasswordField.class)
public interface UnifiedPasswordField extends UnifiedTextInput,
        CommonFilters<UnifiedPasswordField>, LabelFilter<UnifiedPasswordField>,
        PlaceholderFilter<UnifiedPasswordField> {
}
