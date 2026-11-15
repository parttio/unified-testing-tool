package proto.e_verbs;

import static proto.e_verbs.Assertions.assertThat;
import static proto.e_verbs.Steps.step;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import proto.common.Common.CheckoutView;
import proto.common.Common.PeopleView;
import proto.common.Common.UnifiedSpringBootTest;

/** Style F: style E plus optional step(..) groups. Only the two user-flow tests; grid capability tests gain nothing. */
@UnifiedSpringBootTest
class ScenariosWithSteps {

    @Test
    @DisplayName("Billing and shipping each keep their own City")
    void checkout_picksTheFieldThroughItsContainer(UnifiedPage page) {
        page.navigate(CheckoutView.class);
        UnifiedContainer billing = page.find(UnifiedContainer.class).withTestId("billing");
        UnifiedContainer shipping = page.find(UnifiedContainer.class).withId("shipping");

        step("Ship to a different city", () -> {
            billing.fillIn("City", "Turku");
            shipping.fillIn("City", "Berlin");
            page.clickOn("Save");
        });

        assertThat(page).showsNotification("Billing: Turku, shipping: Berlin");
        assertThat(billing).showsField("City", "Turku");
    }

    @Test
    @DisplayName("Editing a person from the grid saves the new name")
    void editPerson_editsAPersonFromTheGrid(UnifiedPage page) {
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);

        step("Rename Alan Turing", () -> {
            people.rowWhere("Name", "Alan Turing").click();
            page.fillIn("Name", "Alan M. Turing");
            page.clickOn("Save");
        });

        assertThat(page).showsNotification("Saved Alan M. Turing");
        assertThat(people.rowWhere("Name", "Alan M. Turing")).hasCellText("Email", "alan@example.com");
    }
}
