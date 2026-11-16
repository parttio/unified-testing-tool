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

/**
 * A scenario with User Tasks: {@code page.step(..)} groups its User Steps under a heading, nested. Every run
 * writes {@code user-steps.txt} and {@code user-steps.json} for it; {@link UserStepsFilesTest} checks them.
 */
@UnifiedSpringBootTest
class UserStepsTest {

    @Test
    @DisplayName("Editing a person in nested steps updates the grid")
    void editsAPersonInNestedSteps(UnifiedPage page) {
        // given
        page.navigate(PeopleView.class);

        // when
        editPerson(page, "Alan Turing", "Alan M. Turing");

        // then
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(page.find(UnifiedGrid.class).row().withCellText("Name", "Alan M. Turing"))
                .hasCellText("Email", "alan@example.com");
    }

    private static void editPerson(UnifiedPage page, String name, String newName) {
        page.step("Edit " + name, () -> {
            page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
            page.step("Change the name", () -> {
                page.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
                page.find(UnifiedTextField.class).withLabel("Email").setValue(page.secret("alan@example.com"));
            });
            page.find(UnifiedButton.class).withText("Save").click();
        });
    }
}
