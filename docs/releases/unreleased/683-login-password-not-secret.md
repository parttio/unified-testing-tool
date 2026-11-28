---
section: New
---
- **A login password declared not secret** ([#683](https://github.com/parttio/unified-testing-tool/issues/683)):
  `UnifiedLogin.login(username, password, Password.NOT_SECRET)` signs in with a password that is test data. The
  reports show it wherever it occurs, so a seeded user whose password is its user name (`admin` / `admin`) reads
  `sign in as “admin” in login` instead of `sign in as “••••” in login`. `login(username, password)` keeps the
  password secret ([ADR 0023](../adr/0023-a-test-declares-a-login-password-not-secret-per-call.md),
  [guide](../guide/structuring-a-scenario.md#secret-values)).
