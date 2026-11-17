package com.vaadin.testing.unified.query;

import com.vaadin.testing.unified.UnifiedElement;
import java.util.List;
import java.util.function.Predicate;

/**
 * Filters every Wrapper has, as a mixin: {@code SELF} is the Wrapper type itself, e.g.
 * {@code UnifiedTextField extends CommonFilters<UnifiedTextField>} (ADR 0007). The Unified API implements these
 * methods at run time; a Wrapper author only lists the mixin.
 * <p>
 * Each filter returns a new Wrapper and leaves the one it is called on unchanged. Exact match by default;
 * {@code ...Containing} for partial matches. The Wrapper finds its component again on every use and waits up
 * to the timeout for exactly one match.
 * <p>
 * Lookups include hidden components, on every Testing Tool: a component hidden after it was shown stays in a
 * browser's page. Add {@link #visible()} where that matters, e.g. "the action is gone". A component that was
 * never visible is in no browser's page (Flow doesn't send it), but Browserless finds it; {@code visible()}
 * gives the same answer everywhere.
 */
public interface CommonFilters<SELF> {
    /** Visible components only: {@link com.vaadin.testing.unified.UnifiedElement#isVisible()}. */
    SELF visible();

    /** Recommended for every type: what a screen reader announces. */
    SELF withAccessibleName(String name);
    SELF withAccessibleNameContaining(String part);

    /** Fallbacks for components without a user-visible name. */
    SELF withTestId(String testId);
    SELF withId(String id);

    /**
     * Supported, not recommended: implementation details. One class name, as Flow's
     * {@code addClassName(..)}: a name with whitespace throws {@link IllegalArgumentException} on every Testing
     * Tool. For several, use {@link #withClassNames(String...)}.
     */
    SELF withClassName(String className);

    /**
     * Components with every one of these class names, as Flow's {@code addClassNames(..)}: each argument may
     * hold several names separated by whitespace. The same as chaining {@link #withClassName(String)}.
     */
    SELF withClassNames(String... classNames);
    SELF withTheme(String theme);
    SELF withAttribute(String name, String value);

    /** Building blocks for custom filters. */
    SELF withProperty(String name, Object value);
    SELF where(Predicate<UnifiedElement> condition);

    /**
     * Every match right now, as lazy Wrappers by position: the {@code i}-th Wrapper is the {@code i}-th match
     * whenever it is used. Only for looping over matches; to check a count, use
     * {@code assertThat(wrapper).hasCount(n)}, which waits.
     */
    List<SELF> all();
}
