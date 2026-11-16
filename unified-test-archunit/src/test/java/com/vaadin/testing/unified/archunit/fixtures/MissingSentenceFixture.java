package com.vaadin.testing.unified.archunit.fixtures;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Breaks the scenario sentence rule four times. */
@UnifiedUITest
public class MissingSentenceFixture {

    @Test
    void savesName(UnifiedPage page) {
        page.navigate("profile");
    }

    @RepeatedTest(2)
    void savesTwice(UnifiedPage page) {
        page.navigate("profile");
    }

    @Test
    @DisplayName("savesEmail")
    void savesEmail(UnifiedPage page) {
        page.navigate("profile");
    }

    @Nested
    class WhenLoggedIn {
        @Test
        void seesTheDashboard(UnifiedPage page) {
            page.navigate("dashboard");
        }
    }
}
