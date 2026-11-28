# Structuring a scenario

A Unified UI Test is plain JUnit, and the Unified API doesn't impose a structure on it. This page recommends one, so that a test reads as the scenario it checks: for the reviewer of the diff, for whoever reads a failure, and in the [reports](reports-and-failures.md). Spec: [12. Readable tests, Structuring a scenario](../spec/12-readable-tests.md#structuring-a-scenario).

| Part | What it is for | Shows in the reports |
|---|---|---|
| `@DisplayName("…")` | the scenario in one sentence | yes, as the scenario's name |
| `// given`, `// when`, `// then` | the shape of the scenario, for the reader of the code | no |
| **User Task**: a method whose body is one `page.step("…", () -> …)` | one named piece of user work, named after what the user does | yes, as a heading over its User Steps |
| lean page object (optional) | the lookups of one view, as methods returning Wrappers | no |

## A scenario

```java
--8<-- "unified-test-demo/src/test/java/demo/UserStepsTest.java:scenario"
```

Its `user-steps.txt`:

```
✔ UserStepsTest › Editing a person in nested steps updates the grid   browserless · 0.1 s
   1  open People
   2  Edit Alan Turing
      2.1  click row where Name is “Alan Turing” in grid
      2.2  Change the name
         2.2.1  fill text field “Name” with “Alan M. Turing”
         2.2.2  fill text field “Email” with “••••”
      2.3  click button “Save”
   3  see notification “Saved Alan M. Turing”
   4  see row where Name is “Alan M. Turing” with Email “••••” in grid
```

## The scenario sentence

`@DisplayName` names the scenario in a sentence about the user: what they do and what they see. Not the method name in words. It is the heading of the test in the CI log, in `user-steps.txt`, in the run summary and in a failure message. `@DisplayNameGeneration` on the class does the same for every test in it.

The style rule `SCENARIO_SENTENCE` fails a Unified UI Test without one ([Checking the style](style-rules.md)).

## Given, when, then

Mark the three parts of the scenario with comments: `// given` sets the scene (opens the view, prepares data), `// when` is the user work under test, `// then` checks what the user sees. They are for the reader of the code only, and the reports don't show them. Keep them as comments: a step named "when" says nothing about what the user did.

## User Tasks

A **User Task** is a method whose body is one `page.step(name, () -> …)`. The name says what the user gets done ("Edit Alan Turing"), and the reports show it as a heading over the User Steps inside it.

```java
void step(String name, Runnable userSteps);   // on UnifiedPage
```

- **Name it after the user's work**, with its arguments: `"Edit " + name`. Not given/when/then, and not the method name.
- **Steps nest** without limit, as `Change the name` does above. Reports indent them, and a failure names the chain.
- **A failure inside a step** points first at the line that called the step, then at the line inside it.
- **`page.step(..)` is on the page**, not a static method: it needs no import and doesn't clash with `Allure.step`. It records into the test's run, so a step works across `openWindow()`, `openAsUser(..)` and `openNewSession()`.

Every Wrapper action and retrying assertion is a User Step whether or not it is inside a `page.step(..)`: a test without User Tasks is reported too, as a flat list.

### Where User Tasks live

1. **In the test class**, as a `private static` method taking the `UnifiedPage`, like `editPerson` above. Start here.
2. **In a `…Tasks` class of static methods**, once a second test class needs the same work. Move it, don't copy it:

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:peopleTasks"
    ```

    A test calls `PeopleTasks.editPerson(page, "Alan Turing", "Alan M. Turing")`, or imports it statically. The run summary marks every scenario that uses a changed `…Tasks` class as changed ([Changed in this PR](reports-and-failures.md#the-run-summary)).

Keep assertions out of User Tasks, in the test's `// then`: a task is what the user does, and the test decides what it expects.

## Declaring Wrappers before a step

