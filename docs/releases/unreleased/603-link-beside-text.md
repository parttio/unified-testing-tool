---
section: Fixed
---
- **Links beside plain text on Browserless** ([#603](https://github.com/parttio/unified-testing-tool/issues/603)):
  `page.find(UnifiedLink.class)`, and any Wrapper declared `@UnifiedComponent(value = Component.class, tag = "…")`,
  no longer throws `UnsupportedOperationException` on a page that holds a Flow `Text` component, such as the text
  `add(String)` makes. The text is never a match, and the Findable View lists the page's links again.
