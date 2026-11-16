package com.vaadin.testing.unified.internal.failures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ranks the Near Misses of a failed lookup and picks the suggestion (spec chapter 12, "Failures"). Pure: the
 * candidates are read by the caller.
 *
 * <ul>
 * <li>Components of the same kind, at most {@value #SAME_KIND}, closest first: the same text ignoring case, then
 * one containing the other, then the rest; by edit distance within each.</li>
 * <li>Then components of another kind with exactly the wanted name, at most {@value #OTHER_KIND}.</li>
 * <li>A suggestion only when one Near Miss is clearly best: the only one that differs from the wanted name only in
 * case, spacing or punctuation; else the only one at the smallest edit distance, if that is at most
 * {@code max(2, length / 4)}; else the only one of another kind with the exact name. When both of the last two
 * apply, neither is clearly best.</li>
 * </ul>
 */
public final class NearMisses {
    private NearMisses() {}

    public static final int SAME_KIND = 5;
    public static final int OTHER_KIND = 3;

    /**
     * A component the lookup almost matched.
     *
     * @param kind the component kind in words, {@code text field}
     * @param value the value the failed filter matched on (its label, text, …)
     * @param hidden whether it is hidden
     * @param payload what the caller needs to write the suggestion, e.g. the Wrapper type
     */
    public record Candidate<T>(String kind, String value, boolean hidden, T payload) {
        /** {@code text field “E-mail” (hidden)}. */
        public String render() {
            return kind + " “" + value + "”" + (hidden ? " (hidden)" : "");
        }
    }

    /** The Near Misses to show, in order, and the one to suggest (or null). */
    public record Result<T>(List<Candidate<T>> shown, Candidate<T> suggestion) {
        public List<String> rendered() {
            return shown.stream().map(Candidate::render).toList();
        }
    }

    public static <T> Result<T> rank(String wanted, List<Candidate<T>> sameKind, List<Candidate<T>> otherKind) {
        List<Candidate<T>> same = new ArrayList<>(sameKind.stream()
                .filter(c -> c.value() != null && !c.value().isBlank()).toList());
        same.sort(Comparator.<Candidate<T>>comparingInt(c -> category(wanted, c.value()))
                .thenComparingInt(c -> distance(wanted, c.value())));
        List<Candidate<T>> other = otherKind.stream().filter(c -> c.value() != null).limit(OTHER_KIND).toList();
        List<Candidate<T>> shown = new ArrayList<>(same.subList(0, Math.min(SAME_KIND, same.size())));
        shown.addAll(other);
        return new Result<>(List.copyOf(shown), suggestion(wanted, same, otherKind));
    }

    private static <T> Candidate<T> suggestion(String wanted, List<Candidate<T>> same, List<Candidate<T>> other) {
        if (wanted == null || wanted.isBlank()) {
            return null;
        }
        if (same.stream().anyMatch(c -> c.value().equals(wanted))) {
            return null; // the component is there: another filter, or the scope, ruled it out
        }
        List<Candidate<T>> loose = same.stream().filter(c -> loose(c.value()).equals(loose(wanted))).toList();
        if (!loose.isEmpty()) {
            return loose.size() == 1 ? loose.get(0) : null;
        }
        Candidate<T> closest = null;
        if (!same.isEmpty()) {
            int min = same.stream().mapToInt(c -> distance(wanted, c.value())).min().getAsInt();
            List<Candidate<T>> atMin = same.stream().filter(c -> distance(wanted, c.value()) == min).toList();
            if (min <= Math.max(2, wanted.length() / 4) && atMin.size() == 1) {
                closest = atMin.get(0);
            }
        }
        Candidate<T> otherKind = other.size() == 1 ? other.get(0) : null;
        if (closest != null && otherKind != null) {
            return null;
        }
        return closest != null ? closest : otherKind;
    }

    /** 0: the same ignoring case and spacing; 1: one contains the other; 2: neither. */
    static int category(String wanted, String value) {
        String w = normalize(wanted);
        String v = normalize(value);
        if (w.equals(v)) {
            return 0;
        }
        return !w.isEmpty() && (v.contains(w) || w.contains(v)) ? 1 : 2;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Letters and digits only, lower case: {@code E-mail} and {@code email} are the same. */
    static String loose(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Levenshtein distance, ignoring case. */
    public static int distance(String a, String b) {
        String s = normalize(a);
        String t = normalize(b);
        int[] previous = new int[t.length() + 1];
        int[] current = new int[t.length() + 1];
        for (int j = 0; j <= t.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= s.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= t.length(); j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[t.length()];
    }
}
