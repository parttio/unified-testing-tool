package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.Locator;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.spi.GridAccess;
import java.util.List;
import java.util.stream.Collectors;
import org.vaadin.addons.dramafinder.AutoWait;
import org.vaadin.addons.dramafinder.element.GridElement;

/**
 * Grid access on Playwright through Dramafinder's GridElement, which scrolls rows into view and waits for
 * lazily loaded data.
 * <p>
 * Columns are the visible ones in display order, <b>without</b> the multi-select checkbox column: Flow's
 * {@code Grid.getColumns()} (and Browserless) don't count it, so Unified column indexes are mapped to
 * browser column indexes.
 */
final class PlaywrightGridAccess implements GridAccess {

    private final PlaywrightSession session;
    private final Locator locator;
    private final GridElement grid;

    PlaywrightGridAccess(PlaywrightSession session, Locator locator) {
        this.session = session;
        this.locator = locator;
        this.grid = new GridElement(locator);
    }

    // ---------------------------------------------------------------- columns

    /** Browser column index of each Unified column: visible, display order, selection column skipped. */
    private List<Integer> domColumns() {
        return grid.getColumns().stream().filter(c -> !c.selectionColumn()).map(GridElement.ColumnInfo::index)
                .collect(Collectors.toList());
    }

    private int dom(int column) {
        List<Integer> columns = domColumns();
        if (column < 0 || column >= columns.size()) {
            throw new IllegalArgumentException("No visible column at index " + column);
        }
        return columns.get(column);
    }

    @Override
    public int rowCount() {
        grid.waitForGridToStopLoading();
        return grid.getTotalRowCount();
    }

    /**
     * Each column's caption, read from whichever header row holds it (a header group may sit above, #338):
     * Dramafinder's {@code getColumnCaptions()}, which scrolls an unrendered header cell into view.
     */
    @Override
    public List<String> columnHeaders() {
        List<String> all = grid.getColumnCaptions();
        return domColumns().stream().map(i -> all.get(i).strip()).toList();
    }

    @Override
    public int columnIndexOfId(String id) {
        java.util.OptionalInt domIndex = grid.findColumnIndexById(id);
        return domIndex.isPresent() ? domColumns().indexOf(domIndex.getAsInt()) : -1;
    }

    @Override
    public String cellText(int row, int column) {
        return cellOf(row, column).getText().strip();
    }

    private GridElement.CellElement cellOf(int row, int column) {
        return grid.findCell(row, dom(column))
                .orElseThrow(() -> new IllegalStateException("No cell at row " + row + ", column " + column));
    }

    private GridElement.RowElement rowOf(int row) {
        return grid.findRow(row).orElseThrow(() -> new IllegalStateException("No row " + row));
    }

    /**
     * Reads a whole column a page at a time (#446): in the browser, in one script that scrolls, asks the server for
     * the page at once and reads it, as Selenium does (#404); if a page doesn't load in time, through Dramafinder,
     * which waits with Playwright's timeout. The whole column, as the exactly-one rule needs to see a second match.
     */
    @Override
    public List<String> columnTexts(int column) {
        Object read = locator.evaluateAll(COLUMN_IN_PAGE, List.of(column, PAGE_WAIT_MS));
        if (read instanceof List<?> texts && !texts.contains(null)) {
            return texts.stream().map(t -> ((String) t).strip()).toList();
        }
        return grid.getColumnTexts(dom(column)).stream().map(t -> t == null ? null : t.strip()).toList();
    }

    /** How long {@link #COLUMN_IN_PAGE} waits for one page to load, in ms, before giving up. */
    private static final int PAGE_WAIT_MS = 5000;

