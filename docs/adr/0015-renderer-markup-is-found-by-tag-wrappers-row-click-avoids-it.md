---
status: accepted
---

# Renderer markup in a grid cell is found by tag Wrappers; renderer functions are no Unified call; a row click never lands on interactive content

A `LitRenderer` draws markup in a grid cell that only exists in a browser: Crud's `vaadin-crud-edit` pencil, Bakery's `order-card`. #608 asked how a Unified UI Test reaches it, whether it can run the renderer's functions (`cardClick`) for a row, and what `row.click()` does when such markup fills the cell, where Playwright refused the click and Selenium clicked without effect. Decided in #608:

- **Renderer markup is found by a Wrapper bound to its tag, in the cell's Scope:** `row.cell(0).find(UnifiedCrudEdit.class).click()`, with a project's `@UnifiedComponent(value = Component.class, tag = "vaadin-crud-edit")`. No new API: the lookup gets Auto-Wait, "exactly one", a User Step and Near Misses like any other ([ADR 0007](0007-wrappers-carry-their-filters.md)).
- **On Browserless, a lookup in the cell of a column whose renderer isn't a component renderer fails** with a message that says the markup exists only in a browser and names `@RequiresBrowser`, and the Escape Hatch for renderer functions. It fails when the cell Scope is used, never with an empty result, so `doesNotExist()` can't pass by accident. As [ADR 0012](0012-lookups-enter-templates-explicitly.md) says of client-rendered content, Browserless can't see it.
- **Running a renderer function for a row is not a Unified call.** It isn't a user action, and a browser runs it only through the template's own event handler. On Browserless, Browserless Test's `GridTester.invokeLitRendererFunction` stays reachable through the Escape Hatch; see `.out-of-scope/renderer-functions.md`.
- **`row.click()` clicks a spot of the row free of interactive content, on both browser Testing Tools.** When a row has no such spot (a clickable card fills its cells), Playwright and Selenium fail with the same message, which names `row.cell(..).find(..).click()`. A row click means "activate the row" (select it, open its details), not "click whatever is in the first cell". Browserless can't see layout, so it keeps firing the grid's item click listener.

## Considered Options

- **`element()` on a cell, or on every Scope**, then `findDescendants(tag)`: a raw element without Auto-Wait or a readable User Step, and on every Scope a change to what the page and the Findable View ([ADR 0009](0009-findable-view-in-lookups-not-aria-yaml.md)) expose.
- **A tag lookup on Scope** (`findTag("vaadin-crud-edit")`): the tag Wrapper built in, as new API on every Scope, for what a three-line interface already does.
- **`invokeRendererFunction(name, args)` on a cell, on every Testing Tool**: the same result everywhere, but browsers would call the grid connector's server function directly, skipping the markup the user clicks, and record a step no user takes.
- **A Browserless click on renderer markup that runs the function the template binds to it**, by reading the `LitRenderer`'s template on the server: real parity for the click, but it parses template strings through reflection.
- **`row.click()` clicks the first cell with a pointer on both tools**: `cardClick` runs in browsers, but a row click on a row with a button in its first column would press the button, and Browserless still wouldn't run `cardClick`.

## Consequences

- Selenium's row click no longer clicks the first cell's content blindly: like Dramafinder's, it looks for a free spot first.
- A Bakery-style storefront test clicks the card through its tag Wrapper and declares `@RequiresBrowser`.
