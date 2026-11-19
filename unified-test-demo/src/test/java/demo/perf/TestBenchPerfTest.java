package demo.perf;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.checkbox.testbench.CheckboxElement;
import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.datepicker.testbench.DatePickerElement;
import com.vaadin.flow.component.grid.testbench.GridElement;
import com.vaadin.flow.component.grid.testbench.GridTHTDElement;
import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.flow.component.textfield.testbench.IntegerFieldElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testbench.BrowserTest;
import com.vaadin.testbench.BrowserTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.NoSuchElementException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The TestBench (Selenium) side of every Performance Pair (see package-info). */
@Tag("performance")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "vaadin.testing.tool", matches = "selenium")
class TestBenchPerfTest extends BrowserTestBase {

    @LocalServerPort
    int port;

    private void open(String path) {
        getDriver().get("http://localhost:" + port + "/" + path);
    }

    @BrowserTest
    void smallForm() {
        open("profile");

        $(TextFieldElement.class).withLabel("Name").single().setValue(PerfData.NAME);
        $(TextFieldElement.class).withLabel("Email").single().setValue(PerfData.EMAIL);
        $(ButtonElement.class).withText("Save").single().click();

        Assertions.assertEquals(PerfData.SAVED, $(NotificationElement.class).waitForFirst().getText());
    }

    @BrowserTest
    void longForm() {
        open("perf/long-form");

        for (String label : LongFormView.TEXT_FIELDS) {
            $(TextFieldElement.class).withLabel(label).single().setValue(PerfData.text(label));
        }
        for (String label : LongFormView.INTEGER_FIELDS) {
            $(IntegerFieldElement.class).withLabel(label).single().setValue(String.valueOf(PerfData.INTEGER));
        }
        for (String label : LongFormView.DATE_FIELDS) {
            $(DatePickerElement.class).withLabel(label).single().setDate(PerfData.DATE);
        }
        for (String label : LongFormView.CHECKBOXES) {
            $(CheckboxElement.class).withLabel(label).single().setChecked(true);
        }
        $(ButtonElement.class).withText("Save").single().click();

        Assertions.assertEquals(PerfData.LONG_FORM_SAVED, $("p").id("result").getText());
    }

    @BrowserTest
    void largeGrid() {
        open("perf/large-grid");
        GridElement grid = $(GridElement.class).waitForFirst();

        // getCell(text) looks only at the rendered rows: scroll a page at a time until the row shows
        GridTHTDElement cell = null;
        for (int first = 0; cell == null && first < grid.getRowCount(); first += grid.getPageSize()) {
            grid.scrollToRow(first);
            try {
                cell = grid.getCell(PerfData.GRID_NAME);
            } catch (NoSuchElementException notRendered) {
                // not on this page
            }
        }
        if (cell == null) {
            throw new NoSuchElementException("No row with " + PerfData.GRID_NAME);
        }
        cell.click();

        Assertions.assertEquals(PerfData.GRID_NAME, $(TextFieldElement.class).withLabel("Name").single().getValue());
        Assertions.assertEquals(PerfData.GRID_EMAIL, $(TextFieldElement.class).withLabel("Email").single().getValue());
    }

    @BrowserTest
    void comboBox() {
        open("perf/combo-box");

        $(ComboBoxElement.class).withLabel("Product").single().selectByText(PerfData.ITEM);

        Assertions.assertEquals(PerfData.PICKED, $("p").id("picked").getText());
    }
}
