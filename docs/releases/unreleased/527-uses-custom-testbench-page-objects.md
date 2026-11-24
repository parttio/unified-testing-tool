---
section: Fixed
---
- **Uses of custom TestBench page objects are marked and run on Selenium only**
  ([#527](https://github.com/parttio/unified-testing-tool/issues/527)): a statement calling an application's own
  `TestBenchElement` subclass, such as `loginView.login(..)` on a `LoginViewElement extends LoginOverlayElement`,
  gets the Migration Marker `TestBench page object: LoginViewElement (custom element extending LoginOverlayElement):
  write a Wrapper or convert the page object`. A test reaching one, itself or through a helper of its class hierarchy,
  gets `@RequiresTestingTool(SELENIUM)`, as does a test calling only an inherited helper that goes through the Escape
  Hatch. `FindTestClassesToMigrate` lists the page objects and custom elements of each TestBench class, each
  `converts with includePageObjects` or `stays TestBench (Escape Hatch)` with the reason, in a new `Page objects`
  column, and a class using one that stays TestBench is `migrates partly`.
