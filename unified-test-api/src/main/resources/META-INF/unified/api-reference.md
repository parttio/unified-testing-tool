# Unified API reference

Every Wrapper of the Unified API with its filters, actions, reads and checks. Generated from the code; shipped in the `unified-test-api` jar as `META-INF/unified/api-reference.md`.

- **Find** a component with `page.find(UnifiedX.class)` and filters: `page.find(UnifiedTextField.class).withLabel("Name")`. A Wrapper is lazy: it looks its component up again on every use, so keep it in a variable and reuse it.
- **Actions** wait until the component is visible and enabled, act, then wait for the server (Auto-Wait). Each is a User Step in the reports.
- **Reads** return what is there right now, without waiting. Assert with a check instead when you can.
- **Checks** are `assertThat(wrapper).…` from `UnifiedAssertions`; they retry until the timeout and are User Steps (`see …`).
- A component without a Wrapper: `page.findComponent(FlowClass.class)`, with the filters of every Wrapper.

## The page: `UnifiedPage`

- **Actions:** `navigate(Class<? extends Component>)`, `navigate(Class<? extends Component>, RouteParameters)`, `navigate(Class<C>, T)`, `navigate(String)`, `pressKey(String)`, `reload()`, `screenshot(Path)`, `step(String, Runnable)`, `waitUntil(BooleanSupplier)`
- **Reads:** `hasRealBrowser() → boolean`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`, `openAsUser(String, String...) → UnifiedPage`, `openWindow() → UnifiedPage`, `secret(String) → String`, `tryUnwrap(Class<T>) → Optional<T>`, `unwrap(Class<T>) → T`
- **Checks, `assertThat(UnifiedPage)`:** `hasNoErrors()`, `hasPath(String)`, `hasQueryParameter(String, String)`, `isAt(Class<? extends Component>)`

## Every Wrapper

- **Filters:** `all()`, `visible()`, `where(Predicate<UnifiedElement>)`, `withAccessibleName(String)`, `withAccessibleNameContaining(String)`, `withAttribute(String, String)`, `withClassName(String)`, `withClassNames(String...)`, `withId(String)`, `withProperty(String, Object)`, `withTestId(String)`, `withTheme(String)`
- **Methods:** `element() → UnifiedElement`, `isEnabled() → boolean`, `isVisible() → boolean`, `tryUnwrap(Class<T>) → Optional<T>`, `unwrap(Class<T>) → T`, `withTimeout(Duration) → UnifiedWrapper`
- **Checks, `assertThat(UnifiedWrapper)`:** `doesNotExist()`, `exists()`, `hasCount(int)`, `hasCssValue(String, String)`, `hasProperty(String, Object)`, `hasText(String)`, `hasTextContaining(String)`, `isDisabled()`, `isEnabled()`, `isHidden()`, `isVisible()`

## Wrappers

### `UnifiedArticle`

Article, Flow's `Article`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedAside`

Aside, Flow's `Aside`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedBigDecimalField`

Decimal field, Flow's `BigDecimalField`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `setValue(BigDecimal)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → BigDecimal`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(BigDecimal)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedButton`

Button, Flow's `Button`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Actions:** `click()`
- **Reads:** `getText() → String`
- **Checks:** those of every Wrapper

### `UnifiedCheckbox`

Checkbox, Flow's `Checkbox`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`
- **Actions:** `check()`, `setChecked(boolean)`, `uncheck()`
- **Reads:** `getHelperText() → String`, `getLabel() → String`, `isChecked() → boolean`, `isIndeterminate() → boolean`
- **Checks:** those of every Wrapper, and `hasLabel(String)`, `isChecked()`, `isIndeterminate()`, `isNotChecked()`

### `UnifiedComboBox`

Combo box, Flow's `ComboBox`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `selectItem(String)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getItems() → List<String>`, `getLabel() → String`, `getPlaceholder() → String`, `getSelectedItem() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasItems(String...)`, `hasLabel(String)`, `hasNoSelectedItem()`, `hasSelectedItem(String)`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedContainer`

Container, Flow's `Component`.

- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedDatePicker`

Date picker, Flow's `DatePicker`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `setValue(LocalDate)`, `typeText(String)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → LocalDate`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(LocalDate)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedDateTimePicker`

Date-time picker, Flow's `DateTimePicker`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`
- **Actions:** `clear()`, `setValue(LocalDateTime)`, `typeText(String, String)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getValue() → LocalDateTime`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(LocalDateTime)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedDetails`

Details, Flow's `Details`.

- **Filters:** `withSummaryText(String)`
- **Actions:** `close()`, `open()`, `toggle()`
- **Reads:** `getSummaryText() → String`, `isOpen() → boolean`
- **Also:** `content() → Scope`, `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper, and `hasSummaryText(String)`, `isClosed()`, `isOpen()`

### `UnifiedDialog`

Dialog, Flow's `Dialog`.

- **Filters:** `withHeaderTitle(String)`
- **Actions:** `close()`
- **Reads:** `getHeaderTitle() → String`, `isOpen() → boolean`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper, and `hasHeaderTitle(String)`, `isClosed()`, `isOpen()`

### `UnifiedDiv`

Div, Flow's `Div`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedFooter`

Footer, Flow's `Footer`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedGrid`

Grid, Flow's `Grid`.

- **Actions:** `deselectAll()`, `selectAll()`
- **Reads:** `getColumnHeaders() → List<String>`, `getRowCount() → int`
- **Also:** `column(String) → UnifiedGridColumn`, `column(int) → UnifiedGridColumn`, `columnWithId(String) → UnifiedGridColumn`, `row() → UnifiedGridRow`, `row(int) → UnifiedGridRow`
- **Checks:** those of every Wrapper, and `hasRowCount(int)`, `isEmpty()`

