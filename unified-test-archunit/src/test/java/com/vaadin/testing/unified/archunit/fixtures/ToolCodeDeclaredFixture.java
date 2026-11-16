package com.vaadin.testing.unified.archunit.fixtures;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reaches Testing Tool code only where it says so. */
@UnifiedUITest
public class ToolCodeDeclaredFixture {

    @Test
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @DisplayName("The user reloads the page in Playwright")
    void unwrapsWithTheAnnotation(UnifiedPage page) {
        reload(page);
    }

    @Test
    @DisplayName("The user opens the profile")
    void staysInTheUnifiedApi(UnifiedPage page) {
        page.navigate("profile");
    }

    private void reload(UnifiedPage page) {
        page.unwrap(Page.class).reload();
    }

    @Nested
    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    class InPlaywright {
        @Test
        @DisplayName("The user goes back in the browser")
        void goesBack(UnifiedPage page) {
            page.unwrap(Page.class).goBack();
        }
    }

    @RequiresTestingTool(TestingTool.PLAYWRIGHT)
    @Nested
    class OnlyPlaywright {
        @Nested
        class DeeperStill {
            @Test
            @DisplayName("The user goes forward in the browser")
            void goesForward(UnifiedPage page) {
                page.unwrap(Page.class).goForward();
            }
        }
    }
}
