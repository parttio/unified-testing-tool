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

## Changed behaviour

- **`user-steps.txt` shows the `Did you mean`** ([#518](https://github.com/parttio/unified-testing-tool/issues/518)):
  a failure with a suggestion gets one more line under its `✘` line, in the failure message's wording,
  `Did you mean  find(UnifiedButton.class).withAccessibleName("Previous Week")`. The CI log prints the same text.
  `Nearby` stays in the message, `user-steps.json` and the reports; the JSON is unchanged. *Reading reports and
  failures* now states exactly when a failure gets a `Did you mean`; the rules themselves are unchanged.
