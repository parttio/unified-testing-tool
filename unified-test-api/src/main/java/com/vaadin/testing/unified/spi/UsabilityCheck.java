package com.vaadin.testing.unified.spi;

/**
 * Optional for an adapter's elements: Auto-Wait's "visible and enabled" check in one read, for a Testing Tool
 * where {@code isVisible()} and {@code isEnabled()} are a round trip each (#402). An element that doesn't
 * implement it is checked with both. The answer must be the same as {@code isVisible() && isEnabled()}.
 */
public interface UsabilityCheck {

    /** Whether the element is visible and enabled, as {@code isVisible() && isEnabled()} would say. */
    boolean isVisibleAndEnabled();
}
