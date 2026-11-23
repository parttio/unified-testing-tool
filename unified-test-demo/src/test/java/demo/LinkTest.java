package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedLink;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@code UnifiedLink}: a RouterLink or an Anchor, followed as a user's click does on every Testing Tool (#561). */
@UnifiedSpringBootTest
class LinkTest {

    // --8<-- [start:followsALink]
    @Test
    @DisplayName("The user follows the Profile link")
    void followsARouterLink(UnifiedPage page) {
        page.navigate(LinksView.class);

        page.find(UnifiedLink.class).withText("Profile").click();

        assertThat(page).isAt(ProfileView.class);
    }
    // --8<-- [end:followsALink]

    @Test
    @DisplayName("The user follows the People link, an anchor to a page of the application")
    void followsAnAnchorToARoute(UnifiedPage page) {
        page.navigate(LinksView.class);

        page.find(UnifiedLink.class).withHref("people").click();

        assertThat(page).isAt(PeopleView.class);
    }

    // --8<-- [start:readsALink]
    @Test
    @DisplayName("Each link goes where the application says, with its text")
    void readsWhereALinkGoes(UnifiedPage page) {
        page.navigate(LinksView.class);
        UnifiedLink profile = page.find(UnifiedLink.class).withText("Profile");

        assertThat(profile).hasHref("profile").hasText("Profile");
        assertThat(page.find(UnifiedLink.class).withHref("people")).hasText("People");
        assertEquals("profile", profile.getHref());
        assertEquals("People", page.find(UnifiedLink.class).withHref("people").getText());
    }
    // --8<-- [end:readsALink]

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @DisplayName("Following a link out of the application fails on Browserless, asking for a browser")
    void linkOutOfTheApplicationNeedsABrowser(UnifiedPage page) {
        page.navigate(LinksView.class);
        UnifiedLink vaadin = page.find(UnifiedLink.class).withText("Vaadin");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, vaadin::click);

        assertTrue(e.getMessage().contains("leaves the application (“https://vaadin.com”)"), e.getMessage());
        assertTrue(e.getMessage().contains("@RequiresBrowser"), e.getMessage());
        assertThat(page).isAt(LinksView.class);
    }

    @Test
    @RequiresBrowser
    @DisplayName("A link out of the application goes where the application says")
    void readsALinkOutOfTheApplication(UnifiedPage page) {
        page.navigate(LinksView.class);

        assertThat(page.find(UnifiedLink.class).withText("Vaadin")).hasHref("https://vaadin.com");
    }

    @Test
    @DisplayName("The user can't follow the disabled Users link")
    void disabledLinkCantBeFollowed(UnifiedPage page) {
        page.navigate(LinksView.class);
        UnifiedLink users = (UnifiedLink) page.find(UnifiedLink.class).withText("Users")
                .withTimeout(Duration.ofMillis(500));

        AssertionError e = assertThrows(AssertionError.class, users::click);

        assertTrue(e.getMessage().contains("disabled"), e.getMessage());
        assertThat(page).isAt(LinksView.class);
    }
}
