package com.vaadin.testing.unified;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * One browser tab or window showing the application. Injected into a Unified UI Test.
 * Escape Hatch types: Playwright {@code Page}, Selenium {@code WebDriver}, {@code BrowserlessUIContext}.
 */
public interface UnifiedPage extends Scope, HasEscapeHatch {

    // navigation: returns nothing, Auto-Waits until the view is shown and the server is idle
    void navigate(Class<? extends Component> view);
    <T, C extends Component & HasUrlParameter<T>> void navigate(Class<C> view, T parameter);
    void navigate(Class<? extends Component> view, RouteParameters parameters);
    void navigate(Class<? extends Component> view, QueryParameters query);
    void navigate(Class<? extends Component> view, RouteParameters parameters, QueryParameters query);
    /** Path relative to the base URL, may include a query string: {@code "profile?tab=2"}. */
    void navigate(String path);
    void reload();

    /** Polls the condition until true or the timeout; on Browserless flushes UI.access tasks and signals between polls. */
    void waitUntil(BooleanSupplier condition);

    void pressKey(String key);

    // reports (spec chapter 12)
    /**
     * A User Task: the User Steps {@code userSteps} performs show under this name in the reports. Steps nest.
     * Name it after what the user does ("Edit Alan Turing"), not given/when/then.
     */
    void step(String name, Runnable userSteps);

    /** Marks a value as secret and returns it unchanged: the reports show it as {@code ••••}. */
    String secret(String value);

    /** Another window of the same user, same session. Closed at the end of the test. */
    UnifiedPage openWindow();

    /** Another user with its own session, logged in with the given roles. Closed at the end of the test. */
    UnifiedPage openAsUser(String username, String... roles);

    /** A window in a new session of its own, nobody signed in: sign in through the app. Closed at the end of the test. */
    UnifiedPage openNewSession();

    /** Needs {@link RequiresBrowser}. */
    void screenshot(Path file);

    /**
     * Whether this run's Testing Tool has a real browser: false on Browserless. For a test that runs on
     * every Testing Tool but does browser-only steps, such as {@link #screenshot}, only when it can.
     * A test that only makes sense in a browser declares {@link RequiresBrowser} instead.
     */
    boolean hasRealBrowser();
}
