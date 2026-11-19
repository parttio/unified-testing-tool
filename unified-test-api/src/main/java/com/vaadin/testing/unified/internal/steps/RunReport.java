package com.vaadin.testing.unified.internal.steps;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The run's {@code report.html} (spec chapter 12, "Reports"): {@code summary.md}'s sections in a browser, with each
 * scenario's User Steps as a tree and links to its evidence. One file with its CSS and script inline, so it opens
 * from a downloaded CI artifact; links are relative to the evidence directory, where it is written.
 * <p>
 * In order: the header, "Changed in this PR" when the summary has a base ref, the failed scenarios, then the
 * passing ones, hidden until the reader asks for them (or searches). Under "Changed in this PR" each changed
 * scenario says which changed files it calls from, and, with a base run, shows its User Steps as a diff against it;
 * "Diff against base run" in the controls switches to the full steps.
 *
 * @param summary the run, as {@code summary.md} has it
 * @param evidenceFiles a scenario's evidence files, as paths relative to the evidence directory: its failure
 *        evidence, and its recording ({@code trace.zip}) if it has one
 */
public record RunReport(RunSummary summary, Function<ScenarioResult, List<String>> evidenceFiles) {

    public static final String FILE = "report.html";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    public String html() {
        List<ScenarioResult> scenarios = summary.scenarios();
        ChangedFiles changes = summary.changes();
        List<ScenarioResult> failed = scenarios.stream().filter(ScenarioResult::failed).toList();
        List<ScenarioResult> passed = scenarios.stream().filter(ScenarioResult::passed).toList();

        StringBuilder h = new StringBuilder(16_384);
        h.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>Unified UI Tests · ").append(esc(summary.tool())).append("</title>\n")
                .append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<main>\n");
        header(h, scenarios);
        controls(h, passed.size(), changes != null, summary.base() != null && !summary.base().isEmpty());
        if (changes != null) {
            changed(h, changes);
        }
        h.append("<section id=\"failed\">\n<h2>Failed scenarios</h2>\n");
        if (failed.isEmpty()) {
            h.append("<p class=\"none\">No scenario failed.</p>\n");
        }
        failed.forEach(s -> scenario(h, s, true, true, false));
        h.append("</section>\n");
        for (RunComparison c : summary.comparisons()) {
            h.append("<section class=\"compared\">\n<h2>Compared with ").append(esc(c.otherTool()))
                    .append("</h2>\n<p class=\"lead\">").append(esc(summary.comparedLead(c))).append("</p>\n");
            for (RunComparison.Difference d : c.differences()) {
                h.append("<div class=\"difference\">\n<p><span class=\"name\">").append(esc(d.here().displayName()))
                        .append("</span> <span class=\"class\">").append(esc(d.here().simpleClassName()))
                        .append("</span> <span class=\"badge\">").append(esc(summary.differenceLine(d, c.otherTool())))
                        .append("</span></p>\n");
                d.parting().ifPresent(p -> h.append("<p class=\"why\">Parts at step ").append(p.position())
                        .append(": ").append(partingSide(p.here(), summary.tool())).append(", ")
                        .append(partingSide(p.there(), c.otherTool())).append("</p>\n"));
                ScenarioResult failedOne = d.here().failed() ? d.here() : d.there().failed() ? d.there() : null;
                if (d.endsDifferently() && failedOne != null) {
                    h.append("<p class=\"why\">On ").append(esc(failedOne == d.here() ? summary.tool() : c.otherTool()))
                            .append(": <code>").append(esc(RunSummary.error(failedOne.failure() == null ? Map.of()
                                    : failedOne.failure()))).append("</code></p>\n");
                }
                h.append("</div>\n");
            }
            h.append("</section>\n");
        }
        List<ScenarioResult> unchecked = summary.unchecked();
        if (!unchecked.isEmpty()) {
            h.append("<section id=\"unchecked\">\n<h2>Scenarios without a check</h2>\n<p class=\"lead\">")
                    .append(unchecked.size()).append(unchecked.size() == 1 ? " passing scenario ends"
                            : " passing scenarios end")
                    .append(" on User Steps that nothing checks: no <code>see</code> step follows them, and the")
                    .append(" test reads no value after them.</p>\n");
            unchecked.forEach(s -> scenario(h, s, false, false, false));
            h.append("</section>\n");
        }
        h.append("<section id=\"passing\" hidden>\n<h2>Passing scenarios</h2>\n");
        passed.forEach(s -> scenario(h, s, false, false, false));
        h.append("</section>\n<p id=\"no-match\" class=\"none\" hidden>No scenario matches.</p>\n");
        h.append("</main>\n<script>").append(SCRIPT).append("</script>\n</body>\n</html>\n");
        return h.toString();
    }

