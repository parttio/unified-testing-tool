---
section: Migration
---
- **TestBench removed once the Migration Markers are resolved** ([#623](https://github.com/parttio/unified-testing-tool/issues/623)):
  the `resolve-migration-markers` skill ends with a step that, when no test source still uses TestBench (a
  `com.vaadin.testbench.*` or `*.testbench.*` class, or `TestBenchEscapeHatch`, the rule of `RemoveTestBenchWhenUnused`),
  removes the direct `vaadin-testbench-*` dependencies, compiles and runs the suite again, puts them back if that fails,
  and names what it removed in its report. Before, TestBench stayed in the pom after the skill had converted the last
  page object.
