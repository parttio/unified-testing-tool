package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.Component;
import java.util.ArrayList;
import java.util.List;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class DashboardTest {

    // --8<-- [start:findsCardsByTheirWrapper]
    @Test
    @DisplayName("The dashboard shows two metric cards, twelve trips on one")
    void findsCardsByTheirWrapper(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page.find(UnifiedMetricCard.class)).hasCount(2);
        assertEquals("12", page.find(UnifiedMetricCard.class).withTextContaining("Trips").getValue());
    }
    // --8<-- [end:findsCardsByTheirWrapper]

    // --8<-- [start:loopsOverTheMatches]
    @Test
    @DisplayName("Every metric card on the dashboard is shown with its title")
    void loopsOverTheMatches(UnifiedPage page) {
        page.navigate(DashboardView.class);
        UnifiedMetricCard cards = page.find(UnifiedMetricCard.class);
        assertThat(cards).hasCount(2);

        List<String> titles = new ArrayList<>();
        for (UnifiedMetricCard card : cards.all()) {
            assertThat(card).isVisible();
            titles.add(card.getTitle());
        }
        assertEquals(List.of("Open reports", "Trips"), titles);
    }
    // --8<-- [end:loopsOverTheMatches]

    /** No tag: a browser can't find it, so no Testing Tool accepts the query. */
    @Test
    @DisplayName("Searching for a component class without a tag is refused")
    void refusesAQueryWithoutTag(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThrows(IllegalArgumentException.class, () -> page.findComponent(Component.class));
    }
}