Wrappers are lazy: declaring one searches nothing, and every use finds the component again. So declare the Wrappers the test needs at the top, use them inside a step, and check them after it:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:wrappersBeforeAStep"
```

`page.step(..)` takes a `Runnable` and returns nothing: a step does work, it doesn't hand back values.

## Secret values

Values typed into a password field are shown as `••••` in every report. Mark any other value as secret with `page.secret(..)`, which returns it unchanged:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:secret"
```

From then on the value is masked wherever it appears in the test's reports, in every window: `fill text field “Email” with “••••”` in the scenario above, and again in the assertion that expects it. It is masked even inside a longer word: a log line or an error that echoes it is covered.

Two places keep their text:

- **The login's user name.** A password given to `UnifiedLogin.login(..)` is not masked inside that login's own user name: `login("barista@vaadin.com", "barista")` reads `sign in as “barista@vaadin.com”`, and so does a step you named `Sign in as barista@vaadin.com`. If the password equals the user name (`admin` / `admin`), it is masked there too. The user name and the password are then the same text, so the reports can't show one and hide the other: [name the user in the step](#a-user-whose-password-is-its-user-name) instead.
- **The scenario's name you wrote.** A `@DisplayName`, or the method's name, is shown as written, because it comes from your source. The name of a parameterized or repeated test's invocation (`[1] barista`) is built from its arguments, so it is masked.

### A user whose password is its user name

Seeded test users often have their user name as password (`admin` / `admin`). Their sign-in reads `sign in as “••••” in login`, and in a test that switches users the reports no longer say who acts. Wrap the sign-in in a step that names the user by their role, in words that don't contain the password: a secret is masked inside a step's name too, so `Log in as the administrator` would read `Log in as the ••••istrator`.

```java
page.step("Sign in as the back-office manager",
        () -> page.find(UnifiedLogin.class).login("admin", "admin"));
//   2  Sign in as the back-office manager
//      2.1  sign in as “••••” in login
```

There is no way to declare a password not secret: the reports would then print it wherever it occurs ([#683](https://github.com/parttio/unified-testing-tool/issues/683)). A password that differs from its user name avoids the question.

Reports are for tests that run on test data or anonymised data, so masking keeps a value out of what a reader sees but isn't a security boundary ([ADR 0014](../adr/0014-secrets-are-masked-as-substrings-except-the-login-user-name-and-display-name.md)).

## Page objects: optional, structure only

Wrappers already find components the way a user names them, so most tests need no page object. When a view's lookups are long, scoped or repeated, a **lean page object** holds them in one place: a class of methods returning Wrappers, with no actions and no assertions.

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:peopleViewLookups"
```

The user's work stays in User Tasks, which use the page object's lookups. Structure (where things are) and intent (what the user gets done) stay in two layers.

TestBench page objects that the [Migration Recipes](../migrating-from-testbench.md#page-objects) converted keep working. Their lookup methods are structure, and their action methods are User Tasks in all but name:

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileMigratedPage.java"
```

To get `fill` reported as one User Task, wrap its body in `page.step("Fill in the profile", () -> …)`.

## Where the ideas come from

User Tasks are inspired by Screenplay's tasks, without the framework: no actors, abilities or questions, only a named method over the Unified API. Screenplay's own docs explain the layers: [Serenity BDD, Screenplay Fundamentals](https://serenity-bdd.github.io/docs/screenplay/screenplay_fundamentals) and [Serenity/JS, Organising page elements](https://serenity-js.org/handbook/web-testing/page-objects-pattern/) (lean page objects with tasks on top). The reasoning for this layout is in the research note [Page objects and Tasks](../../docs/research/page-objects-and-tasks.md).

## JGiven, if you want given/when/then stages

The Unified API has no BDD layer and no Gherkin ([BDD research](../../docs/research/bdd-for-unified-ui-tests.md)). If your team wants given/when/then stage classes, [JGiven](https://jgiven.org/) works with `@UnifiedSpringBootTest` on every Testing Tool, with one caveat: JGiven reports a failed step after the test has ended, so the Unified API writes **no failure evidence** (no screenshot, no component tree) for it.
