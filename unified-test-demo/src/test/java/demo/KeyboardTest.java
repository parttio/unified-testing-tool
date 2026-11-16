package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Keyboard, DOM events and reload; written once, runs on Browserless (simulated) and Playwright. */
@UnifiedSpringBootTest
class KeyboardTest {

    @Test
    @DisplayName("Pressing Enter in the Search field starts a search")
    void enterInAFieldTriggersItsKeyListener(UnifiedPage page) {
        page.navigate(KeyboardView.class);
        UnifiedTextField search = page.find(UnifiedTextField.class).withLabel("Search");
        search.setValue("vaadin");

        search.element().pressKey("Enter");

        assertThat(page.find(UnifiedNotification.class).withText("Searching vaadin")).isOpen();
    }

    @Test
    @DisplayName("Pressing Enter in the Comment field sends the comment")
    void enterInAFormClicksItsShortcutButton(UnifiedPage page) {
        page.navigate(KeyboardView.class);
        UnifiedTextField comment = page.find(UnifiedTextField.class).withLabel("Comment");
        comment.setValue("Hello");

        comment.element().pressKey("Enter");

        assertThat(page.find(UnifiedNotification.class).withText("Sent Hello")).isOpen();
    }

    @Test
    @DisplayName("Pressing Ctrl+S saves")
    void shortcutWithModifier(UnifiedPage page) {
        page.navigate(KeyboardView.class);

        page.find(UnifiedTextField.class).withLabel("Search").element().pressKey("Control+s");

        assertThat(page.find(UnifiedNotification.class).withText("Saved with Ctrl+S")).isOpen();
    }

    // --8<-- [start:escapeOnThePageClosesTheDialog]
    @Test
    @DisplayName("Pressing Escape closes the open dialog")
    void escapeOnThePageClosesTheDialog(UnifiedPage page) {
        page.navigate(KeyboardView.class);
        page.find(UnifiedButton.class).withText("Open dialog").click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Escape me");
        assertThat(dialog).isOpen();

        page.pressKey("Escape");

        assertThat(dialog).isClosed();
    }
    // --8<-- [end:escapeOnThePageClosesTheDialog]

    @Test
    @DisplayName("Dropping an item on the drop zone is acknowledged")
    void dispatchesACustomDomEvent(UnifiedPage page) {
        page.navigate(KeyboardView.class);

        page.find(UnifiedContainer.class).withTestId("drop-zone").element().dispatchEvent("item-dropped");

        assertThat(page.find(UnifiedNotification.class).withText("Item dropped")).isOpen();
    }

    @Test
    @DisplayName("Reloading the page loses an unsaved draft")
    void reloadCreatesTheViewAgain(UnifiedPage page) {
        page.navigate(DraftView.class);
        page.find(UnifiedTextField.class).withLabel("Draft").setValue("unsaved");

        page.reload();

        assertThat(page).isAt(DraftView.class);
        assertThat(page.find(UnifiedTextField.class).withLabel("Draft")).isEmpty();
    }

    @Test
    @DisplayName("Reloading the page keeps the draft of a preserved view")
    void reloadKeepsAPreservedView(UnifiedPage page) {
        page.navigate(PreservedDraftView.class);
        page.find(UnifiedTextField.class).withLabel("Draft").setValue("kept");

        page.reload();

        assertThat(page).isAt(PreservedDraftView.class);
        assertThat(page.find(UnifiedTextField.class).withLabel("Draft")).hasValue("kept");
    }
}
