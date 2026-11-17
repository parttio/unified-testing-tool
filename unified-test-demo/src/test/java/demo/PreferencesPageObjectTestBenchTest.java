package demo;

import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.testbench.BrowserTest;
import com.vaadin.testbench.BrowserTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Golden migration of a page object with no converted twin; its twin is an input of the skill evaluation (#134). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "vaadin.testing.tool", matches = "selenium")
class PreferencesPageObjectTestBenchTest extends BrowserTestBase {

    @LocalServerPort
    int port;

    @BrowserTest
    void savesThePreferences() {
        getDriver().get("http://localhost:" + port + "/preferences");

        PreferencesTestBenchPage preferences = new PreferencesTestBenchPage(this);
        preferences.height(180);
        preferences.subscribe();
        preferences.save();

        Assertions.assertEquals("Saved: height 180.0, birthday null, newsletter yes, remember yes",
                $(NotificationElement.class).waitForFirst().getText());
    }
}
