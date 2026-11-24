package com.vaadin.testing.unified.internal.grid;

import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedGrid.UnifiedGridColumn;
import com.vaadin.testing.unified.component.UnifiedGrid.UnifiedGridRow;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.ElementResolver;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.LazyScope;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.GridAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.vaadin.testing.unified.internal.steps.Phrase;
import com.vaadin.testing.unified.internal.steps.StepTarget;

/**
 * Core implementation of Grid rows and columns, on {@link GridAccess}. Row, column and selection actions go
 * through {@link LazyElement#act} on the grid, so they Auto-Wait like every other action.
 */
public final class GridSupport {
    private GridSupport() {}

    public static UnifiedGridRow row(UnifiedGrid grid) {
        return new Row(grid, null, List.of(), false);
    }

    public static UnifiedGridRow row(UnifiedGrid grid, int index) {
        return new Row(grid, index, List.of(), false);
    }

    public static UnifiedGridColumn columnByHeader(UnifiedGrid grid, String header) {
        return new Column(grid, Column.By.HEADER, header, -1);
    }

    public static UnifiedGridColumn columnById(UnifiedGrid grid, String id) {
        return new Column(grid, Column.By.ID, id, -1);
    }

    public static UnifiedGridColumn columnByIndex(UnifiedGrid grid, int index) {
        return new Column(grid, Column.By.INDEX, null, index);
    }

    public static void selectAll(UnifiedGrid grid) {
        act(grid, "select all rows of", GridAccess::selectAll);
    }

    public static void deselectAll(UnifiedGrid grid) {
        act(grid, "deselect all rows of", GridAccess::deselectAll);
    }

    public static TestRun runOf(UnifiedGrid grid) {
        return Wrappers.lazyElementOf(grid).run();
    }

    /** The row in user words, for the User Step reports. */
    public static StepTarget targetOf(UnifiedGridRow row) {
        return row instanceof Row r ? r.target() : StepTarget.of(row.toString());
    }

    /** The column in user words, for the User Step reports. */
    public static StepTarget targetOf(UnifiedGridColumn column) {
        return column instanceof Column c ? c.target() : StepTarget.of(column.toString());
    }

    /** How many rows a row from {@link UnifiedGrid#row()} matches right now; no waiting. */
    public static int countNow(UnifiedGridRow row) {
        return ((Row) row).matching(access(row.grid())).size();
    }

    /** A grid action with Auto-Wait: the grid is visible and enabled before, the adapter waits after. */
    static void act(UnifiedGrid grid, String action, Consumer<GridAccess> body) {
        Wrappers.lazyElementOf(grid).act(action, e -> body.accept(e.unwrap(GridAccess.class)));
    }

    /** A Scope over a part of the grid (cell, header, details), resolved again on every use. */
    static Scope scope(UnifiedGrid grid, String description, StepTarget target,
            java.util.function.Function<GridAccess, UnifiedElement> part) {
        ElementResolver resolver = new ElementResolver() {
            @Override
            public List<UnifiedElement> resolve() {
                return List.of(part.apply(access(grid)));
            }

            @Override
            public String describe() {
                return description;
            }

            @Override
            public StepTarget userTarget() {
                return target;
            }
        };
        return new LazyScope(new LazyElement(resolver, -1, runOf(grid)));
    }

    /** The grid as the scope of its rows and columns: {@code grid (test id “people”) in dialog “Team”}. */
    static String gridScope(UnifiedGrid grid) {
        return StepTarget.scopeOf(Wrappers.lazyElementOf(grid));
    }

    /** A row or column action: a User Step of its own, with Auto-Wait on the grid. */
    static void record(UnifiedGrid grid, Supplier<Phrase> phrase, String action, Consumer<GridAccess> body) {
        TestRun run = runOf(grid);
        run.steps().action(phrase, run.window(), () -> act(grid, action, body));
    }

    static GridAccess access(UnifiedGrid grid) {
        return grid.element().unwrap(GridAccess.class);
    }

    static int columnIndex(GridAccess access, String header) {
        int index = access.columnHeaders().indexOf(header);
        if (index < 0) {
            throw new IllegalArgumentException("No column with header \"" + header + "\"; headers are "
                    + access.columnHeaders());
        }
        return index;
    }

    static int columnIndex(GridAccess access, int index) {
        int columns = access.columnHeaders().size();
        if (index < 0 || index >= columns) {
            throw new IllegalArgumentException("No visible column at index " + index + " (" + columns + " columns)");
        }
        return index;
    }

