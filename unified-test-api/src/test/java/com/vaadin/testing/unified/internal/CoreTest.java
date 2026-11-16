package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.query.ComponentQuery;
import com.vaadin.testing.unified.query.Query;
import com.vaadin.testing.unified.spi.Filter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreTest {

    @Test
    void parsesTimeouts() {
        assertEquals(Duration.ofSeconds(10), Settings.parseDuration("10s"));
        assertEquals(Duration.ofSeconds(7), Settings.parseDuration("7"));
        assertEquals(Duration.ofMillis(500), Settings.parseDuration("500ms"));
        assertEquals(Duration.ofMinutes(1), Settings.parseDuration("1m"));
        assertThrows(IllegalArgumentException.class, () -> Settings.parseDuration("soon"));
    }

    @Test
    void readsSettingsFromAConfigurationSource() {
        Settings settings = Settings.from(ConfigurationSource.of(Map.of(
                Settings.TOOL, " Playwright ", Settings.TIMEOUT, "500ms", Settings.EVIDENCE_DIR, "build/evidence")));
        assertEquals(TestingTool.PLAYWRIGHT, settings.tool());
        assertEquals(Duration.ofMillis(500), settings.timeout());
        assertEquals(Path.of("build/evidence"), settings.evidenceDir());
    }

    @Test
    void treatsBlankSettingsAsUnset() {
        Settings settings = Settings.from(ConfigurationSource.of(Map.of(
                Settings.TOOL, " ", Settings.TIMEOUT, "", Settings.BASE_URL, "  ")));
        assertEquals(TestingTool.BROWSERLESS, settings.tool());
        assertEquals(Duration.ofSeconds(10), settings.timeout());
        assertEquals(Path.of("target/surefire-reports/unified-evidence"), settings.evidenceDir());
        assertTrue(settings.get(Settings.BASE_URL).isEmpty());
    }

    @Test
    void rejectsAnInvalidTimeoutSetting() {
        Settings settings = Settings.from(ConfigurationSource.of(Map.of(Settings.TIMEOUT, "soon")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, settings::timeout);
        assertTrue(e.getMessage().contains(Settings.TIMEOUT), e.getMessage());
    }

    @Test
    void parsesTestingTool() {
        assertEquals(TestingTool.PLAYWRIGHT, TestingTool.parse(" Playwright "));
        assertTrue(TestingTool.PLAYWRIGHT.hasRealBrowser());
        assertFalse(TestingTool.BROWSERLESS.hasRealBrowser());
    }

    @Test
    void filtersMatchExactlyByDefault() {
        Filter exact = new Filter(Filter.Kind.LABEL, null, "Name", true);
        assertTrue(exact.matches("  Name "));
        assertFalse(exact.matches("Nickname"));
        assertTrue(new Filter(Filter.Kind.LABEL, null, "Name", false).matches("Nickname"));
        assertEquals("withLabelContaining(\"Name\")", new Filter(Filter.Kind.LABEL, null, "Name", false).toString());
    }

    @Test
    void readsQueryAndWrapperTypesFromGenerics() {
        assertEquals(UnifiedTextField.TextFieldQuery.class,
                Wrappers.typeArgument(UnifiedTextField.class, com.vaadin.testing.unified.Findable.class, 0));
        assertEquals(UnifiedButton.class, Wrappers.typeArgument(UnifiedButton.ButtonQuery.class, Query.class, 0));
        assertEquals(UnifiedGenericComponent.class, Wrappers.typeArgument(ComponentQuery.class, Query.class, 0));
    }

    @Test
    void readsTagFromFlowComponent() {
        assertEquals("vaadin-text-field", Wrappers.tagOf(com.vaadin.flow.component.textfield.TextField.class, ""));
        assertEquals("my-tag", Wrappers.tagOf(com.vaadin.flow.component.textfield.TextField.class, "my-tag"));
    }
}
