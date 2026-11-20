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

    public static final String GONE = "gone";
    public static final String OUTSIDE = "outside";

    public static Highlight box(double x, double y, double width, double height) {
        return new Highlight(x, y, width, height, null);
    }

    /** No box: the Step Target is no longer on the page, or not shown. */
    public static Highlight gone() {
        return new Highlight(0, 0, 0, 0, GONE);
    }

    /**
     * An element's box, in pixels of the image it is drawn on (the viewport, or the whole document for a
     * full-page screenshot), as fractions of that image, clipped to it; {@code outside} when none of it is in it.
     */
    public static Highlight of(double left, double top, double width, double height, double imageWidth,
            double imageHeight) {
        double l = Math.max(0, left);
        double t = Math.max(0, top);
        double r = Math.min(imageWidth, left + width);
        double b = Math.min(imageHeight, top + height);
        if (r <= l || b <= t) {
            return new Highlight(0, 0, 0, 0, OUTSIDE);
        }
        return box(l / imageWidth, t / imageHeight, (r - l) / imageWidth, (b - t) / imageHeight);
    }
}
