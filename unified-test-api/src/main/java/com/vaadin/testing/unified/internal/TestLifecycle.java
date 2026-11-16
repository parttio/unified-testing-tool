package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What {@code @UnifiedUITest} does for each test, with no Test Runner types (ADR 0008): decide whether the
 * test runs on this run's Testing Tool, open it to get its {@link UnifiedPage}, and close it, writing the
 * failure evidence when it failed. A Test Runner integration (the JUnit extension, {@code internal.junit.UnifiedExtension})
 * only translates its own test context into these calls.
 */
public final class TestLifecycle {

    /** Whether a test runs, and why. */
    public record Decision(boolean runs, String reason) {}

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
     */
    public Decision decide(AnnotatedElement element, Class<?> testClass) {
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

    /** Opens one test on this run's Testing Tool: a fresh session and its page. */
    public OpenTest open(AdapterContext context) {
        TestingTool tool = settings.tool();
        AdapterSession session = adapters.apply(tool).open(context);
        return new OpenTest(session, new UnifiedPageImpl(new TestRun(tool, session, settings.timeout())));
    }

    /** One open test. Close it exactly once, when the test ends. */
    public final class OpenTest {
        private final AdapterSession session;
        private final UnifiedPage page;

        private OpenTest(AdapterSession session, UnifiedPage page) {
            this.session = session;
            this.page = page;
        }

        public UnifiedPage page() {
            return page;
        }

        /**
         * Closes the test's session. When {@code failure} is not null, first writes the failure evidence
         * and names the files on the failure itself (a suppressed exception), so they show in the test
         * report. Problems while writing never hide the test's own failure.
         *
         * @param name the test's name for the evidence files, such as {@code com.example.LoginTest.logsIn}
         * @return the evidence files written, empty when the test passed or nothing was written
         */
        public List<Path> close(Throwable failure, String name) {
            try {
                return failure == null ? List.of() : writeEvidence(failure, name);
            } finally {
                session.close();
            }
        }

        private List<Path> writeEvidence(Throwable failure, String name) {
            try {
                List<Path> files = session.writeFailureEvidence(settings.evidenceDir(), name);
                if (!files.isEmpty()) {
                    failure.addSuppressed(new FailureEvidence("Failure evidence: " + describe(files)));
                }
                return files;
            } catch (Exception | LinkageError e) {
                failure.addSuppressed(new FailureEvidence("Failure evidence could not be written: " + e));
                return List.of();
            }
        }
    }

    /** The evidence files as the report shows them: absolute paths, comma-separated. */
    public static String describe(List<Path> files) {
        return files.stream().map(p -> p.toAbsolutePath().toString()).collect(Collectors.joining(", "));
    }

    /** Carries the evidence file names on a failed test; no stack trace of its own. */
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
        throw new IllegalStateException("No adapter for vaadin.testing.tool=" + tool.name().toLowerCase()
                + ". Add the test dependency com.vaadin:unified-test-" + tool.name().toLowerCase()
                + ".");
    }
}
