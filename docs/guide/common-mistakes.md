# Common mistakes

Each mistake below passes on a good day and fails on a bad one, or passes when it shouldn't. Each comes as a pair: the code to look for in a test, then what to write instead, with the guide page or style rule that explains why. The examples compile in CI, the mistakes included.

For a reviewer, this page is the other side of [Reviewing a test](reviewing-tests.md).

## Checking with a getter

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:getterBad"
```

**Good**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:getterGood"
```

A getter reads the state now. In a browser, the server's answer can arrive a moment after the action, so the check passes on Browserless and fails now and then on Playwright. `assertThat(..)` retries until the timeout, and shows in the reports as a `see` step. More: [Getters don't retry](assertions-and-waiting.md#getters-dont-retry).

## Reading before the action, comparing after

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:countBad"
```

**Good**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:countGood"
```

Reading a value you need is fine. Comparing it with a second read is the getter mistake again: check with `assertThat(..)`.

## Waiting with a sleep

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:sleepBad"
```

**Good**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:sleepGood"
```

Every action waits for the server, and every check waits until it holds. A sleep adds time to every run and still fails on a slow one. The same goes for a Testing Tool's own waits through the Escape Hatch. For a condition no check covers, use `page.waitUntil(..)`. The style rule `NO_RAW_WAITS` fails a test with a sleep. More: [Assertions and waiting](assertions-and-waiting.md), [Checking the style](style-rules.md#the-rules).

## A longer timeout to make it pass

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:timeoutBad"
```

A check that fails at 10 seconds usually waits for something that doesn't happen, not for something slow. Read the failure's *Found* line first ([The failure message](reports-and-failures.md#the-failure-message)): a notification with another text, or none at all, won't come with more time. Raise a timeout only for work that is slow on purpose, such as an export, and say so in a comment. More: [Timeouts](assertions-and-waiting.md#timeouts).

## Picking the first of several

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:firstOfSeveralBad"
```

**Good**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:firstOfSeveralGood"
```

The order of matches is not something the user sees, and it changes with the data. Narrow the lookup the way the user would: by a scope (a row, a dialog, a form section), or by another filter. `all()` is for a loop over every match. More: [Several matches](finding-components.md#several-matches), [Scopes](finding-components.md#scopes).

## A test id where the user reads a label

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:testIdBad"
```

**Good**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:testIdGood"
```

A test id names nothing the user reads, in the test or in its User Steps, and the test stays green when the label the user reads is wrong or missing. Keep test ids for components the user reads nothing on. More: [By what the user sees](finding-components.md#by-what-the-user-sees-prefer-these).

## Ending on an action

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:noCheckBad"
```

**Good**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:noCheckGood"
```

A test that clicks *Save* and stops passes whether or not the save works. End on what the user sees after the action. The run summary lists such tests under *Scenarios without a check* *(unreleased)*. More: [Scenarios without a check](reports-and-failures.md#scenarios-without-a-check).

## The Escape Hatch for what the Unified API has

**Bad**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:escapeHatchBad"
```

**Good**

```java
--8<-- "unified-test-demo/src/test/java/demo/CommonMistakes.java:escapeHatchGood"
```

The Escape Hatch ties a test to one Testing Tool and leaves a gap in its User Steps. Look in the [API reference](../../unified-test-api/src/main/resources/META-INF/unified/api-reference.md) first. When a test does need a Testing Tool's own API, declare it with `@RequiresTestingTool`, so that the test is skipped, not failed, on the others. The style rule `TOOL_CODE_NEEDS_CAPABILITY` fails a test that doesn't. More: [When a test needs one Testing Tool](tool-specific.md).

## A method name instead of a scenario

**Bad**

```java
@Test
void testSave(UnifiedPage page) { … }
```

**Good**

```java
@Test
@DisplayName("The user saves a name and sees a confirmation")
void savesName(UnifiedPage page) { … }
```

The `@DisplayName` is the scenario's name in the CI log, `user-steps.txt`, the run summary and the failure message. Write what the user does and sees. The style rule `SCENARIO_SENTENCE` fails a test without one. More: [The scenario sentence](structuring-a-scenario.md#the-scenario-sentence).

## Habits from other Testing Tools

A test migrated by hand, or written by someone used to another tool, often keeps that tool's habits. The Migration Recipes convert most of them ([TestBench](../migrating-from-testbench.md#what-it-converts), [Browserless](../migrating-from-browserless.md#what-it-converts), [Dramafinder](../migrating-from-dramafinder.md#what-it-converts)).

| Habit | From | In the Unified API |
|---|---|---|
| `$(ButtonElement.class).first()`, `atIndex(n)`, Dramafinder `get(i)` | TestBench, Browserless, Dramafinder | a lookup that matches exactly one: no `first()` ([Several matches](finding-components.md#several-matches)) |
| `withCaption("Save")` on a button | TestBench, Browserless | `withText("Save")`, or `withAccessibleName(..)` for an icon button |
| `assertEquals(x, el.getValue())`, `assertTrue(cb.isChecked())` | all three | `assertThat(el).hasValue(x)`, `assertThat(cb).isChecked()` |
| `roundTrip()`, `runPendingSignalsTasks()` | Browserless | nothing: actions wait for the server, and checks run pending tasks between attempts ([Assertions and waiting](assertions-and-waiting.md)) |
| `getDriver().get("http://localhost:" + port + "/people")` | TestBench | `page.navigate(PeopleView.class)` ([Navigation](navigation-and-users.md)) |
| `page.locator("#id")`, CSS or XPath | Playwright, Dramafinder | a Wrapper with filters; a test id or id as the last resort ([Finding components](finding-components.md)) |
| `test(component).click()` | Browserless | `wrapper.click()` |
| a base class that starts the browser or the app | TestBench, Dramafinder | `@UnifiedSpringBootTest` or `@UnifiedUITest` ([Getting started](getting-started.md)) |
