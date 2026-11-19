package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

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

    // --8<-- [start:navigatesByClassWithQuery]
    @Test
    @DisplayName("Opening the week view for a given week shows that week")
    void navigatesByClassWithQuery(UnifiedPage page) {
        page.navigate(WeekView.class, QueryParameters.of("week", "2026-11-02"));

        assertThat(page).hasPath("week").hasQueryParameter("week", "2026-11-02");
        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Plan of everyone for 2026-11-02");
    }
    // --8<-- [end:navigatesByClassWithQuery]

    @Test
    @DisplayName("Opening Ada's week view for a given week shows Ada's plan for that week")
    void navigatesByClassWithRouteAndQueryParameters(UnifiedPage page) {
        page.navigate(WeekView.class, new RouteParameters("owner", "ada"), QueryParameters.of("week", "2026-11-02"));

        assertThat(page).hasPath("week/ada").hasQueryParameter("week", "2026-11-02");
        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Plan of ada for 2026-11-02");
    }

    /** The title comes from the view's @PageTitle; the page's title is read on every Testing Tool. */
    @Test
    @DisplayName("The profile has its title once the user goes there from the home view")
    void showsTheViewsTitle(UnifiedPage page) {
        page.navigate(HomeView.class);

        page.find(UnifiedButton.class).withText("Go to profile").click();

        assertThat(page).isAt(ProfileView.class).hasTitle("Profile");
    }

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

    /**
     * Flow changes the address bar after the response that navigated: in a timeout, or, in React mode, through
     * React Router while {@code Vaadin.Flow.navigation} is set. Auto-Wait waits for that, so a one-shot read of
     * the URL right after the click sees the new path (#233). The race was lost about one run in three; here the
     * page holds the URL change back 300 ms with the flag set, so it is lost every time without the wait.
     */
    @Test
    @RequiresTestingTool({ TestingTool.PLAYWRIGHT, TestingTool.SELENIUM })
    @DisplayName("The address bar shows the profile as soon as the button that navigates there returns")
    void updatesTheUrlBeforeAServerSideNavigationReturns(UnifiedPage page) {
        page.navigate(HomeView.class);
        // React Router's pushState waits 300 ms, and Vaadin.Flow.navigation reads as set until it has run
        script(page, """
                let pending = false;
                const pushState = history.pushState.bind(history);
                history.pushState = (state, title, url) => {
                  pending = true;
                  setTimeout(() => { pushState(state, title, url); pending = false; }, 300);
                };
                window.Vaadin.Flow = new Proxy(window.Vaadin.Flow, {
                  get: (flow, name) => name === 'navigation' ? pending || flow.navigation : flow[name]
                });""");

        page.find(UnifiedButton.class).withText("Go to profile").click();

        String href = script(page, "return location.href;");
        assertTrue(href.endsWith("/profile"), href);
    }

    /** Runs a script in the page through the Escape Hatch: no retry, unlike hasPath. */
    private static String script(UnifiedPage page, String body) {
        var playwright = page.tryUnwrap(com.microsoft.playwright.Page.class);
        Object result = playwright.isPresent() ? playwright.get().evaluate("() => { " + body + " }")
                : ((JavascriptExecutor) page.unwrap(WebDriver.class)).executeScript(body);
        return (String) result;
    }
}
