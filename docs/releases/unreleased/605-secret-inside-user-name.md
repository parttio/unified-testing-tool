---
section: Fixed
---
- **A password inside the user name no longer garbles the reports**
  ([#605](https://github.com/parttio/unified-testing-tool/issues/605),
  [ADR 0014](../adr/0014-secrets-are-masked-as-substrings-except-the-login-user-name-and-display-name.md)):
  `login("barista@vaadin.com", "barista")` read `sign in as “••••@vaadin.com”`, and a `@DisplayName` like "A
  signed-in barista logs out" read "A signed-in •••• logs out". A login's password is no longer masked inside that
  login's own user name (unless the two are equal), and the scenario name the author wrote (`@DisplayName`, or the
  method's name) is shown as written. The password stays masked everywhere else, even inside a longer word, and a
  parameterized or repeated invocation's name is still masked.
