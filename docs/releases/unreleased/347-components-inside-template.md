---
section: New
---
- **Components inside a Template** ([#347](https://github.com/parttio/unified-testing-tool/issues/347),
  [ADR 0012](../adr/0012-lookups-enter-templates-explicitly.md)): a test enters a `LitTemplate` with
  `findTemplate(SearchBar.class)` on any Scope, or with a Wrapper that extends `TemplateScope`, and finds its `@Id`
  components inside, on every Testing Tool: `page.findTemplate(SearchBar.class).find(UnifiedButton.class).withId("new")`.
  No lookup enters a shadow root otherwise. On Browserless, an `@Id` component's id now reads as its `id`, as in a
  browser. A lookup that finds nothing names the Templates in which it would, with the lookup that enters them, and the
  Findable View lists each Template. Text written in the template's HTML is browser-only; a web component that renders
  its content in the browser needs `@RequiresBrowser`. The Adapter SPI gains `QuerySpec.inTemplate()`, which
  keeps its four-argument constructor, and `AdapterSession.findTemplates(..)`, which finds none by default.
