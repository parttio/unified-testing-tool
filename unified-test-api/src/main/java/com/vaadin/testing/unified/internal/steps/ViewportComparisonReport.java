package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.ViewportSize;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a {@link ViewportComparison} writes (ADR 0011, #495): Markdown for the console and the GitHub job summary,
 * {@code viewport-comparison.json} for agents, and {@code viewport-comparison.html} for people.
 */
public final class ViewportComparisonReport {
    private ViewportComparisonReport() {}

    public static final String JSON_FILE = "viewport-comparison.json";
    public static final String HTML_FILE = "viewport-comparison.html";

    /** The matrix, then each scenario's findings and lost components that need review. */
    public static String markdown(ViewportComparison c) {
        StringBuilder md = new StringBuilder("### Unified UI Tests · Viewports compared on ")
                .append(shown(c.tool())).append("\n\n");
        int findings = 0;
        int review = 0;
        int failing = 0;
        for (ViewportComparison.Scenario s : c.scenarios()) {
            failing += s.status().values().stream().anyMatch(v -> v.equals("failed")) ? 1 : 0;
            for (ViewportComparison.Point p : s.points()) {
                for (ViewportComparison.AtSize at : p.atSize().values()) {
                    findings += at.findings().size();
                    review += (int) at.lost().stream().filter(l -> !l.explained()).count();
                }
            }
        }
        md.append("Reference: ").append(c.reference()).append(". ").append(c.scenarios().size())
                .append(c.scenarios().size() == 1 ? " scenario" : " scenarios").append(" compared; ")
                .append(failing).append(" failing at some size; ").append(findings)
                .append(findings == 1 ? " Layout Finding; " : " Layout Findings; ").append(review)
                .append(review == 1 ? " lost component needs review.\n\n" : " lost components need review.\n\n");
        md.append("| Scenario |");
        c.sizes().forEach(v -> md.append(' ').append(v).append(" |"));
        md.append("\n|---|").append("---|".repeat(c.sizes().size())).append('\n');
        for (ViewportComparison.Scenario s : c.scenarios()) {
            md.append("| ").append(name(s)).append(" |");
            for (ViewportSize v : c.sizes()) {
                md.append(' ').append(cell(s, v.toString(), v.equals(c.reference()))).append(" |");
            }
            md.append('\n');
        }
        if (!c.fixed().isEmpty()) {
            md.append("\nNot compared, fixed Viewport: ").append(String.join(", ", c.fixed().stream()
                    .map(s -> name(s) + " at " + String.join("", s.status().keySet())).toList())).append(".\n");
        }
        for (ViewportComparison.Scenario s : c.scenarios()) {
            List<String> lines = new ArrayList<>();
            for (ViewportComparison.Point p : s.points()) {
                for (Map.Entry<String, ViewportComparison.AtSize> at : p.atSize().entrySet()) {
                    String where = "- " + at.getKey() + ", point " + p.number() + " (" + p.step() + "): ";
                    at.getValue().findings().forEach(f -> lines.add(where + "⚠ " + f));
                    at.getValue().lost().stream().filter(l -> !l.explained())
                            .forEach(l -> lines.add(where + "lost " + l.line() + ", needs review"));
                }
            }
            s.partings().forEach((size, parting) -> lines.add("- " + size + " parts at step " + parting.position() + ": `"
                    + (parting.there() == null ? "no more steps" : parting.there().strip()) + "`"));
            if (!lines.isEmpty()) {
                md.append("\n**").append(name(s)).append("**\n\n");
                lines.forEach(l -> md.append(l).append('\n'));
            }
        }
        return md.toString();
    }

    /** {@code ✔}, {@code ✘ fails · 2 ⚠ · 1 lost}: a scenario at one Viewport. */
    static String cell(ViewportComparison.Scenario s, String size, boolean reference) {
        String status = s.status().getOrDefault(size, "missing");
        int findings = 0;
        int lost = 0;
        for (ViewportComparison.Point p : s.points()) {
            ViewportComparison.AtSize at = p.atSize().get(size);
            if (at != null) {
                findings += at.findings().size();
                lost += (int) at.lost().stream().filter(l -> !l.explained()).count();
            }
        }
        List<String> parts = new ArrayList<>();
        parts.add(switch (status) {
            case "failed" -> "✘ fails";
            case "missing" -> "– not run";
            case "aborted" -> "– skipped";
            default -> "✔";
        });
        if (findings > 0) {
            parts.add(findings + " ⚠");
        }
        if (lost > 0) {
            parts.add(lost + " lost");
        }
        if (s.partings().containsKey(size)) {
            parts.add("⑂ parts at step " + s.partings().get(size).position());
        }
        return String.join(" · ", parts);
    }

    static String name(ViewportComparison.Scenario s) {
        String cls = s.testClass() == null ? "" : s.testClass().substring(s.testClass().lastIndexOf('.') + 1);
        return s.displayName() + (cls.isEmpty() ? "" : " (" + cls + ")");
    }

    private static String shown(String tool) {
        return tool == null || tool.isEmpty() ? "" : Character.toUpperCase(tool.charAt(0)) + tool.substring(1);
    }

    /** The comparison as JSON: for agents, and the page's source of truth. */
    public static String json(ViewportComparison c, Path outDir) {
        UserStepsReport.Json json = new UserStepsReport.Json();
        json.open('{');
        json.field("schemaVersion", 1);
        json.field("tool", c.tool());
        json.field("reference", c.reference().toString());
        json.key("sizes").open('[');
        c.sizes().forEach(v -> json.value(v.toString()));
        json.close(']');
        json.key("scenarios").open('[');
        for (ViewportComparison.Scenario s : c.scenarios()) {
            scenario(json, s, outDir);
        }
        json.close(']');
        json.key("fixed").open('[');
        for (ViewportComparison.Scenario s : c.fixed()) {
            scenario(json, s, outDir);
        }
        json.close(']');
        json.close('}');
        return json.toString();
    }

    private static void scenario(UserStepsReport.Json json, ViewportComparison.Scenario s, Path outDir) {
        json.open('{');
        json.field("displayName", s.displayName());
        json.field("testClass", s.testClass());
        json.field("testMethod", s.testMethod());
        json.key("status").open('{');
        s.status().forEach(json::field);
        json.close('}');
        json.key("partings").open('{');
        s.partings().forEach((size, p) -> {
            json.key(size).open('{');
            json.field("position", p.position());
            json.optional("reference", p.here() == null ? null : p.here().strip());
            json.optional("here", p.there() == null ? null : p.there().strip());
            json.close('}');
        });
        json.close('}');
        json.key("points").open('[');
        for (ViewportComparison.Point p : s.points()) {
            json.open('{');
            json.field("point", p.number());
            json.field("step", p.step());
            json.key("atSize").open('{');
            p.atSize().forEach((size, at) -> {
                json.key(size).open('{');
                json.key("findings").open('[');
                at.findings().forEach(json::value);
                json.close(']');
                json.key("lost").open('[');
                for (ViewportComparison.Lost l : at.lost()) {
                    json.open('{');
                    json.field("line", l.line());
                    json.field("why", l.why());
                    json.bool("review", !l.explained());
                    json.close('}');
                }
                json.close(']');
                json.optional("screenshot", link(at.screenshot(), outDir));
                json.close('}');
            });
            json.close('}');
            json.close('}');
        }
        json.close(']');
        json.close('}');
    }

    /** A screenshot as the page in {@code outDir} links it: relative to it, with forward slashes. */
    static String link(String screenshot, Path outDir) {
        if (screenshot == null) {
            return null;
        }
        try {
            return outDir.toAbsolutePath().normalize().relativize(Path.of(screenshot).toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return screenshot;
        }
    }

    /** The page people review: the matrix, a detail panel per cell, the User Steps behind a toggle. */
    public static String html(ViewportComparison c, Path outDir) {
        StringBuilder h = new StringBuilder(16_384);
        h.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>Viewport Comparison · ").append(RunReport.esc(shown(c.tool()))).append("</title>\n<style>")
                .append(CSS).append("</style>\n</head>\n<body>\n<main>\n");
        h.append("<h1>Viewport Comparison <span class=\"tool\">").append(RunReport.esc(shown(c.tool())))
                .append("</span></h1>\n<p class=\"lead\">Each size against the widest, ")
                .append(RunReport.esc(String.valueOf(c.reference()))).append(". Click a cell for that size.</p>\n");
        h.append("<table class=\"matrix\">\n<thead><tr><th>Scenario</th>");
        for (ViewportSize v : c.sizes()) {
            h.append("<th>").append(RunReport.esc(v.toString())).append(v.equals(c.reference()) ? " · reference" : "")
                    .append("</th>");
        }
        h.append("</tr></thead>\n<tbody>\n");
        for (int i = 0; i < c.scenarios().size(); i++) {
            ViewportComparison.Scenario s = c.scenarios().get(i);
            h.append("<tr><td>").append(RunReport.esc(name(s))).append("</td>");
            for (int j = 0; j < c.sizes().size(); j++) {
                h.append("<td class=\"cell\" data-detail=\"s").append(i).append('-').append(j).append("\">")
                        .append(pills(s, c.sizes().get(j).toString())).append("</td>");
            }
            h.append("</tr>\n");
        }
        h.append("</tbody>\n</table>\n");
        if (!c.fixed().isEmpty()) {
            h.append("<p class=\"lead\">Not compared, fixed Viewport: ").append(RunReport.esc(String.join(", ",
                    c.fixed().stream().map(s -> name(s) + " at " + String.join("", s.status().keySet())).toList())))
                    .append("</p>\n");
        }
        for (int i = 0; i < c.scenarios().size(); i++) {
            for (int j = 0; j < c.sizes().size(); j++) {
                detail(h, c, c.scenarios().get(i), c.sizes().get(j).toString(), "s" + i + "-" + j, outDir);
            }
        }
        h.append("</main>\n<script>").append(SCRIPT).append("</script>\n</body>\n</html>\n");
        return h.toString();
    }

    /** A cell's pills: status, findings, components lost, where the runs part. */
    private static String pills(ViewportComparison.Scenario s, String size) {
        StringBuilder h = new StringBuilder();
        String status = s.status().getOrDefault(size, "missing");
        h.append(switch (status) {
            case "failed" -> "<span class=\"pill x\">✘ fails</span>";
            case "missing" -> "<span class=\"pill n\">– not run</span>";
            case "aborted" -> "<span class=\"pill n\">– skipped</span>";
            default -> "";
        });
        int findings = 0;
        int lost = 0;
        for (ViewportComparison.Point p : s.points()) {
            ViewportComparison.AtSize at = p.atSize().get(size);
            if (at != null) {
                findings += at.findings().size();
                lost += (int) at.lost().stream().filter(l -> !l.explained()).count();
            }
        }
        if (findings > 0) {
            h.append("<span class=\"pill f\">").append(findings).append(" ⚠</span>");
        }
        if (lost > 0) {
            h.append("<span class=\"pill l\">").append(lost).append(" lost</span>");
        }
        if (s.partings().containsKey(size)) {
            h.append("<span class=\"pill n\">⑂ parts at step ").append(s.partings().get(size).position())
                    .append("</span>");
        }
        return h.length() == 0 ? "<span class=\"pill ok\">✔</span>" : h.toString();
    }

    private static void detail(StringBuilder h, ViewportComparison c, ViewportComparison.Scenario s, String size,
            String id, Path outDir) {
        String reference = c.reference().toString();
        h.append("<section class=\"detail\" id=\"").append(id).append("\" hidden>\n<h2>")
                .append(RunReport.esc(name(s))).append(" · ").append(RunReport.esc(size))
                .append(size.equals(reference) ? "" : " against " + RunReport.esc(reference)).append("</h2>\n");
        for (ViewportComparison.Point p : s.points()) {
            ViewportComparison.AtSize at = p.atSize().get(size);
            if (at == null) {
                continue;
            }
            h.append("<p class=\"point\">Point ").append(p.number()).append(" · <b>").append(RunReport.esc(p.step()))
                    .append("</b></p>\n<div class=\"pair\">");
            ViewportComparison.AtSize atReference = p.atSize().get(reference);
            if (!size.equals(reference) && atReference != null) {
                image(h, atReference.screenshot(), reference, outDir);
            }
            image(h, at.screenshot(), size, outDir);
            h.append("<div class=\"lines\">");
            for (String f : at.findings()) {
                h.append("<div class=\"finding\">").append(icon(f)).append(' ').append(RunReport.esc(f)).append("</div>");
            }
            for (ViewportComparison.Lost l : at.lost()) {
                h.append(l.explained() ? "<div class=\"lost explained\">◌ " : "<div class=\"lost\">− ")
                        .append(RunReport.esc(l.line())).append(" <small>").append(RunReport.esc(l.why()))
                        .append(l.explained() ? "" : ", needs review").append("</small></div>");
            }
            if (at.findings().isEmpty() && at.lost().isEmpty()) {
                h.append("<div class=\"ok\">✔ ").append(size.equals(reference) ? "reference, no Layout Findings"
                        : "nothing lost, no Layout Findings").append("</div>");
            }
            h.append("</div></div>\n");
        }
        steps(h, s, size, reference);
        h.append("</section>\n");
    }

    private static void image(StringBuilder h, String screenshot, String size, Path outDir) {
        if (screenshot == null) {
            return;
        }
        h.append("<figure><img src=\"").append(RunReport.esc(link(screenshot, outDir))).append("\" alt=\"")
                .append(RunReport.esc(size)).append("\"><figcaption>").append(RunReport.esc(size))
                .append("</figcaption></figure>");
    }

    /** ⇿ sideways scroll, ▭ clipped, ⧉ overlap, as in {@code report.html}. */
    private static String icon(String finding) {
        return finding.contains(" scrolls sideways ") ? "⇿" : finding.contains(" is clipped ") ? "▭"
                : finding.contains(" is overlapped ") ? "⧉" : "•";
    }

    /** The User Steps once, with each size's Layout Findings on a measured step, forking where the runs part. */
    private static void steps(StringBuilder h, ViewportComparison.Scenario s, String size, String reference) {
        List<String> ref = s.steps().getOrDefault(reference, List.of());
        List<String> here = s.steps().getOrDefault(size, List.of());
        RunComparison.Parting parting = s.partings().get(size);
        int shared = parting == null ? ref.size() : parting.position() - 1;
        h.append("<details class=\"steps\"><summary>User Steps</summary><ol>");
        for (int k = 0; k < shared && k < ref.size(); k++) {
            h.append("<li>").append(RunReport.esc(ref.get(k))).append(measuredPills(s, ref.get(k))).append("</li>");
        }
        h.append("</ol>");
        if (parting != null) {
            h.append("<div class=\"fork\">");
            fork(h, reference, ref, shared);
            fork(h, size, here, shared);
            h.append("</div>");
        }
        h.append("</details>\n");
    }

    private static void fork(StringBuilder h, String size, List<String> lines, int from) {
        h.append("<div><h4>").append(RunReport.esc(size)).append("</h4><ol start=\"").append(from + 1).append("\">");
        for (int k = from; k < lines.size(); k++) {
            h.append("<li>").append(RunReport.esc(lines.get(k))).append("</li>");
        }
        h.append("</ol></div>");
    }

    /** On a measured step: each size's findings and lost components as pills. */
    private static String measuredPills(ViewportComparison.Scenario s, String line) {
        StringBuilder h = new StringBuilder();
        for (ViewportComparison.Point p : s.points()) {
            if (!p.step().equals(line)) {
                continue;
            }
            p.atSize().forEach((size, at) -> {
                int lost = (int) at.lost().stream().filter(l -> !l.explained()).count();
                h.append(" <span class=\"pill ").append(at.findings().isEmpty() && lost == 0 ? "ok" : "f").append("\">")
                        .append(RunReport.esc(size)).append(at.findings().isEmpty() ? "" : " " + at.findings().size() + " ⚠")
                        .append(lost == 0 ? "" : " " + lost + " lost").append(at.findings().isEmpty() && lost == 0 ? " ✔" : "")
                        .append("</span>");
            });
            break;
        }
        return h.toString();
    }

    private static final String CSS = """
            :root{--bg:#fbfbfa;--surface:#fff;--text:#1d1d1f;--muted:#5f6368;--line:#e3e3e0;--pass:#1a7f37;\
            --fail:#c62828;--fail-bg:#fdecea;--accent:#1f5fbf;--code:#f3f3f1;--warn:#9a6700;--warn-bg:#fff8e1;\
            --lost:#8250df;--lost-bg:#f3edff;--ok-bg:#eaf6ec}
            @media (prefers-color-scheme: dark){:root{--bg:#141416;--surface:#1c1c1f;--text:#e8e8ea;--muted:#a0a0a8;\
            --line:#2e2e33;--pass:#57c470;--fail:#ff6b6b;--fail-bg:#3a1e1e;--accent:#7aa7ff;--code:#26262b;\
            --warn:#e3b341;--warn-bg:#3a3117;--lost:#c297ff;--lost-bg:#2c2340;--ok-bg:#1d3324}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,"Segoe UI",\
            Roboto,sans-serif}
            main{max-width:1180px;margin:0 auto;padding:24px 16px 64px}
            h1{font-size:22px;margin:0 0 4px}h1 .tool{color:var(--muted);font-weight:500}h2{font-size:17px;margin:4px 0 8px}
            .lead{color:var(--muted)}
            table.matrix{border-collapse:collapse;width:100%;background:var(--surface);border:1px solid var(--line)}
            table.matrix th,table.matrix td{border-bottom:1px solid var(--line);padding:8px 10px;text-align:left}
            table.matrix th{font-size:13px;color:var(--muted)}
            td.cell{cursor:pointer;white-space:nowrap}td.cell:hover,td.cell.sel{outline:2px solid var(--accent);outline-offset:-2px}
            .pill{display:inline-block;border-radius:10px;padding:0 8px;font-size:12px;font-weight:600;margin-right:4px}
            .pill.f{background:var(--warn-bg);color:var(--warn)}.pill.l{background:var(--lost-bg);color:var(--lost)}
            .pill.x{background:var(--fail-bg);color:var(--fail)}.pill.ok{background:var(--ok-bg);color:var(--pass)}
            .pill.n{background:var(--code);color:var(--muted)}
            .detail{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:10px 14px;margin-top:12px}
            .point{font-size:13px;color:var(--muted);margin:14px 0 6px}.point b{color:var(--text)}
            .pair{display:flex;gap:16px;flex-wrap:wrap;align-items:flex-start}
            figure{margin:0}figure img{max-width:320px;border:1px solid var(--line);border-radius:4px}
            figcaption{font-size:12px;color:var(--muted)}.lines{flex:1 1 260px;min-width:0}
            .finding{font-size:13px;margin:2px 0;padding:1px 6px;border-radius:4px;background:var(--warn-bg);color:var(--warn)}
            .lost{font-size:13px;margin:2px 0;padding:1px 6px;border-radius:4px;background:var(--lost-bg);color:var(--lost)}
            .lost.explained{background:transparent;color:var(--muted)}.ok{font-size:13px;color:var(--pass)}
            details.steps{margin-top:12px}.fork{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
            .fork h4{margin:4px 0;font-size:13px;color:var(--muted)}
            @media (max-width:600px){.fork{grid-template-columns:1fr}figure img{max-width:100%}}
            """;

    private static final String SCRIPT = """
            document.addEventListener('click', e => {
              const cell = e.target.closest('td.cell');
              if (!cell) return;
              document.querySelectorAll('td.cell.sel').forEach(c => c.classList.remove('sel'));
              document.querySelectorAll('section.detail').forEach(d => d.hidden = true);
              cell.classList.add('sel');
              const detail = document.getElementById(cell.dataset.detail);
              if (detail) { detail.hidden = false; detail.scrollIntoView({block: 'nearest'}); }
            });
            """;
}
