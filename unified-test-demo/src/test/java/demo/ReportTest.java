package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class ReportTest {

    // --8<-- [start:findsAFieldByItsAccessibleName]
    /** A field named with setAriaLabel(..), no visible label. */
    @Test
    @DisplayName("The user picks the report year from a field without a visible label")
    void findsAFieldByItsAccessibleName(UnifiedPage page) {
        page.navigate(ReportView.class);

        page.find(UnifiedComboBox.class).withAccessibleName("Year").selectItem("2026");

        assertThat(page.findComponent(Span.class).withId("selected-year")).hasText("2026");
    }
    // --8<-- [end:findsAFieldByItsAccessibleName]

    /** Closed dialogs (the view's own, the dev tools') never match: the lookup finds the open one. */
    @Test
    @DisplayName("The user opens the export dialog and closes it")
    void findsOnlyTheOpenDialog(UnifiedPage page) {
        page.navigate(ReportView.class);
        page.find(UnifiedComboBox.class).withAccessibleName("Year").selectItem("2025");
        page.find(UnifiedButton.class).withText("Export").click();

        UnifiedDialog dialog = page.find(UnifiedDialog.class);

        assertThat(dialog).isOpen().hasHeaderTitle("Export report");
        dialog.find(UnifiedButton.class).withText("Close").click();
        assertThat(dialog).isClosed();
    }

    /** A tooltip ({@code setTooltipText}) sits in the button's DOM, yet is not its text. */
    @Test
    @DisplayName("The Export and Print buttons show their own text, not their tooltips")
    void aTooltipIsNotPartOfTheText(UnifiedPage page) {
        page.navigate(ReportView.class);

        UnifiedButton export = page.find(UnifiedButton.class).withText("Export");
        UnifiedButton print = page.find(UnifiedButton.class).withAccessibleName("Print");

        assertThat(export).hasText("Export");
        assertThat(print).hasText("");
    }

    /** Without an aria-label, the accessible name falls back to the text: the tooltip is not part of it. */
    @Test
    @DisplayName("The Export button is named by its own text, not its tooltip")
    void aTooltipIsNotPartOfTheAccessibleName(UnifiedPage page) {
        page.navigate(ReportView.class);

        UnifiedButton export = page.find(UnifiedButton.class).withAccessibleName("Export");

        assertThat(export).hasText("Export");
    }
}
