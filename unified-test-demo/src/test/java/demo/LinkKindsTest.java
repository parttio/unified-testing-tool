package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.component.UnifiedLink;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RouterLink and Anchor both render {@code <a>}: a lookup by either class finds the same components on every
 * Testing Tool, as Browserless does by class (#562). {@code UnifiedLink} finds both.
 */
@UnifiedSpringBootTest
class LinkKindsTest {

    @Test
    @DisplayName("A RouterLink lookup finds the router links, an Anchor lookup the anchor")
    void countsEachClassApart(UnifiedPage page) {
        page.navigate(LinkKindsView.class);

        assertThat(page.findComponent(RouterLink.class)).hasCount(2);
        assertThat(page.findComponent(Anchor.class)).hasCount(1);
        assertThat(page.find(UnifiedLink.class)).hasCount(3);
    }

    @Test
    @DisplayName("The Logout anchor is not a router link, and the Profile router link not an anchor")
    void existsAsItsOwnClassOnly(UnifiedPage page) {
        page.navigate(LinkKindsView.class);

        assertThat(page.findComponent(Anchor.class).withText("Logout")).exists();
        assertThat(page.findComponent(RouterLink.class).withText("Logout")).doesNotExist();
        assertThat(page.findComponent(RouterLink.class).withText("Profile")).exists();
        assertThat(page.findComponent(Anchor.class).withText("Profile")).doesNotExist();
        assertEquals("logout-tab", page.findComponent(Anchor.class).element().getAttribute("id"));
    }

    @Test
    @DisplayName("A failed RouterLink lookup doesn't offer the Logout anchor as a router link")
    void nearMissesAreOfTheClass(UnifiedPage page) {
        page.navigate(LinkKindsView.class);
        UnifiedGenericComponent logout = page.findComponent(RouterLink.class)
                .withText("Logout").withTimeout(Duration.ofMillis(500));

        AssertionError e = assertThrows(AssertionError.class, () -> logout.element().click());

        assertFalse(e.getMessage().contains("logout-tab"), e.getMessage());
        assertTrue(e.getMessage().contains("Profile"), e.getMessage());
    }
}
