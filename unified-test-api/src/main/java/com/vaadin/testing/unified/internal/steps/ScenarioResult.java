package com.vaadin.testing.unified.internal.steps;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One test's {@code user-steps.json}, read back for the run summary.
 *
 * @param testClass binary name, {@code demo.EditPersonTest}
 * @param testMethod the method's name
 * @param displayName the scenario
 * @param tool the Testing Tool, lower case
 * @param status {@code passed}, {@code failed} or {@code aborted}
 * @param startedAt when the test started
 * @param durationMs how long it ran, 0 when unknown
 * @param failure the {@code failure} object, or null
 * @param steps the entries, as read
 * @param readsBeforeFirstEntry how many values the test read before its first entry
 * @param evidencePath the test's evidence directory, as the summary shows it
 * @param viewport the Viewport the test ran at; null without a browser
 * @param fixedViewport whether the test declares its Viewport with {@code @Viewport}
 */
public record ScenarioResult(String testClass, String testMethod, String displayName, String tool, String status,
        Instant startedAt, long durationMs, Map<String, Object> failure, List<Map<String, Object>> steps, int readsBeforeFirstEntry,
        String evidencePath, com.vaadin.testing.unified.ViewportSize viewport, boolean fixedViewport) {

    /**
     * Reads a {@code user-steps.json}.
     *
     * @throws IllegalArgumentException when it isn't JSON, or not schema version 1
     */
    public static ScenarioResult parse(String json, String evidencePath) {
        if (!(JsonReader.parse(json) instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("not a JSON object");
        }
        Map<String, Object> test = cast(root);
        if (!(test.get("schemaVersion") instanceof Number version) || version.intValue() != 1) {
            throw new IllegalArgumentException("schemaVersion is not 1: " + test.get("schemaVersion"));
        }
        String testClass = string(test, "testClass");
        String testMethod = string(test, "testMethod");
        String displayName = Optional.ofNullable(string(test, "displayName")).orElse(testMethod);
        Instant startedAt = Optional.ofNullable(string(test, "startedAt")).map(Instant::parse).orElse(Instant.EPOCH);
        Map<String, Object> failure = test.get("failure") instanceof Map<?, ?> f ? cast(f) : null;
        long durationMs = test.get("durationMs") instanceof Number n ? n.longValue() : 0;
        return new ScenarioResult(testClass, testMethod, displayName, string(test, "tool"), string(test, "status"),
                startedAt, durationMs, failure, entries(test.get("steps")),
                test.get("readsBeforeFirstEntry") instanceof Number n ? n.intValue() : 0, evidencePath,
                viewport(test.get("viewport")), test.get("viewport") instanceof Map<?, ?> v && Boolean.TRUE.equals(v.get("fixed")));
    }

    private static com.vaadin.testing.unified.ViewportSize viewport(Object json) {
        if (json instanceof Map<?, ?> v && v.get("width") instanceof Number w && v.get("height") instanceof Number h) {
            return new com.vaadin.testing.unified.ViewportSize(v.get("name") instanceof String n ? n : "", w.intValue(),
                    h.intValue());
        }
        return null;
    }

    public boolean passed() {
        return "passed".equals(status);
    }

    public boolean failed() {
        return "failed".equals(status);
    }

    public boolean aborted() {
        return "aborted".equals(status);
    }

    /**
     * The User Steps of a passed scenario that no check follows.
     *
     * @param all true when the scenario has no check at all
     * @param steps the steps' texts, in order: every one when {@code all}, else those after the last check
     */
    public record Unchecked(boolean all, List<String> steps) {
    }

    /**
     * What a passed scenario leaves unchecked, as far as its User Steps tell: the steps after its last check, or
     * all of them when it has none. Empty when it ends on a check, and for a failed or aborted scenario.
     * <p>
     * A check is a retrying assertion ({@code see}, {@code see no}). A failed step counts as one, since a passing
     * test that has it expected the failure, and so does a gap that may hold one ({@code waitUntil}, the Escape
     * Hatch). So does a value the test read ({@code readsAfter}): a plain assertion on it is no User Step, but
     * the read says the test looked at the result of the steps before it.
     */
    public Optional<Unchecked> unchecked() {
        if (!passed()) {
            return Optional.empty();
        }
        List<String> pending = new ArrayList<>();
        boolean[] checked = { readsBeforeFirstEntry > 0 };
        unchecked(steps, pending, checked);
        return pending.isEmpty() && checked[0] ? Optional.empty()
                : Optional.of(new Unchecked(!checked[0], List.copyOf(pending)));
    }

    /**
     * Whether the scenario recorded an Escape Hatch gap anywhere, step groups opened. Its checks may then be in
     * code the reports can't see (JUnit or AssertJ assertions on what the Escape Hatch read), so a scenario
     * without a check that has one is counted apart from the others.
     */
    public boolean escapeHatch() {
        return escapeHatch(steps);
    }

    private static boolean escapeHatch(List<Map<String, Object>> entries) {
        for (Map<String, Object> entry : entries) {
            if ("gap".equals(string(entry, "type")) && "Escape Hatch".equals(string(entry, "reason"))
                    || escapeHatch(entries(entry.get("steps")))) {
                return true;
            }
        }
        return false;
    }

    private static void unchecked(List<Map<String, Object>> entries, List<String> pending, boolean[] checked) {
        for (Map<String, Object> entry : entries) {
            String type = string(entry, "type");
            boolean group = "group".equals(type);
            if (!group && isCheck(entry, type)) {
                checked[0] = true;
                pending.clear();
            } else if (!group && !"gap".equals(type)) {
                pending.add(orEmpty(string(entry, "text")));
            }
            if (entry.get("readsAfter") instanceof Number reads && reads.intValue() > 0) {
                checked[0] = true;
                pending.clear();
            }
            if (group) {
                unchecked(entries(entry.get("steps")), pending, checked);
            }
        }
    }

    private static boolean isCheck(Map<String, Object> entry, String type) {
        if ("failed".equals(string(entry, "status"))) {
            return true;
        }
        if ("gap".equals(type)) {
            String reason = string(entry, "reason");
            return "waitUntil".equals(reason) || "Escape Hatch".equals(reason);
        }
        String verb = string(entry, "verb");
        return "see".equals(verb) || "see no".equals(verb);
    }

    /** The test class without its package, nested classes with a dot: {@code UsersTest.AsAlice}. */
    public String simpleClassName() {
        return testClass.substring(testClass.lastIndexOf('.') + 1).replace('$', '.');
    }

    /** Every call site of the scenario's entries, groups included, in order. */
    public List<Map<String, Object>> callSites() {
        List<Map<String, Object>> sites = new ArrayList<>();
        collectCallSites(steps, sites);
        return sites;
    }

    private static void collectCallSites(List<Map<String, Object>> entries, List<Map<String, Object>> sites) {
        for (Map<String, Object> entry : entries) {
            if (entry.get("callSite") instanceof Map<?, ?> site) {
                sites.add(cast(site));
            }
            collectCallSites(entries(entry.get("steps")), sites);
        }
    }

    /** The entry with this index, searched through the groups. */
    public Optional<Map<String, Object>> entry(String index) {
        return entry(steps, index);
    }

    private static Optional<Map<String, Object>> entry(List<Map<String, Object>> entries, String index) {
        for (Map<String, Object> entry : entries) {
            if (index.equals(entry.get("index"))) {
                return Optional.of(entry);
            }
            Optional<Map<String, Object>> inside = entry(entries(entry.get("steps")), index);
            if (inside.isPresent()) {
                return inside;
            }
        }
        return Optional.empty();
    }

    /** The entries as {@code user-steps.txt} lists them, without the header and the failure. */
    public String stepsText() {
        StringBuilder sb = new StringBuilder();
        if (steps.isEmpty()) {
            sb.append("(no User Steps)\n");
        }
        stepsText(sb, steps, 0);
        return sb.toString();
    }

    private static void stepsText(StringBuilder sb, List<Map<String, Object>> entries, int depth) {
        for (Map<String, Object> entry : entries) {
            String type = string(entry, "type");
            boolean failed = "failed".equals(string(entry, "status"));
            String window = string(entry, "window");
            sb.append(UserStepsReport.indent(depth));
            if ("group".equals(type)) {
                sb.append(UserStepsReport.line(string(entry, "index"), window, orEmpty(string(entry, "name")), false))
                        .append('\n');
                stepsText(sb, entries(entry.get("steps")), depth + 1);
            } else {
                sb.append(UserStepsReport.line("gap".equals(type) ? null : string(entry, "index"), window,
                        orEmpty(string(entry, "text")), failed)).append('\n');
            }
        }
    }

    /**
     * The entries as lines without their indices, for comparing two runs: each line indented by its depth, a
     * User Step or gap as its window and text, a step group as its name.
     */
    public List<String> stepLines() {
        List<String> lines = new ArrayList<>();
        stepLines(lines, steps, 0);
        return lines;
    }

    private static void stepLines(List<String> lines, List<Map<String, Object>> entries, int depth) {
        for (Map<String, Object> entry : entries) {
            boolean group = "group".equals(string(entry, "type"));
            lines.add(UserStepsReport.indent(depth)
                    + UserStepsReport.line(null, string(entry, "window"), orEmpty(string(entry, group ? "name" : "text")),
                            false));
            if (group) {
                stepLines(lines, entries(entry.get("steps")), depth + 1);
            }
        }
    }

    /**
     * Where the scenario's files are under the evidence directory, {@code EditPersonTest/editsAPerson} or
     * {@code PersonTest/savesAll[2]}: the same in every run of the same test and invocation.
     */
    public String evidenceKey() {
        String[] parts = evidencePath.replace('\\', '/').replaceAll("/+$", "").split("/");
        return parts.length < 2 ? evidencePath : parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    static String string(Map<String, Object> map, String key) {
        return map.get(key) instanceof String s ? s : null;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    static List<Map<String, Object>> entries(Object value) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> m) {
                    list.add(cast(m));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
