package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTabSheet;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TabSheet; written once, runs on Browserless and Playwright. */
@UnifiedSpringBootTest
class AccountTest {

    @Test
    @DisplayName("Switching to the Security tab lets the user change the password there")
    void switchesTabsAndWorksInside(UnifiedPage page) {
        page.navigate(AccountView.class);
        UnifiedTabSheet tabs = page.find(UnifiedTabSheet.class);
        assertThat(tabs).hasSelectedTab("Profile").hasTabs("Profile", "Security", "Billing");

        tabs.selectTab("Security");

        assertThat(tabs).hasSelectedTab("Security");
        assertThat(page.find(UnifiedNotification.class).withText("Opened Security")).isOpen();
        tabs.content().find(UnifiedButton.class).withText("Change password").click();
        assertThat(page.find(UnifiedNotification.class).withText("Password changed")).isOpen();
    }

    // --8<-- [start:contentFollowsTheSelectedTab]
    @Test
    @DisplayName("The tab sheet's content shows the fields of whichever tab is selected")
    void contentFollowsTheSelectedTab(UnifiedPage page) {
        page.navigate(AccountView.class);
        UnifiedTabSheet tabs = page.find(UnifiedTabSheet.class).withTab("Security");
        Scope content = tabs.content();

        content.find(UnifiedTextField.class).withLabel("Notes").setValue("profile notes");
        assertThat(content.find(UnifiedTextField.class).withLabel("Name")).hasCount(1);

        tabs.selectTab("Security");

        // same Scope object, now the Security tab: one "Notes" field, empty, and no "Name"
        assertThat(content.find(UnifiedTextField.class).withLabel("Notes")).isEmpty();
        assertThat(content.find(UnifiedTextField.class).withLabel("Name")).doesNotExist();
        assertEquals("Security", tabs.getSelectedTab());
    }
    // --8<-- [end:contentFollowsTheSelectedTab]

    @Test
    @DisplayName("Selecting a tab that doesn't exist fails, listing the tabs there are")
    void unknownTabFailsWithTheTabs(UnifiedPage page) {
        page.navigate(AccountView.class);
        UnifiedTabSheet tabs = page.find(UnifiedTabSheet.class);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> tabs.selectTab("Admin"));

        assertTrue(e.getMessage().contains("[Profile, Security, Billing]"), e.getMessage());
    }

    @Test
    @DisplayName("The user can't select the disabled Billing tab")
    void disabledTabCantBeSelected(UnifiedPage page) {
        page.navigate(AccountView.class);
        UnifiedTabSheet tabs = page.find(UnifiedTabSheet.class);

        AssertionError e = assertThrows(AssertionError.class,
                () -> ((UnifiedTabSheet) tabs.withTimeout(Duration.ofMillis(500))).selectTab("Billing"));

        assertTrue(e.getMessage().contains("the tab is disabled"), e.getMessage());
        assertThat(tabs).hasSelectedTab("Profile");
    }
}
