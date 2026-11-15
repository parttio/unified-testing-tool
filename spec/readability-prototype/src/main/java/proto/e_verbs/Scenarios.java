package proto.e_verbs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static proto.e_verbs.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import proto.common.Common.CheckoutView;
import proto.common.Common.PeopleView;
import proto.common.Common.UnifiedSpringBootTest;
import proto.common.Common.UsersView;

/** Style E: fillIn / clickOn / shows* for the top intents, style C for the rest. */
@UnifiedSpringBootTest
class Scenarios {

    @Test
    @DisplayName("Billing and shipping each keep their own City")
    void checkout_picksTheFieldThroughItsContainer(UnifiedPage page) {
        page.navigate(CheckoutView.class);
        UnifiedContainer billing = page.find(UnifiedContainer.class).withTestId("billing");
        UnifiedContainer shipping = page.find(UnifiedContainer.class).withId("shipping");

        billing.fillIn("City", "Turku");
        shipping.fillIn("City", "Berlin");
        page.clickOn("Save");

        assertThat(page).showsNotification("Billing: Turku, shipping: Berlin");
        assertThat(billing).showsField("City", "Turku");
    }

    @Test
    @DisplayName("Editing a person from the grid saves the new name")
    void editPerson_editsAPersonFromTheGrid(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);

        people.rowWhere("Name", "Alan Turing").click();
        page.fillIn("Name", "Alan M. Turing");
        page.clickOn("Save");

        assertThat(page).showsNotification("Saved Alan M. Turing");
        assertThat(people.rowWhere("Name", "Alan M. Turing")).hasCellText("Email", "alan@example.com");
    }

    @Test
    @DisplayName("A row far beyond the first page is found by what it shows")
    void grid_findsARowByWhatItShows(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users");

        UnifiedGrid.Rows row = users.rowWhere("Name", "User 110");

        assertEquals(109, row.getIndex());
        assertThat(row).hasCellText("Email", "user110@example.com").hasCellText("Role", "Admin");
    }

    @Test
    @DisplayName("Cell filters combine")
    void grid_combinesCellFilters(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users");

        assertThat(users.rows().withCellTextContaining("Email", "user11")).hasCount(11);
        assertThat(users.rows().withCellTextContaining("Email", "user11").withCellText("Role", "Admin")).hasCount(1);
        assertThat(users.rowWhere("Name", "Nobody")).doesNotExist();
    }
}
