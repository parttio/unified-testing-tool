package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A date picker whose field is hidden and whose calendar the application opens from a badge (#516). */
@UnifiedSpringBootTest
class WeekPickerTest {

    // --8<-- [start:picksAWeekFromTheOpenCalendar]
    @Test
    @DisplayName("The user opens the week picker from the badge and picks another week")
    void picksAWeekFromTheOpenCalendar(UnifiedPage page) {
        page.navigate(WeekPickerView.class);
        UnifiedSpan badge = page.find(UnifiedSpan.class).withId("week-badge");
        UnifiedDatePicker picker = page.find(UnifiedDatePicker.class).withLabel("Week");

        badge.click();
        picker.setValue(LocalDate.of(2026, 11, 23));

        assertThat(badge).hasText("Week of 2026-11-23");
        assertEquals(LocalDate.of(2026, 11, 23), picker.getValue());
        assertThat(picker).hasProperty("opened", false);
    }
    // --8<-- [end:picksAWeekFromTheOpenCalendar]

    @Test
    @RequiresBrowser
    @DisplayName("A hidden week picker whose calendar is closed can't be set")
    void aClosedHiddenPickerCantBeSet(UnifiedPage page) {
        page.navigate(WeekPickerView.class);
        // the picker stays hidden: no need to wait the full timeout for it
        UnifiedDatePicker picker = page.find(UnifiedDatePicker.class).withLabel("Week")
                .withTimeout(Duration.ofSeconds(1));

        UnifiedAssertionError error = assertThrows(UnifiedAssertionError.class,
                () -> picker.setValue(LocalDate.of(2026, 11, 23)));

        assertTrue(error.getFound().contains("hidden"), error.getMessage());
    }
}
