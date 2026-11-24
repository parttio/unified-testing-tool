---
section: New
---
- **Login** ([#564](https://github.com/parttio/unified-testing-tool/issues/564)): `page.find(UnifiedLogin.class)`
  finds Flow's `LoginOverlay` (while open) or `LoginForm`; `withTitle(..)` filters by the overlay's title.
  `login(username, password)` signs in: in a browser it types into the form's fields and presses submit, which also
  posts a form with an `action` (Spring Security's `/login`); on Browserless it fires the component's `LoginEvent`,
  and a component with an `action` fails, asking for `@RequiresBrowser` or `@WithMockUser`
  ([#582](https://github.com/parttio/unified-testing-tool/issues/582)). The User Step reads
  `sign in as “barista@vaadin.com” in login`; the password is a secret of the run, never shown in a step, report or
  failure. `forgotPassword()`, `isOpen()`, `isError()`, `getTitle()`, `getDescription()`, and the checks `isOpen`,
  `isClosed`, `hasError`, `hasNoError`, `hasTitle`. The TestBench recipe converts `LoginOverlayElement` and
  `LoginFormElement` lookups (`isOpened()`, `getTitle()`, `forgotPassword()`), and its login marker names
  `login(..)`; the Dramafinder recipe converts `LoginFormElement` and `LoginOverlayElement`, `login(..)` included.
  `@UnifiedComponent(tag = ..)` takes several comma-separated tags. **Adapter SPI:** `LoginAccess`.
