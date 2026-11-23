# 12. Readable tests and reports

Decided on the map [Make Unified UI Tests read like what the user does](https://github.com/parttio/unified-testing-tool/issues/193); lookups in [ADR 0007](../adr/0007-wrappers-carry-their-filters.md). Terms: **User Step**, **User Task** and **Near Miss** in [GLOSSARY.md](../../GLOSSARY.md). Research in [`docs/research/`](../../docs/research/).

Unified UI Tests are mostly written by AI agents. They are read, in this order, by the reviewer of the diff, by whoever reads a failure (in CI or in an agent's conversation), by the developer who maintains them, and by non-developers. This chapter makes the test code read as the scenario, and lets every run report what the user did in user terms.

Three rules shape it:

- **One obvious way per intent.** Each common intent has one canonical form. A longer form exists only for what the short one can't express.
- **More API is not better.** Readability comes from the structure of the test code and from the reports, not from extra lookup forms or verbs.
- **Java only.** No Gherkin and no external DSL ([BDD research](../../docs/research/bdd-for-unified-ui-tests.md)). The guide may mention JGiven as an option.

## Lookups

[ADR 0007](../adr/0007-wrappers-carry-their-filters.md) replaces the query-and-terminal shape of [4. Locating](04-locating.md):

```java
page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
assertThat(users.row().withCellTextContaining("Email", "user11")).hasCount(11);
```

| Rule | Detail |
|---|---|
| The Wrapper is the query | `find(UnifiedX.class)` returns the Wrapper. Filters are Wrapper methods, specific to its type (ADR 0001), declared as mixins (`LabelFilter<UnifiedTextField>`). There is no query type and no `single()`. |
| Immutable | A filter returns a new Wrapper and never changes the one it was called on. |
| Exactly one, on every use | Every action, read and assertion finds the component again and waits up to the timeout for exactly one match. 0 or several matches fail with Near Misses (see [Failures](#failures)). A Wrapper never holds a component instance. |
| Checks are assertions | `assertThat(x).exists()`, `.doesNotExist()`, `.hasCount(n)`, which retry like every assertion. There is no boolean `exists()`. |
| `all()` | Only to loop over matches. It returns lazy Wrappers by position. |
| Grid rows | `grid.row().withCellText("Name", "Alan Turing")`, `grid.row(n)`. Row actions are Wrapper actions and get Auto-Wait. |
| `findComponent(X.class)` | Returns a lazy `UnifiedGenericComponent` with the shared filters. |

## Structuring a scenario

A Unified UI Test is plain JUnit. The Unified API does not impose a structure. The guide recommends this one:

| Element | Purpose | Reaches the reports |
|---|---|---|
| `@DisplayName("…")` or `@DisplayNameGeneration` | the scenario in one sentence | yes, as the scenario name |
| `// given`, `// when`, `// then` comments | the shape of the scenario, for the reviewer of the diff | no |
| **User Task**: a method whose body is one `page.step("…", () -> …)` | one named piece of user work, named after what the user does ("Edit Alan Turing") | yes, as a heading over its User Steps |
| Lean page object (optional) | the lookups of one view, as methods returning Wrappers. No actions, no assertions | no |

Where User Tasks live: a private method in the test class, until a second test class needs it. It then moves to a `…Tasks` class of static methods that take the `UnifiedPage` (`PeopleTasks.editPerson(page, …)`). Page objects are optional and hold structure only. TestBench page objects migrated by the Migration Recipes keep working: their lookup methods are structure, and their action methods are User Tasks in all but name ([page objects research](../../docs/research/page-objects-and-tasks.md)).

```java
@Test
@DisplayName("Editing a person in the dialog updates the grid")
void editsAPersonInTheDialog(UnifiedPage page) {
    // given
    page.navigate(PeopleView.class);

    // when
    editPerson(page, "Alan Turing", "Alan M. Turing", "alan@bletchley.uk", "Admin");

    // then
    assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
    assertThat(page.find(UnifiedGrid.class).row().withCellText("Name", "Alan M. Turing"))
            .hasCellText("Email", "alan@bletchley.uk")
            .hasCellText("Role", "Admin");
}

private static void editPerson(UnifiedPage page, String name, String newName, String email, String role) {
    page.step("Edit " + name, () -> {
        page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
        dialog.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
        dialog.find(UnifiedTextField.class).withLabel("Email").setValue(email);
        dialog.find(UnifiedComboBox.class).withLabel("Role").selectItem(role);
        dialog.find(UnifiedButton.class).withText("Save").click();
    });
}
```

## `page.step(..)`

```java
void step(String name, Runnable userSteps);   // on UnifiedPage
```

- **On the page, not static.** It needs no static import and doesn't clash with `Allure.step`. It records into the test's run, so it works across `openWindow` and `openAsUser`.
- **`Runnable` only.** Wrappers are lazy, so a test declares them before the step and uses them inside and after it.
- **Nesting:** steps nest without limit. Reports indent them, and a failure names the chain (`In step  Edit Alan Turing › …`).
- **Failure location:** a failure inside a step points first at the line that called the step, then at the line inside it.
- **Naming:** step names describe user work. They are not given/when/then keywords.

## User Steps

Every Wrapper action and every retrying assertion records one User Step, on every Testing Tool, whether or not the test uses `page.step(..)`.

**Wording:** `<verb> <component kind> “<name>” [with “<value>”] [in <scope>]`

- **Verbs** come from a fixed table. Assertions read **see** or **see no**.

  | Verb | Wrapper actions |
  |---|---|
  | open | `navigate` |
  | click, double-click | `click`, `doubleClick` |
  | fill, clear | `setValue`, `clear` |
  | choose | `selectItem` on single-select fields |
  | check, uncheck | `setChecked` |
  | select, deselect | selection in grids and multi-select fields |
  | sort, expand, upload, press | `sort`, `toggleDetails`/`expand`, `upload`, `pressKey` |
  | see / see no | retrying assertions, `doesNotExist` |
- **Component kind:** the Wrapper type in plain words (`UnifiedComboBox` → “combo box”).
- **Name:** the main filter (label, text or accessible name). Other filters go in brackets: `(test id “billing”)`.
- **Scope** names the container: `in dialog “Edit person”`.
- **Add-on Wrappers** get their kind from the class name and a default verb per method, and can override both.
- **Secret values:** values typed into a password field are always shown as `••••`, and a test can mark other values as secret. Values longer than 80 characters are cut with `…`.
- **Calls the recorder can't see** (Escape Hatch, opaque `where(..)`/`waitUntil(..)`, plain JUnit assertions) appear as one gap line, `… 3 calls outside the Unified API (Escape Hatch)`. They are never silently left out.

## Reports

Recording is always on (in memory). One Testing Tool per run, named in every header.

| Output | Where | When | For |
|---|---|---|---|
| CI log | stdout | failed tests by default | whoever reads CI |
| `user-steps.txt` | evidence directory, per test | every test | agents, people |
| `user-steps.json` | evidence directory, per test | every test | the later outputs and tools. Versioned: `"schemaVersion": 1` |
| `summary.md` | evidence directory, per run | every run | the reviewer |
| `report.html` | evidence directory, per run | every run | the reviewer, in a browser |
| `summary.json` | evidence directory, per run | every run | agents and CI tooling. Versioned: `"schemaVersion": 1` |
| GitHub job summary | `summary.md` appended when `GITHUB_STEP_SUMMARY` is set | every run in GitHub Actions | the reviewer |

**`summary.md`**, in order:

1. **Header:** Testing Tool and counts.
2. **Changed in this PR:** scenarios with any User Step or User Task called from a file changed against the base branch (`GITHUB_BASE_REF`, or `vaadin.testing.user-steps.changed-since`), marked new or changed. A change in a shared `…Tasks` class marks every scenario using it. A new scenario's steps are unfolded. A changed one says why it is listed (`Called from changed files: <file> (<methods>)`, each changed file it calls from with the methods calling from it, in call order), then its steps diffed against the Base Run when there is one, else unfolded. Left out outside a PR.
3. **Failed scenarios:** `@DisplayName`, the failed User Step, Near Misses, evidence path.
4. **Compared with `<Testing Tool>`**: with `vaadin.testing.user-steps.compare-dir` set, this run against a run of the same tests on another Testing Tool: how many scenarios ran on both, then each scenario that differs. One that passes on one Testing Tool and fails on the other comes first, with the failed side's error; then those that end alike and take different User Steps. Each says where its steps part. At most ten are listed, then a count. One section per other Testing Tool found.
5. **Scenarios without a check**: the passing scenarios that end on User Steps nothing checks, one line each: `no check`, or `no check after:` and the steps after the last check (three, then a count). At most ten are listed, then a count. Left out when there is none.

Passing scenarios count in the header only, unless they are under "Changed in this PR" or "Scenarios without a check": a list of every passing scenario is long to scroll past on the job summary, and each one's User Steps are in its `user-steps.txt`. Skipped scenarios count in the header only too: tests that ended `aborted`, and Unified UI Tests the run skipped without running (disabled, or lacking a Capability).

How `summary.md` is made:

- **Where and when.** `<vaadin.testing.evidence-dir>/summary.md`, written when the JUnit Platform has run the test plan (a `TestExecutionListener` registered through `META-INF/services`). A run without Unified UI Tests writes none. Tests that launch other tests through the JUnit launcher don't write one.
- **Which files.** The `user-steps.json` under the evidence directory whose `startedAt` is not before the run's start: the Maven JVM that started the Surefire forks, else the test JVM. Each fork rewrites the summary when it ends, from the files of all forks, so the last one writes the whole run.
- **Changed files.** `git diff --name-status --no-renames <merge base of the ref and HEAD>` plus untracked files, run in the project's directory. In CI that equals `git diff <ref>...HEAD`; locally, uncommitted changes count too. Without git, or with an unknown ref, the section is left out and one line is logged.
- **Matching.** A call site matches a changed file whose path ends with `<package as directories>/<callSite.file>` (the outermost class's name plus `.java` when `file` is missing). A change anywhere in a file marks every scenario calling from it.
- **New or changed.** New when the test class's file was added (renamed files count as added), or when that file at the merge base has no `<test method>(`. Otherwise changed.
- **Diff against the Base Run.** With `vaadin.testing.user-steps.baseline-dir` set, every `user-steps.json` under that directory, at any depth, whose `tool` is this run's. A changed scenario matches the Base Run's scenario with the same test class and evidence directory (`<class>/<method>[n]`), else the only one of the test class with the same display name. Its steps are compared as lines without indices (an added step renumbers the ones after it): each User Step and gap as its window and text, each step group as its name, indented by depth. A longest-common-subsequence diff, removed lines before the added ones, shown in full as a `diff` block headed `User Steps +<added> −<removed> against the base run`. Same steps: `same User Steps as the base run`, without the block. Not matched: `not in the base run`, steps unfolded. No `user-steps.json` for the tool, or no directory: one line, `No base run found to compare their User Steps with.`, and steps unfolded. Steps' status, durations and call sites are not compared. In this project's CI the Base Run is the evidence artifact of the base branch's last finished push run of the workflow (`scripts/download-base-user-steps.sh`).
- **Compared with another Testing Tool****.** Every `user-steps.json` under `vaadin.testing.user-steps.compare-dir`, at any depth, whose `tool` is not this run's; several tools give one section each, in alphabetical order. A scenario matches as for the Base Run (test class and evidence directory, else the only one of the class with the same display name). Aborted scenarios count as not run. Two matched scenarios differ when one failed and the other didn't, or when their steps, compared as lines without indices as for the Base Run, are not the same; the steps part at the first line that differs, counted from 1, or where one side ends. Durations, call sites and reads are not compared. **Same code:** each run writes into its `summary.json` the commit it tested (`git rev-parse HEAD`) and, when tracked files have uncommitted changes, a fingerprint of them (the first 12 hex digits of the SHA-256 of `git diff HEAD`). The other run's are read from every `summary.json` under the directory whose `tool` is that Testing Tool's. The section says `Both runs are of commit <7 digits>` when commit and fingerprint are equal; `Not the same code`, with what each run is of, when they differ or when that tool's files name several; and that it is not known when either side names no commit (no git, or evidence written by an earlier version). The comparison is made all the same. Untracked files are not in the fingerprint. No run of another Testing Tool in the directory: one line is logged and the section is left out. Reported only: it fails no test.
- **Compared after the runs****.** `com.vaadin.testing.unified.report.CompareRuns <evidence dir>…` makes the same comparison from evidence directories, for runs that have ended (CI jobs side by side). It reads every `user-steps.json` and `summary.json` under them, sorts the scenarios by Testing Tool, and compares each Testing Tool with the reference: Browserless when it ran, else the first by name. It prints `### Unified UI Tests · Testing Tools compared` and a `#### <Testing Tool> compared with <reference>` section each, and appends them to `GITHUB_STEP_SUMMARY` when set. Exit code 0 whatever the runs say; 2 when fewer than two Testing Tools are found.
- **Without a check****.** Read from a passed scenario's entries in order, step groups opened. A check is a User Step whose verb is `see` or `see no`. These count as one too, since a check may be behind them: a failed step (a passing test that has one expected the failure), a gap whose reason is `waitUntil` or `Escape Hatch`, and a value the test read (`readsAfter` on the entry before it, or `readsBeforeFirstEntry`), which a plain assertion in the test is taken to check. A `where(..) condition` gap doesn't. The steps after the last check are unchecked; a scenario with no check at all is unchecked as a whole, also when it has no User Step. Failed and aborted scenarios are not judged. Reported only: it fails no test.
- **GitHub job summary.** Put in a marked block, one per evidence directory and run: a later fork replaces the block an earlier one wrote.

**`report.html`** is written with `summary.md`, from the same files and the same `ChangedFiles`. One file with its CSS and script inline, so it opens from a downloaded CI artifact; its links are relative to the evidence directory. In order:

1. **Header:** Testing Tool, counts, the run's start (UTC) and duration.
2. **Controls:** show passing (off), changed only (with a base ref), diff against base run (on; with a Base Run that has this run's Testing Tool), and a search over scenario names, classes and step texts.
3. **Changed in this PR:** as in `summary.md`, marked new or changed, with the diff's counts or `same User Steps as the base run` / `not in the base run` next to the badge, and the why line; failed scenarios unfolded, passing ones folded. A scenario whose steps differ shows the diff instead of its step tree; unticking *diff against base run* shows the tree instead. The failed and passing sections show the tree only.
4. **Failed scenarios:** Failed at, Expected and Found (or the error line), Nearby, Did you mean, the code path, the failure evidence (the files its `user-steps.json` names in `failure.evidence`, in the test's evidence directory *(unreleased)*: a screenshot shown inline with its Step Target spotlighted, a component tree shown inline as its readable page with its text linked, other files linked), then the User Steps tree with the failed step marked.
5. **Compared with `<Testing Tool>`:** as in `summary.md`, every scenario that differs.
6. **Scenarios without a check:** as in `summary.md`, every one of them, folded; left out when there is none. Such a scenario carries a `no check` badge wherever it is listed.
7. **Passing scenarios:** hidden until the reader shows them or searches; one folded line each.

Each step shows its index, window, text, duration and call site, and its snapshot when it has one: a component tree opens under the step, a screenshot in the viewer. Step groups nest and fold; gaps are dimmed. Every value is HTML-escaped. Aborted scenarios count in the header only. Each scenario links its `user-steps.txt`, `user-steps.json`, and the Playwright trace this run kept for it (`trace.zip`).

**Screenshots****.** A step's screenshot opens in a viewer over the report: the scenario's step screenshots in order, then its failure's, with the step's index and words as the title, a filmstrip, prev and next (← →), *open PNG* (the file as it is) and close (Esc). Everything outside the Step Target's box (`highlight`) is dimmed, and the box has an outline in the report's accent with a white halo, so it shows on the application's own colours. A step without a box says why: *No box: the component is no longer on the page.* or *…outside the visible area.* A step that singled out no component shows neither. The failure's screenshot shows its spotlight inline too. One switch in the controls, *highlight step targets*, on by default, turns every spotlight off; `h` flips it in the viewer. The report draws the box; the PNG and the page are never changed ([ADR 0010](../adr/0010-browser-screenshots-are-highlighted-by-the-report.md)).

**Component trees.** For each Browserless tree the report shows (a step's `.tree.txt` snapshot, a failure's `.tree.txt` evidence), the report's writer writes a readable page beside it, `<name>.tree.html`, from the text: the location as a header, one foldable node per component, its type, id, flags (`DISABLED`, `INVIS`, …) and naming attributes (label, caption, text, value, …) first, the other attributes muted, hidden components dimmed, and the component the step acted on highlighted, scrolled into view and linked from the header. The page has its CSS and script inline, works in light and dark, expands and collapses all, and finds a component by its text, showing the components it is in. It shows only what the text says, so the text's masking holds; the `.tree.txt` stays the evidence agents read.

**`summary.json`** is written with `summary.md`, from the same files, the same `ChangedFiles` and the same Base Run: the run as data, so an agent or a CI tool reads the verdict without parsing Markdown or walking the evidence directory. Its fields are [below](#summaryjson-schema-version-1).

### Log lines under the User Step

On by default; `vaadin.testing.user-steps.server-log` sets which of the application's lines are kept (`warn`, `error`, `off`).

- **The application's log.** The application runs in the test's JVM on every Testing Tool, so the Unified API hears what it logs: through an appender on Logback's root logger when Logback is the SLF4J backend, else through a handler on `java.util.logging`'s root logger. An application on another backend, such as Log4j 2, that doesn't send its lines to `java.util.logging` is not heard: its tests get no server lines, and nothing else changes. Logback is an optional dependency of `unified-test-api`: a project needs none for this, its own is used. Kept: warnings and errors, with the logged exception's class and the first line of its message. Not kept: lines of the Unified API's and the Testing Tools' own loggers.
- **The browser's console.** After every User Step the step's window is asked for the errors its console got since the last time (`AdapterSession.consoleErrors()`): `console.error` and uncaught exceptions, without the browser's own request for `/favicon.ico`. Playwright has them, and Selenium on Chrome (Selenium's browser log, at `SEVERE`); Firefox on Selenium has no browser log, and Browserless no console.
- **Which entry.** A line belongs to the User Step that was running when it was logged. Between steps it belongs to the entry before, whose late effect it most likely is, and before the first entry to the test. This is by time, whatever thread logged: a background job's line shows under the step during which it ran.
- **Where.** Under the entry in `user-steps.txt` (`⚠ server ERROR PersonService: Could not save Alan (IllegalStateException: no id)`), in the entry's `logs` in `user-steps.json`, and under the step in `report.html`. Masked like the rest. The first 100 lines of a test are kept. They are not part of what the Base Run diff and the comparison with another Testing Tool compare.
- **Tests running in parallel in one JVM.** A line is given to the test whose own thread logged it, and lines of other threads are dropped while more than one test listens: they can't be told apart.
- **`assertThat(page).hasNoErrors()`** fails when the test has an `ERROR` line so far, from the application or a console, and lists them. It is a User Step, `see no errors`, checked once without waiting: a logged error doesn't go away. Nothing fails a test for a logged error by itself.

### Evidence per User Step

Off by default; both settings below cost time and disk on every step.

- **Snapshots** (`vaadin.testing.user-steps.snapshots=true`): after each User Step, passed or failed, the step's window writes the page into `<test evidence dir>/steps/<number>`: a viewport screenshot (`.png`) in a browser, with its Step Target's box in the step's `highlight`; the component tree with its location (`.tree.txt`) on Browserless, with the Step Target marked at the end of its line (`  ← this step`). The Step Target is the component the step singled out to act on or to check, so a check marks what it checked. At most 200 per test. The step's `snapshot` field names it. A test's run starts by removing the snapshots an earlier run left. A text snapshot is masked like the reports; a Browserless tree shows a password field's value as `••••`. A snapshot that can't be taken is left out and never fails the test.
- **Playwright's trace** (`vaadin.testing.playwright.trace`: `off`, `on`, `on-failure`): the test's browser context records a trace with screenshots and DOM snapshots, each User Task and User Step a group named in its words, so Playwright's trace viewer lists the test's steps with their actions inside. It is kept as `<test evidence dir>/trace.zip` always (`on`) or when the test failed (`on-failure`), and named on the failure (`Recording: …`). Windows from `openWindow()` share it; users from `openAsUser(..)` have browser contexts of their own and are not traced. Open it at trace.playwright.dev, or with `mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace trace.zip"`.

Later outputs, built from the same `user-steps.json`: living documentation per view.

### `user-steps.json`, schema version 1

One file per test, at `<vaadin.testing.evidence-dir>/<test class>/<test method>/user-steps.json`, next to `user-steps.txt`. The test class is its binary name without the package (`EditPersonTest`, `UsersTest$AsAlice`). The n-th invocation of a parameterized or repeated test adds `[n]` to the method (`savesAll[2]`). UTF-8, written for every test that injects a `UnifiedPage`, passed or failed.

A reader checks `schemaVersion` first. Version 1 only adds fields in a compatible way: readers ignore fields they don't know. Optional fields are left out when they have no value, never written as `null`.

**Test**

| Field | Type | Meaning |
|---|---|---|
| `schemaVersion` | number | `1` |
| `testClass` | string | binary name, `demo.EditPersonTest` |
| `testMethod` | string | `editsAPersonInTheDialog` |
| `displayName` | string | the scenario: `@DisplayName`, or the method name |
| `tool` | string | `browserless`, `playwright` or `selenium` |
| `status` | string | `passed`, `failed` or `aborted` (an assumption failed) |
| `startedAt` | string | ISO-8601 instant, UTC |
| `durationMs` | number | from injecting the `UnifiedPage` to the end of the test |
| `stepCount` | number | User Steps recorded, groups and gaps not counted |
| `failure` | object, optional | only when `status` is `failed`, see below |
| `steps` | array | the entries, in order: User Steps, step groups and gaps |

**`failure`**

| Field | Type | Meaning |
|---|---|---|
| `type` | string | the exception class, `java.lang.AssertionError` |
| `message` | string, optional | its message, secrets masked |
| `stepIndex`, `stepNumber` | string, number, optional | the User Step that threw it; left out when the test failed outside any User Step (a plain JUnit assertion) |
| `codePath` | array of call sites | where it happened, outermost first: the call site of each `page.step(..)` it passed through, then the failed User Step's (or the first line of the failure's stack trace outside the Unified API) |
| `expected`, `found` | string, optional | a `UnifiedAssertionError`'s `Expected` and `Found` lines (see [Failures](#failures)) |
| `nearMisses` | array of strings, optional | its Near Misses, closest first: `text field “E-mail”`, `dialog “Help” (hidden)`; left out when there are none |
| `suggestion` | string, optional | its `Did you mean`: `find(UnifiedTextField.class).withLabel("E-mail")` |
| `highlight` | object, optional | where the failure's screenshot shows the failed step's Step Target, as a step's `highlight`; left out when no step singled out a component, and on Browserless |
| `evidence` | array of strings, optional | *(unreleased)* the failure evidence, relative to the test's evidence directory: `failure.png` (browsers) or `failure.tree.txt` (Browserless), and `failure.view.txt`; left out when none was written |
| `lookup` | string, optional | with `suggestion`: the failed lookup or call in the same form, as the test asked for it: `find(UnifiedTextField.class).withLabel("Email")`. The two differ in what to change; the `ApplyDidYouMean` recipe reads both |

**Entries** (`type` tells them apart)

| Field | In | Type | Meaning |
|---|---|---|---|
| `type` | all | string | `step`, `group` or `gap` |
| `index` | step, group | string | position in the tree, `2.1`. Gaps have none |
| `number` | step | number | 1-based among all User Steps of the test, in the order they finished |
| `text` | step, gap | string | the line as in `user-steps.txt`: `fill text field “Name” with “Alan M. Turing”` |
| `verb` | step | string | from the verb table: `open`, `click`, `fill`, `see`, `see no`, …; for add-on Wrappers their `@UserStepVerb` or method name in words |
| `kind` | step, optional | string | component kind, `combo box`; left out for page steps (`open People`) |
| `name` | step, optional | string | the main filter's value |
| `filters` | step, optional | array of strings | the other filters in words, `test id “billing”`; for grid rows the conditions, `Name is “Alan Turing”` |
| `position` | step, optional | number | 1-based position from `all()` |
| `scope` | step, optional | string | the containers, innermost first, `dialog “Edit person”` |
| `values` | step, optional | array of strings | values typed, chosen or expected; masked and cut as in the text |
| `name` | group | string | the `page.step(..)` name |
| `steps` | group | array | its entries |
| `reason` | gap | string | `Escape Hatch`, `waitUntil` or `where(..) condition` |
| `count` | gap | number | calls in this run of gaps. For the Escape Hatch: how often it was entered (`unwrap`, `tryUnwrap`, an Escape Hatch method), not what ran inside |
| `window` | all, optional | string | the window the entry ran in, `window 2` or `user bob`; left out for the test's first window |
| `status` | all | string | `passed` or `failed`. A test that catches a failure (`assertThrows`) keeps the failed step |
| `durationMs` | step, group | number | |
| `callSite` | all, optional | call site | the first stack frame outside the Unified API, its adapters, Wrapper interfaces and the JDK; for a group, the line calling `page.step(..)` |
| `error` | all, optional | string | when `failed`: exception class and first line of the message |
| `logs` | all, optional | array | the lines logged during the entry or after it, before the next one starts: `{ "source": "server", "level": "ERROR", "logger": "demo.PersonService", "message": "Could not save Alan", "error": "IllegalStateException: no id", "text": "server ERROR PersonService: Could not save Alan (IllegalStateException: no id)" }`. `source` is `server` (the application's log) or `console` (an error in the browser's console, `logger` left out); `level` is `ERROR` or `WARN`; `error` is the logged exception's class and first line; `text` is the line as `user-steps.txt` shows it |
| `readsAfter` | all, optional | number | how many values the test read after this entry and before the next one, outside any User Step: a Unified Element's getter, a Wrapper's getter, a grid row's cell. For a group: after it started and before its first entry. Left out when none. The reads are listed nowhere; the count tells a scenario that reads a value after its last action from one that checks nothing |
| `snapshot` | step, optional | string | with `vaadin.testing.user-steps.snapshots` on: the page right after the step, relative to the test's evidence directory: `steps/003.png` (browsers) or `steps/003.tree.txt` (Browserless) |
| `highlight` | step, optional | with a screenshot: where it shows the step's Step Target, as fractions of the image (0 to 1, clipped to it), `{ "x": 0.12, "y": 0.4, "width": 0.2, "height": 0.05 }`; or why it shows none, `{ "missing": "gone" }` (no longer on the page, or not shown) or `{ "missing": "outside" }` (outside the picture). Measured when the screenshot is taken. Left out when the step singled out no component, and on Browserless |

**Call site:** `{ "file": "EditPersonTest.java", "line": 24, "className": "demo.EditPersonTest", "method": "editPerson" }`. `file` is left out without debug information; `line` is `-1` when unknown; a lambda's method is the method that declares it.

The test itself has `readsBeforeFirstEntry` (number, optional): the values read before its first entry; `logsBeforeFirstEntry` (array, optional): the lines logged before it; and `logLinesLeftOut` (number, optional): how many lines beyond the first 100 of the test were dropped.

Values typed into a password field, and every value passed through `page.secret(..)`, are written as `••••` in every field. Values longer than 80 characters are cut with `…`.

### `summary.json`, schema version 1

One file per run, at `<vaadin.testing.evidence-dir>/summary.json`, next to `summary.md` and made as it is (which files, changed files, the Base Run). UTF-8. Every path in it is relative to the evidence directory, so the file reads the same in a downloaded CI artifact.

**The run**

| Field | Type | Meaning |
|---|---|---|
| `schemaVersion` | number | `1` |
| `tool` | string | the Testing Tool, lower case, as in `user-steps.json` |
| `status` | string | `failed` when any scenario failed, else `passed` |
| `startedAt` | string, optional | when the first scenario started (UTC, ISO-8601); left out for a run whose tests were all skipped without running |
| `durationMs` | number, optional | from `startedAt` to the end of the scenario that ended last |
| `commit` | string, optional | the commit the run tested, 40 hex digits (`git rev-parse HEAD`); left out without git |
| `uncommittedChanges` | string, optional | with `commit`, when tracked files differ from it: a fingerprint of `git diff HEAD`, 12 hex digits. Two runs of the same commit with the same fingerprint tested the same tracked files |
| `passed`, `failed`, `skipped` | number | the counts of `summary.md`'s header. `skipped` is the aborted scenarios plus the Unified UI Tests skipped without running, which have no entry in `scenarios` |
| `changedSince` | string, optional | the base ref of "Changed in this PR"; left out outside a PR |
| `comparedWith` | array, optional | with a run on another Testing Tool to compare with, one object per tool: `tool`, `both` (scenarios that ran on both), `endDifferently`, `takeDifferentSteps`, `onlyHere`, `onlyThere`, `sameCode` (boolean; left out when either run names no commit), and the other run's `commit` and `uncommittedChanges` when its files agree on one |
| `scenarios` | array | every scenario of the run, passed, failed or aborted, by test class, then start |

**A scenario**

| Field | Type | Meaning |
|---|---|---|
| `testClass`, `testMethod`, `displayName`, `status`, `startedAt`, `durationMs` | | as in its `user-steps.json` |
| `evidenceDir` | string | the test's evidence directory, `EditPersonTest/editsAPerson/` |
| `userSteps` | string | its `user-steps.json`, `EditPersonTest/editsAPerson/user-steps.json`; `user-steps.txt` is next to it |
| `change` | string, optional | under "Changed in this PR": `new` or `changed` |
| `calledFromChangedFiles` | array, optional | for a changed scenario: `{ "file": "PeopleTasks.java", "methods": ["editPerson"] }` for each changed file it calls from |
| `stepsDiff` | object, optional | for a changed scenario found in the Base Run: `{ "added": 1, "removed": 2 }`, the counts of the diff's lines; both `0` for the same User Steps |
| `differsOn` | array, optional | for a scenario that differs on another Testing Tool, one object per tool: `tool`, `status` there, `partsAt` when the User Steps differ (`step`, counted from 1, and the line `here` and `there`; a side whose steps end before is left out), and the `failure` object from there when it failed there |
| `unchecked` | string, optional | for a passing scenario without a check: `all` when it has no check, `end` when User Steps follow its last check |
| `uncheckedSteps` | array of strings, optional | with `unchecked`: the texts of the User Steps nothing checks, in order |
| `failedStep` | string, optional | for a failure in a User Step: the step's text |
| `failure` | object, optional | for a failed scenario: the `failure` object of its `user-steps.json`, unchanged |
| `evidence` | array of strings, optional | for a failed scenario: its failure evidence (`EditPersonTest/editsAPerson/failure.png`) *(unreleased)*, and the Playwright trace this run kept for it |

The steps themselves are not repeated: they are in each scenario's `user-steps.json`. A reader ignores fields it doesn't know.

## Failures

Every Unified failure throws `UnifiedAssertionError extends AssertionError`, so JUnit reports a failure, not an error. Its message uses fixed labels in a fixed order, and lines with nothing to say are left out. The same parts are available as fields.

```
UnifiedAssertionError: Editing a person in the dialog updates the grid
  Failed at     User Step 5 of 10: fill text field “Email” with “alan@bletchley.uk”
  In step       Edit Alan Turing
  Expected      exactly one text field labelled “Email” in dialog “Edit person”
  Found         none
  Nearby        text field “Name”, text field “E-mail”, combo box “Role”
  Did you mean  find(UnifiedTextField.class).withLabel("E-mail")
  Waited        10 s (vaadin.testing.timeout)
  Tool          playwright (chromium)
  Code          EditPersonTest.java:13 (editsAPersonInTheDialog) ← EditPersonTest.java:24 (editPerson)
  Evidence      target/surefire-reports/unified-evidence/EditPersonTest/editsAPersonInTheDialog/  (failure.png, failure.view.txt)
```

- **Near Misses** are computed on failure only:
  - components of the same Wrapper type in the same scope, showing the property the failed filter matched on: at most 5, closest first (same text ignoring case, then contains, then edit distance);
  - then up to 3 components of another kind with the exact name wanted;
  - hidden components marked `(hidden)`;
  - if the scope itself is missing, Near Misses for the scope instead;
  - *(unreleased)* if nothing in the scope matches, the Templates there, and in them, inside which the lookup does match, each with the lookup that enters them (`text field (id “filter”) in Template <demo-search-bar> in Template <demo-orders-panel>: findTemplate(OrdersPanel.class).findTemplate(SearchBar.class).find(UnifiedTextField.class).withId("filter")`). A browser knows a Template by its tag only, so the lookup names the Wrapper or class the test has looked up for that tag, the same on every Testing Tool, and `findTemplate(<the class with @Tag("…")>.class)` otherwise. *Did you mean* gives the lookup when it is the only match and names no placeholder.
- **Did you mean** appears only when one Near Miss is clearly best, by the first of these rules that applies: the only one of the same Wrapper type that equals the wanted name compared on letters and digits, ignoring case; else the only one of the same Wrapper type at the smallest edit distance (Levenshtein, ignoring case), if that is at most `max(2, length / 4)` of the wanted name; else the only one of another kind with the exact name. When the last two both find one, there is none. There is none either when a component of the same Wrapper type has exactly the wanted name, and none for a name that only contains the wanted one. It is written from `find(..)` on, in ADR 0007 style. *(unreleased)* `user-steps.txt`, and so the CI log, shows it as the line `Did you mean  find(..)` under the failure.
- **Other `Found` lines:**
  - several matches (`Found 2: …`, each with its scope, no suggestion);
  - an assertion timeout (the distinct values seen while waiting: `Found “Saving…”, then “Saved Alan Turing”`);
  - not usable (`Found 1, but disabled`, or hidden, read-only, covered by an open dialog).

  The message is the same on every Testing Tool.
- **When the parts are known.** Expected, Found, Nearby, Did you mean, Waited and Tool are known when the failure is thrown. The failed User Step, the step chain and Code are added when its User Step ends; the scenario and the evidence directory are known from the start of the test. `m` in `User Step n of m` is the number of User Steps the test recorded, read when the message is: after the test, all of them. `getMessage()` renders the message from its parts each time it is read, so the Test Runner, which reads it after the test, reports it whole. A test that catches the failure (`assertThrows`) reads it as it is then.
- **Without a scenario** (outside a Unified UI Test), the first line is the summary, `Expected …, found …`. The User Step lists, the `error` of a step in `user-steps.json` and the run summary also show the summary, since the scenario is their heading.
- **Waited** names where the timeout came from: `vaadin.testing.timeout`, or `withTimeout` for a Wrapper's own.
- **Items a component lacks** (`selectItem`, `selectTab`) fail the same way, with the component's items as Near Misses (`item “Finland”`) and a suggestion such as `selectItem("France")`.
- **Several matches** are told apart by their nearest ancestor with a test id or an id: `in container (test id “billing”)`. Flow's own root, whose id a browser sees as `ROOT-` and a number, is never named: it holds every view and Browserless has no such element.
- **`UnifiedElement.getAccessibleName()`** reads the accessible name (4. Locating's order), for Near Misses and for tests.

## Findable View

What a Unified UI Test can find in a page or a container, one line per component: its kind, name and state in User Step words, and the lookup that singles it out ([ADR 0009](../adr/0009-findable-view-in-lookups-not-aria-yaml.md)).

```
Findable view of “/profile” on playwright (chromium): 11 components
dialog “Change email”                   page.find(UnifiedDialog.class).withHeaderTitle("Change email")
text field “Name”                       page.find(UnifiedTextField.class).withLabel("Name")
text field (no name; placeholder “Nickname”)  page.find(UnifiedTextField.class).withPlaceholder("Nickname")
button “Delete account”                 page.find(UnifiedButton.class).withAccessibleName("Delete account")
button “Archive” (disabled)             page.find(UnifiedButton.class).withText("Archive")
button “Save”                           not unique: 2 alike; one in dialog “Change email”: page.find(UnifiedDialog.class).withHeaderTitle("Change email").find(UnifiedButton.class).withText("Save")
…
```

- **Where.** `describe()` on the page or on any Scope (a dialog, a container) returns it; a container's lookups are on the container. On failure it is written into the test's evidence directory, as `failure.view.txt` *(unreleased)*, on every Testing Tool ([09. Capabilities](09-capabilities.md#failure-evidence)).
- **What is listed.** The components `find(Type.class)` finds in the scope, hidden ones included, for the Wrappers of the Unified API that name a component (fields, buttons, checkbox, radio button group, grid, tabs, tab sheet, menu bar, details, dialog, notification, upload), then the custom Wrappers the test has looked up. A custom Wrapper of the same tag and selector as one listed already is left out. Plain HTML Wrappers and `UnifiedContainer` are not listed. *(unreleased)* A component's internal parts, which it renders itself and the application did not create, are not listed: in a browser, the text area inside a message input is not, while a text area the application adds next to it is. A browser tells them apart by Flow's client: an internal part has no server-side element bound to it, but an ancestor in the same DOM tree does. Lines come grouped by Wrapper type, at most 100 of them.
- **The name** is the first value the component has among the Wrapper's own naming filters (`withHeaderTitle`, as for the User Steps), its label, text and accessible name. An accessible name that is only the component's text doesn't count. A field, button or checkbox without one says `(no name; placeholder …)` or `(no name; test id …)`: an accessibility finding.
- **The state.** The value of a text, number, date or date-time field (`= “Alan Turing”`), `(checked)`, `(disabled)` and `(hidden)`. A grid shows its row count and column headers; *(unreleased)* a message list its message count (`message list, 3 messages`).
- **The lookup** uses the first filter whose value only this component has, among its naming filters, placeholder and test id, compared as the exact filters compare. It is the bare `find(Type.class)` when the component is the only one of its type and has no such value. Otherwise the line says how many are alike and gives a lookup through a container: its nearest ancestor with a test id or an id (`UnifiedContainer`), or a dialog or other Scope listed with a lookup of its own. That lookup is run first and printed only when it finds exactly one. *(unreleased)* When the nearest container is the only one with its test id or id but holds several alike, the line says how many: `not unique: 3 alike; 2 in container (test id “billing”)`; a container whose test id or id others share too (every row of a list) is not named ([#522](https://github.com/parttio/unified-testing-tool/issues/522)). Flow's generated root id (`ROOT-…`) is never used.
- **Templates.** *(unreleased)* A [Template](04-locating.md#templates) is one line, `Template <demo-orders-panel>`, with the lookup that enters it, named as in the Near Misses; what is inside it is listed by `describe()` on the Template. A Template the test has a Wrapper for is listed as that Wrapper.
- **Not a User Step.** Its reads are not recorded or counted as the test's reads. It is for people and agents, not for assertions, and its format may change between releases.
- **Cost.** One query per Wrapper type and one read of each match. In a browser it runs only on failure or when asked.

## Style rules

An opt-in module, `unified-test-archunit` (test scope, in the BOM), checks the style in a plain `@Test`:

```java
class UnifiedStyleTest {
    @Test
    void followsTheUnifiedStyle() {
        UnifiedStyleRules.ALL.check(new ClassFileImporter().importPackages("com.example"));
    }
}
```

| Rule | Fails when |
|---|---|
| Scenario sentence | a `@Test` in a Unified UI Test has no `@DisplayName` and its class no `@DisplayNameGeneration` |
| No raw waits | `Thread.sleep(..)` or a Testing Tool's own wait API is called |
| Testing Tool code needs its Capability | a Unified UI Test class calls an Escape Hatch or depends on Playwright, TestBench or Browserless packages, directly or through its own helpers, without `@RequiresTestingTool` on the class or test method |

Suites with existing violations can wrap the rules in ArchUnit's `FreezingArchRule`. Everything bytecode can't check reliably stays in the guide: `all()` only in loops, no plain JUnit assertions on what the user sees, shared User Tasks rather than copies.

## The agent skill

`write-unified-ui-tests` is the one place agents learn this chapter. It ships in a jar, matching the API version. The recipe `AddUnifiedTestingSkill` (run by every Migration Recipe, or alone) writes it to `.claude/skills/write-unified-ui-tests/SKILL.md`, replaces an older stamped copy, leaves an unstamped (hand-edited) copy alone, and adds a pointer section to `AGENTS.md` (or to `CLAUDE.md`, see below).

The skill covers:
- the canonical forms;
- reading the view class (and its translation keys) before writing a lookup;
- `@DisplayName`, comments and User Tasks;
- proving the test on Browserless then Playwright, plus `UnifiedStyleTest` and `user-steps.txt`;
- reading a failure;
- reviewing a test: the scenario against its User Steps, the code against the canonical forms, the runs, and what a change made to pass took away;
- when the Escape Hatch is acceptable.

How the recipe decides:

- **Stamp.** The shipped skill carries `<!-- unified-test-skill: write-unified-ui-tests <version> -->`, the release it came with (Maven resource filtering). A copy with an older version is replaced; one with the same or a newer version is left as it is; one without the line counts as hand-edited, is kept, and gets an OpenRewrite warning (`Markup.Warn`) naming how to take the shipped one.
- **`AGENTS.md`, or `CLAUDE.md`.** The pointer is a section between `<!-- unified-test: write-unified-ui-tests -->` and `<!-- /unified-test -->`, added at the end or replaced where it is; the rest of the file is left as it is. It goes in `AGENTS.md`, created when missing. A project with a `CLAUDE.md` and no `AGENTS.md` gets it in `CLAUDE.md` instead, and no `AGENTS.md`: Claude Code reads `CLAUDE.md` ([#365](https://github.com/parttio/unified-testing-tool/issues/365)). With both files, only `AGENTS.md` gets it.
- **Where.** It writes only at a project's root: one with a build file (`pom.xml`, Gradle), an `AGENTS.md`, a `CLAUDE.md` or the skill, and Java sources.

A second `skill-eval` suite (`unified-test-rewrite/skill-eval/writing/`, `scripts/eval-writing-skill.sh`) scores it: prose scenarios in, tests out, judged on compiling, passing on two Testing Tools, the style rules, intent share and no Escape Hatch. `resolve-migration-markers` defers to it for style.

### What an agent reads

- **The skill**, above, installed in the project.
- **The [Findable View](#findable-view)**: `page.describe()` while writing a test, and `failure.view.txt` after a failure, for lookups that match what is on the page.
- **The API reference**: `META-INF/unified/api-reference.md` in the `unified-test-api` jar, so it is of the version the project uses and needs no network. Every Wrapper with its filters, actions, reads and checks, and the page's. Generated from the Wrapper interfaces and `UnifiedAssertions`; a test of the build fails when the file is not what the code says. The docs site has the same page.
- **The docs site** for agents: `llms.txt` at its root (one link per page, to the page as Markdown with its code examples included; the specification and the ADRs under *Optional*), and `llms-full.txt` (the home page, the API reference, the developer guide and the glossary in one file). Written by the site's build from its navigation.

## Moving to this chapter

A clean break, since the API is alpha: no deprecation release and no Upgrade Recipe. The release that implements ADR 0007 also moves the Migration Recipes and their golden pairs, the demo tests, the custom Wrappers, `resolve-migration-markers`, the guide and chapters 4–9. The Migration Recipes' `first()` rule is amended in [ADR 0004](../adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md).

### Earlier chapters

[4. Locating](04-locating.md), [5. Waiting](05-waiting.md), [6. Wrappers](06-wrappers.md), [7. Grid](07-grid.md), [8. Escape Hatch](08-escape-hatch.md) and [9. Capabilities](09-capabilities.md) follow this chapter: lookups without a terminal, counts and absence as retrying assertions, filter mixins, `row()`, failures as in [Failures](#failures), gap lines and the style rule for Escape Hatch calls.

## Settings

| Setting | Default |
|---|---|
| `vaadin.testing.user-steps.log` | `failed` (or `all`, `none`) |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` when `GITHUB_STEP_SUMMARY` is set |
| `vaadin.testing.user-steps.changed-since` | in a PR, `origin/$GITHUB_BASE_REF`; otherwise unset |
| `vaadin.testing.user-steps.baseline-dir` | unset; the Base Run's evidence directory |
| `vaadin.testing.user-steps.compare-dir` | unset; the evidence directory of a run of the same tests on another Testing Tool: the run summary says which scenarios differ between the two |
| `vaadin.testing.user-steps.server-log` | `warn`: the application's warnings and errors under the User Step during which they were logged; `error`, or `off` |
| `vaadin.testing.user-steps.snapshots` | `false`; `true`: a snapshot of the page after every User Step |
| `vaadin.testing.playwright.trace` | `off`; `on` or `on-failure`: keep Playwright's trace, grouped by User Step |

## Requirements on Testing Tool adapters

- Let the core list Near Misses: re-run a lookup without its user-facing filters, and read `label`, text, `placeholder`, `data-testid` and the accessible name of each candidate. *(unreleased)* For the Findable View, also say whether each is an internal part of another component (`ElementFacts.internalPart()`).
- Expose the accessible name through the Unified Element.
- Give grid row and column actions the same Auto-Wait as every other action.
- Provide failure evidence for the failed User Step (`AdapterSession.writeFailureEvidence`): a screenshot in a browser, the component tree on Browserless, marking the Step Target the core passes in.
- When asked, write a snapshot after a User Step (`AdapterSession.writeStepSnapshot`): a viewport screenshot in a browser, the component tree on Browserless, marking the Step Target the core passes in.
- Both return `Evidence`: the files written and, for a screenshot, the Step Target's `Highlight` (`Highlight.of(..)` turns a box in pixels into clipped fractions of the image; `Highlight.gone()` when it is no longer shown). Measure without waiting, and never let a highlight cost the screenshot. Never change the page to mark it.
- An adapter with a trace of its own groups it by User Step (`tracesSteps`, `stepStarted`, `stepEnded`) and keeps it in the test's evidence directory when the test ends (`testEnded`). All four default to doing nothing.
