package com.vaadin.testing.unified.internal.steps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * This run's scenarios against another run of the same Unified UI Tests on another Testing Tool
 * ({@code vaadin.testing.user-steps.compare-dir}): which scenarios end differently, which take different User
 * Steps, and which ran on one Testing Tool only. A scenario is matched as with the {@link BaseRun}; aborted
 * scenarios count as not run.
 *
 * @param otherTool the other run's Testing Tool as shown, {@code Playwright}
 * @param both how many scenarios ran on both Testing Tools
 * @param differences the scenarios that differ: those that end differently first, then in this run's order
 * @param onlyHere how many of this run's scenarios did not run on the other Testing Tool
 * @param onlyThere how many of the other run's scenarios did not run here
 * @param otherRevisions what the other run tested, as its {@code summary.json} files say: one revision when they
 *        agree, several when the directory mixes runs, none when no file names a commit
 */
public record RunComparison(String otherTool, int both, List<Difference> differences, int onlyHere, int onlyThere,
        List<Revision> otherRevisions) {

    /** Without the other run's revision. */
    public RunComparison(String otherTool, int both, List<Difference> differences, int onlyHere, int onlyThere) {
        this(otherTool, both, differences, onlyHere, onlyThere, List.of());
    }

    /**
     * Whether the other run tested the code {@code here} did: empty when either side names no commit.
     *
     * @param here this run's revision, or null when unknown
     */
    public Optional<Boolean> sameCode(Revision here) {
        if (here == null || otherRevisions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(otherRevisions.size() == 1 && otherRevisions.get(0).equals(here));
    }

    /**
     * One scenario that differs between the two runs.
     *
     * @param here the scenario in this run
     * @param there the same scenario on the other Testing Tool
     * @param parting where their User Steps part; empty when both took the same steps
     */
    public record Difference(ScenarioResult here, ScenarioResult there, Optional<Parting> parting) {

        /** Passed on one Testing Tool and failed on the other. */
        public boolean endsDifferently() {
            return here.failed() != there.failed();
        }
    }

    /**
     * The first User Step two runs of a scenario don't share.
     *
     * @param position 1-based, among the scenario's lines as {@link ScenarioResult#stepLines()} lists them
     * @param here this run's line, without indent; null when this run's steps end before
     * @param there the other run's line; null when its steps end before
     */
    public record Parting(int position, String here, String there) {
    }

    /** How many scenarios pass on one Testing Tool and fail on the other. */
    public long endDifferently() {
        return differences.stream().filter(Difference::endsDifferently).count();
    }

    /** How many scenarios end alike and take different User Steps. */
    public long takeDifferentSteps() {
        return differences.size() - endDifferently();
    }

    /**
     * Compares this run with the scenarios of other runs: one comparison per other Testing Tool, in the tools'
     * alphabetical order. Scenarios of this run's own Testing Tool are left out.
     *
     * @param tool this run's Testing Tool
     * @param here this run's scenarios
     * @param others the scenarios read from the other runs, on any Testing Tool
     */
    public static List<RunComparison> of(String tool, List<ScenarioResult> here, List<ScenarioResult> others) {
        return of(tool, here, others, List.of());
    }

    /**
     * {@link #of(String, List, List)}, with what the other runs tested.
     *
     * @param otherRuns the other runs' {@code summary.json} files, as read
     */
    public static List<RunComparison> of(String tool, List<ScenarioResult> here, List<ScenarioResult> others,
            List<Revision.OfRun> otherRuns) {
        Map<String, List<ScenarioResult>> byTool = new java.util.TreeMap<>();
        for (ScenarioResult s : others) {
            if (s.tool() != null && !s.tool().equalsIgnoreCase(tool) && !s.aborted()) {
                byTool.computeIfAbsent(s.tool().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(s);
            }
        }
        List<ScenarioResult> ran = here.stream().filter(s -> !s.aborted()).toList();
        List<RunComparison> comparisons = new ArrayList<>();
        byTool.forEach((otherTool, scenarios) -> {
            RunComparison c = compare(otherTool, ran, scenarios);
            List<Revision> revisions = otherRuns.stream().filter(r -> otherTool.equalsIgnoreCase(r.tool()))
                    .map(Revision.OfRun::revision).filter(java.util.Objects::nonNull).distinct().toList();
            comparisons.add(new RunComparison(c.otherTool(), c.both(), c.differences(), c.onlyHere(), c.onlyThere(),
                    revisions));
        });
        return comparisons;
    }

    private static RunComparison compare(String otherTool, List<ScenarioResult> here, List<ScenarioResult> there) {
        BaseRun other = new BaseRun(there);
        Map<ScenarioResult, ScenarioResult> matched = new LinkedHashMap<>();
        List<Difference> ending = new ArrayList<>();
        List<Difference> steps = new ArrayList<>();
        int onlyHere = 0;
        for (ScenarioResult s : here) {
            Optional<ScenarioResult> same = other.find(s).filter(o -> !matched.containsValue(o));
            if (same.isEmpty()) {
                onlyHere++;
                continue;
            }
            matched.put(s, same.get());
            Difference difference = new Difference(s, same.get(), parting(s.stepLines(), same.get().stepLines()));
            if (difference.endsDifferently()) {
                ending.add(difference);
            } else if (difference.parting().isPresent()) {
                steps.add(difference);
            }
        }
        ending.addAll(steps);
        String shown = Character.toUpperCase(otherTool.charAt(0)) + otherTool.substring(1);
        return new RunComparison(shown, matched.size(), List.copyOf(ending), onlyHere, there.size() - matched.size());
    }

    /** The first line the two lists don't share, or empty when they are equal. */
    static Optional<Parting> parting(List<String> here, List<String> there) {
        int shared = 0;
        while (shared < here.size() && shared < there.size() && here.get(shared).equals(there.get(shared))) {
            shared++;
        }
        if (shared == here.size() && shared == there.size()) {
            return Optional.empty();
        }
        return Optional.of(new Parting(shared + 1, shared < here.size() ? here.get(shared).strip() : null,
                shared < there.size() ? there.get(shared).strip() : null));
    }
}
