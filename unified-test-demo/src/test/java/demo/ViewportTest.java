package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.ViewportSize;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** A test runs at an exact Viewport on every browser Testing Tool, and the server hears of a change (ADR 0011). */
@UnifiedSpringBootTest
@RequiresBrowser
class ViewportTest {

    private static void measures(UnifiedPage page, String size) {
        page.find(UnifiedButton.class).withText("Measure").click();
        assertThat(page.findComponent(Span.class).withId("measured")).hasText(size);
    }

    @Test
    @DisplayName("Without @Viewport, the page is exactly 1280 × 720")
    void defaultViewport(UnifiedPage page) {
        page.navigate(ViewportView.class);

        measures(page, "1280 × 720");
        assertEquals(ViewportSize.of(1280, 720), page.viewport());
    }

    @Test
    @Viewport(width = 375, height = 667, name = "phone")
    @DisplayName("With @Viewport, the page is exactly that size from the start")
    void declaredViewport(UnifiedPage page) {
        page.navigate(ViewportView.class);

        measures(page, "375 × 667");
    }

    @Test
    @DisplayName("After setViewport, the server has heard of the new size by the next User Step")
    void resizedMidTest(UnifiedPage page) {
        page.navigate(ViewportView.class);

        page.setViewport(768, 1024);

        // read once, no retry: setViewport waited for the server
        assertEquals("768 × 1024", page.findComponent(Span.class).withId("heard").getText());
        measures(page, "768 × 1024");
    }

    @Test
    @Viewport(width = 375, height = 667)
    @DisplayName("Another window starts at the test's Viewport, not the size its opener was changed to")
    void anotherWindow(UnifiedPage page) {
        page.navigate(ViewportView.class);
        page.setViewport(1024, 768);

        UnifiedPage other = page.openWindow();
        other.navigate(ViewportView.class);

        measures(other, "375 × 667");
        measures(page, "1024 × 768");
    }

    /**
     * The run's browser is shared by its tests on Selenium ({@code reuse-browser}, the default): a size one test sets
     * stays with that test.
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OneAfterAnother {

        @Test
        @Order(1)
        @DisplayName("A test makes the page as small as an old phone, exactly 320 × 568")
        void resizesToAnOldPhone(UnifiedPage page) {
            page.navigate(ViewportView.class);

            page.setViewport(320, 568);

            measures(page, "320 × 568");
        }

        @Test
        @Order(2)
        @DisplayName("The next test starts at its own Viewport, not the size the one before left")
        void theNextStartsAtItsOwn(UnifiedPage page) {
            page.navigate(ViewportView.class);

            measures(page, "1280 × 720");
        }
    }
}
