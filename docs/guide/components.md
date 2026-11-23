# Working with components

Once [found](finding-components.md), a Wrapper acts like a user: it finds its component, waits until exactly one is there, visible and enabled, acts, then waits until the server has answered (Auto-Wait). Every action is reported as a User Step, such as `fill text field “Name” with “Alice”` ([Reading reports and failures](reports-and-failures.md)). Spec: [6. Wrappers](../spec/06-wrappers.md).

Every Wrapper has:

| Method | Does |
|---|---|
| `isVisible()`, `isEnabled()` | reads the state now |
| `withTimeout(Duration)` | the same Wrapper with its own timeout |
| `element()` | the [Unified Element](../spec/06-wrappers.md#unified-element) underneath: text, properties, attributes, `click()`, `pressKey(..)` |
| `unwrap(..)`, `tryUnwrap(..)` | the Testing Tool's own object ([Escape Hatch](tool-specific.md)) |

## Text fields

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:textFields"
```

`UnifiedTextArea` works the same way.

## Typed values

Number and date fields take and return Java types, `null` when empty:

| Wrapper | Value type |
|---|---|
| `UnifiedNumberField` | `Double` |
| `UnifiedIntegerField` | `Integer` |
| `UnifiedBigDecimalField` | `BigDecimal` |
| `UnifiedDatePicker` | `LocalDate` |
| `UnifiedDateTimePicker` | `LocalDateTime` |

```java
--8<-- "unified-test-demo/src/test/java/demo/ExpenseTest.java:fillsTheValueFields"
```

`setValue(LocalDate)` is the way to choose a date, whether the user types it or picks it in the calendar: the
calendar is the date picker's own business, tested in Vaadin. *(unreleased)* It also works on a picker whose field
is hidden, such as a week picker the application opens from a badge of its own, as long as its calendar is open:
the date is set as a pick sets it, and the calendar closes. A hidden picker whose calendar is closed can't be set,
as the user can't reach it. Browserless sees no CSS, so there the field counts as visible.

```java
--8<-- "unified-test-demo/src/test/java/demo/WeekPickerTest.java:picksAWeekFromTheOpenCalendar"
```

To check how the field handles text it can't parse, type it:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:unparsableText"
```

Text is typed as it is, and Browserless parses only ISO dates while a browser parses the field's locale format, so
`"2026-07-01"` is bad input in a browser. For incomplete input, a date the field can parse with no time, give the
date as a `LocalDate`: `typeText(LocalDate, String)` types it in the field's own format, on every
Testing Tool and in every locale.

```java
--8<-- "unified-test-demo/src/test/java/demo/ExpenseTest.java:typesADateWithoutATime"
```

## Checkbox

```java
--8<-- "unified-test-demo/src/test/java/demo/PreferencesTest.java:savesTypedValues"
```

`check()` and `uncheck()` do nothing when the checkbox is already in that state; `setChecked(boolean)` does either. `click()` toggles it, as a user's click does, on every Testing Tool; prefer it to `element().click()`, which in a browser misses a checkbox wider than its box and label. Assertions: `isChecked()`, `isNotChecked()`, `isIndeterminate()`.

## Selection: ComboBox, RadioButtonGroup, MultiSelectComboBox

Items are named by the **label the user sees** (the item label generator's result), not by the Java object:

```java
--8<-- "unified-test-demo/src/test/java/demo/OrderTest.java:selectsByVisibleLabel"
```

| | `UnifiedComboBox`, `UnifiedRadioButtonGroup` | `UnifiedMultiSelectComboBox` |
|---|---|---|
| select | `selectItem("Germany")` | `selectItems("Olives", "Cheese")` |
| deselect | `clear()` | `deselectItems("Olives")`, `clear()` |
| read | `getSelectedItem()`, `getItems()` | `getSelectedItems()`, `getItems()` |
| assert | `hasSelectedItem(..)`, `hasNoSelectedItem()`, `hasItems(..)` | `hasSelectedItems(..)`, `hasNoSelectedItems()`, `hasItems(..)` |

Selecting an item that doesn't exist fails with the list of items there are, e.g. `… [Finland, Germany, France]`.

## Buttons and menus

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:buttons"
```

A MenuBar opens its sub-menus as a user does: give the path of item names.

```java
--8<-- "unified-test-demo/src/test/java/demo/ExpenseTest.java:clicksNestedMenuItems"
```

An icon-only item is named by its `aria-label`. `assertThat(menu).hasItems(..)` checks the top-level items and
`hasItems(List.of("File", "Export"), ..)` those of a sub-menu; `hasItem(..)`, `hasItemEnabled(..)` and
`hasItemDisabled(..)` take a path like `clickItem(..)` and check that the user sees the item, and whether they can
click it:

```java
--8<-- "unified-test-demo/src/test/java/demo/ItemNameTest.java:assertsWhetherAMenuItemIsEnabled"
```

`getItems(..)` and `isItemEnabled(..)` read the same now, without waiting.

## Tabs and Details

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:tabsAndDetails"
```

A tab holding a `RouterLink` is named by the link's text.

## Dialogs and notifications

Only open dialogs and notifications are found. A dialog is a [Scope](finding-components.md#scopes):

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:overlays"
```

When several notifications can be open at once, filter by text: an unfiltered `find(UnifiedNotification.class)` fails with "found 2".

## Upload

`upload` returns once the server has received the file:

```java
--8<-- "unified-test-demo/src/test/java/demo/ExpenseTest.java:uploadsAFile"
```

## Chat

An app built on Vaadin's AI components has a `MessageInput` and a `MessageList`. `send` types the text and submits it as a user does; the reply is checked with an assertion, which waits for it:

```java
--8<-- "unified-test-demo/src/test/java/demo/ChatTest.java:sendsAMessage"
```

A message's text is the text the application gave it: with Markdown on, the Markdown source (`**Pancakes**`), not the rendered text, so that it reads the same on every Testing Tool. `getMessages()` gives each message's user name and text, `getMessageTexts()` the texts only. `hasMessageTexts(..)` checks every message, in order; `isEmpty()` checks there are none.

*(unreleased)* One message is a lazy lookup, as a [grid row](grid.md) is: `message(i)` by its index among all messages, from 0, or `message()` narrowed by `withUserName(..)`, `withText(..)` and `withTextContaining(..)`, with `last()` taking the last of the matches so far. Its checks wait for a reply that comes later:

```java
--8<-- "unified-test-demo/src/test/java/demo/ChatTest.java:checksOneMessage"
```

`hasText(..)`, `hasTextContaining(..)` and `hasUserName(..)` need exactly one matching message, `exists()`, `doesNotExist()` and `hasCount(n)` count them. Use `hasMessageContaining(..)` when any message will do, and `message()` for a message at a position, from someone, the last one, or none.

## Keyboard and DOM events

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:keysAndEvents"
```

```java
--8<-- "unified-test-demo/src/test/java/demo/KeyboardTest.java:escapeOnThePageClosesTheDialog"
```

On Browserless, key presses fire the server-side listeners and shortcuts; client-side behaviour such as Tab moving focus doesn't happen.

## Plain HTML and text

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:withoutWrapper"
```

`getText()` is the DOM text, the same on every Testing Tool: CSS such as `text-transform` doesn't change it. In a browser, `element().getVisibleText()` gives the text as rendered.

A `Div`, `Span` or other plain HTML element with a click listener, such as a navigation tab or a wordmark, is clicked like a button. So is a layout, through `UnifiedContainer`:

```java
--8<-- "unified-test-demo/src/test/java/demo/ClickableHtmlTest.java:clicksADiv"

--8<-- "unified-test-demo/src/test/java/demo/ClickableHtmlTest.java:clicksAContainer"
```

`click()` reads as a User Step, `click div “Shop”`. A click on an element without a listener does nothing, as for a user.
