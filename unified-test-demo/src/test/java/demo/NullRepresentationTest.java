package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedNumberField;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A value a test sets reaches the field as a user's does, through its client-side update, on every Testing Tool (#689). */
@UnifiedSpringBootTest
class NullRepresentationTest {

    @Test
    @DisplayName("The app reads a value the user enters in its own text field that is null until the user types")
    void appReadsAValueEnteredInAFieldWithANullRepresentation(UnifiedPage page) {
        page.navigate(NullRepresentationView.class);

        page.find(UnifiedTextField.class).withLabel("First name").setValue("Kilo");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedParagraph.class).withId("saved-name")).hasText("Saved: Kilo");
    }

    @Test
    @DisplayName("The app reads a number the user enters in its own number field that is null until the user types")
    void appReadsANumberEnteredInAFieldWithANullRepresentation(UnifiedPage page) {
        page.navigate(NullRepresentationView.class);

        page.find(UnifiedNumberField.class).withLabel("Height").setValue(1.85);
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedParagraph.class).withId("saved-height")).hasText("Saved: 1.85");
    }

    @Test
    @DisplayName("The app reads a date the user enters in its own date picker that is null until the user types")
    void appReadsADateEnteredInAFieldWithANullRepresentation(UnifiedPage page) {
        page.navigate(NullRepresentationView.class);

        page.find(UnifiedDatePicker.class).withLabel("Birthday").setValue(LocalDate.of(1990, 5, 17));
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedParagraph.class).withId("saved-birthday")).hasText("Saved: 1990-05-17");
    }
}
