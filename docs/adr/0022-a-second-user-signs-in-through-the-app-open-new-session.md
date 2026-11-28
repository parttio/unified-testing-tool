---
status: accepted
---

# A second user signs in through the app: `openNewSession()`, no pluggable login

`openAsUser(..)` signs a new user in through the test-only endpoint of `@UnifiedSpringBootTest` (`TestLogin`), so it fails without Spring Security. A test of an application without it (Vaadin Flow on Payara, #681) had no way to get a second Unified Page in a session of its own, and ran its second user on raw Playwright behind the Escape Hatch: no User Steps, no Auto-Wait, no Near Misses. Spec 11 left "Authentication without Spring Security" unspecified. We decide that `UnifiedPage.openNewSession()` opens a window in a new session of its own with nobody signed in, and the test signs in through the application's own login, usually with `UnifiedLogin`. `openAsUser(..)` stays Spring-only. A reusable sign-in is the test's own helper or User Task, not a Unified API type.

```java
UnifiedPage officer = admin.openNewSession();
officer.step("Sign in as officer", () -> {
    officer.navigate(LoginView.class);
    officer.find(UnifiedLogin.class).login("officer", password);
});
```

## Decisions

- **On every Testing Tool, `openNewSession()` is `openAsUser(..)` without the login.** On Browserless it is a new user of the same application (`newUser()`): a VaadinSession of its own, with no security context, even on a Spring-secured application. On Playwright it is a new browser context, which isn't traced, like `openAsUser(..)`'s. On Selenium it is a new browser. It starts on no view, ignores the test's `@WithMockUser`, and closes at the end of the test.
- **The name says what differs from `openWindow()`: the session.** It makes no claim about security, which an application without Spring Security may not have. The User Step reads `open a new session`, and the reports call its window `session 2`, `session 3`, and so on.
- **The login is a User Step.** Signing in through the form gets Auto-Wait, Near Misses, and the password masked as a Secret Value ([ADR 0014](0014-secrets-are-masked-as-substrings-except-the-login-user-name-and-display-name.md)). On Browserless, a `LoginForm` with a form `action` (Spring Security's `/login`) still can't be submitted. An application that signs in through its `LoginEvent` listener works on every Testing Tool.

## Considered Options

- **A pluggable login strategy for `openAsUser(..)`**, such as a `UnifiedLogin` form login used when there is no Spring endpoint. It needs a new public type, a way to register it, a password source (`openAsUser(..)` takes roles, not a password), and a rule for when it wins over the endpoint. It would still need a page in a new, signed-out session underneath, and it saves the test only the two lines its own helper holds.
- **`openSignedOut()`**, as the issue suggested. It reads as opening a signed-out page rather than a new session. **`openAsAnonymous()`** uses Spring Security's word.
- **On Selenium, a new window with its cookies cleared** instead of a new browser. It would be cheaper, but WebDriver can't keep cookies apart per window, so the two windows would not have separate sessions.

## Consequences

- [11. Users and windows](../spec/11-users.md) no longer lists authentication without Spring Security as unspecified. The test user of the injected page without Spring Security is the application's own login, as before.
- `openAsUser(..)`'s error without the endpoint points to `openNewSession()`.
