package com.vaadin.testing.unified.internal.login;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.links.LinkSupport;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.LoginAccess;

/**
 * Signing in through a LoginOverlay or a LoginForm ({@link LoginAccess}), on every Testing Tool. The password is
 * a secret of the run from the moment the test hands it over: no User Step, report or failure shows it.
 */
public final class LoginSupport {
    private LoginSupport() {}

    /** The tag of a {@code LoginForm}; a {@code LoginOverlay}'s is {@code vaadin-login-overlay}. */
    static final String FORM_TAG = "vaadin-login-form";

    /**
     * Signs in once the component is usable (Auto-Wait). A component with an {@code action} posts a form, which
     * a Testing Tool without a browser can't submit: that fails, before anything is fired. A sign-in that
     * navigated is followed by an {@code open} User Step.
     */
    public static void login(UnifiedWrapper login, String username, String password) {
        LazyElement element = Wrappers.lazyElementOf(login);
        TestRun run = element.run();
        run.steps().password(username, password);
        String before = run.session().currentPath();
        element.act("sign in", e -> {
            LoginAccess access = e.unwrap(LoginAccess.class);
            String action = e.getProperty("action");
            if (action != null && !action.isBlank() && !access.postsForms()) {
                throw run.failure(FailureParts.of("to sign in through " + StepTarget.of(element).renderWithScope(),
                        "a login form that posts to “" + action + "”, which " + run.tool() + " can't submit: "
                                + run.tool() + " fires the component's LoginEvent, and doesn't follow a form post")
                        .suggestion("@RequiresBrowser on the test, so it is skipped on " + run.tool()
                                + "; or @WithMockUser to sign in without the form"), null);
            }
            access.login(username, password);
        });
        LinkSupport.recordNavigation(run, before);
    }

    /** Presses "Forgot password" once the component is usable; fails when the component hides that button. */
    public static void forgotPassword(UnifiedWrapper login) {
        LazyElement element = Wrappers.lazyElementOf(login);
        TestRun run = element.run();
        element.act("press “Forgot password” in", e -> {
            if (Boolean.TRUE.equals(e.getPropertyBoolean("noForgotPassword"))) {
                throw run.failure(FailureParts.of("to press “Forgot password” in "
                        + StepTarget.of(element).renderWithScope(), "no “Forgot password” button: the application hid it")
                        .suggestion("show the button in the application (setForgotPasswordButtonVisible(true))"),
                        null);
            }
            e.unwrap(LoginAccess.class).forgotPassword();
        });
    }

    /** Whether {@code e} is a {@code LoginForm}, which has no overlay. */
    public static boolean isForm(UnifiedElement e) {
        return FORM_TAG.equalsIgnoreCase(e.getTag());
    }

    /**
     * A {@code LoginOverlay}'s title, null when it has none: on every Testing Tool, though a browser's element has
     * an empty {@code title} property of its own.
     */
    public static String title(UnifiedElement e) {
        if (isForm(e)) {
            return null;
        }
        String title = e.getProperty("title");
        return title == null || title.isEmpty() ? null : title;
    }
}
