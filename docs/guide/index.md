# Developer guide

How to write Unified UI Tests, by example. The [Specification](../spec/README.md) says what the Unified API guarantees and why; this guide shows how to use it. The [API reference](../../unified-test-api/src/main/resources/META-INF/unified/api-reference.md) lists every Wrapper with its filters, actions, reads and checks.

Most examples below are included from the tests in [`unified-test-demo`](https://github.com/parttio/unified-testing-tool/tree/main/unified-test-demo/src/test/java/demo). They run in CI on every Testing Tool, so they can't go out of date.

## Your first test

Add the dependencies as in [Try it](../index.md#try-it). Then:

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:savesName"
```

in a class like this:

```java
import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest          // @UnifiedUITest for an app without Spring Boot
class ProfileViewTest {
    // tests take a UnifiedPage parameter
}
```

Run it:

```
mvn test                                         # Browserless, the default
mvn test -Dvaadin.testing.tool=playwright        # the same test in a real browser
```

!!! tip "Import `assertThat` from `UnifiedAssertions`"
    `UnifiedAssertions.assertThat(wrapper)` retries until the timeout; AssertJ's or JUnit's assertions don't. Import it statically next to your other assertion libraries: it only accepts Unified API types.

## Four steps in every test

```mermaid
flowchart LR
    N["navigate<br/>page.navigate(View.class)"] --> F["find<br/>page.find(UnifiedX.class).withLabel(..)"] --> A["act<br/>setValue, click, selectItem"] --> C["check<br/>assertThat(wrapper).has…"]
```

| Step | API | Guide page |
|---|---|---|
| Open a view | `page.navigate(View.class)` | [Navigation, users and windows](navigation-and-users.md) |
| Find a component | `page.find(Wrapper.class)` and its filters: it returns the Wrapper | [Finding components](finding-components.md) |
| Act on it | the wrapper's methods: `setValue`, `click`, `selectItem`, … | [Working with components](components.md), [Grid](grid.md) |
| Check the result | `assertThat(wrapper)`, also `exists()`, `doesNotExist()`, `hasCount(n)` | [Assertions and waiting](assertions-and-waiting.md) |

Every action and assertion is reported as a User Step, in the user's words. To make a test read as its scenario, and to read what it reports:

- [Structuring a scenario](structuring-a-scenario.md): `@DisplayName`, given/when/then, User Tasks with `page.step(..)`, page objects.
- [Reading reports and failures](reports-and-failures.md): the CI log, `user-steps.txt`, the run summary, the failure message with its Near Misses.
- [Running in CI](ci-setup.md): a GitHub Actions workflow that puts the run summary on the job's page, and the setup for other CI servers.

Then, when you need more:

- [When a test needs one Testing Tool](tool-specific.md): the Escape Hatch, `@RequiresBrowser`, `@RequiresTestingTool`.
- [Wrappers for your own components](custom-wrappers.md): add-ons, custom components, custom filters.
- [Checking the style](style-rules.md): scenario sentences, no raw waits and `@RequiresTestingTool`, checked by a test.

## Running it in a browser

> **Unreleased:** on `main`, not in `0.1.0-alpha3`. See [what is unreleased](../releases/unreleased.md).

The same test runs in a real browser when the run says so (`-Dvaadin.testing.tool=playwright` or `selenium`); [Running in CI](ci-setup.md) shows the jobs. Two things are set up for you:

- **The application is served in development mode**, with the frontend bundle that ships with `vaadin-dev`, so a browser run needs no frontend build first.
- **Vaadin's development tools are off** in a `@UnifiedSpringBootTest`'s application (`vaadin.devmode.devTools.enabled=false`). A test needs neither Copilot nor the tools' overlay, and with them on, Copilot starts file watchers for every browser session and keeps them until the application stops, which runs a long test run out of them. To have the tools in a test, set the property yourself; anything that sets it wins over this default:

```java
@UnifiedSpringBootTest(properties = "vaadin.devmode.devTools.enabled=true")
```

## For agents

- **In your project:** the `write-unified-ui-tests` skill, installed by the `AddUnifiedTestingSkill` recipe, and the [API reference](../../unified-test-api/src/main/resources/META-INF/unified/api-reference.md) inside the `unified-test-api` jar (`META-INF/unified/api-reference.md`), which is of the version you use and needs no network *(unreleased)*.
- **On this site:** [`llms.txt`](https://parttio.github.io/unified-testing-tool/llms.txt) lists every page with a link to it as Markdown, and [`llms-full.txt`](https://parttio.github.io/unified-testing-tool/llms-full.txt) holds this guide, the API reference and the glossary in one file.
- **After a run *(unreleased)*:** `summary.json` says what failed and where each test's files are ([The run as data](reports-and-failures.md#the-run-as-data)).

## Rules of thumb

- **Find components the way a user does:** by type and label, accessible name or text. Use a test id only when there is nothing the user can read.
- **Check state with `assertThat`**, not with getters and `assertEquals`: getters don't retry, and in a browser the update may arrive a moment later.
- **Keep Wrappers, not values.** A Wrapper finds its component again on every call, so it never goes stale: declare a field's Wrapper once, use it for the whole test.
- **Name the scenario and the user's work:** `@DisplayName` with a sentence, `page.step(..)` around a piece of user work ([Structuring a scenario](structuring-a-scenario.md)).
- **Never name a Testing Tool in a test**, unless it needs one ([When a test needs one Testing Tool](tool-specific.md)).
