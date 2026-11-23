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
import com.vaadin.flow.component.html.Anchor;
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

    // issue #530: a route template with an optional parameter, and its aliases

    @Test
    @DisplayName("Opening the week view without an owner shows everyone's plan")
    void navigatesByClassToARouteWithAnOptionalParameter(UnifiedPage page) {
        page.navigate(WeekView.class);

        assertThat(page).isAt(WeekView.class).hasPath("week");
        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Plan of everyone for this week");
    }

    @Test
    @DisplayName("Opening the shipments without a number shows all shipments")
    void navigatesByClassWithoutTheOptionalParameter(UnifiedPage page) {
        page.navigate(ShipmentView.class);

        assertThat(page).isAt(ShipmentView.class).hasPath("shipment");
        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("All shipments");
    }

    @Test
    @DisplayName("Opening shipment 42 shows that shipment")
    void navigatesByClassWithTheOptionalParameter(UnifiedPage page) {
        page.navigate(ShipmentView.class, new RouteParameters("shipmentID", "42"));

        assertThat(page).isAt(ShipmentView.class).hasPath("shipment/42");
        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Shipment 42");
    }

    @Test
    @DisplayName("Opening the edit alias of shipment 42 shows the shipments view")
    void isAtTheViewOnAnAliasWithAParameter(UnifiedPage page) {
        page.navigate("shipment/42/edit");

        assertThat(page).isAt(ShipmentView.class).hasPath("shipment/42/edit");
        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("Shipment 42");
    }

    @Test
    @DisplayName("Opening the parcels alias shows the shipments view")
    void isAtTheViewOnARootLevelAlias(UnifiedPage page) {
        page.navigate("parcels");

        assertThat(page).isAt(ShipmentView.class).hasPath("parcels");
        assertThat(page.find(UnifiedParagraph.class).withId("showing")).hasText("All shipments");
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

    /**
     * A link out of the router loads a new page. Flow starts its UI when the router asks for the server's routes,
     * which can come well after load: in development mode, while the bundle builds or the server redeploys. Until
     * then only Flow's TypeScript client is registered, not active, so the page reads as idle while it shows
     * nothing: Auto-Wait waits until Flow has started the page's UI (#397). Here the router starts 500 ms after
     * load, so the race is lost every time without that wait.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The profile shows as soon as the link that loads it anew returns, however late Flow starts")
    void showsTheViewOfAPageLoadedByALink(UnifiedPage page) {
        page.navigate(HomeView.class);
        com.microsoft.playwright.Page playwright = page.unwrap(com.microsoft.playwright.Page.class);
        // in every page loaded from now on, React's scheduler (MessageChannel), and so the router that starts Flow,
        // waits until 500 ms after load
        playwright.addInitScript("""
                const post = MessagePort.prototype.postMessage;
                const held = [];
                let started = false;
                addEventListener('load', () => setTimeout(() => {
                  started = true;
                  held.splice(0).forEach(send => send());
                }, 500));
                MessagePort.prototype.postMessage = function (...args) {
                  if (started) return post.apply(this, args);
                  held.push(() => post.apply(this, args));
                };""");

        page.findComponent(Anchor.class).withText("Profile, loaded anew").element().click();

        // one read, no retry: what a screenshot or the Escape Hatch would see right after the step
        Object fields = playwright.evaluate("() => document.querySelectorAll('vaadin-text-field').length");
        assertTrue(((Number) fields).intValue() > 0, "the profile's fields, right after the click");
    }

    /**
     * Page.setLocation changes the location while Flow handles the click's response: the browser asks for the next
     * page after Flow is idle again, and nothing in Flow waits for it. Auto-Wait sees the page start that load and
     * waits for the new page, then for Flow to start it (#397). Here the server answers the new page 500 ms late,
     * so the race is lost every time without that wait.
     */
    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The profile shows as soon as the button that sends the user there through a slow server returns")
    void showsTheViewOfAPageTheServerSentTheUserTo(UnifiedPage page) {
        page.navigate(HomeView.class);

        page.find(UnifiedButton.class).withText("Open profile from a slow server").click();

        // one read, no retry: what a screenshot or the Escape Hatch would see right after the step
        Object fields = page.unwrap(com.microsoft.playwright.Page.class)
                .evaluate("() => document.querySelectorAll('vaadin-text-field').length");
        assertTrue(((Number) fields).intValue() > 0, "the profile's fields, right after the click");
    }

    /** Runs a script in the page through the Escape Hatch: no retry, unlike hasPath. */
    private static String script(UnifiedPage page, String body) {
        var playwright = page.tryUnwrap(com.microsoft.playwright.Page.class);
        Object result = playwright.isPresent() ? playwright.get().evaluate("() => { " + body + " }")
                : ((JavascriptExecutor) page.unwrap(WebDriver.class)).executeScript(body);
        return (String) result;
    }
}
