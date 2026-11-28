# 2. Test setup

Decided in [#9](https://github.com/parttio/unified-testing-tool/issues/9) (choosing the Testing Tool), [#15](https://github.com/parttio/unified-testing-tool/issues/15) and [#25](https://github.com/parttio/unified-testing-tool/issues/25) (reaching the app), proven in [#24](https://github.com/parttio/unified-testing-tool/issues/24).

## Marking a test

| Annotation | Use for |
|---|---|
| `@UnifiedUITest` | Any app. No base class. On JUnit 6 it registers the Unified API's extension. |
| `@UnifiedSpringBootTest` | Spring Boot apps. `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@UnifiedUITest`. |

On JUnit, the extension injects `UnifiedPage` as a test method or constructor parameter. JUnit 6 is the Test Runner integration the Unified API ships; nothing else in it depends on JUnit, so other Test Runners can be integrated later ([ADR 0008](../adr/0008-adapter-spi-is-test-runner-neutral.md)).

```java
@UnifiedUITest
class LoginViewTest {
    @Test
    void logsIn(UnifiedPage page) { ... }
}
```

## Choosing the Testing Tool

One Testing Tool per **run**. Test code never names it.

| Setting | Values | Default |
|---|---|---|
| `vaadin.testing.tool` | `browserless`, `playwright`, `selenium` | `browserless` |
| `vaadin.testing.base-url` | URL of the running app (browser Testing Tools), when the test class has no `@BaseUrl` and no Spring port | `http://localhost:8080` |
| `vaadin.testing.timeout` | Auto-Wait and assertion timeout | `10s` |
| `vaadin.testing.viewport` | the Viewport of every test without `@Viewport`, `[name:]WIDTHxHEIGHT` such as `phone:375x667` (browser Testing Tools) | `1280x720` |
| `vaadin.testing.layout-checks` | `on`: measure the layout after every navigation, reload and Viewport change, in a browser ([ADR 0011](../adr/0011-responsive-testing-viewports-layout-findings-viewport-comparison.md)) | `off` |
| `vaadin.testing.layout-findings` | `report`, or `fail`: a Layout Finding fails the User Step that measured it | `report` |
| `vaadin.testing.layout-checks.screenshots` | `on`: a screenshot of the Viewport per measurement | `off` |

On JUnit, set them as JUnit configuration parameters, in `src/test/resources/junit-platform.properties`, or as system properties with `-D` from Maven, Gradle or the IDE.

A build runs the suite once per Testing Tool it wants:

```mermaid
flowchart LR
    C[mvn test] -->|Surefire, every commit| B[Browserless run]
    N[mvn verify -Dvaadin.testing.tool=playwright] -->|Failsafe, nightly| P[Playwright run]
```

All other settings stay with each tool: browser type, headless mode, TestBench license key, Browserless view packages. The Selenium adapter creates its browser itself and always runs it locally: it has no remote Selenium grid setting ([ADR 0020](../adr/0020-failsafe-profiles-that-test-elsewhere-get-a-marker-no-remote-grid.md), [#645](https://github.com/parttio/unified-testing-tool/issues/645)).

## Reaching the application

The Unified API never starts a server itself.

| App | Browserless | Browser Testing Tools |
|---|---|---|
| Spring Boot, `@UnifiedSpringBootTest` | App built in the test JVM from the Spring context (`SpringBrowserlessApplicationContext`); routes from the packages of the `@SpringBootApplication` and the test's own package. Needs `com.vaadin:browserless-test-spring` (a Spring test without it fails at once). | Same Spring context, random port; base URL `http://localhost:<port>` |
| Plain servlet / other | Views found with Browserless's `@ViewPackages` | Server started by the build (e.g. Failsafe + `jetty:start`); base URL from `vaadin.testing.base-url`, or the test class's `@BaseUrl` |

### The Base URL of a test

Decided in [#678](https://github.com/parttio/unified-testing-tool/issues/678) ([ADR 0021](../adr/0021-the-base-url-is-chosen-per-test-class-by-base-url.md)). On browser Testing Tools, relative navigation, the path checks, `isAt(..)` and the Findable View resolve against the test's Base URL, the first of:

| Source | Scope |
|---|---|
| `@BaseUrl("http://…")`, or `@BaseUrl(provider = P.class)` with `P implements BaseUrlProvider`, called with the test class when the test's page opens | Test class: inherited, and found on an enclosing class for a `@Nested` test. Both or neither set fails the test. |
| The port of the test's Spring web server, with `server.servlet.context-path` | Spring context |
| `vaadin.testing.base-url` | Run |
| `http://localhost:8080` | Default |

A trailing `/` is dropped. Browserless ignores the Base URL. Adapters read it as `AdapterContext.baseUrl()`, so every browser Testing Tool resolves it the same way.

`@UnifiedSpringBootTest` always uses a random port, also on Browserless runs. Cost: one embedded-server start per cached Spring context, not per test. Benefit: same app setup on every Testing Tool. A suite whose classes each declare their own `@MockitoBean`s, `@Import`s, properties or `@ServiceConnection` containers starts one context per class; keep it on one through a shared base class ([#331](https://github.com/parttio/unified-testing-tool/issues/331), [Migrating from Browserless](../migrating-from-browserless.md#one-spring-context)).

```java
@UnifiedSpringBootTest(properties = "app.feature.x=true")
class ProfileViewTest {
    @MockitoBean ProfileService service;   // works on all three: the server runs in the test JVM

    @Test
    void savesName(UnifiedPage page) { ... }
}
```

| Case | Rule |
|---|---|
| Narrow Spring context (`@ContextConfiguration`, no web server) | Browserless only: declare `@RequiresTestingTool(BROWSERLESS)`. Portable alternative: `@UnifiedSpringBootTest` + `@MockitoBean`. |
| Browser runs | Need `com.vaadin:vaadin-dev` (dev mode with the pre-built dev bundle). |
| Development tools | Off in a `@UnifiedSpringBootTest`'s application: `vaadin.devmode.devTools.enabled=false`, as the last property source, so the test's `properties`, the application's configuration, system properties and the environment win over it. In development mode Vaadin Copilot starts file watchers for every browser session and stops them when the application stops; a run's sessions add up to the machine's limit (about two watchers per test, 128 instances on a usual Linux), after which each session logs an error. |
| Quarkus | Not specified yet ([open-questions.md](open-questions.md)). |

## Test data

On Browserless the views run on the test's thread; in a browser they run on the server's request threads.
So a test's transaction doesn't reach the views in a browser:

| `@Transactional` on a `@UnifiedSpringBootTest` | Browserless | Playwright, Selenium |
|---|---|---|
| Views see data the test wrote but didn't commit | yes | no |
| What the views write is rolled back after the test | yes | no, later tests see it |

A `@Transactional` test therefore fails on browser Testing Tools before it starts, with this reason
(`propagation = NOT_SUPPORTED` or `NEVER` is allowed). To isolate test data on every Testing Tool, commit
it, and reset the database after each test: delete what the test created, or restore the tables from a
copy taken once after the context started (with Testcontainers, a fresh schema per test class also works).

## Requirements on Testing Tool adapters

- Read settings only through `AdapterContext.configuration(...)` and `timeout()`, and the test class, run-wide instances (such as a shared browser) and the Spring context only through `AdapterContext`. No Test Runner types: the build fails on a JUnit import in an adapter ([ADR 0008](../adr/0008-adapter-spi-is-test-runner-neutral.md)).
- Browser adapters: own the browser lifecycle; one fresh page per test, signed out with fresh storage. Selenium reuses one browser per run (a fresh tab with no cookies or storage per test) unless `vaadin.testing.selenium.reuse-browser=false`. Tests running in parallel in one JVM (JUnit's parallel execution) each get a browser no other test is using at that moment: Playwright launches one more `Playwright` and browser for each test running at once, reuses them for later tests, and closes them all when the run ends; Selenium starts a browser of its own for a test that finds the run's in use. Playwright for Java must not be used by two threads at once ([#686](https://github.com/parttio/unified-testing-tool/issues/686)).
- Browserless adapter: build the app per test, from the Spring context if there is one.
- Close everything after each test.
