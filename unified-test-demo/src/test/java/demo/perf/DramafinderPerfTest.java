package demo.perf;

import com.microsoft.playwright.Page;
import demo.ProfileView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.vaadin.addons.dramafinder.element.ButtonElement;
import org.vaadin.addons.dramafinder.element.CheckboxElement;
import org.vaadin.addons.dramafinder.element.ComboBoxElement;
import org.vaadin.addons.dramafinder.element.DatePickerElement;
import org.vaadin.addons.dramafinder.element.GridElement;
import org.vaadin.addons.dramafinder.element.IntegerFieldElement;
import org.vaadin.addons.dramafinder.element.NotificationElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;
import org.vaadin.addons.dramafinder.spring.SpringPlaywrightIT;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** The Dramafinder (Playwright) side of every Performance Pair (see package-info). */
@Tag("performance")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "vaadin.testing.tool", matches = "playwright")
class DramafinderPerfTest extends SpringPlaywrightIT {

    /**
     * SpringPlaywrightIT's setup, then a new page in a browser context of its own: the browser starts as on the
     * Unified side (fairness rule 4, #444). The base class loads the application in its page before the test,
     * so the body's {@code navigate} would be the context's second load, about 300 ms faster than the first,
     * which the Unified UI Test's {@code navigate} makes in a new context.
     */
    @Override
    @BeforeEach
    public void setupTest() throws Exception {
        super.setupTest();
        Page fresh = getBrowser().newPage();
        prepareBrowserContext(fresh.context());
        // the base class's timeouts
        fresh.setDefaultNavigationTimeout(4000);
        fresh.setDefaultTimeout(15000);
        page.close();
        page = fresh;
    }

    @Test
    void smallForm() {
        navigate(ProfileView.class);

        TextFieldElement.getByLabel(page, "Name").setValue(PerfData.NAME);
        TextFieldElement.getByLabel(page, "Email").setValue(PerfData.EMAIL);
        ButtonElement.getByText(page, "Save").click();

        NotificationElement.getByText(page, PerfData.SAVED).assertOpen();
    }

    @Test
    void longForm() {
        navigate(LongFormView.class);

        for (String label : LongFormView.TEXT_FIELDS) {
            TextFieldElement.getByLabel(page, label).setValue(PerfData.text(label));
        }
        for (String label : LongFormView.INTEGER_FIELDS) {
            IntegerFieldElement.getByLabel(page, label).setValue(PerfData.INTEGER);
        }
        for (String label : LongFormView.DATE_FIELDS) {
            DatePickerElement.getByLabel(page, label).setValue(PerfData.DATE);
        }
        for (String label : LongFormView.CHECKBOXES) {
            CheckboxElement.getByLabel(page, label).check();
        }
        ButtonElement.getByText(page, "Save").click();

        assertThat(page.locator("#result")).hasText(PerfData.LONG_FORM_SAVED);
    }

    @Test
    void largeGrid() {
        navigate(LargeGridView.class);
        GridElement grid = GridElement.get(page);

        int row = grid.findRowIndexesWithColumnText(0, PerfData.GRID_NAME).get(0);
        grid.findRow(row).orElseThrow().getCell("Name").click();

        TextFieldElement.getByLabel(page, "Name").assertValue(PerfData.GRID_NAME);
        TextFieldElement.getByLabel(page, "Email").assertValue(PerfData.GRID_EMAIL);
    }

    @Test
    void comboBox() {
        navigate(LargeComboBoxView.class);

        ComboBoxElement.getByLabel(page, "Product").selectItem(PerfData.ITEM);

        assertThat(page.locator("#picked")).hasText(PerfData.PICKED);
    }
}
