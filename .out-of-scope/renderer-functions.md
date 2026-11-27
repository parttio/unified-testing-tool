# Running a LitRenderer function for a grid row

The Unified API has no call that runs a `LitRenderer`'s function (such as a card's `cardClick`) for a grid row, and it will not get one.

## Why this is out of scope

A renderer function isn't something a user does. In a browser it runs only when the template's own event handler calls it, after a click or a key press on the markup the renderer draws. A Unified call that ran it on Playwright or Selenium would have to call the grid connector's server function directly, skipping the markup the user actually clicks, and the User Steps would report a step no user takes. Only Browserless Test can run the function on its own (`GridTester.invokeLitRendererFunction`), so a Unified call would be a Browserless call with a browser imitation next to it. [ADR 0015](../docs/adr/0015-renderer-markup-is-found-by-tag-wrappers-row-click-avoids-it.md) records the decision.

## What to do instead

- **In a browser,** click the markup the way a user does, with a Wrapper bound to its tag in the cell's Scope: `row.cell(0).find(OrderCard.class).click()`, and declare `@RequiresBrowser`. On Browserless, that lookup fails and says so.
- **On Browserless,** call `GridTester.invokeLitRendererFunction(row, column, "cardClick")` through the Escape Hatch, in a test that declares `@RequiresTestingTool(BROWSERLESS)`.

## Prior requests

- #608: "Grid: reach LitRenderer content in a cell (headerless column, renderer functions on Browserless)"
