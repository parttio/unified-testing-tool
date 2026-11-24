---
section: Changed behaviour
---
- **A Spring Boot app's TestBench tests start the app themselves** ([#528](https://github.com/parttio/unified-testing-tool/issues/528),
  [ADR 0013](../adr/0013-migrated-spring-boot-tests-start-the-app.md)): in a module with a `@SpringBootApplication`,
  the TestBench recipe turns a test class without `@SpringBootTest`, which ran against a server the build started,
  into a `@UnifiedSpringBootTest` instead of a `@UnifiedUITest`, with a Migration Marker saying the build's
  `spring-boot:start`/`stop` executions can go (the recipe leaves the pom's executions alone). The new option
  `springBootTest: false` keeps `@UnifiedUITest`, with a Migration Marker. In a project with Spring Security, the
  build gets `spring-boot-starter-security-test` (`spring-security-test` on a Spring Boot without it), and, by
  default, a statement calling a login element (`LoginOverlayElement`, `LoginFormElement` or a page object extending
  one) gets a Migration Marker suggesting `@WithMockUser`. `FindTestClassesToMigrate` names the annotation such a
  class gets in a new `Annotation` column.
