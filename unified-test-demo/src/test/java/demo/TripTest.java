package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class TripTest {

    /** getText() is the DOM text: CSS doesn't change it, so it is the same on every Testing Tool. */
    @Test
    @DisplayName("The attention note and the trip total read the same on every Testing Tool")
    void readsTheSameTextOnEveryTool(UnifiedPage page) {
        page.navigate(TripView.class);

        assertThat(page.findComponent(Span.class).withId("attention")).hasText("Needs your attention");
        assertThat(page.findComponent(HorizontalLayout.class).withId("total")).hasText("Trip total€54.00");
    }

    /** getVisibleText() is what the browser renders. */
    @Test
    @RequiresBrowser
    @DisplayName("A real browser renders the attention note in capitals and the total on two lines")
    void readsTheRenderedText(UnifiedPage page) {
        page.navigate(TripView.class);

        assertEquals("NEEDS YOUR ATTENTION",
                page.findComponent(Span.class).withId("attention").element().getVisibleText());
        assertEquals("Trip total\n€54.00",
                page.findComponent(HorizontalLayout.class).withId("total").element().getVisibleText());
    }

    @Test
    @DisplayName("The trip total's inline styles read as the view declares them")
    void readsInlineStylesAsDeclared(UnifiedPage page) {
        page.navigate(TripView.class);
        var total = page.findComponent(HorizontalLayout.class).withId("total").element();

        assertEquals("var(--lumo-space-m)", total.getInlineStyle("gap"));
        assertEquals("180px", total.getInlineStyle("width"));
        assertEquals(null, total.getInlineStyle("height"));
    }

    // --8<-- [start:filtersByClassName]
    /** A class filter on the any-tag container: one selector query, not a check per element of the page. */
    @Test
    @DisplayName("Sixty trip cards are shown and Trip 42 is selected")
    void filtersByClassName(UnifiedPage page) {
        page.navigate(TripView.class);

        assertThat(page.find(UnifiedContainer.class).withClassName("trip-card")).hasCount(60);
        assertThat(page.find(UnifiedContainer.class).withClassName("selected")).hasText("Trip 42");
        assertThat(page.findComponent(Div.class).withClassName("trip-card").withText("Trip 7")).hasCount(1);
    }
    // --8<-- [end:filtersByClassName]
}
