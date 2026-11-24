package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.grid.GridSupport;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.spi.GridAccess;
import java.time.Duration;
import java.util.List;

/**
 * Grid wrapper. Rows are found by what they show; index access stays available. Rows and columns are
 * lazy. Row access scrolls the row into view and waits for its data (Auto-Wait), on every Testing Tool.
 * Values are rendered text; the item is reachable only through the Escape Hatch.
 */
@UnifiedComponent(Grid.class)
public interface UnifiedGrid extends UnifiedWrapper, CommonFilters<UnifiedGrid> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedGrid withTimeout(Duration timeout);

    // rows
    /**
     * A lazy row found by what it shows: {@code row().withCellText("Name", "Alan Turing")}. Every use finds the
     * row again and waits up to the timeout for exactly one match.
     */
    default UnifiedGridRow row() { return GridSupport.row(this); }
    /** The row at this index. */
    default UnifiedGridRow row(int index) { return GridSupport.row(this, index); }
    /**
     * The Findable View of the grid: its columns and rows, each with the lookup on this grid that finds it
     * ({@code row().withCellText("Name", "Alan Turing")}). The first rows only; print it, don't assert on it.
     */
    default String describe() { return com.vaadin.testing.unified.internal.ItemsView.ofGrid(this); }
    /** All rows, not only the rendered ones. Immediate; use assertThat(grid).hasRowCount(n) to wait. */
    default int getRowCount() { return element().unwrap(GridAccess.class).rowCount(); }

    // columns
    /** By header text (recommended). */
    default UnifiedGridColumn column(String header) { return GridSupport.columnByHeader(this, header); }
    /** By id, for columns without header text (Flow {@code Column.setId}); the id reaches the browser. */
    default UnifiedGridColumn columnWithId(String id) { return GridSupport.columnById(this, id); }
    /** By position among the visible columns; last resort, breaks when columns are reordered. */
    default UnifiedGridColumn column(int index) { return GridSupport.columnByIndex(this, index); }
    default List<String> getColumnHeaders() { return element().unwrap(GridAccess.class).columnHeaders(); }

    // selection (Auto-Wait)
    default void selectAll() { GridSupport.selectAll(this); }
    default void deselectAll() { GridSupport.deselectAll(this); }

    /**
     * A lazy row. From {@link #row()}, its filters narrow the rows it stands for; each returns a new row and
     * leaves this one unchanged. Actions Auto-Wait for the grid and for exactly one matching row. Its
     * {@code toString()} reads as the target of its User Steps, {@code row where Name is “Alan Turing” in grid}, so
     * that it reads right in a User Task name: {@code page.step("Edit " + row, ..)}. It asks the grid nothing.
     */
    interface UnifiedGridRow {
        // filters
        /** Rows whose cell in this column shows exactly this text. */
        UnifiedGridRow withCellText(String columnHeader, String text);
        /** Rows with a cell showing exactly this text, in any column. */
        UnifiedGridRow withCellText(String text);
        UnifiedGridRow withCellTextContaining(String columnHeader, String part);
        /** Selected rows only. */
        UnifiedGridRow selected();
        /**
         * Every matching row right now, by its index. Only for looping over rows; to check a count, use
         * {@code assertThat(row).hasCount(n)}, which waits.
         */
        List<UnifiedGridRow> all();

        UnifiedGrid grid();
        int getIndex();
        String getCellText(String columnHeader);
        /** By index among the visible columns: for columns without header text; prefer the header. */
        String getCellText(int columnIndex);
        /** Components inside the cell, e.g. an Edit button. */
        Scope cell(String columnHeader);
        /**
         * Components inside the cell, by index among the visible columns: for columns without header text, e.g.
         * an action column; prefer the header.
         */
        Scope cell(int columnIndex);
        void select();
        void deselect();
        boolean isSelected();
        void click();
        void doubleClick();
        void toggleDetails();
        boolean isDetailsOpen();
        /** Components inside the row's details. */
        Scope details();
    }

    /**
     * A lazy column. Its {@code toString()} reads as the target of its User Steps, {@code column “Name” in grid},
     * and asks the grid nothing.
     */
    interface UnifiedGridColumn {
        UnifiedGrid grid();
        /** Index among the visible columns. */
        int getIndex();
        /** All rows of this column, loading lazily. */
        List<String> getCellTexts();
        /** Components in the column's header cells, e.g. a filter field. */
        Scope header();
        void sort(SortDirection direction);
        /** Null when not sorted. */
        SortDirection getSortDirection();
    }
}
