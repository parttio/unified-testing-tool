# Running in CI

The Unified API needs no CI plugin. When the tests have run, it writes its reports itself, from inside the test JVM: every test's User Steps, the failure evidence, and the run summary (`summary.md`). On GitHub Actions it also puts that summary on the job's summary page. This page shows the CI setup that makes the most of it. [Reading reports and failures](reports-and-failures.md) explains what the reports contain.

| What you get | Needs |
|---|---|
| Failed tests' User Steps in the CI log | nothing |
| `summary.md`: the counts, the scenarios changed in the pull request, the failed scenarios | nothing: written to `target/surefire-reports/unified-evidence/` |
| `report.html`: the same run in a browser, with every scenario's User Steps and the failure screenshots | nothing: written next to `summary.md`; read it from the artifact |
| `summary.json`: the same run as data, for a script or an agent | nothing: written next to `summary.md` |
| The summary on the GitHub job's page | GitHub Actions: nothing |
| **Changed in this PR** in the summary | the git history and the base branch in the checkout |
| How each changed scenario's User Steps differ from the base branch's | the base branch's evidence artifact, downloaded |
| Where the Testing Tools' runs differ | a job after the others, with `CompareRuns` ([below](#comparing-the-testing-tools-runs)) |
| Every test's User Steps and failure screenshots after the build | an artifact upload of the evidence directory |

## GitHub Actions

A complete workflow, saved as `.github/workflows/ui-tests.yml`: the tests on Browserless on every push and pull request, and the same tests in a browser with Playwright.

```yaml
name: UI tests

on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read
  actions: read   # to download the base branch's evidence

jobs:
  browserless:
    runs-on: ubuntu-latest
    steps:
      # history and the base branch, for "Changed in this PR"
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Fetch the pull request's base branch
        if: github.event_name == 'pull_request'
        run: git fetch --no-tags origin "+refs/heads/${GITHUB_BASE_REF}:refs/remotes/origin/${GITHUB_BASE_REF}"
      # the base branch's last evidence, to diff each changed scenario's User Steps against it
      - name: Download the base branch's last User Steps
        if: github.event_name == 'pull_request'
        continue-on-error: true
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          for run in $(gh run list --workflow ui-tests.yml --branch "$GITHUB_BASE_REF" --event push \
              --status completed --limit 5 --json databaseId --jq '.[].databaseId'); do
            gh run download "$run" --name unified-evidence-browserless --dir "$RUNNER_TEMP/base-user-steps" && break
            rm -rf "$RUNNER_TEMP/base-user-steps"
          done
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Tests on Browserless
        run: mvn -B -ntp verify -Dvaadin.testing.user-steps.baseline-dir="$RUNNER_TEMP/base-user-steps"
      # every test's User Steps and failure evidence, also when the build passes
      - name: Upload Unified evidence
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unified-evidence-browserless
          path: '**/target/*-reports/unified-evidence/'
          if-no-files-found: ignore

  playwright:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Fetch the pull request's base branch
        if: github.event_name == 'pull_request'
        run: git fetch --no-tags origin "+refs/heads/${GITHUB_BASE_REF}:refs/remotes/origin/${GITHUB_BASE_REF}"
      # the base branch's last evidence, to diff each changed scenario's User Steps against it
      - name: Download the base branch's last User Steps
        if: github.event_name == 'pull_request'
        continue-on-error: true
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          for run in $(gh run list --workflow ui-tests.yml --branch "$GITHUB_BASE_REF" --event push \
              --status completed --limit 5 --json databaseId --jq '.[].databaseId'); do
            gh run download "$run" --name unified-evidence-playwright --dir "$RUNNER_TEMP/base-user-steps" && break
            rm -rf "$RUNNER_TEMP/base-user-steps"
          done
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Compile
        run: mvn -B -ntp test-compile
      # the libraries the browser needs on the runner; the run downloads the browser itself
      - name: Install the libraries Chromium needs
        run: >
          mvn -B -ntp exec:java
          -Dexec.mainClass=com.microsoft.playwright.CLI
          -Dexec.classpathScope=test
          -Dexec.args="install-deps chromium"
      # a failed test keeps Playwright's trace, grouped by User Step
      - name: Tests on Playwright
        run: >
          mvn -B -ntp verify -Dvaadin.testing.tool=playwright -Dvaadin.testing.playwright.trace=on-failure
          -Dvaadin.testing.user-steps.baseline-dir="$RUNNER_TEMP/base-user-steps"
      - name: Upload Unified evidence
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unified-evidence-playwright
          path: '**/target/*-reports/unified-evidence/'
          if-no-files-found: ignore
```

