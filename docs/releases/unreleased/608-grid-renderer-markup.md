---
section: Changed behaviour
---
- **Grid: markup a renderer draws, and row clicks** ([#608](https://github.com/parttio/unified-testing-tool/issues/608)):
  a `LitRenderer`'s markup in a cell, such as Crud's `vaadin-crud-edit` pencil or a clickable card, is found in a
  browser with a Wrapper bound to its tag, `row.cell(1).find(UnifiedCrudEdit.class).click()`. On Browserless, a
  lookup in such a cell now fails, naming `@RequiresBrowser` and the Escape Hatch to
  `GridTester.invokeLitRendererFunction`, instead of `Target column doesn't have a ComponentRenderer`. On Selenium,
  `row.click()` and `row.doubleClick()` now click a spot of the row free of interactive content, as on Playwright,
  rather than the first cell's content; a row that such content covers fails with the same message on both,
  naming `row.cell(..).find(..).click()`. See [ADR 0015](../adr/0015-renderer-markup-is-found-by-tag-wrappers-row-click-avoids-it.md).
