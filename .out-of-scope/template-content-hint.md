# A summary of a Template's content in the Findable View

The Findable View lists a Template as one line, `Template <storefront-view>`, with the lookup that enters it. It will not add a summary of what is inside, such as `Template <storefront-view>  (a dialog, a grid, 2 templates inside)`, and it will not list a Template's content inline under its line.

## Why this is out of scope

The hint was asked for to save the `describe()` calls it takes to walk from a page to a dialog, an editor and an item editor, one per Template level. A count doesn't save them: to learn what the two nested Templates are, or how to reach the grid, a reader still calls `describe()` on each one. Only listing every Template's content inline would save the calls, and that is the whole view again for each Template.

The view makes about one query per Wrapper type in its scope: 27 built-in Wrappers, 10 plain HTML types with two queries each, the custom Wrappers the test has looked up, and one to list the Templates. That is about 48 queries per scope, and a scope lists up to 20 Templates. Each kind of hint multiplies that:

| Hint | Extra queries per scope |
|---|---|
| nested Templates only: `(2 templates inside)` | up to 20 |
| dialogs, grids and Templates: `(a dialog, a grid, 2 templates inside)` | up to about 80 |
| each Template's content inline, 3 levels deep | hundreds |

In a browser each query is a round trip, paid on every failure and every `describe()`. The Performance Pairs' round-trip counts (spec 13) would not change, since a passing test never builds the view, but a failing Bakery test would take noticeably longer to report. A partial count is also a choice of what matters, and on a page made of Templates it is mostly noise.

What the hint was meant to help with is covered elsewhere:

- **When the test knows what it wants,** a failed lookup searches up to three Templates deep and its Near Miss gives the chained lookup that finds the component, the same on every Testing Tool ([ADR 0012](../docs/adr/0012-lookups-enter-templates-explicitly.md)):

  ```
  text field (id “filter”) in Template <demo-search-bar> in Template <demo-orders-panel>:
      findTemplate(OrdersPanel.class).findTemplate(SearchBar.class).find(UnifiedTextField.class).withId("filter")
  ```

- **When it is exploring,** `describe()` on the Template lists what is inside it, with `withId(..)` first (#609). That is one call per level, and the view stays cheap.

ADR 0012 decided that a Template is announced as one line with the lookup that enters it. The view stays that way.

## Prior requests

- #618: "Findable View: a Template's line hints at what is inside it" (from the Bakery alpha5 migration, #602, split from #609)
