package com.vaadin.testing.unified.archunit.fixtures;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Breaks the no raw waits rule in every way it knows; declares its Playwright and Selenium code. */
@UnifiedUITest
@RequiresTestingTool({ TestingTool.PLAYWRIGHT, TestingTool.SELENIUM })
public class RawWaitsFixture {

    @Test
    @DisplayName("The user waits a second after saving")
    void sleeps(UnifiedPage page) throws InterruptedException {
        Thread.sleep(1000);
    }

    @Test
    @DisplayName("The user waits for Playwright")
    void waitsInPlaywright(UnifiedPage page) {
        Page playwright = page.unwrap(Page.class);
        playwright.waitForTimeout(100);
        playwright.waitForSelector("vaadin-button");
        Locator button = playwright.locator("vaadin-button");
        button.waitFor();
    }

    @Test
    @DisplayName("The user waits for Selenium")
    void waitsInSelenium(UnifiedPage page) {
        new WebDriverWait(page.unwrap(WebDriver.class), Duration.ofSeconds(1)).until(driver -> true);
    }

    @Test
    @DisplayName("The user waits with Awaitility")
    void waitsWithAwaitility(UnifiedPage page) {
        Awaitility.await().until(() -> true);
    }

    @Test
    @DisplayName("The user waits in another thread")
    void sleepsInAnAnonymousClass(UnifiedPage page) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }
}
