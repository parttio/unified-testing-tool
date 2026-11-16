package demo;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/** Dramafinder 1.x, without the Unified API: the golden source of the 1.x migration. */
@UnifiedSpringBootTest
class EditPersonDramafinder1MigratedTest {

    @Test
    void editsAPersonFromTheGrid(UnifiedPage page) {
        page.navigate("people");
        UnifiedGrid people = page.find(UnifiedGrid.class);

        // TODO unified: was findRowIndexesWithColumnText(0, ..).get(0): any column matches now; the row lookup fails when several rows match
        UnifiedGrid.UnifiedGridRow alan = people.row(people.row().withCellText("Alan Turing").getIndex());
        // TODO unified: was getCell("Name").click(); a row's click() clicks the row, not that cell
        alan.click();
        // TODO unified: was 1.x getByLabel(..), a case-insensitive substring and first match; tighten it to withLabel(..) once the test passes
        page.find(UnifiedTextField.class).withAccessibleNameContaining("Name").setValue("Alan M. Turing");
        // TODO unified: was 1.x getByText(..), a case-insensitive substring and first match; tighten it to withText(..) once the test passes
        page.find(UnifiedButton.class).withTextContaining("Save").click();

        // TODO unified: was getByText(..), a substring and first match; the lookup fails when several components match
        assertThat(page.find(UnifiedNotification.class).withTextContaining("Saved Alan M. Turing")).isOpen();
        assertThat(alan).hasCellText("Name", "Alan M. Turing");
    }
}
