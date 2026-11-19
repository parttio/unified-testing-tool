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
     * Per visible column, in display order (Dramafinder's column index): the first non-blank text of its header
     * cells, its own cell first, then those of the column groups around it, so a caption under a header group
     * wins over the group's joined cell (#338); "" when none has text. A cell whose content isn't rendered yet
     * gives [header row index, index of the th in its row] instead, to scroll it into view and read again.
     */
    private static final String HEADER_TEXTS_JS = """
            g => {
                const rows = [...g.shadowRoot.querySelectorAll('thead tr')];
                const columns = g._getColumns().filter(c => !c.hidden).sort((a, b) => a._order - b._order);
                return columns.map(column => {
                    for (let c = column; c && c.localName.startsWith('vaadin-grid-column'); c = c.parentElement) {
                        const th = c._headerCell;
                        if (!th || !rows.includes(th.parentElement) || th.parentElement.hidden) continue;
                        const slot = th.querySelector('slot');
                        const content = slot && g.querySelector(
                                ':scope > vaadin-grid-cell-content[slot="' + CSS.escape(slot.name) + '"]');
                        if (!content) {
                            return [rows.indexOf(th.parentElement), [...th.parentElement.children].indexOf(th)];
                        }
                        const text = content.innerText.trim();
                        if (text) return text;
                    }
                    return '';
                });
            }
            """;

    /** Each column's caption, read from whichever header row holds it (a header group may sit above). */
    @Override
    public List<String> columnHeaders() {
        List<?> all = (List<?>) locator.evaluate(HEADER_TEXTS_JS);
        boolean scrolled = false;
        for (Object value : all) {
            if (value instanceof List<?> position) {
                locator.locator("thead tr").nth(((Number) position.get(0)).intValue())
                        .locator(":scope > *").nth(((Number) position.get(1)).intValue())
                        .scrollIntoViewIfNeeded();
                scrolled = true;
            }
        }
        if (scrolled) {
            all = (List<?>) locator.evaluate(HEADER_TEXTS_JS);
        }
        List<?> texts = all;
        return domColumns().stream().map(i -> texts.get(i) instanceof String text ? text : "").toList();
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

    /** A page of rows at a time (Dramafinder), not one scroll per row: fast on a lazy grid. */
    @Override
    public List<String> columnTexts(int column) {
        return grid.getColumnTexts(dom(column)).stream().map(t -> t == null ? null : t.strip()).toList();
    }

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
