---
section: Fixed
---
- **Navigation the TestBench recipe can't convert is marked** ([#607](https://github.com/parttio/unified-testing-tool/issues/607)):
  every `get(..)` on a `WebDriver` that the recipe keeps gets a Migration Marker suggesting `page.navigate("<path>")`,
  whatever the URL's form: a helper's parameter (`driver.get(url)`), a local, a method call or a field. In a class
  that becomes a `@UnifiedSpringBootTest`, a field holding a local application URL with a fixed port, such as
  `APP_URL = "http://localhost:8080/"`, gets one too. Such a navigation used to stay unmarked and kept testing the
  server the build starts, not the app the test starts on a random port.
- **`testBench` declared when a test passes the driver to a plain method** ([#607](https://github.com/parttio/unified-testing-tool/issues/607)):
  a TestBench test calling `open(getDriver(), url)`, where `open` makes no TestBench call, now declares the
  `TestBenchEscapeHatch testBench` its migrated `open(testBench.getDriver(), url)` reads, so it compiles.
