package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A {@code Div}, a {@code Span} or a layout with a click listener is clicked like a button (#358). */
@UnifiedSpringBootTest
class ClickableHtmlTest {

    // --8<-- [start:clicksADiv]
    @Test
    @DisplayName("The user opens a tab that is a div")
    void clicksADiv(UnifiedPage page) {
        page.navigate(MealPlanView.class);

        page.find(UnifiedDiv.class).withText("Shop").click();

        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Showing: Shop");
    }
    // --8<-- [end:clicksADiv]

    @Test
    @DisplayName("The user goes home with the wordmark, a span")
    void clicksASpan(UnifiedPage page) {
        page.navigate(MealPlanView.class);

        page.find(UnifiedSpan.class).withText("Mise").click();

        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Showing: Home");
    }

    // --8<-- [start:clicksAContainer]
    @Test
    @DisplayName("The user opens a recipe by its card, a layout")
    void clicksAContainer(UnifiedPage page) {
        page.navigate(MealPlanView.class);

        page.find(UnifiedContainer.class).withTestId("recipe-card").click();

        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Showing: Lentil soup");
    }
    // --8<-- [end:clicksAContainer]
}
