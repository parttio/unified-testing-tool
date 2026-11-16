package com.vaadin.testing.unified;

import java.util.List;
import java.util.Optional;

/**
 * What every Unified failure throws: a lookup that found no component or several, an action on a component that
 * stays disabled or hidden, a retrying assertion that timed out. An {@link AssertionError}, so the Test Runner
 * reports a failure. Its message has fixed labels in a fixed order (spec chapter 12, "Failures"); the parts are
 * also available as getters.
 */
public abstract class UnifiedAssertionError extends AssertionError {
    /** {@code Expected exactly one text field labelled “Email”, found none}. */
    public abstract String getSummary();
    /** The scenario: {@code @DisplayName}, or the test method's name. */
    public abstract String getScenario();
    /** {@code fill text field “Email” with “alan@bletchley.uk”}. */
    public abstract Optional<String> getFailedStep();
    /** The {@code page.step(..)} names the failure passed through, outermost first. */
    public abstract List<String> getStepChain();
    public abstract String getExpected();
    public abstract String getFound();
    /** Closest first: {@code text field “E-mail”}. */
    public abstract List<String> getNearMisses();
    /** {@code find(UnifiedTextField.class).withLabel("E-mail")}, when one Near Miss is clearly best. */
    public abstract Optional<String> getSuggestion();
    public abstract String getTool();
    public abstract List<String> getCodePath();
}
