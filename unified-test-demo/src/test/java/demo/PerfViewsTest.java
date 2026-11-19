package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import demo.perf.LargeComboBoxView;
import demo.perf.LargeGridView;
import demo.perf.LongFormView;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The performance scenarios' views work on every Testing Tool. The Performance Pairs themselves live in
 * {@code demo.perf}, outside the normal run; this keeps the views from breaking unnoticed.
 */
@UnifiedSpringBootTest
class PerfViewsTest {

    @Test
    @DisplayName("The long form counts the fields the user filled")
    void longFormCountsFilledFields(UnifiedPage page) {
        page.navigate(LongFormView.class);

        page.find(UnifiedTextField.class).withLabel("City").setValue("Turku");
        page.find(UnifiedIntegerField.class).withLabel("Age").setValue(42);
        page.find(UnifiedDatePicker.class).withLabel("Birthday").setValue(LocalDate.of(1990, 5, 17));
        page.find(UnifiedCheckbox.class).withLabel("Accept terms").check();
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedParagraph.class).withId("result")).hasText("Saved 4 of 30 fields");
    }

    @Test
    @DisplayName("Selecting a row far down the large grid shows it in the editor")
    void largeGridShowsTheSelectedRow(UnifiedPage page) {
        page.navigate(LargeGridView.class);

        page.find(UnifiedGrid.class).row().withCellText("Name", "Person 250").click();

        assertThat(page.find(UnifiedTextField.class).withLabel("Name")).hasValue("Person 250");
        assertThat(page.find(UnifiedTextField.class).withLabel("Email")).hasValue("person250@example.com");
    }

    @Test
    @DisplayName("The user picks an item far down the lazy combo box")
    void comboBoxPicksALazyItem(UnifiedPage page) {
        page.navigate(LargeComboBoxView.class);

        page.find(UnifiedComboBox.class).withLabel("Product").selectItem("Item 0742");

        assertThat(page.find(UnifiedParagraph.class).withId("picked")).hasText("Picked: Item 0742");
    }
}
