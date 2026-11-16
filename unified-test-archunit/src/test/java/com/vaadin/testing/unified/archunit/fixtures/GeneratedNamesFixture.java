package com.vaadin.testing.unified.archunit.fixtures;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Sentences generated from the method names, for this class and its nested ones. */
@UnifiedUITest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class GeneratedNamesFixture {

    @Test
    void the_user_opens_the_profile(UnifiedPage page) {
        page.navigate("profile");
    }

    @Nested
    class When_logged_in {
        @Test
        void the_user_sees_the_dashboard(UnifiedPage page) {
            page.navigate("dashboard");
        }
    }
}
