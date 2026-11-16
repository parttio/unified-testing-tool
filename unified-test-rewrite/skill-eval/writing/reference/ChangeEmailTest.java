package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class ChangeEmailTest {

    @Test
    @DisplayName("Changing the email in the dialog updates the profile")
    void changesTheEmailInTheDialog(UnifiedPage page) {
        // given
        page.navigate(ProfileView.class);
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Change email");

        // when
        page.step("Change the email to new@example.com", () -> {
            page.find(UnifiedButton.class).withText("Change email").click();
            dialog.find(UnifiedTextField.class).withLabel("Email").setValue("new@example.com");
            dialog.find(UnifiedButton.class).withText("Save").click();
        });

        // then
        assertThat(dialog).isClosed();
        assertThat(page.find(UnifiedNotification.class)).hasText("Email changed to new@example.com");
        assertThat(page.find(UnifiedTextField.class).withLabel("Email")).hasValue("new@example.com");
    }
}
