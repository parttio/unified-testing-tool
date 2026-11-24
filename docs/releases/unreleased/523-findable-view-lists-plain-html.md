---
section: New
---
- **The Findable View lists plain HTML the user can tell is clickable**
  ([#523](https://github.com/parttio/unified-testing-tool/issues/523)): a `div`, `span`, `article` and the other
  plain HTML Wrappers' components with a `role` such as `button` or `tab`, or `tabindex` 0 or more:
  `span “Week of 2026-11-16” (role button)  page.find(UnifiedSpan.class).withText("Week of 2026-11-16")`. A click
  listener alone doesn't count. **Adapter SPI:** an `ATTRIBUTE` `Filter` with a `null` value matches an element that
  has the attribute; a custom adapter that applies `ATTRIBUTE` filters itself handles it.
