---
section: Fixed
---
- **Parallel tests on Playwright** ([#686](https://github.com/parttio/unified-testing-tool/issues/686)): with JUnit's
  parallel execution, tests running at the same time shared one `Playwright` and failed at random with protocol errors
  (`Object doesn't exist`, `Cannot find object to call pausedStateChanged`). Each test running at the same time now
  gets a `Playwright` and browser no other test is using, reused by later tests and all closed when the run ends; a
  sequential run still launches one browser. `openAsUser(..)` and `openNewSession()` use the test's browser
  ([spec](../spec/02-test-setup.md), [guide](../guide/ci-setup.md#parallel-tests-in-one-jvm)).
