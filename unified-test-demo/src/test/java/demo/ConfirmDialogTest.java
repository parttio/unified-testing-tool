package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UnifiedConfirmDialog presses a button by its role, whatever its text, and reads the dialog the same on every
 * Testing Tool (#532). An open ConfirmDialog is visible on every Testing Tool (#556).
 */
@UnifiedSpringBootTest
class ConfirmDialogTest {

    private UnifiedConfirmDialog deleteOrder(UnifiedPage page) {
        page.navigate(ConfirmDialogsView.class);
        page.find(UnifiedButton.class).withText("Delete order").click();
        return page.find(UnifiedConfirmDialog.class).withHeaderTitle("Delete order?");
    }

    private void assertAnswer(UnifiedPage page, String answer) {
        assertThat(page.findComponent(Span.class).withId("answer")).hasText(answer);
    }

    // --8<-- [start:confirms]
    @Test
    @DisplayName("Confirming the order deletion fires the confirm event and closes the dialog")
    void confirms(UnifiedPage page) {
        UnifiedConfirmDialog dialog = deleteOrder(page);

        dialog.confirm(); // the button the application named "Delete"

        assertThat(dialog).isClosed();
        assertAnswer(page, "Confirmed");
    }
    // --8<-- [end:confirms]

    @Test
    @DisplayName("Rejecting the order deletion fires the reject event and closes the dialog")
    void rejects(UnifiedPage page) {
        UnifiedConfirmDialog dialog = deleteOrder(page);

        dialog.reject();

        assertThat(dialog).isClosed();
        assertAnswer(page, "Rejected");
    }

    @Test
    @DisplayName("Cancelling the order deletion fires the cancel event and closes the dialog")
    void cancels(UnifiedPage page) {
        UnifiedConfirmDialog dialog = deleteOrder(page);

        dialog.cancel();

        assertThat(dialog).isClosed();
        assertAnswer(page, "Cancelled");
    }

    @Test
    @DisplayName("Pressing Escape on a cancelable dialog fires the cancel event and closes it")
    void escapeCancels(UnifiedPage page) {
        UnifiedConfirmDialog dialog = deleteOrder(page);

        dialog.close();

        assertThat(dialog).isClosed();
        assertAnswer(page, "Cancelled");
    }

    @Test
    @DisplayName("The dialog's header, message, state and button texts read the same on every Testing Tool")
    void readsTheDialog(UnifiedPage page) {
        UnifiedConfirmDialog dialog = deleteOrder(page);

        assertThat(dialog).isOpen().isVisible().hasHeaderTitle("Delete order?")
                .hasText("The order can't be restored.").hasTextContaining("can't be restored");
        assertEquals("Delete order?", dialog.getHeaderTitle());
        assertEquals("The order can't be restored.", dialog.getText());
        assertTrue(dialog.isOpen());
        assertEquals("Delete", dialog.getConfirmText());
        assertEquals("Archive instead", dialog.getRejectText());
        assertEquals("Keep order", dialog.getCancelText());
    }

    @Test
    @DisplayName("A dialog without a header is found by its text, and shows the default button texts")
    void aDialogWithoutAHeader(UnifiedPage page) {
        page.navigate(ConfirmDialogsView.class);
        page.find(UnifiedButton.class).withText("Leave page").click();
        UnifiedConfirmDialog dialog = page.find(UnifiedConfirmDialog.class).withTextContaining("without saving");

        assertNull(dialog.getHeaderTitle());
        assertEquals("Leave without saving?", dialog.getText());
        assertEquals("Confirm", dialog.getConfirmText());
        assertNull(dialog.getRejectText());
        assertEquals("Cancel", dialog.getCancelText());

        dialog.confirm();

        assertThat(dialog).isClosed();
        assertAnswer(page, "Confirmed");
    }

    @Test
    @DisplayName("Rejecting a dialog without a Reject button fails, listing the buttons it has")
    void rejectingWithoutARejectButtonFails(UnifiedPage page) {
        page.navigate(ConfirmDialogsView.class);
        page.find(UnifiedButton.class).withText("Leave page").click();
        UnifiedConfirmDialog dialog = page.find(UnifiedConfirmDialog.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, dialog::reject);

        assertTrue(e.getMessage().contains("no Reject button; its buttons: Confirm “Confirm”, Cancel “Cancel”"),
                e.getMessage());
        assertThat(dialog).isOpen();
        assertAnswer(page, "No answer");
    }

    @Test
    @DisplayName("Confirming presses the application's own confirm button")
    void confirmsWithTheApplicationsOwnButton(UnifiedPage page) {
        page.navigate(DiscardView.class);
        page.find(UnifiedButton.class).withText("Discard draft").click();
        UnifiedConfirmDialog dialog = page.find(UnifiedConfirmDialog.class).withHeaderTitle("Discard draft?");
        assertEquals("Discard", dialog.getConfirmText());

        dialog.confirm();

        assertThat(dialog).isClosed();
        assertThat(page.findComponent(Span.class).withId("discard-status")).hasText("Draft discarded");
    }

    @Test
    @DisplayName("An open confirm dialog is visible, and none is once it is confirmed")
    void anOpenConfirmDialogIsVisible(UnifiedPage page) {
        page.navigate(DiscardView.class);
        page.find(UnifiedButton.class).withText("Discard draft").click();

        assertThat(page.findComponent(ConfirmDialog.class).visible()).hasCount(1);
        assertThat(page.findComponent(ConfirmDialog.class)).isVisible();

        page.find(UnifiedButton.class).withText("Discard").click();

        assertThat(page.findComponent(Span.class).withId("discard-status")).hasText("Draft discarded");
        assertThat(page.findComponent(ConfirmDialog.class).visible()).doesNotExist();
    }
}
