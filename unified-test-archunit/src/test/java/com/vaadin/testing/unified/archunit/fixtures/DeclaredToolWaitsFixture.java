package com.vaadin.testing.unified.archunit.fixtures;

import com.microsoft.playwright.Page;
import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Waits with a Testing Tool's own wait API only in code that declares that Testing Tool. */
@UnifiedUITest
public class DeclaredToolWaitsFixture {

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    @DisplayName("The user waits for the button in TestBench")
    void waitsInTestBench(UnifiedPage page) {
        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
        testBench.waitUntil(driver -> true);
        testBench.$(ButtonElement.class).waitForFirst();
    }

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    @DisplayName("The user waits for the button through a helper")
    void waitsInAHelper(UnifiedPage page) {
        waitForTheButton(page);
    }

    private void waitForTheButton(UnifiedPage page) {
        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
        testBench.waitUntil(driver -> true);
        testBench.$(ButtonElement.class).waitForFirst();
    }

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    @DisplayName("The user waits for the button in a lambda")
    void waitsInALambda(UnifiedPage page) {
        Runnable waitForTheButton = () -> TestBenchEscapeHatch.of(page).$(ButtonElement.class).waitForFirst();
        waitForTheButton.run();
    }

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user waits for the button in Playwright")
    void waitsInPlaywright(UnifiedPage page) {
        page.unwrap(Page.class).locator("vaadin-button").waitFor();
    }

    @Nested
    @RequiresTestingTool(TestingTool.SELENIUM)
    class InTestBench {
        @Test
        @DisplayName("The user waits for the button in a TestBench class")
        void waitsInTheClass(UnifiedPage page) {
            TestBenchEscapeHatch.of(page).$(ButtonElement.class).waitForFirst();
        }
    }
}
