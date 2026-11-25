package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.grid.GridTester;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.vaadin.testing.unified.UnifiedAssertionError;
import java.time.Duration;
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

    // --8<-- [start:clicksAComponentInACellByIndex]
    @Test
    @DisplayName("The user clicks Remove in Bob's row, in a column without header text")
    void clicksAComponentInACellByIndex(UnifiedPage page) {
        UnifiedGrid accounts = grid(page, "accounts");
        UnifiedGrid.UnifiedGridRow bob = accounts.row().withCellText("Name", "Bob");

        // the hidden Email column doesn't count: Name is at 0, the Remove column at 1
        assertEquals("Bob", bob.getCellText(0));
        bob.cell(1).find(UnifiedButton.class).withText("Remove").click();

        assertNotification(page, "Removing Bob");
    }
    // --8<-- [end:clicksAComponentInACellByIndex]

    // --8<-- [start:clicksRendererMarkupInACell]
    @Test
    @RequiresBrowser // the markup a LitRenderer draws exists only in a browser
    @DisplayName("The user clicks the pencil in Bob's row and the card in Ada's, both drawn by a LitRenderer")
    void clicksRendererMarkupInACell(UnifiedPage page) {
        UnifiedGrid editable = grid(page, "editable");
        // DemoEdit is a Wrapper bound to the tag only: @UnifiedComponent(value = Component.class, tag = "demo-edit")
        editable.row().withCellText("Name", "Bob").cell(1).find(DemoEdit.class).click();
        assertNotification(page, "Editing Bob");

        UnifiedGrid cards = page.find(UnifiedGrid.class).withTestId("cards");
        cards.row(0).cell("Order").find(DemoCard.class).click();
        assertNotification(page, "Opening Ada");
    }
    // --8<-- [end:clicksRendererMarkupInACell]

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @DisplayName("On Browserless, a lookup in a LitRenderer's cell fails and names @RequiresBrowser, never finding nothing")
    void rendererMarkupIsNotFoundOnBrowserless(UnifiedPage page) {
        UnifiedGrid editable = grid(page, "editable").withTimeout(Duration.ofMillis(500));
        Scope pencilCell = editable.row().withCellText("Name", "Bob").cell(1);
        String expected = "The cell at index 1 is drawn by a LitRenderer, whose markup exists only in a browser, so"
                + " Browserless can't search inside it. Annotate the test with @RequiresBrowser to skip it here; to run"
                + " a renderer function on Browserless, use the Escape Hatch: GridTester.invokeLitRendererFunction.";

        UnifiedAssertionError click = assertThrows(UnifiedAssertionError.class,
                () -> pencilCell.find(DemoEdit.class).click());
        assertEquals(expected, click.getCause().getMessage());

        // not an empty cell: an absence check fails too, rather than passing
        UnifiedAssertionError absent = assertThrows(UnifiedAssertionError.class,
                () -> assertThat(pencilCell.find(DemoEdit.class)).doesNotExist());
        assertEquals(expected, absent.getCause().getMessage());
    }

    @Test
    @RequiresBrowser // Browserless can't see layout: it fires the grid's item click listener
    @DisplayName("Clicking a row that a clickable card fills fails the same way on every browser, naming the cell")
    void rowClickAvoidsRendererMarkup(UnifiedPage page) {
        UnifiedGrid cards = grid(page, "cards").withTimeout(Duration.ofMillis(500));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> cards.row(0).click());

        assertEquals("Cannot click row 0: interactive content covers every data cell of the row, padding included,"
                + " so a click would land on it rather than on the row. To click that content, find it in its cell:"
                + " row.cell(..).find(..).click()", error.getMessage());
    }

    // --8<-- [start:runsRendererFunctionOnBrowserless]
    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS) // the Escape Hatch to Browserless Test's GridTester
    @DisplayName("On Browserless, the test runs the card's renderer function for Ada's row")
    void runsRendererFunctionOnBrowserless(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid cards = page.find(UnifiedGrid.class).withTestId("cards");

        // not a user action: what the card's @click would call, run without the card
        cards.unwrap(GridTester.class).invokeLitRendererFunction(0, 0, "open");

        assertNotification(page, "Opening Ada");
    }
    // --8<-- [end:runsRendererFunctionOnBrowserless]

    @Test
    @DisplayName("A cell index past the last visible column fails, naming the cell by its index and the column count")
    void cellIndexOutOfRange(UnifiedPage page) {
        UnifiedGrid accounts = grid(page, "accounts").withTimeout(Duration.ofMillis(500));

        UnifiedAssertionError error = assertThrows(UnifiedAssertionError.class, () -> accounts.row()
                .withCellText("Name", "Bob").cell(2).find(UnifiedButton.class).withText("Remove").click());

        // the User Step names the cell by its index
        assertTrue(error.getMessage().contains("click button “Remove” in cell at index 2 in row where Name is “Bob”"
                + " in grid (test id “accounts”)"), error.getMessage());
        assertEquals("No visible column at index 2 (2 columns)", error.getCause().getMessage());
    }

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
