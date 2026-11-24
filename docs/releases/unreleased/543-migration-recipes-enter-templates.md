---
section: New
---
- **The Migration Recipes enter Templates** ([#543](https://github.com/parttio/unified-testing-tool/issues/543)): the
  TestBench recipe turns `$(SearchBarElement.class)`, or `$("search-bar")`, into `page.findTemplate(SearchBar.class)`
  when a `LitTemplate` in the sources has that `@Tag`, and the lookups on its element into lookups inside it, with a
  Migration Marker on a text or label filter there. TestBench's `id("x")` becomes `withId("x")` everywhere. The
  Browserless recipe turns a Component Reference to a `LitTemplate`'s `@Id` field, such as
  `test(view.panel.search.newOrder).click()`, into `page.findTemplate(OrdersPanel.class).findTemplate(SearchBar.class)
  .find(UnifiedButton.class).withId("new").click()`.
