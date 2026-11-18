# Reading reports and failures

Every Wrapper action and every retrying assertion is recorded as a **User Step**, worded the way a user would say it: `fill text field “Name” with “Alan M. Turing”`. `page.step(..)` groups User Steps under a **User Task** ([Structuring a scenario](structuring-a-scenario.md)). Recording is always on, on every Testing Tool, and the same steps reach every output below. Spec: [12. Readable tests, Reports](../spec/12-readable-tests.md#reports) and [Failures](../spec/12-readable-tests.md#failures).

| Output | Where | When | For |
|---|---|---|---|
| [The CI log](#the-ci-log) | stdout and the test report | failed tests, by default | whoever reads CI |
| [`user-steps.txt`, `user-steps.json`](#the-user-steps-of-a-test) | the evidence directory, per test | every test | people, agents and tools |
| [`summary.md`](#the-run-summary) | the evidence directory, per run | every run | the reviewer |
| [`report.html`](#the-html-report) | the evidence directory, per run | every run | the reviewer, in a browser |
| [`summary.json`](#the-run-as-data) | the evidence directory, per run | every run | agents and CI tooling |
| [GitHub job summary](#the-github-job-summary) | the job's summary page | every run in GitHub Actions | the reviewer |

The examples on this page are real output of the demo's tests on Browserless, with one test added that looks for a field labelled *E-mail* in a view that labels it *Email*:

```java
@Test
@DisplayName("Changing a person's email updates the grid")
void changesTheEmail(UnifiedPage page) {
    // given
    page.navigate(PeopleView.class);

    // when
    changeEmail(page, "Alan Turing", "alan@bletchley.uk");

    // then
    assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan Turing");
}

private static void changeEmail(UnifiedPage page, String name, String email) {
    page.step("Change the email of " + name, () -> {
        page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
        page.find(UnifiedTextField.class).withLabel("E-mail").setValue(email);
        page.find(UnifiedButton.class).withText("Save").click();
    });
}
```

## The failure message

Every Unified failure throws `UnifiedAssertionError`, an `AssertionError`, so JUnit reports a failure, not an error. Its message has fixed labels in a fixed order, and leaves out the lines with nothing to say. It reads the same on every Testing Tool:

```
com.vaadin.testing.unified.UnifiedAssertionError:
Changing a person's email updates the grid
  Failed at     User Step 3 of 3: fill text field “E-mail” with “alan@bletchley.uk”
  In step       Change the email of Alan Turing
  Expected      exactly one text field labelled “E-mail”
  Found         none
  Nearby        text field “Email”, text field “Name”
  Did you mean  find(UnifiedTextField.class).withLabel("Email")
  Waited        10 s (vaadin.testing.timeout)
  Tool          browserless
  Code          EditEmailTest.java:31 (changeEmail) ← EditEmailTest.java:33 (changeEmail)
  Evidence      target/surefire-reports/unified-evidence/EditEmailTest/changesTheEmail/
```

| Line | Says |
|---|---|
| first line | the scenario: the test's `@DisplayName`, or its method name |
| `Failed at` | the User Step that failed, and how many the test recorded |
| `In step` | the `page.step(..)` names it was in, outermost first |
| `Expected` | what the lookup or assertion wanted, in words |
| `Found` | `none`; several matches (`2: …`, each with where it is); `1, but disabled` (or hidden, read-only, covered by an open dialog); or, for an assertion, the values it saw while waiting |
| `Nearby` | the Near Misses: components the lookup almost matched |
| `Did you mean` | the lookup you probably meant, when one Near Miss is clearly best |
| `Waited` | how long, and where the timeout came from: `vaadin.testing.timeout`, or `withTimeout` |
| `Tool` | the Testing Tool, and its browser |
| `Code` | where it happened: the line that called the step, then the line inside it |
| `Evidence` | the test's evidence directory: its User Steps, and the screenshot or component tree |

### Near Misses

When a lookup finds nothing, the failure lists what is there instead, so you can see what the test should have asked for:

- components of the same kind in the same scope, with the property the filter matched on: at most 5, closest first (the same text ignoring case, then containing it, then by spelling);
- then up to 3 components of another kind with the exact name the test wanted (`combo box “Email”`);
- hidden components, marked `(hidden)`;
- when the scope itself is missing (a dialog that isn't open), the Near Misses of the scope.

### Did you mean

`Did you mean` appears only when one Near Miss is clearly what the test meant: it differs only in case, spacing or punctuation; or it is a small edit away with no equal rival; or it has the exact name but is another kind. It is written as the lookup to paste, from `find(..)` on. A dialog whose title the test misspelled:

```
  Failed at     User Step 3 of 3: click button “Close” in dialog “Export Report”
  Expected      exactly one dialog with header title “Export Report”
  Found         none
  Nearby        dialog “Export report”
  Did you mean  find(UnifiedDialog.class).withHeaderTitle("Export report")
```

### Other failures

Two components match where the test meant one. They are told apart by their nearest container with a test id or an id, and there is no suggestion:

```
  Failed at     User Step 2 of 2: fill text field “City” with “Turku”
  Expected      exactly one text field labelled “City”
  Found         2: text field “City” in container (test id “billing”), text field “City” in container (id “shipping”)
```

An assertion that times out lists the values it saw while it waited:

```
  Failed at     User Step 4 of 4: see span “Saved Alan M. Turing” (id “status”)
  Expected      span (id “status”) to have text “Saved Alan M. Turing”
  Found         “Saving…”, then “Saved Alan Turing”
```

A component that is there but can't be used:

```
  Failed at     User Step 2 of 2: click button “Save”
  Expected      a visible and enabled button with text “Save”
  Found         1, but disabled
```

### In code

The parts are getters on `UnifiedAssertionError`, for a test that checks a failure with `assertThrows`: `getSummary()`, `getScenario()`, `getFailedStep()`, `getStepChain()`, `getExpected()`, `getFound()`, `getNearMisses()`, `getSuggestion()`, `getWaited()`, `getTool()`, `getCodePath()`, `getEvidenceDir()`.

## The CI log

A failed test prints its User Steps to stdout, so the CI log shows them next to the failure. The failed step is marked `✘`, followed by the failure in one line and where it happened:

```
✘ EditEmailTest › Changing a person's email updates the grid   browserless · 11.4 s
   1  open People
   2  Change the email of Alan Turing
      2.1  click row where Name is “Alan Turing” in grid
      2.2  fill text field “E-mail” with “alan@bletchley.uk”   ✘
   ✘ UnifiedAssertionError: Expected exactly one text field labelled “E-mail”, found none
     at EditEmailTest.java:31 (changeEmail) ← EditEmailTest.java:33 (changeEmail)
```

The same text is attached to the failure, so Surefire's report (`target/surefire-reports/<test class>.txt`) and the IDE show it under the stack trace. Set `vaadin.testing.user-steps.log` to `all` to print the steps of passing tests too, or to `none`.

## The User Steps of a test

Every test that injects a `UnifiedPage` writes two files to `<evidence dir>/<test class>/<test method>/`, passed or failed:

- **`user-steps.txt`**, the text above: for people, and for an agent reading why a test failed.
- **`user-steps.json`**, the same steps as data (`"schemaVersion": 1`), for tools. Each entry has its verb, component kind, name, filters, scope, values, status, duration and the line of code that called it ([schema](../spec/12-readable-tests.md#user-stepsjson-schema-version-1)). A failed test has a `failure` with the same parts as the message:

```
"failure": {
  "type": "com.vaadin.testing.unified.UnifiedAssertionError",
  "stepIndex": "2.2",
  "stepNumber": 3,
  "expected": "exactly one text field labelled “E-mail”",
  "found": "none",
  "nearMisses": [ "text field “Email”", "text field “Name”" ],
  "suggestion": "find(UnifiedTextField.class).withLabel(\"Email\")",
  …
}
```

Next to them, a failed test leaves its failure evidence: a screenshot on Playwright and Selenium, the component tree as text on Browserless (`<test class>.<method>.tree.txt` in the evidence directory). The evidence directory is `target/surefire-reports/unified-evidence` by default, so CI can upload it with the test reports.

### Reading a User Step

`<verb> <component kind> “<name>” [with “<value>”] [in <scope>]`

| Part | Comes from |
|---|---|
| verb | the action: *open*, *click*, *fill*, *choose*, *check*, *select*, …; *see* or *see no* for an assertion |
| component kind | the Wrapper in words: `UnifiedComboBox` → *combo box*. An add-on Wrapper can name its own ([Wrappers for your own components](custom-wrappers.md#its-words-in-the-reports)) |
| name | the main filter: label, text or accessible name. Other filters follow in brackets: `(test id “billing”)` |
| value | what was typed, chosen or expected. Password fields and `page.secret(..)` values show as `••••`; values over 80 characters are cut with `…` |
| scope | the container: `in dialog “Edit person”`, `in grid` |

What the recorder can't see is never left out silently. Escape Hatch calls, `page.waitUntil(..)` and a `where(..)` written in the test show as one gap line, such as `… 1 call outside the Unified API (waitUntil)`.

## The run summary

When the tests have run, the Unified API writes `<evidence dir>/summary.md`: the run's scenarios in the words of the reports, for the reviewer of a pull request. In order:

1. a header with the Testing Tool and the counts;
2. **Changed in this PR**: the scenarios that call code changed against the base branch, marked *new* or *changed*. A change in a shared `…Tasks` class marks every scenario that uses it. A new scenario shows its User Steps; a changed one shows how its User Steps differ from the base branch's last run, when the build gives it that run ([below](#how-a-changed-scenarios-steps-changed)). Left out when the run has no base to compare against;
3. **Failed scenarios**: the failed User Step, the failure, the Near Misses and the evidence directory.

Passing scenarios count in the header only, unless the pull request changed them: the summary stays short to read on the job summary page. Every test's User Steps are in its own `user-steps.txt`, in the evidence directory and the CI artifact.

The demo's run, with the base set to the commit before `UserStepsTest` was added (shortened):

````markdown
### Unified UI Tests · Browserless · 2 passed, 1 failed, 0 skipped

#### Changed in this PR

3 scenarios call code changed since `5951757~1`.

**✘ Changing a person's email updates the grid (EditEmailTest)** · new

```text
1  open People
2  Change the email of Alan Turing
   2.1  click row where Name is “Alan Turing” in grid
   2.2  fill text field “E-mail” with “alan@bletchley.uk”   ✘
```

**✔ The user picks a person in the grid, renames them and saves (EditPersonUnifiedTest)** · changed

```text
1  open People
2  click row where Name is “Alan Turing” in grid
3  fill text field “Name” with “Alan M. Turing”
4  click button “Save”
5  see notification “Saved Alan M. Turing”
6  see row where Name is “Alan M. Turing” with Email “alan@example.com” in grid
```

**✔ Editing a person in nested steps updates the grid (UserStepsTest)** · new

…

#### Failed scenarios

**✘ Changing a person's email updates the grid (EditEmailTest)**

- Failed at: User Step 3: `fill text field “E-mail” with “alan@bletchley.uk”`
- Error: `UnifiedAssertionError: Expected exactly one text field labelled “E-mail”, found none`
- Nearby: text field “Email”, text field “Name”
- Did you mean: `find(UnifiedTextField.class).withLabel("Email")`
- Evidence: `target/surefire-reports/unified-evidence/EditEmailTest/changesTheEmail/`
````

A scenario is *changed* when any of its User Steps or User Tasks is called from a file changed against the base: `git diff` from the merge base of the base ref and `HEAD`, plus uncommitted and untracked files. It is *new* when its test class's file was added, or when its test method wasn't in that file at the base. Without git, or with a base ref git doesn't know, the section is left out and one line is logged.

Skipped scenarios (disabled, aborted, or lacking a Capability) count in the header only, as passing ones do.

### How a changed scenario's steps changed

A scenario marked *changed* calls changed code, but that alone doesn't say what the test does differently. Did it gain a step? Did an agent drop the check that was failing? Give the run the evidence of the base branch's last run, the Base Run, with `-Dvaadin.testing.user-steps.baseline-dir=<directory>`, and each changed scenario shows its User Steps against the Base Run's:

````markdown
**✔ Editing a person in the dialog updates the grid (EditPersonTest)** · changed · User Steps +1 −2 against the base run

```diff
  open People
  click row where Name is “Alan Turing” in grid
- fill text field “Email” with “alan@bletchley.uk”
+ fill text field “E-mail” with “alan@bletchley.uk”
  click button “Save”
- see row where Name is “Alan Turing” in grid
```
````

- Under its title, a changed scenario says why it is listed: `Called from changed files: PeopleTasks.java (editPerson)`, each changed file one of its steps is called from, with the methods calling from it. A file counts as changed as a whole, so a method named there may itself be unchanged; the pull request's diff shows what changed in it. This line is there with or without a Base Run.
- Steps are compared by their words, without their numbers, so an added step doesn't make every later one differ. User Tasks are lines too, and their steps are indented under them.
- A scenario whose steps didn't change takes one line, `same User Steps as the base run`: the change is in code the steps don't show, such as a locator in a shared `…Tasks` class.
- A scenario is matched by its test class and method (and invocation, `[2]`), or by its display name when the method was renamed. One the Base Run doesn't have says `not in the base run` and shows its steps.
- Only the Base Run's tests on the same Testing Tool count. When the directory has none, the summary says `No base run found to compare their User Steps with.` and shows the steps.

The directory is any copy of the base branch's evidence directory, such as its downloaded CI artifact; `user-steps.json` files are found at any depth. [Running in CI](ci-setup.md#github-actions) downloads it in a pull request.

## The HTML report

Next to `summary.md`, the Unified API writes `<evidence dir>/report.html`: the same run in a browser. Open it after a local run (`target/surefire-reports/unified-evidence/report.html`), or from the evidence artifact a CI run uploaded. It is one file with nothing to load from the network, and its links point to files next to it, so keep the evidence directory together when you download or move it.

- **At the top:** the Testing Tool, the counts, when the run started and how long it took. Below, a bar that stays in view: *Show passing*, *Changed only* (when the run has a base to compare against) and a search over scenario names and steps.
- **Changed in this PR:** the scenarios the pull request touches, marked *new* or *changed*, each changed one with the files that put it there. Failed ones are unfolded; passing ones are one line each, unfolded on a click. With a Base Run, a changed scenario shows its User Steps as a diff against it, with the count of steps added and removed by its name; untick *Diff against base run* in the bar to see the full steps instead.
- **Failed scenarios:** where it failed, what was expected and found, the Near Misses and *Did you mean*, the code path, and the failure evidence: the browser's screenshot shown in the page, Browserless's component tree as a link. Then the scenario's User Steps, with the failed one marked.
- **Passing scenarios:** hidden until you tick *Show passing* or search, so a long run doesn't bury what needs attention.

Each User Step shows its number, its text, how long it took and the line that called it. User Tasks fold, and calls outside the Unified API show as dimmed gap lines. Each scenario links its `user-steps.txt` and `user-steps.json`.

## The run as data

Next to `summary.md`, the Unified API writes `<evidence dir>/summary.json`: the same run for a program to read. An agent that has run the tests, or a CI step that posts a comment, reads the verdict from one file instead of parsing Markdown or walking the evidence directory.

```json
{
  "schemaVersion": 1,
  "tool": "browserless",
  "status": "failed",
  "startedAt": "2026-11-16T10:00:01Z",
  "durationMs": 4120,
  "passed": 2,
  "failed": 1,
  "skipped": 0,
  "changedSince": "origin/main",
  "scenarios": [
    {
      "testClass": "com.vaadin.testing.unified.demo.EditEmailTest",
      "testMethod": "changesTheEmail",
      "displayName": "Changing a person's email updates the grid",
      "status": "failed",
      "startedAt": "2026-11-16T10:00:01Z",
      "durationMs": 310,
      "evidenceDir": "EditEmailTest/changesTheEmail/",
      "userSteps": "EditEmailTest/changesTheEmail/user-steps.json",
      "change": "new",
      "failedStep": "fill text field “E-mail” with “alan@bletchley.uk”",
      "failure": { "type": "com.vaadin.testing.unified.UnifiedAssertionError", "…": "…" },
      "evidence": ["com.vaadin.testing.unified.demo.EditEmailTest.changesTheEmail.tree.txt"]
    }
  ]
}
```

- **The verdict:** `status` is `failed` when any scenario failed. The counts are those of the summary's header.
- **Every scenario** is listed, passed or failed, with the directory of its files. Paths are relative to the evidence directory, so they work in a downloaded CI artifact too.
- **A failed scenario** carries the text of the failed User Step, the `failure` object of its `user-steps.json` unchanged (expected, found, Near Misses, *Did you mean*, the code path), and its failure evidence.
- **A scenario the pull request changed** says `new` or `changed`, the changed files it calls from, and how many User Steps were added and removed against the Base Run.

The User Steps themselves stay in each test's `user-steps.json`. The file is versioned (`schemaVersion`); the fields are in the spec, [`summary.json`, schema version 1](../spec/12-readable-tests.md#summaryjson-schema-version-1).

## What the page looked like at each step

Two settings, both off by default, record more than the failure:

- **A snapshot per User Step**: `-Dvaadin.testing.user-steps.snapshots=true`. After every User Step the page is saved into the test's evidence directory, under `steps/`: a screenshot of the browser window (Playwright, Selenium), or the component tree (Browserless). In the HTML report each step gets a *screenshot* link that opens the picture under the step, or a *tree* link. On Selenium each screenshot is a round trip to the browser, so expect slower tests.
- **Playwright's trace**: `-Dvaadin.testing.playwright.trace=on-failure` (or `on`). Playwright records its trace, with a screenshot and the DOM before and after each of its actions, and the Unified API names a group in it after each User Task and User Step. The trace is kept as `trace.zip` in the test's evidence directory, for every test (`on`) or for failed ones (`on-failure`), and the HTML report links it. Open it at [trace.playwright.dev](https://trace.playwright.dev) (the file stays in your browser) or with Playwright's own viewer:

```
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test \
    -Dexec.args="show-trace target/surefire-reports/unified-evidence/EditPersonTest/editsAPerson/trace.zip"
```

A trace is about 1 MB per test, a screenshot about 130 KB per step. With snapshots on, the demo's tests on Playwright took 7 minutes instead of 4 and wrote 542 screenshots (74 MB), so turn them on to look into a test rather than on every CI run. In CI, `trace=on-failure` keeps what you need to see why a test failed and costs little when tests pass. Values passed through `page.secret(..)` are masked in text snapshots, and Browserless trees show a password field's value as `••••`; screenshots and traces show the page as the browser drew it, where password fields show dots.

## The GitHub job summary

A complete workflow, and the setup for other CI servers, is in [Running in CI](ci-setup.md).

In GitHub Actions (`GITHUB_STEP_SUMMARY` is set), `summary.md` is also appended to the job's summary page, so the reviewer reads it on the pull request's checks without downloading anything. In a pull request (`GITHUB_BASE_REF` is set) the base is `origin/<base branch>`, so the checkout needs that branch and its history:

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0                 # history, to find the merge base
- name: Fetch the pull request's base branch
  if: github.event_name == 'pull_request'
  run: git fetch --no-tags origin "+refs/heads/${GITHUB_BASE_REF}:refs/remotes/origin/${GITHUB_BASE_REF}"
```

To keep every test's User Steps and failure evidence, upload the evidence directory as an artifact, also when the build passes:

```yaml
- name: Upload Unified evidence
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: unified-evidence
    path: '**/target/surefire-reports/unified-evidence/'
    if-no-files-found: ignore
```

## Settings

JUnit configuration parameters (`junit-platform.properties`) or system properties:

| Setting | Default |
|---|---|
| `vaadin.testing.user-steps.log` | `failed`: print the User Steps of failed tests to the CI log; `all` or `none` |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` to the GitHub job summary when `GITHUB_STEP_SUMMARY` is set |
| `vaadin.testing.user-steps.changed-since` | in a pull request, `origin/$GITHUB_BASE_REF`; otherwise unset. Any git ref, such as `origin/main`, gives **Changed in this PR** locally |
| `vaadin.testing.user-steps.baseline-dir` | unset; the base branch's evidence directory, such as its downloaded CI artifact: **Changed in this PR** shows how each changed scenario's User Steps differ from it |
| `vaadin.testing.user-steps.snapshots` | `false`; `true`: a snapshot of the page after every User Step, in `steps/` |
| `vaadin.testing.playwright.trace` | `off`; `on` or `on-failure`: keep Playwright's trace, grouped by User Step, as `trace.zip` |
| `vaadin.testing.evidence-dir` | `target/surefire-reports/unified-evidence` |
| `vaadin.testing.timeout` | `10s`: how long a lookup or assertion waits, the `Waited` of a failure |

To see the summary for your branch before you push:

```
mvn test -Dvaadin.testing.user-steps.changed-since=origin/main
```
