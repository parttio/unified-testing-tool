package com.vaadin.testing.unified.internal.steps;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    }

    private final ThreadLocal<Frame> frame = new ThreadLocal<>();
    private final Group root = new Group(null, "", null, null);
    private final Deque<Group> open = new ArrayDeque<>();
    private final Set<String> secrets = new LinkedHashSet<>();
    private final Instant startedAt = Instant.now();
    private final long start = System.nanoTime();
    private int steps;
    private int windows = 1;
    private long durationNanos = -1;

    // ---------------------------------------------------------------- recording

    /**
     * A Wrapper default method called through its proxy. It becomes a User Step when it is the outermost call
     * and acted through Auto-Wait ({@link #action}) or is a void non-getter; a call that only read becomes a
     * "read" step when it fails, and is forgotten otherwise.
     */
    public Object wrapperCall(Supplier<Phrase> action, Supplier<Phrase> read, boolean voidAction, String window,
            Body<Object> body) throws Throwable {
        if (frame.get() != null) {
            return body.run();
        }
        Frame f = new Frame();
        frame.set(f);
        try {
            Object result = body.run();
            if (f.acted || voidAction) {
                add(f, action.get(), window, null);
            }
            return result;
        } catch (Throwable t) {
            add(f, f.acted || voidAction ? action.get() : read.get(), window, t);
            throw t;
        } finally {
            frame.remove();
        }
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
        frame.set(f);
        try {
            T result = sneaky(body);
            add(f, phrase.get(), window, null);
            return result;
        } catch (Throwable t) {
            add(f, phrase.get(), window, t);
            throw t;
        } finally {
            frame.remove();
        }
    }

    /** A call the recorder can't see into, e.g. {@code waitUntil(..)}: one gap, whatever runs inside. */
    public void opaque(String reason, String window, Runnable body) {
        if (frame.get() != null) {
            body.run();
            return;
        }
        Frame f = new Frame();
        frame.set(f);
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
            return last;
        }
        Gap gap = new Gap(reason, window, callSite);
        gap.count = 1;
        container.children.add(gap);
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
        }
        long t0 = System.nanoTime();
        try {
            body.run();
        } catch (Throwable t) {
            group.status = Status.FAILED;
            group.error = t;
            throw t;
        } finally {
            group.durationNanos = System.nanoTime() - t0;
            synchronized (this) {
                open.remove(group);
            }
        }
    }

    private synchronized void add(Frame f, Phrase phrase, String window, Throwable error) {
        Group container = current();
        Step step = new Step(container.nextIndex(), ++steps, phrase, window, f.callSite);
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
        }
        container.children.add(step);
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
