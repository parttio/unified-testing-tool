package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class GridInteractionTest {

    // --8<-- [start:helpers]
    private static UnifiedGrid grid(UnifiedPage page, String testId) {
        page.navigate(UsersView.class);
        return page.find(UnifiedGrid.class).withTestId(testId);
    }

    private static void assertNotification(UnifiedPage page, String text) {
        assertThat(page.find(UnifiedNotification.class).withText(text)).isOpen();
    }
    // --8<-- [end:helpers]

    // --8<-- [start:clicksAComponentInACell]
    @Test
    @DisplayName("The user clicks Edit in User 3's row")
    void clicksAComponentInACell(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        users.row().withCellText("Name", "User 3")
                .cell("Actions").find(UnifiedButton.class).withText("Edit").click();

        assertNotification(page, "Editing User 3");
    }
    // --8<-- [end:clicksAComponentInACell]

    /** A page-wide find reaches components rendered in grid cells, as in a browser. */
    @Test
    @DisplayName("The user clicks User 3's Edit button without going through the grid")
    void findsACellComponentPageWide(UnifiedPage page) {
        grid(page, "users");

        page.find(UnifiedButton.class).withAccessibleName("Edit User 3").click();

        assertNotification(page, "Editing User 3");
    }

    // --8<-- [start:filtersThroughAHeaderField]
    @Test
    @DisplayName("The user filters the users by name in the column header")
    void filtersThroughAHeaderField(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        users.column("Name").header().find(UnifiedTextField.class).withPlaceholder("Filter by name")
                .setValue("User 11");

        // User 11 and User 110..119
        assertThat(users).hasRowCount(11);
        assertThat(users.row(0)).hasCellText("Name", "User 11");
    }
    // --8<-- [end:filtersThroughAHeaderField]

    @Test
    @DisplayName("The user sorts the roles descending, then ascending")
    void sortsAColumn(UnifiedPage page) {
        UnifiedGrid roles = grid(page, "roles");

        roles.column("Role").sort(SortDirection.DESCENDING);
        assertThat(roles.column("Role")).isSortedDescending().hasCellTexts("Viewer", "Editor", "Admin");

        roles.column("Role").sort(SortDirection.ASCENDING);
        assertThat(roles.column("Role")).isSortedAscending().hasCellTexts("Admin", "Editor", "Viewer");
    }

    // --8<-- [start:selectsRows]
    @Test
    @DisplayName("The user sorts columns whose captions sit under a header group")
    void sortsAColumnUnderAHeaderGroup(UnifiedPage page) {
        UnifiedGrid staff = grid(page, "staff");
        assertEquals(List.of("Name", "Role", "Email"), staff.getColumnHeaders());

        staff.column("Email").sort(SortDirection.DESCENDING);
        assertThat(staff.column("Email")).isSortedDescending()
                .hasCellTexts("c@example.com", "b@example.com", "a@example.com");

        staff.column("Role").sort(SortDirection.ASCENDING);
        assertThat(staff.column("Role")).isSortedAscending().hasCellTexts("Admin", "Editor", "Viewer");
    }

    @Test
    @DisplayName("The user selects one role, then all of them, then none")
    void selectsRows(UnifiedPage page) {
        UnifiedGrid roles = grid(page, "roles");

        // the multi-select checkbox column is not a Unified column
        assertThat(roles.column(1)).hasCellTexts("Ada", "Bob", "Cy");

        UnifiedGrid.UnifiedGridRow bob = roles.row().withCellText("Role", "Editor");
        bob.select();
        assertThat(bob).isSelected();
        assertThat(roles.row().selected()).hasCount(1);

        roles.selectAll();
        assertThat(roles.row().selected()).hasCount(3);

        roles.deselectAll();
        assertThat(bob).isNotSelected();
    }
    // --8<-- [end:selectsRows]

    @Test
    @DisplayName("Clicking a row and double-clicking another each notify the user")
    void clicksAndDoubleClicksRows(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        users.row(1).click();
        assertNotification(page, "Clicked User 2");

        users.row(4).doubleClick();
        assertNotification(page, "Opened User 5");
    }

    /** A row click lands beside the button the first cell holds, as a user clicking the row would. */
    @Test
    @DisplayName("Clicking a task's row doesn't press the Done button in it")
    void clicksARowWithoutClickingTheButtonInItsFirstCell(UnifiedPage page) {
        UnifiedGrid tasks = grid(page, "tasks");

        tasks.row(0).click();

        assertNotification(page, "Clicked Task 1");
        assertThat(page.find(UnifiedNotification.class).withText("Done Task 1")).doesNotExist();
    }

    @Test
    @DisplayName("The user opens and closes a row's details")
    void togglesRowDetails(UnifiedPage page) {
        UnifiedGrid.UnifiedGridRow row = grid(page, "users").row(2);

        row.toggleDetails();
        assertThat(row).hasDetailsOpen();

        row.toggleDetails();
        assertThat(row).hasDetailsClosed();
    }

    @Test
    @RequiresBrowser // Browserless can't search inside row details yet (gap ticket)
    @DisplayName("The user resets a password from the row details")
    void clicksAButtonInRowDetails(UnifiedPage page) {
        UnifiedGrid.UnifiedGridRow row = grid(page, "users").row(2);

        row.toggleDetails();
        row.details().find(UnifiedButton.class).withText("Reset password").click();

        assertNotification(page, "Password reset for User 3");
    }
}
