package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.testing.unified.spi.Filter;
import java.util.List;

/**
 * Component classes that render the same tag, and the attribute that tells each apart (spec 04, "Classes that
 * share a tag"). Browserless finds a component by its class; a browser sees only its element. A lookup for one of
 * these classes, or a subclass with the same tag, also checks the attribute, so that it finds what Browserless
 * finds. A lookup for a supertype ({@code UnifiedLink} is {@code Component.class} with tag {@code "a"}) matches
 * them all. Classes that share a tag and render no such attribute are not listed: their lookups match by tag
 * (appendix-gaps.md).
 */
final class SharedTags {
    private SharedTags() {}

    /** {@code type}'s elements, with {@code tag}, are those that pass {@code filter}. */
    private record Condition(Class<? extends Component> type, String tag, Filter filter) {}

    /** Flow puts a {@code router-link} attribute on a {@code RouterLink}'s {@code <a>}; an {@code Anchor} has none. */
    private static final String ROUTER_LINK = "router-link";

    private static final List<Condition> CONDITIONS = List.of(
            new Condition(RouterLink.class, "a", new Filter(Filter.Kind.ATTRIBUTE, ROUTER_LINK, null, true)),
            new Condition(Anchor.class, "a", new Filter(Filter.Kind.WITHOUT_ATTRIBUTE, ROUTER_LINK, null, true)));

    /**
     * The filters a lookup for {@code componentType}, by {@code tag}, starts with: the condition of the nearest
     * listed class it extends, when it has that class's tag; none otherwise.
     */
    static List<Filter> filters(Class<? extends Component> componentType, String tag) {
        if (componentType == null || tag == null) {
            return List.of();
        }
        for (Class<?> c = componentType; c != null; c = c.getSuperclass()) {
            for (Condition condition : CONDITIONS) {
                if (condition.type() == c) {
                    return condition.tag().equalsIgnoreCase(tag) ? List.of(condition.filter()) : List.of();
                }
            }
        }
        return List.of();
    }
}
