---
section: Changed behaviour
---
- **No `@WithMockUser` advice in a test that checks the login** ([#622](https://github.com/parttio/unified-testing-tool/issues/622)):
  the TestBench recipe's "signs in through the login form: ... consider @WithMockUser" Migration Marker is left out
  of a test method that asserts on a call to a login element, such as
  `assertEquals("Email", loginView.getUsernameLabel())`. Such a test checks the login itself. Every other call on a
  login element is still marked, as before.