In a multi-module build, run the Playwright CLI in the module that has the tests (`-pl <module>`), after `mvn install -DskipTests`.

What each part does:

- **The job summary.** GitHub Actions sets `GITHUB_STEP_SUMMARY` in every job, and the Unified API appends `summary.md` to it, so the summary shows on the job's page and on the pull request's checks. Each job adds its own block, headed with its Testing Tool. To leave it out, set `vaadin.testing.user-steps.github-summary` to `false`.
- **Changed in this PR.** In a pull request GitHub sets `GITHUB_BASE_REF`, and the Unified API compares `HEAD` with `origin/<base branch>`. `actions/checkout` fetches one commit and no other branch by default, so the workflow asks for the history (`fetch-depth: 0`) and fetches the base branch. Without them the section is left out and one line is logged; the rest of the summary is unchanged.
- **The base branch's User Steps.** In a pull request, the job downloads the evidence artifact its Testing Tool's job uploaded in the base branch's last finished push run (skipping runs without one, such as cancelled runs), and `vaadin.testing.user-steps.baseline-dir` points the run summary at it. Each changed scenario then shows how its User Steps differ, as a diff. The download needs `actions: read`; when it finds nothing, for example after the artifact expired, the summary says so in one line and shows the steps in full. On a push the directory isn't there and isn't read: the diff is only in **Changed in this PR**.
- **The evidence artifact.** `target/surefire-reports/unified-evidence/` holds `summary.md`, `report.html` and `summary.json`, and for every test, passed or failed, its `user-steps.txt` and `user-steps.json`, plus a screenshot (browsers) or the component tree (Browserless) when it failed. `if: always()` keeps it when the build passes too. A reviewer downloads it from the run's page, unzips it and opens `report.html`; an agent reads `summary.json`, then the `user-steps.json` of the scenarios it names.

### Comparing the Testing Tools' runs

With a job per Testing Tool, the jobs run side by side and none has another's evidence, so `vaadin.testing.user-steps.compare-dir` has nothing to read. A job after them compares what they uploaded, with the `CompareRuns` command of `unified-test-api`:

```yaml
  compare:
    name: Compare Testing Tools
    needs: [browserless, playwright]
    if: ${{ !cancelled() }}        # also when a job failed: that is when the comparison says most
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Download the jobs' Unified evidence
        env:
          GH_TOKEN: ${{ github.token }}
        run: gh run download "$GITHUB_RUN_ID" --repo "$GITHUB_REPOSITORY" --pattern 'unified-evidence-*' --dir evidence || true
      - name: Compare the runs
        run: |
          mvn -B -ntp -q dependency:copy -Dartifact=org.vaadin.addons:unified-test-api:<version> -DoutputDirectory=tools
          java -cp "tools/*" com.vaadin.testing.unified.report.CompareRuns evidence/* || [ $? -eq 2 ]
```

It needs `actions: read`, and the evidence artifacts named as above (`unified-evidence-<tool>`). The job's summary page then says, for each Testing Tool against Browserless, which scenarios pass on one and fail on the other, which take different User Steps and where they part, and whether both runs tested the same commit ([what it says](reports-and-failures.md#compared-with-another-testing-tool)). It reports and fails nothing: exit code 2 means fewer than two Testing Tools left evidence, which the jobs before it already show.

On another CI server the command is the same: give it the evidence directories the jobs kept.

