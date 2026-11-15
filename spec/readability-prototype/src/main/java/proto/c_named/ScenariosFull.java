package proto.c_named;

import static proto.c_named.Assertions.assertThat;
import static proto.c_named.DialogAssertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import proto.common.Common.PeopleView;
import proto.common.Common.UnifiedSpringBootTest;

@UnifiedSpringBootTest
class ScenariosFull {

    @Test
    @DisplayName("Editing a person in the dialog updates the grid")
    void full_editsAPersonInTheDialog(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);
        people.rowWhere("Name", "Alan Turing").click();

        UnifiedDialog dialog = page.find(UnifiedDialog.class, "Edit person");
        dialog.find(UnifiedTextField.class, "Name").setValue("Alan M. Turing");
        dialog.find(UnifiedTextField.class, "Email").setValue("alan@bletchley.uk");
        dialog.find(UnifiedComboBox.class, "Role").selectItem("Admin");
        dialog.find(UnifiedButton.class, "Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(dialog).isClosed();
        assertThat(people.rowWhere("Name", "Alan M. Turing"))
                .hasCellText("Email", "alan@bletchley.uk")
                .hasCellText("Role", "Admin");
    }
}
