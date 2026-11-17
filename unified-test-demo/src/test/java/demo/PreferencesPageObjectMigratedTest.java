package demo;

import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Test;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

/** Golden migration of a page object with no converted twin; its twin is an input of the skill evaluation (#134). */
@UnifiedSpringBootTest
class PreferencesPageObjectMigratedTest {

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    void savesThePreferences(UnifiedPage page) {
        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
        page.navigate(PreferencesView.class);

        // TODO unified: TestBench page object: new PreferencesTestBenchPage(this)
        PreferencesTestBenchPage preferences = new PreferencesTestBenchPage(testBench);
        preferences.height(180);
        preferences.subscribe();
        preferences.save();

        // TODO unified: was waitForFirst(); the lookup fails when several components match
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved: height 180.0, birthday null, newsletter yes, remember yes");
    }
}
