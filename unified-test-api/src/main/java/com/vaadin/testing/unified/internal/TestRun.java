package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.spi.AdapterSession;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * One running test: its Testing Tool, adapter session and timeout, and the recorder of its User Steps, shared by
 * all its windows. {@code window} names the window the steps run in: null for the test's first window.
 */
public record TestRun(TestingTool tool, AdapterSession session, Duration timeout, StepRecorder steps,
        String window) {

    private static final long POLL_MILLIS = 50;

    /** A run of its own, with a fresh recorder: for tests of the Unified API itself. */
    public TestRun(TestingTool tool, AdapterSession session, Duration timeout) {
        this(tool, session, timeout, new StepRecorder(), null);
    }

    public TestRun withTimeout(Duration newTimeout) {
        return new TestRun(tool, session, newTimeout, steps, window);
    }

    /** Another window of the same test, recording into the same User Steps. */
    public TestRun inWindow(AdapterSession windowSession, String windowName) {
        return new TestRun(tool, windowSession, timeout, steps, windowName);
    }

    /**
     * Calls {@code attempt} until it returns a non-null value or the timeout ends. Exceptions count as
     * "not yet"; the last one is the cause of the failure. Between attempts the session syncs.
     */
    public <T> T poll(Supplier<T> attempt, Supplier<String> failure) {
        long deadline = System.nanoTime() + timeout.toNanos();
        RuntimeException last = null;
        while (true) {
            try {
                T result = attempt.get();
                if (result != null) {
                    return result;
                }
                last = null;
            } catch (RuntimeException e) {
                last = e;
            }
            if (System.nanoTime() > deadline) {
                AssertionError error = new AssertionError(failure.get() + " (waited " + timeout.toMillis() + " ms)");
                if (last != null) {
                    error.initCause(last);
                }
                throw error;
            }
            session.sync();
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting", e);
            }
        }
    }

    /** Fails if the run's Testing Tool has no real browser. */
    public void requireBrowser(String feature) {
        if (!tool.hasRealBrowser()) {
            throw new UnsupportedOperationException(feature + " needs a real browser, but this run uses "
                    + tool + ". Annotate the test with @RequiresBrowser to skip it on " + tool + ".");
        }
    }
}
