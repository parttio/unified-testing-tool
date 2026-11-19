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

To check how the field handles text it can't parse, type it:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:unparsableText"
```

## Checkbox

```java
--8<-- "unified-test-demo/src/test/java/demo/PreferencesTest.java:savesTypedValues"
```

`check()` and `uncheck()` do nothing when the checkbox is already in that state; `setChecked(boolean)` does either. Assertions: `isChecked()`, `isNotChecked()`, `isIndeterminate()`.

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

An icon-only item is named by its `aria-label`. `isItemEnabled("Actions for Hotel", "Delete")` reads whether an item can be clicked.

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
