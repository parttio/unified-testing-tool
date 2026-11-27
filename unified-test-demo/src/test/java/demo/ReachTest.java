package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a user can reach at a phone's Viewport, in the Findable View and in failures (ADR 0011, #494). */
@UnifiedSpringBootTest
@Viewport(width = 375, height = 667, name = "phone")
class ReachTest {

    @Test
    @DisplayName("With the drawer open on a phone, the Findable View shows the content's buttons behind it")
    void theFindableViewShowsWhatTheDrawerCovers(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        page.find(UnifiedButton.class).withText("Open the drawer").click();

        String view = page.describe();

        assertTrue(view.lines().findFirst().orElseThrow().contains(" at Viewport phone 375 × 667: "), view);
        assertTrue(view.lines().anyMatch(l -> l.startsWith("button “Save” (behind the AppLayout drawer)")), view);
    }

    @Test
    @DisplayName("Clicking a button the open drawer covers fails, saying what covers it")
    void clickingACoveredButtonSaysWhatCoversIt(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        page.find(UnifiedButton.class).withText("Open the drawer").click();

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> page.find(UnifiedButton.class).withText("Save").click());

        assertEquals("behind the AppLayout drawer", e.getFound());
    }
}
