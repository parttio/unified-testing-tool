# Reading reports and failures

Every Wrapper action and every retrying assertion is recorded as a **User Step**, worded the way a user would say it: `fill text field “Name” with “Alan M. Turing”`. `page.step(..)` groups User Steps under a **User Task** ([Structuring a scenario](structuring-a-scenario.md)). Recording is always on, on every Testing Tool, and the same steps reach every output below. To review a test with these outputs, see [Reviewing a test](reviewing-tests.md). For a failure's likely cause and fix, see [Troubleshooting](troubleshooting.md). Spec: [12. Readable tests, Reports](../spec/12-readable-tests.md#reports) and [Failures](../spec/12-readable-tests.md#failures).

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
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:changesTheEmail"
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
  Evidence      target/surefire-reports/unified-evidence/EditEmailTest/changesTheEmail/  (failure.tree.txt, failure.view.txt)
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
| `Cause` | the error the step's last attempt threw, such as what this Testing Tool can't do: `UnsupportedOperationException: …` |
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

`Did you mean` appears only when one Near Miss is clearly what the test meant. The rules are tried in this order, and the first that applies decides:

1. **Same letters and digits.** Exactly one component of the same kind has the wanted name when case, spaces and punctuation are ignored: `Previous week` and `Previous Week`, `Email` and `E-mail`. If two do, there is no suggestion.
2. **A few edits away.** Otherwise, exactly one component of the same kind is the fewest edits (characters added, removed or changed, ignoring case) from the wanted name, and that is at most a quarter of the wanted name's length, rounded down, or 2 for a short name. `Previous week` has 13 characters, so up to 3 edits: `Previus week` gets a suggestion, `Go to previous week` (6 edits) does not. Two components equally close get none.
3. **Another kind, the exact name.** Otherwise, exactly one component of another kind has the exact name: a combo box labelled `Role` when the test looked for a text field. When rule 2 and rule 3 both find one, neither is clearly best, and there is no suggestion.

