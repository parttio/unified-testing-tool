package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * A failed row or message step's evidence holds that grid's or message list's items, as {@code describe()} lists
 * them, after the page's view (#590); another failure's doesn't.
 */
class ItemsEvidenceTest {

    /** Fails on purpose; tagged so Surefire doesn't run it on its own. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class Failing {
        @Test
        @DisplayName("The user opens the row of a person who isn't there")
        void rowLookup(UnifiedPage page) {
            page.navigate(UsersView.class);
            page.find(UnifiedGrid.class).withTestId("users").row().withCellText("Name", "Nobody").click();
        }

        @Test
        @DisplayName("The first chat message is checked against the wrong text")
        void messageCheck(UnifiedPage page) {
            page.navigate(ChatView.class);
            assertThat(page.find(UnifiedMessageList.class).message(0)).hasText("Hello");
        }

        @Test
        @DisplayName("The user clicks a button the profile doesn't have")
        void buttonLookup(UnifiedPage page) {
            page.navigate(ProfileView.class);
            page.find(UnifiedButton.class).withText("Nope").click();
        }
    }

    @Test
    void aFailedRowOrMessageStepKeepsItsItemsInTheView(@TempDir Path dir) throws Exception {
        TestingTool tool = TestingTool.parse(System.getProperty("vaadin.testing.tool", "browserless"));
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Failing.class))
                .configurationParameter("vaadin.testing.tool", tool.name().toLowerCase())
                .configurationParameter("vaadin.testing.evidence-dir", dir.toString())
                .configurationParameter("vaadin.testing.timeout", "1s")
                .configurationParameter("vaadin.testing.user-steps.server-log", "off")
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        assertEquals(3, listener.getSummary().getTotalFailureCount());
        Path tests = dir.resolve("ItemsEvidenceTest$Failing");

        String row = Files.readString(tests.resolve("rowLookup").resolve("failure.view.txt"));
        assertTrue(row.contains("\nFindable view of grid (test id “users”) on "), row);
        assertTrue(row.contains(", 120 rows; lookups are on it\n"), row);
        assertTrue(row.lines().anyMatch(l -> l.startsWith("row 0: User 1 | user1@example.com | Member | Edit")
                && l.endsWith("row().withCellText(\"Name\", \"User 1\")")), row);

        String message = Files.readString(tests.resolve("messageCheck").resolve("failure.view.txt"));
        assertTrue(message.contains("\nFindable view of message list on "), message);
        assertTrue(message.lines().anyMatch(l -> l.startsWith("message 0 from “Assistant”: Hi! Ask me for a recipe.")),
                message);

        String button = Files.readString(tests.resolve("buttonLookup").resolve("failure.view.txt"));
        assertFalse(button.contains("lookups are on it"), button);
    }
}
