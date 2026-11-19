package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * The failure that docs/guide/getting-started.md shows in "4. Break it": its first test ({@link
 * demo.ProfileViewTest#savesName}) with the Email field's label mistyped. Runs it through the JUnit Launcher on this
 * run's Testing Tool and compares what it reports with the text the guide includes.
 */
class BreakItTest {

    /** The guide's first test, broken; tagged so Surefire doesn't run it on its own. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class ProfileViewTest {

        @Test
        @DisplayName("The user saves a name and an email and sees a confirmation")
        void savesName(UnifiedPage page) {
            page.navigate(ProfileView.class);

            UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
            name.setValue("Alice");
            page.find(UnifiedTextField.class).withLabel("E-mail").setValue("alice@example.com");
            page.find(UnifiedButton.class).withText("Save").click();

            assertThat(page.find(UnifiedNotification.class)).isOpen().hasText("Saved Alice");
            assertThat(name).hasValue("Alice").isValid();
        }
    }

    private static final String SCENARIO = "The user saves a name and an email and sees a confirmation";

    @TempDir
    static Path dir;
    private static UnifiedAssertionError failure;

    @BeforeAll
    static void runTheBrokenTest() {
        TestingTool tool = TestingTool.parse(System.getProperty("vaadin.testing.tool", "browserless"));
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(ProfileViewTest.class))
                .configurationParameter("vaadin.testing.tool", tool.name().toLowerCase())
                .configurationParameter("vaadin.testing.evidence-dir", dir.toString())
                .configurationParameter("vaadin.testing.timeout", "2s")
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        assertEquals(1, summary.getTotalFailureCount(), () -> summary.getFailures().toString());
        failure = assertInstanceOf(UnifiedAssertionError.class, summary.getFailures().get(0).getException());
    }

    @Test
    void theFailureMessageIsTheOneTheGuideShows() throws Exception {
        assertSameLines(expected("message.txt"), failure.getMessage().lines().toList(), 6);
    }

    @Test
    void theUserStepsAreTheOnesTheGuideShows() throws Exception {
        Path steps = dir.resolve("BreakItTest$ProfileViewTest").resolve("savesName").resolve("user-steps.txt");
        List<String> actual = Files.readAllLines(steps);
        List<String> expected = expected("user-steps.txt");
        // the heading names this test's class and the run's Testing Tool and time: compare the scenario only
        assertTrue(actual.get(0).startsWith("✘ ") && actual.get(0).contains(" › " + SCENARIO + "   "), actual.get(0));
        assertSameLines(expected.subList(1, expected.size()), actual.subList(1, actual.size()), 4);
    }

    /**
     * The first {@code exact} lines are the same; the lines after them, which depend on the run (the time waited,
     * the Testing Tool, the code line, the evidence directory), start with the same label.
     */
    private static void assertSameLines(List<String> expected, List<String> actual, int exact) {
        assertEquals(expected.size(), actual.size(), String.join("\n", actual));
        assertEquals(expected.subList(0, exact), actual.subList(0, exact));
        for (int i = exact; i < expected.size(); i++) {
            String label = expected.get(i).substring(0, expected.get(i).stripLeading().indexOf(' ')
                    + expected.get(i).length() - expected.get(i).stripLeading().length());
            assertTrue(actual.get(i).startsWith(label), () -> "expected a line starting with: " + label);
        }
    }

    private static List<String> expected(String file) throws Exception {
        try (var in = BreakItTest.class.getResourceAsStream("break-it/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
