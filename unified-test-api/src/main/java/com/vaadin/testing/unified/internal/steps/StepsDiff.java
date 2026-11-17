package com.vaadin.testing.unified.internal.steps;

import java.util.ArrayList;
import java.util.List;

/**
 * How a scenario's User Steps changed since the base run: a line diff over the step tree, steps matched by their
 * text. Indices are left out, since one added step renumbers every step after it; step groups and gaps are lines
 * too, indented as in {@code user-steps.txt}, so an unchanged group reads as context.
 *
 * @param lines every line of both sides, in order: unchanged ones as context
 */
public record StepsDiff(List<Line> lines) {

    /** One line of the diff. */
    public record Line(Kind kind, String text) {
    }

    public enum Kind {
        SAME(' '), REMOVED('-'), ADDED('+');

        private final char mark;

        Kind(char mark) {
            this.mark = mark;
        }
    }

    /** The diff from the base run's steps to this run's. */
    public static StepsDiff of(ScenarioResult base, ScenarioResult current) {
        return of(base.stepLines(), current.stepLines());
    }

    /** A longest-common-subsequence diff: removed lines before the added lines that replace them. */
    static StepsDiff of(List<String> before, List<String> after) {
        int n = before.size();
        int m = after.size();
        int[][] common = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                common[i][j] = before.get(i).equals(after.get(j)) ? common[i + 1][j + 1] + 1
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        List<Line> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && before.get(i).equals(after.get(j))) {
                lines.add(new Line(Kind.SAME, before.get(i++)));
                j++;
            } else if (j == m || (i < n && common[i + 1][j] >= common[i][j + 1])) {
                lines.add(new Line(Kind.REMOVED, before.get(i++)));
            } else {
                lines.add(new Line(Kind.ADDED, after.get(j++)));
            }
        }
        return new StepsDiff(List.copyOf(lines));
    }

    public boolean isEmpty() {
        return lines.stream().allMatch(l -> l.kind() == Kind.SAME);
    }

    public long added() {
        return lines.stream().filter(l -> l.kind() == Kind.ADDED).count();
    }

    public long removed() {
        return lines.stream().filter(l -> l.kind() == Kind.REMOVED).count();
    }

    /** As a unified diff's body: {@code "  "}, {@code "- "} or {@code "+ "}, then the line. */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Line line : lines) {
            sb.append(line.kind().mark).append(' ').append(line.text()).append('\n');
        }
        return sb.toString();
    }
}
