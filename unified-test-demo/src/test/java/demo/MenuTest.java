package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.component.UnifiedTabSheet;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A {@code Tabs} used as a navigation menu, as Vaadin's Bakery builds it (#531): {@code selectTab} on a tab holding a
 * link follows the link, as a user's click on the tab does, on every Testing Tool.
 */
@UnifiedSpringBootTest
class MenuTest {

    // --8<-- [start:followsATabsLink]
    @Test
    @DisplayName("The user opens the Profile page from the menu")
    void followsTheLinkOfATab(UnifiedPage page) {
        page.navigate(MenuView.class);

        page.find(UnifiedTabs.class).withId("menu").selectTab("Profile");

        assertThat(page).isAt(ProfileView.class);
    }
    // --8<-- [end:followsATabsLink]

    @Test
    @DisplayName("The user opens the People page from the menu")
    void followsTheLinkOfAnotherTab(UnifiedPage page) {
        page.navigate(MenuView.class);

        page.find(UnifiedTabs.class).withId("menu").selectTab("People");

        assertThat(page).isAt(PeopleView.class);
    }

    @Test
    @DisplayName("The user opens the Notes tab, which holds no link: it is selected and shows the notes")
    void selectsATabWithoutALink(UnifiedPage page) {
        page.navigate(MenuView.class);
        UnifiedTabs menu = page.find(UnifiedTabs.class).withId("menu");

        menu.selectTab("Notes");

        assertThat(menu).hasSelectedTab("Notes");
        assertThat(page.find(UnifiedSpan.class).withId("note")).hasText("Notes opened");
        assertThat(page).isAt(MenuView.class);
    }

    @Test
    @DisplayName("The user opens the People page from a tab sheet's tab")
    void followsTheLinkOfATabSheetsTab(UnifiedPage page) {
        page.navigate(MenuView.class);

        page.find(UnifiedTabSheet.class).selectTab("People");

        assertThat(page).isAt(PeopleView.class);
    }

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @DisplayName("A tab holding a link out of the application fails on Browserless, asking for a browser")
    void tabLinkOutOfTheApplicationNeedsABrowser(UnifiedPage page) {
        page.navigate(MenuView.class);
        UnifiedTabs menu = page.find(UnifiedTabs.class).withId("menu");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> menu.selectTab("Account"));

        assertTrue(e.getMessage().contains("the link of tab “Account”"), e.getMessage());
        assertTrue(e.getMessage().contains("leaves the application"), e.getMessage());
        assertTrue(e.getMessage().contains("@RequiresBrowser"), e.getMessage());
        assertThat(page).isAt(MenuView.class);
        assertThat(menu).hasSelectedTab("Profile");
    }

    @Test
    @RequiresBrowser
    @DisplayName("The user opens the Account tab, a link to an absolute URL, and the page loads anew")
    void followsATabLinkToAnAbsoluteUrl(UnifiedPage page) {
        page.navigate(MenuView.class);

        page.find(UnifiedTabs.class).withId("menu").selectTab("Account");

        assertThat(page).isAt(ProfileView.class);
    }
}
