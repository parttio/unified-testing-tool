package com.vaadin.testing.unified.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A Step Target's box, measured in pixels, as fractions of the screenshot it is drawn on. */
class HighlightTest {

    @Test
    void aBoxInsideTheImageIsItsFractions() {
        assertEquals(Highlight.box(0.1, 0.25, 0.3, 0.05), Highlight.of(100, 150, 300, 30, 1000, 600));
    }

    @Test
    void aBoxPartlyOutsideIsClippedToTheImage() {
        // 50 px above the top and 100 px past the right edge
        assertEquals(Highlight.box(0.9, 0, 0.1, 0.05), Highlight.of(900, -50, 200, 80, 1000, 600));
    }

    @Test
    void aBoxWhollyOutsideTheImageIsOutside() {
        assertEquals("outside", Highlight.of(100, 700, 300, 30, 1000, 600).missing(), "below the viewport");
        assertEquals("outside", Highlight.of(-400, 10, 300, 30, 1000, 600).missing(), "left of it");
    }

    @Test
    void aBoxThatOnlyTouchesTheEdgeIsOutside() {
        assertEquals("outside", Highlight.of(100, 600, 300, 30, 1000, 600).missing());
    }
}