    private void header(StringBuilder h, List<ScenarioResult> scenarios) {
        h.append("<header>\n<h1>Unified UI Tests <span class=\"tool\">").append(esc(summary.tool()))
                .append("</span></h1>\n<p class=\"counts\">")
                .append("<span class=\"count passed\">").append(summary.passed()).append(" passed</span>")
                .append("<span class=\"count failed\">").append(summary.failed()).append(" failed</span>")
                .append("<span class=\"count skipped\">").append(summary.skipped()).append(" skipped</span>");
        Optional<Instant> start = scenarios.stream().map(ScenarioResult::startedAt).min(Instant::compareTo);
        Optional<Instant> end = scenarios.stream().map(s -> s.startedAt().plusMillis(s.durationMs()))
                .max(Instant::compareTo);
        if (start.isPresent() && start.get().isAfter(Instant.EPOCH)) {
            h.append("<span class=\"when\">started ").append(TIME.format(start.get()));
            end.ifPresent(e -> h.append(", ").append(duration(Duration.between(start.get(), e).toMillis())));
            h.append("</span>");
        }
        h.append("</p>\n</header>\n");
    }

    private static void controls(StringBuilder h, int passing, boolean changes, boolean baseRun) {
        h.append("<nav class=\"controls\">\n")
                .append("<label><input type=\"checkbox\" id=\"show-passing\"> Show passing (").append(passing)
                .append(")</label>\n");
        if (changes) {
            h.append("<label><input type=\"checkbox\" id=\"changed-only\"> Changed only</label>\n");
        }
        if (baseRun) {
            h.append("<label title=\"Changed scenarios: their User Steps against the base branch's last run, or in ")
                    .append("full\"><input type=\"checkbox\" id=\"show-diff\" checked> Diff against base run</label>\n");
        }
        h.append("<input type=\"search\" id=\"search\" placeholder=\"Search scenarios and steps\" ")
                .append("aria-label=\"Search scenarios and steps\">\n</nav>\n");
    }

    private void changed(StringBuilder h, ChangedFiles changes) {
        List<ScenarioResult> touched = summary.scenarios().stream().filter(changes::touches).toList();
        h.append("<section id=\"changed\">\n<h2>Changed in this PR</h2>\n<p class=\"lead\">");
        if (touched.isEmpty()) {
            h.append("No scenario calls code changed since <code>").append(esc(changes.baseRef()))
                    .append("</code>.</p>\n");
        } else {
            h.append(touched.size()).append(touched.size() == 1 ? " scenario calls" : " scenarios call")
                    .append(" code changed since <code>").append(esc(changes.baseRef())).append("</code>.</p>\n");
        }
        // failed ones unfolded; passing ones a line each, as the reader scrolls past them
        touched.forEach(s -> scenario(h, s, s.failed(), false, true));
        h.append("</section>\n");
    }

