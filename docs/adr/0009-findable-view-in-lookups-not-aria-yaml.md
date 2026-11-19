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

## Considered Options

- **Playwright's ARIA snapshot YAML** (`- textbox "Name"`). It is a known format and Playwright can assert on it. We rejected it because Browserless can't compute roles and accessible names the way a browser does, so the same page would read differently on two Testing Tools. It also says nothing about how to find a component in a Unified UI Test.
- **A role-based view computed by each adapter.** That needs a new Adapter SPI method that each Testing Tool implements its own way, for a view the core can already build from `find` and `facts`.
- **An assertion API like `toMatchAriaSnapshot`.** Deferred: a snapshot of a whole view breaks on any unrelated change, and the per-component checks already exist.

## Consequences

- #204's first open question, how close Browserless's roles are to a browser's, no longer blocks it. The view uses roles nowhere.
- On a failure, a browser run makes about one query per Wrapper type, plus one read of each match. That is about 20 queries for the Wrappers of the Unified API, and it happens only on failure or when asked.
- Lines come grouped by Wrapper type, not in document order: the Adapter SPI has no order across queries.
- The "Class to Wrapper, best unique filter" logic is in `FindableView`, ready for the recorder (#215) and the live session (#288).
