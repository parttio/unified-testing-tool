---
section: New
---
- **Email and password fields** ([#555](https://github.com/parttio/unified-testing-tool/issues/555)):
  `page.find(UnifiedEmailField.class)` and `page.find(UnifiedPasswordField.class)` find Flow's `EmailField` and
  `PasswordField`, with a text field's filters, actions, getters and checks on every Testing Tool, where a project
  wrote its own Wrapper for each. Both share the new `UnifiedTextInput` with `UnifiedTextField` and
  `UnifiedTextArea`. What is typed into a password field reads `••••` in the User Steps, as before, and now its
  values read `“••••”` in a failed check too. The TestBench, Dramafinder and Browserless recipes convert
  `EmailFieldElement`, `PasswordFieldElement`, `EmailField` and `PasswordField` lookups to the new Wrappers.
