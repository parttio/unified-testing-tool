---
section: New
---
- **The Dramafinder recipe converts a chat in a dock, `focus()` and a message by index**
  ([#511](https://github.com/parttio/unified-testing-tool/issues/511)): `MessageInputElement.get(page.getByTestId("dock"))`
  becomes `page.find(UnifiedContainer.class).withTestId("dock").find(UnifiedMessageInput.class)` (and the same for
  `MessageListElement`, and for `locator("#x")` / `locator("[data-testid=x]")`), `new MessageListElement(locator)`
  `page.find(UnifiedMessageList.class).withId(..)`, `input.focus()` `input.element().focus()`, and
  `assertThat(list.getMessage(i)).containsText(s)` / `hasText(s)` `assertThat(list.message(i)).hasTextContaining(s)`
  / `hasText(s)`. A message list read by index no longer keeps `assertMessageCount(n)` on it from converting.
