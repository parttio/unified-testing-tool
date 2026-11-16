package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/** Written once; runs on Browserless (default) and on Playwright (-Dvaadin.testing.tool=playwright). */
@UnifiedSpringBootTest
class ProfileViewTest {

    // --8<-- [start:savesName]
    @Test
    void savesName(UnifiedPage page) {
        page.navigate(ProfileView.class);

        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
        name.setValue("Alice");
        page.find(UnifiedTextField.class).withLabel("Email").setValue("alice@example.com");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).isOpen().hasText("Saved Alice");
        assertThat(name).hasValue("Alice").isValid();
    }
    // --8<-- [end:savesName]

    @Test
    void requiresEmail(UnifiedPage page) {
        page.navigate(ProfileView.class);

        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedTextField.class).withLabel("Email"))
                .isInvalid()
                .hasErrorMessage("Email is required");
    }

    // --8<-- [start:findsIconButtonByAccessibleName]
    @Test
    void findsIconButtonByAccessibleName(UnifiedPage page) {
        page.navigate(ProfileView.class);

        page.find(UnifiedButton.class).withAccessibleName("Delete account").click();

        assertThat(page.find(UnifiedNotification.class).withText("Account deleted")).isOpen();
    }
    // --8<-- [end:findsIconButtonByAccessibleName]

    @Test
    void findsByTestIdAndPlaceholder(UnifiedPage page) {
        page.navigate(ProfileView.class);

        page.find(UnifiedTextField.class).withTestId("nickname").setValue("Ali");

        assertThat(page.find(UnifiedTextField.class).withPlaceholder("Nickname")).hasValue("Ali");
    }

    @Test
    void knowsDisabledButtons(UnifiedPage page) {
        page.navigate(ProfileView.class);

        assertThat(page.find(UnifiedButton.class).withText("Archive")).isDisabled();
        assertThat(page.find(UnifiedButton.class).withText("Save")).isEnabled().hasText("Save");
    }

    // --8<-- [start:escapeHatchIsOptional]
    @Test
    void escapeHatchIsOptional(UnifiedPage page) {
        page.navigate(ProfileView.class);
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");

        // the Flow component exists only on Browserless; the test still runs everywhere
        name.tryUnwrap(TextField.class).ifPresent(field -> assertEquals("Name", field.getLabel()));
        assertThat(page.find(UnifiedButton.class).withText("Save")).exists();
        assertThat(page.find(UnifiedButton.class).withText("No such button")).doesNotExist();
    }
    // --8<-- [end:escapeHatchIsOptional]

    @Test
    void unwrapNamesTheMissingAnnotation(UnifiedPage page) {
        page.navigate(ProfileView.class);
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");

        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> name.unwrap(java.io.File.class));
        assertTrue(e.getMessage().contains("@RequiresTestingTool"), e.getMessage());
    }

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    void escapeHatchToTestBench(UnifiedPage page) {
        page.navigate(ProfileView.class);
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");

        TextFieldElement testBench = name.unwrap(TextFieldElement.class);
        testBench.setValue("Carol");

        assertEquals("Name", testBench.getLabel());
        assertThat(name).hasValue("Carol");
        assertTrue(page.unwrap(WebDriver.class).getCurrentUrl().endsWith("/profile"));
    }

    // --8<-- [start:readsServerSideState]
    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    void readsServerSideState(UnifiedPage page) {
        page.navigate(ProfileView.class);
        page.find(UnifiedTextField.class).withLabel("Name").setValue("Bob");

        TextField field = page.find(UnifiedTextField.class).withLabel("Name").unwrap(TextField.class);
        assertEquals("Bob", field.getValue());
    }
    // --8<-- [end:readsServerSideState]

    @Test
    @RequiresBrowser
    void needsARealBrowser(UnifiedPage page) {
        page.navigate(ProfileView.class);
        assertThat(page.find(UnifiedButton.class).withText("Save")).hasCssValue("cursor", "pointer");
    }

    // --8<-- [start:screenshotOnlyWithARealBrowser]
    @Test
    void screenshotOnlyWithARealBrowser(UnifiedPage page) throws Exception {
        page.navigate(ProfileView.class);
        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alice");

        Path file = Path.of("target/screenshots/profile-alice.png");
        Files.deleteIfExists(file);
        if (page.hasRealBrowser()) {
            page.screenshot(file);
        }

        assertEquals(page.hasRealBrowser(), Files.isRegularFile(file));
    }
    // --8<-- [end:screenshotOnlyWithARealBrowser]
}
