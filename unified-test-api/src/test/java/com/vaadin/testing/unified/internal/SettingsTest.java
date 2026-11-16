package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
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
}
