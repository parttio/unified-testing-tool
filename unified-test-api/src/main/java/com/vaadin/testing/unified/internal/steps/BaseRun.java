package com.vaadin.testing.unified.internal.steps;

import java.util.List;
import java.util.Optional;

/**
 * The base branch's last run, read from its {@code user-steps.json}: what "Changed in this PR" compares a changed
 * scenario's User Steps with ({@code vaadin.testing.user-steps.baseline-dir}).
 *
 * @param scenarios the base run's scenarios on this run's Testing Tool; empty when no base run was found
 */
public record BaseRun(List<ScenarioResult> scenarios) {

    /** A base run with nothing in it: the summary says no base run was found. */
    public static final BaseRun NONE = new BaseRun(List.of());

    /** The base run's scenarios that ran on the Testing Tool, lower case: steps differ from tool to tool. */
    public static BaseRun of(List<ScenarioResult> scenarios, String tool) {
        return new BaseRun(scenarios.stream().filter(s -> tool.equalsIgnoreCase(s.tool())).toList());
    }

    public boolean isEmpty() {
        return scenarios.isEmpty();
    }

    /**
     * The same scenario in the base run: the same test class and evidence directory (method, and invocation of a
     * parameterized test), else the only one of the test class with the same display name.
     */
    public Optional<ScenarioResult> find(ScenarioResult scenario) {
        Optional<ScenarioResult> same = scenarios.stream().filter(s -> s.testClass().equals(scenario.testClass())
                && s.evidenceKey().equals(scenario.evidenceKey())).findFirst();
        if (same.isPresent()) {
            return same;
        }
        List<ScenarioResult> named = scenarios.stream().filter(s -> s.testClass().equals(scenario.testClass())
                && s.displayName().equals(scenario.displayName())).toList();
        return named.size() == 1 ? Optional.of(named.get(0)) : Optional.empty();
    }
}
