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
class DialogTest {

    // --8<-- [start:helpers]
    private UnifiedDialog openDialog(UnifiedPage page) {
        page.navigate(ProfileView.class);
        page.find(UnifiedButton.class).withText("Change email").click();
        return page.find(UnifiedDialog.class).withHeaderTitle("Change email");
    }
    // --8<-- [end:helpers]

    @Test
    @DisplayName("Clicking Change email opens a dialog titled Change email")
    void opensWithHeaderTitle(UnifiedPage page) {
        UnifiedDialog dialog = openDialog(page);

        assertThat(dialog).isOpen().isVisible().hasHeaderTitle("Change email");
    }

    // --8<-- [start:searchesInsideTheDialog]
    @Test
    @DisplayName("The user changes the email in the dialog, and the profile shows the new one")
    void searchesInsideTheDialog(UnifiedPage page) {
        UnifiedDialog dialog = openDialog(page);

        // the page has its own "Email" field; scoping picks the dialog's one
        dialog.find(UnifiedTextField.class).withLabel("Email").setValue("new@example.com");
        dialog.find(UnifiedButton.class).withText("Save").click();

        assertThat(dialog).isClosed();
        assertThat(page.find(UnifiedNotification.class)).hasText("Email changed to new@example.com");
        assertThat(page.find(UnifiedTextField.class).withLabel("Email")).hasValue("new@example.com");
    }
    // --8<-- [end:searchesInsideTheDialog]

    @Test
    @DisplayName("Cancel closes the Change email dialog")
    void closesWithCancel(UnifiedPage page) {
        UnifiedDialog dialog = openDialog(page);

        dialog.find(UnifiedButton.class).withText("Cancel").click();

        assertThat(dialog).isClosed();
    }

    @Test
    @DisplayName("Pressing Escape closes the Change email dialog")
    void closesWithEscape(UnifiedPage page) {
        UnifiedDialog dialog = openDialog(page);

        dialog.close();

        assertThat(dialog).isClosed();
    }

    @Test
    @DisplayName("A search inside the dialog finds what is in it, not the dialog itself")
    void aSearchInsideTheDialogLeavesTheDialogOut(UnifiedPage page) {
        UnifiedDialog dialog = openDialog(page);

        assertThat(dialog.find(UnifiedDialog.class)).doesNotExist();
    }
}
