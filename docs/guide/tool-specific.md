# When a test needs one Testing Tool

Most tests run everywhere. Some need what only one Testing Tool has: a real browser's CSS, the server-side Java state, the tool's own API. Spec: [8. Escape Hatch](../spec/08-escape-hatch.md), [9. Capabilities](../spec/09-capabilities.md).

| The test… | Use |
|---|---|
| only makes sense in a real browser (CSS, screenshots, rendered text) | `@RequiresBrowser` |
| needs one tool's own API | `@RequiresTestingTool(TestingTool.PLAYWRIGHT)` + `unwrap(..)` |
| runs everywhere, with an extra check on one tool | `tryUnwrap(..)`, no annotation |
| runs everywhere, with a browser-only step | `if (page.hasRealBrowser()) { … }` |

On another Testing Tool, a test with `@RequiresBrowser` or `@RequiresTestingTool` is **skipped**, not failed.

## A real browser

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:needsARealBrowser"
```

An optional browser-only step:

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:screenshotOnlyWithARealBrowser"
```

## The Escape Hatch

`unwrap(Type.class)` gives the Testing Tool's own object behind a Wrapper or a page:

| Testing Tool | From a Wrapper | From `UnifiedPage` |
|---|---|---|
| Browserless | the Flow `Component` (e.g. `TextField`), its tester | `BrowserlessUIContext` |
| Playwright | Dramafinder element (e.g. `TextFieldElement`), `Locator` | `Page` |
| Selenium | TestBench element (e.g. `TextFieldElement`), `WebElement` | `WebDriver` |

Server-side state, on Browserless only:

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:readsServerSideState"
```

An extra check where it can be made, while the test runs everywhere:

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:escapeHatchIsOptional"
```

Without the annotation, `unwrap(..)` on a tool that doesn't have the type fails with a message naming `@RequiresTestingTool`.

!!! warning
    Inside the Escape Hatch, the Unified API guarantees stop: no Auto-Wait, no lazy lookup, no retrying assertions, no User Steps. Go back to Wrappers and `assertThat` as soon as you can.

### After an Escape Hatch action

An Escape Hatch action that reaches the server, such as a click through Playwright's own `Locator`, returns before the server has answered. Here the click opens Alan Turing in the editor, and the server loads the person into the form a moment later:

```java
page.unwrap(Page.class).getByText("Alan Turing").click();   // the server loads the person into the form...
name.setValue("Alan M. Turing");                             // ...and Auto-Wait waits for that before typing
```

The next Unified action waits for that answer before it acts, on every Testing Tool: TestBench waits for the server around its own commands, and on Playwright Auto-Wait waits until Flow is idle, for at most 5 s, then acts anyway ([5. Waiting](../spec/05-waiting.md#auto-wait), [#604](https://github.com/parttio/unified-testing-tool/issues/604)). Before that, on Playwright, the answer could arrive after the next action and overwrite it.

Escape Hatch code that comes next, reading the page itself, gets no such wait, and neither does an action after an answer that takes longer than 5 s. Make it wait in one of two ways:

- **Check the state the server produces.** An assertion retries until the server's answer is there, on every Testing Tool. Prefer this wherever the answer changes something the user sees:

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/EscapeHatchRoundTripTest.java:assertTheServersValue"
    ```

- **Wait for the server inside the Escape Hatch**, with Dramafinder's `AutoWait.waitForVaadinIdle(Page)`, when the answer has nothing to check. It is Playwright's code, so the test declares `@RequiresTestingTool(TestingTool.PLAYWRIGHT)`:

    ```java
    --8<-- "unified-test-demo/src/test/java/demo/EscapeHatchRoundTripTest.java:waitForVaadinIdle"
    ```

Playwright's `Locator.waitFor()` waits for an element's state, not for the server: it doesn't help here.

The reports can't see what runs inside the Escape Hatch, so they show a gap line where the test used it, instead of User Steps:

```
   2  fill text field “Name” with “Bob”
   … 1 call outside the Unified API (Escape Hatch)
```

### Keeping an unwrapped object

The gap line counts each time the test calls `unwrap(..)`, not each time it uses what came back. Work done later through a kept `Page` or `WebDriver` isn't seen:

```java
Page playwright = page.unwrap(Page.class);    // the gap line, once
page.navigate(ProfileView.class);
playwright.getByText("Alan Turing").click();  // not seen
playwright.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();  // not seen
```

```
   … 1 call outside the Unified API (Escape Hatch)
   1  open view “Profile”
```

The User Steps after it may look empty, though the test did work there, and the run summary may count the scenario as [without a check](reports-and-failures.md#scenarios-without-a-check).

To have each use show, unwrap where the code uses it:

```java
page.navigate(ProfileView.class);
page.unwrap(Page.class).getByText("Alan Turing").click();
page.unwrap(Page.class).getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
```

```
   1  open view “Profile”
   … 2 calls outside the Unified API (Escape Hatch)
```

A test migrated by the [Dramafinder recipe](../migrating-from-dramafinder.md#what-it-leaves-for-you) keeps one `Page` in a local like the first example. That is fine as a first step: the test runs, and the gap line says the Escape Hatch is there. As you rewrite its calls with Wrappers, the User Steps fill in.

## Declaring the Capability

`@RequiresTestingTool` is what makes the test skip, not fail, on the other Testing Tools. The opt-in style rule `TOOL_CODE_NEEDS_CAPABILITY` checks that every test reaching a Testing Tool's own code, directly or through a helper in its class, declares it; `tryUnwrap(..)` needs no annotation ([Checking the style](style-rules.md)).

## Migrated TestBench tests

A test migrated from TestBench can keep TestBench's test base API (`$(..)`, `executeScript`, `waitUntil`) on Selenium with `TestBenchEscapeHatch.of(page)` and `@RequiresTestingTool(SELENIUM)`, while the rest of it moves to the Unified API ([ADR 0004](../adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md)).
