package com.vaadin.testing.unified.archunit.fixtures;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import com.vaadin.testing.unified.component.UnifiedButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reaches Testing Tool code without @RequiresTestingTool, in each way the rule knows (five violations). */
@UnifiedUITest
public class ToolCodeFixture {

    @Test
    @DisplayName("The user clicks Save through Playwright")
    void unwraps(UnifiedPage page) {
        page.find(UnifiedButton.class).withText("Save").unwrap(Locator.class).click();
    }

    @Test
    @DisplayName("A helper reloads the page in Playwright")
    void unwrapsInAHelper(UnifiedPage page) {
        reload(page);
    }

    @Test
    @DisplayName("A helper names a Playwright type")
    void dependsOnPlaywrightInAHelper(UnifiedPage page) {
        page.navigate(selector());
    }

    @Test
    @DisplayName("The Escape Hatch reads the view")
    void entersAnEscapeHatch(UnifiedPage page) {
        BrowserlessFakeEscapeHatch.of(page);
    }

    @Test
    @DisplayName("A lambda reloads the page in Playwright")
    void unwrapsInALambda(UnifiedPage page) {
        Runnable reload = () -> page.unwrap(Page.class).reload();
        reload.run();
    }

    @Test
    @DisplayName("The user saves without leaving the Unified API")
    void staysInTheUnifiedApi(UnifiedPage page) {
        page.find(UnifiedButton.class).withText("Save").click();
    }

    private void reload(UnifiedPage page) {
        reloadIn(page.unwrap(Page.class));
    }

    private static void reloadIn(Page page) {
        page.reload();
    }

    private static String selector() {
        return Locator.class.getSimpleName();
    }
}