### Surefire and Failsafe in one build

*(unreleased)* The evidence directory is `target/surefire-reports/unified-evidence` whichever plugin runs the tests. A build that runs its Browserless tests with Surefire and its ITs with Failsafe would put both phases in it: one `summary.md` with the counts of both runs, Failsafe's over Surefire's. The migration recipes give every `maven-failsafe-plugin` in `<build><plugins>` and in each profile its own directory, unless it sets one already ([#512](https://github.com/parttio/unified-testing-tool/issues/512)):

```xml
<plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <vaadin.testing.evidence-dir>${project.build.directory}/failsafe-reports/unified-evidence</vaadin.testing.evidence-dir>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

Without the recipe, add it by hand. The upload above takes both directories (`*-reports`), and `ApplyDidYouMean` reads both. On Gradle, a second test task (`integrationTest`) sets `systemProperty("vaadin.testing.evidence-dir", layout.buildDirectory.dir("integration-test-reports/unified-evidence").get().asFile.path)`.

### Both Testing Tools in one build

The jobs above run the tests once each. To run them on two Testing Tools in one `mvn verify`, give the plugin an execution per Testing Tool, and give each execution its own directories. Executions share the defaults, so without them the second execution silently overwrites the first's reports, its `summary.md` and `report.html`, and Failsafe's summary, and `verify` then checks only the last Testing Tool:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <!-- spring-boot-starter-parent declares this one: without it, the tests run a third time, on Browserless -->
        <execution>
            <id>default</id>
            <configuration>
                <skip>true</skip>
            </configuration>
        </execution>
        <execution>
            <id>it-browserless</id>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
            <configuration>
                <reportsDirectory>${project.build.directory}/failsafe-reports/browserless</reportsDirectory>
                <summaryFile>${project.build.directory}/failsafe-reports/browserless/failsafe-summary.xml</summaryFile>
                <systemPropertyVariables>
                    <vaadin.testing.tool>browserless</vaadin.testing.tool>
                    <vaadin.testing.evidence-dir>${project.build.directory}/failsafe-reports/browserless/unified-evidence</vaadin.testing.evidence-dir>
                </systemPropertyVariables>
            </configuration>
        </execution>
        <execution>
            <id>it-playwright</id>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
            <configuration>
                <reportsDirectory>${project.build.directory}/failsafe-reports/playwright</reportsDirectory>
                <summaryFile>${project.build.directory}/failsafe-reports/playwright/failsafe-summary.xml</summaryFile>
                <systemPropertyVariables>
                    <vaadin.testing.tool>playwright</vaadin.testing.tool>
                    <vaadin.testing.evidence-dir>${project.build.directory}/failsafe-reports/playwright/unified-evidence</vaadin.testing.evidence-dir>
                    <!-- the run before this one, for "Compared with Browserless" in this run's summary -->
                    <vaadin.testing.user-steps.compare-dir>${project.build.directory}/failsafe-reports/browserless/unified-evidence</vaadin.testing.user-steps.compare-dir>
                </systemPropertyVariables>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- **Three settings per execution.** `reportsDirectory` and `summaryFile` are Failsafe's; `vaadin.testing.evidence-dir` is the Unified API's. Each `verify` goal reads its own execution's summary, so a failure on either Testing Tool fails the build.
- **The parent's execution.** `spring-boot-starter-parent` binds Failsafe with an execution of its own, `default`, which runs the tests on the default Testing Tool, Browserless. Skip it as above. Without that parent there is no `default` execution to skip.
- **The comparison.** Executions bound to the same phase run in the order of the POM, so the second can read the first's evidence: its `summary.md` gets [Compared with Browserless](reports-and-failures.md#compared-with-another-testing-tool), with no `CompareRuns` job.
- **CI.** Upload `target/failsafe-reports/*/unified-evidence/`. On GitHub Actions each run adds its own block, headed with its Testing Tool, to the job summary.
- **Surefire** (tests named `*Test`) is the same with `maven-surefire-plugin`: its own execution is `default-test`, give it the first Testing Tool's settings, and it has no `summaryFile`.

### Selenium (TestBench)

TestBench is under the Vaadin Commercial License and checks it when it loads. Give the job your key as a secret, and use the Chrome that GitHub's Ubuntu runners have installed (Selenium Manager finds its driver):

```yaml
  selenium:
    runs-on: ubuntu-latest
    env:
      VAADIN_OFFLINE_KEY: ${{ secrets.VAADIN_OFFLINE_KEY }}
    steps:
      # the same checkout, fetch, setup-java and upload steps as above
      - name: Tests on Selenium
        run: mvn -B -ntp verify -Dvaadin.testing.tool=selenium
```

## Other CI servers

The reports are written the same way on any CI server: only the GitHub job summary needs GitHub. Two things to set up yourself:

- **The base for Changed in this PR.** Pass the base branch as `-Dvaadin.testing.user-steps.changed-since=origin/<branch>`, and make sure the checkout has the history and that branch. Any git ref works; outside a merge request, leave it unset.
- **The base branch's User Steps.** Keep the evidence directory of the base branch's builds, and pass a copy of the last one as `-Dvaadin.testing.user-steps.baseline-dir=<directory>` to show how each changed scenario's User Steps differ.
- **Publishing.** Keep `target/*-reports/unified-evidence/` (Surefire's, and Failsafe's when the build has it) as a build artifact, also when the build passes, so `summary.md` and every test's User Steps can be read after the build.

Settings are read from JUnit configuration parameters, which fall back to system properties, so `-D` on the Maven command line reaches the tests.

### GitLab CI

A sketch (not run in this project's CI): in a merge request pipeline GitLab sets `CI_MERGE_REQUEST_TARGET_BRANCH_NAME`, and `GIT_DEPTH: "0"` asks for the whole history.

```yaml
ui-tests:
  image: maven:3-eclipse-temurin-21
  variables:
    GIT_DEPTH: "0"
  script:
    - |
      if [ -n "$CI_MERGE_REQUEST_TARGET_BRANCH_NAME" ]; then
        git fetch --no-tags origin "+refs/heads/$CI_MERGE_REQUEST_TARGET_BRANCH_NAME:refs/remotes/origin/$CI_MERGE_REQUEST_TARGET_BRANCH_NAME"
        CHANGED_SINCE="-Dvaadin.testing.user-steps.changed-since=origin/$CI_MERGE_REQUEST_TARGET_BRANCH_NAME"
      fi
      mvn -B verify $CHANGED_SINCE
  artifacts:
    when: always
    paths:
      - target/*-reports/unified-evidence/
```

### Jenkins

In a multibranch pipeline building a pull request, Jenkins sets `CHANGE_TARGET` to the base branch: pass `-Dvaadin.testing.user-steps.changed-since=origin/$CHANGE_TARGET` when it is set, and archive the evidence directory with `archiveArtifacts artifacts: '**/target/*-reports/unified-evidence/**', allowEmptyArchive: true` in a `post { always { … } }` block.

## Settings

| Setting | Default |
|---|---|
| `vaadin.testing.tool` | `browserless`; `playwright` or `selenium` for a browser |
| `vaadin.testing.user-steps.log` | `failed`: the User Steps of failed tests in the CI log; `all` or `none` |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` to the GitHub job summary when `GITHUB_STEP_SUMMARY` is set |
| `vaadin.testing.user-steps.changed-since` | in a GitHub pull request, `origin/$GITHUB_BASE_REF`; otherwise unset |
| `vaadin.testing.user-steps.snapshots` | `false`; `true`: a snapshot of the page after every User Step |
| `vaadin.testing.playwright.trace` | `off`; `on-failure` keeps the trace of failed tests as `trace.zip` |
| `vaadin.testing.evidence-dir` | `target/surefire-reports/unified-evidence` |

All settings: [Configuration](configuration.md), [Reading reports and failures](reports-and-failures.md#settings) and [2. Test setup](../spec/02-test-setup.md).
