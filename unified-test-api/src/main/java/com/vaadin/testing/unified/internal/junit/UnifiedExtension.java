package com.vaadin.testing.unified.internal.junit;

import com.vaadin.testing.unified.internal.Settings;
import com.vaadin.testing.unified.internal.TestLifecycle;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.UnifiedPage;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 6 extension behind {@code @UnifiedUITest}: translates JUnit's test context into calls to
 * {@link TestLifecycle}, which skips tests whose Capabilities the run lacks, opens the test's
 * {@link UnifiedPage} for injection, and after the test reports its User Steps and closes it.
 */
public final class UnifiedExtension implements ExecutionCondition, ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(UnifiedExtension.class);
    private static final String OPEN_TEST = "open-test";
    private static final String STEPS = "user-steps";
    private static final Pattern INVOCATION = Pattern.compile("-invocation:#(\\d+)\\]$");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (context.getElement().isEmpty()) {
            return ConditionEvaluationResult.enabled("no element");
        }
        TestLifecycle.Decision decision = lifecycle(context)
                .decide(context.getElement().get(), context.getTestClass().orElse(null));
        return decision.runs()
                ? ConditionEvaluationResult.enabled(decision.reason())
                : ConditionEvaluationResult.disabled(decision.reason());
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == UnifiedPage.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        TestLifecycle lifecycle = lifecycle(context);
        // one recorder per test, even when it is given more than one page
        StepRecorder steps = context.getStore(NAMESPACE).computeIfAbsent(STEPS, key -> new StepRecorder(),
                StepRecorder.class);
        TestLifecycle.OpenTest test = lifecycle.open(new JUnitAdapterContext(context, lifecycle.settings()), steps,
                context.getTestMethod().isPresent() && context.getTestClass().isPresent() ? testId(context) : null);
        context.getStore(NAMESPACE).put(OPEN_TEST, test);
        return test.page();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestLifecycle.OpenTest test = context.getStore(NAMESPACE).remove(OPEN_TEST, TestLifecycle.OpenTest.class);
        context.getStore(NAMESPACE).remove(STEPS);
        if (test == null) {
            return;
        }
        List<Path> evidence = test.close(context.getExecutionException().orElse(null), testId(context));
        if (!evidence.isEmpty()) {
            context.publishReportEntry("unified.failure-evidence", TestLifecycle.describe(evidence));
        }
    }

    /** The test as its reports name it, with the invocation number of a parameterized or repeated test. */
    static TestLifecycle.TestId testId(ExtensionContext context) {
        Matcher invocation = INVOCATION.matcher(context.getUniqueId());
        return new TestLifecycle.TestId(context.getRequiredTestClass().getName(),
                context.getRequiredTestMethod().getName(), displayName(context),
                invocation.find() ? Integer.valueOf(invocation.group(1)) : null);
    }

    /** {@code @DisplayName}, or the method's name when JUnit's default ({@code name(UnifiedPage)}) is used. */
    private static String displayName(ExtensionContext context) {
        String name = context.getDisplayName();
        String method = context.getRequiredTestMethod().getName();
        return name.startsWith(method + "(") && name.endsWith(")") ? method : name;
    }

    /** The JUnit integration's settings: JUnit configuration parameters, which fall back to system properties. */
    private static TestLifecycle lifecycle(ExtensionContext context) {
        return TestLifecycle.create(Settings.from(context::getConfigurationParameter));
    }
}
