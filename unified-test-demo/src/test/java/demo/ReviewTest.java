package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

/** Buttons replaced on every click: finding one by text right after a click must not hit a stale element. */
@UnifiedSpringBootTest
class ReviewTest {

    @Test
    void findsReplacedButtonsRightAfterAClick(UnifiedPage page) {
        page.navigate(ReviewView.class);

        for (int i = 0; i < 5; i++) {
            page.find(UnifiedButton.class).withText("Submit").click();
            page.find(UnifiedButton.class).withText("Reject").click();
        }
        page.find(UnifiedButton.class).withText("Submit").click();
        page.find(UnifiedButton.class).withText("Approve").click();

        assertThat(page.findComponent(com.vaadin.flow.component.html.Span.class).withId("status"))
                .hasText("Approved");
    }

    /** An exception in a listener shows the app's error dialog, on Browserless too (route-errors-to-session). */
    @Test
    void showsTheErrorDialogWhenAnActionFails(UnifiedPage page) {
        page.navigate(ReviewView.class);
        page.find(UnifiedButton.class).withText("Submit").click();
        page.find(UnifiedButton.class).withText("Approve").click();

        page.find(UnifiedButton.class).withText("Archive").click();

        UnifiedDialog error = page.find(UnifiedDialog.class).withHeaderTitle("Something went wrong");
        assertThat(error).isOpen().hasTextContaining("The archive is not available");
    }
}
