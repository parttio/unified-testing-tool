package com.vaadin.testing.unified.internal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * PROTOTYPE (#302), never merged: exclusive time per phase of the Unified layer, per test. A nested phase's time
 * is charged to it, not to the phase around it.
 */
public final class Proto {

    private static final Map<String, Long> TOTALS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Deque<String>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<long[]> LAST = ThreadLocal.withInitial(() -> new long[] { System.nanoTime() });

    private Proto() {
    }

    private static void charge() {
        long now = System.nanoTime();
        Deque<String> stack = STACK.get();
        if (!stack.isEmpty()) {
            TOTALS.merge(stack.peek(), now - LAST.get()[0], Long::sum);
        }
        LAST.get()[0] = now;
    }

    public static <T> T time(String phase, Supplier<T> body) {
        charge();
        STACK.get().push(phase);
        try {
            return body.get();
        } finally {
            charge();
            STACK.get().pop();
        }
    }

    public static void time(String phase, Runnable body) {
        time(phase, () -> {
            body.run();
            return null;
        });
    }

    /** Phase=ms pairs since the last call, then reset. */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(TOTALS).forEach((k, v) -> sb.append(k).append('=').append(String.format(java.util.Locale.ROOT, "%.2f", v / 1e6)).append(';'));
        TOTALS.clear();
        return sb.toString();
    }
}
