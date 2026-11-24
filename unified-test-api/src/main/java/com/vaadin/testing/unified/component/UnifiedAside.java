package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.html.Aside;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;
import java.time.Duration;

/**
 * A {@code <aside>} ({@link Aside}), to read its text, search inside it or click it. Bound to its tag, so a browser
 * finds it with one query, unlike {@link UnifiedContainer} (any tag).
 */
@UnifiedComponent(Aside.class)
public interface UnifiedAside extends UnifiedWrapper, Scope, CommonFilters<UnifiedAside>, TextFilter<UnifiedAside> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedAside withTimeout(Duration timeout);

    /** Clicks it as a user does, for a click listener ({@code addClickListener}); Auto-Wait. */
    default void click() { element().click(); }

    /** {@link com.vaadin.testing.unified.UnifiedElement#getText()}: the DOM text, the same on every tool. */
    default String getText() { return element().getText(); }
}
