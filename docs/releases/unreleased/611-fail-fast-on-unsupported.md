---
section: Changed behaviour
---
- **What a Testing Tool can't do fails at once, and every failure names its error**
  ([#611](https://github.com/parttio/unified-testing-tool/issues/611)): a lookup, action, assertion or `waitUntil(..)`
  whose attempt throws `UnsupportedOperationException`, such as a lookup inside a LitRenderer's cell on Browserless,
  fails at the first attempt instead of after the timeout. Other errors are still retried until the timeout. The
  failure names the error its last attempt threw in a new `Cause` line (`Cause  UnsupportedOperationException: …`),
  and `user-steps.json` has it as `failure.cause`. The Findable View ends with `Not listed: link (…)` for a kind whose
  lookup threw, instead of leaving it out. The same on every Testing Tool
  ([ADR 0017](../adr/0017-only-what-a-testing-tool-cannot-do-ends-a-wait-early.md)).
