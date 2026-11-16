package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class DashboardTest {

    @Test
    void findsCardsByTheirWrapper(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThat(page.find(UnifiedMetricCard.class)).hasCount(2);
        assertEquals("12", page.find(UnifiedMetricCard.class).withTextContaining("Trips").getValue());
    }

    /** No tag: a browser can't find it, so no Testing Tool accepts the query. */
    @Test
    void refusesAQueryWithoutTag(UnifiedPage page) {
        page.navigate(DashboardView.class);

        assertThrows(IllegalArgumentException.class, () -> page.findComponent(Component.class));
    }
}
