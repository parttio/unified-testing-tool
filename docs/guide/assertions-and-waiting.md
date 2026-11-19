# Assertions and waiting

In a browser, the page updates a moment after the server answers. A test that reads a value too early fails now and then. The Unified API removes the waits from your test: actions wait, Wrappers wait, and assertions retry. Spec: [5. Waiting](../spec/05-waiting.md).

## Assert with `assertThat`

```java
import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

assertThat(name).hasValue("Alice").isValid();                         // retries until it holds
assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alice");
assertThat(page).isAt(ProfileView.class);
```

Each assertion retries until it holds or the timeout (10 s by default) passes, then fails with a `UnifiedAssertionError` that names what it expected and the values it saw while waiting ([Reading reports and failures](reports-and-failures.md#the-failure-message)). Chained assertions each retry. Each assertion is reported as a User Step: `see notification “Saved Alice”`.

| Wrapper | Assertions (besides the common ones) |
|---|---|
| every Wrapper | `exists()`, `doesNotExist()`, `hasCount(n)`, `isVisible()`, `isHidden()`, `isEnabled()`, `isDisabled()`, `hasText(..)`, `hasTextContaining(..)`, `hasProperty(name, value)`, `hasCssValue(..)` (browser only) |
| fields | `hasValue(..)`, `isEmpty()`, `isValid()`, `isInvalid()`, `hasErrorMessage(..)`, `isRequired()`, `hasLabel(..)` |
| `UnifiedCheckbox` | `isChecked()`, `isNotChecked()`, `isIndeterminate()`, `hasLabel(..)` |
| `UnifiedComboBox`, `UnifiedRadioButtonGroup` | `hasSelectedItem(..)`, `hasNoSelectedItem()`, `hasItems(..)` |
| `UnifiedMultiSelectComboBox` | `hasSelectedItems(..)`, `hasNoSelectedItems()`, `hasItems(..)` |
| `UnifiedDialog` | `isOpen()`, `isClosed()`, `hasHeaderTitle(..)` |
| `UnifiedNotification` | `isOpen()`, `hasText(..)` |
| `UnifiedTabSheet`, `UnifiedTabs` | `hasSelectedTab(..)`, `hasTabs(..)` |
| `UnifiedDetails` | `isOpen()`, `isClosed()`, `hasSummaryText(..)` |
| `UnifiedGrid` | `hasRowCount(..)`, `isEmpty()` |
| grid row | `exists()`, `doesNotExist()`, `hasCount(n)`, `hasCellText(column, text)`, `isSelected()`, `isNotSelected()`, `hasDetailsOpen()`, `hasDetailsClosed()` |
| grid column | `hasCellTexts(..)`, `isSortedAscending()`, `isSortedDescending()` |
| `UnifiedPage` | `isAt(View.class)`, `hasPath(..)`, `hasQueryParameter(..)`, `hasNoErrors()` ([what the application logged](reports-and-failures.md#what-the-application-logged)) |

## Getters don't retry

`getValue()`, `getText()`, `getItems()` and `all()` read the state **now**. Use them to get a value you need, not to check one:

```java
assertThat(name).hasValue("Alice");                // good: retries
assertEquals("Alice", name.getValue());            // flaky in a browser after an async update
```

## Waiting for anything else

`page.waitUntil(..)` polls a condition with the same timeout. Use it for what no assertion covers, such as a value of your own Wrapper:

```java
--8<-- "unified-test-demo/src/test/java/demo/AddonTest.java:wrapperWithoutOverride"
```

On Browserless, retrying assertions and `waitUntil` also run pending `UI.access` tasks and signal effects between attempts, so views updated from background threads behave as in a browser.

## Timeouts

| Scope | How |
|---|---|
| the whole run | `vaadin.testing.timeout=20s` in `junit-platform.properties`, or `-Dvaadin.testing.timeout=20s` |
| one Wrapper | `wrapper.withTimeout(Duration.ofSeconds(30))` |

`withTimeout` returns a `UnifiedWrapper`; cast it back to use the Wrapper's own methods:

```java
UnifiedButton slow = (UnifiedButton) page.find(UnifiedButton.class).withText("Export")
        .withTimeout(Duration.ofSeconds(30));
slow.click();
```

## Checking that something is there, or gone

Whether a component is there, and how many are, are assertions too: they retry like the others. There is no boolean `exists()`.

```java
assertThat(page.find(UnifiedButton.class).withText("Approve")).exists();                // at least one
assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist(); // none shown
assertThat(page.find(UnifiedMetricCard.class)).hasCount(2);                               // exactly two
```

A lookup includes hidden components. To check that a component is no longer shown:

```java
assertThat(approve).isHidden();                                          // still in the page, hidden
assertThat(dialog).isClosed();                                           // dialogs and notifications
assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist();
```

`doesNotExist()` reads as `see no button “Approve” (visible)` in the reports.
