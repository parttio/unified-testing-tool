package com.vaadin.testing.unified.archunit.fixtures;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Waits with TestBench where the code doesn't declare SELENIUM, or sleeps where it does. */
@UnifiedUITest
public class UndeclaredToolWaitsFixture {

    @Test
    @DisplayName("The user waits in TestBench without saying so")
    void waitsUndeclared(UnifiedPage page) {
        TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
        testBench.waitUntil(driver -> true);
        testBench.$(ButtonElement.class).waitForFirst();
    }

    @Test
    @DisplayName("The user waits in TestBench through a helper without saying so")
    void waitsUndeclaredInAHelper(UnifiedPage page) {
        waitForTheButton(page);
    }

    private void waitForTheButton(UnifiedPage page) {
        TestBenchEscapeHatch.of(page).$(ButtonElement.class).waitForFirst();
    }

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user waits in TestBench in a Playwright test")
    void waitsInAnotherTestingTool(UnifiedPage page) {
        TestBenchEscapeHatch.of(page).waitUntil(driver -> true);
    }

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    @DisplayName("The user sleeps in a TestBench test")
    void sleepsInTestBench(UnifiedPage page) throws InterruptedException {
        Thread.sleep(10);
    }

    @Test
    @RequiresTestingTool(TestingTool.SELENIUM)
    @DisplayName("The user waits through a shared helper in TestBench")
    void sharesTheHelperInTestBench(UnifiedPage page) {
        waitUntilSaved(page);
    }

    @Test
    @DisplayName("The user waits through a shared helper without saying so")
    void sharesTheHelperUndeclared(UnifiedPage page) {
        waitUntilSaved(page);
    }

    private void waitUntilSaved(UnifiedPage page) {
        TestBenchEscapeHatch.of(page).waitUntil(driver -> true);
    }
}
