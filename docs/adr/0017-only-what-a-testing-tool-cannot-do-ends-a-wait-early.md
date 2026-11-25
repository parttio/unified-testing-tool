---
status: accepted
---

# Only what a Testing Tool can't do ends a wait early; every failure names the error its last attempt threw

Every wait of the Unified API, whether a lookup, Auto-Wait, a retrying assertion or `waitUntil`, runs through `TestRun.pollFor`. It treated every `RuntimeException` from an attempt as "not yet". A deterministic adapter error therefore cost the whole timeout and reached the reader only as the Java cause: on Browserless, #603 made every `UnifiedLink` lookup throw `UnsupportedOperationException`, and the test waited 10 s, then failed with a bare `Expected link … to exist`. Tests that checked such an error had to shorten the timeout to `withTimeout(500ms)` to avoid the wait. Decided in #611:

- **`UnsupportedOperationException` ends a wait at the first attempt**, everywhere `pollFor` waits, including `waitUntil(..)`. In the Unified API and its adapters it means "this Testing Tool can't do that" (`@RequiresBrowser`, a LitRenderer's markup on Browserless, an access interface a Testing Tool doesn't provide), and waiting never changes it. It fails as a `UnifiedAssertionError`, with the failed User Step, the Near Misses and the evidence as usual, and without a `Waited` line.
- **Every other exception is still retried until the timeout**: stale elements, Playwright and Selenium errors during navigation, and `IllegalStateException` and `IllegalArgumentException`, which the adapters also throw for "not yet" (`Row 3 was not rendered`, `No item "Bob"; there are …`).
- **The failure names the error its last attempt threw**, of any type, in a `Cause` line after `Did you mean` (`Cause  UnsupportedOperationException: …`, the simple class name and the first line of the message) and in `user-steps.json` as `failure.cause`. It stays the Java cause too.
- **An adapter throws `UnsupportedOperationException` only for what its Testing Tool can never do**, and something else for what may change while the test waits ([5. Waiting](../spec/05-waiting.md#requirements-on-testing-tool-adapters)).
- **The Findable View names a Wrapper kind whose lookup threw**, rather than leaving it out ([ADR 0009](0009-findable-view-in-lookups-not-aria-yaml.md), amended).

## Considered Options

- **A wider fatal list** (`UnsupportedOperationException`, `ClassCastException`, `IllegalArgumentException`): `No visible column at index 2` would fail at once, but a grid whose items are still loading (`IllegalArgumentException: No item …`) would fail where it passes today.
- **A dedicated Adapter SPI exception for "final"**: precise, but every adapter would have to adopt it, and a bug that throws a plain `UnsupportedOperationException`, as #603 did, would still wait out the timeout.
- **No fail fast, only the `Cause` line**: the safest choice, but every such failure keeps costing the whole timeout.

## Consequences

- A test that waited out the timeout on what its Testing Tool can't do now fails at once. A test that relied on such a wait passing later, because the error went away, now fails. In the Browserless and Playwright suites of this repository, every `UnsupportedOperationException` an attempt threw was deterministic.
- `IllegalArgumentException: No visible column at index 2` still waits out the timeout, now with its `Cause` line.
- A test that checked such an error needs no shorter timeout any more.
