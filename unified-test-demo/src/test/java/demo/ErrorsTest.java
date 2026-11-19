package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
