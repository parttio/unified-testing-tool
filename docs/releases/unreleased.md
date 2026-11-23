# Unreleased

What is on `main` and not in a release yet: the changes since [0.1.0-alpha4](0.1.0-alpha4.md). The documentation
site describes `main`, so everything below is in the guide and the specification but not in `0.1.0-alpha4`. This
page becomes the notes of the next alpha.

## New

- **One message of a chat** ([#515](https://github.com/parttio/unified-testing-tool/issues/515)):
  `UnifiedMessageList.message(i)` (0-based) and `message()` with `withUserName(..)`, `withText(..)`,
  `withTextContaining(..)` and `last()` find one message lazily, as `grid.row()` finds a row. Its checks,
  `exists()`, `doesNotExist()`, `hasCount(n)`, `hasText(..)`, `hasTextContaining(..)` and `hasUserName(..)`, wait
  for a reply that comes later and are User Steps:
  `see last message from “Assistant” with text containing “Pancakes” in message list`.
- **The Findable View counts a message list's messages**: `message list, 3 messages`, as it counts a grid's rows.
- **The Dramafinder recipe opens a view by its route class after `getUrl()` too**
  ([#513](https://github.com/parttio/unified-testing-tool/issues/513)): `page.navigate(getUrl() + "/plan")` becomes
  `page.navigate(PlanView.class)`, and `page.navigate(getUrl() + "/plan?week=" + monday)` becomes
  `page.navigate(PlanView.class, QueryParameters.of("week", String.valueOf(monday)))`, when exactly one `@Route`
  among the migrated sources has the path, as `getView()` does since 0.1.0-alpha4. A renamed route then breaks at
  compile time, and the User Step reads `open Plan` instead of `open “plan”`. Any other URL, such as one with two
  query parameters, still becomes the path after the base URL.
