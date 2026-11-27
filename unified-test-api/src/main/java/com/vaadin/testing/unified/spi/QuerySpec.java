package com.vaadin.testing.unified.spi;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedElement;
import java.util.List;

/**
 * What to find. {@code parent} is the adapter's own element to search inside, or null for the whole page.
 * Property and predicate filters are applied by the core afterwards, on {@link UnifiedElement}.
 *
 * <p>{@code inTemplate}: {@code parent} is a Template the test entered (spec 04, "Templates"). Search its shadow
 * root, keeping in a browser only the elements with an {@code id} there and what is under them (on Browserless:
 * its virtual children, the {@code @Id} components, and what is under them), then its light DOM. Never the shadow
 * root of a Template nested in it. Without it, a lookup never enters a shadow root.
 */
public record QuerySpec(Class<? extends Component> componentType, String tag, List<Filter> filters,
        UnifiedElement parent, boolean inTemplate) {

    /** A lookup in the page, or in a parent that is not a Template. */
    public QuerySpec(Class<? extends Component> componentType, String tag, List<Filter> filters,
            UnifiedElement parent) {
        this(componentType, tag, filters, parent, false);
    }

    public QuerySpec {
        if (inTemplate && parent == null) {
            throw new IllegalArgumentException("A lookup inside a Template needs the Template as its parent");
        }
    }
}
