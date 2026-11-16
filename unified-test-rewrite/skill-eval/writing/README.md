# Writing skill evaluation

Scores the `write-unified-ui-tests` agent skill
([source](../../src/main/resources/META-INF/unified/skills/write-unified-ui-tests/SKILL.md), spec chapter 12,
"The agent skill"): prose scenarios in, tests out. It sits next to the migration skill's suite (`../baseline.json`,
`scripts/eval-migration-skill.sh`) and works the same way.

| Path | What |
|---|---|
| `fixtures/<TestClass>.md` | one scenario in prose, on a view of `unified-test-demo`; the file name is the test class the agent writes in `demo` |
| `reference/<TestClass>.java` | a hand-written test per fixture, written the way the skill says: what an agent's test is measured against, and a check that each fixture can be written |
| `baseline.json` | the score of the last accepted run, for the skill whose SHA-256 it names |

## Run it

```
scripts/eval-writing-skill.sh                 # an agent (Claude Code, `claude -p`) writes each test
scripts/eval-writing-skill.sh --reference     # score the reference tests instead: no agent
scripts/eval-writing-skill.sh --only RenamePersonTest,ChangeEmailTest
scripts/eval-writing-skill.sh --accept        # when acceptable, write the run as baseline.json
```

Options as in `eval-migration-skill.sh`: `--keep` keeps the scratch worktree, `--playwright-executable PATH` passes
a browser to the Playwright runs. Results, logs, the tests written and their `user-steps.txt` are under
`target/skill-eval-writing/<timestamp>/`, with `report.md` and `result.json`.

The script makes a scratch worktree of `HEAD`, installs the skill at `.claude/skills/write-unified-ui-tests/SKILL.md`
as `AddUnifiedTestingSkill` does, builds it, and then, one fixture at a time:

1. asks Claude Code (`claude -p`, edits accepted, Maven allowed, as the migration evaluation runs it) to write the
   fixture's test class, following the skill; with `--reference`, copies the reference test instead;
2. keeps the test sources the run wrote, and lists the files it changed elsewhere;
3. compiles the demo's tests, runs the class and `UnifiedStyleTest` on Browserless, then the class on Playwright;
4. scores the test class with `scripts/score_intent.py`;
5. reverts the worktree, so each fixture starts from `HEAD`.

## What it scores

Per fixture:

| Metric | Better |
|---|---|
| `compiles` | 1: the demo's tests compile with the new test |
| `browserless.passed`, `playwright.passed` / `.failed` | passed tests up, failed down |
| `styleRules` | 1: `UnifiedStyleTest` (the `unified-test-archunit` rules) passes with the new test |
| intent share, `intent` / (`intent` + `plumbing`) | up; it must drop more than 5 points to count as worse, since agents word tests differently from run to run |
| `escapeHatches` | down: `.unwrap(`, `…EscapeHatch.of(` and `@RequiresTestingTool` in the test sources written |
| `otherFilesChanged` | down: files changed outside `unified-test-demo/src/test/java/demo` (the application must stay as it is) |

Intent share uses the readability prototype's token rules (branch `prototype/readable-styles`,
`spec/readability-prototype/score/score.py`), unchanged: string and number literals, actions, assertions,
`is…`/`has…` checks, the view class and the first Wrapper type of each statement are intent; the rest is plumbing.
It only counts methods taking `UnifiedPage page`, and a `page.step(..)` with its lambda is one statement.

The comparison is fixture by fixture, over the fixtures in both the baseline and the run, like the migration suite:
a run is acceptable when no compared metric is worse, and, when the skill changed, at least one is better, unless the
baseline has nothing left to improve (every compared fixture compiling, passing on both Testing Tools, following the
style rules, with no Escape Hatch and no other file changed; intent share has no ceiling). A new fixture is reported
and compared against nothing.

Known limits: one agent run per fixture, so a score varies between runs; the agent can read the demo's existing
tests and the guide in the worktree, which a real project has fewer of; the scores say nothing about whether the
test checks what the scenario asked beyond its passing (read `user-steps.txt` in the run directory for that).

## Baselines
