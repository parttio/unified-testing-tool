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

The reports can't see what runs inside the Escape Hatch, so they show a gap line where the test used it, instead of User Steps:

```
   2  fill text field “Name” with “Bob”
   … 1 call outside the Unified API (Escape Hatch)
```

## Declaring the Capability

`@RequiresTestingTool` is what makes the test skip, not fail, on the other Testing Tools. The opt-in style rule `TOOL_CODE_NEEDS_CAPABILITY` checks that every test reaching a Testing Tool's own code, directly or through a helper in its class, declares it; `tryUnwrap(..)` needs no annotation ([Checking the style](style-rules.md)).

## Migrated TestBench tests

A test migrated from TestBench can keep TestBench's test base API (`$(..)`, `executeScript`, `waitUntil`) on Selenium with `TestBenchEscapeHatch.of(page)` and `@RequiresTestingTool(SELENIUM)`, while the rest of it moves to the Unified API ([ADR 0004](../adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md)).
