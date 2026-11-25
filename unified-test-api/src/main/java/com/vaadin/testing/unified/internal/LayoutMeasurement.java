package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedLogin;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.internal.steps.JsonReader;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.LayoutScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One run of the layout script (ADR 0011): the page's Layout Findings at its current Viewport, and the Reach of the
 * subject, if one was given. Built by the core from the script's JSON; the script itself is {@link LayoutScript}.
 *
 * @param findings the Layout Findings, in the script's order
 * @param subject the Reach of the subject element; null without one
 */
public record LayoutMeasurement(List<Finding> findings, Reach subject) {

    /** The Wrapper types the script reports on: the Findable View's built-in ones. */
    static final List<Class<?>> TYPES = FindableView.BUILT_IN;

    /** Wrapper types whose components a user doesn't act on: one over another is not an overlap. */
    private static final Set<Class<?>> NOT_INTERACTIVE = Set.of(UnifiedDialog.class, UnifiedConfirmDialog.class,
            UnifiedLogin.class, UnifiedNotification.class, UnifiedMessageList.class);

    /**
     * A Layout Finding.
     *
     * @param kind {@code scroll}, {@code clipped} or {@code overlap}
     * @param text what it says, in the failures' words
     * @param box where it is in the Viewport, in CSS pixels: x, y, width, height; empty when the script gave none
     */
    public record Finding(String kind, String text, List<Double> box) {
        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Whether and how a user can get to a component at the current Viewport.
     *
     * @param reach {@code visible}, {@code after scroll}, {@code clipped}, {@code behind} or {@code hidden}
     * @param by what clips or covers it; null otherwise
     * @param byMatch when what covers it is itself a match: its target and position; null otherwise
     */
    public record Reach(String reach, String by, int[] byMatch) {
        public Reach(String reach, String by) {
            this(reach, by, null);
        }
    }

    /** The findings of one kind. */
    public List<Finding> findings(String kind) {
        return findings.stream().filter(f -> f.kind().equals(kind)).toList();
    }

    /** Measures the run's page now: one round trip, no waiting. Fails without a real browser. */
    public static LayoutMeasurement measure(TestRun run, UnifiedElement subject) {
        run.requireBrowser("Checking the layout");
        List<LayoutScript.Target> targets = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        for (Class<?> type : TYPES) {
            UnifiedComponent annotation = type.getAnnotation(UnifiedComponent.class);
            String tag = annotation == null ? null : Wrappers.tagOf(annotation.value(), annotation.tag());
            if (tag != null && !tag.equals("*")) {
                String css = BrowserQueries.tagSelector(tag);
                targets.add(NOT_INTERACTIVE.contains(type) ? LayoutScript.Target.of(css)
                        : LayoutScript.Target.interactive(css));
                kinds.add(annotation.kind().isBlank() ? StepTarget.kindOf(type) : annotation.kind());
            }
        }
        return parse(run.session().measureLayout(targets, subject), kinds);
    }

    /**
     * The Reach of every match of each target, in document order: one run of the layout script, no waiting. Fails
     * without a real browser.
     */
    @SuppressWarnings("unchecked")
    static List<List<Reach>> matches(TestRun run, List<LayoutScript.Target> targets) {
        run.requireBrowser("Measuring the layout");
        Map<String, Object> root = (Map<String, Object>) JsonReader.parse(run.session().measureLayout(targets, null));
        List<List<Reach>> matches = new ArrayList<>();
        for (Object list : (List<Object>) root.getOrDefault("matches", List.of())) {
            matches.add(((List<Object>) list).stream().map(m -> (Map<String, Object>) m)
                    .map(m -> new Reach(String.valueOf(m.get("reach")), m.get("by") == null ? null
                            : String.valueOf(m.get("by")), m.get("byMatch") instanceof List<?> at && at.size() == 2
                                    ? new int[] { ((Number) at.get(0)).intValue(), ((Number) at.get(1)).intValue() }
                                    : null)).toList());
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    static LayoutMeasurement parse(String json, List<String> kinds) {
        Map<String, Object> root = (Map<String, Object>) JsonReader.parse(json);
        List<Finding> findings = new ArrayList<>();
        for (Object f : (List<Object>) root.getOrDefault("findings", List.of())) {
            Map<String, Object> finding = (Map<String, Object>) f;
            String kind = String.valueOf(finding.get("kind"));
            List<Double> box = finding.get("box") instanceof List<?> b
                    ? b.stream().map(n -> n instanceof Number num ? num.doubleValue() : 0d).toList() : List.of();
            findings.add(new Finding(kind, switch (kind) {
                case "scroll" -> finding.get("container") + " scrolls sideways by " + number(finding.get("by")) + " px";
                case "clipped" -> name(kinds, finding.get("target"), finding.get("index")) + " is clipped by "
                        + finding.get("by");
                case "overlap" -> name(kinds, finding.get("target"), finding.get("index")) + " is overlapped by "
                        + name(kinds, finding.get("byTarget"), finding.get("byIndex"));
                default -> kind;
            }, box));
        }
        Reach subject = null;
        if (root.get("subject") instanceof Map<?, ?> s) {
            subject = new Reach(String.valueOf(s.get("reach")), s.get("by") == null ? null : String.valueOf(s.get("by")));
        }
        return new LayoutMeasurement(List.copyOf(findings), subject);
    }

    /** {@code the 2nd button}: a match by its Wrapper kind and position, until the Findable View names it (#494). */
    private static String name(List<String> kinds, Object target, Object index) {
        int t = (int) number(target);
        int i = (int) number(index);
        String kind = t >= 0 && t < kinds.size() ? kinds.get(t) : "component";
        return "the " + ordinal(i + 1) + " " + kind;
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th"
                : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
        return n + suffix;
    }

    private static long number(Object value) {
        return value instanceof Number n ? Math.round(n.doubleValue()) : 0;
    }
}
