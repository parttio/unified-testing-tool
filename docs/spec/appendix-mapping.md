# Appendix A. Mapping to the Testing Tools

How each Unified API call maps onto the three Testing Tools: Browserless Test 25.3, Dramafinder 2.0.0, TestBench 25.3. "Adapter" marks behaviour the adapter adds because the tool lacks it ([appendix-gaps.md](appendix-gaps.md)). Side-by-side test: [COMPARISON.md](https://github.com/parttio/unified-testing-tool/blob/prototype/component-wrapper/prototypes/component-wrapper/COMPARISON.md).

## Setup and navigation

| Unified API | Browserless | Dramafinder (Playwright) | TestBench (Selenium) |
|---|---|---|---|
| `@UnifiedUITest` | `BrowserlessExtension` | adapter owns Playwright + browser | `@BrowserTest` setup, adapter-owned driver |
| `@UnifiedSpringBootTest` | `SpringBrowserlessApplicationContext.create(springCtx, …)` | `SpringPlaywrightIT` base URL from the port | `@SpringBootTest(RANDOM_PORT)` + port |
| `page.navigate(View.class)` | `navigate(View.class)` | `RouteNavigation` / `navigate(View.class)` | `getDriver().get(baseUrl + path)` |
| `page.reload()` | page reload simulation | `page.reload()` | `navigate().refresh()` |
| `assertThat(page).isAt(View.class)` | `getCurrentView()` | `assertAt(View.class)` | URL vs resolved path (adapter) |
| `assertThat(page).hasTitle(s)` | `UI.getInternals().getTitle()` (adapter) | `page.title()` | `getDriver().getTitle()` |

## Locating

| Unified API | Browserless | Dramafinder | TestBench |
|---|---|---|---|
| `find(UnifiedTextField.class)` | `find(TextField.class)` / `findTextField()` | `find(page, TextFieldElement.class)` | `$(TextFieldElement.class)` |
| `.withLabel("Name")` | `withLabel` | `withLabel` (exact) | `withLabel` |
| `.withAccessibleName("x")` | `withLabel` / `withAriaLabel` combined (adapter) | `withAccessibleName` | label or `aria-label` (adapter) |
| `.withText("Save")` | `withText` | `withText` | `withText` |
| `.withTestId("x")` | `withTestId` | `withTestId` | `withTestId` (25.4+) or `data-testid` attribute |
| each use of the Wrapper, `assertThat(w).exists()` / `doesNotExist()` / `hasCount(n)`, `.all()` | every match in the component tree, then the core counts (adapter) | every match of a locator, filters checked in the page in one call, then the core counts (adapter) | every match of an element query in one script call, then the core counts (adapter) |
| `dialog.find(…)` | `inside(dialog)` | `dialog.find(…)` | `dialog.$(…)` |
| `findComponent(Rating.class)` | `find(Rating.class)` | locator on `@Tag` | `$("tag")` |

## Actions, waiting, assertions

| Unified API | Browserless | Dramafinder | TestBench |
|---|---|---|---|
| `field.setValue("x")` | tester `setValue` (as user) | `setValue` (fills, change, idle) | `setValue` |
| `button.click()` | tester `click` | `click()` (waits for the round-trip) | `click()` |
| Auto-Wait before | synchronous | Playwright actionability | element-state wait (adapter) |
| Auto-Wait after | synchronous | every action waits for the round-trip | `waitForVaadin` |
| `assertThat(field).hasValue("x")` | retry with `roundTrip()` + signals (adapter) | `assertValue` | retry loop (adapter) |
| `page.waitUntil(…)` | poll + flush (adapter) | poll | `waitUntil` |

## Grid

| Unified API | Browserless | Dramafinder | TestBench |
|---|---|---|---|
| `row().withCellText(col, text)` | scan `getCellText(row, col)` | `findRowIndexesWithColumnText` | scan `getCell(row, col).getText()` |
| `row().withCellText(text)`, any column | the same, over every column | the same, over every column | `getCell(text)` (first match) |
| `row.getCellText(col)` | `getCellText(row, col)` | `getCell(..).getText()` | `getCell(row, col).getText()` |
| `row.getCellText(index)` | `getCellText(row, index)` | `getCell(..).getText()` | `getCell(row, index).getText()` |
| `row.cell(col).find(..)` | `getCellComponent(row, col)` | cell content locator | cell element `$(..)` |
| `row.select()` | `select(row)` | row checkbox / click | `select(row)` |
| `column.sort(dir)` | `sortByColumn(col, dir)` | header sorter click | header sorter click |
| row access, lazy data | in memory | `waitForGridToStopLoading` | `scrollToRow` |
| `getRowCount()` | `size()` | `getTotalRowCount()` | `getRowCount()` |

## Dramafinder calls the Migration Recipe converts

What `MigrateDramafinderToUnified` writes for Dramafinder code with no row above ([ADR 0006](../adr/0006-dramafinder-migration-recipes.md)).

| Dramafinder | Unified API |
|---|---|
| `page.navigate(getUrl() + "/path?q=" + q)` | `page.navigate(View.class, QueryParameters.of("q", String.valueOf(q)))` when exactly one `@Route("path")` is among the migrated sources, else `page.navigate("path?q=" + q)` |
| `XElement.getByLabel(page, s)` / `getByText` / `getById` / `getByTestId` / `…Containing` | `page.find(UnifiedX.class).withLabel(s)` / `withText` / `withId` / `withTestId` / `…Containing` |
| `ButtonElement.getByLabel(page, s)` | `.withAccessibleName(s)` |
| `DialogElement.getByHeaderText(page, s)` | `.withHeaderTitle(s)` |
| `NotificationElement.getByText(page, s)` (substring, first match) | `.withTextContaining(s)` + Migration Marker |
| query `first()` / `get(i)` | the plain lookup + `was first()` Migration Marker / `all().get(i)` |
| `page.locator("#x")` / `locator("[data-testid=x]")` / `getByTestId("x")` | `page.find(W.class).withId("x")` / `withTestId("x")`, `W` the Wrapper of the component the sources give that id, else `UnifiedContainer` + Migration Marker; `fill(s)` / `inputValue()` are `setValue(s)` / `getValue()` |
| on such a raw locator, `assertThat(l).containsText(s)` / `hasText(s)` / `hasAttribute("disabled", "")` / `not().hasAttribute("disabled", ..)` or `not().hasAttribute("aria-disabled", "true")` / `isAttached()` *(unreleased)* | `assertThat(w).hasTextContaining(s)` / `hasText(s)` / `isDisabled()` / `isEnabled()` / `exists()` |
| on such a raw locator, `assertThat(l).hasClass("c")` or `hasClass(Pattern.compile(".*c.*"))` / `not().isVisible()` *(unreleased)* | `assertThat(w.withClassName("c")).exists()` / `assertThat(w.visible()).doesNotExist()` |
| `assertVisible` / `assertHidden` / `assertEnabled` / `assertDisabled` | `assertThat(w).isVisible()` / `isHidden()` / `isEnabled()` / `isDisabled()` |
| `assertValue` / `assertLabel` / `assertValid` / `assertInvalid` / `assertErrorMessage` | `hasValue` / `hasLabel` / `isValid` / `isInvalid` / `hasErrorMessage` |
| `assertChecked` / `assertNotChecked` / `assertIndeterminate` | `isChecked` / `isNotChecked` / `isIndeterminate` |
| dialog `assertOpen` / `assertClosed` / `assertHeaderText`, `getHeaderText()`, `closeWithEscape()` | `isOpen` / `isClosed` / `hasHeaderTitle`, `getHeaderTitle()`, `close()` |
| confirm dialog *(unreleased)*: as a dialog, and `confirm()` / `reject()` / `cancel()`, `getMessage()`, `assertMessage(s)` | `UnifiedConfirmDialog`: `confirm()` / `reject()` / `cancel()`, `getText()`, `hasText(s)` |
| login form or overlay *(unreleased)*: `login(u, p)`, `isErrorVisible()`, `assertErrorVisible` / `assertNoError`, overlay `getByTitle(page, s)` / `assertHeaderTitle` | `UnifiedLogin`: `login(u, p)`, `isError()`, `hasError` / `hasNoError`, `withTitle(s)` / `hasTitle` |
| notification `assertOpen` / `assertContent` | `isOpen` / `hasTextContaining` |
| `AutoWait.waitForVaadinIdle(page)`, `page.waitForLoadState()` | dropped (Auto-Wait) |
| `GridElement.get(page)` | `page.find(UnifiedGrid.class)` (no marker: a second grid fails loudly) |
| `grid.findRow(i).orElseThrow()` | `grid.row(i)` |
| `grid.findRowIndexesWithColumnText(col, s).get(0)` | `grid.row().withCellText(s).getIndex()` + Migration Marker (any column matches) |
| `row.getCell(col).getText()` / `row.getCell("Header").assertText(s)` | `row.getCellText(col)` / `assertThat(row).hasCellText("Header", s)` |
| `row.getCell(col).click()` | `row.click()` + Migration Marker (the row, not that cell) |
| `getTotalRowCount()`, `assertRowCount(n)`, `row.assertSelected()` | `getRowCount()`, `assertThat(grid).hasRowCount(n)`, `assertThat(row).isSelected()` |
| `waitForGridToStopLoading()`, `scrollToRow(i)` | dropped (Auto-Wait) |
| `MessageInputElement.get(page)`, `MessageListElement.get(page)` | `page.find(UnifiedMessageInput.class)`, `page.find(UnifiedMessageList.class)` (no marker: a second one fails loudly) |
| `MessageInputElement.get(l)` / `MessageListElement.get(l)`, `l` an id or test id locator on the page *(unreleased)* | `page.find(UnifiedContainer.class).withId(..)` / `withTestId(..)` + `.find(UnifiedMessageInput.class)` / `.find(UnifiedMessageList.class)` |
| `new MessageInputElement(l)` / `new MessageListElement(l)`, `l` an id or test id locator on the page *(unreleased)* | `page.find(UnifiedMessageInput.class)` / `page.find(UnifiedMessageList.class)` + `.withId(..)` / `.withTestId(..)` |
| message input `typeAndSubmit(s)` | `send(s)` |
| message input `focus()` *(unreleased)* | `element().focus()` |
| message list `assertMessageCount(n)` / `assertEmpty()` | `assertThat(list).hasMessageCount(n)` / `isEmpty()` |
| `assertThat(list.getMessage(i)).containsText(s)` / `hasText(s)` *(unreleased)* | `assertThat(list.message(i)).hasTextContaining(s)` / `hasText(s)` |

## Escape Hatch

| `unwrap(…)` | Browserless | Dramafinder | TestBench |
|---|---|---|---|
| wrapper | `TextFieldTester`, `Component` | `TextFieldElement`, `Locator` | `TextFieldElement`, `WebElement` |
| page | `BrowserlessUIContext` | `Page` | `WebDriver` |
