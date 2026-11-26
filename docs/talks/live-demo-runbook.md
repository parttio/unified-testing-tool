# Live demo runbook: Unified UI Tests in 30 minutes

The live part of a 30-minute talk to Vaadin colleagues who know TestBench and Browserless. The goal is buy-in: a
pilot next quarter, then Unified UI Tests as the recommended way in the Vaadin docs.

| Minutes | Part | How |
|---|---|---|
| 0–5 | Slides: the problem, the idea, the headline number | Deck |
| 5–12 | [1. One test, every tool](#1-one-test-every-tool) | Live |
| 12–17 | [2. When it fails](#2-when-it-fails) | Break it live, show the report prepared beforehand |
| 17–23 | [3. Migrate one TestBench class](#3-migrate-one-testbench-class) | Live on one class |
| 23–25 | Slide "Also in the box", with the prepared Viewport Comparison if there is time | Deck, browser |
| 25–30 | Slides: status, the ask, questions | Deck |

## The day before

```bash
scripts/prepare-talk.sh
```

It builds and installs every module, runs the demo's tests on Browserless and on Playwright, then compares the runs.
It also prepares the failure reports, the Viewport Comparison, the Browserless vs Playwright timing and the migration
worktree, all under `target/talk/`. At the end it lists the files to open. Options: `--selenium` (needs the TestBench
licence), `--quick` (a subset of the tests), `--skip-bench`, `--skip-viewports`, `--skip-migration`. Set
`PLAYWRIGHT_EXECUTABLE` to use an installed Chrome instead of Playwright's download.

Then:

- Copy the median times from `target/talk/benchmark/compare-browserless-playwright.md` into the deck's "The same
  test, two speeds" slide.
- Run it again an hour before the talk, on the machine and network you present from.

## Before going on stage

- IDE open on `unified-testing-tool`, font size 18 or more, presentation mode ready. Open these tabs:
  `unified-test-demo/src/test/java/demo/EditPersonUnifiedTest.java`,
  `unified-test-demo/src/test/java/demo/ProfileViewTest.java`.
- A second IDE window, or a terminal, on the migration worktree (`../unified-testing-tool-talk-migration`, branch
  `talk/migration-before`), with `unified-test-demo/src/test/java/demo/ProfileTestBenchTest.java` open.
- Browser tabs: `target/talk/evidence-failure-playwright/report.html`, `target/talk/evidence-playwright/report.html`,
  `target/talk/viewport-comparison/viewport-comparison.html`.
- Terminal at the repository root, cleared, large font. `git status` is clean.
- Turn off notifications.

## 1. One test, every tool

**Point:** one test class, three Testing Tools, no change to the test.

1. Show `EditPersonUnifiedTest`. Say what it does: click Alan Turing's row, rename him, save, check the notification
   and the grid row. Point out `page.find(UnifiedTextField.class).withLabel("Name")`: components are found by type
   and by what the user sees.
2. Run it on Browserless:

   ```bash
   mvn -o -q -pl unified-test-demo test -Dtest=EditPersonUnifiedTest
   ```

   It takes a few seconds, most of them Spring starting. No browser, no server port.
3. Run the same class on Playwright:

   ```bash
   mvn -o -q -pl unified-test-demo test -Dtest=EditPersonUnifiedTest -Dvaadin.testing.tool=playwright
   ```

   To let the room see the browser, add `-Dvaadin.testing.playwright.headless=false`. It is a real Chromium, with the
   app on a random port.
4. Selenium: don't run it live. Say "and `-Dvaadin.testing.tool=selenium` runs it on TestBench". If you ran
   `--selenium` in preparation, show `target/talk/evidence-selenium/summary.md`.
5. Show `target/talk/tool-comparison.md`, or the "Compared with Browserless" section of
   `target/talk/evidence-playwright/summary.md`: the scenarios that end differently, or take other User Steps, in a
   browser.

**Fallback:** if a run hangs, stop it (Ctrl+C) and open `target/talk/evidence-playwright/report.html` on the
`EditPersonUnifiedTest` scenario.

## 2. When it fails

**Point:** a failure tells you what the test could find, and what you probably meant.

1. In `ProfileViewTest`, change the `savesName` lookup `withLabel("Email")` to `withLabel("E-mail")`.
2. Run it on Browserless:

   ```bash
   mvn -o -q -pl unified-test-demo test -Dtest=ProfileViewTest -Dvaadin.testing.timeout=2s
   ```

   A failed lookup waits for the timeout (10 s by default) in case the component is still coming. That is why the run
   uses 2 s: ten silent seconds on stage feel long. Say why it waits.

   Read the failure aloud: `Failed at` (the User Step), `Expected`, `Found none`, `Nearby` (the Near Miss),
   `Did you mean find(UnifiedTextField.class).withLabel("Email")`. Scroll to the Findable View: one line per
   component the test can find, with the lookup that singles it out.
3. Switch to the browser tab `target/talk/evidence-failure-playwright/report.html`: the same failure from Playwright,
   with the User Steps and the screenshot, the Step Target highlighted.
4. Bonus, 30 seconds: the tool fixes its own test.

   ```bash
   cd unified-test-demo
   mvn -o -q org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
       -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-SNAPSHOT \
       -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.ApplyDidYouMean
   cd ..
   git diff
   ```

   The diff puts `"Email"` back. Say it: no model involved. The recipe reads the failure the run left behind.

**Reset:** `git checkout -- unified-test-demo/src/test/java/demo/ProfileViewTest.java`.

**Fallback:** `target/talk/failure-browserless.txt` has the console output of the same failure.

## 3. Migrate one TestBench class

**Point:** existing suites don't have to be rewritten by hand.

Work in the migration worktree (`../unified-testing-tool-talk-migration`). Its demo module has one test class,
`ProfileTestBenchTest`, a TestBench test that runs on Selenium only and needs a licence.

1. Show `ProfileTestBenchTest`: `BrowserTestBase`, `$(TextFieldElement.class).withLabel("Name").single()`,
   `getDriver().get("http://localhost:" + port + "/profile")`.
2. Run the recipe:

   ```bash
   mvn -o -q org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run -pl unified-test-demo \
       -Drewrite.recipeArtifactCoordinates=org.vaadin.addons:unified-test-rewrite:0.1.0-SNAPSHOT \
       -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified
   ```

3. `git diff`: walk through it. `BrowserTestBase` becomes `@UnifiedSpringBootTest`. `$(…Element.class)…single()`
   becomes `page.find(Unified….class)…`. The URL becomes `page.navigate(…)`. Point at any `// TODO unified:`
   Migration Marker: what the recipe couldn't convert, left running through the Escape Hatch.
4. Run the migrated test on Browserless, with no licence and no browser:

   ```bash
   mvn -o -q -pl unified-test-demo test
   ```

5. Scale it up in one sentence: the same recipe runs on a whole project. `FindTestClassesToMigrate` lists, as a dry
   run, which classes it converts and why. Show `target/talk/migration/FindTestClassesToMigrate*.csv` from the main
   checkout. Then the `resolve-migration-markers` agent skill works through the markers.

**Reset:** `git reset --hard && git clean -fd` in the worktree.

**Fallback:** `target/talk/migration/ProfileTestBenchTest.diff` is the same diff, from the rehearsal run.
`unified-test-demo/src/test/java/demo/ProfileMigratedTest.java` in the main checkout is the golden output that CI
checks.

## Questions to expect

| Question | Answer |
|---|---|
| What if I need raw Playwright or WebDriver? | The Escape Hatch calls the Testing Tool directly. A test can require a Capability, such as a real browser, and is skipped on a tool without it. |
| How much slower than a Direct Test? | Performance Pairs compare each Unified UI Test with a Direct Test of the same scenario in CI, against a budget of 10 % or 2 ms per user action. `largeGrid` on Browserless is currently over budget; say so. |
| Which components? | The Core Set, one per interaction pattern, plus Wrappers for the common components. The API reference is in the jar: `META-INF/unified/api-reference.md`. |
| Which Vaadin versions? | Vaadin 25.3, Java 21, Spring Boot 4.1 for `@UnifiedSpringBootTest`. On an older Vaadin each test fails at once and names the version it found. |
| What doesn't work the same everywhere? | Troubleshooting, "Known limitations by Testing Tool", and the spec's Appendix: gaps. |
