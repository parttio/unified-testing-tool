# 6. Wrappers

Decided in [#6](https://github.com/parttio/unified-testing-tool/issues/6) (Core Set), [#12](https://github.com/parttio/unified-testing-tool/issues/12) (wrapper shape) and [#18](https://github.com/parttio/unified-testing-tool/issues/18) (custom and add-on wrappers) and #199 (filters on the Wrapper); see [ADR 0002](../adr/0002-unified-wrapper-names-and-class-literal-lookups.md), [ADR 0003](../adr/0003-wrappers-are-interfaces-shipped-in-the-addon-jar.md) and [ADR 0007](../adr/0007-wrappers-carry-their-filters.md).

## Core Set

One component per interaction pattern. Every other component follows the same pattern.

| Pattern | Wrapper | Status |
|---|---|---|
| Text value | `UnifiedTextField`, `UnifiedTextArea` (PasswordField, EmailField follow) | implemented |
| Typed value | `UnifiedNumberField`, `UnifiedIntegerField`, `UnifiedBigDecimalField`, `UnifiedDatePicker`, `UnifiedDateTimePicker` | implemented; `UnifiedDatePicker.typeText(..)` and `UnifiedDateTimePicker.typeText(date, time)` type unparsed text; `UnifiedDateTimePicker.typeText(LocalDate, time)` types the date in the field's own format, for incomplete input on every Testing Tool |
| Boolean value | `UnifiedCheckbox` | implemented |
| Single selection | `UnifiedComboBox`, `UnifiedRadioButtonGroup` | implemented |
| Multi selection | `UnifiedMultiSelectComboBox` | implemented |
| Action | `UnifiedButton`, `UnifiedMenuBar` | implemented: `clickItem("File", "Export", "PDF")` opens the menus as a user does; items by Item Name (an icon-only item by its `aria-label`); `isItemEnabled(..)` |
| Overlays | `UnifiedDialog`, `UnifiedNotification` | implemented |
| Disclosure | `UnifiedDetails` | implemented: `open()`, `close()`, `content()` Scope |
| Files | `UnifiedUpload` | implemented: `upload(name, type, bytes)` waits until the server has the file |
| Chat | `UnifiedMessageInput`, `UnifiedMessageList` | implemented: `send(text)` types and submits as a user does; a message's text is the one the application gave it (the Markdown source, not the rendered text), so every Testing Tool reads the same; `hasMessageCount(n)` and `hasMessageContaining(..)` wait for a reply ([#357](https://github.com/parttio/unified-testing-tool/issues/357)) |
| Data | `UnifiedGrid` | in the sketch: [07-grid.md](07-grid.md) |
| Navigation | `UnifiedTabSheet`, `UnifiedTabs` | implemented: `selectTab(name)`, `getSelectedTab()`; a tab's name is its Item Name (the text of a tab holding a `RouterLink`); a TabSheet's `content()` Scope follows the selected tab |
| (layouts, for scoping) | `UnifiedContainer` | implemented: any component, found by test id or id; `click()` for a layout with a click listener ([#358](https://github.com/parttio/unified-testing-tool/issues/358)) |
| (plain HTML, for scoping, text and clicks) | `UnifiedDiv`, `UnifiedSpan`, `UnifiedParagraph`, `UnifiedSection`, `UnifiedNav`, `UnifiedHeader`, `UnifiedFooter`, `UnifiedMain`, `UnifiedArticle`, `UnifiedAside` | implemented: bound to their tag, so one browser query; Scopes; `click()`, as each is a Flow `ClickNotifier` ([#358](https://github.com/parttio/unified-testing-tool/issues/358)) |

Input fields share `UnifiedField` (label, helper text, validation getters). Typed values are Java types: `Double` for a NumberField, `Integer`, `BigDecimal` (compare with `compareTo`: the scale is what the user typed), `LocalDate` for a DatePicker, `LocalDateTime` for a DateTimePicker, `null` when empty. Selection fields identify items by the label the user sees (the item label generator's result): `selectItem("Germany")`, `getSelectedItem()`, `getItems()`. A select fails when the item doesn't exist (listing the items) or isn't selected afterwards. A combo box's `getItems()` returns every item, paging through a lazy data provider; on Selenium only the items its drop-down loads at once (first page, 50 by default; [#127](https://github.com/parttio/unified-testing-tool/issues/127)).

Rules: a Core Set component is supported by all three Testing Tools; no commercial components (Grid Pro, Charts, Spreadsheet, Board, Crud); no layouts.

## Naming

Prefix `Unified`. The plain and suffixed names are taken by classes a test may import at the same time:

| Name | Taken by |
|---|---|
| `TextField` | Flow component |
| `TextFieldElement` | Dramafinder **and** TestBench |
| `TextFieldTester`, `TextFieldLocator` | Browserless |

## Shape

```mermaid
flowchart TD
    W["UnifiedTextField<br/>(interface, default methods)"] --> E[UnifiedElement]
    E --> B[Browserless: server-side component]
    E --> P[Playwright: DOM]
    E --> S[Selenium: DOM]
```

A wrapper is an **interface with default methods** over `element()`. The Unified API implements it at runtime. Authors write no implementation class. A Wrapper carries its own filters: it lists the filter mixins it accepts, with itself as the type parameter, and the Unified API implements them too.

```java
@UnifiedComponent(TextField.class)
public interface UnifiedTextField extends UnifiedField,
        CommonFilters<UnifiedTextField>, LabelFilter<UnifiedTextField>, PlaceholderFilter<UnifiedTextField> {

    default void setValue(String value) { element().setValueAsUser(value); }          // action, Auto-Wait
    default String getValue() { return element().getProperty("value"); }             // getter, immediate
}
```

| Part | Rule |
|---|---|
| Filters | Mixins in `com.vaadin.testing.unified.query`: `CommonFilters<W>` (every Wrapper: accessible name, test id, id, `visible()`, `where(..)`, `all()`, …), `LabelFilter<W>`, `TextFilter<W>`, `PlaceholderFilter<W>`. Each filter returns a new Wrapper of type `W` and leaves the one it was called on unchanged. Type-specific filters are default methods built from `where(..)` or `withProperty(..)` ([Custom filters](#custom-filters)). |
| Actions | Auto-Wait (`setValue`, `clear`, `click`, …) |
| Getters | Immediate, no retry |
| Assertions | Static `assertThat(wrapper)` in `UnifiedAssertions`, retrying |
| Every wrapper | `element()`, `unwrap`, `tryUnwrap`, `withTimeout`, `isVisible`, `isEnabled` |
| Scopes | `UnifiedDialog`, `UnifiedDetails`, `UnifiedContainer`, the plain HTML wrappers and any `findComponent(..)` result also have `find` |

## Unified Element

The portable layer every wrapper is written on:

| Group | Operations |
|---|---|
| Read | `getTag`, `getText`, `getAccessibleName`, `getProperty…`, `getAttribute`, `getInlineStyle`, `isVisible`, `isEnabled`, `getVisibleText` and `getCssValue` (browser only) |
| Write | `setProperty` |
| User actions (Auto-Wait) | `click`, `setValueAsUser`, `focus`, `blur`, `pressKey`, `dispatchEvent` |
| Structure | `findChildren(tag)`, `children()`, `findDescendants(tag)` |

| Operation | Meaning on every Testing Tool |
|---|---|
| `getText()` | the DOM text (`textContent`), whitespace collapsed and trimmed. CSS doesn't change it: `text-transform`, hidden descendants and line breaks between flex items don't count. A tooltip (`setTooltipText`) isn't part of it, though its text sits in the DOM in a browser. Browserless reads the same text from the server-side elements. |
| `getVisibleText()` | the text as rendered (`innerText`); browser only |
| `getAccessibleName()` | what a screen reader announces, in [4. Locating](04-locating.md#filters)'s order: what `withAccessibleName(..)` matches. Read for Near Misses and by tests |
| `getInlineStyle(name)` | the value the inline style declares, as written (`var(--gap)`), or null |
| `findChildren(tag)` | direct light-DOM children with this tag (`*`: any), in order; text nodes don't count; never a web component's shadow DOM. `children()` is `findChildren("*")`. |
| `findDescendants(tag)` | every light-DOM element below, in document order. Web components add light DOM of their own in a browser (a text field's input and label), which Browserless doesn't have. |
| `setValueAsUser` on a native `<input>` | sets the value and fires `input` and `change`, for Flow's `Input` and a bare `Element("input")` |

Browserless acts on the server-side element and component, firing events as from the browser. Browser Testing Tools act on the DOM. **Limitation:** on Browserless a property is the server-side value; it equals the browser value only for properties Flow synchronises.

## Custom and add-on components

### Identifying the component

`@UnifiedComponent(Rating.class)`: the Flow class for Browserless; the tag for browsers is read from its `@Tag`. Add `tag = "…"` only for `Composite`-based components.

A component built from a common one (`MetricCard extends VerticalLayout`) has that one's tag: a browser can't tell it apart by class. `selector` adds what tells it apart, applied by every Testing Tool:

```java
@UnifiedComponent(value = MetricCard.class, selector = ".metric-card")
public interface UnifiedMetricCard extends UnifiedWrapper, CommonFilters<UnifiedMetricCard>,
        TextFilter<UnifiedMetricCard> { … }
```

`selector` is a compound CSS selector of `.class`, `#id`, `[attribute="value"]` and `[theme~="value"]`, without spaces. Wrapper interfaces must be public, and so must the classes they are nested in.

### Per-tool overrides

When the Unified Element is not enough (canvas, overlays, content written by client-side JavaScript, which Browserless doesn't run):

```java
@UnifiedComponent(value = Badge.class,
        overrides = @ToolImplementation(tool = TestingTool.BROWSERLESS, value = UnifiedBadgeBrowserless.class))
public interface UnifiedBadge extends UnifiedWrapper, CommonFilters<UnifiedBadge> {
    default int getCount() { return Integer.parseInt(element().getText().split(" ")[0]); }  // "3 new"
}

public interface UnifiedBadgeBrowserless extends UnifiedBadge {
    @Override
    default int getCount() { return unwrap(Badge.class).getCount(); }   // Escape Hatch: the Java component
}
```

| Rule | Detail |
|---|---|
| Shape | an **interface extending the wrapper**; it redefines only the methods that need the tool's own API, the rest is inherited |
| When used | on that Testing Tool, the wrapper returned by `find(..)` implements the override (`instanceof UnifiedBadgeBrowserless`) |
| Wrong shape | a class, or an interface not extending the wrapper: fails at the first `find(..)` with a message |

Implemented; demo: `demo.addon` (component) and `demo.addon.testing` (wrappers, in the main jar).

### Custom filters

Default methods on the Wrapper, returning the Wrapper, built from `withProperty`, `withAttribute` or `where(Predicate<UnifiedElement>)`:

```java
@UnifiedComponent(Rating.class)
public interface UnifiedRating extends UnifiedWrapper, CommonFilters<UnifiedRating>, LabelFilter<UnifiedRating> {
    default UnifiedRating withValue(int stars) {
        return where(e -> Integer.valueOf(stars).equals(e.getPropertyInteger("value")));
    }
}
```

A custom filter reads as itself in User Steps and failures (`dialog with header title “Edit person”`, `Did you mean find(UnifiedDialog.class).withHeaderTitle("Edit person")`), not as `where(..)`. A `where(..)` the test calls directly is opaque: it shows as a gap line in the reports ([12. Readable tests](12-readable-tests.md#user-steps)).

### In the reports

Every action of an add-on Wrapper is a User Step ([12. Readable tests](12-readable-tests.md#user-steps)). Its wording comes from the Wrapper:

| Part | Default | Override |
|---|---|---|
| Component kind | the Wrapper's name in words, without `Unified` (`UnifiedRating` → “rating”) | `@UnifiedComponent(value = Rating.class, kind = "star rating")` |
| Verb | the core verb table (`click`, `setValue` → “fill”, …), else the method name in words (`setRating(4)` → `set rating rating “Food” with “4”`) | `@UserStepVerb("rate")` on the method: `rate star rating “Food” with “4”`; `{value}` places the arguments: `@UserStepVerb("give {value} stars to")` reads `give “4” stars to star rating “Food”` |

### Three ways to test an add-on

| Effort | How | When |
|---|---|---|
| none | `page.findComponent(Rating.class)`: shared filters + Unified Element | any add-on, today |
| low (recommended) | wrapper interface **in the add-on's own jar**, Unified API as `optional` dependency | add-on authors; works with single-module Vaadin Directory add-ons |
| shared | community bundle (e.g. `unified-addons`) | popular add-ons without their own wrapper |

The Core Set is built with this same mechanism, with overrides where needed.

## Requirements on Testing Tool adapters

- Implement every Unified Element operation with the same meaning.
- Implement Wrapper interfaces and their filter mixins at runtime (`InvocationHandler.invokeDefault`) and honour `@ToolImplementation` overrides.
- Expose the accessible name through the Unified Element (`getAccessibleName()`).
- Read the tag from `@Tag` unless `@UnifiedComponent.tag` is set.