    /**
     * One scenario: its title, then its User Steps, and for a failure what went wrong and its evidence.
     *
     * @param open shown unfolded
     * @param failure with the failure's details and evidence
     * @param changedSection under "Changed in this PR": with why, and the diff against the base run
     */
    private void scenario(StringBuilder h, ScenarioResult s, boolean open, boolean failure, boolean changedSection) {
        ChangedFiles changes = summary.changes();
        boolean changed = changes != null && changes.touches(s);
        boolean isNew = changed && changes.isNew(s);
        Optional<StepsDiff> diff = changedSection && !isNew ? summary.diff(s) : Optional.empty();
        boolean showDiff = diff.isPresent() && !diff.get().isEmpty();
        h.append("<details class=\"scenario ").append(esc(s.status() == null ? "" : s.status()))
                .append(showDiff ? " has-diff" : "").append('"')
                .append(" data-changed=\"").append(changed).append('"')
                .append(" data-search=\"").append(esc(searchText(s))).append('"')
                .append(open ? " open" : "").append(">\n<summary><span class=\"mark\" aria-label=\"")
                .append(s.failed() ? "failed" : s.aborted() ? "aborted" : "passed").append("\">")
                .append(s.failed() ? "✘" : s.aborted() ? "–" : "✔").append("</span> <span class=\"name\">")
                .append(esc(s.displayName())).append("</span> <span class=\"class\">")
                .append(esc(s.simpleClassName())).append("</span>");
        if (changed) {
            h.append(" <span class=\"badge ").append(isNew ? "new" : "changed").append("\">")
                    .append(isNew ? "new" : "changed").append("</span>");
        }
        if (diff.isPresent()) {
            StepsDiff d = diff.get();
            h.append(" <span class=\"diffstat\">").append(d.isEmpty() ? "same User Steps as the base run"
                    : "<span class=\"add\">+" + d.added() + "</span> <span class=\"del\">−" + d.removed()
                            + "</span> against the base run").append("</span>");
        } else if (changedSection && !isNew && summary.base() != null && !summary.base().isEmpty()) {
            h.append(" <span class=\"diffstat\">not in the base run</span>");
        }
        Optional<ScenarioResult.Unchecked> unchecked = s.unchecked();
        if (unchecked.isPresent()) {
            h.append(" <span class=\"badge unchecked\">no check</span>");
        }
        if (s.durationMs() > 0) {
            h.append(" <span class=\"dur\">").append(duration(s.durationMs())).append("</span>");
        }
        h.append("</summary>\n");
        if (unchecked.isPresent() && !unchecked.get().all()) {
            h.append("<p class=\"why\">").append(esc(RunSummary.uncheckedLine(unchecked.get(), t -> "“" + t + "”")))
                    .append("</p>\n");
        }
        if (changedSection && changed && !isNew) {
            h.append("<p class=\"why\">Called from changed files: ").append(String.join(", ", summary.why(s).stream()
                    .map(w -> "<code>" + esc(w.file()) + "</code>" + (w.methods().isEmpty() ? ""
                            : " (" + esc(String.join(", ", w.methods())) + ")")).toList())).append("</p>\n");
        }
        if (failure) {
            failure(h, s);
        }
        if (showDiff) {
            h.append("<pre class=\"diff\" aria-label=\"User Steps against the base run\">");
            for (StepsDiff.Line line : diff.get().lines()) {
                String kind = switch (line.kind()) {
                    case ADDED -> "add";
                    case REMOVED -> "del";
                    case SAME -> "same";
                };
                String mark = switch (line.kind()) {
                    case ADDED -> "+ ";
                    case REMOVED -> "- ";
                    case SAME -> "  ";
                };
                h.append("<span class=\"").append(kind).append("\">").append(mark).append(esc(line.text()))
                        .append("</span>");
            }
            h.append("</pre>\n");
        }
        h.append("<ol class=\"steps\">\n");
        if (s.steps().isEmpty()) {
            h.append("<li class=\"gap\">(no User Steps)</li>\n");
        }
        entries(h, s.steps(), evidenceDir(s));
        h.append("</ol>\n<p class=\"files\"><a href=\"").append(href(evidenceDir(s) + UserStepsReport.TEXT_FILE))
                .append("\">user-steps.txt</a> · <a href=\"").append(href(evidenceDir(s) + UserStepsReport.JSON_FILE))
                .append("\">user-steps.json</a>");
        for (String trace : evidenceFiles.apply(s).stream().filter(RunReport::isTrace).toList()) {
            h.append(" · <a href=\"").append(href(trace)).append("\" title=\"Playwright trace: open it at ")
                    .append("trace.playwright.dev, or with Playwright's show-trace\">trace.zip</a>");
        }
        h.append("</p>\n</details>\n");
    }

