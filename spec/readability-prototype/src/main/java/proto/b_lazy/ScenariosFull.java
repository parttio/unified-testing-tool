package proto.b_lazy;

import static proto.b_lazy.Assertions.assertThat;
import static proto.b_lazy.DialogAssertions.assertThat;
import static proto.b_lazy.Steps.step;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import proto.common.Common.PeopleView;
import proto.common.Common.UnifiedSpringBootTest;

@UnifiedSpringBootTest
@SuppressWarnings("unused")
class ScenariosFull {

    // ---- B: lazy, nothing else
    @Test
    @DisplayName("Editing a person in the dialog updates the grid")
    void full_editsAPersonInTheDialog(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);
        people.rows().withCellText("Name", "Alan Turing").click();

        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
        dialog.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
        dialog.find(UnifiedTextField.class).withLabel("Email").setValue("alan@bletchley.uk");
        dialog.find(UnifiedComboBox.class).withLabel("Role").selectItem("Admin");
        dialog.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(dialog).isClosed();
        assertThat(people.rows().withCellText("Name", "Alan M. Turing"))
                .hasCellText("Email", "alan@bletchley.uk")
                .hasCellText("Role", "Admin");
    }

    // ---- B + given/when/then comments: no API
    @Test
    @DisplayName("Editing a person in the dialog updates the grid")
    void fullGwt_editsAPersonInTheDialog(UnifiedPage page) {
        // given Alan Turing is listed
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);

        // when I change his name, email and role
        people.rows().withCellText("Name", "Alan Turing").click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
        dialog.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
        dialog.find(UnifiedTextField.class).withLabel("Email").setValue("alan@bletchley.uk");
        dialog.find(UnifiedComboBox.class).withLabel("Role").selectItem("Admin");
        dialog.find(UnifiedButton.class).withText("Save").click();

        // then the grid shows the new details
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(dialog).isClosed();
        assertThat(people.rows().withCellText("Name", "Alan M. Turing"))
                .hasCellText("Email", "alan@bletchley.uk")
                .hasCellText("Role", "Admin");
    }

    // ---- B + step(..): one static method, sections reach the report
    @Test
    @DisplayName("Editing a person in the dialog updates the grid")
    void fullSteps_editsAPersonInTheDialog(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);

        step("When I change Alan Turing's name, email and role", () -> {
            people.rows().withCellText("Name", "Alan Turing").click();
            UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
            dialog.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
            dialog.find(UnifiedTextField.class).withLabel("Email").setValue("alan@bletchley.uk");
            dialog.find(UnifiedComboBox.class).withLabel("Role").selectItem("Admin");
            dialog.find(UnifiedButton.class).withText("Save").click();
        });

        step("Then the grid shows the new details", () -> {
            assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
            assertThat(page.find(UnifiedDialog.class).withHeaderTitle("Edit person")).isClosed();
            assertThat(people.rows().withCellText("Name", "Alan M. Turing"))
                    .hasCellText("Email", "alan@bletchley.uk")
                    .hasCellText("Role", "Admin");
        });
    }

    // ---- B + task methods: plain Java in the test class, no API
    @Test
    @DisplayName("Editing a person in the dialog updates the grid")
    void fullTasks_editsAPersonInTheDialog(UnifiedPage page) {
        page.navigate(PeopleView.class);

        editPerson(page, "Alan Turing", "Alan M. Turing", "alan@bletchley.uk", "Admin");

        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(personRow(page, "Alan M. Turing"))
                .hasCellText("Email", "alan@bletchley.uk")
                .hasCellText("Role", "Admin");
    }

    private static void editPerson(UnifiedPage page, String name, String newName, String email, String role) {
        personRow(page, name).click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
        dialog.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
        dialog.find(UnifiedTextField.class).withLabel("Email").setValue(email);
        dialog.find(UnifiedComboBox.class).withLabel("Role").selectItem(role);
        dialog.find(UnifiedButton.class).withText("Save").click();
        assertThat(dialog).isClosed();
    }

    private static UnifiedGrid.Rows personRow(UnifiedPage page, String name) {
        return page.find(UnifiedGrid.class).rows().withCellText("Name", name);
    }
}
