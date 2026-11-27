package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A sign-up form with an EmailField and a PasswordField (#555). Signing up validates both on the server, so every
 * Testing Tool sees the same validation state.
 */
@AnonymousAllowed
@Route("sign-up")
public class EmailSignUpView extends VerticalLayout {

    public EmailSignUpView() {
        EmailField email = new EmailField("Email");
        email.setPlaceholder("name@example.com");
        email.setHelperText("We send the confirmation there");
        email.setRequiredIndicatorVisible(true);
        email.setManualValidation(true);
        PasswordField password = new PasswordField("Password");
        password.setHelperText("At least 8 characters");
        password.setManualValidation(true);

        Button signUp = new Button("Sign up", e -> {
            boolean emailOk = email.getValue().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
            email.setErrorMessage(emailOk ? null : "Enter a valid email address");
            email.setInvalid(!emailOk);
            boolean passwordOk = password.getValue().length() >= 8;
            password.setErrorMessage(passwordOk ? null : "The password is too short");
            password.setInvalid(!passwordOk);
            if (emailOk && passwordOk) {
                Notification.show("Welcome " + email.getValue());
            }
        });
        add(email, password, signUp);
    }
}
