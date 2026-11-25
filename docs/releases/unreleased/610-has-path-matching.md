---
section: New
---
- **Check a path the test only partly knows** ([#610](https://github.com/parttio/unified-testing-tool/issues/610)):
  `assertThat(page).hasPathMatching("products/\\d+")` passes when the page's path matches the regular expression as
  a whole, without a leading `/` and the query string, such as after opening an item whose id the test doesn't
  know. It retries until the timeout like `hasPath(..)`, on every Testing Tool, and reads `see path matching
  “products/\d+”` in the User Steps. A `java.util.regex.Pattern` overload takes flags. See
  [3. Navigation](../spec/03-navigation.md) and [ADR 0017](../adr/0017-the-page-path-is-checked-not-read.md).
