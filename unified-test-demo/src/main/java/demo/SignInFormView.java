package demo;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A LoginForm that signs in through its LoginEvent listener, without an {@code action}: the right password opens
 * the home view, a wrong one shows the form's error. "Forgot password" says so on the page.
 */
@AnonymousAllowed
@Route("sign-in")
public class SignInFormView extends VerticalLayout {

    /** The only user the listener accepts, and its password. */
    static final String USERNAME = "barista@vaadin.com";
    static final String PASSWORD = "coffee-beans";

    public SignInFormView() {
        Span status = new Span("Not signed in");
        status.setId("status");
        LoginForm login = new LoginForm();
        login.addLoginListener(e -> {
            if (USERNAME.equals(e.getUsername()) && PASSWORD.equals(e.getPassword())) {
                e.getSource().getUI().ifPresent(ui -> ui.navigate(HomeView.class));
            } else {
                login.setError(true);
            }
        });
        login.addForgotPasswordListener(e -> status.setText("Password reset requested"));
        add(status, login);
    }
}
