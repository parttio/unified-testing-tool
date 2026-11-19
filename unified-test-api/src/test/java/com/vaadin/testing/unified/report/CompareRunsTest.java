package com.vaadin.testing.unified.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link CompareRuns}: the Testing Tools' runs compared from their evidence directories, after they ended. */
class CompareRunsTest {

    private static final String COMMIT = "1f5fbf0000000000000000000000000000000abc";

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream log = new ByteArrayOutputStream();

    /** One Testing Tool's evidence artifact, as its CI job uploaded it: a module's path, a test, the run's summary. */
    private Path evidence(String tool, String status, String lastStep, String commit) throws Exception {
        Path evidence = dir.resolve("unified-evidence-" + tool).resolve("app/target/surefire-reports/unified-evidence");
        Path test = evidence.resolve("EditPersonTest/edits/user-steps.json");
        Files.createDirectories(test.getParent());
        Files.writeString(test, """
                {"schemaVersion": 1, "testClass": "demo.EditPersonTest", "testMethod": "edits",
                 "displayName": "Editing a person updates the grid", "tool": "%s", "status": "%s",
                 "startedAt": "2026-11-16T10:00:05Z", "durationMs": 1,%s
                 "steps": [{"type": "step", "index": "1", "number": 1, "text": "open People", "verb": "open", "status": "passed"},
                           {"type": "step", "index": "2", "number": 2, "text": "%s", "verb": "see", "status": "%s"}]}
                """.formatted(tool, status, status.equals("failed") ? """
                         "failure": {"type": "com.vaadin.testing.unified.UnifiedAssertionError",
                                     "expected": "notification “Saved”", "found": "none", "stepIndex": "2"},""" : "",
                lastStep, status));
        Files.writeString(evidence.resolve("summary.json"), "{\"schemaVersion\": 1, \"tool\": \"" + tool
                + "\", \"commit\": \"" + commit + "\", \"scenarios\": []}");
        return dir.resolve("unified-evidence-" + tool);
    }

    private int run(Path summary, Path... directories) throws Exception {
        return CompareRuns.run(List.of(directories), summary == null ? null : summary.toString(),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(log, true, StandardCharsets.UTF_8));
    }

    @Test
    void eachTestingToolIsComparedWithBrowserlessAndTheResultGoesToTheJobSummary() throws Exception {
        Path browserless = evidence("browserless", "passed", "see notification “Saved”", COMMIT);
        Path playwright = evidence("playwright", "failed", "see notification “Saved”", COMMIT);
        Path selenium = evidence("selenium", "passed", "see notification “Saved Alan”", "9".repeat(40));
        Path summary = dir.resolve("job-summary.md");
        Files.writeString(summary, "### An earlier step's summary\n");

        int status = run(summary, selenium, playwright, browserless, dir.resolve("not-uploaded"));

        assertEquals(0, status, "it reports: a difference fails nothing");
        String md = out.toString(StandardCharsets.UTF_8);
        assertEquals("""
                ### Unified UI Tests · Testing Tools compared

                #### Playwright compared with Browserless

                1 scenario ran on both Testing Tools: 1 ends differently, 0 take different User Steps.

                Both runs are of commit `1f5fbf0`.

                **Editing a person updates the grid (EditPersonTest)** · failed on Playwright, passed on Browserless

                - On Playwright: `UnifiedAssertionError: Expected notification “Saved”, found none`

                #### Selenium compared with Browserless

                1 scenario ran on both Testing Tools: 0 end differently, 1 takes different User Steps.

                **Not the same code.** This run is of commit `9999999`, the Browserless run of commit `1f5fbf0`. \
                A difference below may come from the code, not from the Testing Tool.

                **Editing a person updates the grid (EditPersonTest)** · passed on both, different User Steps

                - Parts at step 2: `see notification “Saved Alan”` on Selenium, `see notification “Saved”` on Browserless
                """, md);
        assertEquals("### An earlier step's summary\n" + md + "\n", Files.readString(summary));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("No evidence in " + dir.resolve("not-uploaded")));
    }

    @Test
    void oneTestingToolAloneIsNothingToCompare() throws Exception {
        assertEquals(2, run(null, evidence("playwright", "passed", "see notification “Saved”", COMMIT)));
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("Nothing to compare: only playwright ran"));
    }

    @Test
    void withoutBrowserlessTheFirstTestingToolByNameIsTheReference() throws Exception {
        run(null, evidence("selenium", "passed", "see notification “Saved”", COMMIT),
                evidence("playwright", "passed", "see notification “Saved”", COMMIT));

        String md = out.toString(StandardCharsets.UTF_8);
        assertTrue(md.contains("#### Selenium compared with Playwright\n\n1 scenario ran on both Testing Tools: same "
                + "result and same User Steps on each.\n\nBoth runs are of commit `1f5fbf0`.\n"), md);
    }
}
