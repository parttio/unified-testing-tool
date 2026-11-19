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
- **Chrome on Selenium is started with a logging capability**
  ([#315](https://github.com/parttio/unified-testing-tool/pull/315)), and its console is read after every User
  Step.
- **Adapters:** `AdapterSession` has a new method with a default, `consoleErrors()`. An adapter outside this
  repository keeps compiling.

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
  same code.
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
- **For agents** ([#319](https://github.com/parttio/unified-testing-tool/pull/319)): the `unified-test-api` jar
  ships [`META-INF/unified/api-reference.md`](https://parttio.github.io/unified-testing-tool/api-reference/), every
  Wrapper with its filters, actions, reads and checks, generated from the code. The site has an
  [`llms.txt`](https://parttio.github.io/unified-testing-tool/llms.txt), every page as Markdown and the guide in
  one file.

## New settings

| Setting | Default |
|---|---|
| `vaadin.testing.user-steps.compare-dir` | unset; the evidence directory of a run on another Testing Tool |
| `vaadin.testing.user-steps.server-log` | `warn`; `error`, or `off` |

## Fixed

- `hasSelectedItems(..)` worded its User Step in an order that changed from run to run
  ([#309](https://github.com/parttio/unified-testing-tool/pull/309)).

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
- The `write-unified-ui-tests` skill names the API reference but none of the other additions, and its evaluation
  was not rerun after that change.
