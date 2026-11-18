package com.vaadin.testing.unified.internal.steps;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The run's {@code summary.md} (spec chapter 12, "Reports"), built from the {@code user-steps.json} of the run's
 * tests: a header with the counts, "Changed in this PR" when a base ref is known, the failed scenarios, then the
 * passing scenarios without a check. A changed scenario's User Steps are diffed against the {@link BaseRun} when
 * there is one.
 * Passing scenarios count in the header only: a run's list of them is long to scroll past on the job summary,
 * and each one's User Steps are in its own {@code user-steps.txt}.
 *
 * @param tool the Testing Tool as shown, {@code Playwright}
 * @param scenarios the run's tests that injected a {@code UnifiedPage}
 * @param disabled how many Unified UI Tests the run skipped without running them; aborted scenarios count as
 *        skipped too
 * @param changes the files changed against the base ref, or null outside a PR
 * @param base the base branch's last run, to compare a changed scenario's User Steps with; null when none was
 *        asked for ({@code vaadin.testing.user-steps.baseline-dir} unset)
 */
public record RunSummary(String tool, List<ScenarioResult> scenarios, int disabled, ChangedFiles changes,
        BaseRun base) {

    public static final String FILE = "summary.md";

    private static final Comparator<ScenarioResult> ORDER = Comparator.comparing(ScenarioResult::testClass)
            .thenComparing(ScenarioResult::startedAt).thenComparing(ScenarioResult::evidencePath);

    public RunSummary {
        scenarios = scenarios.stream().sorted(ORDER).toList();
    }

    /** Without a base run: changed scenarios show their User Steps in full. */
    public RunSummary(String tool, List<ScenarioResult> scenarios, int disabled, ChangedFiles changes) {
        this(tool, scenarios, disabled, changes, null);
    }

    public long passed() {
        return scenarios.stream().filter(ScenarioResult::passed).count();
    }

    public long failed() {
        return scenarios.stream().filter(ScenarioResult::failed).count();
    }

    public long skipped() {
        return disabled + scenarios.stream().filter(ScenarioResult::aborted).count();
    }

    public String markdown() {
        StringBuilder md = new StringBuilder();
        md.append("### Unified UI Tests · ").append(tool).append(" · ").append(passed()).append(" passed, ")
                .append(failed()).append(" failed, ").append(skipped()).append(" skipped\n");
        if (changes != null) {
            changed(md);
        }
        List<ScenarioResult> failed = scenarios.stream().filter(ScenarioResult::failed).toList();
        if (!failed.isEmpty()) {
            md.append("\n#### Failed scenarios\n");
            failed.forEach(s -> failed(md, s));
        }
        unchecked(md);
        return md.toString();
    }

    /** At most this many scenarios without a check are listed; the rest are counted. */
    static final int UNCHECKED_LISTED = 10;

    /** The passing scenarios that end without a check ({@link ScenarioResult#unchecked()}). */
    public List<ScenarioResult> unchecked() {
        return scenarios.stream().filter(s -> s.unchecked().isPresent()).toList();
    }

    private void unchecked(StringBuilder md) {
        List<ScenarioResult> unchecked = unchecked();
        if (unchecked.isEmpty()) {
            return;
        }
        md.append("\n#### Scenarios without a check\n\n").append(unchecked.size())
                .append(unchecked.size() == 1 ? " passing scenario ends" : " passing scenarios end")
                .append(" on User Steps that nothing checks: no `see` step follows them. A plain assertion on a")
                .append(" value read from a Wrapper is not a User Step, and does not retry.\n\n");
        for (ScenarioResult s : unchecked.subList(0, Math.min(UNCHECKED_LISTED, unchecked.size()))) {
            md.append("- **").append(markdown(title(s))).append("** · ")
                    .append(uncheckedLine(s.unchecked().orElseThrow(), RunSummary::code)).append('\n');
        }
        if (unchecked.size() > UNCHECKED_LISTED) {
            md.append("- … and ").append(unchecked.size() - UNCHECKED_LISTED).append(" more, in `")
                    .append(RunReport.FILE).append("`\n");
        }
    }

    /** {@code no check}, or {@code no check after: <the last steps>}: at most three steps, then the count. */
    static String uncheckedLine(ScenarioResult.Unchecked unchecked, java.util.function.UnaryOperator<String> step) {
        if (unchecked.all()) {
            return "no check";
        }
        List<String> steps = unchecked.steps();
        String shown = String.join(", ", steps.stream().limit(3).map(step).toList());
        return "no check after: " + shown + (steps.size() > 3 ? " and " + (steps.size() - 3) + " more" : "");
    }

    private void changed(StringBuilder md) {
        md.append("\n#### Changed in this PR\n\n");
        List<ScenarioResult> touched = scenarios.stream().filter(changes::touches).toList();
        if (touched.isEmpty()) {
            md.append("No scenario calls code changed since ").append(code(changes.baseRef())).append(".\n");
            return;
        }
        md.append(touched.size()).append(touched.size() == 1 ? " scenario calls" : " scenarios call")
                .append(" code changed since ").append(code(changes.baseRef())).append(".\n");
        if (base != null && base.isEmpty()) {
            md.append("No base run found to compare their User Steps with.\n");
        }
        for (ScenarioResult s : touched) {
            md.append("\n**").append(markdown(mark(s) + " " + title(s))).append("** · ");
            if (changes.isNew(s)) {
                md.append("new\n\n");
                steps(md, s);
            } else {
                changedSteps(md, s);
            }
        }
    }

    /**
     * A changed scenario: why it is listed, then its User Steps against the base run's when there is one, else in
     * full. A scenario whose steps didn't change gets no steps: the reviewer reads the code change instead.
     */
    private void changedSteps(StringBuilder md, ScenarioResult s) {
        Optional<StepsDiff> diff = diff(s);
        md.append("changed");
        if (diff.isEmpty()) {
            md.append(base == null || base.isEmpty() ? "" : " · not in the base run");
        } else if (diff.get().isEmpty()) {
            md.append(" · same User Steps as the base run");
        } else {
            md.append(" · User Steps +").append(diff.get().added()).append(" −").append(diff.get().removed())
                    .append(" against the base run");
        }
        md.append("\n\nCalled from changed files: ").append(String.join(", ", why(s).stream()
                .map(w -> code(w.file()) + (w.methods().isEmpty() ? "" : " (" + markdown(String.join(", ",
                        w.methods())) + ")")).toList())).append('\n');
        if (diff.isEmpty()) {
            md.append('\n');
            steps(md, s);
        } else if (!diff.get().isEmpty()) {
            String text = diff.get().text();
            String fence = fence(text);
            md.append('\n').append(fence).append("diff\n").append(text).append(fence).append('\n');
        }
    }

    /** A changed file a scenario calls from, and the methods calling from it. */
    public record Why(String file, List<String> methods) {
    }

    /** Why a scenario is under "Changed in this PR": the changed files it calls from. Empty outside a PR. */
    public List<Why> why(ScenarioResult s) {
        return changes == null ? List.of()
                : changes.callersByFile(s).entrySet().stream().map(e -> new Why(e.getKey(), e.getValue())).toList();
    }

    /** How a scenario's User Steps differ from the base run's; empty without a base run or without it in there. */
    public Optional<StepsDiff> diff(ScenarioResult s) {
        return base == null ? Optional.empty() : base.find(s).map(before -> StepsDiff.of(before, s));
    }

    private void failed(StringBuilder md, ScenarioResult s) {
        md.append("\n**").append(markdown(mark(s) + " " + title(s))).append("**\n\n");
        Map<String, Object> failure = s.failure() == null ? Map.of() : s.failure();
        Optional<Map<String, Object>> step = Optional.ofNullable(ScenarioResult.string(failure, "stepIndex"))
                .flatMap(s::entry);
        if (step.isPresent()) {
            Object number = step.get().get("number");
            md.append("- Failed at: User Step ").append(number == null ? step.get().get("index") : number).append(": ")
                    .append(code(ScenarioResult.string(step.get(), "text"))).append('\n');
        } else {
            md.append("- Failed at: outside any User Step").append(codePathEnd(failure)).append('\n');
        }
        md.append("- Error: ").append(code(error(failure))).append('\n');
        nearMisses(md, s);
        md.append("- Evidence: ").append(code(s.evidencePath())).append('\n');
    }

    /** The failed lookup's Near Misses and the suggestion, from the {@code failure} object. */
    private void nearMisses(StringBuilder md, ScenarioResult s) {
        Map<String, Object> failure = s.failure() == null ? Map.of() : s.failure();
        List<String> nearMisses = failure.get("nearMisses") instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
        if (!nearMisses.isEmpty()) {
            md.append("- Nearby: ").append(markdown(String.join(", ", nearMisses))).append('\n');
        }
        String suggestion = ScenarioResult.string(failure, "suggestion");
        if (suggestion != null) {
            md.append("- Did you mean: ").append(code(suggestion)).append('\n');
        }
    }

    /** {@code  (EditPersonTest.java:13)}: the last call site of the failure's code path, if any. */
    private static String codePathEnd(Map<String, Object> failure) {
        List<Map<String, Object>> path = ScenarioResult.entries(failure.get("codePath"));
        if (path.isEmpty()) {
            return "";
        }
        Map<String, Object> site = path.get(path.size() - 1);
        String file = ScenarioResult.string(site, "file");
        String where = file != null ? file : String.valueOf(site.get("className"));
        Object line = site.get("line");
        String at = where + (line instanceof Number n && n.intValue() >= 0 ? ":" + n : "") + " ("
                + site.get("method") + ")";
        return ", " + code(at);
    }

    /**
     * {@code AssertionError: first line of the message}; for a Unified failure, {@code UnifiedAssertionError:
     * Expected …, found …} (the first line of its message is the scenario, already the heading).
     */
    static String error(Map<String, Object> failure) {
        String type = Optional.ofNullable(ScenarioResult.string(failure, "type")).orElse("failure");
        String simple = type.substring(type.lastIndexOf('.') + 1);
        String expected = ScenarioResult.string(failure, "expected");
        if (expected != null) {
            String found = ScenarioResult.string(failure, "found");
            return simple + ": Expected " + expected + (found == null ? "" : ", found " + found);
        }
        String message = ScenarioResult.string(failure, "message");
        String first = message == null ? "" : message.lines().map(String::strip).filter(l -> !l.isEmpty())
                .findFirst().orElse("");
        return first.isEmpty() ? simple : simple + ": " + first;
    }

    private static void steps(StringBuilder md, ScenarioResult s) {
        String text = s.stepsText();
        String fence = fence(text);
        md.append(fence).append("text\n").append(text).append(fence).append('\n');
    }

    private static String mark(ScenarioResult s) {
        return s.failed() ? "✘" : s.aborted() ? "–" : "✔";
    }

    /** The scenario, and its class to tell scenarios of the same name apart. */
    private static String title(ScenarioResult s) {
        return s.displayName() + " (" + s.simpleClassName() + ")";
    }

    /** A code span that holds any text: a fence longer than the longest run of backticks in it. */
    static String code(String text) {
        String oneLine = text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ");
        String ticks = "`".repeat(longestBacktickRun(oneLine) + 1);
        String pad = oneLine.startsWith("`") || oneLine.endsWith("`") ? " " : "";
        return ticks + pad + oneLine + pad + ticks;
    }

    private static String fence(String text) {
        return "`".repeat(Math.max(3, longestBacktickRun(text) + 1));
    }

    private static int longestBacktickRun(String text) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            run = text.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return longest;
    }

    /** Text in Markdown: HTML and Markdown's inline characters escaped. */
    static String markdown(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : html(text).toCharArray()) {
            if ("\\`*_[]#|~".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Text in HTML, e.g. in {@code <summary>}. */
    static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replaceAll("\\s*\\R\\s*", " ");
    }
}
