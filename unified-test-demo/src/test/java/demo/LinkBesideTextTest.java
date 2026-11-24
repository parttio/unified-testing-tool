package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedLink;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A lookup by tag on a page that also holds plain text, a Flow {@code Text} component (#603): the text is never a
 * match, and never breaks the lookup.
 */
@UnifiedSpringBootTest
class LinkBesideTextTest {

    @Test
    @DisplayName("The user follows the Users link beside the welcome text")
    void followsALinkBesideText(UnifiedPage page) {
        page.navigate(WelcomeView.class);

        page.find(UnifiedLink.class).withText("Users").click();

        assertThat(page).isAt(UsersView.class);
    }

    @Test
    @DisplayName("The page has one link, found without waiting")
    void countsTheLinkBesideText(UnifiedPage page) {
        page.navigate(WelcomeView.class);

        assertEquals(1, page.find(UnifiedLink.class).all().size());
        assertThat(page.find(UnifiedLink.class).withTimeout(Duration.ofMillis(500))).hasCount(1);
    }

    @Test
    @DisplayName("The Findable View lists the Users link")
    void listsTheLinkBesideText(UnifiedPage page) {
        page.navigate(WelcomeView.class);

        assertTrue(page.describe().lines().anyMatch(l -> l.startsWith("link “Users”")), page.describe());
    }

    @Test
    @DisplayName("A Wrapper of any span finds the span, and not the text beside it")
    void findsASpanByTagBesideText(UnifiedPage page) {
        page.navigate(WelcomeView.class);

        List<UnifiedSpanTag> spans = page.find(UnifiedContainer.class).withId("welcome").find(UnifiedSpanTag.class).all();

        assertEquals(1, spans.size());
        assertEquals("New", spans.get(0).element().getText());
    }
}
