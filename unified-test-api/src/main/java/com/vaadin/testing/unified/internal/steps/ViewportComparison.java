package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.ViewportSize;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A Viewport Comparison (ADR 0011): runs of the same scenarios on one Testing Tool at different Viewports, compared
 * with the widest, measurement point by measurement point, from their {@code user-steps.json}.
 *
 * @param tool the Testing Tool
 * @param reference the widest Viewport, which the others are compared with
 * @param sizes every Viewport, widest first
 * @param scenarios one per scenario of the reference run, then those only at other sizes
 */
public record ViewportComparison(String tool, ViewportSize reference, List<ViewportSize> sizes,
        List<Scenario> scenarios) {

    /** What a component's Reach is when a user can get to it: on the reference, it counts as there. */
    private static final Set<String> REACHABLE = Set.of("visible", "after scroll", "behind");

    /** Disclosures Vaadin makes on purpose: a component hidden in one is explained, not lost. */
    private static final Set<String> KNOWN_DISCLOSURES = Set.of("the AppLayout drawer", "the MenuBar overflow");

    /**
     * One scenario at every size.
     *
     * @param status {@code passed}, {@code failed}, {@code aborted}, or {@code missing}, by Viewport
     * @param fixed whether it declares its Viewport ({@code @Viewport}): run at that size everywhere, not compared
     * @param points its measurement points, matched by order as long as the runs take the same User Steps
     * @param partings where the run at a size takes other User Steps than the reference's, by Viewport
     */
    public record Scenario(String displayName, String testClass, String testMethod, Map<String, String> status,
            boolean fixed, List<Point> points, Map<String, RunComparison.Parting> partings) {}

    /**
     * One measurement point.
     *
     * @param number its number in the reference run, from 1
     * @param step the User Step that measured it
     * @param atSize what each Viewport has there, by Viewport; the reference included, without lost components
     */
    public record Point(int number, String step, Map<String, AtSize> atSize) {}

    /**
     * A measurement point at one Viewport.
     *
     * @param findings its Layout Findings, as they read
     * @param lost the components the reference reaches and this size doesn't
     * @param screenshot its screenshot, from the comparison's working directory; null without one
     */
    public record AtSize(List<String> findings, List<Lost> lost, String screenshot) {}

    /**
     * A component the reference reaches, and a Viewport doesn't.
     *
     * @param line its Findable View line: {@code button “Export”}
     * @param why {@code missing}, {@code hidden}, {@code hidden in the AppLayout drawer}, {@code clipped by div#box}
     * @param explained whether Vaadin hides it on purpose, in a disclosure a user opens
     */
    public record Lost(String line, String why, boolean explained) {}

    /** Compares runs at different Viewports of one Testing Tool. */
    public static ViewportComparison of(String tool, List<ScenarioResult> results) {
        Map<String, List<ScenarioResult>> bySize = new LinkedHashMap<>();
        Map<String, ViewportSize> sizeOf = new LinkedHashMap<>();
        for (ScenarioResult r : results) {
            if (r.viewport() != null) {
                bySize.computeIfAbsent(r.viewport().toString(), k -> new ArrayList<>()).add(r);
                sizeOf.putIfAbsent(r.viewport().toString(), r.viewport());
            }
        }
        List<ViewportSize> sizes = sizeOf.values().stream()
                .sorted(Comparator.comparingInt(ViewportSize::width).reversed()
                        .thenComparing(Comparator.comparingInt(ViewportSize::height).reversed()))
                .toList();
        ViewportSize reference = sizes.isEmpty() ? null : sizes.get(0);
        List<Scenario> scenarios = new ArrayList<>();
        if (reference != null) {
            for (ScenarioResult ref : bySize.get(reference.toString())) {
                scenarios.add(scenario(ref, reference, sizes, bySize));
            }
        }
        return new ViewportComparison(tool, reference, sizes, List.copyOf(scenarios));
    }

    private static Scenario scenario(ScenarioResult ref, ViewportSize reference, List<ViewportSize> sizes,
            Map<String, List<ScenarioResult>> bySize) {
        Map<String, String> status = new LinkedHashMap<>();
        Map<String, ScenarioResult> runs = new LinkedHashMap<>();
        Map<String, RunComparison.Parting> partings = new LinkedHashMap<>();
        for (ViewportSize size : sizes) {
            String label = size.toString();
            Optional<ScenarioResult> run = size.equals(reference) ? Optional.of(ref)
                    : new BaseRun(bySize.get(label)).find(ref);
            status.put(label, run.map(ScenarioResult::status).orElse("missing"));
            run.ifPresent(r -> runs.put(label, r));
            if (run.isPresent() && !size.equals(reference)) {
                RunComparison.parting(ref.stepLines(), run.get().stepLines()).ifPresent(p -> partings.put(label, p));
            }
        }
        List<Measured> refPoints = measured(ref);
        List<Point> points = new ArrayList<>();
        for (int k = 0; k < refPoints.size(); k++) {
            Measured at = refPoints.get(k);
            Map<String, AtSize> atSize = new LinkedHashMap<>();
            for (Map.Entry<String, ScenarioResult> run : runs.entrySet()) {
                RunComparison.Parting parting = partings.get(run.getKey());
                List<Measured> theirs = measured(run.getValue());
                if (k >= theirs.size() || (parting != null && Math.max(at.line(), theirs.get(k).line()) >= parting.position() - 1)) {
                    continue; // the runs parted before this point: nothing to compare it with
                }
                Measured there = theirs.get(k);
                atSize.put(run.getKey(), new AtSize(there.findings(), run.getValue() == ref ? List.of()
                        : lost(at.components(), there.components()), there.screenshot() == null ? null
                                : run.getValue().evidencePath() + there.screenshot()));
            }
            points.add(new Point(k + 1, at.step(), atSize));
        }
        return new Scenario(ref.displayName(), ref.testClass(), ref.testMethod(), status, ref.fixedViewport(),
                List.copyOf(points), partings);
    }

    /** The components the reference reaches and the other run doesn't, in the reference's order. */
    private static List<Lost> lost(List<Map<String, Object>> reference, List<Map<String, Object>> other) {
        List<Map<String, Object>> left = new ArrayList<>(other);
        List<Lost> lost = new ArrayList<>();
        for (Map<String, Object> c : reference) {
            if (!REACHABLE.contains(ScenarioResult.string(c, "reach"))) {
                continue;
            }
            String line = ScenarioResult.string(c, "line");
            Optional<Map<String, Object>> same = left.stream().filter(o -> line.equals(ScenarioResult.string(o, "line")))
                    .findFirst();
            if (same.isEmpty()) {
                lost.add(new Lost(line, "missing", false));
                continue;
            }
            left.remove(same.get());
            String reach = ScenarioResult.string(same.get(), "reach");
            String by = ScenarioResult.string(same.get(), "by");
            if ("hidden".equals(reach)) {
                lost.add(new Lost(line, by == null ? "hidden" : "hidden in " + by, by != null
                        && KNOWN_DISCLOSURES.contains(by)));
            } else if ("clipped".equals(reach)) {
                lost.add(new Lost(line, by == null ? "clipped" : "clipped by " + by, false));
            }
        }
        return List.copyOf(lost);
    }

    /** A measured User Step of a run: its line's position in the run's User Steps, from 0, and its layout. */
    private record Measured(int line, String step, List<String> findings, List<Map<String, Object>> components,
            String screenshot) {}

    private static List<Measured> measured(ScenarioResult run) {
        List<Measured> measured = new ArrayList<>();
        int[] line = { 0 };
        collect(run.steps(), measured, line);
        return measured;
    }

    /** In the order {@link ScenarioResult#stepLines()} lists the entries. */
    private static void collect(List<Map<String, Object>> entries, List<Measured> measured, int[] line) {
        for (Map<String, Object> entry : entries) {
            if (entry.get("layout") instanceof Map<?, ?> l) {
                @SuppressWarnings("unchecked")
                Map<String, Object> layout = (Map<String, Object>) l;
                measured.add(new Measured(line[0], ScenarioResult.string(entry, "text"),
                        ScenarioResult.entries(layout.get("findings")).stream()
                                .map(f -> ScenarioResult.string(f, "text")).toList(),
                        ScenarioResult.entries(layout.get("components")),
                        layout.get("screenshot") instanceof String s ? s : null));
            }
            line[0]++;
            if ("group".equals(ScenarioResult.string(entry, "type"))) {
                collect(ScenarioResult.entries(entry.get("steps")), measured, line);
            }
        }
    }
}
