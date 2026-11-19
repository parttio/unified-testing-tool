# Unreleased

What is on `main` and not in a release yet: the changes since [0.1.0-alpha3](0.1.0-alpha3.md). The documentation
site describes `main`, so everything below is in the guide and the specification but not in `0.1.0-alpha3`. This
page becomes the notes of the next alpha.

The theme is the loop of someone, or an agent, writing and fixing tests: the run as data, what a passing test
fails to check, where two Testing Tools disagree, what the application logged, and a repair for a renamed label.

## Changed behaviour

Nothing was removed and no test needs changes, but a run behaves differently in these ways:

- **Vaadin's development tools are off in a `@UnifiedSpringBootTest`'s application**
  ([#317](https://github.com/parttio/unified-testing-tool/pull/317)): `vaadin.devmode.devTools.enabled=false`, as
  the last property source, so anything else that sets it wins. In development mode Vaadin Copilot starts file
  watchers for every browser session and keeps them until the application stops
  ([#318](https://github.com/parttio/unified-testing-tool/issues/318)); a run of some 64 browser tests reached
  the usual Linux limit, and every session after that logged an error. A test that needs the tools sets the
  property to `true`.
- **`user-steps.txt` can have more lines** ([#314](https://github.com/parttio/unified-testing-tool/pull/314)): the
  application's warnings and errors, and the browser console's errors, show under the User Step during which they
  were logged. `vaadin.testing.user-steps.server-log=off` turns the application's lines off.
- **`hasSelectedItems(..)` names its items in the test's order**
  ([#309](https://github.com/parttio/unified-testing-tool/pull/309)). The order used to change from one JVM to
  the next, so the same scenario worded its User Step differently in two runs. A Base Run recorded before this
  can show one changed line for a scenario that uses it, once.
- **Every run asks git for its commit** ([#312](https://github.com/parttio/unified-testing-tool/pull/312)): two
  calls when the run ends, for `summary.json`. Without git the fields are left out.
- **Playwright downloads only the browser a run launches**
  ([#299](https://github.com/parttio/unified-testing-tool/pull/299),
  [#337](https://github.com/parttio/unified-testing-tool/issues/337)). `Playwright.create()` used to download every
  browser it supports, some 1.3 GB, though a run opens one. Now the run installs the browser of
  `vaadin.testing.playwright.browser`, with Playwright's `install` command: for headless Chromium, the default,
  Chromium's headless shell, about 280 MB. With `vaadin.testing.playwright.executable` set it downloads nothing,
  and `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` is no longer needed beside the executable. The environment still has
  the last word: with `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` or `SELENIUM_REMOTE_URL` set, the run installs nothing.
  Playwright decides its own download at the JVM's first `Playwright.create()`, so code that creates a `Playwright`
  before the first test still downloads everything. A CI step that ran `install --with-deps chromium` before the
  tests can run `install-deps chromium` instead, for the system libraries only.
- **Chrome on Selenium is started with a logging capability**
  ([#315](https://github.com/parttio/unified-testing-tool/pull/315)), and its console is read after every User
  Step.
- **A failed test writes one more evidence file**, `<test>.view.txt`, the Findable View below, and the failure's
  `Failure evidence` line names it. Writing it makes about 20 lookups, on failure only. It is written even when
  the screenshot can't be.
- **`Scope` has a new method, `describe()`.** The Unified API implements it for the page and for every Wrapper
  that is a Scope, custom ones included. Only a class of your own that implements `Scope` must add it.
- **Adapters:** `AdapterSession` has new methods with a default, `consoleErrors()` and `title()`. An adapter
  outside this repository keeps compiling; its `hasTitle(..)` fails until it implements `title()`.

## New

- **`summary.json`: the run as data** ([#306](https://github.com/parttio/unified-testing-tool/pull/306)), next to
  `summary.md` and `report.html`: the verdict and the counts, the commit, then every scenario with the paths to
  its files. A failed scenario carries the `failure` object of its `user-steps.json`; a scenario under "Changed
  in this PR" says why. See [The run as data](https://parttio.github.io/unified-testing-tool/guide/reports-and-failures/#the-run-as-data).
- **Scenarios without a check** ([#307](https://github.com/parttio/unified-testing-tool/pull/307)): the run summary
  lists the passing scenarios that end on User Steps nothing checks.
  ```markdown
  #### Scenarios without a check

  - **Saving a person closes the dialog (EditPersonTest)** · no check after: `click button “Save”`
  ```
  A retrying assertion is a check; so is a value the test reads after its last action, which the recorder now
  counts (`readsAfter` in `user-steps.json`). It reports and fails no test.
- **Compared with another Testing Tool** ([#309](https://github.com/parttio/unified-testing-tool/pull/309),
  [#312](https://github.com/parttio/unified-testing-tool/pull/312)): give a run the evidence directory of a run on
  another Testing Tool (`vaadin.testing.user-steps.compare-dir`), and its summary says which scenarios pass on one
  and fail on the other, which take different User Steps and where they part, and whether both runs tested the
  same code. In CI, where each Testing Tool has its own job, the `CompareRuns` command does the same from the
  jobs' evidence; see [Running in CI](https://parttio.github.io/unified-testing-tool/guide/ci-setup/#comparing-the-testing-tools-runs).
  ```
  mvn test -Dvaadin.testing.evidence-dir=target/evidence-browserless
  mvn test -Dvaadin.testing.tool=playwright \
      -Dvaadin.testing.user-steps.compare-dir=target/evidence-browserless
  ```
- **What the application logged, under the User Step** ([#314](https://github.com/parttio/unified-testing-tool/pull/314),
  [#315](https://github.com/parttio/unified-testing-tool/pull/315)): on every Testing Tool, the application's
  warnings and errors with the logged exception; on Playwright and on Selenium with Chrome, the browser console's
  errors.
  ```text
     3  click button “Save” in dialog “Edit person”
        ⚠ server ERROR PersonService: Could not save Alan Turing (IllegalStateException: no id)
  ```
  Heard through Logback, else `java.util.logging`; nothing to add to a project's build.
- **`assertThat(page).hasNoErrors()`** ([#314](https://github.com/parttio/unified-testing-tool/pull/314)): fails a
  scenario that left an error in the application's log or a browser's console, and lists what. It looks once and
  doesn't wait.
- **`ApplyDidYouMean`** ([#313](https://github.com/parttio/unified-testing-tool/pull/313)), a recipe in
  `unified-test-rewrite`: after a failed run, it writes each failure's `Did you mean` into the test.
  `withLabel("Email")` becomes `withLabel("E-mail")`. No model, nothing at test time: a diff to review. See
  [Applying it](https://parttio.github.io/unified-testing-tool/guide/reports-and-failures/#applying-it).
- **The Findable View** ([#204](https://github.com/parttio/unified-testing-tool/issues/204)): `page.describe()`,
  or `describe()` on a dialog or any other Scope, lists what a test can find there, one line each, with the lookup
  that singles it out. It reads the same on every Testing Tool, and a failed test writes the page's view next to
  its evidence. See [What is there to find?](https://parttio.github.io/unified-testing-tool/guide/finding-components/#what-is-there-to-find)
  ```text
  text field “Name” = “Alan Turing”    page.find(UnifiedTextField.class).withLabel("Name")
  button “Archive” (disabled)          page.find(UnifiedButton.class).withText("Archive")
  button “Delete account”              page.find(UnifiedButton.class).withAccessibleName("Delete account")
  ```
- **`assertThat(page).hasTitle(..)`** ([#360](https://github.com/parttio/unified-testing-tool/issues/360)): the
  page's title, from the view's `@PageTitle`, `HasDynamicTitle` or `Page.setTitle(..)`, on every Testing Tool.
  Browserless reads the title the UI last set; a "page has its title" test no longer needs the Escape Hatch.
  ```java
  assertThat(page).isAt(ProfileView.class).hasTitle("Profile");
  ```
- **Wrappers for a chat** ([#357](https://github.com/parttio/unified-testing-tool/issues/357)):
  `UnifiedMessageInput` and `UnifiedMessageList`, for apps built on Vaadin's AI components. `send(text)` types and
  submits a message; the list's `getMessages()` reads each message's user name and text, the text the application
  gave it (the Markdown source), the same on every Testing Tool. Its checks wait for a reply:
  ```java
  page.find(UnifiedMessageInput.class).send("Pancakes");
  assertThat(page.find(UnifiedMessageList.class)).hasMessageCount(3).hasMessageContaining("Pancakes");
  ```
  See [Chat](https://parttio.github.io/unified-testing-tool/guide/components/#chat).
- **For agents** ([#319](https://github.com/parttio/unified-testing-tool/pull/319)): the `unified-test-api` jar
  ships [`META-INF/unified/api-reference.md`](https://parttio.github.io/unified-testing-tool/api-reference/), every
  Wrapper with its filters, actions, reads and checks, generated from the code. The site has an
  [`llms.txt`](https://parttio.github.io/unified-testing-tool/llms.txt), every page as Markdown and the guide in
  one file.
- **Retrying assertions for a grid's columns** ([#359](https://github.com/parttio/unified-testing-tool/issues/359)):
  `assertThat(grid).hasColumn("Cooked times")`, `doesNotHaveColumn(..)` and `hasColumnHeaders(..)`, for a column
  the application adds or removes while the test runs. They replace `page.waitUntil(() ->
  grid.getColumnHeaders().contains(..))`, a gap in the reports, and fail with the headers the grid has. See
  [Columns](https://parttio.github.io/unified-testing-tool/guide/grid/#columns).

## New settings

| Setting | Default |
|---|---|
| `vaadin.testing.user-steps.compare-dir` | unset; the evidence directory of a run on another Testing Tool |
| `vaadin.testing.user-steps.server-log` | `warn`; `error`, or `off` |

## Fixed

- `hasSelectedItems(..)` worded its User Step in an order that changed from run to run
  ([#309](https://github.com/parttio/unified-testing-tool/pull/309)).
- On Browserless, a lookup inside a Scope also found the Scope itself: `dialog.find(UnifiedDialog.class)` found
  the dialog, where a browser finds none ([#333](https://github.com/parttio/unified-testing-tool/issues/333)).
  A grid cell, a column header, and Details and TabSheet content still find the components they hold.
- In a browser, a failure with several matches outside any container named Flow's root as their container,
  `in container (id “ROOT-2521314”)`, an id that changes on every page load and tells none of them apart
  ([#334](https://github.com/parttio/unified-testing-tool/issues/334)). It now reads as on Browserless.
- The Migration Recipes ignored the options a project's `rewrite.yml` set: `addAgentSkill`, the adapters,
  `includePageObjects` ([#353](https://github.com/parttio/unified-testing-tool/issues/353)). OpenRewrite matches
  options to a recipe's constructor parameters by name, and `unified-test-rewrite` was compiled without the names,
  so the recipe ran with its defaults and said nothing. It is now compiled with `-parameters`.

## Known limitations

Those of [0.1.0-alpha3](0.1.0-alpha3.md), and:

- The browser's console is read on Playwright and on Selenium with Chrome. Firefox on Selenium has no browser
  log, so `hasNoErrors()` checks the application's log only there. TestBench has no API for the console
  ([#316](https://github.com/parttio/unified-testing-tool/issues/316)).
- Log lines are given to a User Step by time, not by cause. With tests running in parallel in one JVM, only the
  lines a test's own thread logged are kept.
- An application that logs through a backend other than Logback or `java.util.logging`, such as Log4j 2, gets no
  server lines.
- "Scenarios without a check" takes a value read after the last action as checked; it can't see whether an
  assertion follows.
- The comparison with another Testing Tool doesn't say whether a difference is a documented Limitation
  ([#310](https://github.com/parttio/unified-testing-tool/issues/310)).
- The `write-unified-ui-tests` skill names the API reference and the Findable View but none of the other
  additions, and its evaluation was not rerun after those changes.
- The Findable View lists components grouped by Wrapper type, not in the order of the page.
