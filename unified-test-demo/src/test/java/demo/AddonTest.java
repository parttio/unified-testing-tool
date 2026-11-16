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
import org.junit.jupiter.api.Test;

/** Add-on wrappers (spec 06, ADR 0003); written once, runs on Browserless and Playwright. */
@UnifiedSpringBootTest
class AddonTest {

    // --8<-- [start:wrapperWithoutOverride]
    @Test
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
    void wrapperWithToolOverride(UnifiedPage page) {
        page.navigate(AddonView.class);
        UnifiedBadge inbox = page.find(UnifiedBadge.class).withTestId("inbox");
        page.waitUntil(() -> inbox.getCount() == 2);

        page.find(UnifiedButton.class).withText("Receive").click();

        page.waitUntil(() -> inbox.getCount() == 3);
        // the override is what runs on Browserless; elsewhere the wrapper's own default methods
        boolean browserless = page.unwrap(Object.class).getClass().getName().contains("browserless");
        assertEquals(browserless, inbox instanceof UnifiedBadgeBrowserless);
    }

    // --8<-- [start:anyComponentWithoutAWrapper]
    @Test
    void anyComponentWithoutAWrapper(UnifiedPage page) {
        page.navigate(AddonView.class);

        UnifiedGenericComponent service = page.findComponent(Rating.class).withLabel("Service");

        assertEquals(4, service.element().getPropertyInteger("value"));
        assertTrue(service.isVisible());
    }
    // --8<-- [end:anyComponentWithoutAWrapper]
}
