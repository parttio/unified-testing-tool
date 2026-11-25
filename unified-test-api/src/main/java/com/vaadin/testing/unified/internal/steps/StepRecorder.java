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
import java.util.LinkedHashMap;
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
        private com.vaadin.testing.unified.spi.Highlight highlight;
        private Layout layout;
        /** A failed step's Step Target, for the failure's evidence; null otherwise. */
        private transient com.vaadin.testing.unified.UnifiedElement target;

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
        /** Where its screenshot shows its Step Target; null without one. */
        public com.vaadin.testing.unified.spi.Highlight highlight() { return highlight; }
        /** What the automatic layout checks measured during the step; null when they didn't. */
        public Layout layout() { return layout; }
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
        /** The Step Target: the element the step last singled out, or null. */
        com.vaadin.testing.unified.UnifiedElement target;
        /** The Step Target's name as the user read it, when its lookup doesn't name it ({@link #nameTarget}). */
        String targetName;
        /** User Steps that follow this one when it passes, e.g. the page a link opened ({@link #followedBy}). */
        final List<Supplier<Phrase>> followUps = new ArrayList<>();
        /** The layout measured during the step by the automatic layout checks, or null. */
        Layout layout;
    }

    /**
     * What the automatic layout checks measured during a User Step (ADR 0011).
     *
     * @param point the measurement's number in the test, from 1
     * @param findings the Layout Findings
     * @param screenshot its screenshot, relative to the test's evidence directory; null without one
     */
    public record Layout(int point, List<LayoutFinding> findings, String screenshot) {}

    /**
     * One Layout Finding, as the reports show it.
     *
     * @param kind {@code scroll}, {@code clipped} or {@code overlap}
     * @param text what it says
     * @param box where it is, in CSS pixels of the Viewport: x, y, width, height; empty when unknown
     */
    public record LayoutFinding(String kind, String text, List<Double> box) {}

    private boolean layoutChecks;
    private boolean layoutChecksFail;
    private java.nio.file.Path layoutScreenshots;
    private int layoutPoints;

    /**
     * Turns the automatic layout checks on for this test, in a browser (ADR 0011).
     *
     * @param screenshots where each measurement keeps a screenshot of the Viewport; null for none
     */
    public void layoutChecks(boolean fail, java.nio.file.Path screenshots) {
        this.layoutChecks = true;
        this.layoutChecksFail = fail;
        this.layoutScreenshots = screenshots;
    }

    public boolean layoutChecks() {
        return layoutChecks;
    }

    /** Whether a Layout Finding fails its User Step. */
    public boolean layoutChecksFail() {
        return layoutChecksFail;
    }

    /** Where each measurement keeps a screenshot; null for none. */
    public java.nio.file.Path layoutScreenshots() {
        return layoutScreenshots;
    }

    /** The next measurement's number. */
    public synchronized int nextLayoutPoint() {
        return ++layoutPoints;
    }

    /** The running User Step measured this layout; outside a step, nothing. */
    public void layout(Layout layout) {
        Frame f = frame.get();
        if (f != null) {
            f.layout = layout;
        }
    }

    /**
     * The running User Step singled out this element (to act on it or check it): it is the step's Step Target,
     * which its snapshot marks. Outside a step, nothing.
     */
    public void singledOut(com.vaadin.testing.unified.UnifiedElement element) {
        Frame f = frame.get();
        if (f != null) {
            f.target = element;
        }
    }

    /**
     * The running User Step's target reads as {@code name} when its lookup gave it none: what the user read on the
     * component before the step changed it, such as the header of a dialog the step closes. Outside a step, nothing.
     */
    public void nameTarget(String name) {
        Frame f = frame.get();
        if (f != null && name != null) {
            f.targetName = name;
        }
    }

    private final ThreadLocal<Frame> frame = new ThreadLocal<>();
    private final Group root = new Group(null, "", null, null);
    private final Deque<Group> open = new ArrayDeque<>();
    private final Set<String> secrets = new LinkedHashSet<>();
    /** Sign-ins' user names that contain their password, with those passwords: not masked inside the name. */
    private final Map<String, Set<String>> userNames = new LinkedHashMap<>();
    /** The Wrapper types the test has looked up, in order: the custom ones show in its Findable View. */
    private final Set<Class<?>> wrapperTypes = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Class<?>> templateTypes = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> applicationPackages = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
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
    private volatile boolean scenarioAuthored;
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
        Object result;
        try {
            result = body.run();
            if (f.acted || voidAction) {
                add(f, action.get(), window, null);
            } else if (f.read) {
                countRead();
            }
        } catch (Throwable t) {
            add(f, f.acted || voidAction ? action.get() : read.get(), window, t);
            throw t;
        } finally {
            ended(f);
            frame.remove();
            traceEnd(traced);
        }
        recordFollowUps(f, window);
        return result;
    }

    /**
     * A User Step that follows the running one once it passes, such as {@code open “users”} after a click on a link
     * that navigated: what the user saw happen, recorded as {@code navigate(..)} records it. Outside a User Step, it
     * is recorded at once.
     */
    public void followedBy(Supplier<Phrase> phrase, String window) {
        Frame current = frame.get();
        if (current != null) {
            current.followUps.add(phrase);
        } else {
            step(phrase, window, () -> null);
        }
    }

    private void recordFollowUps(Frame f, String window) {
        for (Supplier<Phrase> phrase : f.followUps) {
            step(phrase, window, () -> null);
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
        return step(phrase, window, body::get);
    }

    private <T> T step(Supplier<Phrase> phrase, String window, Body<T> body) {
        Frame f = new Frame();
        f.step = true;
        frame.set(f);
        started(f);
        AdapterSession traced = traceStart(window, () -> phrase.get().render());
        T result;
        try {
            result = sneaky(body);
            add(f, phrase.get(), window, null);
        } catch (Throwable t) {
            add(f, phrase.get(), window, t);
            throw t;
        } finally {
            ended(f);
            frame.remove();
            traceEnd(traced);
        }
        recordFollowUps(f, window);
        return result;
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

    private synchronized void add(Frame f, Phrase given, String window, Throwable error) {
        Phrase phrase = f.targetName == null || given.target() == null || given.target().name() != null ? given
                : given.withTarget(given.target().withName(f.targetName));
        Group container = current();
        Step step = new Step(container.nextIndex(), ++steps, phrase, window, f.callSite);
        step.layout = f.layout;
        last = step;
        step.durationNanos = System.nanoTime() - f.start;
        if (error != null) {
            step.status = Status.FAILED;
            step.error = error;
            step.target = f.target;
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
        snapshot(step, f.target);
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
    private void snapshot(Step step, com.vaadin.testing.unified.UnifiedElement target) {
        Path dir = snapshotDir;
        AdapterSession session = sessions.get(step.window == null ? "" : step.window);
        if (dir == null || session == null || snapshots >= MAX_SNAPSHOTS) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(dir);
            com.vaadin.testing.unified.spi.Evidence evidence = session.writeStepSnapshot(dir,
                    String.format(java.util.Locale.ROOT, "%03d", step.number), target);
            List<Path> files = evidence.files();
            step.highlight = evidence.highlight();
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
    private com.vaadin.testing.unified.ViewportSize viewport;
    private boolean viewportFixed;

    /**
     * The Viewport the test ran at, for its reports: {@code fixed} when the test declares it with {@code @Viewport},
     * so a Viewport Comparison leaves it out. Not called without a browser.
     */
    public void viewport(com.vaadin.testing.unified.ViewportSize size, boolean fixed) {
        this.viewport = size;
        this.viewportFixed = fixed;
        viewportChanged(null, size);
    }

    /** Each window's Viewport now ("" for the first window). */
    private final Map<String, com.vaadin.testing.unified.ViewportSize> currentViewports =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** A window's Viewport changed, or a window opened at one. */
    public void viewportChanged(String window, com.vaadin.testing.unified.ViewportSize size) {
        currentViewports.put(window == null ? "" : window, size);
    }

    /** The window's Viewport now; null without a browser. */
    public com.vaadin.testing.unified.ViewportSize currentViewport(String window) {
        return currentViewports.get(window == null ? "" : window);
    }

    /** The Viewport the test ran at; null without a browser. */
    public com.vaadin.testing.unified.ViewportSize viewport() {
        return viewport;
    }

    /** Whether the test declares its Viewport. */
    public boolean viewportFixed() {
        return viewportFixed;
    }

    public void describeTest(String scenario, String evidenceDir) {
        describeTest(scenario, false, evidenceDir);
    }

    /**
     * {@link #describeTest(String, String)}, saying whether the test's author wrote the scenario's name
     * ({@code @DisplayName}, the method's name), which the reports then show unmasked (ADR 0014). A parameterized
     * or repeated invocation's name, built from the test's arguments, is masked like the rest.
     */
    public void describeTest(String scenario, boolean authored, String evidenceDir) {
        this.scenario = scenario;
        this.scenarioAuthored = authored;
        this.evidenceDir = evidenceDir;
    }

    /** The scenario's name as the reports show it: as written when its author wrote it, else masked. */
    public String maskScenario(String name) {
        return scenarioAuthored ? name : mask(name);
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
            parts.scenario(scenario, scenarioAuthored);
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

    // ---------------------------------------------------------------- Wrapper types

    /**
     * Runs reads that are not the test's own, such as the Findable View's: no User Step, and not counted as the
     * test's reads, even when they call a Wrapper's getters.
     */
    public <T> T unrecorded(Supplier<T> reads) {
        if (frame.get() != null) {
            return reads.get();
        }
        Frame f = new Frame();
        f.step = true;
        frame.set(f);
        try {
            return reads.get();
        } finally {
            frame.remove();
        }
    }

    /** A lookup of this Wrapper type started, in any window of the test. */
    public void wrapperUsed(Class<?> wrapperType) {
        wrapperTypes.add(wrapperType);
    }

    /** A {@code findTemplate(..)} of this component type started, in any window of the test. */
    public void templateUsed(Class<?> templateType) {
        templateTypes.add(templateType);
    }

    /** The application's own packages, where a Template's class is looked up by its tag (ADR 0018). */
    public void applicationPackages(java.util.Collection<String> packages) {
        applicationPackages.addAll(packages);
    }

    /** The test opened this route: its package is one of the application's. */
    public void routeUsed(Class<?> route) {
        if (!route.getPackageName().isEmpty()) {
            applicationPackages.add(route.getPackageName());
        }
    }

    /** The application's own packages known so far: from the test, and the routes it opened. */
    public List<String> applicationPackages() {
        synchronized (applicationPackages) {
            return List.copyOf(applicationPackages);
        }
    }

    /** The Template classes the test has looked up with {@code findTemplate(..)} so far, first lookup first. */
    public List<Class<?>> templateTypes() {
        synchronized (templateTypes) {
            return List.copyOf(templateTypes);
        }
    }

    /** The Wrapper types the test has looked up so far, in the order of their first lookup. */
    public List<Class<?>> wrapperTypes() {
        synchronized (wrapperTypes) {
            return List.copyOf(wrapperTypes);
        }
    }

    // ---------------------------------------------------------------- secrets and windows

    /** Hides this value wherever it appears in the reports from now on, as {@value Phrase#MASK}. */
    public synchronized String secret(String value) {
        if (value != null && !value.isEmpty()) {
            secrets.add(value);
        }
        return value;
    }

    /**
     * Hides a sign-in's password like {@link #secret(String)}, except inside its own user name, which isn't secret
     * (ADR 0014): {@code login("barista@vaadin.com", "barista")} is shown as {@code “barista@vaadin.com”}. A
     * password equal to its user name is masked there too.
     */
    public synchronized void password(String username, String password) {
        secret(password);
        if (username != null && password != null && !password.isEmpty() && !username.equals(password)
                && username.contains(password)) {
            userNames.computeIfAbsent(username, u -> new LinkedHashSet<>()).add(password);
        }
    }

    /**
     * The text with every secret value replaced by {@value Phrase#MASK}, except a sign-in's password inside that
     * sign-in's user name. At each position the longest match wins, a secret over a user name of the same length.
     */
    public synchronized String mask(String text) {
        if (text == null || secrets.isEmpty()) {
            return text;
        }
        StringBuilder masked = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            String secret = longestAt(text, i, secrets);
            String userName = longestAt(text, i, userNames.keySet());
            if (userName != null && (secret == null || userName.length() > secret.length())) {
                Set<String> others = new LinkedHashSet<>(secrets);
                others.removeAll(userNames.get(userName));
                masked.append(replaceAll(userName, others));
                i += userName.length();
            } else if (secret != null) {
                masked.append(Phrase.MASK);
                i += secret.length();
            } else {
                masked.append(text.charAt(i++));
            }
        }
        return masked.toString();
    }

    private static String longestAt(String text, int index, Set<String> candidates) {
        String longest = null;
        for (String candidate : candidates) {
            if (text.startsWith(candidate, index) && (longest == null || candidate.length() > longest.length())) {
                longest = candidate;
            }
        }
        return longest;
    }

    private static String replaceAll(String text, Set<String> secrets) {
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
    /** The Step Target of the step that threw this failure, for its evidence; null when there is none. */
    public com.vaadin.testing.unified.UnifiedElement targetOf(Throwable failure) {
        Step step = stepThatThrew(failure);
        return step == null ? null : step.target;
    }

    private volatile com.vaadin.testing.unified.spi.Highlight failureHighlight;

    /** Where the failure's screenshot shows the failed step's Step Target; null without one. */
    public com.vaadin.testing.unified.spi.Highlight failureHighlight() {
        return failureHighlight;
    }

    public void failureHighlight(com.vaadin.testing.unified.spi.Highlight highlight) {
        this.failureHighlight = highlight;
    }

    private volatile List<String> failureEvidence = List.of();

    /** The failure evidence files in the test's evidence directory, by name; empty without any. */
    public List<String> failureEvidence() {
        return failureEvidence;
    }

    public void failureEvidence(List<String> names) {
        this.failureEvidence = List.copyOf(names);
    }

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
