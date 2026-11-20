package com.vaadin.testing.unified.spi;

import java.time.LocalDate;

/**
 * Adapter access to a DateTimePicker, reached by the core with
 * {@code element.unwrap(DateTimePickerAccess.class)}.
 */
public interface DateTimePickerAccess {

    /**
     * Types this text into each half as it is, without parsing it, and commits it as a user leaving the
     * field does; an empty text leaves that half empty.
     */
    void typeText(String date, String time);

    /**
     * The text a user types into the date half to enter this date: the date in the field's own format, which
     * a browser takes from the field's locale.
     */
    String formatDate(LocalDate date);
}