    /**
     * For the first grid: scrolls it a page at a time from the first row not read yet, polls every 5 ms until that
     * row is rendered with its data and Flow is idle, and reads the column's rendered cells as Dramafinder's
     * {@code CellElement.getText()} does (the cell content's {@code innerText}). Unified column {@code column} is
     * the visible columns in display order without the selection column. Every row's text, or null when the grid
     * or the column is missing or a page didn't load in time.
     * <p>
     * When a jump lands on rows that aren't loaded, it calls Flow's {@code $connector.fetchCurrentRange()} itself:
     * the connector waits 150 ms before it asks (its {@code requestDebouncerDelay}, so a user's fast scrolling sends
     * one request), which was most of the time of each page. The connector skips a request for a range already
     * asked, so its own delayed call adds none. A connector without that method waits as before.
     */
    private static final String COLUMN_IN_PAGE = """
            async (gs, [column, pageMs]) => {
              const g = gs[0];
              if (!g || !g.isConnected || !g._getRenderedRows) return null;
              const col = g._getColumns().filter(c => !c.hidden && !c.localName.endsWith('selection-column'))
                  .sort((a, b) => a._order - b._order)[column];
              if (!col) return null;
              const count = g._flatSize;
              const tick = ms => new Promise(r => setTimeout(r, ms));
              const busy = () => {
                const flow = window.Vaadin && window.Vaadin.Flow;
                return !!(flow && flow.clients && Object.values(flow.clients).some(c => c.isActive()));
              };
              const texts = new Array(count).fill(null);
              const read = () => {
                for (const tr of g._getRenderedRows()) {
                  const i = tr.index;
                  if (tr.hidden || tr.hasAttribute('loading') || !(i >= 0 && i < count) || texts[i] !== null) continue;
                  const cell = [...tr.children].find(td => td._column === col);
                  const slot = cell && cell.querySelector('slot');
                  const content = slot && slot.assignedElements()[0];
                  if (content) texts[i] = content.innerText;
                }
              };
              let next = 0;
              read();
              while (next < count && texts[next] !== null) next++;
              while (next < count) {
                g._scrollToFlatIndex(next);
                const start = performance.now();
                let fetched = false;
                for (;;) {
                  await g.updateComplete;
                  read();
                  if (texts[next] !== null && !busy()) break;
                  if (!fetched && texts[next] === null && g.$connector && g.$connector.fetchCurrentRange) {
                    fetched = true;
                    g.$connector.fetchCurrentRange();
                  }
                  if (!g.isConnected || performance.now() - start > pageMs) return null;
                  await tick(5);
                }
                while (next < count && texts[next] !== null) next++;
              }
              return texts;
            }""";

    // ---------------------------------------------------------------- selection, clicks

    @Override
    public void select(int row) {
        rowOf(row).select(); // Dramafinder checks that the row is selected after the click
    }

    @Override
    public void deselect(int row) {
        rowOf(row).deselect();
    }

    @Override
    public boolean isSelected(int row) {
        return rowOf(row).isSelected();
    }

    @Override
    public void selectAll() {
        grid.checkSelectAll();
        AutoWait.waitForVaadinIdle(session.page());
    }

    @Override
    public void deselectAll() {
        grid.uncheckSelectAll();
        AutoWait.waitForVaadinIdle(session.page());
    }

    /** Clicks a spot of the row free of interactive content, so a button or link in a cell isn't hit. */
    @Override
    public void clickRow(int row) {
        rowOf(row).click();
    }

    @Override
    public void doubleClickRow(int row) {
        rowOf(row).doubleClick();
    }

    // ---------------------------------------------------------------- sorting

    /** Flow's direction, {@code null} when the column isn't sorted. */
    @Override
    public SortDirection sortDirection(int column) {
        return switch (headerCell(column).getSortDirection()) {
            case ASCENDING -> SortDirection.ASCENDING;
            case DESCENDING -> SortDirection.DESCENDING;
            case NONE -> null;
        };
    }

    /** {@code null} clears the sort. */
    @Override
    public void sort(int column, SortDirection direction) {
        headerCell(column).sort(direction == null ? GridElement.SortDirection.NONE
                : direction == SortDirection.ASCENDING ? GridElement.SortDirection.ASCENDING
                : GridElement.SortDirection.DESCENDING);
    }

    /**
     * The header cell holding the column's sorter: its own cell or a group's, in whichever header row (#338).
     * Without a sorter, the column's own cell, the lowest one, for Dramafinder's "not sortable" error.
     */
    private GridElement.HeaderCellElement headerCell(int column) {
        List<GridElement.HeaderCellElement> cells = grid.getHeaderCells(dom(column));
        if (cells.isEmpty()) {
            throw new IllegalStateException("No header cell for column " + column);
        }
        for (int i = cells.size() - 1; i >= 0; i--) {
            if (cells.get(i).isSortable()) {
                return cells.get(i);
            }
        }
        return cells.get(cells.size() - 1);
    }

    // ---------------------------------------------------------------- details

    @Override
    public boolean isDetailsOpen(int row) {
        return rowOf(row).isDetailsOpen();
    }

    /** Clicks the row, as a user does (details open on click, Flow's default). */
    @Override
    public void toggleDetails(int row) {
        GridElement.RowElement element = rowOf(row);
        if (element.isDetailsOpen()) {
            element.closeDetails();
        } else {
            element.openDetails();
        }
    }

    // ---------------------------------------------------------------- parts

    @Override
    public UnifiedElement cell(int row, int column) {
        return element(cellOf(row, column).getCellContentLocator());
    }

    /** The column's header cells in every header row (a group header above, a filter row below). */
    @Override
    public UnifiedElement header(int column) {
        return element(grid.getHeaderContentLocator(dom(column)));
    }

    @Override
    public UnifiedElement details(int row) {
        return element(rowOf(row).getDetailsCell().getCellContentLocator());
    }

    private UnifiedElement element(Locator part) {
        return new PlaywrightElement(session, part, null, PlaywrightComponents.DEFAULT);
    }
}
