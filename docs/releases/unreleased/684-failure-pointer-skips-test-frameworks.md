---
section: Fixed
---
- **A failing JUnit or AssertJ assertion points at the test's line** ([#684](https://github.com/parttio/unified-testing-tool/issues/684)):
  the failure's code path named JUnit's own frame (`AssertionFailureBuilder.java:158 (build)`) as the line inside the
  step. Frames of `org.junit.`, `org.opentest4j.`, `org.assertj.` and `org.hamcrest.` are now skipped, so it names the
  line of the failing assertion. The outer pointer still names a base-class helper that forwards to `page.step(..)`.
