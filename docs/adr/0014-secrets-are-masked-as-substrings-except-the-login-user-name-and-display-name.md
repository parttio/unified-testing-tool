---
status: accepted
---

# Secrets are masked as substrings, except inside the login's user name and the author's display name

A Secret Value (a password given to `UnifiedLogin.login(..)`, or a value passed through `page.secret(..)`) used to be replaced by `••••` wherever it appeared as a substring in the reports. Demo apps and Spring Security samples often use a password that is part of the user name (`barista@vaadin.com` / `barista`, `admin` / `admin`). In those cases the login step read `sign in as “••••@vaadin.com”`, a `@DisplayName` like "A signed-in barista logs out" read "A signed-in •••• logs out", and the masked user name told a reader what the password was (#605).

We decide to keep substring masking, with two exceptions. A login's password is not masked inside that login's user name, unless the two are equal. A test's `@DisplayName`, or its method name, is not masked: the author wrote it in the source. The name of a parameterized or repeated invocation is still masked, because JUnit builds it from the test's arguments, and those can carry the password. The reports exist to run tests on test data or anonymised data. Masking is there to keep a value out of what a reader sees, not to be a security boundary. So we give up nothing that the source doesn't already show, and the scenario stays readable.

## Considered Options

- **Mask only where the value is typed** (the password field and that value in a failed check). This would leak the password through log lines, adapter errors, URLs and DOM text that echo it.
- **Whole tokens only:** mask the secret only where no letter, digit or `@ . _ - +` touches it. This fixes the user name, but it still masks "A signed-in barista logs out". It also has to decide whether `/ = &` are delimiters, and every URL and path echo depends on that choice. A `\b` regex doesn't work, because `@` counts as a word boundary.
- **Minimum length:** don't search for secrets shorter than N characters. Short passwords like `admin` or `boom` would then appear in logs and in failure messages.

## Consequences

- A login's user name appears in full in every report, even where it contains the password. The user name isn't secret: the step names it anyway.
- Another Secret Value inside the user name (a `page.secret(..)` value, or another login's password) is still masked there.
- A password that equals its user name is masked everywhere, the user name included.
