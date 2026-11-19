package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Entry;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Gap;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Group;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Status;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Step;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One test's User Steps as {@code user-steps.txt} (also the CI log) and {@code user-steps.json}, schema version 1
 * (spec chapter 12, "user-steps.json, schema version 1").
 *
 * @param testClass the test class's binary name, e.g. {@code demo.EditPersonTest}
 * @param testMethod the test method's name
 * @param displayName the scenario: {@code @DisplayName}, or JUnit's default
 * @param tool the Testing Tool, lower case
 * @param status "passed", "failed" or "aborted"
 * @param failure what the test threw, or null
 * @param steps the recording, finished
 */
public record UserStepsReport(String testClass, String testMethod, String displayName, String tool, String status,
        Throwable failure, StepRecorder steps) {

    public static final int SCHEMA_VERSION = 1;
    public static final String TEXT_FILE = "user-steps.txt";
    public static final String JSON_FILE = "user-steps.json";

    private static final String PASSED_MARK = "✔";
    private static final String FAILED_MARK = "✘";
    private static final String INDENT = "   ";

    /** Writes both files into the directory, creating it. */
    public List<Path> write(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path text = directory.resolve(TEXT_FILE);
        Path json = directory.resolve(JSON_FILE);
        Files.writeString(text, text(), StandardCharsets.UTF_8);
        Files.writeString(json, json(), StandardCharsets.UTF_8);
        return List.of(text, json);
    }

    // ---------------------------------------------------------------- text

    /**
     * <pre>
     * ✘ EditPersonTest › Editing a person in the dialog updates the grid   playwright · 10.8 s
     *    1  open People
     *    2  Edit Alan Turing
     *       2.1  click row where Name is “Alan Turing” in grid
     *    3  see notification “Saved Alan M. Turing”   ✘
     *    ✘ AssertionError: Expected …
     *      at EditPersonTest.java:13 (editsAPersonInTheDialog)
     * </pre>
     */
    public String text() {
        StringBuilder sb = new StringBuilder();
        sb.append(status.equals("failed") ? FAILED_MARK : status.equals("aborted") ? "–" : PASSED_MARK)
                .append(' ').append(scenario()).append(INDENT).append(tool).append(" · ")
                .append(seconds(steps.durationNanos())).append('\n');
        List<Entry> entries = steps.entries();
        if (entries.isEmpty()) {
            sb.append(INDENT).append("(no User Steps)\n");
        }
        text(sb, entries, 1);
        if (failure != null && status.equals("failed")) {
            sb.append(INDENT).append(FAILED_MARK).append(' ').append(steps.mask(firstLine(failure))).append('\n');
            List<CallSite> codePath = codePath();
            if (!codePath.isEmpty()) {
                sb.append(INDENT).append("  at ").append(String.join(" ← ", codePath.stream().map(String::valueOf).toList()))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private void text(StringBuilder sb, List<Entry> entries, int depth) {
        String indent = indent(depth);
        for (Entry entry : entries) {
            switch (entry) {
                case Step step -> sb.append(indent).append(line(step.index(), step.window(),
                        steps.mask(step.phrase().render()), step.status() == Status.FAILED)).append('\n');
                case Group group -> {
                    sb.append(indent).append(line(group.index(), group.window(), steps.mask(group.name()), false))
                            .append('\n');
                    text(sb, group.children(), depth + 1);
                }
                case Gap gap -> sb.append(indent)
                        .append(line(null, gap.window(), gap.text(), gap.status() == Status.FAILED)).append('\n');
            }
        }
    }

    /**
     * One entry of the list, without its indent: {@code 2.1  [window 2] fill text field “Name” with “Alan”   ✘}.
     * The run summary renders the entries it reads back from {@code user-steps.json} with it, so both read alike.
     *
     * @param index the entry's index, or null for a gap
     * @param window the window, or null for the test's first window
     * @param text the step's text, the group's name or the gap's text, already masked
     * @param failed whether to mark it failed; groups are not marked, their failed entry is
     */
    static String line(String index, String window, String text, boolean failed) {
        return (index == null ? "" : index + "  ") + window(window) + oneLine(text)
                + (failed ? INDENT + FAILED_MARK : "");
    }

    /** The indent of the list at a nesting depth. */
    static String indent(int depth) {
        return INDENT.repeat(depth);
    }

    /** Line breaks in a value or step name would break the list: they read as spaces. */
    private static String oneLine(String text) {
        return text.replaceAll("\\s*\\R\\s*", " ");
    }

    private static String window(String window) {
        return window == null ? "" : "[" + window + "] ";
    }

    /** {@code EditPersonTest › Editing a person in the dialog updates the grid}. */
    private String scenario() {
        String simple = testClass.substring(testClass.lastIndexOf('.') + 1).replace('$', '.');
        return simple + " › " + steps.mask(displayName);
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / 1e9);
    }

    /**
     * {@code AssertionError: first line of the message}; for a Unified failure, its summary ({@code Expected …,
     * found …}): its message's first line is the scenario, which the report already names.
     */
    private static String firstLine(Throwable t) {
        String message = t instanceof UnifiedAssertionError unified ? unified.getSummary() : t.getMessage();
        String line = message == null ? "" : message.lines().findFirst().orElse("");
        line = line.length() > 300 ? line.substring(0, 299) + "…" : line;
        return t.getClass().getSimpleName() + (line.isEmpty() ? "" : ": " + line);
    }

    /**
     * Where the failure happened, outermost first: the step groups' call sites, then the failed User Step's, or
     * the first line of the failure's own stack trace outside the Unified API.
     */
    public List<CallSite> codePath() {
        if (failure == null) {
            return List.of();
        }
        Step failed = steps.stepThatThrew(failure);
        if (failed != null) {
            return failed.codePath();
        }
        List<CallSite> path = new ArrayList<>();
        for (Group group : steps.groupsThatThrew(failure)) {
            if (group.callSite() != null) {
                path.add(group.callSite());
            }
        }
        CallSite.of(failure).ifPresent(path::add);
        return path;
    }

    // ---------------------------------------------------------------- JSON

    public String json() {
        Json json = new Json();
        json.open('{');
        json.field("schemaVersion", SCHEMA_VERSION);
        json.field("testClass", testClass);
        json.field("testMethod", testMethod);
        json.field("displayName", steps.mask(displayName));
        json.field("tool", tool);
        json.field("status", status);
        json.field("startedAt", steps.startedAt().toString());
        json.field("durationMs", steps.durationNanos() / 1_000_000);
        json.field("stepCount", steps.stepCount());
        if (steps.readsBeforeFirstEntry() > 0) {
            json.field("readsBeforeFirstEntry", steps.readsBeforeFirstEntry());
        }
        if (failure != null && status.equals("failed")) {
            json.key("failure").open('{');
            json.field("type", failure.getClass().getName());
            json.field("message", steps.mask(failure.getMessage()));
            Step failed = steps.stepThatThrew(failure);
            if (failed != null) {
                json.field("stepIndex", failed.index());
                json.field("stepNumber", failed.number());
            }
            json.key("codePath").open('[');
            codePath().forEach(site -> callSite(json, site));
            json.close(']');
            if (failure instanceof UnifiedAssertionError unified) {
                json.optional("expected", steps.mask(unified.getExpected()));
                json.optional("found", steps.mask(unified.getFound()));
                if (!unified.getNearMisses().isEmpty()) {
                    json.key("nearMisses").open('[');
                    unified.getNearMisses().forEach(n -> json.value(steps.mask(n)));
                    json.close(']');
                }
                json.optional("suggestion", steps.mask(unified.getSuggestion().orElse(null)));
                json.optional("lookup", unified.getSuggestion().isPresent() ? steps.mask(unified.parts().lookup()) : null);
            }
            json.close('}');
        }
        json.key("steps");
        entries(json, steps.entries());
        json.close('}');
        return json.toString() + "\n";
    }

    private void entries(Json json, List<Entry> entries) {
        json.open('[');
        for (Entry entry : entries) {
            json.open('{');
            switch (entry) {
                case Step step -> {
                    Phrase phrase = step.phrase();
                    json.field("type", "step");
                    json.field("index", step.index());
                    json.field("number", step.number());
                    json.field("text", steps.mask(phrase.render()));
                    json.field("verb", phrase.verb());
                    StepTarget target = phrase.target();
                    if (target != null) {
                        json.field("kind", target.kind());
                        json.optional("name", steps.mask(target.name()));
                        if (!target.filters().isEmpty()) {
                            json.key("filters").open('[');
                            target.filters().forEach(f -> json.value(steps.mask(f)));
                            json.close(']');
                        }
                        if (target.position() > 0) {
                            json.field("position", target.position());
                        }
                        json.optional("scope", steps.mask(target.scope()));
                    }
                    if (!phrase.values().isEmpty()) {
                        json.key("values").open('[');
                        phrase.values().forEach(v -> json.value(steps.mask(v)));
                        json.close(']');
                    }
                    common(json, step.window(), step.status(), step.durationNanos(), step.callSite(), step.error());
                    json.optional("snapshot", step.snapshot());
                    readsAfter(json, step);
                }
                case Group group -> {
                    json.field("type", "group");
                    json.field("index", group.index());
                    json.field("name", steps.mask(group.name()));
                    common(json, group.window(), group.status(), group.durationNanos(), group.callSite(),
                            group.error());
                    readsAfter(json, group);
                    json.key("steps");
                    entries(json, group.children());
                }
                case Gap gap -> {
                    json.field("type", "gap");
                    json.field("text", gap.text());
                    json.field("reason", gap.reason());
                    json.field("count", gap.count());
                    common(json, gap.window(), gap.status(), -1, gap.callSite(), gap.error());
                    readsAfter(json, gap);
                }
            }
            json.close('}');
        }
        json.close(']');
    }

    /** Before a group's {@code steps}, so the file reads in the order things happened. */
    private void readsAfter(Json json, Entry entry) {
        int reads = steps.readsAfter(entry);
        if (reads > 0) {
            json.field("readsAfter", reads);
        }
    }

    private void common(Json json, String window, Status status, long durationNanos, CallSite callSite,
            Throwable error) {
        json.optional("window", window);
        json.field("status", status.name().toLowerCase(Locale.ROOT));
        if (durationNanos >= 0) {
            json.field("durationMs", durationNanos / 1_000_000);
        }
        if (callSite != null) {
            json.key("callSite");
            callSite(json, callSite);
        }
        if (error != null) {
            json.field("error", steps.mask(firstLine(error)));
        }
    }

    private static void callSite(Json json, CallSite site) {
        json.open('{');
        json.optional("file", site.file());
        json.field("line", site.line());
        json.field("className", site.className());
        json.field("method", site.method());
        json.close('}');
    }

    /** A minimal JSON writer, two-space indented. */
    static final class Json {
        private final StringBuilder sb = new StringBuilder();
        private final List<Boolean> first = new ArrayList<>();
        private boolean afterKey;

        Json open(char bracket) {
            beforeValue();
            sb.append(bracket);
            first.add(true);
            return this;
        }

        Json close(char bracket) {
            boolean empty = first.remove(first.size() - 1);
            if (!empty) {
                newline();
            }
            sb.append(bracket);
            return this;
        }

        Json key(String key) {
            beforeValue();
            string(key);
            sb.append(": ");
            afterKey = true;
            return this;
        }

        void field(String key, String value) {
            key(key);
            value(value);
        }

        void field(String key, long value) {
            key(key);
            beforeValue();
            sb.append(value);
        }

        void optional(String key, String value) {
            if (value != null) {
                field(key, value);
            }
        }

        void value(String value) {
            beforeValue();
            if (value == null) {
                sb.append("null");
            } else {
                string(value);
            }
        }

        /** A value read back from JSON, written as it was: an object, an array, a string, a number, a boolean or null. */
        void any(Object value) {
            if (value instanceof Map<?, ?> map) {
                open('{');
                map.forEach((k, v) -> {
                    key(String.valueOf(k));
                    any(v);
                });
                close('}');
            } else if (value instanceof List<?> list) {
                open('[');
                list.forEach(this::any);
                close(']');
            } else if (value instanceof String s) {
                value(s);
            } else {
                beforeValue();
                sb.append(value);
            }
        }

        private void beforeValue() {
            if (afterKey) {
                afterKey = false;
                return;
            }
            if (!first.isEmpty()) {
                int last = first.size() - 1;
                if (!first.get(last)) {
                    sb.append(',');
                }
                first.set(last, false);
                newline();
            }
        }

        private void newline() {
            sb.append('\n').append("  ".repeat(first.size()));
        }

        private void string(String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
