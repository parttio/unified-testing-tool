package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Findable View ({@code describe()}) reads the same on every Testing Tool: the same components, names, states
 * and lookups (spec chapter 12, "Findable View"). Only the header names the Testing Tool.
 */
@UnifiedSpringBootTest
class FindableViewTest {

    @Test
    @DisplayName("The profile lists its fields and buttons with the lookup that finds each")
    void listsEachComponentWithItsLookup(UnifiedPage page) {
        page.navigate(ProfileView.class);

        assertEquals(List.of(
                "text field “Name”                       page.find(UnifiedTextField.class).withLabel(\"Name\")",
                "text field “Email”                      page.find(UnifiedTextField.class).withLabel(\"Email\")",
                "text field (no name; placeholder “Nickname”)  page.find(UnifiedTextField.class).withPlaceholder(\"Nickname\")",
                "button “Save”                           page.find(UnifiedButton.class).withText(\"Save\")",
                "button “Delete account”                 page.find(UnifiedButton.class).withAccessibleName(\"Delete account\")",
                "button “Archive” (disabled)             page.find(UnifiedButton.class).withText(\"Archive\")",
                "button “Change email”                   page.find(UnifiedButton.class).withText(\"Change email\")"),
                lines(page.describe()));
    }

    @Test
    @DisplayName("A chat's message list shows how many messages it holds")
    void showsAMessageListsMessageCount(UnifiedPage page) {
        page.navigate(ChatView.class);

        // only this line: until #517, Playwright and Selenium also list the message input's own text area
        assertEquals("message list, 1 message  page.find(UnifiedMessageList.class)",
                lines(page.describe()).stream().filter(l -> l.startsWith("message list")).findFirst().orElse(null)
                        .replaceAll(" {2,}", "  "));
    }

    @Test
    @DisplayName("Two fields with the same label are found through the containers that tell them apart")
    void findsAlikeFieldsThroughTheirContainers(UnifiedPage page) {
        page.navigate(CheckoutView.class);

        assertEquals(List.of(
                "text field “Street”  page.find(UnifiedContainer.class).withTestId(\"billing\").find(UnifiedTextField.class).withLabel(\"Street\")",
                "text field “City”    page.find(UnifiedContainer.class).withTestId(\"billing\").find(UnifiedTextField.class).withLabel(\"City\")",
                "text field “Street”  page.find(UnifiedContainer.class).withId(\"shipping\").find(UnifiedTextField.class).withLabel(\"Street\")",
                "text field “City”    page.find(UnifiedContainer.class).withId(\"shipping\").find(UnifiedTextField.class).withLabel(\"City\")",
                "button “Save”        page.find(UnifiedButton.class).withText(\"Save\")",
                "button “Cancel”      page.find(UnifiedButton.class).withText(\"Cancel\")"),
                lines(page.describe()));
    }

    @Test
    @DisplayName("An open dialog describes what is inside it, and the page names the dialog for what is in both")
    void describesAnOpenDialog(UnifiedPage page) {
        page.navigate(ProfileView.class);
        page.find(UnifiedButton.class).withText("Change email").click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Change email");

        String view = dialog.describe();

        assertEquals("Findable view inside dialog “Change email” on " + tool(view) + "; lookups are on it: 3"
                + " components", view.lines().findFirst().orElseThrow());
        assertEquals(List.of(
                "text field “Email”  find(UnifiedTextField.class).withLabel(\"Email\")",
                "button “Cancel”     find(UnifiedButton.class).withText(\"Cancel\")",
                "button “Save”       find(UnifiedButton.class).withText(\"Save\")"), lines(view));
        assertEquals("text field “Email”                      not unique: 2 alike; one in dialog “Change email”:"
                + " page.find(UnifiedDialog.class).withHeaderTitle(\"Change email\").find(UnifiedTextField.class)"
                + ".withLabel(\"Email\")", lines(page.describe()).get(2));
    }

    /** {@code browserless}, {@code playwright (chromium)}: the Testing Tool as the header names it. */
    private static String tool(String view) {
        String header = view.lines().findFirst().orElseThrow();
        return header.substring(header.indexOf(" on ") + 4, header.indexOf(";"));
    }

    /** The view without its header line. */
    private static List<String> lines(String view) {
        return view.lines().skip(1).toList();
    }
}
