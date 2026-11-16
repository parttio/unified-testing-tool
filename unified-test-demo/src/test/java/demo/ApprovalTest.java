package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

/** Hidden components: queries include them on every Testing Tool; visible() leaves them out. */
@UnifiedSpringBootTest
class ApprovalTest {

    // --8<-- [start:aHiddenButtonIsStillFoundButNotVisible]
    @Test
    void aHiddenButtonIsStillFoundButNotVisible(UnifiedPage page) {
        page.navigate(ApprovalView.class);
        UnifiedButton approve = page.find(UnifiedButton.class).withText("Approve");

        approve.click();

        assertThat(page.findComponent(Span.class).withId("approval-status")).hasText("Approved");
        assertThat(approve).isHidden();
        assertThat(page.find(UnifiedButton.class).withText("Approve")).exists();
        assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist();
    }
    // --8<-- [end:aHiddenButtonIsStillFoundButNotVisible]
}
