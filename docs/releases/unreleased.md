# Unreleased

What is on `main` and not in a release yet: the changes since [0.1.0-alpha4](0.1.0-alpha4.md). The documentation
site describes `main`, so everything below is in the guide and the specification but not in `0.1.0-alpha4`. This
page becomes the notes of the next alpha.

## New

- **One message of a chat** ([#515](https://github.com/parttio/unified-testing-tool/issues/515)):
  `UnifiedMessageList.message(i)` (0-based) and `message()` with `withUserName(..)`, `withText(..)`,
  `withTextContaining(..)` and `last()` find one message lazily, as `grid.row()` finds a row. Its checks,
  `exists()`, `doesNotExist()`, `hasCount(n)`, `hasText(..)`, `hasTextContaining(..)` and `hasUserName(..)`, wait
  for a reply that comes later and are User Steps:
  `see last message from “Assistant” with text containing “Pancakes” in message list`.
- **The Findable View counts a message list's messages**: `message list, 3 messages`, as it counts a grid's rows.
- **The Dramafinder recipe converts more Playwright checks on raw locators**
  ([#510](https://github.com/parttio/unified-testing-tool/issues/510)): on a `locator("#x")` or `getByTestId("x")`
  it already turns into a lookup, `assertThat(locator)`'s `containsText(s)` becomes `hasTextContaining(s)`,
  `hasText(s)` `hasText(s)`, `hasAttribute("disabled", "")` `isDisabled()`, `not().hasAttribute("disabled", ..)`
  and `not().hasAttribute("aria-disabled", "true")` `isEnabled()`, `isAttached()` `exists()`, `hasClass("c")`
  `assertThat(w.withClassName("c")).exists()` and `not().isVisible()` `assertThat(w.visible()).doesNotExist()`.
- **The Dramafinder recipe converts a chat in a dock, `focus()` and a message by index**
  ([#511](https://github.com/parttio/unified-testing-tool/issues/511)): `MessageInputElement.get(page.getByTestId("dock"))`
  becomes `page.find(UnifiedContainer.class).withTestId("dock").find(UnifiedMessageInput.class)` (and the same for
  `MessageListElement`, and for `locator("#x")` / `locator("[data-testid=x]")`), `new MessageListElement(locator)`
  `page.find(UnifiedMessageList.class).withId(..)`, `input.focus()` `input.element().focus()`, and
  `assertThat(list.getMessage(i)).containsText(s)` / `hasText(s)` `assertThat(list.message(i)).hasTextContaining(s)`
  / `hasText(s)`. A message list read by index no longer keeps `assertMessageCount(n)` on it from converting.

## Changed behaviour

- **`user-steps.txt` shows the `Did you mean`** ([#518](https://github.com/parttio/unified-testing-tool/issues/518)):
  a failure with a suggestion gets one more line under its `✘` line, in the failure message's wording,
  `Did you mean  find(UnifiedButton.class).withAccessibleName("Previous Week")`. The CI log prints the same text.
  `Nearby` stays in the message, `user-steps.json` and the reports; the JSON is unchanged. *Reading reports and
  failures* now states exactly when a failure gets a `Did you mean`; the rules themselves are unchanged.
