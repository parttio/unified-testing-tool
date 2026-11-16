package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest
class NavigationTest {

    @Test
    @DisplayName("The user goes from the home view to the profile with a button")
    void navigatesByButton(UnifiedPage page) {
        page.navigate(HomeView.class);
        assertThat(page).isAt(HomeView.class);

        page.find(UnifiedButton.class).withText("Go to profile").click();

        assertThat(page).isAt(ProfileView.class).hasPath("profile");
    }

    // --8<-- [start:navigatesByPathWithQuery]
    @Test
    @DisplayName("Opening profile?tab=2 shows the profile with the tab parameter")
    void navigatesByPathWithQuery(UnifiedPage page) {
        page.navigate("profile?tab=2");

        assertThat(page).isAt(ProfileView.class).hasPath("profile").hasQueryParameter("tab", "2");
    }
    // --8<-- [end:navigatesByPathWithQuery]

    /** A RouterLink navigates on the client only; isAt still sees the new location. */
    @Test
    @DisplayName("The user follows the Reports link from the home view")
    void navigatesByRouterLink(UnifiedPage page) {
        page.navigate(HomeView.class);

        page.findComponent(RouterLink.class).withText("Reports").element().click();

        assertThat(page).isAt(ReportView.class).hasPath("report");
    }

    /** An error view has no route: isAt matches it by the view the page shows. */
    @Test
    @DisplayName("Following a broken link shows the not found view")
    void showsTheNotFoundView(UnifiedPage page) {
        page.navigate(HomeView.class);

        page.find(UnifiedButton.class).withText("Broken link").click();

        assertThat(page).isAt(NotFoundView.class).hasPath("no-such-page");
    }
}
