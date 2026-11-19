package com.vaadin.testing.unified.selenium;

import com.vaadin.flow.component.grid.testbench.GridColumnElement;
import com.vaadin.flow.component.grid.testbench.GridElement;
import com.vaadin.flow.component.grid.testbench.GridTHTDElement;
import com.vaadin.flow.component.grid.testbench.GridTRElement;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testbench.TestBenchElement;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.spi.GridAccess;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Grid access on Selenium, through TestBench's GridElement: its columns, rows, cells, header cells and
 * selection.
 * <p>
 * Columns are the visible ones in display order, <b>without</b> the multi-select checkbox column, as in
 * Flow's {@code Grid.getColumns()} (TestBench counts it: #158).
 * <p>
 * The scripts left here have no TestBench API yet: paging through a column (#155, #159). A cell's content
 * element is read through {@code GridTHTDElement.getContext()} (#156). Sorting and row details have no
 * TestBench API (#21).
 */
final class SeleniumGridAccess implements GridAccess {

    private static final String SELECTION_COLUMN = "vaadin-grid-flow-selection-column";

    private final SeleniumSession session;
    private final WebElement grid;

    SeleniumGridAccess(SeleniumSession session, WebElement grid) {
        this.session = session;
        this.grid = grid;
    }

    private GridElement testBench() {
        return session.testBench(grid).wrap(GridElement.class);
    }

    // ---------------------------------------------------------------- columns

    /** The visible columns in display order, without the selection column (#158). */
    private List<GridColumnElement> dataColumns() {
        return testBench().getVisibleColumns().stream()
                .filter(c -> !SELECTION_COLUMN.equals(c.getTagName()))
                .toList();
    }

    private GridColumnElement column(int column) {
        List<GridColumnElement> data = dataColumns();
        if (column < 0 || column >= data.size()) {
            throw new IllegalArgumentException("No visible column at index " + column);
        }
        return data.get(column);
    }

    @Override
    public int rowCount() {
        return testBench().getRowCount();
    }

    /** The first non-empty header text of each column: its own header cell, then its groups'. */
    @Override
    public List<String> columnHeaders() {
        return dataColumns().stream()
                .map(c -> headerCells(c).stream()
                        .map(cell -> BrowserQueries.normalize(cell.getText()))
                        .filter(text -> !text.isEmpty())
                        .findFirst().orElse(""))
                .toList();
    }

    @Override
    public int columnIndexOfId(String id) {
        List<GridColumnElement> data = dataColumns();
        for (int i = 0; i < data.size(); i++) {
            if (id.equals(data.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    /** The header cells of a column: its own, then those of the column groups around it (header rows). */
    private static List<GridTHTDElement> headerCells(GridColumnElement column) {
        List<GridTHTDElement> cells = new ArrayList<>();
        for (TestBenchElement c = column; c != null && !"vaadin-grid".equals(c.getTagName()); c = c.getParent()) {
            GridTHTDElement cell = headerCell(c.wrap(GridColumnElement.class));
            if (cell != null) {
                cells.add(cell);
            }
        }
        return cells;
    }

    /** The column's header cell, or null if the grid has no header row for it. */
    private static GridTHTDElement headerCell(GridColumnElement column) {
        try {
            return column.getHeaderCell();
        } catch (NullPointerException e) { // getHeaderCell() wraps a missing _headerCell
            return null;
        }
    }

    /** The {@code vaadin-grid-cell-content} a cell shows (#156), or null if it has none. */
    private static WebElement content(GridTHTDElement cell) {
        return cell.getContext() instanceof WebElement content ? content : null;
    }

    // ---------------------------------------------------------------- rows and cells

    /** The row, scrolled into view and rendered with its data. */
    private GridTRElement row(int row) {
        if (row < 0 || row >= rowCount()) {
            throw new IllegalStateException("No row " + row);
        }
        long deadline = System.nanoTime() + session.timeout().toNanos();
        while (true) {
            GridTRElement tr = testBench().getRow(row, true);
            if (tr != null && !tr.hasAttribute("loading")) {
                return tr;
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Row " + row + " was not rendered");
            }
            session.waitForVaadin();
        }
    }

    private GridTHTDElement cellElement(int row, int column) {
        GridTHTDElement cell = row(row).getCell(column(column));
        if (cell == null) {
            throw new IllegalStateException("No cell at row " + row + ", column " + column);
        }
        return cell;
    }

    private WebElement cellContent(int row, int column) {
        WebElement content = content(cellElement(row, column));
        if (content == null) {
            throw new IllegalStateException("No cell content at row " + row + ", column " + column);
        }
        return content;
    }

    @Override
    public String cellText(int row, int column) {
        return BrowserQueries.normalize(cellElement(row, column).getText());
    }

    /**
     * Reads a whole column a page at a time (#155, #159): in the browser, in one asynchronous script that scrolls,
     * waits for each page and reads it (#404); if that doesn't finish, from here, a few commands per page.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> columnTexts(int column) {
        GridColumnElement columnElement = column(column);
        int count = rowCount();
        try {
            Object read = session.jsAsync(COLUMN_IN_PAGE, grid, columnElement, count, PAGE_WAIT_MS);
            if (read instanceof List<?> texts && texts.size() == count && !texts.contains(null)) {
                return texts.stream().map(t -> BrowserQueries.normalize(String.valueOf(t))).toList();
            }
        } catch (org.openqa.selenium.ScriptTimeoutException | org.openqa.selenium.JavascriptException e) {
            // the page by page reading below
        }
        return columnTextsPageByPage(columnElement, count);
    }

    /** How long {@link #COLUMN_IN_PAGE} waits for one page to load, in ms, before giving up. */
    private static final int PAGE_WAIT_MS = 5000;

    /**
     * Scrolls the grid a page at a time from the first row not read yet, waits until that row is rendered and
     * Flow is idle, reads the column's rendered cells as {@link #RENDERED_CELLS} does: every row's text, or null
     * when a page didn't load in time.
     */
    private static final String COLUMN_IN_PAGE = """
            const g = arguments[0], column = arguments[1], count = arguments[2], pageMs = arguments[3];
            const done = arguments[arguments.length - 1];
            const tick = (ms) => new Promise(r => setTimeout(r, ms));
            const busy = () => {
              const flow = window.Vaadin && window.Vaadin.Flow;
              return !!(flow && flow.clients && Object.values(flow.clients).some(c => c.isActive()));
            };
            (async () => {
              const texts = new Array(count).fill(null);
              const read = () => {
                for (const tr of g._getRenderedRows()) {
                  const i = tr.index;
                  if (tr.hidden || tr.hasAttribute('loading') || i < 0 || i >= count || texts[i] !== null) continue;
                  const cell = [...tr.children].find(td => td._column === column);
                  const slot = cell && cell.firstElementChild;
                  const nodes = slot && slot.assignedNodes ? slot.assignedNodes() : null;
                  if (nodes) texts[i] = nodes.map(n => n.textContent).join('');
                }
              };
              let next = 0;
              while (next < count) {
                g._scrollToFlatIndex(next);
                const start = Date.now();
                for (;;) {
                  await tick(10);
                  read();
                  if (texts[next] !== null && !busy()) break;
                  if (Date.now() - start > pageMs) return null;
                }
                while (next < count && texts[next] !== null) next++;
              }
              return texts;
            })().then(done, () => done(null));""";

    @SuppressWarnings("unchecked")
    private List<String> columnTextsPageByPage(GridColumnElement columnElement, int count) {
        int column = dataColumns().indexOf(columnElement);
        String[] texts = new String[count];
        int next = 0;
        while (next < count) {
            session.js("arguments[0]._scrollToFlatIndex(arguments[1]);", grid, next); // #155
            session.waitForVaadin();
            List<List<Object>> rendered = (List<List<Object>>) session.js(RENDERED_CELLS, grid, columnElement);
            for (List<Object> pair : rendered) {
                int index = ((Number) pair.get(0)).intValue();
                if (index >= 0 && index < count && texts[index] == null) {
                    texts[index] = BrowserQueries.normalize(String.valueOf(pair.get(1)));
                }
            }
            if (texts[next] == null) {
                texts[next] = cellText(next, column); // not rendered after the jump: read it the slow way
            }
            while (next < count && texts[next] != null) {
                next++;
            }
        }
        return List.of(texts);
    }

    /** [flat row index, cell text] of every rendered, loaded body row, for one column (#159). */
    private static final String RENDERED_CELLS = """
            const g = arguments[0], column = arguments[1];
            return [...g._getRenderedRows()]
              .filter(tr => !tr.hidden && !tr.hasAttribute('loading'))
              .map(tr => {
                const cell = [...tr.children].find(td => td._column === column);
                const slot = cell && cell.firstElementChild;
                const nodes = slot && slot.assignedNodes ? slot.assignedNodes() : null;
                return [tr.index, nodes ? nodes.map(n => n.textContent).join('') : null];
              })
              .filter(pair => pair[1] !== null);""";

    // ---------------------------------------------------------------- selection, clicks

    @Override
    public void select(int row) {
        row(row);
        testBench().select(row);
        session.waitForVaadin();
        if (!isSelected(row)) {
            throw new IllegalStateException("Row " + row + " could not be selected");
        }
    }

    @Override
    public void deselect(int row) {
        row(row);
        testBench().deselect(row);
        session.waitForVaadin();
    }

    @Override
    public boolean isSelected(int row) {
        return row(row).isSelected();
    }

    @Override
    public void selectAll() {
        if (!selectAllChecked()) {
            testBench().clickSelectAll();
            session.waitForVaadin();
        }
    }

    @Override
    public void deselectAll() {
        if (selectAllChecked()) {
            testBench().clickSelectAll();
            session.waitForVaadin();
        }
    }

    /** The selection column's {@code selectAll}: GridElement can't read it (#157). */
    private boolean selectAllChecked() {
        return testBench().getVisibleColumns().stream()
                .filter(c -> SELECTION_COLUMN.equals(c.getTagName()))
                .findFirst()
                .map(c -> Boolean.TRUE.equals(c.getPropertyBoolean("selectAll")))
                .orElse(false);
    }

    @Override
    public void clickRow(int row) {
        element(cellContent(row, 0)).click();
    }

    @Override
    public void doubleClickRow(int row) {
        element(cellContent(row, 0)).doubleClick();
    }

    // ---------------------------------------------------------------- sorting

    /** The column's sorter, in its own header cell or a group's (#21). */
    private WebElement sorter(int column) {
        for (WebElement content : headerContents(column)) {
            List<WebElement> sorters = content.findElements(By.tagName("vaadin-grid-sorter"));
            if (!sorters.isEmpty()) {
                return sorters.get(0);
            }
        }
        throw new IllegalStateException("Column " + column + " is not sortable");
    }

    @Override
    public SortDirection sortDirection(int column) {
        String direction = session.testBench(sorter(column)).getPropertyString("direction");
        return direction == null || direction.isEmpty() ? null
                : "asc".equals(direction) ? SortDirection.ASCENDING : SortDirection.DESCENDING;
    }

    /** Clicks the sorter (none → ascending → descending) until it matches. */
    @Override
    public void sort(int column, SortDirection direction) {
        for (int clicks = 0; clicks < 3 && sortDirection(column) != direction; clicks++) {
            element(sorter(column)).click();
        }
        if (sortDirection(column) != direction) {
            throw new IllegalStateException("Column " + column + " could not be sorted " + direction);
        }
    }

    // ---------------------------------------------------------------- details

    @Override
    public boolean isDetailsOpen(int row) {
        return row(row).hasAttribute("details-opened");
    }

    /** What a user does: click the row (details open on click, Flow's default). */
    @Override
    public void toggleDetails(int row) {
        clickRow(row);
    }

    // ---------------------------------------------------------------- parts

    @Override
    public UnifiedElement cell(int row, int column) {
        return element(cellContent(row, column));
    }

    /** The contents of the column's header cells: its own, then its groups'. */
    private List<WebElement> headerContents(int column) {
        List<WebElement> contents = new ArrayList<>();
        for (GridTHTDElement cell : headerCells(column(column))) {
            WebElement content = content(cell);
            if (content != null) {
                contents.add(content);
            }
        }
        return contents;
    }

    @Override
    public UnifiedElement header(int column) {
        List<WebElement> contents = headerContents(column);
        if (contents.isEmpty()) {
            throw new IllegalStateException("No header cell for column " + column);
        }
        return new SeleniumElement(session, contents.get(0), SeleniumComponents.DEFAULT, contents);
    }

    /** The details cell of an open row; GridTRElement.getDetails() matches a part order that may change (#21). */
    @Override
    public UnifiedElement details(int row) {
        List<WebElement> cells = row(row).findElements(By.cssSelector("td[part~='details-cell']"));
        WebElement content = cells.isEmpty() ? null
                : content(session.testBench(cells.get(0)).wrap(GridTHTDElement.class));
        if (content == null) {
            throw new IllegalStateException("Row " + row + " has no details open");
        }
        return element(content);
    }

    private SeleniumElement element(WebElement part) {
        return new SeleniumElement(session, part, SeleniumComponents.DEFAULT);
    }
}
