package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class RenamePersonTest {

    @Test
    @DisplayName("Renaming Alan Turing in the editor updates the grid")
    void renamesAPerson(UnifiedPage page) {
        // given
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);

        // when
        renamePerson(page, "Alan Turing", "Alan M. Turing");

        // then
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(people.row().withCellText("Name", "Alan M. Turing")).hasCellText("Email", "alan@example.com");
    }

    private static void renamePerson(UnifiedPage page, String name, String newName) {
        page.step("Rename " + name, () -> {
            page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
            page.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
            page.find(UnifiedButton.class).withText("Save").click();
        });
    }
}
