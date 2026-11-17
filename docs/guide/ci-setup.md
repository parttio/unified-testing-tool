# Running in CI

The Unified API needs no CI plugin. When the tests have run, it writes its reports itself, from inside the test JVM: every test's User Steps, the failure evidence, and the run summary (`summary.md`). On GitHub Actions it also puts that summary on the job's summary page. This page shows the CI setup that makes the most of it. [Reading reports and failures](reports-and-failures.md) explains what the reports contain.

| What you get | Needs |
|---|---|
| Failed tests' User Steps in the CI log | nothing |
| `summary.md`: the counts, the scenarios changed in the pull request, the failed scenarios | nothing: written to `target/surefire-reports/unified-evidence/` |
| The summary on the GitHub job's page | GitHub Actions: nothing |
| **Changed in this PR** in the summary | the git history and the base branch in the checkout |
| Every test's User Steps and failure screenshots after the build | an artifact upload of the evidence directory |

## GitHub Actions

A complete workflow: the tests on Browserless on every push and pull request, and the same tests in a browser with Playwright.

```yaml
name: UI tests

on:
  push:
    branches: [main]
  pull_request:

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
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Tests on Browserless
        run: mvn -B -ntp verify
      # every test's User Steps and failure evidence, also when the build passes
      - name: Upload Unified evidence
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unified-evidence-browserless
          path: '**/target/surefire-reports/unified-evidence/'
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
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Compile
        run: mvn -B -ntp test-compile
      # the browser and the libraries it needs on the runner
      - name: Install Chromium for Playwright
        run: >
          mvn -B -ntp exec:java
          -Dexec.mainClass=com.microsoft.playwright.CLI
          -Dexec.classpathScope=test
          -Dexec.args="install --with-deps chromium"
      - name: Tests on Playwright
        run: mvn -B -ntp verify -Dvaadin.testing.tool=playwright
      - name: Upload Unified evidence
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unified-evidence-playwright
          path: '**/target/surefire-reports/unified-evidence/'
          if-no-files-found: ignore
```

In a multi-module build, run the Playwright CLI in the module that has the tests (`-pl <module>`), after `mvn install -DskipTests`.

What each part does:

- **The job summary.** GitHub Actions sets `GITHUB_STEP_SUMMARY` in every job, and the Unified API appends `summary.md` to it, so the summary shows on the job's page and on the pull request's checks. Each job adds its own block, headed with its Testing Tool. To leave it out, set `vaadin.testing.user-steps.github-summary` to `false`.
- **Changed in this PR.** In a pull request GitHub sets `GITHUB_BASE_REF`, and the Unified API compares `HEAD` with `origin/<base branch>`. `actions/checkout` fetches one commit and no other branch by default, so the workflow asks for the history (`fetch-depth: 0`) and fetches the base branch. Without them the section is left out and one line is logged; the rest of the summary is unchanged.
- **The evidence artifact.** `target/surefire-reports/unified-evidence/` holds `summary.md`, and for every test, passed or failed, its `user-steps.txt` and `user-steps.json`, plus a screenshot (browsers) or the component tree (Browserless) when it failed. `if: always()` keeps it when the build passes too. A reviewer or an agent downloads it from the run's page.

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
- **Publishing.** Keep `target/surefire-reports/unified-evidence/` as a build artifact, also when the build passes, so `summary.md` and every test's User Steps can be read after the build.

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
      - target/surefire-reports/unified-evidence/
```

### Jenkins

In a multibranch pipeline building a pull request, Jenkins sets `CHANGE_TARGET` to the base branch: pass `-Dvaadin.testing.user-steps.changed-since=origin/$CHANGE_TARGET` when it is set, and archive the evidence directory with `archiveArtifacts artifacts: '**/target/surefire-reports/unified-evidence/**', allowEmptyArchive: true` in a `post { always { … } }` block.

## Settings

| Setting | Default |
|---|---|
| `vaadin.testing.tool` | `browserless`; `playwright` or `selenium` for a browser |
| `vaadin.testing.user-steps.log` | `failed`: the User Steps of failed tests in the CI log; `all` or `none` |
| `vaadin.testing.user-steps.github-summary` | `true`: append `summary.md` to the GitHub job summary when `GITHUB_STEP_SUMMARY` is set |
| `vaadin.testing.user-steps.changed-since` | in a GitHub pull request, `origin/$GITHUB_BASE_REF`; otherwise unset |
| `vaadin.testing.evidence-dir` | `target/surefire-reports/unified-evidence` |

All settings: [Reading reports and failures](reports-and-failures.md#settings) and [2. Test setup](../spec/02-test-setup.md).
