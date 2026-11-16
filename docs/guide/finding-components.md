# Finding components

`find(..)` returns the **Wrapper** for a kind of component, and its filters narrow it down:

```java
page.find(UnifiedTextField.class)   // which kind of component: the Wrapper
    .withLabel("Name")              // filters: zero or more, all must match
    .setValue("Alan M. Turing");    // use it: an action, a read or assertThat(..)
```

There is nothing to end the lookup with: the Wrapper is the lookup. Each time you use it, it finds its component again and waits until exactly one matches. Spec: [4. Locating components](../spec/04-locating.md).

## 1. The Wrapper class

The Wrapper class is the component type, like a role in Playwright's `getByRole`. Each Wrapper has only the filters that make sense for it:

```java
page.find(UnifiedButton.class).withText("Save");      // a button has text
page.find(UnifiedTextField.class).withLabel("Name");  // a field has a label
page.find(UnifiedButton.class).withLabel("Save");     // does not compile: a button has no label
```

So your IDE's completion after `find(UnifiedX.class).` lists what you can filter by, next to what you can do with the component.

Wrappers live in `com.vaadin.testing.unified.component`: `UnifiedTextField`, `UnifiedTextArea`, `UnifiedNumberField`, `UnifiedIntegerField`, `UnifiedBigDecimalField`, `UnifiedDatePicker`, `UnifiedDateTimePicker`, `UnifiedCheckbox`, `UnifiedComboBox`, `UnifiedMultiSelectComboBox`, `UnifiedRadioButtonGroup`, `UnifiedButton`, `UnifiedMenuBar`, `UnifiedDialog`, `UnifiedNotification`, `UnifiedDetails`, `UnifiedUpload`, `UnifiedGrid`, `UnifiedTabSheet`, `UnifiedTabs`, `UnifiedContainer` and the plain HTML Wrappers (`UnifiedDiv`, `UnifiedSpan`, `UnifiedSection`, …). For any other component, see [Components without a Wrapper](#components-without-a-wrapper).

## 2. Filters

### By what the user sees (prefer these)

| Filter | Finds | Example |
|---|---|---|
| `withLabel("Email")` | a field by its label | `page.find(UnifiedTextField.class).withLabel("Email")` |
| `withText("Save")` | a button, notification, … by its text | `page.find(UnifiedButton.class).withText("Save")` |
| `withAccessibleName("Delete account")` | what a screen reader announces: `aria-label`, `setAriaLabel(..)`, label or text | an icon-only button, a field without a visible label |
| `withPlaceholder("Nickname")` | a field by its placeholder | `page.find(UnifiedTextField.class).withPlaceholder("Nickname")` |
| `withHeaderTitle("Delete?")` | a dialog by its header | `page.find(UnifiedDialog.class).withHeaderTitle("Change email")` |
| `withTab("Security")` | a TabSheet or Tabs that has this tab | `page.find(UnifiedTabSheet.class).withTab("Security")` |
| `withItem("File")` | a MenuBar that has this top-level item | `page.find(UnifiedMenuBar.class).withItem("File")` |
| `withSummaryText("Advanced")` | a Details by its summary | `page.find(UnifiedDetails.class).withSummaryText("Advanced")` |

An icon-only button has no text, but it has an accessible name:

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:findsIconButtonByAccessibleName"
```

A field named with `setAriaLabel(..)` and no visible label is found the same way:

```java
--8<-- "unified-test-demo/src/test/java/demo/ReportTest.java:findsAFieldByItsAccessibleName"
```

### Exact or containing

Text filters match the **whole text**, with whitespace normalised. Each has a `…Containing` variant that matches a part, ignoring case:

```java
page.find(UnifiedButton.class).withText("Save");                      // "Save", not "Save draft"
page.find(UnifiedButton.class).withTextContaining("save");            // "Save", "Save draft", "Autosave"
page.find(UnifiedTextField.class).withLabelContaining("phone");       // "Phone", "Mobile phone"
page.find(UnifiedButton.class).withAccessibleNameContaining("Edit");  // "Edit User 3", "Edit User 4", …
```

Prefer the exact form: a lookup that matches more than you meant fails with "found 2" the day someone adds a "Save draft" button.

### By technical attributes (fallback)

When a component has nothing a user can read, give it a test id in the view (`component.setTestId("nickname")`) and find it by that:

| Filter | Matches |
|---|---|
| `withTestId("nickname")` | `data-testid`, set with `Component.setTestId` |
| `withId("receipt")` | `id`, set with `Component.setId` |
| `withClassName("summary")` | a CSS class name |
| `withTheme("primary")` | a theme variant |
| `withAttribute("name", "value")` | any attribute |
| `withProperty("label", "Service")` | any property |

`withClassName`, `withTheme` and `withAttribute` depend on how the view is styled, not on what it does. Use them for plain HTML (cards, layouts), not for fields and buttons:

```java
--8<-- "unified-test-demo/src/test/java/demo/TripTest.java:filtersByClassName"
```

### Combining filters

Filters add up: a component must match all of them. Each filter returns a new Wrapper and leaves the one it was called on as it was, so one Wrapper can be narrowed in several ways:

```java
UnifiedButton delete = page.find(UnifiedButton.class).withText("Delete");
delete.withTheme("error").click();              // the red one
assertThat(delete).hasCount(2);                 // `delete` itself is unchanged
```

### Only visible components

A lookup includes hidden components (`setVisible(false)`), as a browser's page does. Add `.visible()` when visibility is the point, for example "the button is gone after the click":

```java
--8<-- "unified-test-demo/src/test/java/demo/ApprovalTest.java:aHiddenButtonIsStillFoundButNotVisible"
```

## 3. Using the Wrapper

### Exactly one, every time

Every action (`setValue`, `click`), read (`getValue()`) and assertion finds the component again and waits up to the timeout (10 s by default) until **exactly one** component matches. So:

- you can declare a Wrapper **before** its component appears, and use it once it does;
- a Wrapper **never goes stale**: after the view re-renders, it finds the new component;
- keep a Wrapper in a variable and reuse it for the whole test.

```java
UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");  // no search yet
name.setValue("Alice");                  // searches, waits until it is there and enabled, types
assertThat(name).hasValue("Alice");      // searches again
```

When no component or several match, the call fails with a `UnifiedAssertionError` that says what it expected, what it found, and the components that nearly matched, with a suggestion when one is clearly what you meant ([Reading reports and failures](reports-and-failures.md#the-failure-message)).

### Is it there? How many?

Checks are retrying assertions, like any other check. There is no boolean `exists()`:

| I want to check… | Write |
|---|---|
| it is there | `assertThat(wrapper).exists()` |
| it is gone | `assertThat(wrapper).doesNotExist()` |
| how many match | `assertThat(wrapper).hasCount(2)` |

```java
--8<-- "unified-test-demo/src/test/java/demo/CheckoutTest.java:countsMatches"
```

### Several matches

There is no `first()`. When several components match, either the filter is not precise enough (add a filter or a [scope](#scopes)), or you mean all of them: `all()` returns them as lazy Wrappers by position, for a loop.

```java
--8<-- "unified-test-demo/src/test/java/demo/DashboardTest.java:loopsOverTheMatches"
```

`all()` reads the matches **now**, without waiting: check the count with `hasCount(n)` first, and use `all()` only to loop.

## Scopes

A page has many "City" fields, "Save" buttons and "Email" fields. Narrow the search to a part of the page by calling `find(..)` on a **Scope** instead of on the page:

| Scope | Reach it with | Contains |
|---|---|---|
| `UnifiedPage` | the test parameter | the whole page |
| `UnifiedDialog` | `page.find(UnifiedDialog.class).withHeaderTitle(..)` | the dialog's content |
| `UnifiedContainer` | `page.find(UnifiedContainer.class).withTestId("billing")` | any layout or component, by test id or id |
| plain HTML Wrappers | `page.find(UnifiedSection.class)…`, `UnifiedDiv`, … | that element |
| `findComponent(..)` result | `page.findComponent(VerticalLayout.class)…` | that component |
| `UnifiedDetails.content()` | `details.content()` | the Details content |
| `UnifiedTabSheet.content()` | `tabs.content()` | the selected tab's content |
| Grid cells, header, row details | `row.cell("Actions")`, `column.header()`, `row.details()` | see [Grid](grid.md) |

A Scope is a Wrapper too: a lookup inside it finds the Scope again first, so a missing dialog fails with the dialogs that are there.

### A dialog

```java
--8<-- "unified-test-demo/src/test/java/demo/DialogTest.java:helpers"

--8<-- "unified-test-demo/src/test/java/demo/DialogTest.java:searchesInsideTheDialog"
```

### A form section

Two forms with the same labels: give each layout a test id or id in the view, and search inside it.

```java
--8<-- "unified-test-demo/src/test/java/demo/CheckoutTest.java:picksTheFieldThroughItsContainer"
```

Scopes nest:

```java
--8<-- "unified-test-demo/src/test/java/demo/CheckoutTest.java:containersNest"
```

### Details and TabSheet content

```java
--8<-- "unified-test-demo/src/test/java/demo/ExpenseTest.java:opensDetails"
```

A TabSheet's `content()` follows the selected tab: the same Scope searches the new tab after `selectTab(..)`.

```java
--8<-- "unified-test-demo/src/test/java/demo/AccountTest.java:contentFollowsTheSelectedTab"
```

### Plain HTML

```java
--8<-- "unified-test-demo/src/test/java/demo/ExpenseTest.java:searchesInPlainHtmlContainers"
```

## Components without a Wrapper

`findComponent(FlowClass.class)` finds any Flow component with a `@Tag`, without writing a Wrapper. It returns a `UnifiedGenericComponent` with the shared filters (label, text, accessible name, test id, …) and the [Unified Element](../spec/06-wrappers.md#unified-element) underneath:

```java
--8<-- "unified-test-demo/src/test/java/demo/AddonTest.java:anyComponentWithoutAWrapper"
```

It is handy for reading text from a `Span`, `H2` or `Paragraph`:

```java
assertThat(page.findComponent(Span.class).withId("status")).hasText("Approved");
assertThat(page.findComponent(H2.class).withText("Admin area of alice")).isVisible();
```

A component without a tag (a `Composite`, `Component.class`) can't be found by a browser, so `findComponent` refuses it at once on every Testing Tool. Find its root component instead, or write a [Wrapper](custom-wrappers.md) with `tag = "…"`.

## Custom filters

When a filter you need doesn't exist, build it from `withProperty(name, value)` or `where(Predicate<UnifiedElement>)`:

```java
--8<-- "unified-test-demo/src/test/java/demo/AddonTest.java:customFilters"
```

`withValue(4)` is a default method on the Wrapper, written once with `where(..)`; see [Wrappers for your own components](custom-wrappers.md#custom-filters). Prefer a named filter on the Wrapper to a `where(..)` in the test: the reports can't read a condition the test writes inline, and show it as a gap.

## Cheat sheet

| I want… | Write |
|---|---|
| the field labelled *Name* | `page.find(UnifiedTextField.class).withLabel("Name")` |
| the *Save* button | `page.find(UnifiedButton.class).withText("Save")` |
| an icon button | `page.find(UnifiedButton.class).withAccessibleName("Delete account")` |
| the *Save* button of the dialog | `dialog.find(UnifiedButton.class).withText("Save")` |
| the *City* field of the billing form | `page.find(UnifiedContainer.class).withTestId("billing").find(UnifiedTextField.class).withLabel("City")` |
| the open dialog | `page.find(UnifiedDialog.class)` |
| the notification saying *Saved* | `page.find(UnifiedNotification.class).withText("Saved")` |
| check there are two cards | `assertThat(page.find(UnifiedMetricCard.class)).hasCount(2)` |
| check a button is gone | `assertThat(page.find(UnifiedButton.class).withText("Archive").visible()).doesNotExist()` |
| text in a `Span` | `page.findComponent(Span.class).withId("status").getText()` |
| a Grid row | `grid.row().withCellText("Name", "Alice")` ([Grid](grid.md)) |
