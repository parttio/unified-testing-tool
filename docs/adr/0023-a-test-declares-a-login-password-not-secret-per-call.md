---
status: accepted
---

# A test declares a login password not secret, per call: `login(.., Password.NOT_SECRET)`

[ADR 0014](0014-secrets-are-masked-as-substrings-except-the-login-user-name-and-display-name.md) masks a `UnifiedLogin.login(..)` password everywhere, inside its own user name too when the two are equal. Seeded test users often have their user name as password (`admin` / `admin`), so the reports read `sign in as “••••” in login` and `Log in as ••••`, and a workflow that switches users no longer says who acts (#683). Masking protects real credentials in reports that end up in CI artifacts and issues. It doesn't help the test, and for seeded test data it hides only what the source shows anyway. We decide that `UnifiedLogin.login(username, password, Password.NOT_SECRET)` signs in with a password that isn't a Secret Value: the reports show it wherever it occurs. `login(username, password)` stays as it is and means `Password.SECRET`.

```java
page.find(UnifiedLogin.class).login("admin", "admin", Password.NOT_SECRET);
// sign in as “admin” in login
```

## Decisions

- **Per call, not per suite.** The test that knows the password is test data says so where it signs in. A suite that also signs in with a real credential keeps it masked.
- **Not secret means shown everywhere**, not only inside the user name: the password isn't added to the Secret Values at all. A value that another call marks secret (`page.secret(..)`, a plain `login(..)`) stays masked, and so does a value typed into a password field through a Wrapper.
- **An enum, not a boolean:** `login(u, p, Password.NOT_SECRET)` says what it means at the call site; `login(u, p, false)` doesn't.
- **ADR 0014 otherwise stands.** No minimum length: a short real secret (`boom`) would show in logs and failures. `page.step(..)` names are still masked, unlike `@DisplayName`: they are built at run time, often from variables (`"Log in as " + user`), and can carry the password.

## Considered Options

- **A setting** (`vaadin.testing.mask-login-passwords=false`). One line for the whole suite, but it unmasks every login of the suite, real credentials included, and the test that signs in doesn't show it.
- **A minimum length before a value is masked inside longer text.** It would fix `admin` turning "Administration" into "••••istration", but ADR 0014 rejected it because short real secrets would leak. The overload fixes the test-data case without that.
