package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Focus lands on the input inside a component whose host can't take focus (#450). No DOM focus on Browserless. */
@UnifiedSpringBootTest
@RequiresBrowser
class FocusTest {

    @Test
    @DisplayName("Focusing a text area found among a container's descendants focuses its input")
    void focusesADescendantField(UnifiedPage page) {
        page.navigate(FocusView.class);

        page.find(UnifiedContainer.class).withTestId("notes-box").element()
                .findDescendants("vaadin-text-area").getFirst().focus();

        assertThat(page.find(UnifiedNotification.class).withText("Notes focused")).isOpen();
    }

    @Test
    @DisplayName("Focusing a message input focuses its text area")
    void focusesAMessageInput(UnifiedPage page) {
        page.navigate(FocusView.class);

        page.find(UnifiedMessageInput.class).element().focus();

        assertThat(page.find(UnifiedNotification.class).withText("Message input focused")).isOpen();
    }
}
