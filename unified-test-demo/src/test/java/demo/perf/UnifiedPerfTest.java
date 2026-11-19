package demo.perf;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import demo.ProfileView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The Unified side of every Performance Pair: runs on each Testing Tool (see package-info). */
@Tag("performance")
@UnifiedSpringBootTest
class UnifiedPerfTest {

    @Test
    @DisplayName("The user fills the profile and saves it")
    void smallForm(UnifiedPage page) {
        page.navigate(ProfileView.class);

        page.find(UnifiedTextField.class).withLabel("Name").setValue(PerfData.NAME);
        page.find(UnifiedTextField.class).withLabel("Email").setValue(PerfData.EMAIL);
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText(PerfData.SAVED);
    }

    @Test
    @DisplayName("The user fills all 30 fields of a long form and saves it")
    void longForm(UnifiedPage page) {
        page.navigate(LongFormView.class);

        for (String label : LongFormView.TEXT_FIELDS) {
            page.find(UnifiedTextField.class).withLabel(label).setValue(PerfData.text(label));
        }
        for (String label : LongFormView.INTEGER_FIELDS) {
            page.find(UnifiedIntegerField.class).withLabel(label).setValue(PerfData.INTEGER);
        }
        for (String label : LongFormView.DATE_FIELDS) {
            page.find(UnifiedDatePicker.class).withLabel(label).setValue(PerfData.DATE);
        }
        for (String label : LongFormView.CHECKBOXES) {
            page.find(UnifiedCheckbox.class).withLabel(label).check();
        }
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedParagraph.class).withId("result")).hasText(PerfData.LONG_FORM_SAVED);
    }

    @Test
    @DisplayName("The user selects a row far down a 500-row grid")
    void largeGrid(UnifiedPage page) {
        page.navigate(LargeGridView.class);

        page.find(UnifiedGrid.class).row().withCellText("Name", PerfData.GRID_NAME).click();

        assertThat(page.find(UnifiedTextField.class).withLabel("Name")).hasValue(PerfData.GRID_NAME);
        assertThat(page.find(UnifiedTextField.class).withLabel("Email")).hasValue(PerfData.GRID_EMAIL);
    }

    @Test
    @DisplayName("The user picks an item far down a 1,000-item combo box")
    void comboBox(UnifiedPage page) {
        page.navigate(LargeComboBoxView.class);

        page.find(UnifiedComboBox.class).withLabel("Product").selectItem(PerfData.ITEM);

        assertThat(page.find(UnifiedParagraph.class).withId("picked")).hasText(PerfData.PICKED);
    }
}
