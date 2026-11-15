package proto.d_specs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static proto.d_specs.Assertions.assertThat;
import static proto.d_specs.Components.button;
import static proto.d_specs.Components.container;
import static proto.d_specs.Components.grid;
import static proto.d_specs.Components.notification;
import static proto.d_specs.Components.row;
import static proto.d_specs.Components.rows;
import static proto.d_specs.Components.textField;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import proto.common.Common.CheckoutView;
import proto.common.Common.PeopleView;
import proto.common.Common.UnifiedSpringBootTest;
import proto.common.Common.UsersView;

/** Style D: find(textField("City")), lazy Wrappers as in style B. */
@UnifiedSpringBootTest
class Scenarios {

    @Test
    @DisplayName("Billing and shipping each keep their own City")
    void checkout_picksTheFieldThroughItsContainer(UnifiedPage page) {
        page.navigate(CheckoutView.class);
        UnifiedContainer billing = page.find(container().withTestId("billing"));
        UnifiedContainer shipping = page.find(container().withId("shipping"));

        billing.find(textField("City")).setValue("Turku");
        shipping.find(textField("City")).setValue("Berlin");
        page.find(button("Save")).click();

        assertThat(page.find(notification())).hasText("Billing: Turku, shipping: Berlin");
        assertThat(billing.find(textField("City"))).hasValue("Turku");
    }

    @Test
    @DisplayName("Editing a person from the grid saves the new name")
    void editPerson_editsAPersonFromTheGrid(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(grid());

        people.find(row("Name", "Alan Turing")).click();
        page.find(textField("Name")).setValue("Alan M. Turing");
        page.find(button("Save")).click();

        assertThat(page.find(notification())).hasText("Saved Alan M. Turing");
        assertThat(people.find(row("Name", "Alan M. Turing"))).hasCellText("Email", "alan@example.com");
    }

    @Test
    @DisplayName("A row far beyond the first page is found by what it shows")
    void grid_findsARowByWhatItShows(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(grid().withTestId("users"));

        UnifiedRows row = users.find(row("Name", "User 110"));

        assertEquals(109, row.getIndex());
        assertThat(row).hasCellText("Email", "user110@example.com").hasCellText("Role", "Admin");
    }

    @Test
    @DisplayName("Cell filters combine")
    void grid_combinesCellFilters(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(grid().withTestId("users"));

        assertThat(users.find(rows().withCellTextContaining("Email", "user11"))).hasCount(11);
        assertThat(users.find(rows().withCellTextContaining("Email", "user11").withCellText("Role", "Admin"))).hasCount(1);
        assertThat(users.find(row("Name", "Nobody"))).doesNotExist();
    }
}
