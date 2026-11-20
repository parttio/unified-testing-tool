package com.vaadin.testing.unified.spi;

/**
 * Where a screenshot shows its Step Target: a box in fractions of the image (0 to 1, clipped to it), or why it
 * shows none. The report draws it; the picture and the page are never changed (ADR 0010).
 *
 * @param x the box's left edge, a fraction of the image's width
 * @param y the box's top edge, a fraction of the image's height
 * @param width the box's width, a fraction of the image's width
 * @param height the box's height, a fraction of the image's height
 * @param missing null for a box; {@code gone} or {@code outside} when there is none
 */
public record Highlight(double x, double y, double width, double height, String missing) {

    public static Highlight box(double x, double y, double width, double height) {
        return new Highlight(x, y, width, height, null);
    }
}
