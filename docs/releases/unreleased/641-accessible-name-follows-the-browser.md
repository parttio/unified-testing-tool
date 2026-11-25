---
section: Breaking changes
---
- **The accessible name is the browser's, on every Testing Tool**
  ([#641](https://github.com/parttio/unified-testing-tool/issues/641)): `withAccessibleName(..)`,
  `UnifiedElement.getAccessibleName()`, the Findable View, Near Misses and Step Targets compute it as browsers and
  Dramafinder's `getAccessibleName()` do: the `aria-labelledby` targets' texts (every id, joined with a space), else
  `aria-label`, else a field's label, `setAriaLabel(..)` or `setAriaLabelledBy(..)`, else the own text of a component
  named by its content (a button, tab, menu item, breadcrumb item, badge, notification, link or heading, without its
  icon or tooltip). Anything else has no name: `getAccessibleName()` returns `null` where it returned the text. On
  Playwright the name is read with Dramafinder's `AccessibleNames`. See
  [4. Locating](../spec/04-locating.md#filters) and [ADR 0016](../adr/0016-accessible-name-follows-the-browser.md).
  - **Migration:** `withAccessibleName("…")` on a `Div`, a `Span` or another component that only matched by its
    text becomes `withText("…")`. Where a component has both `aria-label` and `aria-labelledby`, the expected name is
    the `aria-labelledby` text. A select's name includes the value it shows (`"Plan Pro"`). A Grid named with
    `setAriaLabel(..)` has no name a lookup finds: give it a test id.
