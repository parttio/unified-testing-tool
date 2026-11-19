package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.internal.steps.ChangedFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which files make the run's {@code summary.md}, where it goes, and when "Changed in this PR" is left out. */
class RunSummaryWriterTest {

    private static final Instant RUN_START = Instant.parse("2020-01-01T00:00:00Z");

    @TempDir
    Path dir;

    private final List<String> log = new ArrayList<>();
    private final List<String> refsAsked = new ArrayList<>();

    private Path evidence() {
        return dir.resolve("evidence");
    }

    private RunSummaryWriter writer(Map<String, String> settings, Map<String, String> env,
            Function<String, Optional<ChangedFiles>> git) {
        Map<String, String> all = new HashMap<>(settings);
        all.put(Settings.EVIDENCE_DIR, evidence().toString());
        all.put(Settings.TOOL, "playwright");
        return new RunSummaryWriter(Settings.from(ConfigurationSource.of(all)), dir, RUN_START, env, ref -> {
            refsAsked.add(ref);
            return git.apply(ref);
        }, log::add);
    }

    private RunSummaryWriter writer(Map<String, String> settings, Map<String, String> env) {
        return writer(settings, env, ref -> Optional.of(ChangedFiles.of(ref,
                List.of("app/src/test/java/demo/EditPersonTest.java"), List.of(), Map.of())));
    }

