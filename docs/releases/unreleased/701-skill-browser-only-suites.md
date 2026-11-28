---
section: Changed behaviour
---
- **The `write-unified-ui-tests` skill covers browser-only suites** ([#701](https://github.com/parttio/unified-testing-tool/issues/701)):
  for a suite that runs only in a browser against a deployed application (no Spring Boot, no Browserless, Failsafe),
  the skill now proves a test on the Testing Tools it allows rather than always on Browserless and Playwright, runs
  it with the plugin that runs the suite (`mvn verify -Dit.test=..`), sets `vaadin.testing.base-url` for the deployed
  application, and finds `user-steps.txt` through `vaadin.testing.evidence-dir` rather than a fixed Surefire path.
