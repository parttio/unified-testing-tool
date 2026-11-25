---
status: accepted
---

# Failsafe profiles that tested a server or browser elsewhere get a Migration Marker; the Selenium adapter has no remote grid

Found migrating Bakery (#602). A TestBench project's Failsafe profiles don't all run against the app the build starts with `spring-boot:start`. `it-native` ran the tests against a native executable it started itself, and `it-saucelabs` sent the browser to Sauce Labs through TestBench's `Parameters.gridBrowsers` and Sauce options. After the TestBench Migration Recipe, each `@UnifiedSpringBootTest` starts the JVM app, and the Unified Selenium adapter always starts a local Chrome or Firefox: it has no `RemoteWebDriver`, hub URL or capabilities setting. Both profiles still ran, green, against something else, and nothing said so. Decided in #613:

- **The recipe marks such a profile in the pom.** A Migration Marker, an XML comment after the profile's `<id>`, says what changed. This amends [ADR 0013](0013-migrated-spring-boot-tests-start-the-app.md)'s "the recipe leaves the pom alone": the recipe still edits nothing in a profile but the evidence directory it gives each Failsafe plugin, and adds comments. A profile gets a marker when:
  - it binds `maven-failsafe-plugin` and starts a server some other way than `spring-boot:start`/`stop`: `native-maven-plugin`, `exec-maven-plugin`, or another `spring-boot-maven-plugin` goal (`build-info` aside);
  - it binds `maven-failsafe-plugin` and gives it a property whose name holds `url` or `host` (system property or environment variable), other than `vaadin.testing.*` and TestBench's grid parameters;
  - it sets anywhere a TestBench grid parameter (`com.vaadin.testbench.Parameters.*`, `gridBrowsers`, `hubHostname`, `hubPort`) or a name holding `sauce`.

  A plain `it` profile running only `spring-boot:start`/`stop` gets none: the class-level marker already says to remove them.
- **The Unified Selenium adapter has no remote grid, for now.** The configuration guide, the TestBench migration guide and [2. Test setup](../spec/02-test-setup.md) say so, and the grid marker says the migrated tests run a local browser. Remote-grid support is on the roadmap at low priority (#645).

## Considered Options

- **No pom markers, guide only:** keeps ADR 0013 as it was, but a profile that silently tests something else is the failure Migration Markers exist for, and a guide paragraph is read once, a marker where the profile is.
- **Rewrite or remove such profiles:** they vary too much to edit safely (ADR 0013), and only the project knows whether the native image or the grid run still matters.
- **A `vaadin.testing.selenium.remote-url` setting now, with the recipe mapping TestBench's hub settings onto it:** creating a `RemoteWebDriver` is small, but a `@UnifiedSpringBootTest` app listens on `localhost` on a random port, which a browser on Sauce Labs or another container can't reach without a tunnel or a host setting. Done alone, the setting would work only for a grid on the test's own network. #645 sizes the three steps.

## Consequences

- A migrated pom can hold `<!-- TODO unified: ... -->` comments in its profiles, and the `resolve-migration-markers` skill lists them for a person: whether to drop the native or grid run, or keep it with `springBootTest: false`, is the project's call.
- The signals are names, not meaning: a profile property such as `db.host` passed to Failsafe gets the server-address marker, and a profile that starts its server through a plugin not listed gets none.
- [2. Test setup](../spec/02-test-setup.md) no longer lists Selenium Grid among the settings that stay with each tool.
