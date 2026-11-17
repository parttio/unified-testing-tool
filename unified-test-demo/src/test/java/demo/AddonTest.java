package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import demo.addon.Rating;
import demo.addon.testing.UnifiedBadge;
import demo.addon.testing.UnifiedBadgeBrowserless;
import demo.addon.testing.UnifiedRating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Add-on wrappers (spec 06, ADR 0003); written once, runs on Browserless and Playwright. */
@UnifiedSpringBootTest
class AddonTest {

    // --8<-- [start:wrapperWithoutOverride]
    @Test
    @DisplayName("The user rates the food 3 and the service keeps its 4")
    void wrapperWithoutOverride(UnifiedPage page) {
        page.navigate(AddonView.class);
        UnifiedRating food = page.find(UnifiedRating.class).withLabel("Food");

        food.rate(3);

        page.waitUntil(() -> food.getValue() == 3);
        assertEquals(4, page.find(UnifiedRating.class).withLabel("Service").getValue());
    }
    // --8<-- [end:wrapperWithoutOverride]

    // --8<-- [start:customFilters]
    @Test
    @DisplayName("The two ratings are found by their value and by their label")
    void customFilters(UnifiedPage page) {
        page.navigate(AddonView.class);

        // custom filter from where(..)
        assertThat(page.find(UnifiedRating.class).withValue(4)).hasCount(1);
        // custom filters can also be built inline from withProperty
        assertThat(page.find(UnifiedRating.class).withProperty("label", "Service")).hasCount(1);
        assertThat(page.find(UnifiedRating.class)).hasCount(2);
    }
    // --8<-- [end:customFilters]

    @Test
    @DisplayName("The inbox badge counts a received message")
    void wrapperWithToolOverride(UnifiedPage page) {
        page.navigate(AddonView.class);
        UnifiedBadge inbox = page.find(UnifiedBadge.class).withTestId("inbox");
        page.waitUntil(() -> inbox.getCount() == 2);

        page.find(UnifiedButton.class).withText("Receive").click();

        page.waitUntil(() -> inbox.getCount() == 3);
        // the override is what runs on Browserless; elsewhere the wrapper's own default methods
        boolean browserless = !page.hasRealBrowser();
        assertEquals(browserless, inbox instanceof UnifiedBadgeBrowserless);
    }

    // --8<-- [start:wrapperWithItsOwnWords]
    @Test
    @DisplayName("The user gives the food three stars")
    void wrapperWithItsOwnWords(UnifiedPage page) {
        page.navigate(AddonView.class);
        UnifiedStarRating food = page.find(UnifiedStarRating.class).withLabel("Food");

        food.rate(3);

        assertThat(food.withStars(3)).exists();
    }
    // --8<-- [end:wrapperWithItsOwnWords]

    // --8<-- [start:anyComponentWithoutAWrapper]
    @Test
    @DisplayName("The service rating is read without a wrapper of its own")
    void anyComponentWithoutAWrapper(UnifiedPage page) {
        page.navigate(AddonView.class);

        UnifiedGenericComponent service = page.findComponent(Rating.class).withLabel("Service");

        assertEquals(4, service.element().getPropertyInteger("value"));
        assertTrue(service.isVisible());
    }
    // --8<-- [end:anyComponentWithoutAWrapper]

    /** A whole-number property reads "3" on every Testing Tool, not "3.0" on Browserless (#255). */
    @Test
    @DisplayName("The food rating reads 3 after the user rates it 3")
    void readsAWholeNumberPropertyTheSameEverywhere(UnifiedPage page) {
        page.navigate(AddonView.class);
        UnifiedRating food = page.find(UnifiedRating.class).withLabel("Food");

        food.rate(3);

        assertThat(food).hasProperty("value", 3);
        assertEquals("3", food.element().getProperty("value"));
    }
}
