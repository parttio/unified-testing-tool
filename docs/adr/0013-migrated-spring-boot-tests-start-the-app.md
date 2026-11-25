---
status: accepted
---

# Migrated TestBench tests of a Spring Boot app start the app themselves

Vaadin's starters (Bakery included) test the application as a server the build starts: an `it` profile runs `spring-boot:start`/`stop` around failsafe, the `*IT` classes have no `@SpringBootTest`, and each test opens `http://localhost:8080/` and signs in through the login form. The TestBench Migration Recipe used to turn every test class without `@SpringBootTest` into a `@UnifiedUITest`, "a test against a running application". For a Spring Boot app that keeps the browser runs working, but on Browserless there is no Spring context, so the app's Spring views can't be built and `@WithMockUser` has no effect.

We decide that, in a module with a `@SpringBootApplication`, such a class becomes a `@UnifiedSpringBootTest` by default: each test run starts the app from its Spring context on a random port, the same way on every Testing Tool. The recipe option `springBootTest: false` keeps the old behaviour (`@UnifiedUITest` and a Migration Marker) for a suite that must run against the server the build starts (#528).

## Considered Options

- **Always `@UnifiedUITest`, with a Migration Marker suggesting `@UnifiedSpringBootTest`.** Nothing about how the app runs changes, but every migrated Spring suite starts with Browserless failing on its views. Most projects keep a recipe's defaults, so the default decides what most migrations look like.
- **Always `@UnifiedSpringBootTest`, with no option.** Simplest, but a suite that depends on the prod-like server (its profile, configuration or external database) would have to undo it by hand.
- **An option defaulting to `@UnifiedUITest`.** The most conservative, but it leaves the default migration in the state above.

## Consequences

- The migrated tests no longer run against the configuration the build's server used. The app runs with its test configuration, so a difference between the two can make a test pass or fail differently than before. The class-level Migration Marker says so.
- The build's `spring-boot:start`/`stop` executions become redundant. With the default they start a second, unused server. The recipe leaves the pom alone: profiles vary too much to edit safely. The marker and the TestBench migration guide say to remove them. Amended by [ADR 0017](0017-failsafe-profiles-that-test-elsewhere-get-a-marker-no-remote-grid.md): a profile that tested a server or browser outside the test (a native executable, a server address, a remote grid) gets a Migration Marker, an XML comment; the recipe still edits nothing else there.
- `@WithMockUser` becomes the way to sign in. The migration adds Spring Security's test dependency to an app that uses Spring Security, and it marks calls on login page objects with "consider `@WithMockUser`". Bakery, migrated this way by hand, went from TestBench's 38–89 s and 2 green runs of 3 to 26–27 s and 3 of 3 on Selenium.
