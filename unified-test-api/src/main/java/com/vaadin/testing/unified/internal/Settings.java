package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.TestingTool;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Unified API's settings, read from the {@link ConfigurationSource} the Test Runner integration supplies.
 * A blank value counts as unset.
 */
public final class Settings {

    public static final String TOOL = "vaadin.testing.tool";
    public static final String BASE_URL = "vaadin.testing.base-url";
    public static final String TIMEOUT = "vaadin.testing.timeout";
    public static final String EVIDENCE_DIR = "vaadin.testing.evidence-dir";

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(ms|s|m)?");

    private final ConfigurationSource source;

    private Settings(ConfigurationSource source) {
        this.source = source;
    }

    public static Settings from(ConfigurationSource source) {
        return new Settings(source);
    }

    public Optional<String> get(String key) {
        return source.get(key).filter(v -> !v.isBlank());
    }

    public TestingTool tool() {
        return get(TOOL).map(TestingTool::parse).orElse(TestingTool.BROWSERLESS);
    }

    public Duration timeout() {
        return get(TIMEOUT).map(Settings::parseDuration).orElse(Duration.ofSeconds(10));
    }

    /** Where failure evidence goes: next to the Surefire reports, so CI uploads it with them. */
    public Path evidenceDir() {
        return Path.of(get(EVIDENCE_DIR).orElse("target/surefire-reports/unified-evidence"));
    }

    static Duration parseDuration(String value) {
        Matcher m = DURATION.matcher(value.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(TIMEOUT + ": expected e.g. 10s, 500ms or 1m, got " + value);
        }
        long amount = Long.parseLong(m.group(1));
        String unit = m.group(2) == null ? "s" : m.group(2);
        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "m" -> Duration.ofMinutes(amount);
            default -> Duration.ofSeconds(amount);
        };
    }
}
