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
    public static final String USER_STEPS_LOG = "vaadin.testing.user-steps.log";
    public static final String USER_STEPS_GITHUB_SUMMARY = "vaadin.testing.user-steps.github-summary";
    public static final String USER_STEPS_CHANGED_SINCE = "vaadin.testing.user-steps.changed-since";
    public static final String USER_STEPS_BASELINE_DIR = "vaadin.testing.user-steps.baseline-dir";
    public static final String USER_STEPS_COMPARE_DIR = "vaadin.testing.user-steps.compare-dir";
    public static final String USER_STEPS_SNAPSHOTS = "vaadin.testing.user-steps.snapshots";

    /** Which tests print their User Steps to stdout, the CI log. */
    public enum UserStepsLog { ALL, FAILED, NONE }

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

    /** {@code vaadin.testing.user-steps.log}: {@code failed} (default), {@code all} or {@code none}. */
    public UserStepsLog userStepsLog() {
        return get(USER_STEPS_LOG).map(v -> {
            try {
                return UserStepsLog.valueOf(v.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(USER_STEPS_LOG + ": expected all, failed or none, got " + v);
            }
        }).orElse(UserStepsLog.FAILED);
    }

    /**
     * {@code vaadin.testing.user-steps.github-summary}: whether {@code summary.md} goes into the GitHub job summary
     * when {@code GITHUB_STEP_SUMMARY} is set. Anything but {@code false} means yes.
     */
    public boolean userStepsGithubSummary() {
        return get(USER_STEPS_GITHUB_SUMMARY).map(v -> !v.trim().equalsIgnoreCase("false")).orElse(true);
    }

    /**
     * {@code vaadin.testing.user-steps.snapshots}: whether every User Step leaves a snapshot of the page in the
     * test's evidence directory ({@code steps/}). Off unless {@code true}.
     */
    public boolean userStepsSnapshots() {
        return get(USER_STEPS_SNAPSHOTS).map(v -> v.trim().equalsIgnoreCase("true")).orElse(false);
    }

    /**
     * The base ref of "Changed in this PR": {@code vaadin.testing.user-steps.changed-since}, else
     * {@code origin/$GITHUB_BASE_REF} when GitHub Actions builds a pull request, else none.
     */
    public Optional<String> userStepsChangedSince(java.util.Map<String, String> env) {
        return get(USER_STEPS_CHANGED_SINCE).map(String::trim).or(() -> Optional
                .ofNullable(env.get("GITHUB_BASE_REF")).filter(v -> !v.isBlank()).map(v -> "origin/" + v.trim()));
    }

    /**
     * {@code vaadin.testing.user-steps.baseline-dir}: the evidence of the base branch's last run, such as its
     * downloaded CI artifact. "Changed in this PR" compares each changed scenario's User Steps with it.
     */
    public Optional<Path> userStepsBaselineDir() {
        return get(USER_STEPS_BASELINE_DIR).map(v -> Path.of(v.trim()));
    }

    /**
     * {@code vaadin.testing.user-steps.compare-dir}: the evidence of a run of the same tests on another Testing
     * Tool. The run summary says which scenarios differ between the two.
     */
    public Optional<Path> userStepsCompareDir() {
        return get(USER_STEPS_COMPARE_DIR).map(v -> Path.of(v.trim()));
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
