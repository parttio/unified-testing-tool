package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.internal.logs.ServerLogs;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.internal.steps.UserStepsReport;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.Evidence;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What {@code @UnifiedUITest} does for each test, with no Test Runner types (ADR 0008): decide whether the
 * test runs on this run's Testing Tool, open it to get its {@link UnifiedPage}, and close it, reporting its
 * User Steps and writing the failure evidence when it failed. A Test Runner integration (the JUnit extension, {@code internal.junit.UnifiedExtension})
 * only translates its own test context into these calls.
 */
public final class TestLifecycle {

    /** Whether a test runs, and why. */
    public record Decision(boolean runs, String reason) {}

    /**
     * Which test this is, as its reports name it.
     *
     * @param displayName the name the Test Runner shows, or {@code methodName}
     * @param invocation the n-th run of a parameterized or repeated test, or null
     */
    public record TestId(String className, String methodName, String displayName, Integer invocation) {
        /**
         * Whether the test's author wrote its name ({@code @DisplayName}, the method's name), which the reports show
         * unmasked; a parameterized or repeated invocation's name is built from its arguments (ADR 0014).
         */
        public boolean authoredName() {
            return invocation == null;
        }
    }

    /** The name of a test's failure evidence files in its evidence directory: {@code failure.png}, … */
    static final String FAILURE_EVIDENCE = "failure";

    private final Settings settings;
    private final Function<TestingTool, TestingToolAdapter> adapters;

    TestLifecycle(Settings settings, Function<TestingTool, TestingToolAdapter> adapters) {
        this.settings = settings;
        this.adapters = adapters;
    }

    /** The lifecycle for a run with these settings, using the adapters registered with {@link ServiceLoader}. */
    public static TestLifecycle create(Settings settings) {
        return new TestLifecycle(settings, TestLifecycle::registeredAdapter);
    }

    public Settings settings() {
        return settings;
    }

    // ---------------------------------------------------------------- Capabilities

    /**
     * Whether the test runs on this run's Testing Tool, from its Capabilities ({@link RequiresTestingTool},
     * {@link RequiresBrowser}) on {@code element} (a test method or class) or else on {@code testClass}.
     * Throws {@link IllegalStateException} when the test's Vaadin is older than the Unified API needs: this runs
     * before anything else of the test, a Spring context included, so that failure comes first.
     */
    public Decision decide(AnnotatedElement element, Class<?> testClass) {
        VaadinVersionCheck.check();
        TestingTool tool = settings.tool();
        Optional<RequiresTestingTool> requiredTool = find(element, testClass, RequiresTestingTool.class);
        if (requiredTool.isPresent() && Arrays.stream(requiredTool.get().value()).noneMatch(t -> t == tool)) {
            return new Decision(false, "Requires " + Arrays.toString(requiredTool.get().value())
                    + ", this run uses " + tool);
        }
        if (find(element, testClass, RequiresBrowser.class).isPresent() && !tool.hasRealBrowser()) {
            return new Decision(false, "Requires a real browser, this run uses " + tool);
        }
        return new Decision(true, "Runs on " + tool);
    }

    private static <A extends Annotation> Optional<A> find(AnnotatedElement element, Class<?> testClass, Class<A> type) {
        Optional<A> onElement = element == null ? Optional.empty() : findAnnotation(element, type, new HashSet<>());
        if (onElement.isPresent() || testClass == null) {
            return onElement;
        }
        return findAnnotation(testClass, type, new HashSet<>());
    }

