---
section: New
---
- **A Base URL per test class** ([#678](https://github.com/parttio/unified-testing-tool/issues/678)): `@BaseUrl("http://localhost:18721/app")`, or `@BaseUrl(provider = MyPool.class)` for a URL known only at run time, such as a free copy of the application from a pool, points a test class's relative navigation, path checks and `isAt(..)` at its own application on Playwright and Selenium. It wins over the Spring port and the run-wide `vaadin.testing.base-url` ([ADR 0021](../adr/0021-the-base-url-is-chosen-per-test-class-by-base-url.md), [Base URL](../guide/configuration.md#base-url)). Adapters read the resolved URL as `AdapterContext.baseUrl()`.
