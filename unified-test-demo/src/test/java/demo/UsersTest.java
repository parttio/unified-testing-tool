package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;

/** Users and windows with Spring Security (spec 11-users.md). */
@UnifiedSpringBootTest
class UsersTest {

    // ---------------------------------------------------------------- both Testing Tools

    @Test
    void anonymousIsSentToLogin(UnifiedPage page) {
        page.navigate(AdminView.class);

        assertThat(page).isAt(LoginView.class);
    }

    // --8<-- [start:openWindowSharesTheSession]
    @Test
    void openWindowSharesTheSession(UnifiedPage page) {
        page.navigate(WhoAmIView.class);
        assertThat(page.findComponent(Span.class).withText("Visits in this session: 1")).isVisible();

        UnifiedPage second = page.openWindow();
        second.navigate(WhoAmIView.class);

        assertThat(second.findComponent(Span.class).withText("Visits in this session: 2")).isVisible();
        assertThat(second.findComponent(Span.class).withText("Anonymous")).isVisible();
    }
    // --8<-- [end:openWindowSharesTheSession]

    // ---------------------------------------------------------------- logged in (browsers: test-only login endpoint)

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    void adminSeesTheAdminArea(UnifiedPage page) {
        page.navigate(AdminView.class);

        assertThat(page).isAt(AdminView.class);
        assertThat(page.findComponent(H2.class).withText("Admin area of alice")).isVisible();
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void userWithoutTheRoleIsKeptOut(UnifiedPage page) {
        page.navigate(AdminView.class);

        assertThat(page.findComponent(H2.class).withTextContaining("Admin area")).doesNotExist();
        page.navigate(WhoAmIView.class);
        assertThat(page.findComponent(Span.class).withText("Signed in as bob")).isVisible();
    }

    @Test
    @WithUserDetails("alice") // from the app's UserDetailsService
    void userFromUserDetailsService(UnifiedPage page) {
        page.navigate(AdminView.class);

        assertThat(page).isAt(AdminView.class);
    }

    /** {@code @WithMockUser} on the class applies to every test in it. */
    @Nested
    @WithMockUser(username = "alice", roles = "ADMIN")
    class AsAlice {

        @Test
        void openAsUserIsAnotherSession(UnifiedPage alice) {
            UnifiedPage bob = alice.openAsUser("bob", "USER");
            alice.navigate(WhoAmIView.class);
            bob.navigate(WhoAmIView.class);

            assertThat(bob.findComponent(Span.class).withText("Signed in as bob")).isVisible();
            assertThat(bob.findComponent(Span.class).withText("Visits in this session: 1")).isVisible();
            // back to alice's window: still alice, own session
            assertThat(alice.findComponent(Span.class).withText("Signed in as alice")).isVisible();
            alice.navigate(AdminView.class);
            assertThat(alice).isAt(AdminView.class);
            bob.navigate(AdminView.class);
            assertThat(bob.findComponent(H2.class).withTextContaining("Admin area")).doesNotExist();
        }

        @Test
        void openWindowKeepsTheUser(UnifiedPage alice) {
            UnifiedPage second = alice.openWindow();
            second.navigate(WhoAmIView.class);

            assertThat(second.findComponent(Span.class).withText("Signed in as alice")).isVisible();
        }
    }
}
