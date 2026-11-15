package proto.a_today;

import static proto.a_today.Assertions.assertThat;
import static proto.a_today.DialogAssertions.assertThat;

import org.junit.jupiter.api.Test;
import proto.common.Common.PeopleView;
import proto.common.Common.UnifiedSpringBootTest;

@UnifiedSpringBootTest
class ScenariosFull {

    @Test
    void full_editsAPersonInTheDialog(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class).single();
        people.rows().withCellText("Name", "Alan Turing").single().click();

        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Edit person").single();
        dialog.find(UnifiedTextField.class).withLabel("Name").single().setValue("Alan M. Turing");
        dialog.find(UnifiedTextField.class).withLabel("Email").single().setValue("alan@bletchley.uk");
        dialog.find(UnifiedComboBox.class).withLabel("Role").single().selectItem("Admin");
        dialog.find(UnifiedButton.class).withText("Save").single().click();

        assertThat(page.find(UnifiedNotification.class).single()).hasText("Saved Alan M. Turing");
        assertThat(dialog).isClosed();
        assertThat(people.rows().withCellText("Name", "Alan M. Turing").single())
                .hasCellText("Email", "alan@bletchley.uk")
                .hasCellText("Role", "Admin");
    }
}
