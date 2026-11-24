package demo;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A LoginOverlay, open when the view opens, that signs in through its LoginEvent listener: the right password
 * closes it and greets the user, a wrong one shows its error. It hides "Forgot password".
 */
@AnonymousAllowed
@Route("sign-in-overlay")
public class SignInOverlayView extends VerticalLayout {

    public SignInOverlayView() {
        Span status = new Span("Not signed in");
        status.setId("status");
        LoginOverlay login = new LoginOverlay();
        login.setTitle("Bakery");
        login.setDescription("Coffee and pastries");
        login.setForgotPasswordButtonVisible(false);
        login.addLoginListener(e -> {
            if (SignInFormView.USERNAME.equals(e.getUsername()) && SignInFormView.PASSWORD.equals(e.getPassword())) {
                login.close();
                status.setText("Welcome, " + e.getUsername());
            } else {
                login.setError(true);
            }
        });
        add(status, login);
        login.setOpened(true);
    }
}
