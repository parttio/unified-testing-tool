package com.vaadin.testing.unified.internal.failures;

import java.time.Duration;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

/**
 * The parts of a {@link com.vaadin.testing.unified.UnifiedAssertionError}, filled in two stages: what the failing
 * call knows when it throws (expected, found, Near Misses, suggestion, waited, tool), then what only the test's
 * recording knows (the failed User Step, the step chain, the code path, the scenario, the evidence directory),
 * added by the User Step recorder and the test lifecycle before the Test Runner reports the failure. The message is
 * rendered from these parts every time it is read ({@link FailureMessage}), so it always holds the latest ones.
 */
public final class FailureParts {

    private volatile String scenario;
    private volatile Integer stepNumber;
    private volatile IntSupplier totalSteps;
    private volatile String stepText;
    private volatile List<String> stepChain = List.of();
    private volatile String expected;
    private volatile String found;
    private volatile List<String> nearMisses = List.of();
    private volatile String suggestion;
    private volatile String lookup;
    private volatile Duration waited;
    private volatile String waitedSetting;
    private volatile String tool;
    private volatile List<String> codePath = List.of();
    private volatile String evidenceDir;
    private volatile List<String> evidenceFiles = List.of();
    private volatile UnaryOperator<String> mask = UnaryOperator.identity();

    /** {@code Expected …} and {@code Found …}: what was wanted, and what there was instead (null to leave out). */
    public static FailureParts of(String expected, String found) {
        FailureParts parts = new FailureParts();
        parts.expected = expected;
        parts.found = found;
        return parts;
    }

    // ---------------------------------------------------------------- known when thrown

    public FailureParts nearMisses(List<String> newNearMisses) {
        this.nearMisses = List.copyOf(newNearMisses);
        return this;
    }

    public FailureParts suggestion(String newSuggestion) {
        this.suggestion = newSuggestion;
        return this;
    }

    /**
     * The failed lookup or call as the test wrote it, in the form of the suggestion:
     * {@code find(UnifiedTextField.class).withLabel("E-mail")}. With the suggestion, it says what to change.
     */
    public FailureParts lookup(String failedLookup) {
        this.lookup = failedLookup;
        return this;
    }

    public FailureParts found(String newFound) {
        this.found = newFound;
        return this;
    }

    /** How long the call waited, and where that timeout came from ({@code vaadin.testing.timeout}). */
    public FailureParts waited(Duration duration, String setting) {
        this.waited = duration;
        this.waitedSetting = setting;
        return this;
    }

    /** {@code playwright (chromium)}. */
    public FailureParts tool(String newTool) {
        this.tool = newTool;
        return this;
    }

    // ---------------------------------------------------------------- added by the recording

    /** The failed User Step: its number, the number of User Steps the test recorded (read when shown), its text. */
    public void failedStep(int number, IntSupplier total, String text) {
        this.stepNumber = number;
        this.totalSteps = total;
        this.stepText = text;
    }

    /** The {@code page.step(..)} names the failure passed through, outermost first. */
    public void stepChain(List<String> names) {
        this.stepChain = List.copyOf(names);
    }

    /** Where it happened, outermost first: {@code EditPersonTest.java:13 (editsAPersonInTheDialog)}. */
    public void codePath(List<String> sites) {
        this.codePath = List.copyOf(sites);
    }

    public void scenario(String newScenario) {
        this.scenario = newScenario;
    }

    public void evidenceDir(String newEvidenceDir) {
        this.evidenceDir = newEvidenceDir;
    }

    /** The failure evidence written into the evidence directory: {@code failure.png}, {@code failure.view.txt}. */
    public void evidenceFiles(List<String> names) {
        this.evidenceFiles = List.copyOf(names);
    }

    /** Hides secret values ({@code page.secret(..)}, password fields) wherever the parts are shown. */
    public void mask(UnaryOperator<String> newMask) {
        this.mask = newMask == null ? UnaryOperator.identity() : newMask;
    }

    // ---------------------------------------------------------------- reading, masked

    public String scenario() { return masked(scenario); }
    public Integer stepNumber() { return stepNumber; }
    /** The User Steps the test recorded so far; after the test, all of them. Null when no step failed. */
    public Integer totalSteps() {
        IntSupplier total = totalSteps;
        return total == null ? null : total.getAsInt();
    }
    public String stepText() { return masked(stepText); }
    public List<String> stepChain() { return stepChain.stream().map(this::masked).toList(); }
    public String expected() { return masked(expected); }
    public String found() { return masked(found); }
    public List<String> nearMisses() { return nearMisses.stream().map(this::masked).toList(); }
    public String suggestion() { return masked(suggestion); }
    /** The failed lookup as code, when there is a suggestion to compare it with; else null. */
    public String lookup() { return masked(lookup); }
    public Duration waited() { return waited; }
    public String waitedSetting() { return waitedSetting; }
    public String tool() { return tool; }
    public List<String> codePath() { return codePath; }
    public String evidenceDir() { return evidenceDir; }
    public List<String> evidenceFiles() { return evidenceFiles; }

    /** Whether the recording already added the failed User Step. */
    public boolean hasFailedStep() {
        return stepNumber != null;
    }

    private String masked(String text) {
        return text == null ? null : mask.apply(text);
    }
}
