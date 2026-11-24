package demo;

import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A migrated TestBench test's {@code if (getRunLocallyBrowser() == null)} goes through the Escape Hatch (#621). The
 * demo's Selenium runs configure no {@code vaadin.testing.selenium.browser}, so no browser was picked to run locally,
 * as for a TestBench test without {@code @RunLocally}.
 */
@UnifiedSpringBootTest
class TestBenchEscapeHatchRunLocallyTest {

    @Test
    @DisplayName("A migrated test asks for the browser picked to run locally, and there is none")
    @RequiresTestingTool(TestingTool.SELENIUM)
    void answersNoLocalBrowserWithoutABrowserSetting(UnifiedPage page) {
        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);

        Assertions.assertNull(testBench.getRunLocallyBrowser());
        Assertions.assertEquals("", testBench.getRunLocallyBrowserVersion());
    }
}
