package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.internal.steps.UserStepsReport;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * JUnit 6 extension behind {@code @UnifiedUITest}: picks the run's Testing Tool adapter, skips tests whose
 * Capabilities it lacks, injects {@link UnifiedPage}, and after each test reports its User Steps and closes
 * the session.
 */
public final class UnifiedExtension implements ExecutionCondition, ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(UnifiedExtension.class);
    private static final String SESSION = "session";
    private static final String STEPS = "user-steps";

    // ---------------------------------------------------------------- Capabilities

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        TestingTool tool = settings(context).tool();
        Optional<AnnotatedElement> element = context.getElement();
        if (element.isEmpty()) {
            return ConditionEvaluationResult.enabled("no element");
        }
        Optional<RequiresTestingTool> requiredTool = find(context, RequiresTestingTool.class);
        if (requiredTool.isPresent() && Arrays.stream(requiredTool.get().value()).noneMatch(t -> t == tool)) {
            return ConditionEvaluationResult.disabled("Requires " + Arrays.toString(requiredTool.get().value())
                    + ", this run uses " + tool);
        }
        if (find(context, RequiresBrowser.class).isPresent() && !tool.hasRealBrowser()) {
            return ConditionEvaluationResult.disabled("Requires a real browser, this run uses " + tool);
        }
        return ConditionEvaluationResult.enabled("Runs on " + tool);
    }

    private static <A extends java.lang.annotation.Annotation> Optional<A> find(ExtensionContext context, Class<A> type) {
        Optional<A> onElement = context.getElement().flatMap(e -> AnnotationSupport.findAnnotation(e, type));
        if (onElement.isPresent()) {
            return onElement;
        }
        return context.getTestClass().flatMap(c -> AnnotationSupport.findAnnotation(c, type));
    }

    // ---------------------------------------------------------------- UnifiedPage injection

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == UnifiedPage.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Settings settings = settings(context);
        TestingTool tool = settings.tool();
        Duration timeout = settings.timeout();
        TestingToolAdapter adapter = adapterFor(tool);
        AdapterSession session = adapter.open(new JUnitAdapterContext(context, settings));
        context.getStore(NAMESPACE).put(SESSION, session);
        StepRecorder steps = context.getStore(NAMESPACE).computeIfAbsent(STEPS, key -> new StepRecorder(),
                StepRecorder.class);
        return new UnifiedPageImpl(new TestRun(tool, session, timeout, steps, null));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        AdapterSession session = context.getStore(NAMESPACE).remove(SESSION, AdapterSession.class);
        StepRecorder steps = context.getStore(NAMESPACE).remove(STEPS, StepRecorder.class);
        try {
            if (steps != null) {
                reportUserSteps(context, steps);
            }
            if (session != null) {
                context.getExecutionException().ifPresent(failure -> writeEvidence(context, session, failure));
            }
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    // ---------------------------------------------------------------- User Steps

    /**
     * Writes {@code user-steps.txt} and {@code user-steps.json} into the test's evidence directory, prints the
     * text to stdout (the CI log) as {@code vaadin.testing.user-steps.log} says, and adds it to a failure.
     * Problems while writing never hide the test's own failure, nor fail a passing test.
     */
    private static void reportUserSteps(ExtensionContext context, StepRecorder steps) {
        steps.finish();
        Settings settings = settings(context);
        Throwable failure = context.getExecutionException().orElse(null);
        String status = failure == null ? "passed"
                : failure.getClass().getName().equals("org.opentest4j.TestAbortedException") ? "aborted" : "failed";
        UserStepsReport report = new UserStepsReport(context.getRequiredTestClass().getName(),
                context.getRequiredTestMethod().getName(), displayName(context),
                settings.tool().name().toLowerCase(Locale.ROOT), status, failure, steps);
        String text = report.text();
        Path directory = testEvidenceDir(context, settings);
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
    static Path testEvidenceDir(ExtensionContext context, Settings settings) {
        String className = context.getRequiredTestClass().getName();
        String classDir = className.substring(className.lastIndexOf('.') + 1);
        String methodDir = context.getRequiredTestMethod().getName();
        Matcher invocation = INVOCATION.matcher(context.getUniqueId());
        if (invocation.find()) {
            methodDir += "[" + invocation.group(1) + "]";
        }
        return settings.evidenceDir().resolve(classDir).resolve(methodDir);
    }

    private static final Pattern INVOCATION = Pattern.compile("-invocation:#(\\d+)\\]$");

    /** {@code @DisplayName}, or the method's name when JUnit's default ({@code name(UnifiedPage)}) is used. */
    private static String displayName(ExtensionContext context) {
        String name = context.getDisplayName();
        String method = context.getRequiredTestMethod().getName();
        return name.startsWith(method + "(") && name.endsWith(")") ? method : name;
    }

    /**
     * Writes the failure evidence and names the files on the failure itself (a suppressed exception), so
     * they show in the test report. Problems while writing never hide the test's own failure.
     */
    private static void writeEvidence(ExtensionContext context, AdapterSession session, Throwable failure) {
        String name = context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName();
        try {
            List<Path> files = session.writeFailureEvidence(settings(context).evidenceDir(), name);
            if (!files.isEmpty()) {
                String list = files.stream().map(p -> p.toAbsolutePath().toString()).collect(Collectors.joining(", "));
                failure.addSuppressed(new FailureEvidence("Failure evidence: " + list));
                context.publishReportEntry("unified.failure-evidence", list);
            }
        } catch (Exception | LinkageError e) {
            failure.addSuppressed(new FailureEvidence("Failure evidence could not be written: " + e));
        }
    }

    /** Carries the evidence file names, or the User Steps, on a failed test; no stack trace of its own. */
    static final class FailureEvidence extends RuntimeException {
        FailureEvidence(String message) {
            super(message, null, false, false);
        }
    }

    /** The JUnit integration's settings: JUnit configuration parameters, which fall back to system properties. */
    private static Settings settings(ExtensionContext context) {
        return Settings.from(context::getConfigurationParameter);
    }

    private static TestingToolAdapter adapterFor(TestingTool tool) {
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
