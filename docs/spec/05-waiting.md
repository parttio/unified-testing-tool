# 5. Waiting

Decided in [#8](https://github.com/parttio/unified-testing-tool/issues/8); lookups and checks as in [ADR 0007](../adr/0007-wrappers-carry-their-filters.md). Term: **Auto-Wait**.

## Auto-Wait

Every action (`click`, `setValue`, `navigate`, …) on every Testing Tool:

```mermaid
sequenceDiagram
    participant T as Test
    participant W as Wrapper
    participant A as App
    T->>W: click()
    W->>W: wait: exactly one match, visible, enabled
    W->>A: user action
    W->>A: wait: server round-trip finished
    W->>A: wait: address bar updated (browsers)
    W-->>T: returns
```

| Testing Tool | Before the action | After the action |
|---|---|---|
| Browserless | by construction | by construction (synchronous) |
| Playwright (Dramafinder) | Playwright actionability | **adapter adds** wait for Vaadin idle |
| Selenium (TestBench) | **adapter adds** element-state wait | `waitForVaadin` |

**Address bar.** A server-side navigation (`ui.navigate(..)` in a listener) changes the address bar after Flow has handled the response: in a timeout, or in React mode through React Router while Flow's `Vaadin.Flow.navigation` flag is set. The round-trip wait is over by then. So after every action, the browser adapters also wait for that change (`AdapterSession.settle()`: one timeout, then while the flag is set, at most 2 s, then one more timeout, then the round trip the navigation may start). A one-shot read of the URL right after an action, through the Escape Hatch or a report, sees the new path ([#233](https://github.com/parttio/unified-testing-tool/issues/233)). The flag is Flow's internal, not an API.

Grid row and column actions (`row().withCellText(..).click()`, `column(..).sort(..)`) are Wrapper actions and get the same Auto-Wait ([7. Grid](07-grid.md)).

## Lazy Wrappers

`find(..)` returns a Wrapper that finds its component again on every action, read or assertion, and waits up to the timeout for exactly one match. It waits for components that appear later and never goes stale. A Wrapper never holds a component instance.

## Assertions and getters

| | Retries | Use |
|---|---|---|
| `assertThat(wrapper).hasValue("x")` | yes, until the timeout; failure shows expected and actual | checking state (recommended) |
| `wrapper.getValue()` | no (read after the round-trip) | reading a value to use it |

```java
assertThat(name).hasValue("Alice").isValid();          // good
assertEquals("Alice", name.getValue());                 // flaky in a browser after async updates
```

## Counts and absence

Whether a component is there, and how many are, are retrying assertions like any other. There is no boolean check:

| Assertion | Holds when |
|---|---|
| `assertThat(wrapper).exists()` | at least one component matches |
| `assertThat(wrapper).doesNotExist()` | no component matches |
| `assertThat(wrapper).hasCount(n)` | `n` components match |

```java
assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist();
assertThat(users.row().withCellTextContaining("Email", "user11")).hasCount(11);
```

`all()` reads the matches now, without waiting: only for looping over them ([4. Locating](04-locating.md#checks-and-several-matches)).

## Failures

A lookup that finds no component or several, an action on a component that stays disabled or hidden, and an assertion that times out all throw `UnifiedAssertionError`, an `AssertionError`. Its message is the same on every Testing Tool: the User Step that failed, what was expected and found, the Near Misses, how long it waited and where the timeout came from ([12. Readable tests, Failures](12-readable-tests.md#failures)). An assertion timeout lists the distinct values it saw while waiting (`Found “Saving…”, then “Saved Alan Turing”`).

## Background work

On Browserless, retrying assertions and waits run `roundTrip()` and `runPendingSignalsTasks()` between attempts, so views updated from background threads (`UI.access`, signals) behave the same as in a browser.

## Timeouts and generic waits

- One default timeout, `10s`, set with `vaadin.testing.timeout`.
- Per call: `wrapper.withTimeout(Duration.ofSeconds(30))`.
- Any other condition: `page.waitUntil(() -> …)`, same timeout and Browserless flushing.

## Turning Auto-Wait off

Not part of the Unified API. Use the Escape Hatch and the tool's own switch (e.g. TestBench `disableWaitForVaadin()`). Not possible on Browserless.

## Requirements on Testing Tool adapters

- Guarantee both halves of Auto-Wait for every action, including the address bar change of a server-side navigation (`settle()`).
- Re-resolve Wrappers on every call, and count the matches for `exists()`, `doesNotExist()` and `hasCount(n)`.
- Retry assertions until the timeout; on Browserless flush `UI.access` tasks and signals between attempts.
- Give grid row and column actions the same Auto-Wait as every other action.
