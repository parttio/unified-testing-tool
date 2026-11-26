package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A MenuBar too narrow for its items (#493, ADR 0011): the trailing items move into its overflow menu, "More
 * options". They are still its items, read and clicked the same at every Viewport.
 */
@UnifiedSpringBootTest
class MenuBarOverflowTest {

    private static final List<String> ITEMS = List.of("File", "Edit", "View", "Insert", "Format", "Share", "Print");

    @Test
    @RequiresBrowser
    @DisplayName("At 1280 px every item of the menu bar fits")
    void onADesktopNothingOverflows(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        UnifiedMenuBar menu = page.find(UnifiedMenuBar.class);

        assertThat(menu).hasNoOverflowItems();
        assertEquals(ITEMS, menu.getItems());
    }

    // --8<-- [start:overflowItems]
    @Test
    @Viewport(width = 375, height = 667, name = "phone")
    @DisplayName("At 375 px Share and Print move into the overflow menu, and are still the menu bar's items")
    void onAPhoneTheLastItemsOverflow(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        UnifiedMenuBar menu = page.find(UnifiedMenuBar.class);

        assertThat(menu).hasOverflowItems("Share", "Print");
        assertEquals(ITEMS, menu.getItems());
    }
    // --8<-- [end:overflowItems]

    // --8<-- [start:clicksAnOverflowedItem]
    @Test
    @Viewport(width = 375, height = 667, name = "phone")
    @DisplayName("At 375 px the user chooses Print from the overflow menu, as clickItem(\"Print\") does at every size")
    void anOverflowedItemIsClickedThroughTheOverflowMenu(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        UnifiedMenuBar menu = page.find(UnifiedMenuBar.class);
        assertThat(menu).hasOverflowItems("Share", "Print");

        menu.clickItem("Print");

        assertThat(page.find(UnifiedSpan.class).withId("chosen")).hasText("Chose Print");
    }
    // --8<-- [end:clicksAnOverflowedItem]

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @DisplayName("Browserless lists the menu bar's items but can't tell which overflow: that needs a browser")
    void browserlessCantTellWhichItemsOverflow(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        UnifiedMenuBar menu = page.find(UnifiedMenuBar.class);

        assertEquals(ITEMS, menu.getItems());
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> assertThat(menu).hasOverflowItems("Share", "Print"));
        assertEquals("hasOverflowItems needs a real browser, but this run uses BROWSERLESS. Annotate the test with "
                + "@RequiresBrowser to skip it on BROWSERLESS.", e.getMessage());
    }
}
