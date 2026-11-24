package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedEmailField;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedPasswordField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@code UnifiedEmailField} and {@code UnifiedPasswordField}: text fields, the same on every Testing Tool (#555). */
@UnifiedSpringBootTest
class SignUpTest {

    // --8<-- [start:signsUp]
    @Test
    @DisplayName("The user signs up with an email and a password")
    void signsUp(UnifiedPage page) {
        page.navigate(SignUpView.class);
        UnifiedEmailField email = page.find(UnifiedEmailField.class).withLabel("Email");
        UnifiedPasswordField password = page.find(UnifiedPasswordField.class).withLabel("Password");

        email.setValue("ada@example.com");
        password.setValue("correct horse");
        page.find(UnifiedButton.class).withText("Sign up").click();

        assertThat(page.find(UnifiedNotification.class)).hasText("Welcome ada@example.com");
        assertThat(email).hasValue("ada@example.com").isValid();
        assertThat(password).hasValue("correct horse").isValid();
    }
    // --8<-- [end:signsUp]

    @Test
    @DisplayName("The fields read their label, helper text, placeholder and value")
    void readsTheFields(UnifiedPage page) {
        page.navigate(SignUpView.class);
        UnifiedEmailField email = page.find(UnifiedEmailField.class).withPlaceholder("name@example.com");
        UnifiedPasswordField password = page.find(UnifiedPasswordField.class).withLabel("Password");

        email.setValue("ada@example.com");
        password.setValue("s3cret!");

        assertEquals("Email", email.getLabel());
        assertEquals("We send the confirmation there", email.getHelperText());
        assertEquals("name@example.com", email.getPlaceholder());
        assertEquals("ada@example.com", email.getValue());
        assertTrue(email.isRequired());
        assertEquals("At least 8 characters", password.getHelperText());
        assertEquals("s3cret!", password.getValue());
        assertThat(email).hasLabel("Email").isRequired();
        assertThat(password).hasLabel("Password");
    }

    @Test
    @DisplayName("Signing up with a malformed email and a short password marks both fields invalid")
    void validatesBothFields(UnifiedPage page) {
        page.navigate(SignUpView.class);
        UnifiedEmailField email = page.find(UnifiedEmailField.class).withLabel("Email");
        UnifiedPasswordField password = page.find(UnifiedPasswordField.class).withLabel("Password");

        email.setValue("ada");
        password.setValue("short");
        page.find(UnifiedButton.class).withText("Sign up").click();

        assertThat(email).isInvalid().hasErrorMessage("Enter a valid email address");
        assertThat(password).isInvalid().hasErrorMessage("The password is too short");
        assertTrue(password.isInvalid());
        assertEquals("Enter a valid email address", email.getErrorMessage());
        assertThat(page.find(UnifiedNotification.class)).doesNotExist();
    }

    @Test
    @DisplayName("The user clears both fields")
    void clearsTheFields(UnifiedPage page) {
        page.navigate(SignUpView.class);
        UnifiedEmailField email = page.find(UnifiedEmailField.class).withLabel("Email");
        UnifiedPasswordField password = page.find(UnifiedPasswordField.class).withLabel("Password");
        email.setValue("ada@example.com");
        password.setValue("correct horse");

        email.clear();
        password.clear();

        assertThat(email).isEmpty();
        assertThat(password).isEmpty();
        assertEquals("", password.getValue());
    }

    @Test
    @DisplayName("A failed check on the password field doesn't show the password")
    void failureHidesThePassword(UnifiedPage page) {
        page.navigate(SignUpView.class);
        UnifiedPasswordField password = (UnifiedPasswordField) page.find(UnifiedPasswordField.class)
                .withLabel("Password").withTimeout(Duration.ofMillis(500));
        password.setValue("correct horse");

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class,
                () -> assertThat(password).hasValue("battery staple"));

        assertEquals("password field “Password” to have value “••••”", e.getExpected(), e.getMessage());
        assertFalse(e.getMessage().contains("correct horse"), e.getMessage());
        assertFalse(e.getMessage().contains("battery staple"), e.getMessage());
        assertTrue(e.getMessage().contains("“••••”"), e.getMessage());
    }
}
