---
status: accepted
---

# The adapter SPI is Test Runner-neutral; JUnit is one integration on top

Today the Testing Tool adapters get JUnit's `ExtensionContext` through `AdapterContext.extensionContext()`. They use it for only three things: the test class, a run-wide store that shares one browser across the run (Playwright, Selenium), and the Spring application context (`SpringExtension.getApplicationContext`). Nothing a Unified UI Test calls depends on JUnit: `UnifiedPage`, the Unified Element, the Wrappers and `UnifiedAssertions` all fail with a plain `AssertionError`. We decide that `AdapterContext` exposes these three needs as neutral methods and no longer exposes `ExtensionContext`. JUnit stays the only Test Runner we ship and the default one. We make the change now, while the project is at 0.1.0-alpha, because after 1.0 changing the SPI breaks every third-party adapter, and because Cucumber step definitions (see [BDD research](../../docs/research/bdd-for-unified-ui-tests.md)) get no Jupiter extensions even when Cucumber runs on the JUnit Platform.

## Decisions

- **`AdapterContext` loses `extensionContext()`** and gains:
  - `testClass()` and `testMethod()`: the running test, which can be empty for a Test Runner without methods, such as a Cucumber scenario.
  - `<T extends AutoCloseable> T runScoped(Class<T> key, Supplier<T> create)`: one instance per test run, closed when the run ends. It replaces `getRoot().getStore(GLOBAL).computeIfAbsent(...)`.
  - `Optional<Object> springContext()`: the Spring `ApplicationContext` the Test Runner integration has already resolved. It is typed `Object` so that the API does not depend on Spring, and each adapter's Spring-only class casts it.
  - `configuration(key)` and `timeout()` stay as they are.
- **The test lifecycle moves out of `UnifiedExtension` into a neutral core** in `unified-test-api` with three steps: decide whether to skip the test (Capabilities), open the test and get its `UnifiedPage`, and close it with an optional failure, which writes the failure evidence. `UnifiedExtension` becomes a thin JUnit layer that translates `ExtensionContext` to that core, so its behaviour does not change.
- **Settings come from a configuration source** that the Test Runner integration supplies. JUnit configuration parameters (`junit-platform.properties`) are the JUnit source, and system properties work for every Test Runner. The `vaadin.testing.*` keys do not change.
- **`@UnifiedUITest` keeps `@ExtendWith`** and stays in `unified-test-api`, with `junit-jupiter-api` declared `optional`. The JVM ignores an annotation whose class is missing at runtime, so a test written for another Test Runner can carry the same annotation without JUnit on its classpath. No user code changes.
- **No second Test Runner integration is built yet.** TestNG or Cucumber modules get added when someone asks for one; this ADR only makes them possible without an SPI break.

## Considered Options

- **Keep JUnit everywhere.** It is the Vaadin default, and Browserless Test and TestBench ship JUnit flavours. We rejected it because it leaves JUnit in the SPI, which is the one place that is expensive to change later, and blocks the BDD direction.
- **Decouple fully now and ship TestNG and Cucumber modules.** That means more modules to build and maintain with no user asking for them. The Spring part costs the most per Test Runner, because Spring's TestNG support is a base class, not an extension.
- **Pass the JUnit `ExtensionContext` as `Object`.** That hides the import but not the dependency: adapters would still cast it to JUnit's type.

## Consequences

- The Playwright, Selenium and Browserless adapters change in about a dozen lines. Their `SpringSupport` classes stop calling `SpringExtension` and use `springContext()` instead.
- `unified-test-spring` (`@UnifiedSpringBootTest`, `TransactionalCheck`) stays JUnit-only. Another Test Runner needs its own Spring integration.
- `browserless-test-junit6` stays the Browserless dependency for now. If Browserless Test publishes an artifact without JUnit, the adapter switches to it.
- The Migration Recipes are unaffected: they read and write JUnit tests on purpose.
- The spec ([02 Test setup](../spec/02-test-setup.md), [10 Packaging](../spec/10-packaging.md)) stops calling the API a "JUnit 6 extension" and lists JUnit 6 as the default Test Runner integration, not as a dependency of the Unified API.
- The lifecycle core is `TestLifecycle`, and it also reports the test's User Steps. The JUnit integration (`UnifiedExtension`, its `AdapterContext`, finding the Spring context) lives in `internal.junit`.
- The build enforces the decision: it fails on a JUnit import in the Unified API outside `internal.junit` and `@UnifiedUITest`, and in the adapters' main code. With `junit-jupiter-api` optional, the adapters do not have JUnit on their compile classpath either.

Implemented under #219: settings (#221), the neutral `AdapterContext` (#222), the adapters (#223), the lifecycle core (#224), removing `extensionContext()` (#225) and making JUnit optional (#226).
