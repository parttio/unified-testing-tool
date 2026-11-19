package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class GridTest {

    // --8<-- [start:helpers]
    private static UnifiedGrid grid(UnifiedPage page, String testId) {
        page.navigate(UsersView.class);
        return page.find(UnifiedGrid.class).withTestId(testId);
    }
    // --8<-- [end:helpers]

    @Test
    @DisplayName("The users grid shows 120 rows under Name, Email, Role and Actions")
    void readsCountHeadersAndCells(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        assertThat(users).hasRowCount(120);
        assertEquals(List.of("Name", "Email", "Role", "Actions"), users.getColumnHeaders());
        assertThat(users).hasColumnHeaders("Name", "Email", "Role", "Actions").hasColumn("Email")
                .doesNotHaveColumn("Phone");
        assertThat(users.row(0)).hasCellText("Name", "User 1").hasCellText("Email", "user1@example.com");
    }

    // --8<-- [start:findsARowByWhatItShows]
    @Test
    @DisplayName("The user finds User 110's row far down the grid by its name")
    void findsARowByWhatItShows(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        // far beyond the first page: loaded lazily
        UnifiedGrid.UnifiedGridRow row = users.row().withCellText("Name", "User 110");

        assertEquals(109, row.getIndex());
        assertThat(row).hasCellText("Email", "user110@example.com").hasCellText("Role", "Admin");
    }
    // --8<-- [end:findsARowByWhatItShows]

    // --8<-- [start:waitsForAColumn]
    @Test
    @DisplayName("Showing the archive date adds an Archived on column, hiding it takes it away")
    void waitsForAColumnToComeAndGo(UnifiedPage page) {
        UnifiedGrid archived = grid(page, "archived");

        page.find(UnifiedButton.class).withText("Show archive date").click();
        assertThat(archived).hasColumn("Archived on").hasColumnHeaders("Name", "Archived on");

        page.find(UnifiedButton.class).withText("Hide archive date").click();
        assertThat(archived).doesNotHaveColumn("Archived on").hasColumnHeaders("Name");
    }
    // --8<-- [end:waitsForAColumn]

    @Test
    @DisplayName("A row is found by a cell's text in any column")
    void findsARowByCellTextInAnyColumn(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        // "user110@example.com" is in the Email column, "User 110" in the Name column
        assertEquals(109, users.row().withCellText("user110@example.com").getIndex());
        assertEquals(109, users.row().withCellText("User 110").getIndex());
        assertThat(users.row().withCellText("user110@example")).doesNotExist();
    }

    @Test
    @DisplayName("A cell is read by the index of its column")
    void readsACellByColumnIndex(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        assertEquals("User 1", users.row(0).getCellText(0));
        assertEquals("user110@example.com", users.row().withCellText("Name", "User 110").getCellText(1));
    }

    // --8<-- [start:combinesCellFilters]
    @Test
    @DisplayName("Only one user with an email starting with user11 is an admin")
    void combinesCellFilters(UnifiedPage page) {
        UnifiedGrid users = grid(page, "users");

        // "user11" matches user11 and user110..user119 (11 rows); only user110 of them is an Admin
        assertThat(users.row().withCellTextContaining("Email", "user11")).hasCount(11);
        // ...Containing ignores case
        assertThat(users.row().withCellTextContaining("Email", "USER11@")).hasCount(1);
        assertThat(users.row().withCellTextContaining("Email", "user11").withCellText("Role", "Admin")).hasCount(1);
        assertThat(users.row().withCellText("Name", "Nobody")).doesNotExist();
    }
    // --8<-- [end:combinesCellFilters]

    @Test
    @DisplayName("The same column is found by its header, its id and its index")
    void readsColumnsByHeaderIdAndIndex(UnifiedPage page) {
        UnifiedGrid roles = grid(page, "roles");

        assertThat(roles.column("Role")).hasCellTexts("Admin", "Editor", "Viewer");
        assertThat(roles.columnWithId("owner")).hasCellTexts("Ada", "Bob", "Cy");
        assertThat(roles.column(1)).hasCellTexts("Ada", "Bob", "Cy");
    }

    @Test
    @DisplayName("The archived users grid is empty")
    void knowsAnEmptyGrid(UnifiedPage page) {
        assertThat(grid(page, "archived")).isEmpty();
    }
}
