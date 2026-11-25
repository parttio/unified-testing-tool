---
status: accepted
---

# The Findable View is written in Wrapper kinds and lookups, not in roles or Playwright's ARIA YAML

When a test fails, Playwright and Selenium write a screenshot and Browserless writes its component tree ([09. Capabilities, Failure evidence](../spec/09-capabilities.md#failure-evidence)). Neither is text a person or an agent can turn into the next lookup on every Testing Tool. #204 asked for a text view of the page, by role and name, written on every Testing Tool, and left open whether to use Playwright's ARIA snapshot YAML. We decide that the view is our own format: one line per component a Unified UI Test can find, with its kind in User Step words, its name and state, and the lookup that singles it out, run through the same Adapter SPI calls as the Near Misses.

```
text field “Name” = “Alan Turing”     page.find(UnifiedTextField.class).withLabel("Name")
button “Save” (disabled)              page.find(UnifiedButton.class).withText("Save")
grid, 3 rows: Name, Email             page.find(UnifiedGrid.class)
button (no name; test id “close”)     page.find(UnifiedButton.class).withTestId("close")
```

## Decisions

- **Kinds come from Wrappers, not roles.** A line lists what `find(Type.class)` finds, for the Wrappers of the Unified API and the custom Wrappers the test has looked up. Browserless has no accessibility tree, so roles would be a second guess there. Wrapper kinds are what the test writes and what the User Steps say.
- **Each line carries a lookup when one is unique.** The lookup uses the Wrapper's own naming filters first (`withHeaderTitle`), then label, text, accessible name, placeholder and test id. A component that none of these singles out is shown with the container that does (an ancestor's test id or id, or a listed dialog), and that lookup is run before it is printed. A lookup is never printed unchecked.
- **The view is a read for people and agents, not something to assert on.** `describe()` on the page or on any Scope returns it as a `String`. Nothing in it is a User Step, and its format may change between releases.
- **It is written on failure by the core**, as `<test>.view.txt` next to the Testing Tool's own evidence, on every Testing Tool, with no change to the Adapter SPI.
  _Amended 2026-11-21 by "Does the Findable View show each component's reach at the current Viewport?" (#487): in a browser, the view is headed by its Viewport and its lines gain the Reach words `(clipped)` and `(behind …)`, from one extra `AdapterSession.measureLayout(selectors)` round trip (the core's layout script, matched to each Wrapper type's matches by document order). The lines and their checked lookups are still built as above; Browserless shows no Reach._
  _Amended 2026-11-23 by "Findable View differs between tools" (#517): `ElementFacts` gains `internalPart()`, true for a match the application did not create but a component renders inside itself, such as the text area of a message input. The view leaves such matches out, so it lists the same components on every Testing Tool. Playwright and Selenium read it in the existing facts round trip (Flow's client knows no node for the element, but does for an ancestor in the same DOM tree); Browserless always reports false, as it finds only the application's own components. No new Adapter SPI method or round trip; a custom adapter built on the old `ElementFacts` constructor reports false._
  _Amended 2026-11-23 by "Findable View on failure is written outside the test's evidence directory" (#514): the view and the Testing Tool's evidence are written into the test's own evidence directory, next to its User Steps, as `failure.view.txt` (and `failure.png` or `failure.tree.txt`), no longer at the evidence directory's root as `<test class>.<method>.*`. The failure's `Evidence` line and `user-steps.json`'s `failure.evidence` name them. No change to the Adapter SPI: the core passes the test's directory and the name `failure`._
  _Amended 2026-11-23 by "Findable View: list plain HTML components the user clicks" (#523): plain HTML Wrappers are listed for the components with a clickable `role` or a `tabindex` of 0 or more, not for click listeners, which only Browserless can see. The Adapter SPI's `ATTRIBUTE` filter with a `null` value now means "has the attribute", so the view finds them with two queries per type (`[role]`, `[tabindex]`) and reads the values of those matches only._
  _Amended 2026-11-24 by "describe() on a grid or message list" (#534): besides the page and any Scope, a Wrapper with items describes itself: `grid.describe()` lists its columns and first rows, `messageList.describe()` its first messages, each with the lookup on that Wrapper that finds it. They read through the existing grid and message list access, the same on every Testing Tool; no change to the Adapter SPI._
  _Amended 2026-11-24 by "suggest an attribute that tells alike components apart" (#547): `ElementFacts` gains `data()`, the element's own `data-*` attributes but `data-testid`, read in the existing facts round trip (`el.attributes` in a browser, the Flow element on Browserless; empty from an adapter that doesn't read them). When neither a component's own filters nor a container single it out, its line names it and adds the first `data-*` attribute only it has, run once before it is printed. Ancestors' attributes are not used._
  _Amended 2026-11-24 by "Templates: Findable View suggests no withId for @Id components" (#609): `ElementFacts` gains `id()`, the element's own id as a browser reads it (on Browserless also an `@Id` binding's id; never Flow's `ROOT-…`), read in the existing facts round trip; a custom adapter built on the old constructors reports none. `withId(..)` joins the component's own filters: last, after test id, outside a Template, and first inside one, where ADR 0012 makes it the portable lookup. A no-name component with an id reads `button (no name; id “save”)`. No new Adapter SPI method or round trip._
  _Amended 2026-11-25 by "A lookup that throws an unexpected error fails with a bare timeout" (#611): a Wrapper kind whose lookup throws is no longer left out silently. The view ends with a line per such kind, `Not listed: link (UnsupportedOperationException: …)`, with the simple class name and the first line of the error's message, so a Testing Tool error doesn't look like a page without that kind. No Testing Tool fails to list a kind today, so no kind is left out quietly. No change to the Adapter SPI ([ADR 0017](0017-only-what-a-testing-tool-cannot-do-ends-a-wait-early.md))._

## Considered Options

- **Playwright's ARIA snapshot YAML** (`- textbox "Name"`). It is a known format and Playwright can assert on it. We rejected it because Browserless can't compute roles and accessible names the way a browser does, so the same page would read differently on two Testing Tools. It also says nothing about how to find a component in a Unified UI Test.
- **A role-based view computed by each adapter.** That needs a new Adapter SPI method that each Testing Tool implements its own way, for a view the core can already build from `find` and `facts`.
- **An assertion API like `toMatchAriaSnapshot`.** Deferred: a snapshot of a whole view breaks on any unrelated change, and the per-component checks already exist.

## Consequences

- #204's first open question, how close Browserless's roles are to a browser's, no longer blocks it. The view uses roles nowhere.
- On a failure, a browser run makes about one query per Wrapper type, plus one read of each match. That is about 20 queries for the Wrappers of the Unified API, and it happens only on failure or when asked.
- Lines come grouped by Wrapper type, not in document order: the Adapter SPI has no order across queries.
- The "Class to Wrapper, best unique filter" logic is in `FindableView`, ready for the recorder (#215) and the live session (#288).
