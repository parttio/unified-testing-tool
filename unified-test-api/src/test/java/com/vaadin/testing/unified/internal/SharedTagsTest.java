package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.spi.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The class-to-selector mapping for component classes that share a tag (#562). */
class SharedTagsTest {

    /** An application's own Anchor, still an {@code <a>}. */
    static class LogoutLink extends Anchor {}

    /** An Anchor subclass that renders another tag: the Anchor condition is not its. */
    @Tag("my-link")
    static class MyLink extends Anchor {}

    private static String selector(Class<? extends Component> type) {
        String tag = Wrappers.tagOf(type, "");
        return BrowserQueries.selector(tag, SharedTags.filters(type, tag));
    }

    private static List<Object> inPage(Class<? extends Component> type) {
        String tag = Wrappers.tagOf(type, "");
        return BrowserQueries.remaining(SharedTags.filters(type, tag)).stream()
                .map(m -> (Object) (m.get("kind") + " " + m.get("name"))).toList();
    }

    @Test
    void aRouterLinkIsAnAWithTheRouterLinkAttribute() {
        assertEquals(List.of(new Filter(Filter.Kind.ATTRIBUTE, "router-link", null, true)),
                SharedTags.filters(RouterLink.class, "a"));
        assertEquals("a", selector(RouterLink.class));
        assertEquals(List.of("ATTRIBUTE router-link"), inPage(RouterLink.class));
    }

    @Test
    void anAnchorIsAnAWithoutTheRouterLinkAttribute() {
        assertEquals(List.of(new Filter(Filter.Kind.WITHOUT_ATTRIBUTE, "router-link", null, true)),
                SharedTags.filters(Anchor.class, "a"));
        assertEquals(List.of("WITHOUT_ATTRIBUTE router-link"), inPage(Anchor.class));
        assertEquals(SharedTags.filters(Anchor.class, "a"), SharedTags.filters(LogoutLink.class, "a"));
    }

    @Test
    void aClassWithAUniqueTagIsFoundByItsTagAlone() {
        assertEquals(List.of(), SharedTags.filters(Button.class, "vaadin-button"));
        assertEquals("vaadin-button", selector(Button.class));
        assertEquals(List.of(), SharedTags.filters(MyLink.class, "my-link"));
    }

    @Test
    void aSupertypeMatchesEveryClassWithTheTag() {
        // UnifiedLink: @UnifiedComponent(value = Component.class, tag = "a"), RouterLinks and Anchors alike
        assertEquals(List.of(), SharedTags.filters(Component.class, "a"));
    }
}
