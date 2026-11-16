package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedNumberField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Typed value fields; written once, runs on Browserless and Playwright. */
@UnifiedSpringBootTest
class PreferencesTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 5, 17);

    // --8<-- [start:savesTypedValues]
    @Test
    @DisplayName("The user saves a height, a birthday and a newsletter subscription")
    void savesTypedValues(UnifiedPage page) {
        page.navigate(PreferencesView.class);

        UnifiedNumberField height = page.find(UnifiedNumberField.class).withLabel("Height (cm)");
        UnifiedDatePicker birthday = page.find(UnifiedDatePicker.class).withLabel("Birthday");
        UnifiedCheckbox newsletter = page.find(UnifiedCheckbox.class).withLabel("Subscribe to newsletter");

        height.setValue(180.5);
        birthday.setValue(BIRTHDAY);
        newsletter.check();
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class))
                .hasText("Saved: height 180.5, birthday 1990-05-17, newsletter yes, remember yes");
        assertThat(height).hasValue(180.5).isValid();
        assertThat(birthday).hasValue(BIRTHDAY).isValid();
        assertThat(newsletter).isChecked();
        assertEquals(180.5, height.getValue());
        assertEquals(BIRTHDAY, birthday.getValue());
    }
    // --8<-- [end:savesTypedValues]

    @Test
    @DisplayName("A height of 170.0 reads as 170")
    void wholeNumbers(UnifiedPage page) {
        page.navigate(PreferencesView.class);
        UnifiedNumberField height = page.find(UnifiedNumberField.class).withLabel("Height (cm)");

        height.setValue(170.0);

        assertThat(height).hasValue(170);
    }

    @Test
    @DisplayName("The user clears the height and the birthday")
    void clearsValues(UnifiedPage page) {
        page.navigate(PreferencesView.class);
        UnifiedNumberField height = page.find(UnifiedNumberField.class).withLabel("Height (cm)");
        UnifiedDatePicker birthday = page.find(UnifiedDatePicker.class).withLabel("Birthday");
        height.setValue(180.0);
        birthday.setValue(BIRTHDAY);

        height.clear();
        birthday.clear();

        assertThat(height).isEmpty();
        assertThat(birthday).isEmpty();
        assertNull(height.getValue());
    }

    @Test
    @DisplayName("A height of 300 cm and a birthday in the future are invalid")
    void outOfRangeValuesAreInvalid(UnifiedPage page) {
        page.navigate(PreferencesView.class);
        UnifiedNumberField height = page.find(UnifiedNumberField.class).withLabel("Height (cm)");
        UnifiedDatePicker birthday = page.find(UnifiedDatePicker.class).withLabel("Birthday");

        height.setValue(300.0);
        birthday.setValue(LocalDate.of(2030, 1, 1));

        assertThat(height).isInvalid();
        assertThat(birthday).isInvalid();
    }

    @Test
    @DisplayName("The height field accepts 50 to 250 cm in half steps")
    void readsNumberFieldLimits(UnifiedPage page) {
        page.navigate(PreferencesView.class);
        UnifiedNumberField height = page.find(UnifiedNumberField.class).withLabel("Height (cm)");

        assertEquals(50.0, height.getMin());
        assertEquals(250.0, height.getMax());
        assertEquals(0.5, height.getStep());
        assertThat(height).hasLabel("Height (cm)").isEmpty();
    }

    @Test
    @DisplayName("The user turns Remember me off and subscribes to the newsletter")
    void checksAndUnchecks(UnifiedPage page) {
        page.navigate(PreferencesView.class);
        UnifiedCheckbox remember = page.find(UnifiedCheckbox.class).withLabel("Remember me");
        UnifiedCheckbox newsletter = page.find(UnifiedCheckbox.class).withLabel("Subscribe to newsletter");

        assertThat(remember).isChecked();
        remember.check(); // already checked: no change
        assertThat(remember).isChecked();
        remember.uncheck();
        newsletter.setChecked(true);
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(remember).isNotChecked();
        assertThat(page.find(UnifiedNotification.class))
                .hasText("Saved: height null, birthday null, newsletter yes, remember no");
    }

    @Test
    @DisplayName("Accept terms starts neither checked nor unchecked")
    void readsIndeterminate(UnifiedPage page) {
        page.navigate(PreferencesView.class);

        assertThat(page.find(UnifiedCheckbox.class).withLabel("Accept terms"))
                .isIndeterminate().isNotChecked().hasLabel("Accept terms");
    }
}
