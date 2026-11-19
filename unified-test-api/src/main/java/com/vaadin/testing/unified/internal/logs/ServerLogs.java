package com.vaadin.testing.unified.internal.logs;

import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.internal.steps.StepRecorder.LogLine;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends the application's log lines to the recorder of the test that is running, so the reports show them under
 * the User Step during which they were logged. The application runs in the test's JVM on every Testing Tool, which
 * is what makes this possible.
 * <p>
 * One hook is attached to the logging framework while any test listens: Logback when it is the SLF4J backend, else
 * {@code java.util.logging}. With one test running, every line is its own. With several running at once (parallel
 * tests in one JVM), a line belongs to the test whose thread logged it, and lines of other threads, such as a
 * server's request threads, are dropped: they can't be told apart.
 */
public final class ServerLogs {
    private ServerLogs() {}

    /** Which lines are kept. */
    public enum Level { WARN, ERROR }

    /** One test listening; close it when the test ends. */
    public static final class Listening implements AutoCloseable {
        private final StepRecorder steps;
        private final Level level;
        private final Thread thread = Thread.currentThread();

        private Listening(StepRecorder steps, Level level) {
            this.steps = steps;
            this.level = level;
        }

        @Override
        public void close() {
            stop(this);
        }
    }

    /** The loggers of the Unified API and the Testing Tools: their lines are about the tool, not the application. */
    private static final List<String> TOOLS = List.of("com.vaadin.testing.unified.", "com.vaadin.browserless.",
            "com.vaadin.testbench.", "com.microsoft.playwright.", "org.openqa.selenium.", "org.vaadin.addons.dramafinder.");

    private static final List<Listening> LISTENING = new CopyOnWriteArrayList<>();
    private static Hook hook;

    /** What is attached to the logging framework. */
    interface Hook {
        void detach();
    }

    /**
     * Starts sending log lines of {@code level} and above to {@code steps}, for the test of the calling thread.
     * A logging framework that can't be hooked gives a listener that hears nothing.
     */
    public static synchronized Listening start(StepRecorder steps, Level level) {
        Listening listening = new Listening(steps, level);
        LISTENING.add(listening);
        if (hook == null) {
            try {
                hook = attach();
            } catch (RuntimeException | LinkageError e) {
                hook = () -> { };
            }
        }
        return listening;
    }

    private static synchronized void stop(Listening listening) {
        LISTENING.remove(listening);
        if (LISTENING.isEmpty() && hook != null) {
            try {
                hook.detach();
            } catch (RuntimeException | LinkageError e) {
                // the framework was reconfigured meanwhile: nothing left to detach
            }
            hook = null;
        }
    }

    private static Hook attach() {
        try {
            if (LogbackHook.available()) {
                return LogbackHook.attach();
            }
        } catch (RuntimeException | LinkageError e) {
            // no Logback to hook, or not the one this was compiled against
        }
        return JulHook.attach();
    }

    /**
     * A line the application logged, from the thread that logged it.
     *
     * @param error whether its level is ERROR (or JUL's SEVERE); else it is a warning
     * @param logger the logger's name
     * @param message the formatted message
     * @param thrown the logged exception, or null
     */
    static void logged(boolean error, String logger, String message, Throwable thrown) {
        logged(error, logger, message, thrown == null ? null : thrown.getClass().getSimpleName(),
                thrown == null ? null : thrown.getMessage());
    }

    /** {@link #logged(boolean, String, String, Throwable)} for a framework that keeps the exception as text. */
    static void logged(boolean error, String logger, String message, String thrownClass, String thrownMessage) {
        List<Listening> listening = LISTENING;
        if (listening.isEmpty() || (logger != null && TOOLS.stream().anyMatch(logger::startsWith))) {
            return;
        }
        Thread thread = Thread.currentThread();
        for (Listening l : listening) {
            if ((listening.size() == 1 || l.thread == thread) && (error || l.level == Level.WARN)) {
                String first = thrownMessage == null ? "" : thrownMessage.lines().findFirst().orElse("");
                String simple = thrownClass == null ? null : thrownClass.substring(thrownClass.lastIndexOf('.') + 1);
                l.steps.logged(new LogLine(LogLine.SERVER, error ? "ERROR" : "WARN", logger, message,
                        simple == null ? null : first.isEmpty() ? simple : simple + ": " + first));
            }
        }
    }
}
