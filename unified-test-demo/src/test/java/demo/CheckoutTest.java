package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

/** Narrowing a search to a layout; written once, runs on Browserless and Playwright. */
@UnifiedSpringBootTest
class CheckoutTest {

    // --8<-- [start:picksTheFieldThroughItsContainer]
    @Test
    void picksTheFieldThroughItsContainer(UnifiedPage page) {
        page.navigate(CheckoutView.class);
        UnifiedContainer billing = page.find(UnifiedContainer.class).withTestId("billing");
        UnifiedContainer shipping = page.find(UnifiedContainer.class).withId("shipping");

        billing.find(UnifiedTextField.class).withLabel("City").setValue("Turku");
        shipping.find(UnifiedTextField.class).withLabel("City").setValue("Berlin");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Billing: Turku, shipping: Berlin");
        assertThat(billing.find(UnifiedTextField.class).withLabel("City")).hasValue("Turku");
    }
    // --8<-- [end:picksTheFieldThroughItsContainer]

    @Test
    void sameLabelOutsideAContainerIsAmbiguous(UnifiedPage page) {
        page.navigate(CheckoutView.class);

        assertThat(page.find(UnifiedTextField.class).withLabel("City")).hasCount(2);
        assertThat(page.find(UnifiedContainer.class).withTestId("billing")
                .find(UnifiedTextField.class)).hasCount(2);
    }

    // --8<-- [start:containersNest]
    @Test
    void containersNest(UnifiedPage page) {
        page.navigate(CheckoutView.class);

        UnifiedContainer buttons = page.find(UnifiedContainer.class).withTestId("footer")
                .find(UnifiedContainer.class).withTestId("buttons");

        assertThat(buttons.find(UnifiedButton.class)).hasCount(2);
        assertThat(buttons).isVisible();
        assertThat(page.find(UnifiedContainer.class).withTestId("billing")
                .find(UnifiedButton.class)).doesNotExist();
    }
    // --8<-- [end:containersNest]
}
