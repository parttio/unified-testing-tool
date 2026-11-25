package com.vaadin.testing.unified.internal.failures;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The message of a {@link com.vaadin.testing.unified.UnifiedAssertionError} (spec chapter 12, "Failures"): the
 * scenario, then fixed labels in a fixed order, lines with nothing to say left out.
 *
 * <pre>
 * Editing a person in the dialog updates the grid
 *   Failed at     User Step 5 of 5: fill text field “Email” with “alan@bletchley.uk”
 *   In step       Edit Alan Turing
 *   Expected      exactly one text field labelled “Email” in dialog “Edit person”
 *   Found         none
 *   Nearby        text field “E-mail”, text field “Name”, combo box “Role”
 *   Did you mean  find(UnifiedTextField.class).withLabel("E-mail")
 *   Waited        10 s (vaadin.testing.timeout)
 *   Tool          playwright (chromium)
 *   Code          EditPersonTest.java:13 (editsAPersonInTheDialog) ← EditPersonTest.java:24 (editPerson)
 *   Evidence      target/surefire-reports/unified-evidence/EditPersonTest/editsAPersonInTheDialog/  (failure.png, failure.view.txt)
 * </pre>
 */
public final class FailureMessage {
    private FailureMessage() {}

    static final String INDENT = "  ";
    /** The width of the label column, "Did you mean" and two spaces. */
    static final int LABEL_WIDTH = 14;

    public static String render(FailureParts parts) {
        StringBuilder sb = new StringBuilder(headline(parts));
        if (parts.hasFailedStep()) {
            Integer total = parts.totalSteps();
            line(sb, "Failed at", "User Step " + parts.stepNumber()
                    + (total == null || total < parts.stepNumber() ? "" : " of " + total) + ": " + parts.stepText());
        }
        if (!parts.stepChain().isEmpty()) {
            line(sb, "In step", String.join(" › ", parts.stepChain()));
        }
        line(sb, "Expected", parts.expected());
        line(sb, "Found", parts.found());
        if (!parts.nearMisses().isEmpty()) {
            line(sb, "Nearby", String.join(", ", parts.nearMisses()));
        }
        line(sb, "Did you mean", parts.suggestion());
        line(sb, "Cause", parts.cause());
        if (parts.waited() != null) {
            line(sb, "Waited", duration(parts.waited())
                    + (parts.waitedSetting() == null ? "" : " (" + parts.waitedSetting() + ")"));
        }
        line(sb, "Tool", parts.tool());
        if (!parts.codePath().isEmpty()) {
            line(sb, "Code", String.join(" ← ", parts.codePath()));
        }
        line(sb, "Evidence", parts.evidenceDir() == null || parts.evidenceFiles().isEmpty() ? parts.evidenceDir()
                : parts.evidenceDir() + "  (" + String.join(", ", parts.evidenceFiles()) + ")");
        return sb.toString();
    }

    /** The scenario when known; otherwise the summary, so that the first line always says something. */
    static String headline(FailureParts parts) {
        String scenario = parts.scenario();
        return scenario != null && !scenario.isBlank() ? oneLine(scenario) : summary(parts);
    }

    /**
     * The failure in one line, for the User Step lists and the run summary: {@code Expected exactly one text field
     * labelled “Email”, found none}.
     */
    public static String summary(FailureParts parts) {
        String expected = parts.expected();
        String found = parts.found();
        if (expected == null) {
            return found == null ? "Unified UI Test failure" : "Found " + oneLine(found);
        }
        return "Expected " + oneLine(expected) + (found == null ? "" : ", found " + oneLine(found));
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append('\n').append(INDENT).append(label).append(" ".repeat(Math.max(2, LABEL_WIDTH - label.length())))
                .append(oneLine(value));
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s*\\R\\s*", " ");
    }

    /** {@code 10 s}, {@code 1.5 s}, {@code 300 ms}. */
    public static String duration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + " ms";
        }
        return BigDecimal.valueOf(millis).movePointLeft(3).stripTrailingZeros().toPlainString() + " s";
    }

    /**
     * Values as a failure lists them: {@code “Saving…”, then “Saved Alan Turing”}. More than five: the first, an
     * ellipsis, and the last three.
     */
    public static String sequence(List<String> values) {
        List<String> shown = new ArrayList<>(values);
        if (shown.size() > 5) {
            List<String> cut = new ArrayList<>();
            cut.add(shown.get(0));
            cut.add("…");
            cut.addAll(shown.subList(shown.size() - 3, shown.size()));
            shown = cut;
        }
        return String.join(", then ", shown);
    }
}
