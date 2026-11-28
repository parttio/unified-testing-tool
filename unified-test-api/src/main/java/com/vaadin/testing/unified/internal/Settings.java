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
    public static final String USER_STEPS_SERVER_LOG = "vaadin.testing.user-steps.server-log";
    public static final String USER_STEPS_IGNORED_ERRORS = "vaadin.testing.user-steps.ignored-errors";
    public static final String USER_STEPS_SNAPSHOTS = "vaadin.testing.user-steps.snapshots";
    public static final String VIEWPORT = "vaadin.testing.viewport";
    public static final String LAYOUT_CHECKS = "vaadin.testing.layout-checks";
    public static final String LAYOUT_FINDINGS = "vaadin.testing.layout-findings";
    public static final String LAYOUT_CHECKS_SCREENSHOTS = "vaadin.testing.layout-checks.screenshots";

    /** What a Layout Finding of the automatic layout checks does: shows in the reports, or fails its User Step. */
    public enum LayoutFindings { REPORT, FAIL }

    /** Which tests print their User Steps to stdout, the CI log. */
    public enum UserStepsLog { ALL, FAILED, NONE }

    private static final Pattern VIEWPORT_VALUE = Pattern.compile("(?:([^:]+):)?(\\d+)\\s*[xX×]\\s*(\\d+)");
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

    /**
     * {@code vaadin.testing.viewport}: the Viewport of every test that declares none, {@code [name:]WIDTHxHEIGHT}
     * such as {@code phone:375x667}; 1280 × 720 when unset (ADR 0011).
     */
    public com.vaadin.testing.unified.ViewportSize viewport() {
        return get(VIEWPORT).map(String::trim).map(v -> {
            Matcher m = VIEWPORT_VALUE.matcher(v);
            int width = m.matches() ? Integer.parseInt(m.group(2)) : 0;
            int height = m.matches() ? Integer.parseInt(m.group(3)) : 0;
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException(
                        VIEWPORT + ": expected [name:]WIDTHxHEIGHT, such as phone:375x667, got " + v);
            }
            return new com.vaadin.testing.unified.ViewportSize(m.group(1) == null ? "" : m.group(1).trim(), width, height);
        }).orElse(DEFAULT_VIEWPORT);
    }

    /** The Viewport when neither the test nor the run sets one: the same on every browser Testing Tool. */
    public static final com.vaadin.testing.unified.ViewportSize DEFAULT_VIEWPORT = com.vaadin.testing.unified.ViewportSize.of(1280, 720);

    /**
     * {@code vaadin.testing.layout-checks}: {@code on} measures the layout after every navigation, Viewport change and
     * {@code checkLayout()} in a browser (ADR 0011); {@code off} (the default) measures nothing.
     */
    public boolean layoutChecks() {
        return onOff(LAYOUT_CHECKS);
    }

    /** {@code vaadin.testing.layout-findings}: {@code report} (the default) or {@code fail}. */
    public LayoutFindings layoutFindings() {
        return get(LAYOUT_FINDINGS).map(v -> switch (v.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "report" -> LayoutFindings.REPORT;
            case "fail" -> LayoutFindings.FAIL;
            default -> throw new IllegalArgumentException(LAYOUT_FINDINGS + ": expected report or fail, got " + v);
        }).orElse(LayoutFindings.REPORT);
    }

    /** {@code vaadin.testing.layout-checks.screenshots}: {@code on} keeps a screenshot per measurement; {@code off}. */
    public boolean layoutChecksScreenshots() {
        return onOff(LAYOUT_CHECKS_SCREENSHOTS);
    }

    private boolean onOff(String key) {
        return get(key).map(v -> switch (v.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            default -> throw new IllegalArgumentException(key + ": expected on or off, got " + v);
        }).orElse(false);
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

    /**
     * {@code vaadin.testing.user-steps.server-log}: which of the application's log lines go under the User Step
     * during which they were logged: {@code warn} (the default: warnings and errors), {@code error} or
     * {@code off}. Empty when off.
     */
    public Optional<com.vaadin.testing.unified.internal.logs.ServerLogs.Level> userStepsServerLog() {
        String value = get(USER_STEPS_SERVER_LOG).map(v -> v.trim().toLowerCase(java.util.Locale.ROOT)).orElse("warn");
        return switch (value) {
            case "warn" -> Optional.of(com.vaadin.testing.unified.internal.logs.ServerLogs.Level.WARN);
            case "error" -> Optional.of(com.vaadin.testing.unified.internal.logs.ServerLogs.Level.ERROR);
            case "off" -> Optional.empty();
            default -> throw new IllegalArgumentException(USER_STEPS_SERVER_LOG + ": expected warn, error or off, got "
                    + value);
        };
    }

    /**
     * {@code vaadin.testing.user-steps.ignored-errors}: one Java regex; {@code hasNoErrors()} doesn't count a logged
     * error line whose text, as the reports print it ({@code console ERROR: …}), it finds. The line still shows
     * under its User Step (ADR 0023). Empty when unset.
     */
    public Optional<Pattern> userStepsIgnoredErrors() {
        return get(USER_STEPS_IGNORED_ERRORS).map(value -> {
            try {
                return Pattern.compile(value.strip());
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new IllegalArgumentException(USER_STEPS_IGNORED_ERRORS
                        + ": expected a Java regular expression, got " + value + " (" + e.getDescription() + ")", e);
            }
        });
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
