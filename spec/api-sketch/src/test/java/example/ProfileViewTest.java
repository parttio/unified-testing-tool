package example;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.html.Div;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedSpringBootTest;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The spec's reference example. Compiles against the sketch; never run. */
@UnifiedSpringBootTest
class ProfileViewTest {

    static class ProfileView extends Div {}   // stands in for an application @Route view

    @Test
    @DisplayName("The user changes their name and saves")
    void savesName(UnifiedPage page) {
        // given
        page.navigate(ProfileView.class);
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");

        // when
        name.setValue("Alice");
        page.find(UnifiedButton.class).withText("Save").click();

        // then
        assertThat(page.find(UnifiedNotification.class)).isOpen().hasText("Saved Alice");
        assertThat(name).hasValue("Alice").isValid();
    }

    @Test
    @DisplayName("The user deletes their account after confirming")
    void deletesAccount(UnifiedPage page) {
        page.navigate(ProfileView.class);

        deleteAccount(page);

        assertThat(page.find(UnifiedDialog.class).withHeaderTitle("Delete account?")).doesNotExist();
        assertThat(page).hasPath("goodbye");
    }

    private static void deleteAccount(UnifiedPage page) {
        page.step("Delete the account", () -> {
            page.find(UnifiedButton.class).withAccessibleName("Delete account").click();
            UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Delete account?");
            dialog.find(UnifiedButton.class).withText("Delete").click();
        });
    }

    @Test
    @DisplayName("The user enters a city in the address form")
    void layoutScope(UnifiedPage page) {
        page.navigate("profile?tab=address");
        UnifiedContainer address = page.find(UnifiedContainer.class).withTestId("address-form");
        address.find(UnifiedTextField.class).withLabel("City").setValue("Turku");
    }

    @Test
    @DisplayName("The save button looks clickable")
    @RequiresBrowser
    void looksRight(UnifiedPage page) {
        page.navigate(ProfileView.class);
        assertThat(page.find(UnifiedButton.class).withText("Save")).hasCssValue("cursor", "pointer");
        page.screenshot(Path.of("target/profile.png"));
    }
}
