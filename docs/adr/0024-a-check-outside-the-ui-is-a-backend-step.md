---
status: accepted
---

# A check outside the UI is a Backend Step: `page.backendStep(..)`

The run summary judges whether each passing scenario ends on User Steps that something checks. A test that ends by checking the database, a file or a mail does that in plain Java, which the reports can't see, so the steps before it look unchecked. In a suite migrated from raw Playwright (#688), each of the 62 workflows ended in a database check, and 61 were flagged. Because every one had also used the Escape Hatch somewhere, they were counted as "ending in Escape Hatch code", and the line pointed to Migration Markers that suite never had. Such a check can never move to `assertThat(..)`. We decide that `UnifiedPage.backendStep(name, work)` runs the test's own code outside the UI as a **Backend Step**: a new `backend` entry in the User Steps, shown as `backend: order is stored`, which ends the unchecked tail like a `waitUntil(..)` gap does.

```java
page.find(UnifiedButton.class).withText("Place order").click();
page.backendStep("order is stored", () -> assertThat(orders.findByCustomer("Alan")).hasSize(1));
```

## Decisions

- **A Backend Step is not a User Step.** It is no user action and no look at the screen, so it is neither numbered as a step nor counted in `stepCount`. It is an entry of its own, type `backend`, with an index, a name, a status, a duration and a call site, like a User Task's heading. User Steps called inside it nest under it.
- **It counts as a check wherever it is.** Like a `waitUntil(..)` gap, it may hold one: the run can't tell an assertion from setup. A setup Backend Step at the start of a test costs nothing, since the steps after it are still judged.
- **The body runs once.** Auto-Wait has already waited for the server to be idle after the last action. A result the application writes later is waited for with `waitUntil(..)`. A throw fails the entry and the test, with the failure pointer on the line that called `backendStep(..)`.
- **The run summary stops naming Migration Markers.** It can't see the test sources, and `resolve-migration-markers` already covers them. The line for unchecked scenarios that used the Escape Hatch now says what is known: they used the Escape Hatch and end on User Steps the report sees no check for. It points to `assertThat(..)` for checks of the UI and `page.backendStep(..)` for the rest. report.html keeps listing them folded.

## Considered Options

- **`page.check(name, ..)` or `page.verify(name, ..)`, as a User Step.** The verb `check` is already the checkbox Wrapper's action and `checkLayout()`'s, and neither name says what is checked: the application's backend, not its UI.
- **A retrying body**, like `see` steps. It would hide a slow write the test should wait for on purpose, and retrying an assertion with side effects (a mail read once) is unsafe.
- **Only documenting `page.waitUntil(() -> dbCheck)`.** It already counts, but it shows as an anonymous gap and reads as a wait, not a check.
- **Scanning `src/test/java` for `TODO unified:`** to name Migration Markers only when some exist. It ties the report to the source layout for one sentence of advice.

## Consequences

- [12. Readable tests](../spec/12-readable-tests.md): the entry types in `user-steps.json` and `summary.json` gain `backend`, and the check rule names it.
