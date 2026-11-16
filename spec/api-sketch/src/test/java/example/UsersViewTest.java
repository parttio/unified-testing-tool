package example;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedTextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The spec's Grid example. Compiles against the sketch; never run. */
@UnifiedUITest
class UsersViewTest {

    static class UsersView extends Div {}   // stands in for an application @Route view

    @Test
    @DisplayName("The admin filters the users and edits Alice")
    void editsAlice(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withAccessibleName("Users");

        users.column("Name").header().find(UnifiedTextField.class).setValue("Ali");
        assertThat(users).hasRowCount(1);

        UnifiedGrid.UnifiedGridRow alice = users.row().withCellText("Name", "Alice");
        assertThat(alice).hasCellText("Email", "alice@example.com");
        alice.cell("Actions").find(UnifiedButton.class).withText("Edit").click();

        alice.select();
        assertThat(alice).isSelected();
        assertThat(users.row().selected()).hasCount(1);
        alice.toggleDetails();
        alice.details().find(UnifiedButton.class).withText("Reset password").click();
        assertThat(users.row().withCellText("Name", "Mallory")).doesNotExist();
    }

    @Test
    @DisplayName("The admin sorts the users by name")
    void sortsByName(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class);

        users.column("Name").sort(SortDirection.ASCENDING);
        assertThat(users.column("Name")).isSortedAscending().hasCellTexts("Alice", "Bob", "Carol");
        assertThat(users.row(0)).hasCellText("Name", "Alice");
    }
}
