package com.vaadin.testing.unified.archunit.fixtures;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

/** A plain test, not a Unified UI Test: none of the rules apply, whatever it does. */
public class NotUnifiedFixture {

    @Test
    void waitsAndUsesPlaywright() throws InterruptedException {
        Thread.sleep(10);
        Page page = null;
        if (page != null) {
            page.waitForTimeout(10);
        }
    }
}
