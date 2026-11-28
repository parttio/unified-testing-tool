package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.login.AbstractLogin;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.login.LoginSupport;
import com.vaadin.testing.unified.query.CommonFilters;
import java.time.Duration;

/**
 * A {@code LoginOverlay} or a {@code LoginForm}: there is normally one per page, so no filter is needed. A
 * {@code LoginOverlay} is found while it is open.
 *
 * <pre>{@code
 * UnifiedLogin login = page.find(UnifiedLogin.class);
 * login.login("barista@vaadin.com", "barista");
 * assertThat(page).isAt(StorefrontView.class);
 * }</pre>
 *
 * The password is never shown: not in the User Steps, the reports or a failure.
 */
@UnifiedComponent(value = AbstractLogin.class, tag = "vaadin-login-overlay, vaadin-login-form")
public interface UnifiedLogin extends UnifiedWrapper, CommonFilters<UnifiedLogin> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedLogin withTimeout(Duration timeout);

    /** The overlay's title ({@link #getTitle()}), exactly. A custom filter built from {@code where(..)}. */
    default UnifiedLogin withTitle(String title) {
        return where(e -> title.equals(LoginSupport.title(e)));
    }

    // actions (Auto-Wait)
    /**
     * Signs in as a user does, and waits for the server round-trip, or for the page a form post opened. In a
     * browser: types the username and password into the form's fields and presses its submit button, which also
     * posts a form with an {@code action} (Spring Security's {@code /login}). On Browserless: fires the
     * component's {@code LoginEvent}; a component with an {@code action} fails, as Browserless can't submit a form
     * post: the test needs {@code @RequiresBrowser}, or {@code @WithMockUser} to sign in without the form.
     * The User Step reads {@code sign in as “barista@vaadin.com” in login}; the password is never shown.
     */
    default void login(String username, String password) { login(username, password, Password.SECRET); }

    /**
     * Signs in as {@link #login(String, String)} does, saying whether the password is a secret. A seeded test user
     * whose password is test data, often its user name ({@code admin} / {@code admin}), can be declared
     * {@link Password#NOT_SECRET}: the reports then show it wherever it occurs, and the User Step reads
     * {@code sign in as “admin” in login} instead of {@code sign in as “••••” in login}.
     *
     * <pre>{@code
     * page.find(UnifiedLogin.class).login("admin", "admin", Password.NOT_SECRET);
     * }</pre>
     */
    default void login(String username, String password, Password secrecy) {
        LoginSupport.login(this, username, password, secrecy);
    }

    /**
     * Presses "Forgot password": the component fires its {@code ForgotPasswordEvent}. Fails when the component
     * hides that button ({@code setForgotPasswordButtonVisible(false)}).
     */
    default void forgotPassword() { LoginSupport.forgotPassword(this); }

    // getters (immediate)
    /** A {@code LoginOverlay}: whether it is open. A {@code LoginForm}, which has no overlay: whether it is on the page. */
    default boolean isOpen() {
        UnifiedElement e = element();
        return LoginSupport.isForm(e) || Boolean.TRUE.equals(e.getPropertyBoolean("opened"));
    }
    /** Whether the component shows its error message: the application called {@code setError(true)}. */
    default boolean isError() { return Boolean.TRUE.equals(element().getPropertyBoolean("error")); }
    /** Whether the component shows its "Forgot password" button. */
    default boolean isForgotPasswordButtonVisible() {
        return !Boolean.TRUE.equals(element().getPropertyBoolean("noForgotPassword"));
    }
    /** A {@code LoginOverlay}'s title, as the application set it with {@code setTitle(String)}; null if none. */
    default String getTitle() { return LoginSupport.title(element()); }
    /** A {@code LoginOverlay}'s description, as set with {@code setDescription(String)}; null if none. */
    default String getDescription() {
        String description = element().getProperty("description");
        return description == null || description.isEmpty() ? null : description;
    }

    /** Whether a sign-in's password is a secret of the run, which the reports show as {@code ••••}. */
    enum Password {
        /** The reports show the password as {@code ••••} wherever it occurs, except inside a different user name. */
        SECRET,
        /** The password is test data: the reports show it as it is, unless another call marks it secret. */
        NOT_SECRET
    }
}
