# Field feedback: migrating a plain Playwright suite (Project A)

Feedback collected after migrating a real end-to-end suite from **plain Playwright for Java** to the Unified API.
It records what went well and what didn't, to steer the project and to name its advantages.

| | |
|---|---|
| Unified API version | **0.1.0-alpha6** (Playwright Testing Tool; Browserless for one spike test) |
| Project | **Project A** (anonymised): a non-Spring Vaadin app with a Vaadin 8-style UI, Keycloak sign-in, several app copies for tests |
| Suite size | 62 workflow classes, about 12,500 lines of test source |
| Collected | 2026-11 |
| Feedback issue | [#677](https://github.com/parttio/unified-testing-tool/issues/677) |

The findings are filed in this repository: the feedback issue [#677](https://github.com/parttio/unified-testing-tool/issues/677) lists them as **I-01 to I-17**, with one
ticket each from I-02 ([#678](https://github.com/parttio/unified-testing-tool/issues/678)) to I-17 ([#693](https://github.com/parttio/unified-testing-tool/issues/693)). I-01 was resolved without a ticket. Each I-number below links
to its ticket. The project's own migration logs stay in that project.

- **Measured** means numbers from runs on the project's machine.
- **Judgement** marks an assessment from doing the work. Writing time wasn't compared against plain Playwright: nobody
  wrote the same new test twice.

## In short

| | Verdict |
|---|---|
| Running the suite | **Same speed.** 11 min 20 s before, 11 min 21 s after, for the same 10 workflows (measured). |
| Failure evidence and reports | **Much better.** A User Steps tree named after the test catalogue's steps, failure screenshot, the view as text, a run report, CI job summary. |
| Suite code to maintain | **A little less.** About 200 lines of browser lifecycle code removed; the source went from 12,516 to 12,410 lines. |
| Writing tests for this app | **Not easier yet.** Most locators stay raw Playwright behind the Escape Hatch. The Wrappers don't cover the app's Vaadin 8-style UI, and some Wrapper behaviours cost time to discover. |
| Parallel runs | **Worse.** Parallel test threads are no longer possible ([I-10](https://github.com/parttio/unified-testing-tool/issues/686)). The suite moved to one JVM per app copy, which took a rework of the copy pool. |
| Running without a browser | **New possibility, but not free.** The same spike steps pass on Browserless, 2.5 times faster per scenario. They need four tool-specific branches and setup the tool has no hook for. |
| Migration effort | **All by hand.** No recipe applies to a plain Playwright suite ([I-04](https://github.com/parttio/unified-testing-tool/issues/680)). A script of the project's own converted the 62 classes; the helpers were done by hand. |

## What went well

### 1. Evidence and reporting (the main gain)

- **Before:** a failing step gave a JUnit stack trace and a screenshot. The suite had its own code to take screenshots
  and to skip the remaining steps after a failure.
- **After:**
  - each workflow has a **User Steps** tree whose entries are the catalogue's step names;
  - a failure adds `failure.png` and `failure.view.txt`, a text view of the page's components;
  - the run gets `report.html` and `summary.md`, which CI appends to the job summary.
- Actions through Wrappers are named in plain words, for example `click button "Save" in the 2nd dialog` or
  `click button "…" in div (class name "valo-menu")`.
- Passwords are masked in every report. The suite reads them at run time and never writes them down.
- On Browserless, a server exception shows up inside the User Steps (`⚠ server ERROR …`). During the spike that pointed
  straight at each missing backend service.

### 2. Lifecycle handled by the tool

- Browser, page, viewport, timeouts and failure screenshots are now the tool's job.
- The suite's base test class and browser session class (about 200 lines) were removed; its JUnit extension lost its
  screenshot and skip code.
- Former suite code became configuration: `vaadin.testing.timeout`, `vaadin.testing.viewport`,
  `vaadin.testing.playwright.headless`.

### 3. Wrappers for the common actions

- Menu, buttons, text fields, combo boxes, dialogs and tabs go through Wrappers in a shared helper most workflows call.
- Wrappers wait automatically.
- They read like the user's view (`withLabel("Category").selectItem("…")`) instead of selectors such as
  `vaadin-combo-box input`.

### 4. One test, two tools (spike)

- One test's six steps are the same code on Browserless and Playwright. Both pass.
- Scenario time: 12–13 s on Browserless against 22–31 s on Playwright (measured).
- Plain Playwright offers nothing like this.

## What was incomplete

### Wrappers don't cover the app's UI

The shared helper still uses raw Playwright selectors 277 times, against 21 Wrapper calls. The workflows use raw
selectors 217 times, the same number as before the migration. What stays raw, and why:

- **Grids:** rows are read by their text, with Vaadin 8-style column renderers.
- **Date fields:** the app's own custom fields. A `CustomField`'s label doesn't find the field inside it ([I-17](https://github.com/parttio/unified-testing-tool/issues/693)).
- **Notifications:** several cards shown at once and read without waiting. On Browserless, `getText()` throws ([I-14](https://github.com/parttio/unified-testing-tool/issues/690)).
- **Uploads, downloads and Keycloak's login page:** Keycloak's page isn't a Vaadin page.
- **Waiting for a dialog to close:** there's no non-waiting presence check ([I-11](https://github.com/parttio/unified-testing-tool/issues/687)).

Because everything raw goes through `unwrap(Page.class)`:

- the reports don't describe those actions;
- the Escape Hatch is counted once per `unwrap`, not per use, so steps look empty in the report ([I-09](https://github.com/parttio/unified-testing-tool/issues/685));
- the run summary calls 61 of 62 passing workflows "scenarios without a check" ([I-12](https://github.com/parttio/unified-testing-tool/issues/688)).

### Not built for a non-Spring, multi-copy setup

| Gap | What the suite does instead |
|---|---|
| Base URL is set once per run ([I-02](https://github.com/parttio/unified-testing-tool/issues/678)) | Pages opened by absolute URL; path checks not used |
| Browser context options can't be set ([I-03](https://github.com/parttio/unified-testing-tool/issues/679)) | Settings, plus listeners on the page behind the Escape Hatch |
| `openAsUser` needs Spring Security ([I-05](https://github.com/parttio/unified-testing-tool/issues/681)) | A second user is a raw `BrowserContext`, without User Steps or auto-wait |
| `hasNoErrors()` has no HTTP check and no ignore list ([I-06](https://github.com/parttio/unified-testing-tool/issues/682)) | The suite keeps its own browser-problem listeners |
| A user name equal to its password is masked everywhere ([I-07](https://github.com/parttio/unified-testing-tool/issues/683)) | Accepted: reports show `••••` instead of the user |
| The failure pointer names JUnit's frame ([I-08](https://github.com/parttio/unified-testing-tool/issues/684)) | Accepted |

### Browserless needs what the API doesn't offer yet

- No hook for signing in or for the app's servlet-bound startup.
- The backend and data differ from Playwright's.
- The adapter reads text fields, HTML text and grid cells differently ([I-13](https://github.com/parttio/unified-testing-tool/issues/689), [I-15](https://github.com/parttio/unified-testing-tool/issues/691), [I-16](https://github.com/parttio/unified-testing-tool/issues/692)).

Result: four `page.hasRealBrowser()` branches in one test.

## What was less efficient

### To write (judgement)

- **The natural presence check is wrong.** `isVisible()` or `isOpen()` on a missing element waits 30 s and fails
  instead of returning false ([I-11](https://github.com/parttio/unified-testing-tool/issues/687)). That broke 10 of 62 workflows when the helpers moved to Wrappers. Plain
  Playwright's `Locator.isVisible()` returns false at once.
- **Two ways to do everything.** Tests mix Wrappers and raw Playwright; each new helper raises the question of whether
  a Wrapper covers it. For this app the answer is often no.
- **Browserless takes finding out.** Each failure needed its own lookup or workaround ([I-13](https://github.com/parttio/unified-testing-tool/issues/689) to [I-17](https://github.com/parttio/unified-testing-tool/issues/693)). Some are silent: a
  field shows "Kilo" while the app reads `null`.
- **Test structure is imposed.** Each `@Test` gets a fresh, signed-out page. One-method-per-step classes sharing a
  logged-in browser became one `@Test` per workflow: JUnit reports 10 tests where it reported 53 steps. The User Steps
  tree replaces the per-step view.

### To run (measured)

- **Same speed on Playwright:** 11 min 20 s before, 11 min 21 s after (10 workflows, two copies). All 62 workflows took
  1 h 23 min with two copies. No measurable overhead from the tool.
- **A missing element costs 30 s.** Every read of an absent element waits the full timeout ([I-11](https://github.com/parttio/unified-testing-tool/issues/687)). The suite avoids it
  with `all().isEmpty()`.
- **Parallelism is coarser.** One JVM per app copy, classes one at a time in each ([I-10](https://github.com/parttio/unified-testing-tool/issues/686)). The old suite ran classes in
  threads. Run time didn't change at two copies, but each copy now costs a JVM, and class ordering only takes effect
  with one fork.
- **Browserless is faster per scenario, less so per test:**

  | | Playwright, warm | Browserless |
  |---|---|---|
  | Scenario steps | 30.9 s | 12.2–12.8 s |
  | Whole test | 31 s | 26–27 s (about 14 s starts the backend in the JVM) |
  | Data before each class | 58–62 s copy reset | Created by the test |

### Build and dependencies

- Required an upgrade from JUnit 5.14 to JUnit 6.
- Required `vaadin-core` on the test classpath although the app runs remotely.
- Required Maven 3.9 to build the tool from source (the project's wrapper was 3.6.3).
- alpha6 was on GitHub about seven hours before Maven Central (I-01); resolved since.
- Migration recipes cover TestBench, Browserless and Dramafinder, not plain Playwright ([I-04](https://github.com/parttio/unified-testing-tool/issues/680)). The dry run marked no
  class, not even as "skipped".

## Verdict from the project

**Keep the migration for its evidence and reports;** those alone justify it, at no run-time cost. For writing tests it
isn't more efficient than plain Playwright yet, for an app like this one.

## Improvements asked for, in order of value

1. **A non-waiting presence check**, and `isVisible()` returning false for a missing element ([I-11](https://github.com/parttio/unified-testing-tool/issues/687)).
2. **Thread-safe Playwright per test**, so suites can run classes in parallel threads again ([I-10](https://github.com/parttio/unified-testing-tool/issues/686)).
3. **A sign-in hook and a per-class base URL** for non-Spring apps and several app copies ([I-05](https://github.com/parttio/unified-testing-tool/issues/681), [I-02](https://github.com/parttio/unified-testing-tool/issues/678)).
4. **Wrappers or lookups that reach more of the UI:** labelled `CustomField`s, HTML text, grid cells from
   `LitRenderer`, notifications ([I-14](https://github.com/parttio/unified-testing-tool/issues/690) to [I-17](https://github.com/parttio/unified-testing-tool/issues/693)).
5. **Per-tool setup hooks and identical adapter behaviour**, so one test runs on both tools without branches.
6. **Counting non-UI checks and Escape Hatch use** in the reports, so migrated suites aren't flagged as having no checks
   ([I-09](https://github.com/parttio/unified-testing-tool/issues/685), [I-12](https://github.com/parttio/unified-testing-tool/issues/688)).

Also raised: a migration path for plain Playwright suites ([I-04](https://github.com/parttio/unified-testing-tool/issues/680)), context options ([I-03](https://github.com/parttio/unified-testing-tool/issues/679)), an HTTP check and ignore
list for `hasNoErrors()` ([I-06](https://github.com/parttio/unified-testing-tool/issues/682)), masking when a user name equals its password ([I-07](https://github.com/parttio/unified-testing-tool/issues/683)), and the failure pointer ([I-08](https://github.com/parttio/unified-testing-tool/issues/684)).

## Advantages to highlight

- Readable, step-named evidence on failure, with no suite code to produce it.
- No run-time cost on Playwright compared with plain Playwright.
- Less lifecycle code to own; settings move to configuration.
- Secrets masked in every report by default.
- The same test can run without a browser, much faster per scenario, once setup hooks exist.
