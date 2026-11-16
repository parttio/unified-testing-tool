package com.vaadin.testing.unified.internal;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Writes the run summary when the JUnit Platform has run the test plan: registered through
 * {@code META-INF/services}, so it runs with every launcher, Surefire's and IDEs' alike.
 * <p>
 * Only the outermost test plan of the process counts: tests that launch other tests (through the JUnit
 * launcher, with their own evidence directory) don't write a summary mid-run.
 */
public final class RunSummaryListener implements TestExecutionListener {

    private static final AtomicInteger RUNNING_PLANS = new AtomicInteger();
    /** The Unified UI Tests this process skipped without running, over all its test plans. */
    private static final Set<String> SKIPPED = ConcurrentHashMap.newKeySet();

    private boolean outermost;
    private TestPlan plan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        outermost = RUNNING_PLANS.getAndIncrement() == 0;
        plan = testPlan;
    }

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {
        if (!outermost || plan == null) {
            return;
        }
        if (identifier.isTest()) {
            skipped(identifier);
        } else {
            plan.getDescendants(identifier).stream().filter(TestIdentifier::isTest).forEach(this::skipped);
        }
    }

    private void skipped(TestIdentifier test) {
        Optional<TestSource> source = test.getSource();
        if (source.isPresent() && source.get() instanceof MethodSource method) {
            if (isUnified(method.getJavaClass())) {
                SKIPPED.add(method.getClassName() + "#" + method.getMethodName() + "#" + test.getUniqueId());
            }
        } else if (source.isPresent() && source.get() instanceof ClassSource type && isUnified(type.getJavaClass())) {
            SKIPPED.add(type.getClassName() + "#" + test.getUniqueId());
        }
    }

    /** Whether the class, or a class it is nested in, registers the Unified extension. */
    private static boolean isUnified(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
            boolean extended = AnnotationSupport.findRepeatableAnnotations(c, ExtendWith.class).stream()
                    .anyMatch(e -> Arrays.asList(e.value()).contains(UnifiedExtension.class));
            if (extended) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        RUNNING_PLANS.decrementAndGet();
        if (!outermost) {
            return;
        }
        Settings settings = Settings.from(key -> testPlan.getConfigurationParameters().get(key));
        try {
            RunSummaryWriter.of(settings, Path.of("").toAbsolutePath(), runStart(), System.getenv())
                    .write(SKIPPED, String.valueOf(ProcessHandle.current().pid()));
        } catch (Exception | LinkageError e) {
            // a summary that can't be written never fails the run
            System.err.println("Unified UI Tests: the run summary could not be written: " + e);
        }
    }

    /**
     * When the run started. In a Surefire fork, the Maven JVM that started the forks (Surefire may start them
     * through a shell), so every fork reads the files of all forks of this run; otherwise this JVM.
     */
    static Instant runStart() {
        Instant jvmStart = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        boolean surefireFork = System.getProperty("surefire.real.class.path") != null
                || System.getProperty("surefire.test.class.path") != null;
        if (!surefireFork) {
            return jvmStart;
        }
        Optional<ProcessHandle> ancestor = ProcessHandle.current().parent();
        for (int level = 0; level < 3 && ancestor.isPresent(); level++) {
            ProcessHandle.Info info = ancestor.get().info();
            boolean java = info.command().map(c -> Path.of(c).getFileName().toString().startsWith("java"))
                    .orElse(false);
            if (java) {
                return info.startInstant().filter(start -> start.isBefore(jvmStart)).orElse(jvmStart);
            }
            ancestor = ancestor.get().parent();
        }
        return jvmStart;
    }
}