    /**
     * {@code type} present on {@code element}, or meta-present through its annotations; on a class, also on its
     * interfaces, and on its superclass when {@code type} is {@link Inherited}.
     */
    static <A extends Annotation> Optional<A> findAnnotation(AnnotatedElement element, Class<A> type, Set<Object> visited) {
        if (!visited.add(element)) {
            return Optional.empty();
        }
        A direct = element.getDeclaredAnnotation(type);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!annotationType.getName().startsWith("java.lang.annotation.")) {
                Optional<A> meta = findAnnotation(annotationType, type, visited);
                if (meta.isPresent()) {
                    return meta;
                }
            }
        }
        if (element instanceof Class<?> c) {
            for (Class<?> i : c.getInterfaces()) {
                Optional<A> onInterface = findAnnotation(i, type, visited);
                if (onInterface.isPresent()) {
                    return onInterface;
                }
            }
            if (type.isAnnotationPresent(Inherited.class) && c.getSuperclass() != null) {
                return findAnnotation(c.getSuperclass(), type, visited);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- open and close

    /**
     * Opens one test on this run's Testing Tool: a fresh session and its page, recording its User Steps into
     * {@code steps} (shared by every page the test is given).
     */
    public OpenTest open(AdapterContext context, StepRecorder steps) {
        return open(context, steps, null);
    }

    /**
     * {@link #open(AdapterContext, StepRecorder)} for a known test: its failures name its scenario and evidence
     * directory from the start.
     */
    public OpenTest open(AdapterContext context, StepRecorder steps, TestId test) {
        if (test != null) {
            steps.describeTest(test.displayName(), test.authoredName(), evidenceDirName(test));
        }
        TestingTool tool = settings.tool();
        AdapterSession session = adapters.apply(tool).open(context);
        steps.session(null, session);
        if (test != null && settings.userStepsSnapshots()) {
            Path snapshots = testEvidenceDir(test).resolve(StepRecorder.SNAPSHOT_DIR);
            deleteSnapshots(snapshots);
            steps.snapshotsInto(snapshots);
        }
        ServerLogs.Listening logs = settings.userStepsServerLog().map(level -> ServerLogs.start(steps, level))
                .orElse(null);
        return new OpenTest(session, steps, new UnifiedPageImpl(new TestRun(tool, session, settings.timeout(), steps, null)),
                logs);
    }

    /** One open test. Close it exactly once, when the test ends. */
    public final class OpenTest {
        private final AdapterSession session;
        private final StepRecorder steps;
        private final UnifiedPage page;
        private final ServerLogs.Listening logs;

        private OpenTest(AdapterSession session, StepRecorder steps, UnifiedPage page, ServerLogs.Listening logs) {
            this.session = session;
            this.steps = steps;
            this.page = page;
            this.logs = logs;
        }

        public UnifiedPage page() {
            return page;
        }

        /**
         * Ends the test: reports its User Steps, writes the failure evidence when {@code failure} is not null,
         * and closes its session. A failed test gets its User Steps and the evidence file names on the failure
         * itself (suppressed exceptions), so they show in the test report. Problems while writing never hide
         * the test's own failure, nor fail a passing test.
         *
         * @param failure what the test threw, or null when it passed
         * @return the evidence files written, empty when the test passed or nothing was written
         */
        public List<Path> close(Throwable failure, TestId test) {
            try {
                // the last console errors, then no more log lines: the reports are written from here on
                steps.errors();
                if (logs != null) {
                    logs.close();
                }
                // the failure evidence first: user-steps.json and the failure name it, and where the screenshot
                // shows the Step Target
                Path directory = testEvidenceDir(test);
                deleteFailureEvidence(directory);
                List<Path> evidence = failure == null ? List.of() : writeEvidence(failure, directory);
                reportUserSteps(steps, failure, test);
                keepRecording(failure, test);
                return evidence;
            } finally {
                session.close();
            }
        }

        /** What the session recorded for the test, such as Playwright's trace, into its evidence directory. */
        private void keepRecording(Throwable failure, TestId test) {
            boolean failed = failure != null
                    && !failure.getClass().getName().equals("org.opentest4j.TestAbortedException");
            try {
                List<Path> files = session.testEnded(testEvidenceDir(test), failed);
                if (failed && !files.isEmpty()) {
                    failure.addSuppressed(new FailureEvidence("Recording: " + describe(files)));
                }
            } catch (Exception | LinkageError e) {
                System.err.println("The recording of " + test + " could not be kept: " + e);
            }
        }

        /** The Testing Tool's failure evidence, or why it could not be written. */
        private record ToolEvidence(List<Path> files, String problem) {
        }

        private ToolEvidence toolEvidence(Throwable failure, Path directory) {
            try {
                Evidence evidence = session.writeFailureEvidence(directory, FAILURE_EVIDENCE,
                        steps.targetOf(failure));
                steps.failureHighlight(evidence.highlight());
                return new ToolEvidence(evidence.files(), null);
            } catch (Exception | LinkageError e) {
                return new ToolEvidence(List.of(), "Failure evidence could not be written: " + e);
            }
        }

        /**
         * The Testing Tool's evidence, then the Findable View, into the test's evidence directory, each written even
         * when the other can't be; their names go to the failure and to {@code user-steps.json}.
         */
        private List<Path> writeEvidence(Throwable failure, Path directory) {
            ToolEvidence tool = toolEvidence(failure, directory);
            List<Path> files = new ArrayList<>(tool.files());
            if (tool.problem() != null) {
                failure.addSuppressed(new FailureEvidence(tool.problem()));
            }
            findableView(directory, failure).ifPresent(files::add);
            if (!files.isEmpty()) {
                failure.addSuppressed(new FailureEvidence("Failure evidence: " + describe(files)));
            }
            List<String> names = files.stream().map(f -> nameIn(directory, f)).toList();
            steps.failureEvidence(names);
            if (failure instanceof UnifiedAssertionError unified) {
                unified.parts().evidenceFiles(names);
            }
            return List.copyOf(files);
        }

        /** The file's path relative to the test's evidence directory, or its name when it lies elsewhere. */
        private static String nameIn(Path directory, Path file) {
            Path dir = directory.toAbsolutePath().normalize();
            Path path = file.toAbsolutePath().normalize();
            return (path.startsWith(dir) ? dir.relativize(path) : path.getFileName()).toString().replace('\\', '/');
        }

        /** The Findable View of the test's first window, {@code failure.view.txt}; none if it can't be read. */
        private Optional<Path> findableView(Path directory, Throwable failure) {
            try {
                TestRun run = ((UnifiedPageImpl) page).run();
                // the items of the grid or message list a failed row or message step was about, after it (#590)
                String items = ItemsView.ofFailedStep(run, steps, failure);
                return Optional.of(FindableView.write(run, directory, FAILURE_EVIDENCE, items));
            } catch (Exception | LinkageError e) {
                return Optional.empty();
            }
        }
    }

    // ---------------------------------------------------------------- User Steps

    /**
     * Writes {@code user-steps.txt} and {@code user-steps.json} into the test's evidence directory, prints the
     * text to stdout (the CI log) as {@code vaadin.testing.user-steps.log} says, and adds it to a failure.
     * Problems while writing never hide the test's own failure, nor fail a passing test.
     */
    private void reportUserSteps(StepRecorder steps, Throwable failure, TestId test) {
        steps.finish();
        String status = failure == null ? "passed"
                : failure.getClass().getName().equals("org.opentest4j.TestAbortedException") ? "aborted" : "failed";
        UserStepsReport report = new UserStepsReport(test.className(), test.methodName(), test.displayName(),
                settings.tool().name().toLowerCase(Locale.ROOT), status, failure, steps);
        // the test may have been opened before it was named; the reports' scenario and a failure need its name
        steps.describeTest(test.displayName(), test.authoredName(), evidenceDirName(test));
        if (failure instanceof UnifiedAssertionError unified) {
            // a failure outside any User Step gets the rest here, before it is shown
            steps.completeFailure(unified, report.codePath());
        }
        String text = report.text();
        Path directory = testEvidenceDir(test);
        String written;
        try {
            report.write(directory);
            written = directory.toAbsolutePath().toString();
        } catch (Exception | LinkageError e) {
            written = "not written: " + e;
            System.err.println("User Steps could not be written to " + directory + ": " + e);
        }
        Settings.UserStepsLog log = settings.userStepsLog();
        if (log == Settings.UserStepsLog.ALL || (log == Settings.UserStepsLog.FAILED && status.equals("failed"))) {
            System.out.print(text);
            System.out.flush();
        }
        if (failure != null && status.equals("failed")) {
            failure.addSuppressed(new FailureEvidence("User Steps (" + written + "):\n" + text));
        }
    }

    /**
     * {@code <evidence-dir>/<test class>/<test method>/}: the class without its package (nested classes keep
     * their {@code $}), and {@code [n]} after the method for the n-th invocation of a parameterized or repeated
     * test.
     */
    Path testEvidenceDir(TestId test) {
        String className = test.className();
        String classDir = className.substring(className.lastIndexOf('.') + 1);
        String methodDir = test.methodName() + (test.invocation() == null ? "" : "[" + test.invocation() + "]");
        return settings.evidenceDir().resolve(classDir).resolve(methodDir);
    }

    /** Removes the failure evidence an earlier run of the test left, so a passing test has none. */
    private static void deleteFailureEvidence(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().startsWith(FAILURE_EVIDENCE + "."))
                    .toList()) {
                Files.deleteIfExists(file);
            }
        } catch (java.io.IOException e) {
            System.err.println("Old failure evidence could not be removed from " + directory + ": " + e);
        }
    }

    /** Removes the snapshots an earlier run of the test left, so the directory holds this run's only. */
    private static void deleteSnapshots(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        } catch (java.io.IOException e) {
            System.err.println("Old snapshots could not be removed from " + directory + ": " + e);
        }
    }

    /** The test's evidence directory as a failure shows it: {@code target/…/EditPersonTest/editsAPerson/}. */
    private String evidenceDirName(TestId test) {
        return testEvidenceDir(test).toString().replace('\\', '/') + "/";
    }

    /** The evidence files as the report shows them: absolute paths, comma-separated. */
    public static String describe(List<Path> files) {
        return files.stream().map(p -> p.toAbsolutePath().toString()).collect(Collectors.joining(", "));
    }

    /** Carries the evidence file names, or the User Steps, on a failed test; no stack trace of its own. */
    static final class FailureEvidence extends RuntimeException {
        FailureEvidence(String message) {
            super(message, null, false, false);
        }
    }

    private static TestingToolAdapter registeredAdapter(TestingTool tool) {
        for (TestingToolAdapter adapter : ServiceLoader.load(TestingToolAdapter.class)) {
            if (adapter.tool() == tool) {
                return adapter;
            }
        }
        // the alphas' group id; it becomes com.vaadin when the Unified API ships with Vaadin (spec 10, Packaging)
        throw new IllegalStateException("No adapter for vaadin.testing.tool=" + tool.name().toLowerCase()
                + ". Add the test dependency org.vaadin.addons:unified-test-" + tool.name().toLowerCase()
                + " (its version is in unified-test-bom).");
    }
}