    private void test(String testClass, String method, String status, String startedAt) throws Exception {
        String simple = testClass.substring(testClass.lastIndexOf('.') + 1);
        Path file = evidence().resolve(simple).resolve(method).resolve("user-steps.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"schemaVersion": 1, "testClass": "%s", "testMethod": "%s", "displayName": "%s",
                 "tool": "playwright", "status": "%s", "startedAt": "%s", "durationMs": 1, "stepCount": 1,
                 "steps": [{"type": "step", "index": "1", "number": 1, "text": "open People", "verb": "open",
                   "status": "passed", "durationMs": 1,
                   "callSite": {"file": "%s.java", "line": 3, "className": "%s", "method": "%s"}}]}
                """.formatted(testClass, method, method, status, startedAt, simple, testClass, method),
                StandardCharsets.UTF_8);
    }

    private String summary() throws Exception {
        return Files.readString(evidence().resolve("summary.md"), StandardCharsets.UTF_8);
    }

    @Test
    void readsOnlyTheFilesOfThisRun() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");
        test("demo.OrderTest", "orders", "passed", "2026-11-16T10:00:06Z");
        test("demo.OldTest", "fromAnEarlierRun", "failed", "2019-12-31T23:59:59Z");
        Files.writeString(evidence().resolve("Broken.json"), "not even JSON");

        Optional<Path> written = writer(Map.of(), Map.of()).write(Set.of("demo.SkippedTest#skips#[id]"), "42");

        assertEquals(Optional.of(evidence().resolve("summary.md")), written);
        String md = summary();
        assertTrue(md.startsWith("### Unified UI Tests · Playwright · 2 passed, 0 failed, 1 skipped\n"), md);
        assertFalse(md.contains("fromAnEarlierRun"), md);
        assertTrue(Files.exists(evidence().resolve(".summary/skipped-42.txt")));
    }

    @Test
    void writesTheHtmlReportNextToTheSummaryWithItsFailureEvidence() throws Exception {
        test("demo.EditPersonTest", "edits", "failed", "2026-11-16T10:00:05Z");
        test("demo.EditPersonTest", "editsAll", "passed", "2026-11-16T10:00:06Z");
        Files.writeString(evidence().resolve("demo.EditPersonTest.edits.png"), "png");
        Files.writeString(evidence().resolve("demo.EditPersonTest.editsAll.tree.txt"), "an earlier run's");

        writer(Map.of(), Map.of()).write(Set.of(), "1");

        String html = Files.readString(evidence().resolve("report.html"), StandardCharsets.UTF_8);
        assertTrue(html.startsWith("<!doctype html>"), html);
        assertTrue(html.contains("<img src=\"demo.EditPersonTest.edits.png\""), html);
        // another method's file with the same start, and evidence of a passing scenario, are not linked
        assertFalse(html.contains("editsAll.tree.txt"), html);
        assertEquals(List.of("demo.A.save.png", "demo.A.save.tree.txt"), RunSummaryWriter.failureEvidence(
                List.of("demo.A.save.png", "demo.A.save.tree.txt", "demo.A.saveAll.png", "summary.md"),
                com.vaadin.testing.unified.internal.steps.ScenarioResult.parse("""
                        {"schemaVersion": 1, "testClass": "demo.A", "testMethod": "save", "status": "failed",
                         "steps": []}""", "A/save/")));
    }

    @Test
    void writesTheRunAsJsonNextToTheSummaryWithPathsRelativeToTheEvidenceDirectory() throws Exception {
        test("demo.EditPersonTest", "edits", "failed", "2026-11-16T10:00:05Z");
        test("demo.OldTest", "fromAnEarlierRun", "failed", "2019-12-31T23:59:59Z");
        Files.writeString(evidence().resolve("demo.EditPersonTest.edits.png"), "png");

        writer(Map.of(), Map.of()).write(Set.of("demo.SkippedTest#skips#[id]"), "1");

        String json = Files.readString(evidence().resolve("summary.json"), StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\n  \"schemaVersion\": 1,\n  \"tool\": \"playwright\",\n  \"status\": \"failed\","),
                json);
        assertTrue(json.contains("\"passed\": 0,\n  \"failed\": 1,\n  \"skipped\": 1,"), json);
        assertTrue(json.contains("\"evidenceDir\": \"EditPersonTest/edits/\""), json);
        assertTrue(json.contains("\"userSteps\": \"EditPersonTest/edits/user-steps.json\""), json);
        assertTrue(json.contains("\"demo.EditPersonTest.edits.png\""), json);
        assertFalse(json.contains("fromAnEarlierRun"), json);
        assertFalse(json.contains(dir.toString()), "no absolute path: the directory is read from a CI artifact too");
    }

    @Test
    void skippedTestsOfOtherProcessesOfTheRunCount() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");
        writer(Map.of(), Map.of()).write(Set.of("demo.A#a#[1]", "demo.B#b#[2]"), "1");
        writer(Map.of(), Map.of()).write(Set.of("demo.B#b#[2]", "demo.C#c#[3]"), "2");

        assertTrue(summary().startsWith("### Unified UI Tests · Playwright · 1 passed, 0 failed, 3 skipped\n"));
    }

    @Test
    void writesNothingWithoutUnifiedUiTests() throws Exception {
        assertEquals(Optional.empty(), writer(Map.of(), Map.of()).write(Set.of(), "1"));
        assertFalse(Files.exists(evidence().resolve("summary.md")));
    }

    @Test
    void noBaseRefMeansNoChangedSection() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");

        writer(Map.of(), Map.of("GITHUB_BASE_REF", " ")).write(Set.of(), "1");

        assertFalse(summary().contains("Changed in this PR"), summary());
        assertEquals(List.of(), refsAsked, "git isn't asked without a base ref");
    }

    @Test
    void theBaseRefComesFromTheSettingOrThePullRequest() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");

        writer(Map.of(), Map.of("GITHUB_BASE_REF", "main")).write(Set.of(), "1");
        assertTrue(summary().contains("#### Changed in this PR\n\n1 scenario calls code changed since `origin/main`."),
                summary());

        writer(Map.of(Settings.USER_STEPS_CHANGED_SINCE, "v1.0"), Map.of("GITHUB_BASE_REF", "main")).write(Set.of(), "1");
        assertTrue(summary().contains("since `v1.0`"), summary());
        assertEquals(List.of("origin/main", "v1.0"), refsAsked);
    }

    @Test
    void changedScenariosAreComparedWithTheBaseRunInTheBaselineDir() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");
        String json = Files.readString(evidence().resolve("EditPersonTest/edits/user-steps.json"));
        // a downloaded artifact keeps the module's path; a base run on another Testing Tool doesn't count
        Path base = dir.resolve("base");
        Path inArtifact = base.resolve("app/target/surefire-reports/unified-evidence/EditPersonTest/edits/user-steps.json");
        Files.createDirectories(inArtifact.getParent());
        Files.writeString(inArtifact, json.replace("open People", "open Home"));
        Path onSelenium = base.resolve("other/EditPersonTest/edits/user-steps.json");
        Files.createDirectories(onSelenium.getParent());
        Files.writeString(onSelenium, json.replace("\"playwright\"", "\"selenium\""));

        String testFile = "app/src/test/java/demo/EditPersonTest.java";
        writer(Map.of(Settings.USER_STEPS_BASELINE_DIR, base.toString()), Map.of("GITHUB_BASE_REF", "main"),
                ref -> Optional.of(ChangedFiles.of(ref, List.of(testFile), List.of(), Map.of(testFile, "void edits() {}"))))
                .write(Set.of(), "1");

        assertTrue(summary().contains("· changed · User Steps +1 −1 against the base run\n\n"
                + "Called from changed files: `EditPersonTest.java` (edits)\n\n```diff\n- open Home\n"
                + "+ open People\n```\n"), summary());
        assertEquals(List.of(), log);
    }

    @Test
    void theRunIsComparedWithTheRunOnAnotherTestingToolInTheCompareDir() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");
        test("demo.OrderTest", "orders", "passed", "2026-11-16T10:00:06Z");
        String json = Files.readString(evidence().resolve("EditPersonTest/edits/user-steps.json"));
        // a downloaded artifact keeps the module's path; this run's own Testing Tool in there is not another run
        Path other = dir.resolve("other");
        Path onBrowserless = other.resolve("app/target/surefire-reports/unified-evidence/EditPersonTest/edits/user-steps.json");
        Files.createDirectories(onBrowserless.getParent());
        Files.writeString(onBrowserless, json.replace("\"playwright\"", "\"browserless\"").replace("open People", "open Home"));
        Path onPlaywright = other.resolve("earlier/EditPersonTest/edits/user-steps.json");
        Files.createDirectories(onPlaywright.getParent());
        Files.writeString(onPlaywright, json.replace("open People", "open Elsewhere"));

        writer(Map.of(Settings.USER_STEPS_COMPARE_DIR, other.toString()), Map.of()).write(Set.of(), "1");

        assertTrue(summary().contains("#### Compared with Browserless\n\n1 scenario ran on both Testing Tools: 0 end "
                + "differently, 1 takes different User Steps. 1 ran on Playwright only, 0 on Browserless only.\n\n"
                + "**edits (EditPersonTest)** · passed on both, different User Steps\n\n"
                + "- Parts at step 1: `open People` on Playwright, `open Home` on Browserless\n"), summary());
        assertFalse(summary().contains("Elsewhere"), summary());
        String data = Files.readString(evidence().resolve("summary.json"), StandardCharsets.UTF_8);
        assertTrue(data.contains("\"comparedWith\": ["), data);
        assertTrue(data.contains("\"differsOn\": ["), data);
        assertTrue(Files.readString(evidence().resolve("report.html")).contains("<h2>Compared with Browserless</h2>"));
        assertEquals(List.of(), log);
    }

    @Test
    void aCompareDirWithoutAnotherTestingToolsRunIsSaidOnce() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");

        writer(Map.of(Settings.USER_STEPS_COMPARE_DIR, dir.resolve("not-downloaded").toString()), Map.of())
                .write(Set.of(), "1");

        assertFalse(summary().contains("Compared with"), summary());
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.get(0).startsWith("no run on another Testing Tool in "), log.toString());
    }

    @Test
    void aMissingBaseRunIsSaidOnce() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");

        writer(Map.of(Settings.USER_STEPS_BASELINE_DIR, dir.resolve("not-downloaded").toString()),
                Map.of("GITHUB_BASE_REF", "main")).write(Set.of(), "1");

        assertTrue(summary().contains("No base run found to compare their User Steps with.\n"), summary());
        assertTrue(summary().contains("```text\n1  open People\n```"), summary());
        assertEquals(1, log.size(), log.toString());
    }

    @Test
    void outsideAPullRequestTheBaseRunIsNotRead() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");

        writer(Map.of(Settings.USER_STEPS_BASELINE_DIR, dir.resolve("not-downloaded").toString()), Map.of())
                .write(Set.of(), "1");

        assertFalse(summary().contains("base run"), summary());
        assertEquals(List.of(), log);
    }

    @Test
    void withoutGitTheChangedSectionIsLeftOut() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");
        Path notARepository = Files.createDirectories(dir.resolve("not-a-repository"));
        RunSummaryWriter writer = writer(Map.of(Settings.USER_STEPS_CHANGED_SINCE, "origin/main"), Map.of(),
                ref -> ChangedFiles.fromGit(ref, notARepository, log::add));

        writer.write(Set.of(), "1");

        assertFalse(summary().contains("Changed in this PR"), summary());
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.get(0).startsWith("\"Changed in this PR\" left out"), log.get(0));
    }

    @Test
    void appendsToTheGitHubJobSummaryOncePerRun() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");
        Path jobSummary = dir.resolve("step-summary.md");
        Files.writeString(jobSummary, "## Earlier step output\n");
        Map<String, String> env = Map.of(RunSummaryWriter.GITHUB_STEP_SUMMARY, jobSummary.toString());

        writer(Map.of(), env).write(Set.of(), "1");
        // a second fork of the same run ends later, with one more test: its summary replaces the first
        test("demo.OrderTest", "orders", "passed", "2026-11-16T10:00:06Z");
        writer(Map.of(), env).write(Set.of(), "2");

        String content = Files.readString(jobSummary);
        assertTrue(content.startsWith("## Earlier step output\n\n<!-- unified-ui-tests "), content);
        assertEquals(1, content.split("### Unified UI Tests", -1).length - 1, content);
        assertTrue(content.contains("2 passed, 0 failed, 0 skipped"), content);
        assertTrue(content.contains(summary()), content);
    }

    @Test
    void theGitHubJobSummaryCanBeTurnedOff() throws Exception {
        test("demo.EditPersonTest", "edits", "passed", "2026-11-16T10:00:05Z");
        Path jobSummary = dir.resolve("step-summary.md");

        writer(Map.of(Settings.USER_STEPS_GITHUB_SUMMARY, "false"),
                Map.of(RunSummaryWriter.GITHUB_STEP_SUMMARY, jobSummary.toString())).write(Set.of(), "1");

        assertTrue(Files.exists(evidence().resolve("summary.md")));
        assertFalse(Files.exists(jobSummary));
    }
}
