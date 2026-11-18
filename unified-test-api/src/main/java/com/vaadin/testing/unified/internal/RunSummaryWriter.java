package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.internal.steps.BaseRun;
import com.vaadin.testing.unified.internal.steps.ChangedFiles;
import com.vaadin.testing.unified.internal.steps.RunComparison;
import com.vaadin.testing.unified.internal.steps.RunReport;
import com.vaadin.testing.unified.internal.steps.RunSummary;
import com.vaadin.testing.unified.internal.steps.RunSummaryJson;
import com.vaadin.testing.unified.internal.steps.ScenarioResult;
import com.vaadin.testing.unified.internal.steps.UserStepsReport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Writes the run's {@code summary.md}, {@code report.html} and {@code summary.json} to the evidence directory's root
 * and, in GitHub Actions, puts the summary in the job summary. Test Runner-neutral: the JUnit integration calls it at the end of the run.
 * <p>
 * <b>Which files belong to the run.</b> The {@code user-steps.json} under the evidence directory whose
 * {@code startedAt} is not before {@code runStart}. With Surefire's forks every fork rewrites the summary when it
 * ends, from the files of all forks, so the last one to end writes the whole run. Tests the run skipped without
 * running are listed per process in {@code .summary/skipped-<pid>.txt}, since they leave no {@code user-steps.json}.
 * <p>
 * <b>The base run.</b> With "Changed in this PR" and {@code vaadin.testing.user-steps.baseline-dir} set, every
 * {@code user-steps.json} under that directory, at any depth (a downloaded CI artifact keeps the module's path),
 * on this run's Testing Tool.
 *
 * @param settings the run's settings
 * @param workDir where git runs, the project's directory
 * @param runStart when the run started; files from earlier runs are ignored
 * @param env the environment ({@code GITHUB_STEP_SUMMARY}, {@code GITHUB_BASE_REF})
 * @param changedFiles the files changed against a base ref, e.g. {@link ChangedFiles#fromGit}
 * @param log where one-line notes go
 */
public record RunSummaryWriter(Settings settings, Path workDir, Instant runStart, Map<String, String> env,
        Function<String, Optional<ChangedFiles>> changedFiles, Consumer<String> log) {

    public static final String GITHUB_STEP_SUMMARY = "GITHUB_STEP_SUMMARY";
    static final String SKIPPED_DIR = ".summary";

    /** Asks git in {@code workDir}, and logs to stderr. */
    public static RunSummaryWriter of(Settings settings, Path workDir, Instant runStart, Map<String, String> env) {
        Consumer<String> log = line -> System.err.println("Unified UI Tests: " + line);
        return new RunSummaryWriter(settings, workDir, runStart, env,
                ref -> ChangedFiles.fromGit(ref, workDir, log), log);
    }

    /**
     * Writes {@code summary.md}, {@code report.html} and {@code summary.json}, if the run has any Unified UI Test,
     * and appends the summary to the GitHub job summary.
     *
     * @param skippedHere the Unified UI Tests this process skipped without running ({@code class#method})
     * @param processId names this process's list of skipped tests
     * @return the summary written, if any
     */
    public Optional<Path> write(Collection<String> skippedHere, String processId) throws IOException {
        Path evidenceDir = settings.evidenceDir();
        if (!skippedHere.isEmpty()) {
            Path list = evidenceDir.resolve(SKIPPED_DIR).resolve("skipped-" + processId + ".txt");
            Files.createDirectories(list.getParent());
            Files.writeString(list, String.join("\n", new TreeSet<>(skippedHere)) + "\n", StandardCharsets.UTF_8);
        }
        List<ScenarioResult> scenarios = scenarios(evidenceDir);
        int skipped = skipped(evidenceDir);
        if (scenarios.isEmpty() && skipped == 0) {
            return Optional.empty();
        }
        ChangedFiles changes = settings.userStepsChangedSince(env).flatMap(changedFiles).orElse(null);
        BaseRun base = changes == null ? null : settings.userStepsBaselineDir().map(this::baseRun).orElse(null);
        List<RunComparison> comparisons = settings.userStepsCompareDir()
                .map(dir -> RunComparison.of(settings.tool().name(), scenarios, otherRuns(dir))).orElse(List.of());
        RunSummary summary = new RunSummary(toolName(), scenarios, skipped, changes, base, comparisons);
        String markdown = summary.markdown();
        Path file = evidenceDir.resolve(RunSummary.FILE);
        Files.createDirectories(evidenceDir);
        Files.writeString(file, markdown, StandardCharsets.UTF_8);
        List<String> rootFiles = rootFiles(evidenceDir);
        Function<ScenarioResult, List<String>> evidence = s -> evidence(evidenceDir, rootFiles, s);
        Files.writeString(evidenceDir.resolve(RunReport.FILE), new RunReport(summary, evidence).html(),
                StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve(RunSummaryJson.FILE), new RunSummaryJson(summary, evidence).json(),
                StandardCharsets.UTF_8);
        String stepSummary = env.get(GITHUB_STEP_SUMMARY);
        if (stepSummary != null && !stepSummary.isBlank() && settings.userStepsGithubSummary()) {
            putInJobSummary(Path.of(stepSummary), key(evidenceDir), markdown);
        }
        return Optional.of(file);
    }

    private String toolName() {
        String name = settings.tool().name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** The run's {@code user-steps.json}: {@code <evidence-dir>/<class>/<method>/}, started in this run. */
    private List<ScenarioResult> scenarios(Path evidenceDir) throws IOException {
        if (!Files.isDirectory(evidenceDir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(evidenceDir, 3)) {
            files = walk.filter(p -> p.getFileName().toString().equals(UserStepsReport.JSON_FILE))
                    .filter(p -> evidenceDir.relativize(p).getNameCount() == 3).sorted().toList();
        }
        List<ScenarioResult> scenarios = new ArrayList<>();
        for (Path json : files) {
            String evidencePath = (evidenceDir.resolve(evidenceDir.relativize(json.getParent())).toString() + "/")
                    .replace('\\', '/');
            try {
                ScenarioResult scenario = ScenarioResult.parse(Files.readString(json, StandardCharsets.UTF_8),
                        evidencePath);
                if (!scenario.startedAt().isBefore(runStart)) {
                    scenarios.add(scenario);
                }
            } catch (IOException | RuntimeException e) {
                log.accept("left out of " + RunSummary.FILE + ", unreadable: " + json + " (" + e.getMessage() + ")");
            }
        }
        return scenarios;
    }

    /** Every readable {@code user-steps.json} under {@code dir}, at any depth; {@code what} names the run in the log. */
    private List<ScenarioResult> runIn(Path dir, String what) {
        List<ScenarioResult> scenarios = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(p -> p.getFileName().toString().equals(UserStepsReport.JSON_FILE)).sorted()
                        .toList();
            } catch (IOException | RuntimeException e) {
                files = List.of();
                log.accept(what + " could not be read: " + dir + " (" + e.getMessage() + ")");
            }
            for (Path json : files) {
                String evidencePath = (json.getParent().toString() + "/").replace('\\', '/');
                try {
                    scenarios.add(ScenarioResult.parse(Files.readString(json, StandardCharsets.UTF_8), evidencePath));
                } catch (IOException | RuntimeException e) {
                    log.accept("left out of " + what + ", unreadable: " + json + " (" + e.getMessage() + ")");
                }
            }
        }
        return scenarios;
    }

    /** The scenarios under {@code dir} that ran on another Testing Tool than this run's; says so when there is none. */
    private List<ScenarioResult> otherRuns(Path dir) {
        List<ScenarioResult> others = runIn(dir, "the run to compare with").stream()
                .filter(s -> s.tool() != null && !s.tool().equalsIgnoreCase(settings.tool().name())).toList();
        if (others.isEmpty()) {
            log.accept("no run on another Testing Tool in " + dir + " to compare with");
        }
        return others;
    }

    /** The base run's scenarios under {@code dir} on this run's Testing Tool; none when it has no readable one. */
    private BaseRun baseRun(Path dir) {
        List<ScenarioResult> scenarios = runIn(dir, "the base run");
        BaseRun base = BaseRun.of(scenarios, settings.tool().name());
        if (base.isEmpty()) {
            log.accept("no base run in " + dir + " to compare User Steps with");
        }
        return base;
    }

    private boolean isFromThisRun(Path file) {
        try {
            return !Files.getLastModifiedTime(file).toInstant().isBefore(runStart);
        } catch (IOException e) {
            return false;
        }
    }

    /** The file names at the evidence directory's root, sorted: where failure evidence is written. */
    private static List<String> rootFiles(Path evidenceDir) throws IOException {
        try (Stream<Path> files = Files.list(evidenceDir)) {
            return files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** A scenario's failure evidence, then its Playwright trace if this run kept one. */
    private List<String> evidence(Path evidenceDir, List<String> rootFiles, ScenarioResult scenario) {
        List<String> files = new ArrayList<>(failureEvidence(rootFiles, scenario));
        Path scenarioDir = Path.of(scenario.evidencePath());
        Path trace = scenarioDir.resolve("trace.zip");
        if (Files.isRegularFile(trace) && isFromThisRun(trace)) {
            files.add(evidenceDir.relativize(trace).toString().replace('\\', '/'));
        }
        return files;
    }

    /**
     * A scenario's failure evidence: the files named {@code <test class>.<test method>.*} at the root
     * ({@code AdapterSession.writeFailureEvidence}), for a failed scenario only.
     */
    static List<String> failureEvidence(List<String> rootFiles, ScenarioResult scenario) {
        if (!scenario.failed()) {
            return List.of();
        }
        String prefix = scenario.testClass() + "." + scenario.testMethod() + ".";
        return rootFiles.stream().filter(f -> f.startsWith(prefix)).toList();
    }

    /** The tests skipped without running, listed by this run's processes. */
    private int skipped(Path evidenceDir) throws IOException {
        Path dir = evidenceDir.resolve(SKIPPED_DIR);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        TreeSet<String> tests = new TreeSet<>();
        try (Stream<Path> lists = Files.list(dir)) {
            for (Path list : lists.filter(p -> p.getFileName().toString().startsWith("skipped-")).toList()) {
                if (!Files.getLastModifiedTime(list).toInstant().isBefore(runStart)) {
                    Files.readAllLines(list, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank())
                            .forEach(tests::add);
                }
            }
        }
        return tests.size();
    }

    /** Names the run's block in the job summary: one per evidence directory and run. */
    private String key(Path evidenceDir) {
        String id = evidenceDir.toAbsolutePath().normalize() + "@" + runStart;
        return Integer.toHexString(id.hashCode());
    }

    /**
     * Appends the summary to the job summary file, or replaces the block an earlier fork of the same run put
     * there. Locked, since forks may end together.
     */
    static void putInJobSummary(Path file, String key, String markdown) throws IOException {
        String start = "<!-- unified-ui-tests " + key + " -->\n";
        String end = "<!-- /unified-ui-tests " + key + " -->\n";
        String block = start + markdown + end;
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            while (buffer.hasRemaining() && channel.read(buffer, buffer.position()) >= 0) {
                // read it all
            }
            String content = new String(buffer.array(), StandardCharsets.UTF_8);
            int from = content.indexOf(start);
            int to = from < 0 ? -1 : content.indexOf(end, from);
            String updated;
            if (from >= 0 && to >= 0) {
                updated = content.substring(0, from) + block + content.substring(to + end.length());
            } else {
                String separator = content.isEmpty() || content.endsWith("\n\n") ? ""
                        : content.endsWith("\n") ? "\n" : "\n\n";
                updated = content + separator + block;
            }
            byte[] bytes = updated.getBytes(StandardCharsets.UTF_8);
            channel.truncate(0);
            channel.write(ByteBuffer.wrap(bytes), 0);
        }
    }
}
