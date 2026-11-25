---
section: Migration
---
- **Failsafe profiles that tested something else are marked** ([#613](https://github.com/parttio/unified-testing-tool/issues/613)):
  the TestBench recipe leaves a Migration Marker, an XML comment, on a pom profile whose Failsafe run tested a server
  or browser outside the test: one that starts the app as a native executable, with `exec` or another
  `spring-boot` goal than `start`/`stop`, one that gives Failsafe a server address, and one with TestBench grid
  (`com.vaadin.testbench.Parameters.gridBrowsers`, `hubHostname`) or Sauce Labs settings. Such a profile used to run
  green after the migration against the JVM app the test starts, on a local browser, with nothing saying so. The
  recipe changes nothing else in the profile ([ADR 0020](../adr/0020-failsafe-profiles-that-test-elsewhere-get-a-marker-no-remote-grid.md)).
  The Selenium adapter has no remote Selenium grid; the configuration guide and [2. Test setup](../spec/02-test-setup.md)
  now say so, and remote-grid support is on the roadmap ([#645](https://github.com/parttio/unified-testing-tool/issues/645)).
