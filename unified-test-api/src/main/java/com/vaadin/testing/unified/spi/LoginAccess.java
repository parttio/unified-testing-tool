package com.vaadin.testing.unified.spi;

/**
 * Adapter access to a LoginOverlay or a LoginForm, reached by the core with
 * {@code element.unwrap(LoginAccess.class)}. Its fields and buttons exist only in the browser: on the server the
 * component has its {@code LoginEvent} and {@code ForgotPasswordEvent} listeners.
 */
public interface LoginAccess {

    /**
     * Whether this Testing Tool submits the form as a browser does, so that a component with an {@code action}
     * (a form post, such as Spring Security's {@code /login}) posts it. False on a Testing Tool without a browser,
     * which only fires the component's {@code LoginEvent}; the core then fails a login on a component with an
     * {@code action}.
     */
    boolean postsForms();

    /**
     * Signs in as a user does: types the username and password into the form's fields and presses its submit
     * button, then waits for the server round-trip or the page the form post opened. Without a browser: fires
     * the component's {@code LoginEvent} with them. The password must not appear in anything this throws.
     */
    void login(String username, String password);

    /** Presses "Forgot password", as a user's click does: the component fires its {@code ForgotPasswordEvent}. */
    void forgotPassword();
}
