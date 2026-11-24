---
section: New
---
- **The Findable View suggests `withId(..)`** ([#609](https://github.com/parttio/unified-testing-tool/issues/609)):
  a component that has an id gets `withId("…")` when nothing else of its own singles it out, and inside a Template
  (entered with `findTemplate` or a `TemplateScope` Wrapper) the id comes first, as it is the lookup that works there
  on every Testing Tool. On Browserless, a Template's `@Id` button whose text the template writes now reads
  `button (no name; id “save”)  find(UnifiedButton.class).withId("save")` instead of `not unique: 5 alike`. Flow's
  `ROOT-…` id is never suggested. A custom adapter fills the id in with the new `ElementFacts` constructor; the
  older ones report none. The guide and spec 04 now also say that a Template's `element()` is its host, whose
  `findDescendants(..)` stays in the light DOM ([Templates](../spec/04-locating.md#templates)).
