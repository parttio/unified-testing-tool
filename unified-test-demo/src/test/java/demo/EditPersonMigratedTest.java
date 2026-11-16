package demo;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/** TestBench (Selenium), without the Unified API. Shown in docs/comparison.md. */
// --8<-- [start:test]
@UnifiedSpringBootTest
class EditPersonMigratedTest {

    @Test
    void editsAPersonFromTheGrid(UnifiedPage page) {
        page.navigate(PeopleView.class);
        // TODO unified: was waitForFirst(); the lookup fails when several components match
        UnifiedGrid people = page.find(UnifiedGrid.class);

        // TODO unified: was getCell("Alan Turing"); the row lookup fails when several rows match
        UnifiedGrid.UnifiedGridRow alan = people.row().withCellText("Alan Turing");
        int row = alan.getIndex();
        alan.click();
        page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
        page.find(UnifiedButton.class).withText("Save").click();

        // TODO unified: was waitForFirst(); the lookup fails when several components match
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        Assertions.assertEquals("Alan M. Turing", people.row(row).getCellText(0));
    }
}
// --8<-- [end:test]
