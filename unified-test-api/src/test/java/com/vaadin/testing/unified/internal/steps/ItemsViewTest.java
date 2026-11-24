package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Findable View of a grid's rows and a message list's messages: {@code describe()} on them (#534). */
class ItemsViewTest {

    private final FakePage fake = new FakePage();

    /** The view's lines without its title, each run of spaces between description and lookup as two. */
    private static List<String> lines(String view) {
        return view.lines().skip(1).map(l -> l.replaceAll(" {2,}", "  ")).toList();
    }

    @Test
    void aGridListsItsColumnsAndEachRowWithTheLookupThatFindsIt() {
        fake.add("vaadin-grid", "headers", "Name,Email,Role",
                "rows", "Alan Turing|alan@example.com|Admin;Ada Lovelace|ada@example.com|Admin;"
                        + "Ada Lovelace|ada2@example.com|User");
        UnifiedGrid grid = fake.page.find(UnifiedGrid.class);

        String view = grid.describe();

        assertEquals("Findable view of grid on browserless, 3 rows; lookups are on it", view.lines().findFirst().orElseThrow());
        assertEquals(List.of(
                "column “Name”  column(\"Name\")",
                "column “Email”  column(\"Email\")",
                "column “Role”  column(\"Role\")",
                "row 0: Alan Turing | alan@example.com | Admin  row().withCellText(\"Name\", \"Alan Turing\")",
                // the name is shared: the first column whose text is its own
                "row 1: Ada Lovelace | ada@example.com | Admin  row().withCellText(\"Email\", \"ada@example.com\")",
                "row 2: Ada Lovelace | ada2@example.com | User  row().withCellText(\"Email\", \"ada2@example.com\")"),
                lines(view));
    }

    /** {@code n} rows, "User 1" to "User n", all with the role "User". */
    private static String users(int n) {
        StringBuilder rows = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            rows.append(i == 1 ? "" : ";").append("User ").append(i).append("|User");
        }
        return rows.toString();
    }

    @Test
    void theFirstTwentyRowsAreListedAndTheRestCounted() {
        fake.add("vaadin-grid", "headers", "Name,Role", "rows", users(25));

        List<String> lines = lines(fake.page.find(UnifiedGrid.class).describe());

        assertEquals("row 19: User 20 | User  row().withCellText(\"Name\", \"User 20\")", lines.get(2 + 19));
        assertEquals("… 5 more rows  row()  (25 rows in all)", lines.get(2 + 20));
        assertEquals(2 + 21, lines.size());
    }

    @Test
    void aGridTooLongToCompareFindsItsRowsByIndex() {
        fake.add("vaadin-grid", "headers", "Name,Role", "rows", users(201));

        List<String> lines = lines(fake.page.find(UnifiedGrid.class).describe());

        assertEquals("row 0: User 1 | User  row(0)", lines.get(2));
    }

    @Test
    void aMessageListListsEachMessageWithTheLookupThatFindsIt() {
        fake.add("vaadin-message-list", "messages", "Mise:Hi! I am Mise, your meal planner. Ask me anything.|You:What's on Friday?"
                + "|Mise:Pancakes|System:Plan saved|Mise:Got it, Friday is pancakes.");
        UnifiedMessageList chat = fake.page.find(UnifiedMessageList.class);

        String view = chat.describe();

        assertEquals("Findable view of message list on browserless, 5 messages; lookups are on it",
                view.lines().findFirst().orElseThrow());
        assertEquals(List.of(
                "message 0 from “Mise”: Hi! I am Mise, your meal planner. Ask m…  message(0)",
                "message 1 from “You”: What's on Friday?  message().withUserName(\"You\")",
                "message 2 from “Mise”: Pancakes  message(2)",
                "message 3 from “System”: Plan saved  message().withUserName(\"System\")",
                "message 4 from “Mise”: Got it, Friday is pancakes.  message().withUserName(\"Mise\").last()"),
                lines(view));
    }
}