    /** {@code at index 2}: the API's own 0-based index, so the report matches the code. */
    static String ordinalWords(int index) {
        return "at index " + index;
    }

    // ---------------------------------------------------------------- rows

    /** A cell filter on one column, or on any column when {@code column} is null. */
    private record CellFilter(String column, Filter filter) {
        /** {@code Name is “Alan Turing”}, {@code a cell contains “Turing”}. */
        String words() {
            return (column == null ? "a cell" : column) + (filter.exact() ? " is " : " contains ")
                    + "“" + filter.value() + "”";
        }

        @Override
        public String toString() {
            return (filter.exact() ? "withCellText" : "withCellTextContaining") + "("
                    + (column == null ? "" : "\"" + column + "\", ") + "\"" + filter.value() + "\")";
        }

        List<Integer> matching(GridAccess access) {
            if (column != null) {
                return access.rowsWhere(columnIndex(access, column), filter::matches);
            }
            TreeSet<Integer> rows = new TreeSet<>();
            for (int c = 0; c < access.columnHeaders().size(); c++) {
                rows.addAll(access.rowsWhere(c, filter::matches));
            }
            return new ArrayList<>(rows);
        }
    }

    /**
     * A lazy row: {@code row(n)} when {@code fixedIndex} is set, otherwise the one row matching the filters,
     * found again on every call.
     */
    public record Row(UnifiedGrid grid, Integer fixedIndex, List<CellFilter> filters, boolean selectedOnly)
            implements UnifiedGridRow {

        // ------------------------------------------------------------ filters

        private UnifiedGridRow with(String column, String text, boolean exact) {
            List<CellFilter> copy = new ArrayList<>(filters);
            copy.add(new CellFilter(column, new Filter(Filter.Kind.TEXT, null, text, exact)));
            return new Row(grid, fixedIndex, List.copyOf(copy), selectedOnly);
        }

        @Override public UnifiedGridRow withCellText(String column, String text) { return with(column, text, true); }
        @Override public UnifiedGridRow withCellText(String text) { return with(null, text, true); }
        @Override public UnifiedGridRow withCellTextContaining(String column, String part) { return with(column, part, false); }
        @Override public UnifiedGridRow selected() { return new Row(grid, fixedIndex, filters, true); }

        @Override
        public List<UnifiedGridRow> all() {
            return matching(access(grid)).stream().map(i -> GridSupport.row(grid, i)).toList();
        }

        /** Matching row indexes right now. */
        List<Integer> matching(GridAccess access) {
            List<Integer> rows = null;
            for (CellFilter f : filters) {
                List<Integer> matching = f.matching(access);
                if (rows == null) {
                    rows = new ArrayList<>(matching);
                } else {
                    rows.retainAll(matching);
                }
            }
            if (selectedOnly) {
                List<Integer> selected = access.selectedRows();
                if (rows == null) {
                    rows = new ArrayList<>(selected);
                } else {
                    rows.retainAll(selected);
                }
            }
            if (rows == null) {
                if (fixedIndex != null) {
                    return fixedIndex >= 0 && fixedIndex < access.rowCount() ? List.of(fixedIndex) : List.of();
                }
                rows = new ArrayList<>();
                for (int i = 0; i < access.rowCount(); i++) {
                    rows.add(i);
                }
            }
            if (fixedIndex != null) {
                rows.retainAll(List.of(fixedIndex));
            }
            return rows;
        }

        /** The row index: {@code row(n)} as is; otherwise waits until exactly one row matches. */
        @Override
        public int getIndex() {
            if (fixedIndex != null && filters.isEmpty() && !selectedOnly) {
                return fixedIndex;
            }
            int[] last = { 0 };
            return runOf(grid).pollFor(() -> {
                List<Integer> rows = matching(access(grid));
                last[0] = rows.size();
                return rows.size() == 1 ? rows.get(0) : null;
            }, () -> com.vaadin.testing.unified.internal.failures.FailureParts.of(
                    "exactly one " + target().renderWithScope(), last[0] == 0 ? "none" : String.valueOf(last[0])));
        }

        // ------------------------------------------------------------ actions (Auto-Wait)

        private void act(String verb, String action, BiConsumer<GridAccess, Integer> body) {
            record(grid, () -> Phrase.of(verb, target()), action + " " + this + " of",
                    access -> body.accept(access, getIndex()));
        }

        @Override public void select() { act("select", "select", GridAccess::select); }
        @Override public void deselect() { act("deselect", "deselect", GridAccess::deselect); }
        @Override public void click() { act("click", "click", GridAccess::clickRow); }
        @Override public void doubleClick() { act("double-click", "double-click", GridAccess::doubleClickRow); }
        @Override public void toggleDetails() { act("expand", "toggle the details of", GridAccess::toggleDetails); }

        /** {@code row where Name is “Alan Turing”} in the grid, for the User Step reports. */
        public StepTarget target() {
            StepTarget row = StepTarget.of(selectedOnly ? "selected row" : "row")
                    .withFilters(filters.stream().map(CellFilter::words).toList(), true)
                    .withScope(gridScope(grid));
            return fixedIndex == null ? row : row.withQualifier(ordinalWords(fixedIndex));
        }

        // ------------------------------------------------------------ getters (immediate)

        @Override
        public String getCellText(String columnHeader) {
            int row = getIndex();
            GridAccess access = access(grid);
            return access.cellText(row, columnIndex(access, columnHeader));
        }

        @Override
        public String getCellText(int columnIndex) {
            return access(grid).cellText(getIndex(), columnIndex);
        }

        @Override
        public Scope cell(String columnHeader) {
            return scope(grid, this + ".cell(\"" + columnHeader + "\")",
                    StepTarget.of("cell").withName(columnHeader).withScope(target().renderWithScope()),
                    access -> access.cell(getIndex(), columnIndex(access, columnHeader)));
        }

        @Override
        public Scope cell(int columnIndex) {
            return scope(grid, this + ".cell(" + columnIndex + ")",
                    StepTarget.of("cell").withQualifier(ordinalWords(columnIndex))
                            .withScope(target().renderWithScope()),
                    access -> access.cell(getIndex(), columnIndex(access, columnIndex)));
        }

        @Override public boolean isSelected() { return access(grid).isSelected(getIndex()); }
        @Override public boolean isDetailsOpen() { return access(grid).isDetailsOpen(getIndex()); }

        @Override
        public Scope details() {
            return scope(grid, this + ".details()",
                    StepTarget.of("details").withScope(target().renderWithScope()),
                    access -> access.details(getIndex()));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(fixedIndex == null ? "row()" : "row(" + fixedIndex + ")");
            filters.forEach(f -> sb.append('.').append(f));
            if (selectedOnly) {
                sb.append(".selected()");
            }
            return sb.toString();
        }
    }

