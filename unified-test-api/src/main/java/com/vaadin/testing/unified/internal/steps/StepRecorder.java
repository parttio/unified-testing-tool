package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.spi.AdapterSession;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The User Steps of one test, in memory: always on, on every Testing Tool. One recorder per test, carried by
 * {@code TestRun} through {@code withTimeout}, {@code openWindow} and {@code openAsUser}.
 * <p>
 * A call into the Unified API opens a <em>frame</em> on its thread. Only the outermost frame can become a User
 * Step: Wrapper default methods calling each other, and the getters an assertion polls, run inside it. Step
 * groups ({@code page.step(..)}) are not frames: they hold the steps run inside them.
 */
public final class StepRecorder {

    public enum Status { PASSED, FAILED }

    /**
     * A line logged while the test ran: by the application ({@link #SERVER}), or an error in the browser's console
     * ({@link #CONSOLE}).
     *
     * @param source {@link #SERVER} or {@link #CONSOLE}
     * @param level {@code ERROR} or {@code WARN}
     * @param logger the logger's name; null for the console
     * @param message the message, as logged
     * @param error the logged exception, {@code IllegalStateException: no id}; null without one
     */
    public record LogLine(String source, String level, String logger, String message, String error) {
        public static final String SERVER = "server";
        public static final String CONSOLE = "console";

        public boolean isError() {
            return "ERROR".equals(level);
        }

        /** {@code server ERROR PersonService: Could not save (IllegalStateException: no id)}. */
        public String text() {
            String simple = logger == null ? null : logger.substring(logger.lastIndexOf('.') + 1);
            String what = message == null ? "" : message.strip().replaceAll("\\s*\\R\\s*", " ");
            what = what.length() > 300 ? what.substring(0, 299) + "…" : what;
            return source + " " + level + (simple == null ? "" : " " + simple) + ": " + what
                    + (error == null ? "" : " (" + error + ")");
        }
    }

    /** A recorded entry: a User Step, a step group or a gap. */
    public sealed interface Entry permits Step, Group, Gap {}

    /** One User Step. */
    public static final class Step implements Entry {
        private final String index;
        private final int number;
        private final Phrase phrase;
        private final String window;
        private final CallSite callSite;
        private Status status = Status.PASSED;
        private long durationNanos;
        private Throwable error;
        private List<CallSite> codePath = List.of();
        private String snapshot;

        Step(String index, int number, Phrase phrase, String window, CallSite callSite) {
            this.index = index;
            this.number = number;
            this.phrase = phrase;
            this.window = window;
            this.callSite = callSite;
        }

        /** Its position in the tree: "2.1". */
        public String index() { return index; }
        /** Its number among all User Steps of the test, from 1, groups not counted. */
        public int number() { return number; }
        public Phrase phrase() { return phrase; }
        /** The window or user it ran in; null for the test's first window. */
        public String window() { return window; }
        public CallSite callSite() { return callSite; }
        public Status status() { return status; }
        public long durationNanos() { return durationNanos; }
        /** Why it failed; null when it passed. */
        public Throwable error() { return error; }
        /** For a failed step: the call sites of the step groups around it, outermost first, then its own. */
        public List<CallSite> codePath() { return codePath; }
        /** Its snapshot, relative to the test's evidence directory ({@code steps/003.png}); null without one. */
        public String snapshot() { return snapshot; }
    }

    /** A step group: {@code page.step("Edit Alan Turing", () -> …)}, a User Task's heading. */
    public static final class Group implements Entry {
        private final String index;
        private final String name;
        private final String window;
        private final CallSite callSite;
        private final List<Entry> children = new ArrayList<>();
        private int numbered;
        private Status status = Status.PASSED;
        private long durationNanos;
        private Throwable error;

        Group(String index, String name, String window, CallSite callSite) {
            this.index = index;
            this.name = name;
            this.window = window;
            this.callSite = callSite;
        }

