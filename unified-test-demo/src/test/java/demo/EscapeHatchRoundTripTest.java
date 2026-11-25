package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.vaadin.addons.dramafinder.AutoWait;

/**
 * A Unified action after an Escape Hatch action that reaches the server (docs/guide/tool-specific.md): the server's
 * answer to the click (the editor loads the person) can arrive after the next Unified action, and overwrite it. Auto-Wait
 * waits for it before the next Unified action acts (#604); the guide's waits are for code that reads the page itself.
 */
@UnifiedSpringBootTest
class EscapeHatchRoundTripTest {

    // --8<-- [start:waitForVaadinIdle]
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user opens a person with a Playwright click, renames them and saves")
    void waitsForTheServerInTheEscapeHatch(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");

        // Escape Hatch: a click through Playwright's own API, as for a component without a Wrapper
        page.unwrap(Page.class).getByText("Alan Turing").click();
        AutoWait.waitForVaadinIdle(page.unwrap(Page.class));     // the server has answered: the form holds Alan Turing
        name.setValue("Alan M. Turing");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
    }
    // --8<-- [end:waitForVaadinIdle]

    // --8<-- [start:assertTheServersValue]
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user opens a person with a Playwright click, sees their name, renames them and saves")
    void assertsTheServersValueFirst(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");

        // Escape Hatch: a click through Playwright's own API, as for a component without a Wrapper
        page.unwrap(Page.class).getByText("Alan Turing").click();
        assertThat(name).hasValue("Alan Turing");     // retries until the server's answer is in the form
        name.setValue("Alan M. Turing");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
    }
    // --8<-- [end:assertTheServersValue]

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user loads the stored name with a Playwright click, types a new one at once and saves")
    void playwrightActionWaitsForTheEscapeHatchsRoundTrip(UnifiedPage page) {
        page.navigate(SlowLoadView.class);

        page.unwrap(Page.class).locator("#load").click();     // returns before the server has answered
        page.find(UnifiedTextField.class).withLabel("Name").setValue("New name");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Saved New name");
    }

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    @DisplayName("The user loads the stored name with a WebDriver click, types a new one at once and saves")
    void seleniumActionWaitsForTheEscapeHatchsRoundTrip(UnifiedPage page) {
        page.navigate(SlowLoadView.class);

        page.unwrap(WebDriver.class).findElement(By.id("load")).click();
        page.find(UnifiedTextField.class).withLabel("Name").setValue("New name");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Saved New name");
    }
}
