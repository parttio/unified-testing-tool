# Unified API reference

Every Wrapper of the Unified API with its filters, actions, reads and checks, and the section of the developer guide that shows it. Generated from the code; shipped in the `unified-test-api` jar as `META-INF/unified/api-reference.md`.

- **Find** a component with `page.find(UnifiedX.class)` and filters: `page.find(UnifiedTextField.class).withLabel("Name")`. A Wrapper is lazy: it looks its component up again on every use, so keep it in a variable and reuse it.
- **Actions** wait until the component is visible and enabled, act, then wait for the server (Auto-Wait). Each is a User Step in the reports, worded as listed here, with the component's name and the values where the "…" are.
- **Reads** return what is there right now, without waiting. Assert with a check instead when you can.
- **Checks** are `assertThat(wrapper).…` from `UnifiedAssertions`; they retry until the timeout and are User Steps (`see …`).
- A component without a Wrapper: `page.findComponent(FlowClass.class)`, with the filters of every Wrapper.
- **Evidence**: a failed test leaves a screenshot (browser) or the component tree (Browserless) under `target/surefire-reports/unified-evidence/`. For every User Step, failed or not, set `vaadin.testing.user-steps.snapshots=true`; for Playwright's trace (`trace.zip`), set `vaadin.testing.playwright.trace=on` or `on-failure`. Every `vaadin.testing.*` setting, with its default: the [configuration guide](https://parttio.github.io/unified-testing-tool/guide/configuration/).

## The page: `UnifiedPage`

