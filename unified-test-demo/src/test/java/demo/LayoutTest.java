package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The layout assertions on real Vaadin views at a phone's Viewport (ADR 0011). */
@UnifiedSpringBootTest
@Viewport(width = 375, height = 667, name = "phone")
class LayoutTest {

    @Test
    @DisplayName("A view that fits a phone has no Layout Findings")
    void aCleanView(UnifiedPage page) {
        page.navigate(LayoutCleanView.class);

        assertThat(page).hasNoLayoutFindings();
        assertThat(page.find(UnifiedButton.class).withText("Save")).isInViewport().isNotClipped();
    }

    @Test
    @DisplayName("A button below the fold is reachable by scrolling, so not clipped, but not in the Viewport")
    void belowTheFold(UnifiedPage page) {
        page.navigate(LayoutCleanView.class);
        UnifiedButton below = page.find(UnifiedButton.class).withText("Below the fold");

        assertThat(below).isNotClipped();
        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> assertThat(below).isInViewport());
        assertEquals("after scroll", e.getFound());
    }

    @Test
    @DisplayName("A row wider than a phone scrolls AppLayout's content area; a wide Grid and Tabs scroll by design")
    void appLayoutContentScrollsSideways(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> assertThat(page).hasNoHorizontalScroll());

        assertTrue(e.getFound().startsWith("vaadin-app-layout [part=content] scrolls sideways by "), e.getFound());
        assertFalse(e.getFound().contains("grid") || e.getFound().contains("tabs"), e.getFound());
    }
}
