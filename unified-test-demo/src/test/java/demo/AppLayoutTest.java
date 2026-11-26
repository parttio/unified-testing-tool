package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.Viewport;
import com.vaadin.testing.unified.component.UnifiedAppLayout;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code UnifiedAppLayout} (#493, ADR 0011): whether the drawer is an overlay and whether it is opened, read the same
 * on every Testing Tool; opened and closed with the layout's DrawerToggle, as a user does.
 */
@UnifiedSpringBootTest
class AppLayoutTest {

    @Test
    @RequiresBrowser
    @DisplayName("At 1280 px the drawer sits beside the content and is opened")
    void onADesktopTheDrawerIsNotAnOverlay(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);

        assertThat(page.find(UnifiedAppLayout.class)).isNotOverlay().isDrawerOpened();
    }

    // --8<-- [start:drawerIsAnOverlay]
    @Test
    @Viewport(width = 375, height = 667, name = "phone")
    @DisplayName("At 375 px the drawer is an overlay, closed until the user opens it")
    void onAPhoneTheDrawerIsAClosedOverlay(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);

        assertThat(page.find(UnifiedAppLayout.class)).isOverlay().isDrawerClosed();
    }
    // --8<-- [end:drawerIsAnOverlay]

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @DisplayName("Browserless knows whether the drawer is opened, as the server does, but not whether it is an overlay")
    void browserlessKnowsTheDrawerButNotTheOverlay(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        UnifiedAppLayout layout = page.find(UnifiedAppLayout.class);

        assertThat(layout).isDrawerOpened();
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> assertThat(layout).isOverlay());

        assertEquals("isOverlay needs a real browser, but this run uses BROWSERLESS. Annotate the test with "
                + "@RequiresBrowser to skip it on BROWSERLESS.", e.getMessage());
    }

    // --8<-- [start:opensTheDrawer]
    @Test
    @Viewport(width = 375, height = 667, name = "phone")
    @DisplayName("On a phone the user opens the drawer with its toggle and closes it again")
    void onAPhoneTheUserOpensAndClosesTheDrawer(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        UnifiedAppLayout layout = page.find(UnifiedAppLayout.class);

        layout.openDrawer();
        assertThat(layout).isDrawerOpened();

        layout.closeDrawer();
        assertThat(layout).isDrawerClosed();
    }
    // --8<-- [end:opensTheDrawer]

    @Test
    @DisplayName("The user hides the drawer with its toggle and shows it again")
    void theUserTogglesTheDrawer(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        UnifiedAppLayout layout = page.find(UnifiedAppLayout.class);

        layout.toggleDrawer();
        assertThat(layout).isDrawerClosed();

        layout.toggleDrawer();
        assertThat(layout).isDrawerOpened();
    }

    @Test
    @RequiresBrowser
    @DisplayName("A DrawerToggle found by its class is the toggle, not a button: clicking it closes the drawer")
    void aDrawerToggleFoundByItsClassIsTheToggle(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);

        page.findComponent(DrawerToggle.class).element().click();

        assertThat(page.find(UnifiedAppLayout.class)).isDrawerClosed();
    }

    @Test
    @Viewport(width = 375, height = 667, name = "phone")
    @DisplayName("The Findable View says the app layout's drawer is a closed overlay")
    void theFindableViewSaysTheDrawerIsAClosedOverlay(UnifiedPage page) {
        page.navigate(LayoutAppLayoutView.class);
        assertThat(page.find(UnifiedAppLayout.class)).isOverlay().isDrawerClosed();

        String line = page.describe().lines().filter(l -> l.startsWith("app layout")).findFirst().orElseThrow();

        assertEquals("app layout (overlay; drawer closed)  page.find(UnifiedAppLayout.class)",
                line.replaceAll("\\s{2,}", "  "));
    }
}
