package com.vaadin.testing.unified.report;

import com.vaadin.testing.unified.internal.steps.Revision;
import com.vaadin.testing.unified.internal.steps.RunComparison;
import com.vaadin.testing.unified.internal.steps.RunSummary;
import com.vaadin.testing.unified.internal.steps.RunSummaryJson;
import com.vaadin.testing.unified.internal.steps.ScenarioResult;
import com.vaadin.testing.unified.internal.steps.UserStepsReport;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Compares runs of the same Unified UI Tests on different Testing Tools after they have ended, from their evidence
 * directories: for CI, where each Testing Tool has a job of its own and none has another's evidence while it runs.
 *
 * <pre>
 * java -cp unified-test-api.jar com.vaadin.testing.unified.report.CompareRuns &lt;evidence dir&gt; &lt;evidence dir&gt; ...
 * </pre>
 *
 * Every {@code user-steps.json} under the directories is read, at any depth, and sorted by its Testing Tool. Each
 * Testing Tool is compared with the reference: Browserless when it is there, since it is the run a developer
 * repeats, else the first by name. The comparison is the one a run makes with
 * {@code vaadin.testing.user-steps.compare-dir} (spec chapter 12): the scenarios that pass on one and fail on the
 * other, those that take different User Steps and where they part, and whether both runs tested the same code.
 * <p>
 * It prints Markdown, and appends it to the GitHub job summary when {@code GITHUB_STEP_SUMMARY} is set. It reports:
 * the exit code is 0 whatever the runs say, and 2 when no two Testing Tools are found.
 */
public final class CompareRuns {
    private CompareRuns() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: CompareRuns <evidence dir> <evidence dir> ...");
            System.exit(2);
        }
        // UTF-8 whatever the machine's default is: the output has “quotes” and is read by tools
        int status = run(Stream.of(args).map(Path::of).toList(), System.getenv("GITHUB_STEP_SUMMARY"),
                new PrintStream(System.out, true, StandardCharsets.UTF_8), System.err);
        System.exit(status);
    }

    /**
     * @param directories the runs' evidence directories; one that doesn't exist is said and left out
     * @param stepSummary the file of the GitHub job summary, or null
     * @return the exit code
     */
    static int run(List<Path> directories, String stepSummary, PrintStream out, PrintStream log) throws IOException {
        Map<String, List<ScenarioResult>> byTool = new TreeMap<>();
        List<Revision.OfRun> runs = new ArrayList<>();
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                log.println("No evidence in " + directory + ": left out");
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(directory)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                String name = file.getFileName().toString();
                try {
                    if (name.equals(UserStepsReport.JSON_FILE)) {
                        ScenarioResult scenario = ScenarioResult.parse(Files.readString(file, StandardCharsets.UTF_8),
                                (file.getParent() + "/").replace('\\', '/'));
                        if (scenario.tool() != null) {
                            byTool.computeIfAbsent(scenario.tool().toLowerCase(Locale.ROOT), t -> new ArrayList<>())
                                    .add(scenario);
                        }
                    } else if (name.equals(RunSummaryJson.FILE)) {
                        Revision.read(Files.readString(file, StandardCharsets.UTF_8)).ifPresent(runs::add);
                    }
                } catch (IOException | RuntimeException e) {
                    log.println("Left out, unreadable: " + file + " (" + e.getMessage() + ")");
                }
            }
        }
        if (byTool.size() < 2) {
            log.println("Nothing to compare: " + (byTool.isEmpty() ? "no user-steps.json found"
                    : "only " + byTool.keySet().iterator().next() + " ran") + " in " + directories);
            return 2;
        }
        String reference = byTool.containsKey("browserless") ? "browserless" : byTool.keySet().iterator().next();
        StringBuilder md = new StringBuilder("### Unified UI Tests · Testing Tools compared\n");
        for (Map.Entry<String, List<ScenarioResult>> tool : byTool.entrySet()) {
            if (tool.getKey().equals(reference)) {
                continue;
            }
            List<RunComparison> comparison = RunComparison.of(tool.getKey(), tool.getValue(), byTool.get(reference),
                    runs);
            // the comparison's words are this run's, so each Testing Tool is "this run" in its own section
            RunSummary summary = new RunSummary(shown(tool.getKey()), tool.getValue(), 0, null, null, comparison,
                    revision(runs, tool.getKey()));
            md.append(summary.comparisonsMarkdown().replace("#### Compared with " + shown(reference),
                    "#### " + shown(tool.getKey()) + " compared with " + shown(reference)));
        }
        out.print(md);
        if (stepSummary != null && !stepSummary.isBlank()) {
            Files.writeString(Path.of(stepSummary), md + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
        return 0;
    }

    /** What the Testing Tool's run tested, when its summary.json files agree on it; else null. */
    private static Revision revision(List<Revision.OfRun> runs, String tool) {
        List<Revision> revisions = runs.stream().filter(r -> tool.equalsIgnoreCase(r.tool())).map(Revision.OfRun::revision)
                .filter(java.util.Objects::nonNull).distinct().toList();
        return revisions.size() == 1 ? revisions.get(0) : null;
    }

    private static String shown(String tool) {
        return Character.toUpperCase(tool.charAt(0)) + tool.substring(1);
    }
}
