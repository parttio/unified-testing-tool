package demo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Runs a failing Unified UI Test and checks it left evidence: a screenshot or the component tree, and its User
 * Steps, on the failure and in the evidence directory.
 */
class FailureEvidenceTest {

    /** Fails on purpose; tagged so Surefire doesn't run it on its own. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class Failing {
        @Test
        @DisplayName("The user fills the name, then the test fails on purpose")
        void fails(UnifiedPage page) {
            page.navigate(ProfileView.class);
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Evidence");
            throw new AssertionError("failing on purpose");
        }
    }

    @Test
    void writesEvidenceOnFailure(@TempDir Path dir) throws Exception {
        TestingTool tool = TestingTool.parse(System.getProperty("vaadin.testing.tool", "browserless"));
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Failing.class))
                .configurationParameter("vaadin.testing.tool", tool.name().toLowerCase())
                .configurationParameter("vaadin.testing.evidence-dir", dir.toString())
                // these tests pin the User Steps' text; what the machine's application logs meanwhile is not theirs
                .configurationParameter("vaadin.testing.user-steps.server-log", "off")
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();

        assertEquals(1, summary.getTotalFailureCount());
        Throwable failure = summary.getFailures().get(0).getException();
        assertEquals("failing on purpose", failure.getMessage());
        Path stepsDir = dir.resolve("FailureEvidenceTest$Failing").resolve("fails");
        String evidence = tool.hasRealBrowser() ? "failure.png" : "failure.tree.txt";
        assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(s -> s.getMessage().startsWith("Failure evidence: ")
                        && s.getMessage().contains(stepsDir.resolve(evidence).toAbsolutePath().toString())),
                "the failure names its evidence: " + Arrays.toString(failure.getSuppressed()));

        String steps = "   1  open Profile\n   2  fill text field “Name” with “Evidence”\n"
                + "   ✘ AssertionError: failing on purpose\n";
        assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(s -> s.getMessage().contains(steps)),
                "the failure shows the User Steps: " + Arrays.toString(failure.getSuppressed()));
        assertTrue(Files.readString(stepsDir.resolve("user-steps.txt")).contains(steps));
        String json = Files.readString(stepsDir.resolve("user-steps.json"));
        assertTrue(json.contains("\"schemaVersion\": 1"));
        // the failure evidence is in the test's own directory, named in user-steps.json, and nothing is at the root
        assertTrue(json.contains("\"evidence\": [\n      \"" + evidence + "\",\n      \"failure.view.txt\"\n    ]"), json);
        assertTrue(Files.readString(stepsDir.resolve("failure.view.txt")).startsWith("Findable view of “/profile”"));
        try (var root = Files.list(dir)) {
            assertEquals(List.of(), root.filter(Files::isRegularFile).map(f -> f.getFileName().toString())
                    .filter(f -> f.startsWith(Failing.class.getName())).toList());
        }

        if (tool.hasRealBrowser()) {
            byte[] png = Files.readAllBytes(stepsDir.resolve("failure.png"));
            assertArrayEquals(new byte[] { (byte) 0x89, 'P', 'N', 'G' }, Arrays.copyOf(png, 4));
        } else {
            String tree = Files.readString(stepsDir.resolve("failure.tree.txt"));
            assertTrue(tree.startsWith("Location: /profile"), tree);
            assertTrue(tree.contains("TextField") && tree.contains("Evidence"), tree);
        }
    }
}
