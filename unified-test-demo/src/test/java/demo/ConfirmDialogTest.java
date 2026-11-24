package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An overlay host counts as visible while its overlay is open, on every Testing Tool (#556). */
@UnifiedSpringBootTest
class ConfirmDialogTest {

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
