package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.BaseUrl;
import com.vaadin.testing.unified.BaseUrlProvider;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * A test class's own Base URL (ADR 0021, #678). The provider stands in for a pool of application copies: it hands
 * the class the same app under another host, 127.0.0.1, so the test tells it apart from the Spring port's localhost.
 */
// --8<-- [start:provider]
@UnifiedSpringBootTest
@RequiresBrowser
@BaseUrl(provider = BaseUrlTest.SameAppOn127.class)
class BaseUrlTest {

    static volatile int port;

    /** Spring sets the random port before the test's session opens, which calls the provider. */
    @LocalServerPort
    void port(int port) {
        BaseUrlTest.port = port;
    }

    public static final class SameAppOn127 implements BaseUrlProvider {
        @Override
        public String baseUrl(Class<?> testClass) {
            return "http://127.0.0.1:" + port + "/";
        }
    }
    // --8<-- [end:provider]

    @Test
    @DisplayName("A relative path opens on the test class's Base URL, not on the Spring port's")
    void opensRelativePathsOnTheClassesBaseUrl(UnifiedPage page) {
        page.navigate("profile?tab=2");

        assertThat(page).isAt(ProfileView.class).hasPath("profile").hasQueryParameter("tab", "2");
        assertEquals("127.0.0.1:" + port, script(page, "return window.location.host"));
    }

    @Test
    @DisplayName("A route class opens on the test class's Base URL, not on the Spring port's")
    void opensRouteClassesOnTheClassesBaseUrl(UnifiedPage page) {
        page.navigate(HomeView.class);

        assertThat(page).isAt(HomeView.class);
        assertEquals("127.0.0.1:" + port, script(page, "return window.location.host"));
    }

    /** Reads the page through the Escape Hatch of either browser Testing Tool. */
    private static String script(UnifiedPage page, String body) {
        var playwright = page.tryUnwrap(com.microsoft.playwright.Page.class);
        Object result = playwright.isPresent() ? playwright.get().evaluate("() => { " + body + " }")
                : ((JavascriptExecutor) page.unwrap(WebDriver.class)).executeScript(body);
        return (String) result;
    }
}