    // ---------------------------------------------------------------- columns

    public record Column(UnifiedGrid grid, By by, String name, int index) implements UnifiedGridColumn {

        public enum By { HEADER, ID, INDEX }

        @Override
        public int getIndex() {
            GridAccess access = access(grid);
            return switch (by) {
                case HEADER -> columnIndex(access, name);
                case ID -> {
                    int found = access.columnIndexOfId(name);
                    if (found < 0) {
                        throw new IllegalArgumentException("No visible column with id \"" + name + "\"");
                    }
                    yield found;
                }
                case INDEX -> columnIndex(access, index);
            };
        }

        @Override
        public List<String> getCellTexts() {
            int column = getIndex();
            return access(grid).columnTexts(column);
        }

        @Override
        public Scope header() {
            return scope(grid, this + ".header()",
                    StepTarget.of("header").withScope(target().renderWithScope()),
                    access -> access.header(getIndex()));
        }

        @Override
        public void sort(SortDirection direction) {
            record(grid, () -> Phrase.of("sort", target())
                            .withTrailing(direction.name().toLowerCase(java.util.Locale.ROOT)),
                    "sort " + this + " of", access -> access.sort(getIndex(), direction));
        }

        /** {@code column “Name”} in the grid, for the User Step reports. */
        public StepTarget target() {
            StepTarget column = StepTarget.of("column").withScope(gridScope(grid));
            return switch (by) {
                case HEADER -> column.withName(name);
                case ID -> column.withFilters(List.of("id “" + name + "”"), false);
                case INDEX -> column.withQualifier(ordinalWords(index));
            };
        }

        @Override
        public SortDirection getSortDirection() {
            return access(grid).sortDirection(getIndex());
        }

        @Override
        public String toString() {
            return switch (by) {
                case HEADER -> "column(\"" + name + "\")";
                case ID -> "columnWithId(\"" + name + "\")";
                case INDEX -> "column(" + index + ")";
            };
        }
    }
}
