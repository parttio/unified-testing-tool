import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scores one run of the write-unified-ui-tests skill evaluation and compares it with the committed baseline.
 * Run by scripts/eval-writing-skill.sh; see its usage. Single-file program: java scripts/EvalWritingSkill.java ...
 *
 * <pre>
 * java EvalWritingSkill.java --runs DIR --skill FILE --baseline FILE --source agent|reference --out FILE [--accept]
 * </pre>
 *
 * {@code --runs} holds one directory per fixture (the test class name), as the script leaves it: {@code files/} (the
 * test sources the run wrote), {@code compiled} (present when the demo's tests compiled), {@code browserless/} and
 * {@code playwright/} (Surefire XML reports; the Browserless run includes {@code UnifiedStyleTest}),
 * {@code intent.json} (scripts/score_intent.py on the test class) and {@code other-changes.txt} (files changed
 * outside the demo's test sources, one per line).
 *
 * The run is compared with the baseline fixture by fixture, over the fixtures both have. Exit code 0 when the run is
 * acceptable (no compared metric worse; when the skill changed, at least one better, unless the baseline has nothing
 * left to improve), 1 when it is not, 2 on bad arguments.
 */
public class EvalWritingSkill {

    static final List<String> TOOLS = List.of("browserless", "playwright");

    /** The counts scored per fixture, ordered as reported. */
    static final List<String> COUNTS = List.of("compiles", "browserless.passed", "browserless.failed",
            "playwright.passed", "playwright.failed", "styleRules", "intent", "plumbing", "escapeHatches",
            "otherFilesChanged");

    /** Intent share may drop this many percentage points before it counts as worse: agents word things differently. */
    static final double INTENT_TOLERANCE = 5.0;

    /** A measure: a count, or the intent share; and whether higher is better. */
    record Measure(String name, String count, boolean higherIsBetter) {

        /** 1 better, -1 worse, 0 same. */
        int compare(Map<String, Long> was, Map<String, Long> is) {
            if (count.equals("intentShare")) {
                double w = share(was);
                double i = share(is);
                return Double.isNaN(w) || Double.isNaN(i) ? 0
                        : i - w > INTENT_TOLERANCE ? 1 : w - i > INTENT_TOLERANCE ? -1 : 0;
            }
            int c = Long.compare(is.getOrDefault(count, 0L), was.getOrDefault(count, 0L));
            return higherIsBetter ? c : -c;
        }
    }

    static final List<Measure> MEASURES = List.of(
            new Measure("compiles", "compiles", true),
            new Measure("browserless.passed", "browserless.passed", true),
            new Measure("browserless.failed", "browserless.failed", false),
            new Measure("playwright.passed", "playwright.passed", true),
            new Measure("playwright.failed", "playwright.failed", false),
            new Measure("styleRules", "styleRules", true),
            new Measure("intent share", "intentShare", true),
            new Measure("escapeHatches", "escapeHatches", false),
            new Measure("otherFilesChanged", "otherFilesChanged", false));

    enum Status {
        COMPARED("compared"), NEW("new: compared against nothing"), GONE("not in this run");

        final String label;

        Status(String label) {
            this.label = label;
        }
    }

    record Row(String fixture, Status status, Map<String, Long> was, Map<String, Long> is, List<String> better,
            List<String> worse) {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> a = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--accept")) {
                a.put("accept", "true");
            } else if (args[i].startsWith("--") && i + 1 < args.length) {
                a.put(args[i].substring(2), args[++i]);
            }
        }
        for (String required : List.of("runs", "skill", "baseline", "out")) {
            if (!a.containsKey(required)) {
                System.err.println("missing --" + required);
                System.exit(2);
            }
        }
        Path runs = Path.of(a.get("runs"));
        String source = a.getOrDefault("source", "agent");

        Map<String, Map<String, Long>> run = new LinkedHashMap<>();
        try (Stream<Path> dirs = Files.list(runs)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                run.put(dir.getFileName().toString(), score(dir));
            }
        }

        String skillHash = sha256(Files.readString(Path.of(a.get("skill"))));
        Path baselineFile = Path.of(a.get("baseline"));
        Map<String, Object> baseline = Files.exists(baselineFile) ? new Json(Files.readString(baselineFile)).object() : Map.of();
        boolean skillChanged = !skillHash.equals(baseline.get("skillSha256"));
        Map<String, Map<String, Long>> was = fixtures(baseline);

        List<Row> rows = new ArrayList<>();
        TreeSet<String> all = new TreeSet<>(run.keySet());
        if (was != null) {
            all.addAll(was.keySet());
        }
        List<String> better = new ArrayList<>();
        List<String> worse = new ArrayList<>();
        for (String fixture : all) {
            Map<String, Long> w = was == null ? null : was.get(fixture);
            Map<String, Long> i = run.get(fixture);
            List<String> b = new ArrayList<>();
            List<String> x = new ArrayList<>();
            if (w != null && i != null) {
                for (Measure measure : MEASURES) {
                    int c = measure.compare(w, i);
                    if (c > 0) {
                        b.add(measure.name());
                    } else if (c < 0) {
                        x.add(measure.name());
                    }
                }
            }
            b.forEach(m -> better.add(fixture + ": " + m));
            x.forEach(m -> worse.add(fixture + ": " + m));
            rows.add(new Row(fixture, w == null ? Status.NEW : i == null ? Status.GONE : Status.COMPARED, w, i, b, x));
        }
        String verdict = was == null ? "no baseline yet" : !worse.isEmpty() ? "worse" : better.isEmpty() ? "same" : "better";
        boolean perfect = was != null && rows.stream().anyMatch(r -> r.status() == Status.COMPARED)
                && rows.stream().filter(r -> r.status() == Status.COMPARED).allMatch(r -> perfect(r.was()));
        boolean acceptable = was == null || worse.isEmpty() && (!skillChanged || !better.isEmpty() || perfect);

        Map<String, Long> totals = totals(run.values());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillSha256", skillHash);
        result.put("source", source);
        result.put("totals", totals);
        result.put("fixtures", run);
        Path out = Path.of(a.get("out"));
        Files.writeString(out.resolveSibling("result.json"), Json.write(result, "") + "\n");
        Files.writeString(out, markdown(verdict, skillChanged, perfect, acceptable, source, baseline.get("source"),
                totals, rows, better, worse));
        if (a.containsKey("accept") && acceptable) {
            Files.writeString(baselineFile, Json.write(result, "") + "\n");
            System.out.println("Baseline updated: " + baselineFile);
        }
        System.out.println(heading(verdict, skillChanged, perfect, acceptable) + " Report: " + out);
        System.exit(acceptable ? 0 : 1);
    }

    /** One fixture's scores, from what the script left in its directory. */
    static Map<String, Long> score(Path dir) throws IOException {
        String fixture = dir.getFileName().toString();
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("compiles", Files.exists(dir.resolve("compiled")) ? 1L : 0L);
        for (String tool : TOOLS) {
            long[] counts = surefire(dir.resolve(tool), fixture);
            m.put(tool + ".passed", counts[0]);
            m.put(tool + ".failed", counts[1]);
        }
        long[] style = surefire(dir.resolve("browserless"), "UnifiedStyleTest");
        m.put("styleRules", style[0] > 0 && style[1] == 0 ? 1L : 0L);
        Path intent = dir.resolve("intent.json");
        String json = Files.exists(intent) ? Files.readString(intent) : "";
        m.put("intent", number(json, "intent"));
        m.put("plumbing", number(json, "plumbing"));
        long escapeHatches = 0;
        Path files = dir.resolve("files");
        if (Files.isDirectory(files)) {
            try (Stream<Path> sources = Files.walk(files)) {
                for (Path f : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(f);
                    escapeHatches += count(text, ".unwrap(") + count(text, "EscapeHatch.of(")
                            + count(text, "@RequiresTestingTool");
                }
            }
        }
        m.put("escapeHatches", escapeHatches);
        Path other = dir.resolve("other-changes.txt");
        m.put("otherFilesChanged", Files.exists(other)
                ? Files.readAllLines(other).stream().filter(l -> !l.isBlank()).count() : 0L);
        return m;
    }

    static long number(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    /** Intent share in percent, NaN without tokens. */
    static double share(Map<String, Long> m) {
        long intent = m.getOrDefault("intent", 0L);
        long total = intent + m.getOrDefault("plumbing", 0L);
        return total == 0 ? Double.NaN : 100.0 * intent / total;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Long>> fixtures(Map<String, Object> baseline) {
        if (!(baseline.get("fixtures") instanceof Map<?, ?> fixtures)) {
            return null;
        }
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        fixtures.forEach((name, scores) -> result.put((String) name, (Map<String, Long>) scores));
        return result;
    }

    /** At its best on every measure but intent share, which has no ceiling. */
    static boolean perfect(Map<String, Long> m) {
        return m.get("compiles") == 1 && m.get("styleRules") == 1 && m.get("escapeHatches") == 0
                && m.get("otherFilesChanged") == 0
                && TOOLS.stream().allMatch(t -> m.get(t + ".failed") == 0 && m.get(t + ".passed") > 0);
    }

    static Map<String, Long> totals(Iterable<Map<String, Long>> scores) {
        Map<String, Long> totals = new LinkedHashMap<>();
        COUNTS.forEach(c -> totals.put(c, 0L));
        for (Map<String, Long> m : scores) {
            m.forEach((k, v) -> totals.merge(k, v, Long::sum));
        }
        return totals;
    }

    static long count(String text, String needle) {
        long n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    /** Passed and failed (failures + errors) test cases of a test class and its nested classes in a report directory. */
    static long[] surefire(Path dir, String testClass) throws IOException {
        long passed = 0;
        long failed = 0;
        if (!Files.isDirectory(dir)) {
            return new long[] {0, 0};
        }
        Pattern name = Pattern.compile("TEST-(.+\\.)?" + Pattern.quote(testClass) + "(\\$.+)?\\.xml");
        Pattern suite = Pattern.compile("<testsuite [^>]*>");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> name.matcher(p.getFileName().toString()).matches()).toList()) {
                Matcher m = suite.matcher(Files.readString(f));
                if (m.find()) {
                    String tag = m.group();
                    long fail = attr(tag, "failures") + attr(tag, "errors");
                    passed += attr(tag, "tests") - fail - attr(tag, "skipped");
                    failed += fail;
                }
            }
        }
        return new long[] {passed, failed};
    }

    static long attr(String tag, String name) {
        Matcher m = Pattern.compile(" " + name + "=\"(\\d+)\"").matcher(tag);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static String heading(String verdict, boolean skillChanged, boolean perfect, boolean acceptable) {
        return "Verdict: " + verdict + (!skillChanged || verdict.startsWith("no baseline") ? "" : perfect
                ? " (the skill changed; the baseline has nothing left to improve: it must not be worse)"
                : " (the skill changed: it must be better)") + (acceptable ? ". Acceptable." : ". Not acceptable.");
    }

    static String markdown(String verdict, boolean skillChanged, boolean perfect, boolean acceptable, String source,
            Object baselineSource, Map<String, Long> totals, List<Row> rows, List<String> better, List<String> worse) {
        StringBuilder sb = new StringBuilder("# Writing skill evaluation\n\n");
        sb.append("**").append(heading(verdict, skillChanged, perfect, acceptable)).append("**\n\n");
        sb.append("This run: ").append(source.equals("reference")
                ? "the hand-written reference tests (`skill-eval/writing/reference/`), no agent"
                : "tests written by an agent with the skill").append(". Baseline: ")
                .append(baselineSource == null ? "none" : baselineSource).append(".\n\n");
        if (!better.isEmpty()) {
            sb.append("Better: ").append(String.join("; ", better)).append("\n\n");
        }
        if (!worse.isEmpty()) {
            sb.append("Worse: ").append(String.join("; ", worse)).append("\n\n");
        }
        sb.append("| Fixture | Status | Compiles | Browserless | Playwright | Style rules | Intent share | Escape Hatches | "
                + "Other files changed | Compared with the baseline |\n|").append("---|".repeat(10)).append("\n");
        for (Row row : rows) {
            Map<String, Long> m = row.is() != null ? row.is() : row.was();
            String comparison = row.status() != Status.COMPARED ? ""
                    : row.better().isEmpty() && row.worse().isEmpty() ? "same"
                    : String.join(", ", Stream.concat(row.better().stream().map(b -> "better " + b),
                            row.worse().stream().map(w -> "worse " + w)).toList());
            sb.append("| ").append(String.join(" | ", List.of(row.fixture(), row.status().label,
                    m.get("compiles") == 1 ? "yes" : "no", tests(m, "browserless"), tests(m, "playwright"),
                    m.get("styleRules") == 1 ? "pass" : "fail", percent(m), String.valueOf(m.get("escapeHatches")),
                    String.valueOf(m.get("otherFilesChanged")), comparison))).append(" |\n");
        }
        sb.append("| **All** | | ").append(totals.get("compiles")).append("/").append(rows.stream().filter(r -> r.is() != null).count())
                .append(" | ").append(tests(totals, "browserless")).append(" | ").append(tests(totals, "playwright"))
                .append(" | ").append(totals.get("styleRules")).append(" pass | ").append(percent(totals)).append(" | ")
                .append(totals.get("escapeHatches")).append(" | ").append(totals.get("otherFilesChanged")).append(" | |\n");
        return sb.toString();
    }

    static String tests(Map<String, Long> m, String tool) {
        long passed = m.get(tool + ".passed");
        return passed + "/" + (passed + m.get(tool + ".failed")) + " passed";
    }

    static String percent(Map<String, Long> m) {
        double s = share(m);
        return Double.isNaN(s) ? "–" : Math.round(s) + "% (" + m.get("intent") + "/" + (m.get("intent") + m.get("plumbing")) + ")";
    }

    /** JSON of objects, strings and whole numbers: what the baseline holds. */
    static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s;
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            if (peek() == '}') {
                i++;
                return map;
            }
            do {
                String key = string();
                expect(':');
                map.put(key, value());
            } while (next() == ',');
            i--;
            expect('}');
            return map;
        }

        private Object value() {
            char c = peek();
            if (c == '{') {
                return object();
            }
            if (c == '"') {
                return string();
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-')) {
                i++;
            }
            return Long.parseLong(s.substring(start, i));
        }

        private String string() {
            expect('"');
            int start = i;
            while (s.charAt(i) != '"') {
                i++;
            }
            return s.substring(start, i++);
        }

        private char peek() {
            while (Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("expected " + c + " at " + (i - 1));
            }
        }

        static String write(Object value, String indent) {
            if (value instanceof Map<?, ?> map) {
                StringBuilder sb = new StringBuilder("{\n");
                int n = 0;
                for (var e : map.entrySet()) {
                    sb.append(indent).append("  \"").append(e.getKey()).append("\": ")
                            .append(write(e.getValue(), indent + "  ")).append(++n < map.size() ? ",\n" : "\n");
                }
                return sb.append(indent).append("}").toString();
            }
            return value instanceof String text ? "\"" + text + "\"" : String.valueOf(value);
        }
    }
}
