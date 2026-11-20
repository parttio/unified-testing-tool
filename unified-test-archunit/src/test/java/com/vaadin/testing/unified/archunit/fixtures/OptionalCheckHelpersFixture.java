package com.vaadin.testing.unified.archunit.fixtures;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An optional check ({@code tryUnwrap(..)}) that calls the test class's own helpers. */
@UnifiedUITest
public class OptionalCheckHelpersFixture {

    @Test
    @DisplayName("The chat dock shows a preview where Playwright can tell")
    void checksInAHelper(UnifiedPage page) {
        page.tryUnwrap(Page.class).ifPresent(pw -> previewShown(pw.getByTestId("chat-dock")));
    }

    @Test
    @DisplayName("The chat dock shows a preview in Playwright")
    void callsTheHelperOutsideTheCheck(UnifiedPage page) {
        previewShown(null);
    }

    @Test
    @DisplayName("The chat dock is reloaded in Playwright after the optional check")
    void unwrapsInAHelperOfTheCheck(UnifiedPage page) {
        page.tryUnwrap(Page.class).ifPresent(pw -> reload(page));
    }

    @Test
    @DisplayName("The chat dock shows its preview text where Playwright can tell")
    void checksInAHelperOfAHelper(UnifiedPage page) {
        page.tryUnwrap(Page.class).ifPresent(pw -> previewText(pw.getByTestId("chat-dock")));
    }

    private static String previewText(Locator dock) {
        return previewShown(dock) ? preview(dock).textContent() : "";
    }

    private static Locator preview(Locator dock) {
        return dock.locator(".preview");
    }

    private static void reload(UnifiedPage page) {
        page.unwrap(Page.class).reload();
    }

    private static boolean previewShown(Locator dock) {
        return dock.isVisible();
    }
}
