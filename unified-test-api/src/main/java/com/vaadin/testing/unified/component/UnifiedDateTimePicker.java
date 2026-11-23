package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.spi.DateTimePickerAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A {@link DateTimePicker}: the value is a {@link LocalDateTime}, {@code null} when empty. */
@UnifiedComponent(DateTimePicker.class)
public interface UnifiedDateTimePicker extends UnifiedField,
        CommonFilters<UnifiedDateTimePicker>, LabelFilter<UnifiedDateTimePicker> {

    // actions (Auto-Wait)
    /** Enters the date and time as a user would; {@code null} empties the field. */
    default void setValue(LocalDateTime value) { element().setValueAsUser(value); }
    default void clear() { element().setValueAsUser(null); }

    /**
     * Types this text into the date half and the time half as it is, without parsing it, then commits it:
     * for testing how the app handles input it can't parse ({@code typeText("dsdds", "10:00")}) or an
     * incomplete one ({@code typeText("", "10:00")}). An empty text leaves that half empty. Text the
     * field can't parse, or only one half filled, leaves the value empty and the field invalid, on every
     * Testing Tool. Browserless parses only ISO text ({@code 2026-11-08}, {@code 10:30}); a browser parses the
     * text in the field's locale format: to type a date the field can parse, use {@link #typeText(LocalDate, String)}.
     */
    default void typeText(String date, String time) {
        Wrappers.lazyElementOf(this).act("type \"" + date + "\" / \"" + time + "\" in",
                e -> e.unwrap(DateTimePickerAccess.class).typeText(date, time));
    }

    /**
     * Types this date into the date half in the field's own format, on every Testing Tool and in every locale,
     * and this text into the time half as it is, then commits them as {@link #typeText(String, String)} does:
     * for testing incomplete input, a date without a time ({@code typeText(LocalDate.of(2026, 11, 8), "")}).
     */
    default void typeText(LocalDate date, String time) {
        Wrappers.lazyElementOf(this).act("type " + date + " / \"" + time + "\" in", e -> {
            DateTimePickerAccess picker = e.unwrap(DateTimePickerAccess.class);
            picker.typeText(picker.formatDate(date), time);
        });
    }

    // getters (immediate)
    /** The value property is ISO ({@code 2026-11-08T10:30}) in the browser and on the server. */
    default LocalDateTime getValue() {
        String value = element().getProperty("value");
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }
}