        public String index() { return index; }
        public String name() { return name; }
        public String window() { return window; }
        public CallSite callSite() { return callSite; }
        public List<Entry> children() { return Collections.unmodifiableList(children); }
        public Status status() { return status; }
        public long durationNanos() { return durationNanos; }
        public Throwable error() { return error; }

        String nextIndex() {
            numbered++;
            return index == null ? String.valueOf(numbered) : index + "." + numbered;
        }
    }

    /** Calls the recorder can't see, one entry per consecutive run of them. */
    public static final class Gap implements Entry {
        private final String reason;
        private final String window;
        private final CallSite callSite;
        private int count;
        private Throwable error;

        Gap(String reason, String window, CallSite callSite) {
            this.reason = reason;
            this.window = window;
            this.callSite = callSite;
        }

        /** "Escape Hatch", "waitUntil", "where(..)". */
        public String reason() { return reason; }
        public String window() { return window; }
        /** Where the first call of the run was made. */
        public CallSite callSite() { return callSite; }
        /** How many calls the recorder saw enter it: an Escape Hatch counts each entry, not what follows. */
        public int count() { return count; }
        /** Set when the opaque call failed, e.g. a {@code waitUntil(..)} that timed out. */
        public Throwable error() { return error; }
        public Status status() { return error == null ? Status.PASSED : Status.FAILED; }

        /** {@code … 3 calls outside the Unified API (Escape Hatch)}. */
        public String text() {
            return "… " + count + (count == 1 ? " call" : " calls") + " outside the Unified API (" + reason + ")";
        }
    }

    /** A body that may throw anything, rethrown unchanged. */
    @FunctionalInterface
    public interface Body<T> {
        T run() throws Throwable;
    }

    private static final class Frame {
        final CallSite callSite = CallSite.here();
        final long start = System.nanoTime();
        boolean acted;
        boolean read;
        /** A User Step or a gap already: what is read inside belongs to it. */
        boolean step;
    }

    private final ThreadLocal<Frame> frame = new ThreadLocal<>();
    private final Group root = new Group(null, "", null, null);
    private final Deque<Group> open = new ArrayDeque<>();
    private final Set<String> secrets = new LinkedHashSet<>();
    private final Instant startedAt = Instant.now();
    private final long start = System.nanoTime();
    private int steps;
    /** The entry recorded last, where the reads that follow it are counted; null before the first one. */
    private Entry last;
    private final Map<Entry, Integer> readsAfter = new java.util.IdentityHashMap<>();
    private int readsBeforeFirstEntry;
    /** The lines logged during or after each entry, until the next one starts. */
    private final Map<Entry, List<LogLine>> logsOf = new java.util.IdentityHashMap<>();
    private final List<LogLine> logsBeforeFirstEntry = new ArrayList<>();
    /** The lines logged while a frame runs: its entry's, once it is known. */
    private final List<LogLine> logsOfRunning = new ArrayList<>();
    private Frame running;
    private int logLines;

    /** The most log lines one test keeps: an application that logs in a loop doesn't fill the reports. */
    public static final int MAX_LOG_LINES = 100;
    private int windows = 1;
    private volatile String scenario;
    private volatile String evidenceDir;
    private long durationNanos = -1;
    /** The session of each window, by window name ("" for the first): snapshots and trace groups. */
    private final Map<String, AdapterSession> sessions = new ConcurrentHashMap<>();
    private volatile Path snapshotDir;
    private int snapshots;

    /** The most snapshots one test keeps: a long test doesn't fill the disk. */
    public static final int MAX_SNAPSHOTS = 200;
    /** Where snapshots go, inside the test's evidence directory. */
    public static final String SNAPSHOT_DIR = "steps";

    // ---------------------------------------------------------------- recording

