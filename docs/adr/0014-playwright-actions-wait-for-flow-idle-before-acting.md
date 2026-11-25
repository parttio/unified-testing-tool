---
status: accepted
---

# Playwright actions wait for Flow to be idle before acting, for a short while only

On Playwright, a Unified action waited for the server only after it acted. A round trip started before it, typically by an Escape Hatch click (a raw Playwright `Locator.click()` returns at once), could still be in flight, and its answer then overwrote what the action typed: Bakery's Crud editor saved the old product name (#604). TestBench waits for Vaadin around its own commands, and Browserless is synchronous, so only Playwright had the gap.

We decide that the Playwright adapter's Auto-Wait also waits, before every action, until no Flow client is busy. The check runs in the same call to the page as the visible-and-enabled check, so a test whose page is idle makes no more round trips than before. The wait is short: when Flow is still busy after the cap that `settle()` already uses for its idle window (5 s), the action goes ahead anyway instead of failing.

## Considered Options

- **A public wait for Escape Hatch code** (the issue suggested `page.waitForServer()`). No change to Auto-Wait, but the test author has to know about the race and remember the call, and the Unified API grows a call that means nothing on Browserless and repeats `waitForVaadin`, a term the GLOSSARY avoids. Rejected: the race shows up only as a test that fails on Playwright, sometimes.
- **Both.** Nothing left for the public call to fix once actions wait.
- **Busy counts as "not usable", failing at the action timeout.** Strict, but an app whose Flow client is busy most of the time (a short `@Poll` interval, a long-running request) could then no longer be driven, even though a user could still use it.

## Consequences

- Spec 05's Auto-Wait table: on Playwright, before the action is now Playwright actionability **and** Flow idle (capped).
- An action that acts after the cap may still race the busy request. The cap only bounds how long a busy app slows each action down.