Guide: [Navigation, users and windows](https://parttio.github.io/unified-testing-tool/guide/navigation-and-users/).

- **Actions:** `checkLayout()`, `navigate(Class<? extends Component>)`, `navigate(Class<? extends Component>, QueryParameters)`, `navigate(Class<? extends Component>, RouteParameters)`, `navigate(Class<? extends Component>, RouteParameters, QueryParameters)`, `navigate(Class<C>, T)`, `navigate(String)`, `pressKey(String)`, `reload()`, `screenshot(Path)`, `setViewport(ViewportSize)`, `setViewport(int, int)`, `step(String, Runnable)`, `waitUntil(BooleanSupplier)`
- **Reads:** `hasRealBrowser() → boolean`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`, `openAsUser(String, String...) → UnifiedPage`, `openWindow() → UnifiedPage`, `secret(String) → String`, `tryUnwrap(Class<T>) → Optional<T>`, `unwrap(Class<T>) → T`, `viewport() → ViewportSize`
- **Checks, `assertThat(UnifiedPage)`:** `hasNoErrors()`, `hasNoHorizontalScroll()`, `hasNoLayoutFindings()`, `hasPath(String)`, `hasPathMatching(Pattern)`, `hasPathMatching(String)`, `hasQueryParameter(String, String)`, `hasTitle(String)`, `isAt(Class<? extends Component>)`

## Every Wrapper

- **Filters:** `all()`, `visible()`, `where(Predicate<UnifiedElement>)`, `withAccessibleName(String)`, `withAccessibleNameContaining(String)`, `withAttribute(String, String)`, `withClassName(String)`, `withClassNames(String...)`, `withId(String)`, `withProperty(String, Object)`, `withTestId(String)`, `withTheme(String)`
- **Methods:** `element() → UnifiedElement`, `isEnabled() → boolean`, `isVisible() → boolean`, `tryUnwrap(Class<T>) → Optional<T>`, `unwrap(Class<T>) → T`, `withTimeout(Duration) → UnifiedWrapper`
- **Checks, `assertThat(UnifiedWrapper)`:** `doesNotExist()`, `exists()`, `hasCount(int)`, `hasCssValue(String, String)`, `hasProperty(String, Object)`, `hasText(String)`, `hasTextContaining(String)`, `isDisabled()`, `isEnabled()`, `isHidden()`, `isInViewport()`, `isNotClipped()`, `isVisible()`

## Wrappers

### `UnifiedAppLayout`

App layout, Flow's `AppLayout`. Guide: [Working with components, App layout](https://parttio.github.io/unified-testing-tool/guide/components/#app-layout).

- **Actions**, each with the User Step it reports:
  - `closeDrawer()`: close drawer app layout “…”
  - `openDrawer()`: open drawer app layout “…”
  - `toggleDrawer()`: toggle drawer app layout “…”
- **Reads:** `isDrawerOpened() → boolean`, `isOverlay() → boolean`
- **Checks:** those of every Wrapper, and `isDrawerClosed()`, `isDrawerOpened()`, `isNotOverlay()`, `isOverlay()`

### `UnifiedArticle`

Article, Flow's `Article`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click article “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedAside`

Aside, Flow's `Aside`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click aside “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedBigDecimalField`

Decimal field, Flow's `BigDecimalField`. Guide: [Working with components, Typed values](https://parttio.github.io/unified-testing-tool/guide/components/#typed-values).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear decimal field “…”
  - `setValue(BigDecimal)`: fill decimal field “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → BigDecimal`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(BigDecimal)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedButton`

Button, Flow's `Button`. Guide: [Working with components, Buttons and menus](https://parttio.github.io/unified-testing-tool/guide/components/#buttons-and-menus).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click button “…”
- **Reads:** `getText() → String`
- **Checks:** those of every Wrapper

### `UnifiedCheckbox`

Checkbox, Flow's `Checkbox`. Guide: [Working with components, Checkbox](https://parttio.github.io/unified-testing-tool/guide/components/#checkbox).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`
- **Actions**, each with the User Step it reports:
  - `check()`: check checkbox “…”
  - `click()`: click checkbox “…”
  - `setChecked(boolean)`: check checkbox “…”
  - `uncheck()`: uncheck checkbox “…”
- **Reads:** `getHelperText() → String`, `getLabel() → String`, `isChecked() → boolean`, `isIndeterminate() → boolean`
- **Checks:** those of every Wrapper, and `hasLabel(String)`, `isChecked()`, `isIndeterminate()`, `isNotChecked()`

### `UnifiedComboBox`

Combo box, Flow's `ComboBox`. Guide: [Working with components, Selection: ComboBox, RadioButtonGroup, MultiSelectComboBox](https://parttio.github.io/unified-testing-tool/guide/components/#selection-combobox-radiobuttongroup-multiselectcombobox).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear combo box “…”
  - `selectItem(String)`: choose “…” in combo box “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getItems() → List<String>`, `getLabel() → String`, `getPlaceholder() → String`, `getSelectedItem() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasItems(String...)`, `hasLabel(String)`, `hasNoSelectedItem()`, `hasSelectedItem(String)`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedConfirmDialog`

Confirm dialog, Flow's `ConfirmDialog`. Guide: [Working with components, Dialogs and notifications](https://parttio.github.io/unified-testing-tool/guide/components/#dialogs-and-notifications).

- **Filters:** `withHeaderTitle(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `cancel()`: cancel “…”
  - `close()`: close confirm dialog “…”
  - `confirm()`: confirm “…”
  - `reject()`: reject “…”
- **Reads:** `getCancelText() → String`, `getConfirmText() → String`, `getHeaderTitle() → String`, `getRejectText() → String`, `getText() → String`, `isOpen() → boolean`
- **Checks:** those of every Wrapper, and `hasHeaderTitle(String)`, `isClosed()`, `isOpen()`

### `UnifiedContainer`

Container, Flow's `Component`. Guide: [Finding components, Scopes](https://parttio.github.io/unified-testing-tool/guide/finding-components/#scopes).

- **Actions**, each with the User Step it reports:
  - `click()`: click container “…”
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedDatePicker`

Date picker, Flow's `DatePicker`. Guide: [Working with components, Typed values](https://parttio.github.io/unified-testing-tool/guide/components/#typed-values).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear date picker “…”
  - `setValue(LocalDate)`: fill date picker “…” with “…”
  - `typeText(String)`: fill date picker “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → LocalDate`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(LocalDate)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedDateTimePicker`

Date-time picker, Flow's `DateTimePicker`. Guide: [Working with components, Typed values](https://parttio.github.io/unified-testing-tool/guide/components/#typed-values).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear date-time picker “…”
  - `setValue(LocalDateTime)`: fill date-time picker “…” with “…”
  - `typeText(LocalDate, String)`: fill date-time picker “…” with “…”, “…”
  - `typeText(String, String)`: fill date-time picker “…” with “…”, “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getValue() → LocalDateTime`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(LocalDateTime)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedDetails`

Details, Flow's `Details`. Guide: [Working with components, Tabs and Details](https://parttio.github.io/unified-testing-tool/guide/components/#tabs-and-details).

- **Filters:** `withSummaryText(String)`
- **Actions**, each with the User Step it reports:
  - `close()`: collapse details “…”
  - `open()`: expand details “…”
  - `toggle()`: toggle details “…”
- **Reads:** `getSummaryText() → String`, `isOpen() → boolean`
- **Also:** `content() → Scope`, `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper, and `hasSummaryText(String)`, `isClosed()`, `isOpen()`

### `UnifiedDialog`

Dialog, Flow's `Dialog`. Guide: [Working with components, Dialogs and notifications](https://parttio.github.io/unified-testing-tool/guide/components/#dialogs-and-notifications).

- **Filters:** `withHeaderTitle(String)`
- **Actions**, each with the User Step it reports:
  - `close()`: close dialog “…”
- **Reads:** `getHeaderTitle() → String`, `isOpen() → boolean`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper, and `hasHeaderTitle(String)`, `isClosed()`, `isOpen()`

### `UnifiedDiv`

Div, Flow's `Div`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click div “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedEmailField`

Email field, Flow's `EmailField`. Guide: [Working with components, Text fields](https://parttio.github.io/unified-testing-tool/guide/components/#text-fields).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear email field “…”
  - `setValue(String)`: fill email field “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(String)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedFooter`

Footer, Flow's `Footer`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click footer “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedGrid`

Grid, Flow's `Grid`. Guide: [Grid](https://parttio.github.io/unified-testing-tool/guide/grid/).

- **Actions**, each with the User Step it reports:
  - `deselectAll()`: deselect all rows in grid “…”
  - `selectAll()`: select all rows in grid “…”
- **Reads:** `getColumnHeaders() → List<String>`, `getRowCount() → int`
- **Also:** `column(String) → UnifiedGridColumn`, `column(int) → UnifiedGridColumn`, `columnWithId(String) → UnifiedGridColumn`, `describe() → String`, `row() → UnifiedGridRow`, `row(int) → UnifiedGridRow`
- **Checks:** those of every Wrapper, and `doesNotHaveColumn(String)`, `hasColumn(String)`, `hasColumnHeaders(String...)`, `hasRowCount(int)`, `isEmpty()`

#### `UnifiedGrid.UnifiedGridColumn`

- **Actions:** `sort(SortDirection)`
- **Reads:** `getCellTexts() → List<String>`, `getIndex() → int`, `getSortDirection() → SortDirection`
- **Also:** `grid() → UnifiedGrid`, `header() → Scope`
- **Checks, `assertThat(UnifiedGridColumn)`:** `hasCellTexts(String...)`, `isSortedAscending()`, `isSortedDescending()`

#### `UnifiedGrid.UnifiedGridRow`

- **Filters:** `selected()`, `withCellText(String)`, `withCellText(String, String)`, `withCellTextContaining(String, String)`
- **Actions:** `click()`, `deselect()`, `doubleClick()`, `select()`, `toggleDetails()`
- **Reads:** `getCellText(String) → String`, `getCellText(int) → String`, `getIndex() → int`, `isDetailsOpen() → boolean`, `isSelected() → boolean`
- **Also:** `all() → List<UnifiedGridRow>`, `cell(String) → Scope`, `cell(int) → Scope`, `details() → Scope`, `grid() → UnifiedGrid`
- **Checks, `assertThat(UnifiedGridRow)`:** `doesNotExist()`, `exists()`, `hasCellText(String, String)`, `hasCount(int)`, `hasDetailsClosed()`, `hasDetailsOpen()`, `isNotSelected()`, `isSelected()`

### `UnifiedHeader`

Header, Flow's `Header`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click header “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedIntegerField`

Integer field, Flow's `IntegerField`. Guide: [Working with components, Typed values](https://parttio.github.io/unified-testing-tool/guide/components/#typed-values).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear integer field “…”
  - `setValue(Integer)`: fill integer field “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → Integer`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(int)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedLink`

Link, any Flow component rendered as `<a>`. Guide: [Working with components, Links](https://parttio.github.io/unified-testing-tool/guide/components/#links).

- **Filters:** `withHref(String)`, `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click link “…”
- **Reads:** `getHref() → String`, `getText() → String`
- **Checks:** those of every Wrapper, and `hasHref(String)`

### `UnifiedLogin`

Login, Flow's `AbstractLogin`. Guide: [Working with components, Login](https://parttio.github.io/unified-testing-tool/guide/components/#login).

- **Filters:** `withTitle(String)`
- **Actions**, each with the User Step it reports:
  - `forgotPassword()`: press “Forgot password” in login “…”
  - `login(String, String)`: sign in as “…” in login “…”
- **Reads:** `getDescription() → String`, `getTitle() → String`, `isError() → boolean`, `isForgotPasswordButtonVisible() → boolean`, `isOpen() → boolean`
- **Checks:** those of every Wrapper, and `hasError()`, `hasNoError()`, `hasTitle(String)`, `isClosed()`, `isOpen()`

### `UnifiedMain`

Main, Flow's `Main`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click main “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedMenuBar`

Menu bar, Flow's `MenuBar`. Guide: [Working with components, Buttons and menus](https://parttio.github.io/unified-testing-tool/guide/components/#buttons-and-menus).

- **Filters:** `withItem(String)`
- **Actions**, each with the User Step it reports:
  - `clickItem(String, String...)`: click “… › …” in menu bar “…”
- **Reads:** `getItems() → List<String>`, `getItems(String, String...) → List<String>`, `getOverflowItems() → List<String>`, `isItemEnabled(String, String...) → boolean`
- **Checks:** those of every Wrapper, and `hasItem(String, String...)`, `hasItemDisabled(String, String...)`, `hasItemEnabled(String, String...)`, `hasItems(List<String>, String...)`, `hasItems(String...)`, `hasNoOverflowItems()`, `hasOverflowItems(String...)`

### `UnifiedMessageInput`

Message input, Flow's `MessageInput`. Guide: [Working with components, Chat](https://parttio.github.io/unified-testing-tool/guide/components/#chat).

- **Actions**, each with the User Step it reports:
  - `send(String)`: send “…” in message input “…”
- **Checks:** those of every Wrapper

### `UnifiedMessageList`

Message list, Flow's `MessageList`. Guide: [Working with components, Chat](https://parttio.github.io/unified-testing-tool/guide/components/#chat).

- **Reads:** `getMessageCount() → int`, `getMessageTexts() → List<String>`, `getMessages() → List<Message>`
- **Also:** `describe() → String`, `message() → UnifiedMessage`, `message(int) → UnifiedMessage`
- **Checks:** those of every Wrapper, and `hasMessageContaining(String)`, `hasMessageCount(int)`, `hasMessageTexts(String...)`, `isEmpty()`

#### `UnifiedMessageList.UnifiedMessage`

- **Filters:** `last()`, `withText(String)`, `withTextContaining(String)`, `withUserName(String)`
- **Reads:** `getIndex() → int`, `getText() → String`, `getUserName() → String`
- **Also:** `all() → List<UnifiedMessage>`, `list() → UnifiedMessageList`
- **Checks, `assertThat(UnifiedMessage)`:** `doesNotExist()`, `exists()`, `hasCount(int)`, `hasText(String)`, `hasTextContaining(String)`, `hasUserName(String)`

### `UnifiedMultiSelectComboBox`

Multi-select combo box, Flow's `MultiSelectComboBox`. Guide: [Working with components, Selection: ComboBox, RadioButtonGroup, MultiSelectComboBox](https://parttio.github.io/unified-testing-tool/guide/components/#selection-combobox-radiobuttongroup-multiselectcombobox).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear multi-select combo box “…”
  - `deselectItems(String...)`: deselect “…” in multi-select combo box “…”
  - `selectItems(String...)`: select “…” in multi-select combo box “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getItems() → List<String>`, `getLabel() → String`, `getPlaceholder() → String`, `getSelectedItems() → List<String>`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasItems(String...)`, `hasLabel(String)`, `hasNoSelectedItems()`, `hasSelectedItems(String...)`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedNav`

Nav, Flow's `Nav`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click nav “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedNotification`

Notification, Flow's `Notification`. Guide: [Working with components, Dialogs and notifications](https://parttio.github.io/unified-testing-tool/guide/components/#dialogs-and-notifications).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`, `isOpen() → boolean`
- **Checks:** those of every Wrapper, and `isOpen()`

### `UnifiedNumberField`

Number field, Flow's `NumberField`. Guide: [Working with components, Typed values](https://parttio.github.io/unified-testing-tool/guide/components/#typed-values).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear number field “…”
  - `setValue(Double)`: fill number field “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getMax() → Double`, `getMin() → Double`, `getPlaceholder() → String`, `getStep() → Double`, `getValue() → Double`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(double)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedParagraph`

Paragraph, Flow's `Paragraph`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click paragraph “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedPasswordField`

Password field, Flow's `PasswordField`. Guide: [Working with components, Text fields](https://parttio.github.io/unified-testing-tool/guide/components/#text-fields).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear password field “…”
  - `setValue(String)`: fill password field “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(String)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedRadioButtonGroup`

Radio button group, Flow's `RadioButtonGroup`. Guide: [Working with components, Selection: ComboBox, RadioButtonGroup, MultiSelectComboBox](https://parttio.github.io/unified-testing-tool/guide/components/#selection-combobox-radiobuttongroup-multiselectcombobox).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`
- **Actions**, each with the User Step it reports:
  - `selectItem(String)`: choose “…” in radio button group “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getItems() → List<String>`, `getLabel() → String`, `getSelectedItem() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasItems(String...)`, `hasLabel(String)`, `hasNoSelectedItem()`, `hasSelectedItem(String)`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedSection`

Section, Flow's `Section`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click section “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedSideNav`

Side nav, Flow's `SideNav`. Guide: [Working with components, Side navigation](https://parttio.github.io/unified-testing-tool/guide/components/#side-navigation).

- **Filters:** `withLabel(String)`
- **Reads:** `getItems() → List<String>`, `getLabel() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper, and `hasItems(String...)`

### `UnifiedSideNavItem`

Side nav item, Flow's `SideNavItem`. Guide: [Working with components, Side navigation](https://parttio.github.io/unified-testing-tool/guide/components/#side-navigation).

- **Filters:** `withLabel(String)`, `withPath(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click side nav item “…”
  - `collapse()`: collapse side nav item “…”
  - `expand()`: expand side nav item “…”
- **Reads:** `getItems() → List<String>`, `getLabel() → String`, `getPath() → String`, `isCurrent() → boolean`, `isExpanded() → boolean`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper, and `hasItems(String...)`, `hasPath(String)`, `isCollapsed()`, `isCurrent()`, `isExpanded()`, `isNotCurrent()`

### `UnifiedSpan`

Span, Flow's `Span`. Guide: [Working with components, Plain HTML and text](https://parttio.github.io/unified-testing-tool/guide/components/#plain-html-and-text).

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions**, each with the User Step it reports:
  - `click()`: click span “…”
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `findTemplate(Class<C>) → UnifiedTemplate`
- **Checks:** those of every Wrapper

### `UnifiedTabSheet`

Tab sheet, Flow's `TabSheet`. Guide: [Working with components, Tabs and Details](https://parttio.github.io/unified-testing-tool/guide/components/#tabs-and-details).

- **Filters:** `withTab(String)`
- **Actions**, each with the User Step it reports:
  - `selectTab(String)`: select tab “…” in tab sheet “…”
- **Reads:** `getSelectedTab() → String`, `getTabs() → List<String>`
- **Also:** `content() → Scope`
- **Checks:** those of every Wrapper, and `hasSelectedTab(String)`, `hasTabs(String...)`

### `UnifiedTabs`

Tabs, Flow's `Tabs`. Guide: [Working with components, Tabs and Details](https://parttio.github.io/unified-testing-tool/guide/components/#tabs-and-details).

- **Filters:** `withTab(String)`
- **Actions**, each with the User Step it reports:
  - `selectTab(String)`: select tab “…” in tabs “…”
- **Reads:** `getSelectedTab() → String`, `getTabs() → List<String>`
- **Checks:** those of every Wrapper, and `hasSelectedTab(String)`, `hasTabs(String...)`

### `UnifiedTextArea`

Text area, Flow's `TextArea`. Guide: [Working with components, Text fields](https://parttio.github.io/unified-testing-tool/guide/components/#text-fields).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear text area “…”
  - `setValue(String)`: fill text area “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(String)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedTextField`

Text field, Flow's `TextField`. Guide: [Working with components, Text fields](https://parttio.github.io/unified-testing-tool/guide/components/#text-fields).

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions**, each with the User Step it reports:
  - `clear()`: clear text field “…”
  - `setValue(String)`: fill text field “…” with “…”
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(String)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedUpload`

Upload, Flow's `Upload`. Guide: [Working with components, Upload](https://parttio.github.io/unified-testing-tool/guide/components/#upload).

- **Actions**, each with the User Step it reports:
  - `upload(Path)`: upload “file.txt” to upload “…”
  - `upload(String, String, byte[])`: upload “…” to upload “…”
- **Checks:** those of every Wrapper

