package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tab whose selection sends the user to another page: selectTab doesn't check tabs that are gone (#554).
 * Browserless doesn't follow {@code Page.setLocation}, hence {@code @RequiresBrowser}.
 */
@UnifiedSpringBootTest
class LeavingTabsTest {

    @Test
    @RequiresBrowser
    @DisplayName("The user opens the Profile tab, which sends them to the profile page")
    void selectsATabThatSendsTheUserToAnotherPage(UnifiedPage page) {
        page.navigate(LeavingTabsView.class);

        page.find(UnifiedTabs.class).selectTab("Profile");

        assertThat(page).isAt(ProfileView.class);
    }
}
