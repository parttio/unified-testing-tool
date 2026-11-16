package com.vaadin.testing.unified.internal.junit;

import com.vaadin.testing.unified.internal.Settings;
import com.vaadin.testing.unified.internal.TestLifecycle;
import com.vaadin.testing.unified.UnifiedPage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 6 extension behind {@code @UnifiedUITest}: translates JUnit's test context into calls to
 * {@link TestLifecycle}, which skips tests whose Capabilities the run lacks, opens the test's
 * {@link UnifiedPage} for injection and closes it after the test.
 */
public final class UnifiedExtension implements ExecutionCondition, ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(UnifiedExtension.class);
    private static final String OPEN_TEST = "open-test";

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
        TestLifecycle.OpenTest test = lifecycle.open(new JUnitAdapterContext(context, lifecycle.settings()));
        context.getStore(NAMESPACE).put(OPEN_TEST, test);
        return test.page();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestLifecycle.OpenTest test = context.getStore(NAMESPACE).remove(OPEN_TEST, TestLifecycle.OpenTest.class);
        if (test == null) {
            return;
        }
        String name = context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName();
        List<Path> evidence = test.close(context.getExecutionException().orElse(null), name);
        if (!evidence.isEmpty()) {
            context.publishReportEntry("unified.failure-evidence", TestLifecycle.describe(evidence));
        }
    }

    /** The JUnit integration's settings: JUnit configuration parameters, which fall back to system properties. */
    private static TestLifecycle lifecycle(ExtensionContext context) {
        return TestLifecycle.create(Settings.from(context::getConfigurationParameter));
    }
}
