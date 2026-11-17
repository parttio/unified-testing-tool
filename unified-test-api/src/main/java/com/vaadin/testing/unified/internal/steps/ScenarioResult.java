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
 * @param evidencePath the test's evidence directory, as the summary shows it
 */
public record ScenarioResult(String testClass, String testMethod, String displayName, String tool, String status,
        Instant startedAt, long durationMs, Map<String, Object> failure, List<Map<String, Object>> steps, String evidencePath) {

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
                startedAt, durationMs, failure, entries(test.get("steps")), evidencePath);
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