    private static String partingSide(String line, String tool) {
        return (line == null ? "no more steps" : "<code>" + esc(line) + "</code>") + " on " + esc(tool);
    }

    private void failure(StringBuilder h, ScenarioResult s) {
        Map<String, Object> failure = s.failure() == null ? Map.of() : s.failure();
        h.append("<dl class=\"failure\">\n");
        Optional<Map<String, Object>> step = Optional.ofNullable(ScenarioResult.string(failure, "stepIndex"))
                .flatMap(s::entry);
        if (step.isPresent()) {
            Object number = step.get().get("number");
            row(h, "Failed at", "User Step " + (number == null ? step.get().get("index") : number) + ": "
                    + ScenarioResult.string(step.get(), "text"), false);
        } else {
            row(h, "Failed at", "outside any User Step", false);
        }
        String expected = ScenarioResult.string(failure, "expected");
        if (expected != null) {
            row(h, "Expected", expected, false);
            String found = ScenarioResult.string(failure, "found");
            if (found != null) {
                row(h, "Found", found, false);
            }
        } else {
            row(h, "Error", RunSummary.error(failure), true);
        }
        List<String> nearMisses = failure.get("nearMisses") instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of();
        if (!nearMisses.isEmpty()) {
            row(h, "Nearby", String.join(", ", nearMisses), false);
        }
        String suggestion = ScenarioResult.string(failure, "suggestion");
        if (suggestion != null) {
            row(h, "Did you mean", suggestion, true);
        }
        List<Map<String, Object>> path = ScenarioResult.entries(failure.get("codePath"));
        if (!path.isEmpty()) {
            row(h, "Code", String.join(" ← ", path.reversed().stream().map(RunReport::site).toList()), true);
        }
        h.append("</dl>\n");
        List<String> files = evidenceFiles.apply(s).stream().filter(f -> !isTrace(f)).toList();
        if (!files.isEmpty()) {
            h.append("<div class=\"evidence\">\n");
            for (String file : files) {
                if (file.endsWith(".png")) {
                    h.append("<a href=\"").append(href(file)).append("\"><img src=\"").append(href(file))
                            .append("\" alt=\"Screenshot when ").append(esc(s.displayName()))
                            .append(" failed\" loading=\"lazy\"></a>\n");
                } else {
                    h.append("<p><a href=\"").append(href(file)).append("\">").append(esc(file))
                            .append("</a></p>\n");
                }
            }
            h.append("</div>\n");
        }
    }

    private static void row(StringBuilder h, String label, String value, boolean code) {
        h.append("<dt>").append(label).append("</dt><dd>").append(code ? "<code>" : "").append(esc(value))
                .append(code ? "</code>" : "").append("</dd>\n");
    }

    private static boolean isTrace(String file) {
        return file.endsWith(".zip");
    }