    /**
     * A Wrapper default method called through its proxy. It becomes a User Step when it is the outermost call
     * and acted through Auto-Wait ({@link #action}) or is a void non-getter; a call that only read becomes a
     * "read" step when it fails, and is counted as a read otherwise ({@link #read()}).
     */
    public Object wrapperCall(Supplier<Phrase> action, Supplier<Phrase> read, boolean voidAction, String window,
            Body<Object> body) throws Throwable {
        if (frame.get() != null) {
            return body.run();
        }
        Frame f = new Frame();
        frame.set(f);
        started(f);
        // a void Wrapper method is a User Step whatever it does; a getter is known to be one only at the end
        AdapterSession traced = voidAction ? traceStart(window, () -> action.get().render()) : null;
        try {
            Object result = body.run();
            if (f.acted || voidAction) {
                add(f, action.get(), window, null);
            } else if (f.read) {
                countRead();
            }
            return result;
        } catch (Throwable t) {
            add(f, f.acted || voidAction ? action.get() : read.get(), window, t);
            throw t;
        } finally {
            ended(f);
            frame.remove();
            traceEnd(traced);
        }
    }

    // ---------------------------------------------------------------- log lines

    /**
     * A line was logged. It belongs to the User Step that is running; between steps, to the entry before, whose
     * late effect it most likely is. Lines are attributed by time: a background thread's line shows under the step
     * during which it was logged, whatever caused it.
     */
    public synchronized void logged(LogLine line) {
        if (++logLines > MAX_LOG_LINES) {
            return;
        }
        if (running != null) {
            logsOfRunning.add(line);
        } else {
            attach(line);
        }
    }

    private void attach(LogLine line) {
        if (last == null) {
            logsBeforeFirstEntry.add(line);
        } else {
            logsOf.computeIfAbsent(last, e -> new ArrayList<>()).add(line);
        }
    }

    private synchronized void started(Frame f) {
        running = f;
    }

    /** The frame's lines go to the entry it recorded, which is the last one; a frame that only read, to the one before. */
    private synchronized void ended(Frame f) {
        if (running == f) {
            running = null;
            logsOfRunning.forEach(this::attach);
            logsOfRunning.clear();
        }
    }

    /** The lines logged during or after an entry, before the next one. */
    public synchronized List<LogLine> logs(Entry entry) {
        return List.copyOf(logsOf.getOrDefault(entry, List.of()));
    }

    /** The lines logged before the test's first entry. */
    public synchronized List<LogLine> logsBeforeFirstEntry() {
        return List.copyOf(logsBeforeFirstEntry);
    }

    /** How many lines were logged beyond {@link #MAX_LOG_LINES} and left out. */
    public synchronized int logLinesLeftOut() {
        return Math.max(0, logLines - MAX_LOG_LINES);
    }

    /**
     * Every error logged so far, in order: the application's ERROR lines and the browsers' console errors. The
     * windows' consoles are read first.
     */
    public List<LogLine> errors() {
        sessions.forEach((window, session) -> console(session));
        synchronized (this) {
            List<LogLine> errors = new ArrayList<>();
            logsBeforeFirstEntry.stream().filter(LogLine::isError).forEach(errors::add);
            collectErrors(root.children, errors);
            logsOfRunning.stream().filter(LogLine::isError).forEach(errors::add);
            return errors;
        }
    }

    private void collectErrors(List<Entry> entries, List<LogLine> errors) {
        for (Entry entry : entries) {
            logsOf.getOrDefault(entry, List.of()).stream().filter(LogLine::isError).forEach(errors::add);
            if (entry instanceof Group group) {
                collectErrors(group.children, errors);
            }
        }
    }

    /** The errors the session's browser console got since they were last read. */
    private void console(AdapterSession session) {
        try {
            for (String error : session.consoleErrors()) {
                logged(new LogLine(LogLine.CONSOLE, "ERROR", null, error, null));
            }
        } catch (RuntimeException | LinkageError e) {
            // the console is a help, never a reason to fail the test
        }
    }

