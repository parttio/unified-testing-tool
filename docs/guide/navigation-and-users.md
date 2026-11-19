# Navigation, users and windows

Spec: [3. Navigation](../spec/03-navigation.md), [11. Users](../spec/11-users.md).

## Opening a view

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:navigate"
```

`navigate` returns once the view is shown and the server is idle. The route class form survives renaming a route; use the path form for query parameters and routes registered at runtime.

## Checking where the page is

```java
--8<-- "unified-test-demo/src/test/java/demo/NavigationTest.java:navigatesByPathWithQuery"
```

`isAt(..)` also follows navigation the app does itself: a button that navigates, a `RouterLink`, a redirect to the login view:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:redirected"
```

## Running as a user

With Spring Security, put Spring's own `@WithMockUser` or `@WithUserDetails` on the test or the class. On Browserless it sets the security context; on Playwright and Selenium the browser logs in through a test-only endpoint. The test is the same:

```java
--8<-- "unified-test-demo/src/test/java/demo/UsersTest.java:adminSeesTheAdminArea"
```

## Several windows and users

| Need | Write | Session |
|---|---|---|
| a second tab of the same user | `page.openWindow()` | shared |
| another user at the same time | `page.openAsUser("bob", "USER")` | its own |

```java
--8<-- "unified-test-demo/src/test/java/demo/UsersTest.java:openWindowSharesTheSession"
```

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:anotherUser"
```

Each page is used on its own; there is no switching. All windows close at the end of the test.

## Spring beans

`@UnifiedSpringBootTest` is a `@SpringBootTest`, so the usual Spring test tools work, such as `@MockitoBean`:

```java
--8<-- "unified-test-demo/src/test/java/demo/MockedServiceTest.java:usesTheMock"
```

On Playwright and Selenium the app runs in a server thread, not in the test's thread: `@Transactional` on a test can't roll back what the app did there, so the Unified API refuses it on those tools.
