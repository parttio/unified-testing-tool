package com.vaadin.testing.unified;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * One browser tab or window showing the application. Injected into a Unified UI Test.
 * Escape Hatch types: Playwright {@code Page}, {@code BrowserlessUIContext}.
 */
public interface UnifiedPage extends Scope, HasEscapeHatch {

    // navigation: returns nothing, Auto-Waits until the view is shown and the server is idle
    void navigate(Class<? extends Component> view);
    <T, C extends Component & HasUrlParameter<T>> void navigate(Class<C> view, T parameter);
    void navigate(Class<? extends Component> view, RouteParameters parameters);
    /** With a query string: {@code navigate(PlanView.class, QueryParameters.of("week", "2026-11-02"))}. */
    void navigate(Class<? extends Component> view, QueryParameters query);
    void navigate(Class<? extends Component> view, RouteParameters parameters, QueryParameters query);
    /** Path relative to the base URL, may include a query string: {@code "profile?tab=2"}. */
    void navigate(String path);
    void reload();

    /** Polls the condition until true or the timeout; on Browserless flushes UI.access tasks and signals between polls. */
    void waitUntil(BooleanSupplier condition);

    void pressKey(String key);

    /** Needs {@link RequiresBrowser}. */
    void screenshot(Path file);

    /**
     * Resizes this window's Viewport, exactly, as a User Step ("set viewport to 768 × 1024"), and waits until the
     * application has heard of it: Vaadin's resize event comes 300 ms after the last one in the browser (ADR 0011).
     * Other windows keep theirs. For the size a test starts at, use {@link Viewport @Viewport}: it costs no wait.
     * Needs {@link RequiresBrowser}.
     */
    default void setViewport(int width, int height) {
        setViewport(ViewportSize.of(width, height));
    }

    /** {@link #setViewport(int, int)} with a size that may have a name, as the reports show it. */
    void setViewport(ViewportSize size);

    /**
     * Measures the layout now, as the User Step "check the layout": its Layout Findings show on that step in the
     * reports, for a moment the automatic layout checks don't measure, such as after opening the drawer (ADR 0011).
     * With {@code vaadin.testing.layout-findings=fail}, a finding fails the step. Needs {@link RequiresBrowser}.
     */
    void checkLayout();

    /**
     * This window's Viewport: the test's ({@link Viewport @Viewport}, the run's {@code vaadin.testing.viewport},
     * else 1280 × 720) until {@link #setViewport(int, int)} changes it.
     */
    ViewportSize viewport();

    /**
     * Whether this run's Testing Tool has a real browser: false on Browserless. For a test that runs on
     * every Testing Tool but does browser-only steps, such as {@link #screenshot}, only when it can.
     * A test that only makes sense in a browser declares {@link RequiresBrowser} instead.
     */
    boolean hasRealBrowser();

    // reports
    /**
     * A named piece of user work, a User Task: the User Steps {@code userSteps} performs show under this name
     * in the reports. Steps nest without limit. On a failure inside, the report points first at the line that
     * called this step, then at the line inside it. Works across {@link #openWindow()},
     * {@link #openAsUser} and {@link #openNewSession()}: every window records into the test's one list of User Steps.
     *
     * <pre>{@code
     * page.step("Edit " + name, () -> {
     *     page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
     *     page.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
     * });
     * }</pre>
     *
     * Name steps after what the user does ("Edit Alan Turing"), not given/when/then.
     */
    void step(String name, Runnable userSteps);

    /**
     * Work the test does outside the application's UI, such as checking what the database, a file or a mail
     * holds, as a Backend Step: the reports show it among the User Steps as {@code backend: order is stored}, and
     * the run summary counts it as a check (ADR 0023). The work runs once, after Auto-Wait has waited for the
     * server to be idle; wait for a later result with {@link #waitUntil}. If it throws, the Backend Step and the
     * test fail.
     *
     * <pre>{@code
     * page.find(UnifiedButton.class).withText("Place order").click();
     * page.backendStep("order is stored", () -> assertThat(orders.findByCustomer("Alan")).hasSize(1));
     * }</pre>
     *
     * Name it after what it checks or does ("order is stored"). For checks of the UI, use
     * {@code assertThat(..)}.
     */
    void backendStep(String name, Runnable work);

    /**
     * Marks a value as secret and returns it unchanged: the User Step reports show it as {@code ••••} wherever
     * it appears, in every window of the test. Values typed into a password field are hidden without it.
     *
     * <pre>{@code
     * page.find(UnifiedTextField.class).withLabel("API key").setValue(page.secret(apiKey));
     * }</pre>
     */
    String secret(String value);

    // windows and users: all close at the end of the test
    /** Another window of the same user, in the same session. */
    UnifiedPage openWindow();

    /**
     * A window of another user, in a session of its own, logged in with these roles (Spring Security,
     * {@code ROLE_} prefix added). Starts on no view: navigate first.
     */
    UnifiedPage openAsUser(String username, String... roles);

    /**
     * A window in a new session of its own, with nobody signed in: sign in through the application's own login,
     * as a user does. Works without Spring Security, and ignores the test's {@code @WithMockUser}. Starts on no
     * view: navigate first.
     *
     * <pre>{@code
     * UnifiedPage officer = admin.openNewSession();
     * officer.navigate(LoginView.class);
     * officer.find(UnifiedLogin.class).login("officer", password);
     * }</pre>
     */
    UnifiedPage openNewSession();
}
