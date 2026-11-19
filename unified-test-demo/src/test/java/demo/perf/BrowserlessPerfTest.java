package demo.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.browserless.BrowserlessApplicationContext;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.SpringBrowserlessApplicationContext;
import com.vaadin.flow.component.grid.GridLocator;
import demo.ProfileView;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/** The Browserless Test side of every Performance Pair (see package-info). */
@Tag("performance")
@SpringBootTest
@DisabledIfSystemProperty(named = "vaadin.testing.tool", matches = "playwright|selenium")
class BrowserlessPerfTest {

    @TestConfiguration
    static class BrowserlessConfiguration {
        @Bean
        BrowserlessApplicationContext browserless(ApplicationContext spring) {
            // the app uses Spring Security: users of a secured context get their own credentials
            return SpringBrowserlessApplicationContext.createSecured(spring, ProfileView.class, LongFormView.class);
        }
    }

    @Autowired
    BrowserlessApplicationContext app;

    BrowserlessUIContext window;

    @BeforeEach
    void openWindow() {
        window = app.newUser().newWindow();
    }

    @AfterEach
    void closeUser() {
        window.getUser().close();
    }

    @Test
    void smallForm() {
        window.navigate(ProfileView.class);

        window.findTextField().withLabel("Name").setValue(PerfData.NAME);
        window.findTextField().withLabel("Email").setValue(PerfData.EMAIL);
        window.findButton().withText("Save").click();

        assertEquals(PerfData.SAVED, window.findNotification().getText());
    }

    @Test
    void longForm() {
        window.navigate(LongFormView.class);

        for (String label : LongFormView.TEXT_FIELDS) {
            window.findTextField().withLabel(label).setValue(PerfData.text(label));
        }
        for (String label : LongFormView.INTEGER_FIELDS) {
            window.findIntegerField().withLabel(label).setValue(PerfData.INTEGER);
        }
        for (String label : LongFormView.DATE_FIELDS) {
            window.findDatePicker().withLabel(label).setValue(PerfData.DATE);
        }
        for (String label : LongFormView.CHECKBOXES) {
            window.findCheckbox().withLabel(label).check();
        }
        window.findButton().withText("Save").click();

        assertEquals(PerfData.LONG_FORM_SAVED, window.findParagraph().withId("result").component().getText());
    }

    @Test
    void largeGrid() {
        window.navigate(LargeGridView.class);
        GridLocator<LargeGridView.Person> grid = window.findGrid(LargeGridView.Person.class);

        int row = IntStream.range(0, grid.size())
                .filter(i -> grid.getCellText(i, 0).equals(PerfData.GRID_NAME))
                .findFirst().orElseThrow();
        grid.clickRow(row);

        assertEquals(PerfData.GRID_NAME, window.findTextField().withLabel("Name").component().getValue());
        assertEquals(PerfData.GRID_EMAIL, window.findTextField().withLabel("Email").component().getValue());
    }

    @Test
    void comboBox() {
        window.navigate(LargeComboBoxView.class);

        window.findComboBox(String.class).withLabel("Product").selectItem(PerfData.ITEM);

        assertEquals(PerfData.PICKED, window.findParagraph().withId("picked").component().getText());
    }
}
