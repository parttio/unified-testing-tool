package com.vaadin.testing.unified.internal.datepicker;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.steps.Phrases;
import java.time.LocalDate;

/**
 * Choosing a date on a {@link UnifiedDatePicker} (#516). The user can choose one while the field is visible and
 * enabled, and also while its calendar is open though the field is hidden, as when the application opens the
 * picker from a button of its own. In that case the date is set as a pick in the calendar sets it, through the
 * picker's {@code value}, and the calendar closes, as it does after a pick. A usable field is set as before, without
 * any read more (#341 counts the round trips); on Browserless, which has no calendar, an open picker is closed.
 */
public final class DatePickerSupport {
    private DatePickerSupport() {}

    public static void setValue(UnifiedDatePicker picker, LocalDate value) {
        LazyElement element = Wrappers.lazyElementOf(picker);
        boolean[] usable = { false };
        element.act(() -> Phrases.forElementAction("set the value of", element, value), "set the value of",
                e -> (usable[0] = LazyElement.usable(e)) || isOpen(e), e -> {
                    if (usable[0]) {
                        // as before, without a read more: in a browser the Testing Tool commits the date its way
                        e.setValueAsUser(value);
                        if (!element.run().tool().hasRealBrowser() && isOpen(e)) {
                            e.setProperty("opened", false); // Browserless has no calendar to close it
                        }
                    } else {
                        e.setProperty("value", value == null ? "" : value.toString());
                        e.setProperty("opened", false); // as after a pick in the calendar
                    }
                });
    }

    /** Whether the picker's calendar is open: the {@code opened} property, on every Testing Tool. */
    private static boolean isOpen(UnifiedElement e) {
        return Boolean.TRUE.equals(e.getPropertyBoolean("opened"));
    }
}
