package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import java.util.List;

/**
 * Grid wrapper. Rows are found by what they show; index access stays available. Rows and columns are
 * lazy; cells, headers and details are Scopes. Row access scrolls the row into view and waits for its data
 * (Auto-Wait), on every Testing Tool. Values are rendered text; the item is reachable only through the
 * Escape Hatch.
 */
@UnifiedComponent(Grid.class)
public interface UnifiedGrid extends UnifiedWrapper, CommonFilters<UnifiedGrid> {

    // rows
    /**
     * A lazy row found by what it shows: {@code row().withCellText("Name", "Alan Turing")}. Every use finds the
     * row again and waits up to the timeout for exactly one match (ADR 0007).
     */
    UnifiedGridRow row();
    /** The row at this index. */
    UnifiedGridRow row(int index);
    /** All rows, not only the rendered ones. Immediate; use assertThat(grid).hasRowCount(n) to wait. */
    int getRowCount();

    // columns
    /** By header text. */
    UnifiedGridColumn column(String header);
    /** By id, for columns without header text (Flow {@code Column.setId}); the id reaches the browser. */
    UnifiedGridColumn columnWithId(String id);
    /** By position among the visible columns; last resort. */
    UnifiedGridColumn column(int index);
    List<String> getColumnHeaders();

    // selection (Auto-Wait)
    void selectAll();
    void deselectAll();

    /**
     * A lazy row. From {@link #row()}, its filters narrow the rows it stands for; each returns a new row and
     * leaves this one unchanged. Actions Auto-Wait for the grid and for exactly one matching row.
     */
    interface UnifiedGridRow {
        // filters
        UnifiedGridRow withCellText(String columnHeader, String text);
        /** Any column. */
        UnifiedGridRow withCellText(String text);
        UnifiedGridRow withCellTextContaining(String columnHeader, String part);
        UnifiedGridRow selected();
        /** Every matching row right now. Only for looping; to check a count, use assertThat(row).hasCount(n). */
        List<UnifiedGridRow> all();

        UnifiedGrid grid();
        int getIndex();
        String getCellText(String columnHeader);
        /** Components inside the cell, e.g. an Edit button. */
        Scope cell(String columnHeader);
        void select();
        void deselect();
        boolean isSelected();
        void click();
        void doubleClick();
        void toggleDetails();
        boolean isDetailsOpen();
        Scope details();
    }

    interface UnifiedGridColumn {
        UnifiedGrid grid();
        /** Header content, e.g. a filter field. */
        Scope header();
        void sort(SortDirection direction);
        SortDirection getSortDirection();
        /** All rows of this column, loading lazily. */
        List<String> getCellTexts();
    }
}
