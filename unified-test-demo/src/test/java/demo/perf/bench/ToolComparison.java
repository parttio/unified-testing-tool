package demo.perf.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Times the Performance Pairs on two Testing Tools in one JVM, so that the tools can be compared with each other
 * (#497): the same scenario, the same application server, the same machine and, with {@code --browser}, the same
 * browser binary. {@link TimeBenchmark} compares a Unified UI Test with the Direct Test on one tool; this compares
 * the tools.
 *
 * <p>Each round runs four test classes: the Unified UI Test and the Direct Test on each tool. The tools' order
 * alternates round by round (ABBA), and so does the order of the two tests on a tool, so a drift during the run
 * hits every side. Only each test's body is timed ({@link BodyTimer}): not the browser's launch.
 *
 * <p>Per scenario and per side (the Unified UI Test, and the Direct Tests, for example Dramafinder against
 * TestBench): the median of the paired ratios (second tool ÷ first tool, round by round) and its bootstrap 95 %
 * interval. Informational only: there is no budget and no verdict.
 *
 * <pre>
 * java ... demo.perf.bench.ToolComparison --tools playwright,selenium [--warmup 5] [--rounds 30]
 *     [--scenario NAME] [--out DIR]
 * </pre>
 *
 * Writes {@code compare-<first>-<second>.csv} (every timed body) and {@code .md} (the table) to the output
 * directory, prints the table, and appends it to {@code GITHUB_STEP_SUMMARY} when set. The system property
 * {@code vaadin.testing.perf.browser}, when set, names the browser binary in the table. Exit code 0, or 2 when a
 * test failed.
 */
public final class ToolComparison {

    /** The two sides of a Performance Pair. */
    enum Side { UNIFIED, DIRECT }

    private ToolComparison() {}

    public static void main(String[] args) throws IOException {
        Map<String, String> options = TimeBenchmark.options(args);
        List<String> tools = tools(options.get("tools"));
        int warmup = Integer.parseInt(options.getOrDefault("warmup", "5"));
        int rounds = Integer.parseInt(options.getOrDefault("rounds", "30"));
        String only = options.get("scenario");
        Path out = Path.of(options.getOrDefault("out", "target/benchmark")).toAbsolutePath();
        List<TimeBenchmark.Scenario> scenarios = TimeBenchmark.SCENARIOS.stream()
                .filter(s -> only == null || s.method().equals(only)).toList();

        // tool -> side -> scenario -> ms per measured round
        Map<String, Map<Side, Map<String, double[]>>> times = new HashMap<>();
        for (String tool : tools) {
            for (Side side : Side.values()) {
                for (TimeBenchmark.Scenario s : scenarios) {
                    times.computeIfAbsent(tool, t -> new HashMap<>()).computeIfAbsent(side, x -> new HashMap<>())
                            .put(s.method(), new double[rounds]);
                }
            }
        }
        List<String> csv = new ArrayList<>(List.of("round,tool,side,scenario,millis"));
        Launcher launcher = LauncherFactory.create();
        for (int round = 0; round < warmup + rounds; round++) {
            for (String tool : order(tools, round)) {
                for (Side side : order(List.of(Side.UNIFIED, Side.DIRECT), round / 2)) {
                    String testClass = side == Side.UNIFIED ? TimeBenchmark.UNIFIED : TimeBenchmark.directTest(tool);
                    // the Direct Tests are enabled by this system property; the Unified UI Test reads it too
                    System.setProperty("vaadin.testing.tool", tool);
                    TimeBenchmark.run(launcher, testClass, only, tool);
                    for (BodyTimer.Timing t : BodyTimer.drain()) {
                        csv.add(round + "," + tool + "," + side.name().toLowerCase(Locale.ROOT) + "," + t.method()
                                + "," + String.format(Locale.ROOT, "%.3f", t.millis()));
                        double[] measured = times.get(tool).get(side).get(t.method());
                        if (round >= warmup && measured != null) {
                            measured[round - warmup] = t.millis();
                        }
                    }
                }
            }
            System.out.println("Round " + (round + 1) + " of " + (warmup + rounds)
                    + (round < warmup ? " (warm-up)" : ""));
        }

        List<Result> results = new ArrayList<>();
        for (TimeBenchmark.Scenario s : scenarios) {
            for (Side side : Side.values()) {
                results.add(Result.of(s.method(), side, times.get(tools.get(0)).get(side).get(s.method()),
                        times.get(tools.get(1)).get(side).get(s.method())));
            }
        }
        String name = "compare-" + tools.get(0) + "-" + tools.get(1);
        String table = table(tools, warmup, rounds, System.getProperty("vaadin.testing.perf.browser", ""), results);
        Files.createDirectories(out);
        Files.write(out.resolve(name + ".csv"), csv);
        Files.writeString(out.resolve(name + ".md"), table);
        System.out.println(table);
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), table + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.exit(0);
    }

    static List<String> tools(String value) {
        if (value == null) {
            throw new IllegalArgumentException("--tools FIRST,SECOND is required, for example playwright,selenium");
        }
        List<String> tools = List.of(value.split(","));
        if (tools.size() != 2 || tools.get(0).equals(tools.get(1))) {
            throw new IllegalArgumentException("--tools takes two different Testing Tools, got " + value);
        }
        tools.forEach(TimeBenchmark::directTest); // fails on an unknown tool
        return tools;
    }

    /** ABBA: the given order on even turns, reversed on odd ones. */
    static <T> List<T> order(List<T> items, int turn) {
        return turn % 2 == 0 ? items : List.of(items.get(1), items.get(0));
    }

    /** One scenario's side over the measured rounds: second tool ÷ first tool. */
    record Result(String scenario, Side side, double firstMedian, double secondMedian, double ratio, double low,
            double high) {

        static Result of(String scenario, Side side, double[] first, double[] second) {
            double[] ratios = new double[first.length];
            for (int i = 0; i < ratios.length; i++) {
                ratios[i] = second[i] / first[i];
            }
            double[] interval = TimeBenchmark.bootstrap(ratios);
            return new Result(scenario, side, TimeBenchmark.median(first), TimeBenchmark.median(second),
                    TimeBenchmark.median(ratios), interval[0], interval[1]);
        }
    }

    static String table(List<String> tools, int warmup, int rounds, String browser, List<Result> results) {
        String first = tools.get(0);
        String second = tools.get(1);
        StringBuilder md = new StringBuilder();
        md.append("### Time per Testing Tool: ").append(second).append(" against ").append(first).append("\n\n");
        md.append("The Performance Pairs on both tools in one JVM, against one application server: test body only, ")
                .append(rounds).append(" ABBA rounds after ").append(warmup).append(" warm-up. Browser: ")
                .append(browser.isBlank() ? "each tool's default" : "`" + browser + "` on both").append(". ")
                .append("A ratio above 1 means ").append(second).append(" is slower. Informational: no budget.\n\n");
        md.append("| Scenario | Tests | ").append(first).append(" (median) | ").append(second)
                .append(" (median) | ").append(second).append(" ÷ ").append(first).append(" | 95 % interval |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (Result r : results) {
            String tests = r.side() == Side.UNIFIED ? "Unified UI Test"
                    : simpleName(TimeBenchmark.directTest(first)) + " vs " + simpleName(TimeBenchmark.directTest(second));
            md.append(String.format(Locale.ROOT, "| %s | %s | %.1f ms | %.1f ms | %.3f | %.3f to %.3f |%n",
                    r.scenario(), tests, r.firstMedian(), r.secondMedian(), r.ratio(), r.low(), r.high()));
        }
        return md.toString();
    }

    private static String simpleName(String testClass) {
        return testClass.substring(testClass.lastIndexOf('.') + 1);
    }
}
