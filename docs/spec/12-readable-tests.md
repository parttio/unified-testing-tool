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
| GitHub job summary | `summary.md` appended when `GITHUB_STEP_SUMMARY` is set | every run in GitHub Actions | the reviewer |

**`summary.md`**, in order:

1. **Header:** Testing Tool and counts.
2. **Changed in this PR:** scenarios with any User Step or User Task called from a file changed against the base branch (`GITHUB_BASE_REF`, or `vaadin.testing.user-steps.changed-since`), steps unfolded, marked new or changed. A change in a shared `…Tasks` class marks every scenario using it. Left out outside a PR.
3. **Failed scenarios:** `@DisplayName`, the failed User Step, Near Misses, evidence path.

Passing scenarios count in the header only, unless they are under "Changed in this PR": a list of every passing scenario is long to scroll past on the job summary, and each one's User Steps are in its `user-steps.txt`. Skipped scenarios count in the header only too: tests that ended `aborted`, and Unified UI Tests the run skipped without running (disabled, or lacking a Capability).

How `summary.md` is made:

- **Where and when.** `<vaadin.testing.evidence-dir>/summary.md`, written when the JUnit Platform has run the test plan (a `TestExecutionListener` registered through `META-INF/services`). A run without Unified UI Tests writes none. Tests that launch other tests through the JUnit launcher don't write one.
- **Which files.** The `user-steps.json` under the evidence directory whose `startedAt` is not before the run's start: the Maven JVM that started the Surefire forks, else the test JVM. Each fork rewrites the summary when it ends, from the files of all forks, so the last one writes the whole run.
- **Changed files.** `git diff --name-status --no-renames <merge base of the ref and HEAD>` plus untracked files, run in the project's directory. In CI that equals `git diff <ref>...HEAD`; locally, uncommitted changes count too. Without git, or with an unknown ref, the section is left out and one line is logged.
- **Matching.** A call site matches a changed file whose path ends with `<package as directories>/<callSite.file>` (the outermost class's name plus `.java` when `file` is missing). A change anywhere in a file marks every scenario calling from it.
- **New or changed.** New when the test class's file was added (renamed files count as added), or when that file at the merge base has no `<test method>(`. Otherwise changed.
- **GitHub job summary.** Put in a marked block, one per evidence directory and run: a later fork replaces the block an earlier one wrote.

**`report.html`** is written with `summary.md`, from the same files and the same `ChangedFiles`. One file with its CSS and script inline, so it opens from a downloaded CI artifact; its links are relative to the evidence directory. In order:

1. **Header:** Testing Tool, counts, the run's start (UTC) and duration.
2. **Controls:** show passing (off), changed only (with a base ref), and a search over scenario names, classes and step texts.
3. **Changed in this PR:** as in `summary.md`, marked new or changed; failed scenarios unfolded, passing ones folded.
4. **Failed scenarios:** Failed at, Expected and Found (or the error line), Nearby, Did you mean, the code path, the failure evidence (`<test class>.<test method>.*` at the evidence directory's root: a screenshot shown inline, other files linked), then the User Steps tree with the failed step marked.
5. **Passing scenarios:** hidden until the reader shows them or searches; one folded line each.

Each step shows its index, window, text, duration and call site, and its snapshot when it has one: a screenshot opens under the step, a tree is a link. Step groups nest and fold; gaps are dimmed. Every value is HTML-escaped. Aborted scenarios count in the header only. Each scenario links its `user-steps.txt`, `user-steps.json`, and the Playwright trace this run kept for it (`trace.zip`).

### Evidence per User Step

Off by default; both settings below cost time and disk on every step.

- **Snapshots** (`vaadin.testing.user-steps.snapshots=true`): after each User Step, passed or failed, the step's window writes the page into `<test evidence dir>/steps/<number>`: a viewport screenshot (`.png`) in a browser, the component tree with its location (`.tree.txt`) on Browserless. At most 200 per test. The step's `snapshot` field names it. A test's run starts by removing the snapshots an earlier run left. A text snapshot is masked like the reports; a Browserless tree shows a password field's value as `••••`. A snapshot that can't be taken is left out and never fails the test.
- **Playwright's trace** (`vaadin.testing.playwright.trace`: `off`, `on`, `on-failure`): the test's browser context records a trace with screenshots and DOM snapshots, each User Task and User Step a group named in its words, so Playwright's trace viewer lists the test's steps with their actions inside. It is kept as `<test evidence dir>/trace.zip` always (`on`) or when the test failed (`on-failure`), and named on the failure (`Recording: …`). Windows from `openWindow()` share it; users from `openAsUser(..)` have browser contexts of their own and are not traced. Open it at trace.playwright.dev, or with `mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace trace.zip"`.

Later outputs, built from the same `user-steps.json`: living documentation per view, and a step-level diff against the base branch's last run ([#264](https://github.com/parttio/unified-testing-tool/issues/264)).

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
| `snapshot` | step, optional | string | with `vaadin.testing.user-steps.snapshots` on: the page right after the step, relative to the test's evidence directory: `steps/003.png` (browsers) or `steps/003.tree.txt` (Browserless) |

**Call site:** `{ "file": "EditPersonTest.java", "line": 24, "className": "demo.EditPersonTest", "method": "editPerson" }`. `file` is left out without debug information; `line` is `-1` when unknown; a lambda's method is the method that declares it.

Values typed into a password field, and every value passed through `page.secret(..)`, are written as `••••` in every field. Values longer than 80 characters are cut with `…`.

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
  Evidence      target/surefire-reports/unified-evidence/EditPersonTest/editsAPersonInTheDialog/
```

- **Near Misses** are computed on failure only:
  - components of the same Wrapper type in the same scope, showing the property the failed filter matched on: at most 5, closest first (same text ignoring case, then contains, then edit distance);
  - then up to 3 components of another kind with the exact name wanted;
  - hidden components marked `(hidden)`;
  - if the scope itself is missing, Near Misses for the scope instead.
- **Did you mean** appears only when one Near Miss is clearly best: it differs only in case, spacing or punctuation; or it is within a small edit distance with no equal rival; or it has the exact name but is another kind. It is written from `find(..)` on, in ADR 0007 style.
- **Other `Found` lines:**
  - several matches (`Found 2: …`, each with its scope, no suggestion);
  - an assertion timeout (the distinct values seen while waiting: `Found “Saving…”, then “Saved Alan Turing”`);
  - not usable (`Found 1, but disabled`, or hidden, read-only, covered by an open dialog).

  The message is the same on every Testing Tool.
- **When the parts are known.** Expected, Found, Nearby, Did you mean, Waited and Tool are known when the failure is thrown. The failed User Step, the step chain and Code are added when its User Step ends; the scenario and the evidence directory are known from the start of the test. `m` in `User Step n of m` is the number of User Steps the test recorded, read when the message is: after the test, all of them. `getMessage()` renders the message from its parts each time it is read, so the Test Runner, which reads it after the test, reports it whole. A test that catches the failure (`assertThrows`) reads it as it is then.
- **Without a scenario** (outside a Unified UI Test), the first line is the summary, `Expected …, found …`. The User Step lists, the `error` of a step in `user-steps.json` and the run summary also show the summary, since the scenario is their heading.
- **Waited** names where the timeout came from: `vaadin.testing.timeout`, or `withTimeout` for a Wrapper's own.
- **Items a component lacks** (`selectItem`, `selectTab`) fail the same way, with the component's items as Near Misses (`item “Finland”`) and a suggestion such as `selectItem("France")`.
- **Several matches** are told apart by their nearest ancestor with a test id or an id: `in container (test id “billing”)`.
- **`UnifiedElement.getAccessibleName()`** reads the accessible name (4. Locating's order), for Near Misses and for tests.

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

`write-unified-ui-tests` is the one place agents learn this chapter. It ships in a jar, matching the API version. The recipe `AddUnifiedTestingSkill` (run by every Migration Recipe, or alone) writes it to `.claude/skills/write-unified-ui-tests/SKILL.md`, replaces an older stamped copy, leaves an unstamped (hand-edited) copy alone, and adds a pointer section to `AGENTS.md`.

The skill covers:
- the canonical forms;
- reading the view class (and its translation keys) before writing a lookup;
- `@DisplayName`, comments and User Tasks;
- proving the test on Browserless then Playwright, plus `UnifiedStyleTest` and `user-steps.txt`;
- reading a failure;
- when the Escape Hatch is acceptable.

How the recipe decides:

- **Stamp.** The shipped skill carries `<!-- unified-test-skill: write-unified-ui-tests <version> -->`, the release it came with (Maven resource filtering). A copy with an older version is replaced; one with the same or a newer version is left as it is; one without the line counts as hand-edited, is kept, and gets an OpenRewrite warning (`Markup.Warn`) naming how to take the shipped one.
- **`AGENTS.md`.** The pointer is a section between `<!-- unified-test: write-unified-ui-tests -->` and `<!-- /unified-test -->`, added at the end or replaced where it is; the file is created when missing.
- **Where.** It writes only at a project's root: one with a build file (`pom.xml`, Gradle), an `AGENTS.md` or the skill, and Java sources.

A second `skill-eval` suite (`unified-test-rewrite/skill-eval/writing/`, `scripts/eval-writing-skill.sh`) scores it: prose scenarios in, tests out, judged on compiling, passing on two Testing Tools, the style rules, intent share and no Escape Hatch. `resolve-migration-markers` defers to it for style.

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
| `vaadin.testing.user-steps.snapshots` | `false`; `true`: a snapshot of the page after every User Step |
| `vaadin.testing.playwright.trace` | `off`; `on` or `on-failure`: keep Playwright's trace, grouped by User Step |

## Requirements on Testing Tool adapters

- Let the core list Near Misses: re-run a lookup without its user-facing filters, and read `label`, text, `placeholder`, `data-testid` and the accessible name of each candidate.
- Expose the accessible name through the Unified Element.
- Give grid row and column actions the same Auto-Wait as every other action.
- Provide failure evidence for the failed User Step: a screenshot in a browser, the component tree on Browserless.
- When asked, write a snapshot after a User Step (`AdapterSession.writeStepSnapshot`): a viewport screenshot in a browser, the component tree on Browserless.
- An adapter with a trace of its own groups it by User Step (`tracesSteps`, `stepStarted`, `stepEnded`) and keeps it in the test's evidence directory when the test ends (`testEnded`). All four default to doing nothing.
