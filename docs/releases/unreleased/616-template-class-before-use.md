---
section: New
---
- **A Template's lookup names its class before the test uses it**
  ([#616](https://github.com/parttio/unified-testing-tool/issues/616)): the Findable View and the Near Misses now
  print `page.findTemplate(SearchBar.class)` for a `<search-bar>` Template from the first failure, instead of
  `findTemplate(<the class with @Tag("search-bar")>.class)`. The class is the only one in the application's packages
  that declares the tag. Those packages are the test's package, the `@SpringBootApplication`'s, the
  `vaadin.testing.browserless.view-packages` and the opened routes' packages, and they're scanned the first time a
  tag needs a class. The lookup is the same on every Testing Tool. When no class there declares the tag, or several
  do, the placeholder stays. `unified-test-api` now depends on ClassGraph, which Browserless Test already brings
  ([ADR 0017](../adr/0017-template-class-from-the-application-packages.md)).
