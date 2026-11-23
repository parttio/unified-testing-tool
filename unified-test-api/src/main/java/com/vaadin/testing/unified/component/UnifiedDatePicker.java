package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.internal.datepicker.DatePickerSupport;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import java.time.LocalDate;

/** A {@link DatePicker}: the value is a {@link LocalDate}, {@code null} when empty. */
@UnifiedComponent(DatePicker.class)
public interface UnifiedDatePicker extends UnifiedField,
        CommonFilters<UnifiedDatePicker>, LabelFilter<UnifiedDatePicker>, PlaceholderFilter<UnifiedDatePicker> {

    // actions (Auto-Wait)
    /**
     * Chooses the date as a user would; {@code null} empties the field. It waits until the field is visible and
     * enabled, or until the picker's calendar is open: a picker whose field is hidden, which the application
     * opens from a button of its own, gets the date as a pick in its calendar would set it, and its calendar
     * closes.
     */
    default void setValue(LocalDate value) { DatePickerSupport.setValue(this, value); }
    default void clear() { element().setValueAsUser(null); }

    /**
     * Types this text into the field as it is, without parsing it, then commits it like Enter: for testing
     * how the app handles input it can't parse ("dsdds"). Text the field can't parse leaves the value empty
     * and the field invalid (bad input), on every Testing Tool. Browserless parses only ISO dates
     * ({@code 2026-11-08}); a browser parses the text in the field's locale format.
     */
    default void typeText(String text) { element().setValueAsUser(text); }

    // getters (immediate)
    /** The value property is an ISO date ({@code 2026-11-08}) in the browser and on the server. */
    default LocalDate getValue() {
        String value = element().getProperty("value");
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    default String getPlaceholder() { return element().getProperty("placeholder"); }
}
