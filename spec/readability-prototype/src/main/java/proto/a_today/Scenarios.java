package proto.a_today;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static proto.a_today.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import proto.common.Common.CheckoutView;
import proto.common.Common.PeopleView;
import proto.common.Common.UnifiedSpringBootTest;
import proto.common.Common.UsersView;

/** Style A, today: copied from the demo tests unchanged. */
@UnifiedSpringBootTest
class Scenarios {

    @Test
    void checkout_picksTheFieldThroughItsContainer(UnifiedPage page) {
        page.navigate(CheckoutView.class);
        UnifiedContainer billing = page.find(UnifiedContainer.class).withTestId("billing").single();
        UnifiedContainer shipping = page.find(UnifiedContainer.class).withId("shipping").single();

        billing.find(UnifiedTextField.class).withLabel("City").single().setValue("Turku");
        shipping.find(UnifiedTextField.class).withLabel("City").single().setValue("Berlin");
        page.find(UnifiedButton.class).withText("Save").single().click();

        assertThat(page.find(UnifiedNotification.class).single()).hasText("Billing: Turku, shipping: Berlin");
        assertThat(billing.find(UnifiedTextField.class).withLabel("City").single()).hasValue("Turku");
    }

    @Test
    void editPerson_editsAPersonFromTheGrid(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class).single();

        people.rows().withCellText("Name", "Alan Turing").single().click();
        page.find(UnifiedTextField.class).withLabel("Name").single().setValue("Alan M. Turing");
        page.find(UnifiedButton.class).withText("Save").single().click();

        assertThat(page.find(UnifiedNotification.class).single()).hasText("Saved Alan M. Turing");
        assertThat(people.rows().withCellText("Name", "Alan M. Turing").single())
                .hasCellText("Email", "alan@example.com");
    }

    @Test
    void grid_findsARowByWhatItShows(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users").single();

        UnifiedGrid.Row row = users.rows().withCellText("Name", "User 110").single();

        assertEquals(109, row.getIndex());
        assertThat(row).hasCellText("Email", "user110@example.com").hasCellText("Role", "Admin");
    }

    @Test
    void grid_combinesCellFilters(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users").single();

        assertEquals(11, users.rows().withCellTextContaining("Email", "user11").all().size());
        assertEquals(1, users.rows().withCellTextContaining("Email", "user11").withCellText("Role", "Admin").all().size());
        assertFalse(users.rows().withCellText("Name", "Nobody").exists());
    }
}
