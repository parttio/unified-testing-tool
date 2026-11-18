package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.internal.steps.UserStepsReport.Json;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The run's {@code summary.json} (spec chapter 12, "summary.json, schema version 1"): the run of {@code summary.md}
 * as data, for agents and CI tooling. The verdict and the counts, then every scenario with where its files are;
 * a failed one with its {@code failure} object as in its {@code user-steps.json}, a scenario under "Changed in this
 * PR" with why it is there, a passing one without a check with the User Steps nothing checks. Paths are relative to the evidence directory, where the file is written.
 *
 * @param summary the run, as {@code summary.md} has it
 * @param evidenceFiles a scenario's evidence files, as paths relative to the evidence directory: its failure
 *        evidence, and its recording ({@code trace.zip}) if it has one
 */
public record RunSummaryJson(RunSummary summary, Function<ScenarioResult, List<String>> evidenceFiles) {

    public static final String FILE = "summary.json";
    public static final int SCHEMA_VERSION = 1;

    public String json() {
        List<ScenarioResult> scenarios = summary.scenarios();
        Json json = new Json();
        json.open('{');
        json.field("schemaVersion", SCHEMA_VERSION);
        json.field("tool", summary.tool().toLowerCase(Locale.ROOT));
        json.field("status", summary.failed() > 0 ? "failed" : "passed");
        Optional<Instant> start = scenarios.stream().map(ScenarioResult::startedAt).min(Instant::compareTo)
                .filter(s -> s.isAfter(Instant.EPOCH));
        if (start.isPresent()) {
            Instant end = scenarios.stream().map(s -> s.startedAt().plusMillis(s.durationMs()))
                    .max(Instant::compareTo).orElse(start.get());
            json.field("startedAt", start.get().toString());
            json.field("durationMs", Duration.between(start.get(), end).toMillis());
        }
        json.field("passed", summary.passed());
        json.field("failed", summary.failed());
        json.field("skipped", summary.skipped());
        ChangedFiles changes = summary.changes();
        if (changes != null) {
            json.field("changedSince", changes.baseRef());
        }
        if (!summary.comparisons().isEmpty()) {
            json.key("comparedWith").open('[');
            for (RunComparison c : summary.comparisons()) {
                json.open('{');
                json.field("tool", c.otherTool().toLowerCase(Locale.ROOT));
                json.field("both", c.both());
                json.field("endDifferently", c.endDifferently());
                json.field("takeDifferentSteps", c.takeDifferentSteps());
                json.field("onlyHere", c.onlyHere());
                json.field("onlyThere", c.onlyThere());
                json.close('}');
            }
            json.close(']');
        }
        json.key("scenarios").open('[');
        scenarios.forEach(s -> scenario(json, s));
        json.close(']');
        json.close('}');
        return json + "\n";
    }

    private void scenario(Json json, ScenarioResult s) {
        json.open('{');
        json.field("testClass", s.testClass());
        json.field("testMethod", s.testMethod());
        json.field("displayName", s.displayName());
        json.field("status", s.status());
        json.field("startedAt", s.startedAt().toString());
        json.field("durationMs", s.durationMs());
        String dir = s.evidenceKey() + "/";
        json.field("evidenceDir", dir);
        json.field("userSteps", dir + UserStepsReport.JSON_FILE);
        changed(json, s);
        differs(json, s);
        s.unchecked().ifPresent(unchecked -> {
            json.field("unchecked", unchecked.all() ? "all" : "end");
            json.key("uncheckedSteps").any(unchecked.steps());
        });
        if (s.failed()) {
            failed(json, s);
        }
        json.close('}');
    }

    /** Under "Changed in this PR": new or changed, the changed files it calls from, the diff's counts. */
    private void changed(Json json, ScenarioResult s) {
        ChangedFiles changes = summary.changes();
        if (changes == null || !changes.touches(s)) {
            return;
        }
        boolean isNew = changes.isNew(s);
        json.field("change", isNew ? "new" : "changed");
        if (isNew) {
            return;
        }
        json.key("calledFromChangedFiles").open('[');
        for (RunSummary.Why why : summary.why(s)) {
            json.open('{');
            json.field("file", why.file());
            json.key("methods").any(why.methods());
            json.close('}');
        }
        json.close(']');
        summary.diff(s).ifPresent(diff -> {
            json.key("stepsDiff").open('{');
            json.field("added", diff.added());
            json.field("removed", diff.removed());
            json.close('}');
        });
    }

    /** How the scenario differs on other Testing Tools: one object per tool it differs on. */
    private void differs(Json json, ScenarioResult s) {
        List<Map.Entry<RunComparison, RunComparison.Difference>> differences = summary.comparisons().stream()
                .flatMap(c -> c.differences().stream().filter(d -> d.here() == s).map(d -> Map.entry(c, d)))
                .toList();
        if (differences.isEmpty()) {
            return;
        }
        json.key("differsOn").open('[');
        for (Map.Entry<RunComparison, RunComparison.Difference> e : differences) {
            RunComparison.Difference d = e.getValue();
            json.open('{');
            json.field("tool", e.getKey().otherTool().toLowerCase(Locale.ROOT));
            json.field("status", d.there().status());
            d.parting().ifPresent(p -> {
                json.key("partsAt").open('{');
                json.field("step", p.position());
                json.optional("here", p.here());
                json.optional("there", p.there());
                json.close('}');
            });
            if (d.there().failure() != null) {
                json.key("failure").any(d.there().failure());
            }
            json.close('}');
        }
        json.close(']');
    }

    private void failed(Json json, ScenarioResult s) {
        Map<String, Object> failure = s.failure() == null ? Map.of() : s.failure();
        Optional.ofNullable(ScenarioResult.string(failure, "stepIndex")).flatMap(s::entry)
                .map(step -> ScenarioResult.string(step, "text")).ifPresent(text -> json.field("failedStep", text));
        if (s.failure() != null) {
            json.key("failure").any(s.failure());
        }
        List<String> evidence = evidenceFiles.apply(s);
        if (!evidence.isEmpty()) {
            json.key("evidence").any(evidence);
        }
    }
}