#### `UnifiedGrid.UnifiedGridColumn`

- **Actions:** `sort(SortDirection)`
- **Reads:** `getCellTexts() → List<String>`, `getIndex() → int`, `getSortDirection() → SortDirection`
- **Also:** `grid() → UnifiedGrid`, `header() → Scope`
- **Checks, `assertThat(UnifiedGridColumn)`:** `hasCellTexts(String...)`, `isSortedAscending()`, `isSortedDescending()`

#### `UnifiedGrid.UnifiedGridRow`

- **Filters:** `selected()`, `withCellText(String)`, `withCellText(String, String)`, `withCellTextContaining(String, String)`
- **Actions:** `click()`, `deselect()`, `doubleClick()`, `select()`, `toggleDetails()`
- **Reads:** `getCellText(String) → String`, `getCellText(int) → String`, `getIndex() → int`, `isDetailsOpen() → boolean`, `isSelected() → boolean`
- **Also:** `all() → List<UnifiedGridRow>`, `cell(String) → Scope`, `details() → Scope`, `grid() → UnifiedGrid`
- **Checks, `assertThat(UnifiedGridRow)`:** `doesNotExist()`, `exists()`, `hasCellText(String, String)`, `hasCount(int)`, `hasDetailsClosed()`, `hasDetailsOpen()`, `isNotSelected()`, `isSelected()`

### `UnifiedHeader`

Header, Flow's `Header`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedIntegerField`

Integer field, Flow's `IntegerField`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `setValue(Integer)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → Integer`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(int)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedMain`

Main, Flow's `Main`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedMenuBar`

Menu bar, Flow's `MenuBar`.

- **Filters:** `withItem(String)`
- **Actions:** `clickItem(String, String...)`
- **Reads:** `getItems() → List<String>`, `getItems(String, String...) → List<String>`, `isItemEnabled(String, String...) → boolean`
- **Checks:** those of every Wrapper

### `UnifiedMessageInput`

Message input, Flow's `MessageInput`.

- **Actions:** `send(String)`
- **Checks:** those of every Wrapper

### `UnifiedMessageList`

Message list, Flow's `MessageList`.

- **Reads:** `getMessageCount() → int`, `getMessageTexts() → List<String>`, `getMessages() → List<Message>`
- **Checks:** those of every Wrapper, and `hasMessageContaining(String)`, `hasMessageCount(int)`, `hasMessageTexts(String...)`, `isEmpty()`

### `UnifiedMultiSelectComboBox`

Multi-select combo box, Flow's `MultiSelectComboBox`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `deselectItems(String...)`, `selectItems(String...)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getItems() → List<String>`, `getLabel() → String`, `getPlaceholder() → String`, `getSelectedItems() → List<String>`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasItems(String...)`, `hasLabel(String)`, `hasNoSelectedItems()`, `hasSelectedItems(String...)`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedNav`

Nav, Flow's `Nav`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedNotification`

Notification, Flow's `Notification`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`, `isOpen() → boolean`
- **Checks:** those of every Wrapper, and `isOpen()`

### `UnifiedNumberField`

Number field, Flow's `NumberField`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `setValue(Double)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getMax() → Double`, `getMin() → Double`, `getPlaceholder() → String`, `getStep() → Double`, `getValue() → Double`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(double)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedParagraph`

Paragraph, Flow's `Paragraph`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedRadioButtonGroup`

Radio button group, Flow's `RadioButtonGroup`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`
- **Actions:** `selectItem(String)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getItems() → List<String>`, `getLabel() → String`, `getSelectedItem() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasItems(String...)`, `hasLabel(String)`, `hasNoSelectedItem()`, `hasSelectedItem(String)`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedSection`

Section, Flow's `Section`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedSpan`

Span, Flow's `Span`.

- **Filters:** `withText(String)`, `withTextContaining(String)`
- **Reads:** `getText() → String`
- **Also:** `describe() → String`, `find(Class<W>) → W`, `findComponent(Class<C>) → UnifiedGenericComponent`
- **Checks:** those of every Wrapper

### `UnifiedTabSheet`

Tab sheet, Flow's `TabSheet`.

- **Filters:** `withTab(String)`
- **Actions:** `selectTab(String)`
- **Reads:** `getSelectedTab() → String`, `getTabs() → List<String>`
- **Also:** `content() → Scope`
- **Checks:** those of every Wrapper, and `hasSelectedTab(String)`, `hasTabs(String...)`

### `UnifiedTabs`

Tabs, Flow's `Tabs`.

- **Filters:** `withTab(String)`
- **Actions:** `selectTab(String)`
- **Reads:** `getSelectedTab() → String`, `getTabs() → List<String>`
- **Checks:** those of every Wrapper, and `hasSelectedTab(String)`, `hasTabs(String...)`

### `UnifiedTextArea`

Text area, Flow's `TextArea`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `setValue(String)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(String)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedTextField`

Text field, Flow's `TextField`.

- **Filters:** `withLabel(String)`, `withLabelContaining(String)`, `withPlaceholder(String)`
- **Actions:** `clear()`, `setValue(String)`
- **Reads:** `getErrorMessage() → String`, `getHelperText() → String`, `getLabel() → String`, `getPlaceholder() → String`, `getValue() → String`, `isInvalid() → boolean`, `isRequired() → boolean`
- **Checks:** those of every Wrapper, and `hasErrorMessage(String)`, `hasLabel(String)`, `hasValue(String)`, `isEmpty()`, `isInvalid()`, `isRequired()`, `isValid()`

### `UnifiedUpload`

Upload, Flow's `Upload`.

- **Actions:** `upload(Path)`, `upload(String, String, byte[])`
- **Checks:** those of every Wrapper

