package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.internal.steps.StepRecorder;

/**
 * The automatic layout checks (ADR 0011): with {@code vaadin.testing.layout-checks=on}, the layout is measured inside
 * every User Step that changes what the page shows as a whole (a navigation, a reload, a Viewport change, and
 * {@code checkLayout()}), and the step carries its Layout Findings into the reports.
 */
final class LayoutChecks {
    private LayoutChecks() {}

    /** Measures the run's page for the running User Step, when the test's layout checks are on. */
    static void measure(TestRun run) {
        if (run.steps().layoutChecks()) {
            measureNow(run);
        }
    }

    /**
     * A screenshot of the Viewport for measurement {@code point}, relative to the test's evidence directory
     * ({@code layout/003.png}); null without a directory, or when it can't be taken: evidence never fails a test.
     */
    private static String screenshot(TestRun run, java.nio.file.Path directory, int point) {
        if (directory == null) {
            return null;
        }
        try {
            java.nio.file.Files.createDirectories(directory);
            java.util.List<java.nio.file.Path> files = run.session().writeStepSnapshot(directory,
                    String.format(java.util.Locale.ROOT, "%03d", point), null).files();
            return files.isEmpty() ? null : directory.getFileName() + "/" + files.get(0).getFileName();
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** Measures the run's page for the running User Step, whether or not the automatic checks are on. */
    static void measureNow(TestRun run) {
        StepRecorder steps = run.steps();
        LayoutMeasurement measured = LayoutMeasurement.measure(run, null);
        int point = steps.nextLayoutPoint();
        steps.layout(new StepRecorder.Layout(point, measured.findings().stream()
                .map(f -> new StepRecorder.LayoutFinding(f.kind(), f.text(), f.box())).toList(),
                screenshot(run, steps.layoutScreenshots(), point)));
        if (steps.layoutChecksFail() && !measured.findings().isEmpty()) {
            throw run.failure(com.vaadin.testing.unified.internal.failures.FailureParts.of(
                    "the page to have no Layout Findings", measured.findings().get(0).text()), null);
        }
    }
}
