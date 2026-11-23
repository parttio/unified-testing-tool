# Reviewing a Unified UI Test

For the reviewer of a pull request that adds or changes Unified UI Tests, written by a colleague or by an agent. A Unified UI Test is meant to read as the scenario it checks, and every run reports that scenario as User Steps. So review the run first, then the code: the run shows what the test does, and the code shows how.

## What to read, in order

1. **The run summary**, on the CI job's page or in `summary.md` ([The run summary](reports-and-failures.md#the-run-summary)):
    - **Changed in this PR** lists the new and changed scenarios. With the base branch's last run, it shows how each changed scenario's User Steps differ, as a diff ([How a changed scenario's steps changed](reports-and-failures.md#how-a-changed-scenarios-steps-changed)).
    - **Scenarios without a check** lists the passing scenarios whose last steps nothing checks ([Scenarios without a check](reports-and-failures.md#scenarios-without-a-check)).
    - **Compared with another Testing Tool** lists the scenarios that end differently, or take different steps, in a browser ([Compared with another Testing Tool](reports-and-failures.md#compared-with-another-testing-tool)).
2. **The User Steps of each new or changed scenario**: in the summary, or in the test's `user-steps.txt` in the CI artifact ([The User Steps of a test](reports-and-failures.md#the-user-steps-of-a-test)). Read them as the person who asked for the scenario would.
3. **The diff of the test code**, with the checklist below. [Common mistakes](common-mistakes.md) shows the code to look for.

## Checklist

### The scenario

| Check | Where you see it | More |
|---|---|---|
| The `@DisplayName` says what the user does and sees, in a sentence, and it is the scenario that was asked for. | the scenario's heading in the summary and in `user-steps.txt` | [The scenario sentence](structuring-a-scenario.md#the-scenario-sentence) |
| Every action the scenario describes is a User Step, in its order, on the component it names. | `user-steps.txt` | [Reading a User Step](reports-and-failures.md#reading-a-user-step) |
| Every outcome the user expects is a `see` or `see no` step, after the action that causes it. | `user-steps.txt`; *Scenarios without a check* | [Scenarios without a check](reports-and-failures.md#scenarios-without-a-check) |
| No gap line (`… 1 call outside the Unified API`) without a reason the test states. | `user-steps.txt` | [Reading a User Step](reports-and-failures.md#reading-a-user-step) |
| Work the user does in several scenarios is a shared User Task, not a copy. | the diff | [Where User Tasks live](structuring-a-scenario.md#where-user-tasks-live) |
| Values that must not appear in reports are marked with `page.secret(..)`. | `••••` in `user-steps.txt` | [Secret values](structuring-a-scenario.md#secret-values) |

### The code

| Check | Where you see it | More |
|---|---|---|
| Components are found by what the user sees: label, text, accessible name. A test id only where the user reads nothing. | the diff; the step's name in `user-steps.txt` | [By what the user sees](finding-components.md#by-what-the-user-sees-prefer-these) |
| `all()` only to loop over every match, never `all().get(0)` to pick one: narrow the lookup instead. | the diff | [Several matches](finding-components.md#several-matches) |
| Checks are `assertThat(..)` from `UnifiedAssertions`, not a getter and `assertEquals`. | the diff; a check shows as a `see` step, a getter shows nothing | [Getters don't retry](assertions-and-waiting.md#getters-dont-retry) |
| No raw waits, and no raised timeout without a reason in a comment. | the diff; the style rules | [Timeouts](assertions-and-waiting.md#timeouts) |
| Testing Tool code only behind `@RequiresTestingTool`, also in `…Tasks` classes and page objects, which the style rules don't follow. | the diff; the style rules | [When a test needs one Testing Tool](tool-specific.md) |

### The runs

| Check | Where you see it | More |
|---|---|---|
| It passed on Browserless and in a browser. | the CI jobs; *Compared with another Testing Tool* | [Running in CI](ci-setup.md) |
| The style rules pass. | the `UnifiedStyleTest` result | [Checking the style](style-rules.md) |
| A test changed to make it pass still checks what it checked before. | the User Steps diff in *Changed in this PR* | [below](#when-a-test-was-changed-to-pass) |

## When a test was changed to pass

A test that failed and now passes either found a bug in the application or was wrong itself. Decide which before you accept the change. The User Steps diff against the base run shows what the change did to the scenario:

```diff
  open People
  click row where Name is “Alan Turing” in grid
- fill text field “Email” with “alan@bletchley.uk”
+ fill text field “E-mail” with “alan@bletchley.uk”
  click button “Save”
- see row where Name is “Alan Turing” in grid
```

- **A changed name**, as on the fourth line, is right when the view's label changed on purpose in the same pull request, and wrong when the test now names something the user doesn't see.
- **A removed `see` line**, as on the last line, means the scenario checks less than before. Accept it only when the outcome is no longer part of the scenario.
- **A longer timeout, a `page.waitUntil(..)` or an Escape Hatch** added to make a test pass usually hides a timing problem instead of fixing it. Ask what the test waits for, and check that with `assertThat(..)`.
- **A changed view or service** in the same pull request as a fixed test: check that the application change was asked for, and not made to fit the test.

## Reviewing a test an agent wrote

An agent following the `write-unified-ui-tests` skill reports the test's `@DisplayName`, its runs on Browserless and Playwright, and its `user-steps.txt`. Review it as you would a new colleague's test, in this order:

1. **Compare the report with the request**, before reading any code. Does the `@DisplayName` say what you asked for? Is every action and every expected outcome a User Step?
2. **Check that CI ran the same steps.** The run summary of the pull request shows the scenario's steps, independently of the agent's report.
3. **Run it again, more than once,** on Browserless and in a browser, before you trust a test that passed the first time:

    ```
    mvn test -Dtest=EditPersonTest
    mvn test -Dtest=EditPersonTest -Dvaadin.testing.tool=playwright
    ```

4. **Read the diff with the checklist above.** Look for changes outside the tests: an agent asked to make a test pass may have changed the application instead.
5. **For a test the agent fixed**, read [When a test was changed to pass](#when-a-test-was-changed-to-pass). A fix that adds a sleep, raises a timeout or drops a check is not a fix.

An agent can do the same review: ask one that has the `write-unified-ui-tests` skill to review a test or a pull request's tests. It follows this checklist and reports its findings without changing the code.

## What the tools check, and what they don't

| Checked for you | By |
|---|---|
| a `@DisplayName` of more than one word | the style rule `SCENARIO_SENTENCE` |
| no `Thread.sleep(..)`, and Testing Tool waits only in code that declares that Testing Tool | the style rule `NO_RAW_WAITS` |
| Testing Tool code in a test class only with `@RequiresTestingTool` | the style rule `TOOL_CODE_NEEDS_CAPABILITY` |
| scenarios that end without a check | the run summary |
| scenarios that behave differently on two Testing Tools | the run summary, with a compared run |
| what a change did to a scenario's steps | the run summary, with the base branch's run |

Left to you: whether the sentence describes the right scenario, whether a check is the right one, Testing Tool code in shared classes, and the rest of [What the rules don't check](style-rules.md#what-the-rules-dont-check).
