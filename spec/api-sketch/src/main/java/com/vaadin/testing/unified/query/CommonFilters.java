package com.vaadin.testing.unified.query;

import com.vaadin.testing.unified.UnifiedElement;
import java.util.List;
import java.util.function.Predicate;

/**
 * Filters every Wrapper has, as a mixin: {@code SELF} is the Wrapper type itself, e.g.
 * {@code UnifiedTextField extends CommonFilters<UnifiedTextField>} (ADR 0007). The Unified API implements these
 * methods at run time; a Wrapper author only lists the mixin. Each filter returns a new Wrapper and leaves the one
 * it is called on unchanged. Exact match by default; {@code ...Containing} for partial matches.
 */
public interface CommonFilters<SELF> {
    /** Visible components only. */
    SELF visible();

    /** Recommended for every type: what a screen reader announces. */
    SELF withAccessibleName(String name);
    SELF withAccessibleNameContaining(String part);

    /** Fallbacks for components without a user-visible name. */
    SELF withTestId(String testId);
    SELF withId(String id);

    /** Supported, not recommended: implementation details. */
    SELF withClassName(String className);
    SELF withClassNames(String... classNames);
    SELF withTheme(String theme);
    SELF withAttribute(String name, String value);

    /** Building blocks for custom filters. */
    SELF withProperty(String name, Object value);
    SELF where(Predicate<UnifiedElement> condition);

    /**
     * Every match right now, as lazy Wrappers by position. Only for looping over matches; to check a count, use
     * {@code assertThat(wrapper).hasCount(n)}, which waits.
     */
    List<SELF> all();
}
