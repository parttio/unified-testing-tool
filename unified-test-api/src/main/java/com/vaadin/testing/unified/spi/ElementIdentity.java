package com.vaadin.testing.unified.spi;

import com.vaadin.testing.unified.UnifiedElement;

/**
 * Optional for an adapter's elements: whether two elements found by different lookups are the same component. The
 * Findable View uses it to print a lookup through a container only when the container's one match is the component
 * listed (#658). An element that doesn't implement it is never taken for another, so no such lookup is printed.
 */
public interface ElementIdentity {

    /** Whether {@code other}, an element of the same Testing Tool, is the same component as this one. */
    boolean isSameElement(UnifiedElement other);
}
