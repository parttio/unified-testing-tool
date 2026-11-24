# Finding components

`find(..)` returns the **Wrapper** for a kind of component, and its filters narrow it down:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:anatomy"
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

Wrappers live in `com.vaadin.testing.unified.component`: `UnifiedTextField`, `UnifiedTextArea`, `UnifiedEmailField`, `UnifiedPasswordField`, `UnifiedNumberField`, `UnifiedIntegerField`, `UnifiedBigDecimalField`, `UnifiedDatePicker`, `UnifiedDateTimePicker`, `UnifiedCheckbox`, `UnifiedComboBox`, `UnifiedMultiSelectComboBox`, `UnifiedRadioButtonGroup`, `UnifiedButton`, `UnifiedMenuBar`, `UnifiedLink`, `UnifiedDialog`, `UnifiedNotification`, `UnifiedDetails`, `UnifiedUpload`, `UnifiedMessageInput`, `UnifiedMessageList`, `UnifiedGrid`, `UnifiedTabSheet`, `UnifiedTabs`, `UnifiedContainer` and the plain HTML Wrappers (`UnifiedDiv`, `UnifiedSpan`, `UnifiedSection`, …). For any other component, see [Components without a Wrapper](#components-without-a-wrapper).

## 2. Filters

### By what the user sees (prefer these)

| Filter | Finds | Example |
|---|---|---|
| `withLabel("Email")` | a field by its label, or by the label of its FormLayout item | `page.find(UnifiedTextField.class).withLabel("Email")` |
| `withText("Save")` | a button, notification, … by its text | `page.find(UnifiedButton.class).withText("Save")` |
| `withAccessibleName("Delete account")` | what a screen reader announces: `aria-label`, `setAriaLabel(..)`, label or text | an icon-only button, a field without a visible label |
| `withPlaceholder("Nickname")` | a field by its placeholder | `page.find(UnifiedTextField.class).withPlaceholder("Nickname")` |
| `withHeaderTitle("Delete?")` | a dialog by its header | `page.find(UnifiedDialog.class).withHeaderTitle("Change email")` |
| `withTab("Security")` | a TabSheet or Tabs that has this tab | `page.find(UnifiedTabSheet.class).withTab("Security")` |
| `withItem("File")` | a MenuBar that has this top-level item | `page.find(UnifiedMenuBar.class).withItem("File")` |
| `withSummaryText("Advanced")` | a Details by its summary | `page.find(UnifiedDetails.class).withSummaryText("Advanced")` |

A field in a FormLayout item has no label of its own: `formLayout.addFormItem(field, "Nickname")` labels the item. `withLabel` finds it by that label too, on every Testing Tool, and a failure lists it under that name:

```java
--8<-- "unified-test-demo/src/test/java/demo/FormItemLabelTest.java:formItemLabel"
```

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
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:containing"
```

Prefer the exact form: a lookup that matches more than you meant fails with "found 2" the day someone adds a "Save draft" button.

### By technical attributes (fallback)

When a component has nothing a user can read, give it a test id in the view (`component.setTestId("nickname")`) and find it by that:

| Filter | Matches |
|---|---|
| `withTestId("nickname")` | `data-testid`, set with `Component.setTestId` |
| `withId("receipt")` | `id`, set with `Component.setId` |
| `withClassName("summary")` | a CSS class name |
| `withClassNames("trip-card", "selected")` | every one of these CSS class names |
| `withTheme("primary")` | a theme variant |
| `withAttribute("name", "value")` | any attribute |
| `withProperty("label", "Service")` | any property |

`withClassName`, `withTheme` and `withAttribute` depend on how the view is styled, not on what it does. Use them for plain HTML (cards, layouts), not for fields and buttons:

```java
--8<-- "unified-test-demo/src/test/java/demo/TripTest.java:filtersByClassName"
```

`withClassName` takes one class name, like Flow's `addClassName`: `withClassName("trip-card selected")` throws `IllegalArgumentException` on every Testing Tool. To require several classes, use `withClassNames`, which splits each argument on spaces like Flow's `addClassNames`, or chain `withClassName` calls:

```java
--8<-- "unified-test-demo/src/test/java/demo/TripTest.java:filtersBySeveralClassNames"
```

### Combining filters

Filters add up: a component must match all of them. Each filter returns a new Wrapper and leaves the one it was called on as it was, so one Wrapper can be narrowed in several ways:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:narrowing"
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
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:lazy"
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

## What is there to find?

`describe()` on the page, or on any Scope, returns the **Findable View**: every component a lookup can reach, one line each, with the lookup that singles it out. Print it while writing a test, then paste the lookups:

```java
page.navigate(ProfileView.class);
System.out.println(page.describe());
```

```
Findable view of “/profile” on browserless: 7 components
text field “Name”                       page.find(UnifiedTextField.class).withLabel("Name")
text field “Email”                      page.find(UnifiedTextField.class).withLabel("Email")
text field (no name; placeholder “Nickname”)  page.find(UnifiedTextField.class).withPlaceholder("Nickname")
button “Save”                           page.find(UnifiedButton.class).withText("Save")
button “Delete account”                 page.find(UnifiedButton.class).withAccessibleName("Delete account")
button “Archive” (disabled)             page.find(UnifiedButton.class).withText("Archive")
button “Change email”                   page.find(UnifiedButton.class).withText("Change email")
```

It reads the same on every Testing Tool. Two components with the same name get a lookup through the container that tells them apart (`page.find(UnifiedContainer.class).withTestId("billing").find(..)`), or the line says `not unique`. A `(no name)` line is a field or button that users of a screen reader can't name either. *(unreleased)* Plain HTML (`div`, `span`, `article`, …) is listed when it says it is clickable, with a `role` such as `button` or `tab`, or `tabindex="0"`: `span “Week of 2026-11-16” (role button)`. A `<span>` with only a click listener is not listed, though `page.find(UnifiedSpan.class).withText(..).click()` finds and clicks it; give it a role, which screen readers need too. `describe()` is not a User Step, and its format may change: don't assert on it. A failed test writes the page's view next to its evidence ([Reading reports and failures](reports-and-failures.md#the-findable-view)). Spec: [12. Readable tests, Findable View](../spec/12-readable-tests.md#findable-view).

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
| a Template *(unreleased)* | `page.findTemplate(SearchBar.class)`, or a Wrapper with `TemplateScope` | its shadow root and light DOM: see [Templates](#templates) |

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

## Templates

> **Unreleased:** on `main`, not in `0.1.0-alpha4`. See [what is unreleased](../releases/unreleased.md).

A `LitTemplate`'s `@Id` components live in its shadow root, and no lookup enters a shadow root by itself: `page.find(UnifiedButton.class).withId("new")` doesn't find the button of a search bar template. Enter the Template first, one at a time, with `findTemplate(..)` or with a Wrapper of your own that extends `TemplateScope`:

```java
--8<-- "unified-test-demo/src/test/java/demo/TemplateTest.java:wrapper"
```

```java
--8<-- "unified-test-demo/src/test/java/demo/TemplateTest.java:entersNestedTemplates"
```

- **Find by id.** Inside a `LitTemplate`, every Testing Tool finds what the server knows: the `@Id` components and what is under them. Text, labels and attributes written in the template's HTML are empty on the server, so `withText("New order")` finds the button in a browser and not on Browserless. `withId("new")` finds it everywhere.
- **Ids are per Template.** The same id in two Templates is not ambiguous: the lookup only sees the Template it entered.
- **Slotted content** (what the view adds to the Template) is found inside it, and on the page, once.
- **A web component that renders its content in the browser** (not a `LitTemplate`) is entered the same way, but Browserless has nothing inside it: such a test declares `@RequiresBrowser`.

When a lookup finds nothing, its failure names the Templates in which it would, with the lookup that enters them, and the Findable View lists each Template as one line. Spec: [4. Locating, Templates](../spec/04-locating.md#templates).

## Components without a Wrapper

`findComponent(FlowClass.class)` finds any Flow component with a `@Tag`, without writing a Wrapper. It returns a `UnifiedGenericComponent` with the shared filters (label, text, accessible name, test id, …) and the [Unified Element](../spec/06-wrappers.md#unified-element) underneath:

```java
--8<-- "unified-test-demo/src/test/java/demo/AddonTest.java:anyComponentWithoutAWrapper"
```

It is handy for reading text from a `Span`, `H2` or `Paragraph`:

```java
--8<-- "unified-test-demo/src/test/java/demo/GuideExamples.java:flowComponents"
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
