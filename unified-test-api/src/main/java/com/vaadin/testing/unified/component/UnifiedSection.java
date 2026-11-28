package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Section;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;
import java.time.Duration;

/**
 * A {@code <section>}: a {@link Section}, an {@code Html} whose root is a {@code <section>}, or any other {@code <section>}, to read
 * its text, search inside it or click it. Bound to its tag, so a browser finds it with one query, unlike
 * {@link UnifiedContainer} (any tag).
 */
@UnifiedComponent(value = Component.class, tag = "section")
public interface UnifiedSection extends UnifiedWrapper, Scope,
        CommonFilters<UnifiedSection>, TextFilter<UnifiedSection> {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedSection withTimeout(Duration timeout);

    /** Clicks it as a user does, for a click listener ({@code addClickListener}); Auto-Wait. */
    default void click() { element().click(); }

    /** {@link com.vaadin.testing.unified.UnifiedElement#getText()}: the DOM text, the same on every tool. */
    default String getText() { return element().getText(); }
}
