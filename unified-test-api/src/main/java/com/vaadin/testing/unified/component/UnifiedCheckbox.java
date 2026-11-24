package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import java.time.Duration;

@UnifiedComponent(Checkbox.class)
public interface UnifiedCheckbox extends UnifiedWrapper, CommonFilters<UnifiedCheckbox>, LabelFilter<UnifiedCheckbox> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedCheckbox withTimeout(Duration timeout);

    // actions (Auto-Wait); no-ops when already in that state
    default void check() { setChecked(true); }
    default void uncheck() { setChecked(false); }
    default void setChecked(boolean checked) { element().setValueAsUser(checked); }
    /**
     * Toggles it, as a user clicking it does: an indeterminate checkbox becomes checked. Not
     * {@code element().click()}, which in a browser clicks the middle of the component and misses
     * the box and label when the checkbox is wider than they are (#328).
     */
    default void click() { setChecked(!isChecked()); }

    // getters (immediate)
    default boolean isChecked() { return Boolean.TRUE.equals(element().getPropertyBoolean("checked")); }
    default boolean isIndeterminate() { return Boolean.TRUE.equals(element().getPropertyBoolean("indeterminate")); }
    default String getLabel() { return element().getProperty("label"); }
    default String getHelperText() { return element().getProperty("helperText"); }
}
