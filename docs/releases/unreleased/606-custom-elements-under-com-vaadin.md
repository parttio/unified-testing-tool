---
section: Fixed
---
- **Page objects of an application under `com.vaadin.*`** ([#606](https://github.com/parttio/unified-testing-tool/issues/606)):
  a `TestBenchElement` subclass is TestBench's own only in `com.vaadin.testbench.*` or a component's
  `com.vaadin.flow.component.**.testbench` package, so the page objects of an application such as Bakery
  (`com.vaadin.starter.bakery`) are custom elements: the dry run lists them and reports their classes `migrates partly`,
  each statement calling one gets the `TestBench page object: ...` Migration Marker, a `$(X.class)` lookup of one says
  it is a custom element, and the tests reaching one get `@RequiresTestingTool(SELENIUM)`. A statement calling a page
  object gets its marker even when it already has another one, such as the sign-in marker.
