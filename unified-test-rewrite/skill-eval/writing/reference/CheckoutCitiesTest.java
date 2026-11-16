package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class CheckoutCitiesTest {

    @Test
    @DisplayName("The user enters a billing and a shipping city and saves both")
    void savesBillingAndShippingCity(UnifiedPage page) {
        // given
        page.navigate(CheckoutView.class);

        // when
        page.find(UnifiedContainer.class).withTestId("billing")
                .find(UnifiedTextField.class).withLabel("City").setValue("Turku");
        page.find(UnifiedContainer.class).withId("shipping")
                .find(UnifiedTextField.class).withLabel("City").setValue("Berlin");
        page.find(UnifiedButton.class).withText("Save").click();

        // then
        assertThat(page.find(UnifiedNotification.class)).hasText("Billing: Turku, shipping: Berlin");
    }
}