    /** The entries as a tree; {@code dir} is the scenario's evidence directory, for the snapshots' links. */
    private static void entries(StringBuilder h, List<Map<String, Object>> entries, String dir) {
        for (Map<String, Object> entry : entries) {
            String type = ScenarioResult.string(entry, "type");
            boolean failed = "failed".equals(ScenarioResult.string(entry, "status"));
            String window = ScenarioResult.string(entry, "window");
            if ("group".equals(type)) {
                h.append("<li class=\"group").append(failed ? " failed" : "").append("\"><details open><summary>");
                index(h, entry);
                h.append("<span class=\"text\">");
                window(h, window);
                h.append(esc(orEmpty(ScenarioResult.string(entry, "name")))).append("</span>");
                meta(h, entry, null);
                h.append("</summary>\n<ol>\n");
                entries(h, ScenarioResult.entries(entry.get("steps")), dir);
                h.append("</ol></details></li>\n");
            } else if ("gap".equals(type)) {
                h.append("<li class=\"gap\">");
                window(h, window);
                h.append(esc(orEmpty(ScenarioResult.string(entry, "text")))).append("</li>\n");
            } else {
                h.append("<li class=\"step").append(failed ? " failed" : "").append("\">");
                index(h, entry);
                h.append("<span class=\"text\">");
                window(h, window);
                h.append(esc(orEmpty(ScenarioResult.string(entry, "text"))))
                        .append(failed ? " <span class=\"mark\" aria-label=\"failed\">✘</span>" : "")
                        .append("</span>");
                meta(h, entry, dir);
                String error = ScenarioResult.string(entry, "error");
                if (failed && error != null) {
                    h.append("<div class=\"error\">").append(esc(error)).append("</div>");
                }
                h.append("</li>\n");
            }
        }
    }

    private static void index(StringBuilder h, Map<String, Object> entry) {
        h.append("<span class=\"idx\">").append(esc(orEmpty(ScenarioResult.string(entry, "index")))).append("</span>");
    }

    private static void window(StringBuilder h, String window) {
        if (window != null) {
            h.append("<span class=\"window\">").append(esc(window)).append("</span>");
        }
    }

    /** Snapshot link, duration and call site, at the end of the line. */
    private static void meta(StringBuilder h, Map<String, Object> entry, String dir) {
        h.append("<span class=\"meta\">");
        String snapshot = ScenarioResult.string(entry, "snapshot");
        if (snapshot != null && dir != null) {
            boolean image = snapshot.endsWith(".png");
            h.append("<a class=\"snap\" href=\"").append(href(dir + snapshot)).append("\"")
                    .append(image ? " data-image" : "").append(">").append(image ? "screenshot" : "tree")
                    .append("</a>");
        }
        if (entry.get("durationMs") instanceof Number n) {
            h.append("<span class=\"dur\">").append(duration(n.longValue())).append("</span>");
        }
        if (entry.get("callSite") instanceof Map<?, ?> site) {
            @SuppressWarnings("unchecked")
            Map<String, Object> callSite = (Map<String, Object>) site;
            h.append("<span class=\"site\">").append(esc(site(callSite))).append("</span>");
        }
        h.append("</span>");
    }

    /** {@code EditPersonTest.java:13 (editPerson)}. */
    private static String site(Map<String, Object> site) {
        String file = ScenarioResult.string(site, "file");
        String where = file != null ? file : String.valueOf(site.get("className"));
        Object line = site.get("line");
        return where + (line instanceof Number n && n.intValue() >= 0 ? ":" + n : "") + " (" + site.get("method")
                + ")";
    }

