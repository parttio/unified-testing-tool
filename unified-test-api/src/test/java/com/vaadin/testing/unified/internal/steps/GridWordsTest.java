package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedGrid.UnifiedGridColumn;
import com.vaadin.testing.unified.component.UnifiedGrid.UnifiedGridRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A grid row's and column's {@code toString()} read as their User Step target (#620). */
class GridWordsTest {

    private final FakePage fake = new FakePage();
    private UnifiedGrid grid;

    @BeforeEach
    void grid() {
        fake.add("vaadin-grid", "testId", "people", "headers", "Name,Role",
                "rows", "Alan Turing|Admin;Ada Lovelace|User", "selected", "1");
        grid = fake.page.find(UnifiedGrid.class).withTestId("people");
    }

    /** The target of the row's click User Step, as the report shows it. */
    private String clickTarget(UnifiedGridRow row) {
        row.click();
        String line = fake.lines().lines().reduce((a, b) -> b).orElseThrow().strip();
        return line.substring(line.indexOf("click ") + "click ".length());
    }

    @Test
    void aRowReadsAsTheTargetOfItsUserSteps() {
        UnifiedGridRow exact = grid.row().withCellText("Name", "Alan Turing");
        UnifiedGridRow containing = grid.row().withCellTextContaining("Name", "Lovelace");
        UnifiedGridRow second = grid.row(1);
        UnifiedGridRow selected = grid.row().selected();

        assertEquals("row where Name is “Alan Turing” in grid (test id “people”)", String.valueOf(exact));
        assertEquals(clickTarget(exact), String.valueOf(exact));
        assertEquals(clickTarget(containing), String.valueOf(containing));
        assertEquals(clickTarget(second), String.valueOf(second));
        assertEquals(clickTarget(selected), String.valueOf(selected));
    }

    @Test
    void aColumnReadsAsTheTargetOfItsUserSteps() {
        UnifiedGridColumn name = grid.column("Name");

        name.sort(SortDirection.ASCENDING);

        assertEquals("column “Name” in grid (test id “people”)", String.valueOf(name));
        assertEquals(com.vaadin.testing.unified.internal.grid.GridSupport.targetOf(name).renderWithScope(),
                String.valueOf(name));
        assertEquals("sort column “Name” ascending in grid (test id “people”)",
                fake.lines().lines().reduce((a, b) -> b).orElseThrow().strip().replaceFirst("^\\d+\\s+", ""));
    }

    @Test
    void readingARowOrColumnAsTextAsksTheAdapterNothingAndRecordsNoRead() {
        UnifiedGridRow row = grid.row().withCellText("Name", "Alan Turing");
        UnifiedGridColumn column = grid.column("Name");
        row.click();
        int calls = fake.adapterCalls.get();

        String.valueOf(row);
        String.valueOf(column);
        String.valueOf(grid.row(0).cell("Name"));

        assertEquals(calls, fake.adapterCalls.get());
        fake.steps.finish();
        String json = new UserStepsReport("demo.FakeTest", "test", "test", "browserless", "passed", null, fake.steps)
                .json();
        assertEquals(false, json.contains("readsAfter"), json);
    }

    @Test
    void aRowInAUserTaskNameReadsAsTheReportsWords() {
        UnifiedGridRow row = grid.row().withCellText("Name", "Alan Turing");

        fake.page.step("Edit " + row, row::click);

        assertEquals(List.of(
                "1  Edit row where Name is “Alan Turing” in grid (test id “people”)",
                "   1.1  click row where Name is “Alan Turing” in grid (test id “people”)"),
                fake.lines().lines().map(String::strip).map(l -> l.startsWith("1.1") ? "   " + l : l).toList());
        fake.steps.finish();
        JsonNode json = new ObjectMapper().readTree(new UserStepsReport("demo.FakeTest", "test", "test",
                "browserless", "passed", null, fake.steps).json());
        assertEquals("Edit row where Name is “Alan Turing” in grid (test id “people”)",
                json.get("steps").get(0).get("name").asText());
    }
}
