package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Texts an application builds from more than plain text read the same on every Testing Tool (#690). */
@UnifiedSpringBootTest
class NoticesTest {

    @Test
    @DisplayName("A notification built from components reads the text of its components")
    void notificationFromComponentsReadsItsComponentsText(UnifiedPage page) {
        page.navigate(NoticesView.class);

        page.find(UnifiedButton.class).withText("Save").click();

        // the components' text as a browser's textContent joins it: no space between a span and a button
        assertThat(page.find(UnifiedNotification.class).withTextContaining("Saved")).hasText("SavedUndo");
    }
}