A component of the same kind with exactly the wanted name rules out every suggestion: it is there, and another filter or the scope left it out. A name that only contains the wanted one, or is contained in it, moves up in `Nearby` but is never suggested. So no `Did you mean` means that no Near Miss was close enough by these rules, not that one was missed, and [`ApplyDidYouMean`](#applying-it) has nothing to apply to that failure: read `Nearby`, or the [Findable View](#the-findable-view), and write the lookup yourself.

It is written as the lookup to paste, from `find(..)` on. A dialog whose title the test misspelled:

```
  Failed at     User Step 3 of 3: click button “Close” in dialog “Export Report”
  Expected      exactly one dialog with header title “Export Report”
  Found         none
  Nearby        dialog “Export report”
  Did you mean  find(UnifiedDialog.class).withHeaderTitle("Export report")
```

### Applying it

When a label changed in the application and the tests that used it fail, each failure already says what to write instead. The `ApplyDidYouMean` recipe writes it for you. Run the tests, then, in the same directory:

```
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
    -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:<version> \
    -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.ApplyDidYouMean
```

```diff
-        page.find(UnifiedTextField.class).withLabel("Email").setValue("alice@example.com");
+        page.find(UnifiedTextField.class).withLabel("E-mail").setValue("alice@example.com");
```

It reads the failures the run left in the evidence directories (`user-steps.json`, every one under the directory the build runs in) and changes the string in the test's code. No model is involved, and nothing runs when the tests do: the result is a diff to read and commit, or to revert.

- **What it changes:** the one string in which the failed lookup and its `Did you mean` differ, where the code has it as a string literal: a filter's value after `find(..)` of the same Wrapper, or the item of a `selectItem(..)` or `selectTab(..)`. Every such call in the files the failure's code path names, and in the scenario's test class.
- **What it leaves:** a suggestion of another kind of component, a value kept in a constant or passed as a parameter, and a lookup written in a file the failure doesn't name. Those failures stay for you, or for an agent.
- **It fixes the test, not the application.** If the label was changed by mistake, the test was right: revert the diff and fix the view. With `-Drewrite.exportDatatables=true` the recipe lists every value it replaced, with the scenario that asked for it, in `target/rewrite/datatables/`, so "Email → E-mail in forty lookups" reads as one decision.

To read another directory, declare the recipe with its `evidenceDir` option in a `rewrite.yml`, as for the [Migration Recipes](../migrating-from-testbench.md).

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

What the Testing Tool can't do. It fails at the first attempt, since waiting won't change it, and its `Cause` line says why. Here it's a lookup inside a cell that a LitRenderer draws, on Browserless:

```
  Failed at     User Step 2 of 2: click edit pencil in cell at index 1 in row where Name is “Bob” in grid (test id “editable”)
  Expected      exactly one cell at index 1 in row where Name is “Bob” in grid (test id “editable”)
  Found         none
  Cause         UnsupportedOperationException: The cell at index 1 is drawn by a LitRenderer, whose markup exists only in a browser, so Browserless can't search inside it. Annotate the test with @RequiresBrowser to skip it here; to run a renderer function on Browserless, use the Escape Hatch: GridTester.invokeLitRendererFunction.
  Tool          browserless
```

Any other error a step's attempts throw is retried until the timeout, and `Cause` names the last one.

### The Findable View

Next to the screenshot or the component tree, a failed test writes `failure.view.txt`, on every Testing Tool: what the page held when the test failed, as lookups. The failure's `Evidence` line names it. When the failed step was about a grid row or a chat message, the grid's columns and first rows, or the list's first messages, follow, each with the lookup that finds it, as `grid.describe()` lists them.

```
Findable view of “/checkout” on playwright (chromium) at Viewport 1280 × 720: 6 components
text field “Street”  page.find(UnifiedContainer.class).withTestId("billing").find(UnifiedTextField.class).withLabel("Street")
text field “City”    page.find(UnifiedContainer.class).withTestId("billing").find(UnifiedTextField.class).withLabel("City")
text field “Street”  page.find(UnifiedContainer.class).withId("shipping").find(UnifiedTextField.class).withLabel("Street")
text field “City”    page.find(UnifiedContainer.class).withId("shipping").find(UnifiedTextField.class).withLabel("City")
button “Save”        page.find(UnifiedButton.class).withText("Save")
button “Cancel”      page.find(UnifiedButton.class).withText("Cancel")
```

In a browser, the header names the Viewport, and a line says when a user can't get to its component as it shows: `(clipped)` when a box with hidden overflow cuts it off, `(behind the AppLayout drawer)` or `(behind button “Menu”)` when something covers it, `(hidden in the AppLayout drawer)` or `(hidden in the MenuBar overflow)` when Vaadin tucked it away. A component a user scrolls to says nothing. A container's `describe()` doesn't say this, only the page's. An action on a component something covers or clips fails with that in `Found`, the same on Playwright and Selenium: `Expected a reachable button with text “Save”`, `Found behind the AppLayout drawer`.

A kind of component whose lookup threw is not left out: the view ends with `Not listed: link (UnsupportedOperationException: …)`.

When `Did you mean` has nothing to say, look for the component here. The same view is `page.describe()` in a test ([Finding components](finding-components.md#what-is-there-to-find)).

### In code

The parts are getters on `UnifiedAssertionError`, for a test that checks a failure with `assertThrows`: `getSummary()`, `getScenario()`, `getFailedStep()`, `getStepChain()`, `getExpected()`, `getFound()`, `getNearMisses()`, `getSuggestion()`, `getWaited()`, `getTool()`, `getCodePath()`, `getEvidenceDir()`.

## The CI log

A failed test prints its User Steps to stdout, so the CI log shows them next to the failure. The failed step is marked `✘`, followed by the failure in one line, its `Did you mean` when it has one, and where it happened:

```
✘ EditEmailTest › Changing a person's email updates the grid   browserless · 11.4 s
   1  open People
   2  Change the email of Alan Turing
      2.1  click row where Name is “Alan Turing” in grid
      2.2  fill text field “E-mail” with “alan@bletchley.uk”   ✘
   ✘ UnifiedAssertionError: Expected exactly one text field labelled “E-mail”, found none
     Did you mean  find(UnifiedTextField.class).withLabel("Email")
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

Next to them, a failed test leaves its failure evidence: `failure.png`, a screenshot, on Playwright and Selenium, or `failure.tree.txt`, the component tree as text, on Browserless; and `failure.view.txt` on every Testing Tool. The failure's `Evidence` line names the directory and these files, and `user-steps.json` lists them as `failure.evidence`. The evidence directory is `target/surefire-reports/unified-evidence` by default, so CI can upload it with the test reports.

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

An Escape Hatch gap counts each `unwrap(..)`, not each use of what it returned. Work done later through a kept `Page` or `WebDriver` isn't seen, so the User Steps after it may look empty, though the test did work there, and the run summary may count the scenario as [without a check](#scenarios-without-a-check):

```java
Page playwright = page.unwrap(Page.class);    // … 1 call outside the Unified API (Escape Hatch)
playwright.getByText("Alan Turing").click();  // not seen
playwright.getByText("Save").click();         // not seen
```

To have each use show, unwrap where the code uses it:

```java
page.unwrap(Page.class).getByText("Alan Turing").click();   // … 2 calls outside the Unified API (Escape Hatch)
page.unwrap(Page.class).getByText("Save").click();
```

A test migrated by the [Dramafinder recipe](../migrating-from-dramafinder.md#what-it-leaves-for-you) keeps one `Page`, like the first example; that is fine as a first step ([Keeping an unwrapped object](tool-specific.md#keeping-an-unwrapped-object)).

### What the application logged

A click that makes the server throw often shows the user nothing, or a generic error. The reason is in the application's log, and the Unified API puts it where the click is:

```
✘ EditPersonTest › Saving a person shows a confirmation   browserless · 0.4 s
   1  open People
   2  click row where Name is “Alan Turing” in grid
   3  click button “Save” in dialog “Edit person”
      ⚠ server ERROR PersonService: Could not save Alan Turing (IllegalStateException: no id)
   4  see notification “Saved Alan Turing”   ✘
```

- **The application's warnings and errors** show under the User Step during which they were logged, with the exception that was logged with them. It works on every Testing Tool, since the application runs in the test's JVM. `-Dvaadin.testing.user-steps.server-log=error` keeps errors only, `off` none. There is nothing to add to your build: the lines are heard through Logback when your application logs with it, as a Spring Boot application does by default, and through `java.util.logging` otherwise. An application that logs through another backend, such as Log4j 2, gets no server lines; everything else works the same.
- **The browser's console errors** show the same way on Playwright, and on Selenium with Chrome: `⚠ console ERROR: Failed to load resource: the server responded with a status of 404 () (http://localhost:8080/missing.css)`. A missing `/favicon.ico`, which every browser asks for by itself, is left out.
- **By time, not by cause:** a line a background thread logs shows under the step that was running then. A line logged between two steps shows under the first.
- **`assertThat(page).hasNoErrors()`** makes a scenario fail when anything was logged as an error so far, and lists what. Put it at the end of a scenario that should leave no error behind, after the check that shows the last action is over: it looks once and doesn't wait. Without it, a logged error is shown and fails nothing.

The lines are also in `user-steps.json` (`logs`) and under each step in the HTML report.

## The run summary

When the tests have run, the Unified API writes `<evidence dir>/summary.md`: the run's scenarios in the words of the reports, for the reviewer of a pull request. In order:

1. a header with the Testing Tool and the counts;
2. **Changed in this PR**: the scenarios that call code changed against the base branch, marked *new* or *changed*. A change in a shared `…Tasks` class marks every scenario that uses it. A new scenario shows its User Steps; a changed one shows how its User Steps differ from the base branch's last run, when the build gives it that run ([below](#how-a-changed-scenarios-steps-changed)). Left out when the run has no base to compare against;
3. **Failed scenarios**: the failed User Step, the failure, the Near Misses and the evidence directory;
4. **Compared with another Testing Tool**: the scenarios that end differently, or take different User Steps, on another Testing Tool, when the build gives it that run ([below](#compared-with-another-testing-tool));
5. **Scenarios without a check**: the passing scenarios that end on User Steps nothing checks ([below](#scenarios-without-a-check)).

Passing scenarios count in the header only, unless the pull request changed them or they have no check: the summary stays short to read on the job summary page. Every test's User Steps are in its own `user-steps.txt`, in the evidence directory and the CI artifact.

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

### Compared with another Testing Tool

A Unified UI Test runs on Browserless in milliseconds, and the fast run is the one a developer or an agent repeats. When the same test then fails in a browser, the first question is where the two runs part. Give a run the evidence directory of a run on another Testing Tool, and its summary answers that:

```
mvn test -Dvaadin.testing.evidence-dir=target/evidence-browserless
mvn test -Dvaadin.testing.tool=playwright \
    -Dvaadin.testing.user-steps.compare-dir=target/evidence-browserless
```

```markdown
#### Compared with Browserless

118 scenarios ran on both Testing Tools: 1 ends differently, 1 takes different User Steps. 13 ran on Playwright only, 12 on Browserless only.

Both runs are of commit `3e74f89`.

**Saving a person shows a confirmation (EditPersonTest)** · failed on Playwright, passed on Browserless

- On Playwright: `UnifiedAssertionError: Expected notification to have text “Saved Alan”, found “Saved”`

**The profile is shown after login (ProfileTest)** · passed on both, different User Steps

- Parts at step 3: `click button “Log in”` on Playwright, `see text field “Name”` on Browserless
```

- **Ends differently:** the scenario passed on one Testing Tool and failed on the other. These come first, with the error of the side that failed.
- **Different User Steps:** both ended alike, but didn't do the same. *Parts at step* is the first line the two runs don't share, counted over the scenario's steps, User Tasks and gap lines from the top. A test that branches on `page.hasRealBrowser()` shows up here, and so does a Wrapper that words a step differently on one Testing Tool.
- **Ran on one only:** tests a Capability skipped on the other Testing Tool (`@RequiresBrowser`, `@RequiresTestingTool`). They are counted, not listed.

- **The same code?** A difference only says something about the Testing Tools when both runs tested the same code. Each run records its commit, and a fingerprint of the uncommitted changes to tracked files, and the section says whether they match: `Both runs are of commit …`, or **Not the same code** with what each run is of. Change a file between the two runs and the second one tells you. Without git, or with evidence from an earlier version of the Unified API, it says that it can't tell. Untracked files are not seen.

In CI, where each Testing Tool has its own job, a job after them does the same from their uploaded evidence ([Running in CI](ci-setup.md#comparing-the-testing-tools-runs)).

The first run needs an evidence directory of its own (`vaadin.testing.evidence-dir`), or the second run overwrites it. The section reports; it fails no test. It doesn't say whether a difference is one of the documented ones ([Tool-specific code](tool-specific.md)): read the step it names. `report.html` has the same section, and `summary.json` marks each such scenario with `differsOn`.

### Scenarios without a check

A test that fills a form, clicks *Save* and stops there passes whether or not the save works. The run summary lists the passing scenarios that end this way, so the author, or the reviewer of a test an agent wrote, sees them without reading each test:

```markdown
#### Scenarios without a check

2 passing scenarios end on User Steps that nothing checks: no `see` step follows them, and the test reads no value after them.

- **Opening the dashboard shows the charts (DashboardTest)** · no check
- **Saving a person closes the dialog (EditPersonTest)** · no check after: `click button “Save”`
```

- **A check** is a retrying assertion, `assertThat(..)` from `UnifiedAssertions`, which shows as a `see` or `see no` User Step. The steps after a scenario's last check are the ones listed.
- **Reading a value counts too.** `assertEquals("Alan", name.getValue())` records no User Step, but the Unified API counts that the test read a value after its last action, and takes the test to assert on it. Such a scenario is not listed. The retrying form, `assertThat(name).hasValue("Alan")`, is still the better one: it waits for the value on every Testing Tool, and its failure names the User Step ([Assertions and waiting](assertions-and-waiting.md)).
- **A check outside the UI counts when it is a Backend Step.** A test that ends by checking the database, a file or a mail in plain Java looks unchecked, since the Unified API sees none of it. Put that check in `page.backendStep("order is stored", () -> ..)`: it shows as `backend: order is stored` and counts as a check ([Checking outside the UI](assertions-and-waiting.md#checking-outside-the-ui)).
- **Not listed either:** a scenario that ends on `page.waitUntil(..)` or an Escape Hatch call, since the check may be in there, and a passing scenario with a failed User Step, since the test expected that failure (`assertThrows`).
- **Escape Hatch scenarios are counted, not listed.** After the Escape Hatch call the Unified API sees nothing: a migrated test that reads TestBench elements and checks them with `assertEquals` or AssertJ looks unchecked. So a scenario without a check that used an Escape Hatch anywhere is counted on one line after the list, with the first three names:

    ```markdown
    13 more used the Escape Hatch and end on User Steps the report sees no check for: Edit an order, Add a product, Log out, … Their checks may be assertions the report can't see: move checks of the UI to `assertThat(..)`, and declare the others with `page.backendStep(..)`.
    ```

    The list keeps the scenarios that really check nothing, and the count shrinks as their checks move to `assertThat(..)` or into Backend Steps. In a migrated suite, resolving the Migration Markers does the same ([Migrating from TestBench](../migrating-from-testbench.md#what-it-leaves-for-you)). When only Escape Hatch scenarios are unchecked, the section is that line alone.

The section reports; it fails no test. It can't tell whether a check is the right one, only that the last steps have none. In `report.html` these scenarios have a section of their own and a *no check* badge, the Escape Hatch ones after their count line with an *Escape Hatch* badge, and `summary.json` marks them with `unchecked`, and `escapeHatch` true for the Escape Hatch ones.

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
      "evidence": ["EditEmailTest/changesTheEmail/failure.tree.txt", "EditEmailTest/changesTheEmail/failure.view.txt"]
    }
  ]
}
```

- **The verdict:** `status` is `failed` when any scenario failed. The counts are those of the summary's header.
- **The code:** `commit` is the commit the run tested, and `uncommittedChanges` a fingerprint of the uncommitted changes to tracked files, when there are any. Both are left out without git.
- **Every scenario** is listed, passed or failed, with the directory of its files. Paths are relative to the evidence directory, so they work in a downloaded CI artifact too.
- **A failed scenario** carries the text of the failed User Step, the `failure` object of its `user-steps.json` unchanged (expected, found, Near Misses, *Did you mean*, the code path), and its failure evidence.
- **A scenario that differs on another Testing Tool** says `differsOn`: the tool, its status there, where the User Steps part, and the failure from there.
- **A passing scenario without a check** says `unchecked` (`all` or `end`) and lists the User Steps nothing checks; `escapeHatch` says whether it used an Escape Hatch, so the summary counts it apart.
- **A scenario the pull request changed** says `new` or `changed`, the changed files it calls from, and how many User Steps were added and removed against the Base Run.

The User Steps themselves stay in each test's `user-steps.json`. The file is versioned (`schemaVersion`); the fields are in the spec, [`summary.json`, schema version 1](../spec/12-readable-tests.md#summaryjson-schema-version-1).

## What the page looked like at each step

Two settings, both off by default, record more than the failure:

- **A snapshot per User Step**: `-Dvaadin.testing.user-steps.snapshots=true`. After every User Step the page is saved into the test's evidence directory, under `steps/`: a screenshot of the browser window (Playwright, Selenium), or the component tree (Browserless). In the HTML report each step gets a *screenshot* link, which opens the picture in a viewer with the step's component spotlighted (← → for the other steps, Esc to close, the *highlight step targets* switch or `h` to turn the spotlight off), or a *tree* link, which opens the tree under the step with the component marked. A check marks the component it checked. On Selenium each screenshot is a round trip to the browser, so expect slower tests.
- **Playwright's trace**: `-Dvaadin.testing.playwright.trace=on-failure` (or `on`). Playwright records its trace, with a screenshot and the DOM before and after each of its actions, and the Unified API names a group in it after each User Task and User Step. The trace is kept as `trace.zip` in the test's evidence directory, for every test (`on`) or for failed ones (`on-failure`), and the HTML report links it. Open it at [trace.playwright.dev](https://trace.playwright.dev) (the file stays in your browser) or with Playwright's own viewer:

```
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test \
    -Dexec.args="show-trace target/surefire-reports/unified-evidence/EditPersonTest/editsAPerson/trace.zip"
```

A trace is about 1 MB per test, a screenshot about 130 KB per step. With snapshots on, the demo's tests on Playwright took 7 minutes instead of 4 and wrote 542 screenshots (74 MB), so turn them on to look into a test rather than on every CI run. In CI, `trace=on-failure` keeps what you need to see why a test failed, but it records the trace of every test and only drops it when the test passes: on this project's demo it made the Playwright run about a fifth slower. To pay only when a test fails, run without the trace and, if tests failed, run those again with it, as this repository's CI does (`.github/workflows/tests.yml`). Values passed through `page.secret(..)` are masked in text snapshots, and Browserless trees show a password field's value as `••••`; screenshots and traces show the page as the browser drew it, where password fields show dots.

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
    path: '**/target/*-reports/unified-evidence/'
    if-no-files-found: ignore
```

## Settings

JUnit configuration parameters (`junit-platform.properties`) or system properties:

| Setting | Default |
|---|---|
| `vaadin.testing.user-steps.log` | `failed`: print the User Steps of failed tests to the CI log; `all` or `none` |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` to the GitHub job summary when `GITHUB_STEP_SUMMARY` is set |
| `vaadin.testing.user-steps.changed-since` | in a pull request, `origin/$GITHUB_BASE_REF`; otherwise unset. Any git ref, such as `origin/main`, gives **Changed in this PR** locally |
| `vaadin.testing.user-steps.server-log` | `warn`: the application's warnings and errors under the User Step during which they were logged ([above](#what-the-application-logged)); `error`, or `off` |
| `vaadin.testing.user-steps.baseline-dir` | unset; the base branch's evidence directory, such as its downloaded CI artifact: **Changed in this PR** shows how each changed scenario's User Steps differ from it |
| `vaadin.testing.user-steps.compare-dir` | unset; the evidence directory of a run of the same tests on another Testing Tool: the run summary says which scenarios differ ([above](#compared-with-another-testing-tool)) |
| `vaadin.testing.user-steps.snapshots` | `false`; `true`: a snapshot of the page after every User Step, in `steps/` |
| `vaadin.testing.playwright.trace` | `off`; `on` or `on-failure`: keep Playwright's trace, grouped by User Step, as `trace.zip` |
| `vaadin.testing.evidence-dir` | `target/surefire-reports/unified-evidence` |
| `vaadin.testing.timeout` | `10s`: how long a lookup or assertion waits, the `Waited` of a failure |

To see the summary for your branch before you push:

```
mvn test -Dvaadin.testing.user-steps.changed-since=origin/main
```
