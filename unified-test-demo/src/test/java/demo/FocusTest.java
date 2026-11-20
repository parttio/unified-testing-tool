package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextArea;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Focus lands on the input inside a component whose host can't take focus (#450). Checked by typing, not by focus
 * events: Chrome under WebDriver fires none while its window isn't focused.
 */
@UnifiedSpringBootTest
@RequiresBrowser
class FocusTest {

    @Test
    @DisplayName("Keys typed after focusing a text area found among a container's descendants land in it")
    void focusesADescendantField(UnifiedPage page) {
        page.navigate(FocusView.class);

        page.find(UnifiedContainer.class).withTestId("notes-box").element()
                .findDescendants("vaadin-text-area").getFirst().focus();
        page.pressKey("a");

        assertThat(page.find(UnifiedTextArea.class).withLabel("Notes")).hasValue("a");
    }

    @Test
    @DisplayName("Keys typed after focusing a message input land in its text area")
    void focusesAMessageInput(UnifiedPage page) {
        page.navigate(FocusView.class);

        page.find(UnifiedMessageInput.class).element().focus();
        page.pressKey("a");
        page.pressKey("Enter");

        assertThat(page.find(UnifiedNotification.class).withText("Sent a")).isOpen();
    }
}