    /**
     * The test read a value from a component: a Unified Element's getter, or a Wrapper getter built on a Testing
     * Tool's access. A read is no User Step and is listed nowhere; the recorder only counts the reads that
     * follow each entry ({@link #readsAfter}), so the reports can tell a scenario that reads a value after its
     * last action, to assert on it in plain Java, from one that checks nothing. Reads inside a User Step, such
     * as the getters an assertion polls, are part of that step and not counted.
     */
    public void read() {
        Frame current = frame.get();
        if (current == null) {
            countRead();
        } else if (!current.step) {
            current.read = true;
        }
    }

    private synchronized void countRead() {
        if (last == null) {
            readsBeforeFirstEntry++;
        } else {
            readsAfter.merge(last, 1, Integer::sum);
        }
    }

    /** How many values the test read after this entry and before the next one, outside any User Step. */
    public synchronized int readsAfter(Entry entry) {
        return readsAfter.getOrDefault(entry, 0);
    }

    /** How many values the test read before its first entry. */
    public synchronized int readsBeforeFirstEntry() {
        return readsBeforeFirstEntry;
    }

    /**
     * A user action. Inside an open frame, it marks the frame as having acted; otherwise it is a User Step of
     * its own (a Unified Element action, a grid row action, navigation).
     */
    public <T> T action(Supplier<Phrase> phrase, String window, Body<T> body) {
        Frame current = frame.get();
        if (current != null) {
            current.acted = true;
            return sneaky(body);
        }
        return step(phrase, window, body);
    }

    public void action(Supplier<Phrase> phrase, String window, Runnable body) {
        action(phrase, window, () -> {
            body.run();
            return null;
        });
    }

    /** A retrying assertion: always its own User Step, unless it runs inside another frame. */
    public <T> T check(Supplier<Phrase> phrase, String window, Supplier<T> body) {
        if (frame.get() != null) {
            return body.get();
        }
        return com.vaadin.testing.unified.internal.Proto.time("assert", () -> step(phrase, window, body::get));
    }

    private <T> T step(Supplier<Phrase> phrase, String window, Body<T> body) {
        Frame f = new Frame();
        f.step = true;
        frame.set(f);
        started(f);
        AdapterSession traced = traceStart(window, () -> phrase.get().render());
        try {
            T result = sneaky(body);
            add(f, phrase.get(), window, null);
            return result;
        } catch (Throwable t) {
            add(f, phrase.get(), window, t);
            throw t;
        } finally {
            ended(f);
            frame.remove();
            traceEnd(traced);
        }
    }

    /** A call the recorder can't see into, e.g. {@code waitUntil(..)}: one gap, whatever runs inside. */
    public void opaque(String reason, String window, Runnable body) {
        if (frame.get() != null) {
            body.run();
            return;
        }
        Frame f = new Frame();
        f.step = true;
        frame.set(f);
        started(f);
        Throwable failure = null;
        try {
            body.run();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            frame.remove();
            Gap gap = gap(reason, window, f.callSite, failure == null);
            if (failure != null) {
                gap.error = failure;
            }
            ended(f);
        }
    }

    /**
     * Records that the test entered the Escape Hatch, or another call the recorder can't see, outside any
     * Unified API call. Consecutive calls of the same kind add up to one gap.
     */
    public void gap(String reason, String window) {
        if (frame.get() == null) {
            gap(reason, window, CallSite.here(), true);
        }
    }

    private synchronized Gap gap(String reason, String window, CallSite callSite, boolean mergeable) {
        Group container = current();
        if (mergeable && !container.children.isEmpty()
                && container.children.get(container.children.size() - 1) instanceof Gap last
                && last.reason.equals(reason) && last.error == null && java.util.Objects.equals(last.window, window)) {
            last.count++;
            this.last = last;
            return last;
        }
        Gap gap = new Gap(reason, window, callSite);
        gap.count = 1;
        container.children.add(gap);
        this.last = gap;
        return gap;
    }

