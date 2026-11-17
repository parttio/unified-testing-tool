package demo;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/**
 * Golden migration of a JUnit 4 TestBench test that picks its browser with {@code @RunLocally}: the recipe removes
 * it and leaves a "browser setup removed" Migration Marker (#134). Compile-only here, as the demo runs no JUnit 4
 * tests.
 */
// TODO unified: browser setup removed (@RunLocally): configure the Unified Selenium adapter (vaadin.testing.* settings)
@UnifiedSpringBootTest
public class JUnit4RunLocallyMigratedTest {

    public void openProfile(UnifiedPage page) {
        page.navigate(ProfileView.class);
    }

    @Test
    public void savesTheEmail(UnifiedPage page) {
        openProfile(page);
        page.find(UnifiedTextField.class).withLabel("Name").setValue("Carol");
        page.find(UnifiedTextField.class).withLabel("Email").setValue("carol@example.com");
        page.find(UnifiedButton.class).withText("Save").click();

        // TODO unified: was waitForFirst(); the lookup fails when several components match
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Carol");
    }
}
