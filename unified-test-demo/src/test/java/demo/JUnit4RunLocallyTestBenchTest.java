package demo;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.testbench.TestBenchTestCase;
import com.vaadin.testbench.annotations.RunLocally;
import com.vaadin.testbench.parallel.Browser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Golden migration of a JUnit 4 TestBench test that picks its browser with {@code @RunLocally}: the recipe removes
 * it and leaves a "browser setup removed" Migration Marker (#134). Compile-only here, as the demo runs no JUnit 4
 * tests.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunLocally(Browser.FIREFOX)
public class JUnit4RunLocallyTestBenchTest extends TestBenchTestCase {

    @LocalServerPort
    int port;

    @Before
    public void openProfile() {
        setDriver(new ChromeDriver());
        getDriver().get("http://localhost:" + port + "/profile");
    }

    @Test
    public void savesTheEmail() {
        $(TextFieldElement.class).withLabel("Name").single().setValue("Carol");
        $(TextFieldElement.class).withLabel("Email").single().setValue("carol@example.com");
        $(ButtonElement.class).withText("Save").single().click();

        Assert.assertEquals("Saved Carol", $(NotificationElement.class).waitForFirst().getText());
    }

    @After
    public void tearDown() {
        getDriver().quit();
    }
}
