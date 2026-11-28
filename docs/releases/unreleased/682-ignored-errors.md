---
section: New settings
---
- **`vaadin.testing.user-steps.ignored-errors`** ([#682](https://github.com/parttio/unified-testing-tool/issues/682)): one Java regex for the run. `assertThat(page).hasNoErrors()` doesn't count an error line in which it finds a match, such as Chromium's `Failed to load resource: net::ERR_NETWORK_CHANGED` during a parallel run in Docker. It is matched against the line as printed (`console ERROR: …`, `server ERROR PoolMonitor: …`), so it can be limited to one source. The line still shows under its User Step. There is no HTTP status check yet ([ADR 0023](../adr/0023-hasnoerrors-ignores-lines-matching-one-run-wide-regex.md), [What the application logged](../guide/reports-and-failures.md#what-the-application-logged)).
