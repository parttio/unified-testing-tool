---
section: Fixed
---
- **Selenium opens and recognises a route with an optional parameter or an alias**
  ([#530](https://github.com/parttio/unified-testing-tool/issues/530)): `page.navigate(StorefrontView.class)` on
  `@Route("storefront/:orderID?")` opens `storefront`, as on the other Testing Tools, not `storefront/:orderID`.
  `assertThat(page).isAt(view)` matches the route's template and each `@RouteAlias` of the view on Selenium and
  Playwright: a required `:x` matches one segment, an optional `:x?` zero or one, a wildcard `:x*` the rest.
  `navigate(view)` on a route with a required parameter fails with a `UnifiedAssertionError` naming the
  parameter, on every Testing Tool, instead of a server error.
