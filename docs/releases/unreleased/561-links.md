---
section: New
---
- **Links** ([#561](https://github.com/parttio/unified-testing-tool/issues/561)): `page.find(UnifiedLink.class)` finds a
  `RouterLink`, an `Anchor` or any other `<a>`, by text, accessible name or `withHref(..)`. `click()` follows the link
  as a user's click does on every Testing Tool, where `findComponent(RouterLink.class).element().click()` followed it
  in a browser only: on Browserless, a link to a route of the application navigates to it, and one that leaves the
  application or opens another window fails asking for `@RequiresBrowser`. The User Steps read `click link “Users”`,
  then `open “users”`. `getHref()` and `assertThat(link).hasHref(..)` read the `href` as the application set it. The
  TestBench recipe turns `$(AnchorElement.class)` into `page.find(UnifiedLink.class)`. **Adapter SPI:** `LinkAccess`,
  which an adapter whose click doesn't follow a link provides; a lookup on Browserless for a class without a `@Tag` of
  its own and an explicit `@UnifiedComponent(tag = ..)` now also matches the tag, as a browser does.
