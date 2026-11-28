# Navigation, users and windows

Spec: [3. Navigation](../spec/03-navigation.md), [11. Users](../spec/11-users.md).

## Opening a view

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:navigate"
```

`navigate` returns once the view is shown and the server is idle. The route class form survives renaming a route; use the path form for routes registered at runtime. Query parameters go after the route class, as in Flow's `UI.navigate(..)`:

```java
--8<-- "unified-test-demo/src/test/java/demo/NavigationTest.java:navigatesByClassWithQuery"
```

## Checking where the page is

```java
--8<-- "unified-test-demo/src/test/java/demo/NavigationTest.java:navigatesByPathWithQuery"
```

`isAt(..)` also follows navigation the app does itself: a button that navigates, a `RouterLink`, a redirect to the login view:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:redirected"
```

`isAt(..)` matches the route template, so it can't tell `shipment` from `shipment/42`, and `hasPath(..)` needs the whole path. When part of the path isn't known in advance, such as the id of an item the test just created, use `hasPathMatching(..)` with a regular expression that matches the whole path. It retries like `hasPath`:

```java
--8<-- "unified-test-demo/src/test/java/demo/NavigationTest.java:hasPathMatching"
```

## Running as a user

With Spring Security, put Spring's own `@WithMockUser` or `@WithUserDetails` on the test or the class. On Browserless it sets the security context; on Playwright and Selenium the browser logs in through a test-only endpoint. The test is the same:

```java
--8<-- "unified-test-demo/src/test/java/demo/UsersTest.java:adminSeesTheAdminArea"
```

`@WithMockUser` comes from Spring Security's test support, which a project tested through its login form may not have yet. Add it as a test dependency: `spring-boot-starter-security-test` on Spring Boot 4, or `org.springframework.security:spring-security-test` otherwise.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

## Several windows and users

| Need | Write | Session |
|---|---|---|
| a second tab of the same user | `page.openWindow()` | shared |
| another user at the same time | `page.openAsUser("bob", "USER")` | its own |
| another user, signing in through the app's login | `page.openNewSession()` | its own, nobody signed in |

```java
--8<-- "unified-test-demo/src/test/java/demo/UsersTest.java:openWindowSharesTheSession"
```

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:anotherUser"
```

Each page is used on its own; there is no switching. All windows close at the end of the test.

### A second user without Spring Security

`openAsUser(..)` signs the user in through Spring Security. An application without it (or a test that should go through the real login) opens a new session with nobody signed in, and signs in through the application's own login form with [`UnifiedLogin`](components.md). Put the sign-in in a helper of the test, so each user is one User Task:

```java
--8<-- "unified-test-demo/src/test/java/demo/UsersTest.java:openNewSessionSignsIn"
```

The new session ignores the test's `@WithMockUser`, and starts on no view. On Browserless it is a new user of the same application; on Playwright a new browser context, which isn't traced; on Selenium a new browser. On Browserless, a login form that posts (`setAction(..)`, Spring Security's `/login`) can't be submitted: give that test `@RequiresBrowser`.

## Spring beans

`@UnifiedSpringBootTest` is a `@SpringBootTest`, so the usual Spring test tools work, such as `@MockitoBean`:

```java
--8<-- "unified-test-demo/src/test/java/demo/MockedServiceTest.java:usesTheMock"
```

On Playwright and Selenium the app runs in a server thread, not in the test's thread: `@Transactional` on a test can't roll back what the app did there, so the Unified API refuses it on those tools.