    /** {@code page.step(name, userSteps)}: a step group around what the body records. Nests without limit. */
    public void group(String name, String window, Runnable body) {
        CallSite callSite = CallSite.here();
        Group group;
        synchronized (this) {
            Group container = current();
            group = new Group(container.nextIndex(), name, window, callSite);
            container.children.add(group);
            open.push(group);
            last = group;
        }
        long t0 = System.nanoTime();
        AdapterSession traced = traceStart(window, () -> name);
        try {
            body.run();
        } catch (Throwable t) {
            group.status = Status.FAILED;
            group.error = t;
            throw t;
        } finally {
            traceEnd(traced);
            group.durationNanos = System.nanoTime() - t0;
            synchronized (this) {
                open.remove(group);
            }
        }
    }

    private synchronized void add(Frame f, Phrase phrase, String window, Throwable error) {
        Group container = current();
        Step step = new Step(container.nextIndex(), ++steps, phrase, window, f.callSite);
        last = step;
        step.durationNanos = System.nanoTime() - f.start;
        if (error != null) {
            step.status = Status.FAILED;
            step.error = error;
            List<CallSite> path = new ArrayList<>();
            for (Iterator<Group> it = open.descendingIterator(); it.hasNext();) {
                CallSite site = it.next().callSite;
                if (site != null) {
                    path.add(site);
                }
            }
            if (f.callSite != null) {
                path.add(f.callSite);
            }
            step.codePath = List.copyOf(path);
            if (error instanceof UnifiedAssertionError unified && !unified.parts().hasFailedStep()) {
                FailureParts parts = unified.parts();
                parts.failedStep(step.number, this::stepCount, phrase.render());
                List<String> chain = new ArrayList<>();
                for (Iterator<Group> it = open.descendingIterator(); it.hasNext();) {
                    chain.add(it.next().name);
                }
                parts.stepChain(chain);
                completeFailure(unified, step.codePath);
            }
        }
        container.children.add(step);
        snapshot(step);
        AdapterSession session = sessions.get(step.window == null ? "" : step.window);
        if (session != null) {
            console(session);
        }
    }

    // ---------------------------------------------------------------- snapshots and trace groups

    /** The session the steps of this window run in: the first window's under null. */
    public void session(String window, AdapterSession session) {
        sessions.put(window == null ? "" : window, session);
    }

    /** Turns snapshots on: one per User Step, into this directory, up to {@value #MAX_SNAPSHOTS}. */
    public void snapshotsInto(Path directory) {
        this.snapshotDir = directory;
    }

