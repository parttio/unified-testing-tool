---
section: Fixed
---
- **Plain HTML Wrappers find `Html`** ([#741](https://github.com/parttio/unified-testing-tool/issues/741)): on
  Browserless, `UnifiedDiv`, `UnifiedSpan`, `UnifiedParagraph` and the other plain HTML Wrappers matched only their
  Flow class (`Div`, `Span`, `Paragraph`..), so `new Html("<div>..</div>")` was not a `UnifiedDiv`, while a browser
  found it by its tag. They now match any component whose root element has their tag, as `UnifiedLink` does for
  `<a>`: `page.find(UnifiedDiv.class)` finds the same elements on every Testing Tool. Lookups of a `Div`, a `Span`
  and the others work as before ([spec](../spec/06-wrappers.md)).
