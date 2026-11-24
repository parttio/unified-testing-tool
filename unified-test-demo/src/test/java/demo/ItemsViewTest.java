package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@code describe()} on a grid and a message list reads the same on every Testing Tool (#534). */
@UnifiedSpringBootTest
class ItemsViewTest {

    /** The view's lines without its title, each run of spaces between description and lookup as two. */
    private static List<String> lines(String view) {
        return view.lines().skip(1).map(l -> l.replaceAll(" {2,}", "  ")).toList();
    }

    @Test
    @DisplayName("The users grid lists its columns and its first rows, with the lookup that finds each")
    void describesAGrid(UnifiedPage page) {
        page.navigate(UsersView.class);
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users");

        String view = users.describe();

        String title = view.lines().findFirst().orElseThrow();
        assertTrue(title.startsWith("Findable view of grid (test id “users”) on ")
                && title.endsWith(", 120 rows; lookups are on it"), title);
        List<String> lines = lines(view);
        assertEquals(List.of(
                "column “Name”  column(\"Name\")",
                "column “Email”  column(\"Email\")",
                "column “Role”  column(\"Role\")",
                "column “Actions”  column(\"Actions\")",
                "row 0: User 1 | user1@example.com | Member | Edit  row().withCellText(\"Name\", \"User 1\")"),
                lines.subList(0, 5));
        assertEquals("row 9: User 10 | user10@example.com | Admin | Edit  row().withCellText(\"Name\", \"User 10\")",
                lines.get(4 + 9));
        assertEquals("… 100 more rows  row()  (120 rows in all)", lines.get(4 + 20));
        assertEquals(4 + 21, lines.size());
    }

    @Test
    @DisplayName("A chat lists its messages, with the lookup that finds each")
    void describesAMessageList(UnifiedPage page) {
        page.navigate(ChatView.class);
        UnifiedMessageList messages = page.find(UnifiedMessageList.class);
        UnifiedMessageInput input = page.find(UnifiedMessageInput.class);
        input.send("Soup");
        input.send("Pancakes");
        com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat(messages).hasMessageCount(5);

        assertEquals(List.of(
                "message 0 from “Assistant”: Hi! Ask me for a recipe.  message(0)",
                "message 1 from “You”: Soup  message(1)",
                "message 2 from “Assistant”: Here is a recipe for **Soup**.  message(2)",
                "message 3 from “You”: Pancakes  message().withUserName(\"You\").last()",
                "message 4 from “Assistant”: Here is a recipe for **Pancakes**.  message().withUserName(\"Assistant\").last()"),
                lines(messages.describe()));
    }
}