    /** The step's snapshot, from its window's session. A snapshot that can't be taken is left out. */
    private void snapshot(Step step) {
        Path dir = snapshotDir;
        AdapterSession session = sessions.get(step.window == null ? "" : step.window);
        if (dir == null || session == null || snapshots >= MAX_SNAPSHOTS) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(dir);
            List<Path> files = session.writeStepSnapshot(dir, String.format(java.util.Locale.ROOT, "%03d",
                    step.number));
            if (!files.isEmpty()) {
                snapshots++;
                step.snapshot = SNAPSHOT_DIR + "/" + files.get(0).getFileName();
                for (Path file : files) {
                    if (file.getFileName().toString().endsWith(".txt")) {
                        // a text snapshot hides the test's secrets, as the reports do
                        String text = java.nio.file.Files.readString(file);
                        java.nio.file.Files.writeString(file, mask(text));
                    }
                }
            }
        } catch (Exception | LinkageError e) {
            // evidence is a help, never a reason to fail the test
        }
    }

    /** Opens a trace group for a User Step or Task on a session that traces its steps; the session, or null. */
    private AdapterSession traceStart(String window, Supplier<String> text) {
        AdapterSession session = sessions.get(window == null ? "" : window);
        if (session == null) {
            return null;
        }
        try {
            if (!session.tracesSteps()) {
                return null;
            }
            session.stepStarted(mask(text.get()));
            return session;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void traceEnd(AdapterSession session) {
        if (session != null) {
            try {
                session.stepEnded();
            } catch (RuntimeException e) {
                // the trace misses a group end; the test goes on
            }
        }
    }

    // ---------------------------------------------------------------- failures

    /**
     * Names the test the recorder belongs to, for the failures it completes: the scenario, and the test's evidence
     * directory as the failure shows it.
     */
    public void describeTest(String scenario, String evidenceDir) {
        this.scenario = scenario;
        this.evidenceDir = evidenceDir;
    }

    /**
     * Adds what the recording knows to a Unified failure that lacks it: the scenario, the evidence directory, the
     * code path, and the masking of secret values. Called when its User Step fails, and when the test ends for a
     * failure outside any User Step.
     */
    public void completeFailure(UnifiedAssertionError failure, List<CallSite> codePath) {
        FailureParts parts = failure.parts();
        parts.mask(this::mask);
        if (parts.scenario() == null && scenario != null) {
            parts.scenario(scenario);
        }
        if (parts.evidenceDir() == null && evidenceDir != null) {
            parts.evidenceDir(evidenceDir);
        }
        if (parts.codePath().isEmpty() && codePath != null) {
            parts.codePath(codePath.stream().map(String::valueOf).toList());
        }
    }

    private Group current() {
        Group innermost = open.peek();
        return innermost == null ? root : innermost;
    }

    /** Whether a Unified API call is running on this thread. */
    public boolean inFrame() {
        return frame.get() != null;
    }

    // ---------------------------------------------------------------- secrets and windows

    /** Hides this value wherever it appears in the reports from now on, as {@value Phrase#MASK}. */
    public synchronized String secret(String value) {
        if (value != null && !value.isEmpty()) {
            secrets.add(value);
        }
        return value;
    }

    /** The text with every secret value replaced by {@value Phrase#MASK}. */
    public synchronized String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = text;
        for (String secret : secrets) {
            masked = masked.replace(secret, Phrase.MASK);
        }
        return masked;
    }

    /** A name for another window of the test's user: "window 2". */
    public synchronized String nextWindow() {
        return "window " + (++windows);
    }

    // ---------------------------------------------------------------- results

    /** Ends the recording: the test's duration stops here. */
    public synchronized void finish() {
        if (durationNanos < 0) {
            durationNanos = System.nanoTime() - start;
        }
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(root.children);
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** From the recorder's creation to {@link #finish()}, or until now. */
    public synchronized long durationNanos() {
        return durationNanos < 0 ? System.nanoTime() - start : durationNanos;
    }

    /** How many User Steps were recorded. */
    public synchronized int stepCount() {
        return steps;
    }

    /** The User Step that threw this failure (or a failure it caused), if any. */
    public synchronized Step stepThatThrew(Throwable failure) {
        Step[] found = { null };
        visit(root.children, entry -> {
            if (found[0] == null && entry instanceof Step step && step.error != null && causes(step.error, failure)) {
                found[0] = step;
            }
        });
        return found[0];
    }

    /** The step groups the failure passed through, outermost first. */
    public synchronized List<Group> groupsThatThrew(Throwable failure) {
        List<Group> groups = new ArrayList<>();
        visit(root.children, entry -> {
            if (entry instanceof Group group && group.error != null && causes(group.error, failure)) {
                groups.add(group);
            }
        });
        return groups;
    }

    private static boolean causes(Throwable error, Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t == error) {
                return true;
            }
        }
        return false;
    }

    private static void visit(List<Entry> entries, java.util.function.Consumer<Entry> visitor) {
        for (Entry entry : entries) {
            visitor.accept(entry);
            if (entry instanceof Group group) {
                visit(group.children, visitor);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneaky(Body<T> body) throws E {
        try {
            return body.run();
        } catch (Throwable t) {
            throw (E) t;
        }
    }
}
