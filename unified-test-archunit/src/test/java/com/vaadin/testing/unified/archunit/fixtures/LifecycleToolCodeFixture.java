package com.vaadin.testing.unified.archunit.fixtures;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Its @BeforeEach leaves the Unified API for every test: the method annotation isn't enough. */
@UnifiedUITest
public class LifecycleToolCodeFixture {

    @BeforeEach
    void openInPlaywright(UnifiedPage page) {
        page.unwrap(Page.class).navigate("http://localhost:8080/profile");
    }

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user opens the profile")
    void opensTheProfile(UnifiedPage page) {
        page.navigate("profile");
    }
}
