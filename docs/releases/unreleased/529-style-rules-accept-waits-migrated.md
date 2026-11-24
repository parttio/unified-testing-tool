---
section: Fixed
---
- **The style rules accept the waits migrated TestBench code keeps**
  ([#529](https://github.com/parttio/unified-testing-tool/issues/529)): `UnifiedStyleRules.NO_RAW_WAITS` allows a
  Testing Tool's own wait API, such as `waitForFirst()` or `TestBenchEscapeHatch.waitUntil(..)`, in code that
  declares `@RequiresTestingTool` for that Testing Tool: the test method, its class or an enclosing class, or a
  helper that only such tests reach. Escape Hatch code gets none of the Unified API's waiting. `Thread.sleep(..)`,
  Awaitility, another Testing Tool's waits, and waits in a helper a test without the annotation also reaches still
  fail; for a wait in code that declares another Testing Tool, the message names the one it belongs to.
