package demo.perf.bench;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * The time benchmark of the Performance Pairs (#342, the method of #301, the budget of #304): on this run's
 * Testing Tool, the Unified UI Test and the Direct Test of each scenario in one JVM, in ABBA order, with warm-up
 * rounds first; only each test's body is timed ({@link BodyTimer}).
 *
 * <p>Per scenario: the median of the paired ratios (Unified ÷ Direct, round by round) and its bootstrap 95 %
 * interval, against the budget, whichever is larger of 10 % and 2 ms per user action:
 * <b>pass</b> when the median is within it, <b>warn</b> when the median is over but the interval reaches below
 * it, <b>fail</b> when the whole interval is over it.
 *
 * <pre>
 * java ... demo.perf.bench.TimeBenchmark [--warmup 5] [--rounds 30] [--scenario NAME] [--out DIR]
 * </pre>
 *
 * Writes {@code <tool>.csv} (every timed body) and {@code <tool>.md} (the table) to the output directory, prints
 * the table, and appends it to {@code GITHUB_STEP_SUMMARY} when set. Exit code 0 when no scenario fails, 1 when
 * one does, 2 when a test failed.
 */
public final class TimeBenchmark {

    /** A scenario: the test methods' name and how many user actions it makes (for the budget per action). */
    record Scenario(String method, int userActions) {}

    static final List<Scenario> SCENARIOS = List.of(
            new Scenario("smallForm", 3),       // fill, fill, click
            new Scenario("longForm", 31),       // 30 fields, then Save
            new Scenario("largeGrid", 1),       // find the row and click it
            new Scenario("comboBox", 1));       // pick the item

    static final String UNIFIED = "demo.perf.UnifiedPerfTest";

    static final double BUDGET_RATIO = 1.10;
    static final double BUDGET_MS_PER_ACTION = 2.0;

    private TimeBenchmark() {}

    public static void main(String[] args) throws IOException {
        Map<String, String> options = options(args);
        int warmup = Integer.parseInt(options.getOrDefault("warmup", "5"));
        int rounds = Integer.parseInt(options.getOrDefault("rounds", "30"));
        String only = options.get("scenario");
        Path out = Path.of(options.getOrDefault("out", "target/benchmark")).toAbsolutePath();
        String tool = System.getProperty("vaadin.testing.tool", "browserless");
        String direct = directTest(tool);
        List<Scenario> scenarios = SCENARIOS.stream().filter(s -> only == null || s.method().equals(only)).toList();

        // round -> side ("Unified"/"Direct") -> method -> ms
        List<String> csv = new ArrayList<>(List.of("round,side,scenario,millis"));
        Map<String, double[]> unified = new HashMap<>();
        Map<String, double[]> directTimes = new HashMap<>();
        for (Scenario s : scenarios) {
            unified.put(s.method(), new double[rounds]);
            directTimes.put(s.method(), new double[rounds]);
        }
        Launcher launcher = LauncherFactory.create();
        for (int round = 0; round < warmup + rounds; round++) {
            // ABBA: the pair's order alternates round by round, so a drift during the run hits both sides
            List<String> order = round % 2 == 0 ? List.of(UNIFIED, direct) : List.of(direct, UNIFIED);
            for (String testClass : order) {
                run(launcher, testClass, only, tool);
                for (BodyTimer.Timing t : BodyTimer.drain()) {
                    boolean isUnified = testClass.equals(UNIFIED);
                    csv.add(round + "," + (isUnified ? "Unified" : "Direct") + "," + t.method() + ","
                            + String.format(Locale.ROOT, "%.3f", t.millis()));
                    if (round >= warmup && unified.containsKey(t.method())) {
                        (isUnified ? unified : directTimes).get(t.method())[round - warmup] = t.millis();
                    }
                }
            }
            System.out.println("Round " + (round + 1) + " of " + (warmup + rounds)
                    + (round < warmup ? " (warm-up)" : ""));
        }

        List<Result> results = scenarios.stream()
                .map(s -> Result.of(s, unified.get(s.method()), directTimes.get(s.method())))
                .toList();
        String table = table(tool, direct, warmup, rounds, results);
        Files.createDirectories(out);
        Files.write(out.resolve(tool + ".csv"), csv);
        Files.writeString(out.resolve(tool + ".md"), table);
        System.out.println(table);
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), table + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        System.exit(results.stream().anyMatch(r -> r.verdict() == Verdict.FAIL) ? 1 : 0);
    }

    static String directTest(String tool) {
        return switch (tool) {
            case "browserless" -> "demo.perf.BrowserlessPerfTest";
            case "playwright" -> "demo.perf.DramafinderPerfTest";
            case "selenium" -> "demo.perf.TestBenchPerfTest";
            default -> throw new IllegalArgumentException("Unknown Testing Tool: " + tool);
        };
    }

    /**
     * The method's parameter types, as a method selector names them: {@code com.vaadin.testing.unified.UnifiedPage}
     * for a Unified UI Test, none for a Direct Test. A selector without them only finds a method with none.
     */
    private static String parameterTypes(String testClass, String method) {
        try {
            return Arrays.stream(Class.forName(testClass).getDeclaredMethods())
                    .filter(m -> m.getName().equals(method)).findFirst()
                    .map(m -> Arrays.stream(m.getParameterTypes()).map(Class::getName)
                            .collect(Collectors.joining(",")))
                    .orElse("");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs one test class (or one of its methods), as the demo's Surefire configuration would; exits 2 on a failure. */
    static void run(Launcher launcher, String testClass, String method, String tool) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(method == null ? selectClass(testClass)
                        : selectMethod(testClass, method, parameterTypes(testClass, method)))
                .configurationParameter("vaadin.testing.tool", tool)
                .configurationParameter("vaadin.testing.browserless.route-errors-to-session", "true")
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        if (summary.getTotalFailureCount() > 0 || summary.getTestsSucceededCount() == 0) {
            summary.getFailures().forEach(f -> {
                System.err.println(f.getTestIdentifier().getDisplayName() + ": " + f.getException());
                f.getException().printStackTrace();
            });
            System.err.println("The Performance Pairs failed on " + tool + " (" + testClass + "): no timing");
            System.exit(2);
        }
    }

    enum Verdict { PASS, WARN, FAIL }

    /** One scenario's result over the measured rounds. */
    record Result(Scenario scenario, double unifiedMedian, double directMedian, double ratio, double low,
            double high, double budget, Verdict verdict) {

        static Result of(Scenario scenario, double[] unified, double[] direct) {
            double[] ratios = new double[unified.length];
            for (int i = 0; i < ratios.length; i++) {
                ratios[i] = unified[i] / direct[i];
            }
            double ratio = median(ratios);
            double[] interval = bootstrap(ratios);
            double directMedian = median(direct);
            double budget = Math.max(BUDGET_RATIO,
                    1 + BUDGET_MS_PER_ACTION * scenario.userActions() / directMedian);
            Verdict verdict = ratio <= budget ? Verdict.PASS : interval[0] > budget ? Verdict.FAIL : Verdict.WARN;
            return new Result(scenario, median(unified), directMedian, ratio, interval[0], interval[1], budget,
                    verdict);
        }

        /** Unified's extra time per user action, in ms. */
        double extraPerAction() {
            return (unifiedMedian - directMedian) / scenario.userActions();
        }
    }

    static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    /** The 95 % interval of the median by bootstrap: 4,000 resamples, a fixed seed so a run can be repeated. */
    static double[] bootstrap(double[] values) {
        Random random = new Random(1);
        double[] medians = new double[4000];
        double[] sample = new double[values.length];
        for (int b = 0; b < medians.length; b++) {
            for (int i = 0; i < sample.length; i++) {
                sample[i] = values[random.nextInt(values.length)];
            }
            medians[b] = median(sample);
        }
        Arrays.sort(medians);
        return new double[] { medians[(int) (medians.length * 0.025)], medians[(int) (medians.length * 0.975) - 1] };
    }

    static String table(String tool, String direct, int warmup, int rounds, List<Result> results) {
        StringBuilder md = new StringBuilder();
        md.append("### Time per Performance Pair on ").append(tool).append("\n\n");
        md.append("Unified UI Test against `").append(direct.substring(direct.lastIndexOf('.') + 1))
                .append("`, test body only, ").append(rounds).append(" ABBA rounds after ").append(warmup)
                .append(" warm-up. Budget: 10 % or 2 ms per user action, whichever is larger. A result fails only when its"
                + " whole 95 % interval is over the budget, and warns when only its median is.\n\n");
        md.append("| Scenario | Direct (median) | Unified (median) | Ratio | 95 % interval | Extra per user action | Budget | Result |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (Result r : results) {
            md.append(String.format(Locale.ROOT, "| %s | %.1f ms | %.1f ms | %.3f | %.3f to %.3f | %.1f ms | %.3f | %s |%n",
                    r.scenario().method(), r.directMedian(), r.unifiedMedian(), r.ratio(), r.low(), r.high(),
                    r.extraPerAction(), r.budget(), switch (r.verdict()) {
                        case PASS -> "pass";
                        case WARN -> "**warn**";
                        case FAIL -> "**fail**";
                    }));
        }
        return md.toString();
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --name value pairs, got " + Arrays.toString(args));
            }
            options.put(args[i].substring(2), args[++i]);
        }
        return options;
    }
}
