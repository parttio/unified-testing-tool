package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.component.UnifiedDatePicker;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code UnifiedDatePicker.setValue} on a picker whose field the user can't reach but whose calendar is open, as
 * when the application opens the picker from a button of its own (#516).
 */
class DatePickerSetValueTest {

    private final FakePage fake = new FakePage().add("vaadin-date-picker", "label", "Week");
    private final Map<String, String> picker = fake.components.get(0);
    private final UnifiedDatePicker week = fake.page.find(UnifiedDatePicker.class).withLabel("Week");

    @Test
    void aVisiblePickerIsFilledAsBefore() {
        week.setValue(LocalDate.of(2026, 11, 23));

        assertEquals(List.of("set 2026-11-23"), fake.actions);
        assertEquals("   1  fill date picker “Week” with “2026-11-23”\n", fake.lines());
    }

    @Test
    void aHiddenPickerWithItsCalendarOpenGetsTheDateAndItsCalendarCloses() {
        picker.put("visible", "false");
        picker.put("opened", "true");

        week.setValue(LocalDate.of(2026, 11, 23));

        assertEquals(List.of("property value=2026-11-23", "property opened=false"), fake.actions);
        assertEquals("false", picker.get("opened"));
        assertEquals("   1  fill date picker “Week” with “2026-11-23”\n", fake.lines());
    }

    @Test
    void aVisiblePickerWithItsCalendarOpenIsFilledAndItsCalendarCloses() {
        picker.put("opened", "true");

        week.setValue(LocalDate.of(2026, 11, 23));

        assertEquals(List.of("set 2026-11-23", "property opened=false"), fake.actions);
    }

    @Test
    void aHiddenPickerWithItsCalendarClosedFailsAsAHiddenComponentDoes() {
        picker.put("visible", "false");

        AssertionError error = assertThrows(AssertionError.class, () -> week.setValue(LocalDate.of(2026, 11, 23)));

        assertTrue(error.getMessage().contains("but hidden"), error.getMessage());
        assertEquals(List.of(), fake.actions);
    }
}
