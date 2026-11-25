package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vaadin.testing.unified.ViewportSize;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static Settings settings(Map<String, String> values) {
        return Settings.from(ConfigurationSource.of(values));
    }

    @Test
    void userStepsLogDefaultsToFailed() {
        assertEquals(Settings.UserStepsLog.FAILED, settings(Map.of()).userStepsLog());
        assertEquals(Settings.UserStepsLog.ALL, settings(Map.of(Settings.USER_STEPS_LOG, "all")).userStepsLog());
        assertEquals(Settings.UserStepsLog.NONE, settings(Map.of(Settings.USER_STEPS_LOG, " None ")).userStepsLog());
        assertThrows(IllegalArgumentException.class,
                () -> settings(Map.of(Settings.USER_STEPS_LOG, "some")).userStepsLog());
    }

    @Test
    void viewportDefaultsTo1280By720AndTakesAnOptionalName() {
        assertEquals(new ViewportSize("", 1280, 720), settings(Map.of()).viewport());
        assertEquals(new ViewportSize("", 375, 667), settings(Map.of(Settings.VIEWPORT, "375x667")).viewport());
        assertEquals(new ViewportSize("phone", 375, 667),
                settings(Map.of(Settings.VIEWPORT, " phone:375x667 ")).viewport());
        assertEquals(new ViewportSize("tablet", 768, 1024),
                settings(Map.of(Settings.VIEWPORT, "tablet:768×1024")).viewport());
    }

    @Test
    void aMalformedViewportSaysWhatIsExpected() {
        for (String bad : new String[] { "375", "phone:", "0x667", "375x", "big:axb", "a:b:375x667" }) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> settings(Map.of(Settings.VIEWPORT, bad)).viewport(), bad);
            assertEquals(Settings.VIEWPORT + ": expected [name:]WIDTHxHEIGHT, such as phone:375x667, got " + bad,
                    e.getMessage());
        }
    }

    @Test
    void githubSummaryIsOnUnlessFalse() {
        assertEquals(true, settings(Map.of()).userStepsGithubSummary());
        assertEquals(true, settings(Map.of(Settings.USER_STEPS_GITHUB_SUMMARY, "true")).userStepsGithubSummary());
        assertEquals(false, settings(Map.of(Settings.USER_STEPS_GITHUB_SUMMARY, " FALSE ")).userStepsGithubSummary());
    }

    @Test
    void changedSinceFallsBackToThePullRequestsBase() {
        assertEquals(Optional.empty(), settings(Map.of()).userStepsChangedSince(Map.of()));
        assertEquals(Optional.empty(), settings(Map.of()).userStepsChangedSince(Map.of("GITHUB_BASE_REF", "")));
        assertEquals(Optional.of("origin/main"),
                settings(Map.of()).userStepsChangedSince(Map.of("GITHUB_BASE_REF", "main")));
        assertEquals(Optional.of("HEAD~3"), settings(Map.of(Settings.USER_STEPS_CHANGED_SINCE, " HEAD~3 "))
                .userStepsChangedSince(Map.of("GITHUB_BASE_REF", "main")));
    }
}
