package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedLogin;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UnifiedLogin signs in through a LoginForm or a LoginOverlay, and reads its state, the same on every Testing
 * Tool (#564). A form that posts (Spring Security's {@code /login}) needs a browser.
 */
@UnifiedSpringBootTest
class LoginTest {

    private static final String USERNAME = SignInFormView.USERNAME;
    private static final String PASSWORD = SignInFormView.PASSWORD;

    private void assertStatus(UnifiedPage page, String status) {
        assertThat(page.findComponent(Span.class).withId("status")).hasText(status);
    }

    // ---------------------------------------------------------------- a LoginForm with a LoginEvent listener

    // --8<-- [start:signsIn]
    @Test
    @DisplayName("The barista signs in through the login form and lands on the home view")
    void signsInThroughTheForm(UnifiedPage page) {
        page.navigate(SignInFormView.class);

        page.find(UnifiedLogin.class).login("barista@vaadin.com", "coffee-beans");

        assertThat(page).isAt(HomeView.class);
    }
    // --8<-- [end:signsIn]

    @Test
    @DisplayName("A wrong password keeps the user on the form, which shows its error")
    void aWrongPasswordShowsTheError(UnifiedPage page) {
        page.navigate(SignInFormView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);
        assertThat(login).hasNoError().isOpen().isEnabled();

        login.login(USERNAME, "tea-leaves");

        assertThat(login).hasError().isEnabled();
        assertTrue(login.isError());
        assertThat(page).isAt(SignInFormView.class);
    }

    @Test
    @DisplayName("After a wrong password, the right one signs in")
    void signsInAfterAWrongPassword(UnifiedPage page) {
        page.navigate(SignInFormView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);
        login.login(USERNAME, "tea-leaves");
        assertThat(login).hasError();

        login.login(USERNAME, PASSWORD);

        assertThat(page).isAt(HomeView.class);
    }

    @Test
    @DisplayName("Pressing “Forgot password” fires the form's forgot password event")
    void forgotPassword(UnifiedPage page) {
        page.navigate(SignInFormView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);
        assertTrue(login.isForgotPasswordButtonVisible());

        login.forgotPassword();

        assertStatus(page, "Password reset requested");
    }

    @Test
    @DisplayName("A form reads as open, without a title or description, on every Testing Tool")
    void readsTheForm(UnifiedPage page) {
        page.navigate(SignInFormView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);

        assertTrue(login.isOpen());
        assertFalse(login.isError());
        assertTrue(login.isEnabled());
        assertNull(login.getTitle());
        assertNull(login.getDescription());
        assertThat(login).isOpen().isVisible();
    }

    // ---------------------------------------------------------------- a LoginOverlay

    @Test
    @DisplayName("The login overlay is open, with its title and description")
    void readsTheOverlay(UnifiedPage page) {
        page.navigate(SignInOverlayView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class).withTitle("Bakery");

        assertThat(login).isOpen().isVisible().hasTitle("Bakery").hasNoError();
        assertTrue(login.isOpen());
        assertEquals("Bakery", login.getTitle());
        assertEquals("Coffee and pastries", login.getDescription());
        assertFalse(login.isForgotPasswordButtonVisible());
    }

    @Test
    @DisplayName("Signing in through the overlay closes it and greets the user")
    void signsInThroughTheOverlay(UnifiedPage page) {
        page.navigate(SignInOverlayView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);

        login.login(USERNAME, PASSWORD);

        assertThat(login).isClosed();
        assertStatus(page, "Welcome, barista@vaadin.com");
    }

    @Test
    @DisplayName("A wrong password keeps the overlay open, showing its error")
    void aWrongPasswordShowsTheOverlaysError(UnifiedPage page) {
        page.navigate(SignInOverlayView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);

        login.login(USERNAME, "tea-leaves");

        assertThat(login).isOpen().hasError();
        assertStatus(page, "Not signed in");
    }

    @Test
    @DisplayName("“Forgot password” fails on an overlay that hides it, without the password in the failure")
    void forgotPasswordFailsWhenHidden(UnifiedPage page) {
        page.navigate(SignInOverlayView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, login::forgotPassword);

        assertTrue(e.getMessage().contains("no “Forgot password” button"), e.getMessage());
        assertTrue(e.getMessage().contains("setForgotPasswordButtonVisible(true)"), e.getMessage());
        assertThat(login).isOpen();
    }

    // ---------------------------------------------------------------- a form that posts (Spring Security)

    @Test
    @RequiresTestingTool(TestingTool.BROWSERLESS)
    @DisplayName("Signing in through a form that posts fails on Browserless, asking for a browser or @WithMockUser")
    void aFormPostNeedsABrowser(UnifiedPage page) {
        page.navigate(LoginView.class);
        UnifiedLogin login = page.find(UnifiedLogin.class);

        UnifiedAssertionError e = assertThrows(UnifiedAssertionError.class, () -> login.login(USERNAME, PASSWORD));

        assertTrue(e.getMessage().contains("posts to “login”"), e.getMessage());
        assertTrue(e.getMessage().contains("can't submit"), e.getMessage());
        assertTrue(e.getMessage().contains("@RequiresBrowser"), e.getMessage());
        assertTrue(e.getMessage().contains("@WithMockUser"), e.getMessage());
        assertFalse(e.getMessage().contains(PASSWORD), e.getMessage());
        assertThat(page).isAt(LoginView.class);
    }

    @Test
    @RequiresBrowser
    @DisplayName("Signing in through Spring Security's form post signs the barista in")
    void signsInThroughAFormPost(UnifiedPage page) {
        page.navigate(LoginView.class);

        page.find(UnifiedLogin.class).login(USERNAME, PASSWORD);

        page.navigate(WhoAmIView.class);
        assertThat(page.findComponent(Span.class).withTestId("user")).hasText("Signed in as barista@vaadin.com");
    }

    @Test
    @RequiresBrowser
    @DisplayName("A wrong password in Spring Security's form post comes back to the login view, with its error")
    void aWrongPasswordInAFormPost(UnifiedPage page) {
        page.navigate(LoginView.class);

        page.find(UnifiedLogin.class).login(USERNAME, "tea-leaves");

        assertThat(page).isAt(LoginView.class);
        assertThat(page.find(UnifiedLogin.class)).hasError();
    }
}
