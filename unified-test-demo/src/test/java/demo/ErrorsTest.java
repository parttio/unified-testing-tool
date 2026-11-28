package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** What goes wrong behind the user's back: the application's log and the browser's console. */
@UnifiedSpringBootTest
class ErrorsTest {

    // --8<-- [start:serverError]
    @Test
    @DisplayName("Saving a draft that the server can't save leaves an error the test finds")
    void findsTheErrorTheServerLogged(UnifiedPage page) {
        page.navigate(DraftErrorsView.class);
        assertThat(page).hasNoErrors();

        page.find(UnifiedButton.class).withText("Save draft").click();

        UnifiedAssertionError failure = assertThrows(UnifiedAssertionError.class, () -> assertThat(page).hasNoErrors());
        assertTrue(failure.getMessage().contains(
                "server ERROR DraftErrorsView: Could not save the draft (IllegalStateException: no id)"),
                failure.getMessage());
    }
    // --8<-- [end:serverError]

    /** The console is the browser's: Playwright and Chrome on Selenium read it. */
    @Test
    @RequiresBrowser
    @DisplayName("Loading a chart that fails in the browser leaves a console error the test finds")
    void findsTheErrorInTheBrowsersConsole(UnifiedPage page) {
        page.navigate(DraftErrorsView.class);

        page.find(UnifiedButton.class).withText("Load chart").click();

        UnifiedAssertionError failure = assertThrows(UnifiedAssertionError.class, () -> assertThat(page).hasNoErrors());
        assertTrue(failure.getMessage().contains("console ERROR"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Could not load the chart"), failure.getMessage());
        assertFalse(failure.getMessage().contains("server ERROR"), failure.getMessage());
    }

    /** Run by {@link #ignoresTheErrorsTheRunSaysToIgnore} with the setting; tagged so Surefire doesn't run it alone. */
    @UnifiedSpringBootTest
    @Tag("launched-only")
    static class KnownErrors {
        @Test
        @DisplayName("Errors the run ignores don't count")
        void ignoresThem(UnifiedPage page) {
            page.navigate(DraftErrorsView.class);
            page.find(UnifiedButton.class).withText("Save draft").click();
            page.find(UnifiedButton.class).withText("Load chart").click();

            assertThat(page).hasNoErrors();
        }
    }

    /** A known error, such as a browser's network noise, is set aside for the whole run, and still shows. */
    @Test
    @DisplayName("Errors the run says to ignore don't fail hasNoErrors() and still show under their step")
    void ignoresTheErrorsTheRunSaysToIgnore(@TempDir Path dir) throws Exception {
        TestingTool tool = TestingTool.parse(System.getProperty("vaadin.testing.tool", "browserless"));
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(KnownErrors.class))
                .configurationParameter("vaadin.testing.tool", tool.name().toLowerCase())
                .configurationParameter("vaadin.testing.evidence-dir", dir.toString())
                .configurationParameter("vaadin.testing.user-steps.ignored-errors",
                        "^server ERROR DraftErrorsView: Could not save the draft|Could not load the chart")
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();

        assertEquals(1, summary.getTestsSucceededCount(),
                () -> summary.getFailures().stream().map(f -> String.valueOf(f.getException())).toList().toString());
        String steps = Files.readString(dir.resolve("ErrorsTest$KnownErrors").resolve("ignoresThem")
                .resolve("user-steps.txt"));
        assertTrue(steps.contains("⚠ server ERROR DraftErrorsView: Could not save the draft"), steps);
        if (tool.hasRealBrowser()) {
            assertTrue(steps.contains("Could not load the chart"), steps);
        }
    }
}
