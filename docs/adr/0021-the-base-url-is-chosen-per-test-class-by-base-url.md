---
status: accepted
---

# A test class chooses its Base URL with `@BaseUrl`, a fixed URL or a provider, and the Unified API resolves it

A suite that is not Spring Boot (Vaadin Flow on Payara) runs several copies of the application, and a pool hands each test class a free copy at run time. The Base URL came only from the Spring port or the run-wide `vaadin.testing.base-url`, so relative `navigate(..)`, the path checks, `isAt(..)` and the Findable View all resolved against the wrong server (#678). Decided in #678:

- **`@BaseUrl` on the test class**, `@Inherited`, and found on an enclosing class for a `@Nested` test. It takes either a fixed URL, `@BaseUrl("http://localhost:18721/app")`, or `@BaseUrl(provider = Pool.class)`. `Pool` implements `BaseUrlProvider`, `String baseUrl(Class<?> testClass)`, has a no-argument constructor, and is called each time a test's session opens, with the running test class. Setting both or neither fails the test and names the class.
- **One ranking for every browser Testing Tool:** `@BaseUrl`, then the port of the test's Spring web server, then `vaadin.testing.base-url`, then `http://localhost:8080`, without a trailing `/`. The most specific source wins. Playwright and Selenium both use it, and Browserless, which has no URL, ignores it.
- **`AdapterContext.baseUrl()` returns the resolved URL.** It is a default method on the Test Runner-neutral SPI (ADR 0008), built from `testClass()`, `springContext()` and `configuration(..)`. The adapters no longer read the Spring port or the setting themselves.

## Considered Options

- **A fixed `@BaseUrl` string only:** simpler, but it can't express a port handed out at run time, which is the case that raised the issue.
- **A run-wide `ServiceLoader` SPI:** nothing on the tests, but the choice isn't visible from the test and is hard to scope to a class.
- **Each adapter reads the annotation through `testClass()`:** no SPI change, but the lookup, the `@Nested` rule, the provider call and the ranking would be copied into each adapter, including third-party ones. The Spring-port lookup was already copied twice.
- **The Spring port wins over `@BaseUrl`:** that would make the annotation useless on a Spring test, where the explicit choice should win.

## Consequences

- A provider that leases app copies is called once per test, not once per class; it caches per class itself if it needs to, and the Unified API has no hook to release a lease.
- The provider's exception fails the test before its body runs.
