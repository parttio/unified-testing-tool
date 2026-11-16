package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class ReportTest {

    // --8<-- [start:findsAFieldByItsAccessibleName]
    /** A field named with setAriaLabel(..), no visible label. */
    @Test
    void findsAFieldByItsAccessibleName(UnifiedPage page) {
        page.navigate(ReportView.class);

        page.find(UnifiedComboBox.class).withAccessibleName("Year").selectItem("2026");

        assertThat(page.findComponent(Span.class).withId("selected-year")).hasText("2026");
    }
    // --8<-- [end:findsAFieldByItsAccessibleName]

    /** Closed dialogs (the view's own, the dev tools') never match: the lookup finds the open one. */
    @Test
    void findsOnlyTheOpenDialog(UnifiedPage page) {
        page.navigate(ReportView.class);
        page.find(UnifiedComboBox.class).withAccessibleName("Year").selectItem("2025");
        page.find(UnifiedButton.class).withText("Export").click();

        UnifiedDialog dialog = page.find(UnifiedDialog.class);

        assertThat(dialog).isOpen().hasHeaderTitle("Export report");
        dialog.find(UnifiedButton.class).withText("Close").click();
        assertThat(dialog).isClosed();
    }
}
