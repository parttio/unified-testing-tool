package com.vaadin.testing.unified.internal.steps;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The run's {@code summary.md} (spec chapter 12, "Reports"), built from the {@code user-steps.json} of the run's
 * tests: a header with the counts, "Changed in this PR" when a base ref is known, the failed scenarios, then the
 * passing ones, folded.
 *
 * @param tool the Testing Tool as shown, {@code Playwright}
 * @param scenarios the run's tests that injected a {@code UnifiedPage}
 * @param disabled how many Unified UI Tests the run skipped without running them; aborted scenarios count as
 *        skipped too
 * @param changes the files changed against the base ref, or null outside a PR
 */
public record RunSummary(String tool, List<ScenarioResult> scenarios, int disabled, ChangedFiles changes) {

    public static final String FILE = "summary.md";

    private static final Comparator<ScenarioResult> ORDER = Comparator.comparing(ScenarioResult::testClass)
            .thenComparing(ScenarioResult::startedAt).thenComparing(ScenarioResult::evidencePath);

    public RunSummary {
        scenarios = scenarios.stream().sorted(ORDER).toList();
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
        List<ScenarioResult> passed = scenarios.stream().filter(ScenarioResult::passed).toList();
        if (!passed.isEmpty()) {
            md.append("\n#### Passing scenarios\n\n");
            for (ScenarioResult s : passed) {
                md.append("<details><summary>").append(html(mark(s) + " " + title(s))).append("</summary>\n\n");
                steps(md, s);
                md.append("\n</details>\n");
            }
        }
        return md.toString();
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
        for (ScenarioResult s : touched) {
            md.append("\n**").append(markdown(mark(s) + " " + title(s))).append("** · ")
                    .append(changes.isNew(s) ? "new" : "changed").append("\n\n");
            steps(md, s);
        }
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

    /** Near Misses of the failed lookup: not in {@code user-steps.json} yet (#238). */
    private void nearMisses(StringBuilder md, ScenarioResult s) {
        // #238 adds the Near Misses to the failure and renders them here, as "- Near Misses: …"
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

    /** {@code AssertionError: first line of the message}. */
    private static String error(Map<String, Object> failure) {
        String type = Optional.ofNullable(ScenarioResult.string(failure, "type")).orElse("failure");
        String simple = type.substring(type.lastIndexOf('.') + 1);
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
