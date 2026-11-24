---
section: New
---
- **The Dramafinder recipe opens a view by its route class after `getUrl()` too**
  ([#513](https://github.com/parttio/unified-testing-tool/issues/513)): `page.navigate(getUrl() + "/plan")` becomes
  `page.navigate(PlanView.class)`, and `page.navigate(getUrl() + "/plan?week=" + monday)` becomes
  `page.navigate(PlanView.class, QueryParameters.of("week", String.valueOf(monday)))`, when exactly one `@Route`
  among the migrated sources has the path, as `getView()` does since 0.1.0-alpha4. A renamed route then breaks at
  compile time, and the User Step reads `open Plan` instead of `open “plan”`. Any other URL, such as one with two
  query parameters, still becomes the path after the base URL.