    /** What the search box matches: the scenario's name, class and every step's text, in lower case. */
    private static String searchText(ScenarioResult s) {
        return (s.displayName() + " " + s.simpleClassName() + " " + s.stepsText()).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    /** The scenario's evidence directory relative to the report: its last two path segments. */
    private static String evidenceDir(ScenarioResult s) {
        String path = s.evidencePath().replace('\\', '/');
        String[] parts = path.split("/");
        int n = parts.length;
        return n >= 2 ? parts[n - 2] + "/" + parts[n - 1] + "/" : path;
    }

    static String duration(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        if (ms < 60_000) {
            return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
        }
        return (ms / 60_000) + " min " + (ms % 60_000) / 1000 + " s";
    }

    /** A relative link: each character outside the URL-safe set percent-encoded as UTF-8. */
    static String href(String path) {
        StringBuilder sb = new StringBuilder();
        for (byte b : path.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "-._~/$".indexOf(c) >= 0) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xff));
            }
        }
        return sb.toString();
    }

    /** Text in HTML, in an element or a quoted attribute. */
    static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static final String CSS = """
            :root{--bg:#fbfbfa;--surface:#fff;--text:#1d1d1f;--muted:#5f6368;--line:#e3e3e0;--pass:#1a7f37;\
            --fail:#c62828;--fail-bg:#fdecea;--accent:#1f5fbf;--badge:#eef3fb;--code:#f3f3f1}
            @media (prefers-color-scheme: dark){:root{--bg:#141416;--surface:#1c1c1f;--text:#e8e8ea;--muted:#a0a0a8;\
            --line:#2e2e33;--pass:#57c470;--fail:#ff6b6b;--fail-bg:#3a1e1e;--accent:#7aa7ff;--badge:#22304a;--code:#26262b}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,"Segoe UI",\
            Roboto,sans-serif}
            main{max-width:1000px;margin:0 auto;padding:24px 16px 64px}
            h1{font-size:22px;margin:0 0 4px}h1 .tool{color:var(--muted);font-weight:500}
            h2{font-size:17px;margin:32px 0 8px}
            .counts{display:flex;flex-wrap:wrap;gap:6px 16px;margin:0;color:var(--muted)}
            .count{font-weight:600}.count.passed{color:var(--pass)}.count.failed{color:var(--fail)}
            .controls{position:sticky;top:0;z-index:1;display:flex;flex-wrap:wrap;gap:8px 16px;align-items:center;\
            margin:16px -16px 0;padding:10px 16px;background:var(--bg);border-bottom:1px solid var(--line)}
            .controls input[type=search]{flex:1 1 220px;min-width:0;padding:6px 10px;border:1px solid var(--line);\
            border-radius:6px;background:var(--surface);color:var(--text);font:inherit}
            .lead,.none{color:var(--muted)}
            details.scenario{background:var(--surface);border:1px solid var(--line);border-radius:8px;margin:8px 0;\
            padding:0 14px}
            details.scenario.failed{border-left:4px solid var(--fail)}
            details.scenario>summary{cursor:pointer;padding:10px 0;font-weight:600;overflow-wrap:anywhere}
            .mark{font-weight:700}.passed .mark,.scenario.passed>summary .mark{color:var(--pass)}
            .failed>summary .mark,.step.failed .mark{color:var(--fail)}
            .class{color:var(--muted);font-weight:400;font-size:13px}
            .badge{display:inline-block;padding:0 8px;border-radius:10px;background:var(--badge);color:var(--accent);\
            font-size:12px;font-weight:600}
            .dur,.site{color:var(--muted);font-weight:400;font-size:12px}
            .snap{font-size:12px;font-weight:400}
            img.snap-img{grid-column:2/-1;max-width:100%;margin:6px 0;border:1px solid var(--line);border-radius:6px}
            ol.steps,ol.steps ol{list-style:none;margin:0;padding:0}
            ol.steps ol{padding-left:22px;border-left:1px solid var(--line);margin-left:6px}
            ol.steps li{padding:3px 0}
            li.step,li.group>details>summary{display:grid;grid-template-columns:2.6em minmax(0,1fr) auto;\
            column-gap:8px;align-items:baseline}
            li.group>details>summary{cursor:pointer;font-weight:600;list-style:none}
            li.group>details>summary::-webkit-details-marker{display:none}
            .idx{color:var(--muted);font-variant-numeric:tabular-nums;font-weight:400}
            .text{min-width:0;overflow-wrap:anywhere}
            .meta{display:flex;gap:8px;justify-content:flex-end;white-space:nowrap}
            .dur{white-space:nowrap}
            .window{font-size:12px;padding:0 6px;margin-right:6px;border-radius:4px;background:var(--code);\
            color:var(--muted);font-weight:400}
            li.gap{color:var(--muted);font-style:italic}
            li.step.failed{background:var(--fail-bg);border-radius:4px;padding:3px 6px;margin:0 -6px}
            .error{grid-column:2/-1;color:var(--fail);font-size:13px;overflow-wrap:anywhere}
            dl.failure{display:grid;grid-template-columns:max-content 1fr;gap:4px 12px;margin:0 0 12px;padding:10px 12px;\
            background:var(--fail-bg);border-radius:6px}
            dl.failure dt{color:var(--muted)}dl.failure dd{margin:0;min-width:0;overflow-wrap:anywhere}
            code{font:13px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;background:var(--code);\
            padding:1px 4px;border-radius:4px}
            .evidence a{overflow-wrap:anywhere}
            .evidence img{max-width:100%;border:1px solid var(--line);border-radius:6px;margin:4px 0 12px}
            .files{font-size:13px;margin:8px 0 12px}
            .diffstat{color:var(--muted);font-weight:400;font-size:12px}
            .diffstat .add,pre.diff .add{color:var(--pass)}.diffstat .del,pre.diff .del{color:var(--fail)}
            .difference{margin:0 0 12px;padding:8px 12px;border:1px solid var(--line);border-radius:6px}\
            .difference p{margin:0 0 4px}\
            .why{margin:0 0 8px;color:var(--muted);font-size:13px;overflow-wrap:anywhere}
            pre.diff{margin:0 0 8px;padding:8px 10px;background:var(--code);border-radius:6px;overflow-x:auto;\
            font:13px/1.5 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
            pre.diff span{display:block}pre.diff .add{background:color-mix(in srgb,var(--pass) 12%,transparent)}
            pre.diff .del{background:color-mix(in srgb,var(--fail) 12%,transparent)}
            main:not(.full-steps) .has-diff>ol.steps{display:none}main.full-steps pre.diff{display:none}
            a{color:var(--accent)}
            @media (max-width:600px){dl.failure{grid-template-columns:1fr}dl.failure dd{margin-bottom:6px}\
            li.step,li.group>details>summary{grid-template-columns:2.2em minmax(0,1fr)}\
            .meta{grid-column:2;justify-content:flex-start;white-space:normal;flex-wrap:wrap;gap:0 8px}\
            ol.steps ol{padding-left:10px;margin-left:2px}}
            """;

    /** Show passing, changed only, and search; without the script the passing section stays hidden. */
    private static final String SCRIPT = """
            (function(){
              var passing=document.getElementById('show-passing'),changed=document.getElementById('changed-only'),
                  search=document.getElementById('search'),none=document.getElementById('no-match');
              function apply(){
                var q=search.value.trim().toLowerCase(),only=changed&&changed.checked,shown=0;
                document.querySelectorAll('details.scenario').forEach(function(d){
                  var ok=(!q||d.dataset.search.indexOf(q)>=0)&&(!only||d.dataset.changed==='true');
                  d.hidden=!ok;
                  if(ok&&d.closest('section').id!=='passing'){shown++;}
                });
                var p=document.getElementById('passing');
                p.hidden=!(passing.checked||q);
                if(!p.hidden){p.querySelectorAll('details.scenario').forEach(function(d){if(!d.hidden){shown++;}});}
                ['changed','failed'].forEach(function(id){
                  var s=document.getElementById(id);
                  if(s){s.querySelectorAll('.none').forEach(function(n){n.hidden=!!(q||only);});}
                });
                none.hidden=!(q||only)||shown>0;
              }
              [passing,changed].forEach(function(c){if(c){c.addEventListener('change',apply);}});
              // changed scenarios: their diff against the base run, or their full steps
              var diff=document.getElementById('show-diff');
              if(diff){diff.addEventListener('change',function(){
                document.querySelector('main').classList.toggle('full-steps',!diff.checked);});}
              // a step's screenshot opens under the step; a second click closes it
              document.addEventListener('click',function(e){
                var a=e.target.closest('a.snap[data-image]');
                if(!a||e.ctrlKey||e.metaKey||e.shiftKey){return;}
                e.preventDefault();
                var li=a.closest('li'),img=li.querySelector(':scope > img.snap-img');
                if(img){img.remove();return;}
                img=document.createElement('img');
                img.className='snap-img';img.src=a.getAttribute('href');img.alt='The page after this step';
                li.appendChild(img);
              });
              search.addEventListener('input',apply);
            })();
            """;
}
