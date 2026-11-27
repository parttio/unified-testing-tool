package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.ViewportSize;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.spi.AdapterSession;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * One running test: its Testing Tool, adapter session and timeout, and the recorder of its User Steps, shared by
 * all its windows. {@code window} names the window the steps run in: null for the test's first window.
 * {@code ownTimeout} is true when a Wrapper's {@code withTimeout(..)} set the timeout, not the setting.
 * {@code viewport} is the size the test declares, which each of its new windows starts at.
 */
public record TestRun(TestingTool tool, AdapterSession session, Duration timeout, StepRecorder steps,
        String window, boolean ownTimeout, ViewportSize viewport) {

    private static final long POLL_MILLIS = 50;

    public TestRun(TestingTool tool, AdapterSession session, Duration timeout, StepRecorder steps, String window) {
        this(tool, session, timeout, steps, window, false, Settings.DEFAULT_VIEWPORT);
    }

    public TestRun(TestingTool tool, AdapterSession session, Duration timeout, StepRecorder steps, String window,
            boolean ownTimeout) {
        this(tool, session, timeout, steps, window, ownTimeout, Settings.DEFAULT_VIEWPORT);
    }

    /** A run of its own, with a fresh recorder: for tests of the Unified API itself. */
    public TestRun(TestingTool tool, AdapterSession session, Duration timeout) {
        this(tool, session, timeout, new StepRecorder(), null);
    }

    public TestRun withTimeout(Duration newTimeout) {
        return new TestRun(tool, session, newTimeout, steps, window, true, viewport);
    }

    /** Another window of the same test, recording into the same User Steps, at the test's Viewport. */
    public TestRun inWindow(AdapterSession windowSession, String windowName) {
        if (tool.hasRealBrowser()) {
            try {
                windowSession.setViewport(viewport);
            } catch (UnsupportedOperationException e) {
                // decided when the test's first page opened: a test that needs its Viewport was skipped then, one
                // on the default size runs at the browser's own size, with its warning already in the report
            }
        }
        steps.session(windowName, windowSession);
        if (tool.hasRealBrowser()) {
            steps.viewportChanged(windowName, viewport);
        }
        return new TestRun(tool, windowSession, timeout, steps, windowName, ownTimeout, viewport);
    }

    /**
     * Calls {@code attempt} until it returns a non-null value or the timeout ends. Exceptions count as
     * "not yet", but for an {@link UnsupportedOperationException}, which the Testing Tool throws for what it can't
     * do and which fails at once (#611). The exception of the last attempt is the cause of the failure, and its
     * {@code Cause} line. Between attempts the session syncs. The failure is a
     * {@link UnifiedAssertionError} expecting what {@code failure} says.
     */
    public <T> T poll(Supplier<T> attempt, Supplier<String> failure) {
        return pollFor(attempt, () -> FailureParts.of(failure.get(), null));
    }

    /**
     * {@link #poll}, failing with a {@link UnifiedAssertionError} from the parts {@code failure} gives (computed
     * on failure only, e.g. the Near Misses), with how long it waited and the Testing Tool added. Every timeout
     * of the Unified API ends here.
     */
    public <T> T pollFor(Supplier<T> attempt, Supplier<FailureParts> failure) {
        long deadline = System.nanoTime() + timeout.toNanos();
        RuntimeException last = null;
        while (true) {
            try {
                T result = attempt.get();
                if (result != null) {
                    return result;
                }
                last = null;
            } catch (UnsupportedOperationException e) {
                // the Testing Tool can't do it: waiting won't change that (#611)
                throw failure(failure.get().cause(e), e);
            } catch (RuntimeException e) {
                last = e;
            }
            if (System.nanoTime() > deadline) {
                throw failure(failure.get().cause(last).waited(timeout, ownTimeout ? "withTimeout" : Settings.TIMEOUT),
                        last);
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

    /** A Unified failure, with the Testing Tool: also for one that didn't wait (an item a combo box lacks). */
    public UnifiedAssertionError failure(FailureParts parts, Throwable cause) {
        return new UnifiedAssertionError(parts.tool(toolName()), cause);
    }

    /** {@code playwright (chromium)}, {@code browserless}. */
    public String toolName() {
        String name = tool.name().toLowerCase(Locale.ROOT);
        String browser = null;
        try {
            browser = session.browserName();
        } catch (RuntimeException e) {
            // a failure message never fails for want of the browser's name
        }
        return browser == null || browser.isBlank() ? name : name + " (" + browser + ")";
    }

    /** Fails if the run's Testing Tool has no real browser. */
    public void requireBrowser(String feature) {
        if (!tool.hasRealBrowser()) {
            throw new UnsupportedOperationException(feature + " needs a real browser, but this run uses "
                    + tool + ". Annotate the test with @RequiresBrowser to skip it on " + tool + ".");
        }
    }
}
