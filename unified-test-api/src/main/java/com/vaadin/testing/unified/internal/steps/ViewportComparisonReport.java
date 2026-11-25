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

    /** The page people review: written in full by {@link #html}. */
    public static String html(ViewportComparison c, Path outDir) {
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<title>Viewport Comparison · "
                + RunReport.esc(shown(c.tool())) + "</title>\n</head>\n<body>\n<h1>Viewport Comparison</h1>\n</body>\n</html>\n";
    }
}
