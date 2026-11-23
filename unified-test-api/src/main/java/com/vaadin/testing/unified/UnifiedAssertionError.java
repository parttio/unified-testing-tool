package com.vaadin.testing.unified;

import com.vaadin.testing.unified.internal.failures.FailureMessage;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * What every Unified failure throws: a lookup that found no component or several, an action on a component that
 * stays disabled or hidden, a retrying assertion that timed out. An {@link AssertionError}, so the Test Runner
 * reports a failure, not an error. The message has fixed labels in a fixed order (spec chapter 12, "Failures"):
 *
 * <pre>
 * Editing a person in the dialog updates the grid
 *   Failed at     User Step 5 of 5: fill text field “Email” with “alan@bletchley.uk”
 *   In step       Edit Alan Turing
 *   Expected      exactly one text field labelled “Email” in dialog “Edit person”
 *   Found         none
 *   Nearby        text field “E-mail”, text field “Name”, combo box “Role”
 *   Did you mean  find(UnifiedTextField.class).withLabel("E-mail")
 *   Waited        10 s (vaadin.testing.timeout)
 *   Tool          playwright (chromium)
 *   Code          EditPersonTest.java:13 (editsAPersonInTheDialog) ← EditPersonTest.java:24 (editPerson)
 *   Evidence      target/surefire-reports/unified-evidence/EditPersonTest/editsAPersonInTheDialog/  (failure.png, failure.view.txt)
 * </pre>
 *
 * The parts are also available as getters. Some are known only after the failure was thrown: the failed User Step
 * and the code path when the User Step ends, the number of User Steps when the test ends. The message is therefore
 * rendered each time it is read, from the parts known then: when the Test Runner reports the failure, after the
 * test, it is complete. A test that catches the failure ({@code assertThrows}) reads it without the total.
 */
public final class UnifiedAssertionError extends AssertionError {

    private static final long serialVersionUID = 1L;

    private final transient FailureParts parts;

    /** For the Unified API and its adapters; tests read the getters. */
    public UnifiedAssertionError(FailureParts parts, Throwable cause) {
        super(FailureMessage.summary(parts), cause);
        this.parts = parts;
    }

    /** The parts, for the Unified API's own reporting. */
    public FailureParts parts() {
        return parts;
    }

    @Override
    public String getMessage() {
        return parts == null ? super.getMessage() : FailureMessage.render(parts);
    }

    /** The failure in one line: {@code Expected exactly one text field labelled “Email”, found none}. */
    public String getSummary() {
        return FailureMessage.summary(parts);
    }

    /** The scenario: {@code @DisplayName}, or the test method's name; null outside a Unified UI Test. */
    public String getScenario() {
        return parts.scenario();
    }

    /** The number of the User Step that failed, from 1. */
    public Optional<Integer> getFailedStepNumber() {
        return Optional.ofNullable(parts.stepNumber());
    }

    /** How many User Steps the test recorded: so far while it runs, all of them once it ended. */
    public Optional<Integer> getStepCount() {
        return Optional.ofNullable(parts.totalSteps());
    }

    /** The failed User Step in words: {@code fill text field “Email” with “alan@bletchley.uk”}. */
    public Optional<String> getFailedStep() {
        return Optional.ofNullable(parts.stepText());
    }

    /** The {@code page.step(..)} names the failure passed through, outermost first. */
    public List<String> getStepChain() {
        return parts.stepChain();
    }

    /** {@code exactly one text field labelled “Email” in dialog “Edit person”}. */
    public String getExpected() {
        return parts.expected();
    }

    /** {@code none}, {@code 2: …}, {@code 1, but disabled}, or the values an assertion saw while waiting. */
    public String getFound() {
        return parts.found();
    }

    /** The Near Misses, closest first: {@code text field “E-mail”}, {@code combo box “Email”}. */
    public List<String> getNearMisses() {
        return parts.nearMisses();
    }

    /** The lookup the test probably meant, when one Near Miss is clearly best. */
    public Optional<String> getSuggestion() {
        return Optional.ofNullable(parts.suggestion());
    }

    /** How long the call waited before it failed. */
    public Optional<Duration> getWaited() {
        return Optional.ofNullable(parts.waited());
    }

    /** The Testing Tool, and its browser: {@code playwright (chromium)}. */
    public String getTool() {
        return parts.tool();
    }

    /** Where it happened, outermost first: {@code EditPersonTest.java:13 (editsAPersonInTheDialog)}. */
    public List<String> getCodePath() {
        return parts.codePath();
    }

    /** The test's evidence directory, where its User Steps and failure evidence are written. */
    public Optional<String> getEvidenceDir() {
        return Optional.ofNullable(parts.evidenceDir());
    }

    /**
     * The failure evidence in the evidence directory, once the test has ended: {@code failure.png} (browsers) or
     * {@code failure.tree.txt} (Browserless), and {@code failure.view.txt}. Empty before.
     */
    public List<String> getEvidenceFiles() {
        return parts.evidenceFiles();
    }
}
