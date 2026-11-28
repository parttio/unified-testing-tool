# Field feedback: migrating a TestBench suite (Project C)

Feedback collected after migrating a real app's UI tests from **TestBench with page objects**
to the Unified API. It looks at how easy the tests were to write and how fast they run, and records what went well and
what didn't, to steer the project and to name its advantages.

| | |
|---|---|
| Unified API version | **0.1.0-alpha5**, on Browserless, Playwright and Selenium |
| Project | **Project C** (anonymised): a Spring Boot 4.1 / Vaadin 25.3 app, mostly Lit templates, two Flow `Crud`s |
| Before | 5 TestBench IT classes (17 tests), 16 page objects, a server started by `spring-boot:start` |
| Fixed since | Every finding was closed by [0.1.0-alpha6](../releases/0.1.0-alpha6.md); see [Status of the findings](#status-of-the-findings) |

The project's own migration log and findings list (F1 to F18) stay in that project; the F-numbers are kept so the
findings can be traced back. Statements marked
*(judgment)* are an assessment, not measurements.

## In short

**The Unified API helped:**

- **Less code.** The test code shrank by about a quarter, and every page object is gone.
- **Tests read like the user's actions.** They find fields by their label and say what the user does.
- **The tests start the app themselves.** No server has to be started by the build.
- **More reliable checks.** Checks retry until they pass or time out; no run was flaky.
- **Better reports.** Every test produces a readable list of steps.
- **A fast run with no browser.** Four tests run without a browser; that whole build takes 17 s.

**What held it back on this app:**

- **Browserless covers little.** Only 4 of the 17 tests can run without a browser. Flow `Crud`, the login form, and
  content drawn by renderers in grid cells still need one.
- **No faster in a browser.** On Selenium and Playwright the suite takes about as long as the TestBench suite did.
- **The recipe left most of the work.** It didn't convert the page objects, so every test class was rewritten.
- **Some bugs cost real time.** A Playwright race saved wrong data (F10), and a Browserless lookup crash hid behind a
  timeout (F13).

## Scorecard

| | TestBench (before) | Unified alpha5 (after) | Better? |
|---|---|---|---|
| Test code | 944 lines: 6 classes and 16 page objects | 703 lines: 9 classes and no page objects | **Unified** |
| How tests find components | Methods on page objects, `$(…)`, element ids | Label, text, Wrapper type, and `findTemplate(…)` with `withId` | **Unified** *(judgment)* |
| Starting the app | Maven `spring-boot:start` on port 8080, with a hard-coded `APP_URL` | `@UnifiedSpringBootTest` on a random port | **Unified** |
| Signing in | Through the login form in every test | `@WithUserDetails`, except for the tests that check the login itself | **Unified** |
| Checks | JUnit `assert*`, which runs once and doesn't wait | 50 retrying `assertThat`, 1 JUnit `assert` | **Unified** |
| Tool to run on | Selenium/Chrome only | Browserless, Playwright or Selenium, set with a property | **Unified** |
| Tests that run without a browser | 0 | 4 of 17 | **Unified**, but few tests |
| Tests that run without TestBench | 0 | 15 (Playwright) | **Unified** |
| Whole `mvn verify -Pit` | 43–51 s | Browserless 17–18 s, Playwright 37–38 s, Selenium 40–41 s | **Unified** without a browser; about the same in one |
| Time spent in the test classes | ~20 s, classes run in parallel | ~30 s on Selenium, summed over the classes | **TestBench** |
| Flaky runs | 0 in 2 rounds × 3 tools | Same |
| Failure messages and reports | Step list, `Found` / `Nearby` / `Did you mean`, `summary.md`, `report.html` | **Unified** |
| Looking at the page while writing a test | Browser devtools, then a page object | `page.describe()` (the Findable View) | **Unified**, with gaps (F13, F15) |
| Reaching shadow-DOM HTML in a template | `$` looks inside shadow roots | Not possible; needs an Escape Hatch (F7) | **TestBench** |
| Flow `Crud` | No Wrapper, and doesn't work on Browserless (F9) | **TestBench** |
| Clicking content drawn in a grid cell | `getCell(…)` | A different Escape Hatch per tool | **TestBench** |
| Window size | Resized to 1024×800 | No setting for it (F16) | **TestBench** |
| Checking part of the URL | `getCurrentUrl()` | `hasPath` only compares the whole path; check removed (F11) | **TestBench** |

## What went well

### 1. One test, three tools

Each test is written once and runs on whichever tool `-Dvaadin.testing.tool` names. A tool a test can't use is named
in an annotation: `@RequiresBrowser` on 11 tests, `@RequiresTestingTool(SELENIUM)` on 1.

- **Browserless** runs 4 tests (dashboard counters, dialogs on the main view, field validation, access denied) in about 0.5 s
  once Spring is up; about 8 s of the 17–18 s build is starting the Spring context. A useful check before every commit,
  though it covers only part of the suite.
- **Playwright** runs 15 tests with no TestBench and no TestBench license. Before, every test needed TestBench.

### 2. Less code, and the code reads better

- All 16 page objects are gone: 944 to 703 lines (−24%).
- Lookups use what the user sees:

  ```java
  // TestBench: three page objects are needed to read this
  EditorElement editor = mainPage.getEditor();
  TextFieldElement nameField = editor.getNameField();
  testFieldOverflow(nameField);

  // Unified: says which field it uses
  UnifiedTextField name = editor(page).find(UnifiedTextField.class).withLabel("Name");
  assertRejectsOverflow(name);
  ```

- **Templates:** `findTemplate(View.class)` with `withId("…")` reaches the `@Id` fields of all 7 Lit templates,
  including nested ones, on all three tools. The alpha4 attempt couldn't, and it was the biggest gain for this app.
- **Login and confirm dialog:** the Core Set has Wrappers for these and for the email and password fields, so no custom
  Wrapper was written. The alpha4 attempt needed 3.

### 3. Less setup

- `@UnifiedSpringBootTest` starts the app inside the test on a random port; the build no longer starts and stops it, and
  the base URL is no longer hard-coded.
- The base test class went from a TestBench base class to two annotations and a static block.
- `@WithUserDetails("…")` signs in through the app's own `UserDetailsService`. Only the tests that check the
  login still use the login form.

### 4. Checks that wait

`assertThat(…)` retries until the check passes or times out, and Unified actions wait for the server before they
return. Every `assertNotNull(element)` and hand-written wait was removed. All runs were green: 2 rounds on each of the
3 tools, plus single-class runs.

### 5. Reports and help while writing

- **Reports:** each test writes `user-steps.txt`, a numbered list of what it did in plain words; `page.step("…")` groups
  steps; each run writes `summary.md` and `report.html`. A reviewer can check that a test does what its `@DisplayName`
  says without reading the code *(judgment)*.
- **Failures:** a failing lookup lists what it `Found`, what is `Nearby`, and `Did you mean`. Quicker to act on than a
  `NoSuchElementException`.
- **Findable View:** `page.describe()` prints what can be found and how. It's how each test was written from the view's
  code without going back to the page objects.
- **Style rules:** `UnifiedStyleRules.ALL`, checked with ArchUnit, catches tool-specific code without an annotation or
  branch for its tool. It found 3 violations in the first version.

### 6. Migration tooling

- The recipe changed `pom.xml`, the annotations and the test method signatures, added the two agent skills, and
  produced 30 Migration Markers and only 1 compile error.
- About 47 minutes of agent time from the dry run to the last green run, with no manual step.

## What was incomplete or less efficient

### Writing tests

1. **The recipe did the setup, the rest was by hand (F1, F3).** All test logic lived in 16 page objects. The recipe left
   them alone, didn't mark the statements calling them, and the dry run didn't list them. Its output ran only on
   Selenium, and "resolving the markers" meant rewriting all five test classes. For a suite built on page objects, plan
   for a rewrite.
2. **Flow `Crud` (F9).** No `UnifiedCrud`; on Browserless its buttons have no text and clicking them does nothing. That
   puts 7 of 17 tests in a browser, and every Crud test needs `.visible()` on its button and dialog lookups (F14).
3. **Content drawn by renderers in grid cells.** The Crud's edit button and the main view's cards come from a
   `LitRenderer`, not Flow components. Clicking them took two Escape Hatches (Dramafinder on Playwright, TestBench on
   Selenium) and a route navigation on Browserless: three pieces of code for one click, where TestBench needed
   `getCell(…)`.
4. **Shadow-DOM HTML in templates (F7).** A check counts `<vaadin-board-row>` elements written in a template's HTML.
   `findDescendants` doesn't look inside shadow roots, so that test runs on Selenium only, through a TestBench Escape
   Hatch. TestBench's `$` looked inside shadow roots by default.
5. **Small API gaps that cost checks.** With no prefix match and no way to read the current path, a check that the URL
   gains the item's id was removed (F11). The 1024×800 window size was dropped: there was no setting for it (F16).
6. **The login form needs a browser.** It posts to Spring Security's `/login`, which Browserless can't do, so the 4
   login tests need a browser. Expected, but Browserless can't test the login.
7. **More rounds of exploring the page.** Inside templates, the Findable View showed `@Id` buttons as
   `button (no name)  not unique: 5 alike` instead of suggesting `withId(…)` (F15); it showed nested templates one level
   at a time; on Browserless it left out every link (F13).

### Running tests

1. **No faster in a browser.** Selenium: 40–41 s against 43–51 s for TestBench, the saving coming from no longer starting
   the app separately. Time in the test classes went from ~20 s (TestBench ran classes in parallel) to ~30 s summed.
   Playwright, at 37–38 s, isn't faster than Selenium here. The checks also do more: 2.5× more retrying checks than in
   alpha4, and each editor test waits for the loaded value before typing.
2. **Browserless covers 4 of 17 tests.** Very fast, but for a quarter of the suite. Full coverage still needs a Selenium
   run with a TestBench license for one test, and running several tools in CI multiplies the cost.
3. **Bugs that were hard to diagnose:**
   - **F10 (Playwright):** after an Escape Hatch click, the next `setValue` didn't wait for the server. The old value
     overwrote the typed text and the test saved wrong data. It failed only on Playwright, and disappeared with
     snapshots on.
   - **F13 (Browserless):** `page.find(UnifiedLink.class)` threw on any page with a Flow `Text`. Inside an assertion the
     exception was swallowed: the check failed after the 10 s timeout without showing it.
   - **F6 (reports):** the password was masked wherever its text appeared. With a user name equal to its password, the
     user name and the `@DisplayName` came out as `••••`.
4. **Build profiles that changed meaning (F17).** A native-image profile now tested the JVM app, and a remote-grid
   profile passed settings the Unified Selenium adapter didn't read. Neither was marked or documented as changed.

## Verdict from the project

For this app, the Unified API is better for **writing and reading** the tests: less code, no page objects, tests that
say what the user does, retrying checks, useful reports. It's about the same for **running** them in a browser, and
better only on the small part that runs without one. Its weak spots, Flow `Crud` and renderer-drawn grid content, are a
large part of this app. The app used page objects for everything, so the migration became a rewrite, which took under
an hour with the agent skills.

A workable setup for such a project *(judgment)*: Browserless on every build for fast feedback; Playwright in CI (no
TestBench license, 15 tests); Selenium only for the shadow-DOM check, until F7 is solved or the check is dropped.

## Status of the findings

| Finding | Status |
|---|---|
| F13 Browserless link lookup crash | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md) |
| F10 Playwright race after an Escape Hatch action | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md) |
| F6 Password masking | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md) |
| F1, F3, F4, F5, F18 Recipe ignores page objects | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md) |
| F9 Renderer-drawn cell content, `row.cell(int)` | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md). A `Crud` Wrapper stays out of scope |
| F7, F15 Template shadow DOM and Findable View hints | Findable View hints fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md). Piercing a template's shadow root was declined ([ADR 0012](../adr/0012-lookups-enter-templates-explicitly.md)) |
| F11, F14 Path prefix check, hidden matches | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md) (`hasPathMatching`, hidden matches in `Found`) |
| F16 Window size | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md) (`@Viewport`) |
| F17 Build profiles that changed meaning | Fixed in [0.1.0-alpha6](../releases/0.1.0-alpha6.md) (marked by the recipe) |

## Advantages to highlight

- Page objects aren't needed: tests find components by label, text and type, and the code shrinks.
- The app starts inside the test, and signing in takes an annotation.
- The same test runs on Browserless, Playwright or Selenium; Playwright needs no TestBench license.
- Retrying checks and server-aware actions removed every hand-written wait, with no flaky run.
- `Found` / `Nearby` / `Did you mean` and the Findable View make failures and new tests quick to work out.
- With the agent skills, even a full rewrite of a page-object suite took under an hour.
