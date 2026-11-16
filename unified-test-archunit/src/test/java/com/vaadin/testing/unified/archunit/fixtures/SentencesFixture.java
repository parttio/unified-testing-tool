package com.vaadin.testing.unified.archunit.fixtures;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import com.vaadin.testing.unified.component.UnifiedButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Follows every rule: each test is a sentence, waits through the Unified API and stays in it. */
@UnifiedUITest
public class SentencesFixture {

    @Test
    @DisplayName("Saving the profile enables the button again")
    void savesProfile(UnifiedPage page) {
        page.navigate("profile");
        UnifiedButton save = page.find(UnifiedButton.class).withText("Save");
        save.click();
        page.waitUntil(save::isEnabled);
        assertThat(save).isEnabled();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Save", "Cancel" })
    @DisplayName("Every action button can be clicked")
    void clicksButtons(String text, UnifiedPage page) {
        page.find(UnifiedButton.class).withText(text).click();
    }

    @Test
    @DisplayName("An extra check runs where the Testing Tool has the type")
    void checksOptionally(UnifiedPage page) {
        // tryUnwrap: an optional extra check, the test runs everywhere (spec 08)
        page.find(UnifiedButton.class).withText("Save")
                .tryUnwrap(com.microsoft.playwright.Locator.class)
                .ifPresent(locator -> locator.click());
    }

    // not a test: no sentence needed
    private void helper() {}
}
