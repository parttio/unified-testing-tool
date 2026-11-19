package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * Vaadin's development tools are off in a Unified UI Test's application, so Copilot starts no file watchers for
 * the browser sessions of a run; a test that wants them turns them on.
 */
class DevToolsTest {

    @Nested
    @UnifiedSpringBootTest
    class ByDefault {
        @Autowired
        Environment environment;

        @Test
        @DisplayName("Vaadin's development tools are off in the test's application")
        void developmentToolsAreOff() {
            assertEquals("false", environment.getProperty("vaadin.devmode.devTools.enabled"));
        }
    }

    @Nested
    @UnifiedSpringBootTest(properties = "vaadin.devmode.devTools.enabled=true")
    class WhenTheTestAsksForThem {
        @Autowired
        Environment environment;

        @Test
        @DisplayName("A test that asks for Vaadin's development tools gets them")
        void developmentToolsAreOn() {
            assertEquals("true", environment.getProperty("vaadin.devmode.devTools.enabled"));
        }
    }
}
