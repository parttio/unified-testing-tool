# Roadmap status audit: what shipped since 2026-11-17, and what is left of each candidate

Research for [#500](https://github.com/parttio/unified-testing-tool/issues/500), part of the map [#499](https://github.com/parttio/unified-testing-tool/issues/499). It audits the roadmap candidates C1–C17 of [agentic-testing-environment-roadmap.md](agentic-testing-environment-roadmap.md) (researched at `37c7b00`, 2026-11-17) and the open candidate issues against `main`.

**Question.** For each candidate C1–C17 and each open candidate issue (#206, #215, #288, #292, #293, #295 and its leftovers, #296, #298, #308, #310, #484): is it **shipped**, **partly shipped** (what is left, and which issue still asks for it), or **open**? Which shipped features have no guide page or are missing from the `write-unified-ui-tests` skill? Which open issues are stale or duplicated?

**Sources.** Local only: `main` at `39411f6` (277 commits after `37c7b00`), `docs/`, the code, and the issues and pull requests of `parttio/unified-testing-tool` read through the REST API on 2026-11-22. Nothing was run. Every claim cites a file, a commit, an issue or a pull request. **[opinion]** marks this note's own judgement.

## Answer in short

Seven of the eight shortlisted candidates shipped in full or in part, all in the four days after the roadmap note, and all are in the [0.1.0-alpha4 release notes](../releases/0.1.0-alpha4.md) (released at `4f8ab6c`). The only shortlisted candidate with nothing built is **C4, the live session (#288)**, together with **C8, coverage in user terms (#292)** and the edit hook **C7(b) (#293)**. None of the nine lower-ranked candidates (C9–C17) has been built; C1's "(no name)" lines are the only accessibility output.

The gaps that recur across the shipped work are three: **nothing fails a passing test by a setting** (C3 and C5 both leave this open), **known Limitations are not data** (C2), and **the writing skill does not mention most of the new outputs**.

| Candidate | Issue(s) | Status on `main` | What is left, and where it is asked for |
|---|---|---|---|
| C1 Findable View | #204 (closed) | **Shipped** | Nothing on #204. Reach at the current Viewport is #494 (out of scope for #499). |
| C2 Cross-tool divergence report | #286 (open), #311 (closed), #310 (open) | **Partly shipped** | Saying whether a difference is a documented Limitation: #310. #286 has nothing else left. |
| C3 Weak-test checks | #287 (open), #308 (open) | **Partly shipped** (level 1) | Failing the build by a setting: #287. Step-skip reruns: #308. PIT guide page: #287. |
| C4 Live session | #288 | **Open** | Everything; the prototype has not started. |
| C5 Server logs and console per User Step, smoke check | #289 (open) | **Partly shipped** | A setting that fails on a logged error; the default level; parallel tests: #289. Selenium on Firefox: #316 (upstream). |
| C6 Apply `Did you mean` | #290 (closed) | **Shipped** | Nothing filed. Suggestions the recipe can't apply are not listed (PR #313, "Merge Danger"). |
| C7(a) `summary.json` | #291 (closed) | **Shipped** | Nothing. |
| C7(b) Edit hook | #293 | **Open** | Everything. |
| C7(c) `llms.txt`, API reference in the jar | #205 (open) | **Shipped** | #205 holds only C17 and a skill-eval rerun that #436 duplicates. |
| C8 Coverage in user terms | #292 | **Open** | Everything; its blocker #204 is gone. |
| C9 Accessibility checks | #295 | **Open** | Only the Findable View's "(no name)" lines exist. |
| C10 Committed User Steps | #295 | **Open** | Everything. |
| C11 Scenarios for a changed view | #295 | **Open** | Everything; `user-steps.json` records no routes. |
| C12 "Application is wrong" eval fixtures | #298 | **Open** | Everything. |
| C13 Flaky check in "prove it" | #296 | **Open** (a hint only) | The skill's review mode says to run a test twice; nothing else. |
| C14 Unified Recorder | #215, #201 | **Open** | Everything. |
| C15 Screenshot comparison | #295 | **Open** | Everything. |
| C16 Clock and network guide | #297 | **Open** | Everything. |
| C17 Vaadin MCP and Skills know the Unified API | #205 | **Open** (not this project's to build) | Asking Vaadin. |
| — #206 Playwright's test agents for Java | #206 | **Superseded in part** | Both ideas became #290 (shipped) and #288 (open); only "does a skill make Playwright's agents write Unified UI Tests" is left, untested. |
| — #484 Vaadin observability | #484 | **Open** (idea) | Its U2 overlaps #289; U3 (data-fetch and SQL counts per step) has no equivalent. |

## 1. The shortlist, C1–C8

### C1. Findable View — shipped

- `describe()` on the page and on every Scope, and `<test>.view.txt` written next to a failure's evidence on every Testing Tool: commit `1b92299` (#204), merged in PR #335 (`24516e4`), which closes #204. #204 is closed as completed.
- The format is the roadmap's proposal: Wrapper kinds and lookups, not roles or Playwright's ARIA YAML. [ADR 0009](../adr/0009-findable-view-in-lookups-not-aria-yaml.md) records it.
- A component without a name shows as `(no name; placeholder …)` or `(no name; test id …)`, which [spec 12](../spec/12-readable-tests.md) (line 351) calls "an accessibility finding". This is the part of C9 the roadmap said C1 gives for free.
- Two cross-tool bugs were found and fixed while building it: #333 (`df5c291`) and #334 (`f4f90d5`).
- Documented in the guide (`finding-components.md`, `reports-and-failures.md` "The Findable View", `troubleshooting.md`, `for-agents.md`, `custom-wrappers.md`) and in the skill (`SKILL.md` step 1 and "Read a failure").
- Follow-up outside #499: Reach in the Findable View is #494, from [ADR 0011](../adr/0011-responsive-testing-viewports-layout-findings-viewport-comparison.md) and the closed grilling #487.

### C2. Cross-tool divergence report — partly shipped

Shipped:

- `vaadin.testing.user-steps.compare-dir`: a run compares itself with another Testing Tool's evidence, and adds a "Compared with `<Testing Tool>`" section to `summary.md` and `report.html` and `comparedWith` and `differsOn` to `summary.json`. Commit `a5598bb`, PR #309 (`7c69402`). The first real comparison found a bug (`hasSelectedItems` word order, `db4d7f6`).
- "Same commit" check: each run records `commit` and an `uncommittedChanges` fingerprint, and the comparison says whether both runs tested the same code. Commit `1ffb853`, PR #312 (`ae8c03a`).
- Comparison after the runs ended (`CompareRuns`, `unified-test-api/src/main/java/com/vaadin/testing/unified/report/CompareRuns.java`), wired into this project's CI at `.github/workflows/tests.yml` line 209. Commit `4239cad`, PR #321 (`5ca8166`), which closes #311.
- Documented: `docs/guide/reports-and-failures.md` "Compared with another Testing Tool", `docs/guide/configuration.md` line 16, `docs/guide/ci-setup.md`.

Left:

- **Known or unexplained**: the section does not say whether a difference is a documented Limitation. That is #310. `unified-test-api/src/main/java` has no Limitations table (a search for `Limitation` finds nothing). #310 asks itself "Is it worth it yet?": the first real comparison found two differences, both bugs and neither a Limitation.

### C3. Weak-test checks — partly shipped (level 1 of 2)

Shipped (level 1):

- "Scenarios without a check" in `summary.md`, a *no check* badge in `report.html`, and `unchecked`/`uncheckedSteps` in `summary.json`. Commits `fb55be4`, `80d4a4a`, `bbde388`; PR #307 (`3e74f89`). A value read after the last action counts as a check (`bbde388`).
- Documented: `docs/guide/reports-and-failures.md` "Scenarios without a check", `reviewing-tests.md`, `common-mistakes.md`, `for-agents.md`.

Left, per the closing comment on #287 (2026-11-18):

- **Fail the build by a setting.** Not done. The reports never fail a passing test (`TestLifecycle.close`), so the setting belongs in the lifecycle. `docs/guide/configuration.md` lists no such setting. Asked in #287.
- **Level 2, step-skip reruns on Browserless.** Not started; split out as #308.
- A read with no assertion after it counts as a check, and a refusal before any User Step stays listed (`DashboardTest.refusesAQueryWithoutTag`). Both are listed as left in #287.
- The PIT guide page. Asked in #287.

### C4. Live session — open

#288 is open, and no commit since `37c7b00` mentions it. `git log 37c7b00..HEAD --grep` finds no commit naming #288, #292, #293, #296, #297, #298, #308, #310 or #215. #288 says it is "Blocked in practice by #204 for `describe`": that blocker shipped (C1). #227 (Browserless Test without JUnit) is still open; its comment of 2026-11-19 says `browserless-test-shared` already has no JUnit dependency and only the Spring flavour still brings JUnit in (upstream vaadin/browserless-test#354).

### C5. Server logs and browser console per User Step, smoke check — partly shipped

Shipped:

- The application's warnings and errors under the User Step during which they were logged, in `user-steps.txt`, `user-steps.json` (`logs`) and `report.html`, through Logback or `java.util.logging`; `assertThat(page).hasNoErrors()`; `vaadin.testing.user-steps.server-log` (`warn`, `error`, `off`). Commits `049c38d`, `9fe456e`, `18a07e4`; PR #314 (`eead46e`).
- Browser console errors on Playwright (PR #314) and on Selenium with Chrome (commit `5600501`, PR #315, `065c4b7`).
- Documented: `docs/guide/reports-and-failures.md` "What the application logged" (line 209 names Playwright and Selenium with Chrome), `assertions-and-waiting.md`, `troubleshooting.md`, `configuration.md` line 17.

Left, per the closing comment on #289 (2026-11-19):

- **A setting that fails a scenario on a logged error by itself.** Only `hasNoErrors()` fails a test. Same question as C3's "fail by a setting".
- **The default level.** Capture is on at `warn`; whether it should be `error` or off is open.
- **Parallel tests in one JVM** keep only their own thread's lines.
- **No demo scenario** logs an error end to end.
- **Selenium on Firefox** reads no console: #316, a TestBench gap, out of scope for #499.

### C6. Apply `Did you mean` — shipped

- `ApplyDidYouMean`, an OpenRewrite recipe in `unified-test-rewrite`, writes each failure's `Did you mean` into the test as a reviewable diff, with a data table of the renames. `user-steps.json` gained `failure.lookup`. Commit `1dadd55`, PR #313 (`19c8633`), which closes #290.
- Documented: `docs/guide/reports-and-failures.md` "Applying it" (line 74), `troubleshooting.md`.
- Not filed anywhere: suggestions the recipe does not apply (another Wrapper, a value in a constant, a lookup in a file the failure doesn't name) are not listed; "nothing but the next run says so" (PR #313, "Merge Danger").

### C7. The loop, packaged — (a) and (c) shipped, (b) open

- **(a) `summary.json`** — shipped: commit `3f5121a`, PR #306 (`239e471`), closes #291. Schema version 1 in spec chapter 12. Documented in `reports-and-failures.md` "The run as data", `ci-setup.md`, `for-agents.md`.
- **(b) Edit hook** — open: #293. `unified-test-rewrite/src/main/java` has no hook code. Two of its open questions are now partly answered: the verdict it would return exists (`summary.json`), but "which test class matches an edited view" still needs routes in `user-steps.json`, which C11 asks for and nothing records.
- **(c) `llms.txt` and the API reference in the jar** — shipped: commit `764c029`, PR #319 (`a08320f`); `scripts/llms_txt.py`, `unified-test-api/src/main/resources/META-INF/unified/api-reference.md`. Later docs work extended both: rules for generators in `llms.txt` (`e85021b`, #411) and the User Step of each action in the reference (`cb9251a`, #414).

### C8. Coverage in user terms — open

#292 is open with no commit. Its stated blocker, #204, shipped. Nothing in `unified-test-api` records the routes a scenario opens (no `routes` field found).

## 2. The lower-ranked candidates, C9–C17

None has code on `main`.

| Candidate | Where tracked | Evidence it is not built |
|---|---|---|
| C9 Accessibility checks | #295 (unchecked) | No `hasNoAccessibilityViolations` or axe in any `.java` file. The Findable View's "(no name)" lines are the only output (spec 12, line 351). |
| C10 Committed User Steps | #295 (unchecked) | No expected-steps file or update setting in `configuration.md`. |
| C11 Scenarios for a changed view | #295 (unchecked) | No routes in `user-steps.json`. |
| C12 "Application is wrong" fixtures | #298 | `unified-test-rewrite/skill-eval/writing/fixtures/` still holds the same six fixtures; `git log 37c7b00..HEAD -- unified-test-rewrite/skill-eval/writing` is empty. |
| C13 Flaky check | #296 | No `flaky`, `flakyFailure` or rerun handling in the code or the guide. The only trace is the skill's review step 3: "Run it a second time before trusting a test that passed once" (`SKILL.md` line 190–191), added with the review mode (`7734c56`, #410). The "prove it" step runs each Testing Tool once. |
| C14 Unified Recorder | #215, #201 | No `unified-test-recorder` module. |
| C15 Screenshot comparison | #295 (unchecked) | No `matchesScreenshot` or `compareScreen` outside research notes. |
| C16 Clock and network guide | #297 | No guide page mentions a `Clock`. |
| C17 Vaadin MCP and Skills | #205, point 3 | Not this project's to build (#205 comment of 2026-11-19). |

## 3. Shipped features missing from the guide or the skill

**Guide.** Every shipped roadmap feature has a guide section (see each candidate above). None is missing. The release notes cover all of them too ([0.1.0-alpha4](../releases/0.1.0-alpha4.md), lines 29–166).

**The `write-unified-ui-tests` skill** (`unified-test-rewrite/src/main/resources/META-INF/unified/skills/write-unified-ui-tests/SKILL.md`) mentions the Findable View (`describe()` in step 1, `view.txt` under "Read a failure") and the API reference (step 2). It does **not** mention:

| Shipped feature | Where the skill would use it [opinion] |
|---|---|
| `summary.json` (C7a) | "Prove it" and "Read a failure": an agent reads the verdict as data. `for-agents.md` lists it; the skill does not. |
| Compared with another Testing Tool, `compare-dir` (C2) | "Prove it" runs Browserless and Playwright and gives no help when they disagree, which was the gap C2 named. |
| Scenarios without a check (C3) | "Review a test" step 1 asks to flag "a test that ends on an action with no check" by reading, without pointing to the summary section that lists them. |
| Server log lines per User Step, `hasNoErrors()` (C5) | "Read a failure" and the canonical-forms table. |
| `ApplyDidYouMean` (C6) | "Read a failure" says "apply `Did you mean`", meaning by hand. |
| Step Target highlight (#472) | "Read a failure": the screenshot evidence now marks the step's component (ADR 0010). |

The skill's text has changed since its evaluation baseline (#436 for the review mode; #205's comment for the API reference). Adding any of the above changes it again.

## 4. Stale or duplicated open issues

| Issue | What is stale | Suggested action [opinion] |
|---|---|---|
| #286 | All three leftovers in its 2026-11-19 comment are resolved or moved: same commit (PR #312), CI (#311, closed by PR #321), Limitations (#310). PR #312 says "What is left of that ticket is now #310 … and #311". | Close as completed; #310 carries the rest. |
| #205 | Points 1 and 2 shipped (PR #319). What it says is left: C17 (ask Vaadin) and a writing-skill evaluation rerun, which #436 also asks for. | Close or narrow to C17 only; the rerun is #436. |
| #206 | Both ideas split out: repair into #290 (shipped), writing from a running application into #288. Only "can a skill make Playwright's agents write Unified UI Tests" is left, untested. | Narrow to that question, or fold into #288. |
| #289 | Its closing comment says "Selenium reads no console"; PR #315 added it for Chrome, and #316 covers Firefox. | Update the "Left" list. |
| #288, #292 | Both say they are blocked by #204, which is closed. | Remove the blocker. |
| #293 | Its open question about the verdict to return is answered by `summary.json` (#291); the routes question is still open (C11). | Update. |
| #295 | Three candidates split out (#296, #297, #298); C9, C10, C11, C15 remain as lines. C9's "#204 gives the cheapest part" is now true. | Keep; #499 re-ranks the four lines. |
| #201 | The research it asks for is `docs/research/copilot-recorder.md`, and #215 "follows #201". Still open with `needs-triage`. | Close as completed; #215 holds the work. |
| #484 | Its U2 (server exception in the failure evidence) overlaps C5 (#289), as #484 says itself; its comment of 2026-11-21 narrows the new part to data-fetch and SQL counts per step. | Keep; re-rank as the SQL-count idea. |
| #287 | Open with two leftovers that are not #308: fail by a setting, and the PIT guide page. "Fail by a setting" is the same question as #289's. | Keep, or move "fail by a setting" to one issue for both. |

## Not verified

- Nothing was built or run; "shipped" means merged to `main` and described in the guide, the spec and the release notes.
- "Not built" for C9–C17 rests on searches of the code and `docs/` for the names the roadmap uses; a feature under another name could be missed.
- Issue and PR states are as of 2026-11-22.
