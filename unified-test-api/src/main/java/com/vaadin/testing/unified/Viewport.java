package com.vaadin.testing.unified;

/**
 * The visible area of a Unified Page, in CSS pixels, which the application lays itself out for. Exact on every
 * browser Testing Tool: 1280 × 720 unless {@link com.vaadin.testing.unified.Viewport @Viewport} or the run's
 * {@code vaadin.testing.viewport} says otherwise (ADR 0011).
 *
 * @param name what the reports call this size ({@code "phone"}), or empty
 * @param width in CSS pixels, at least 1
 * @param height in CSS pixels, at least 1
 */
public record Viewport(String name, int width, int height) {

    public Viewport {
        if (name == null) {
            name = "";
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("A Viewport needs a width and height of at least 1, got " + width
                    + " × " + height);
        }
    }

    /** A size without a name. */
    public static Viewport of(int width, int height) {
        return new Viewport("", width, height);
    }

    /** {@code 375 × 667}, or {@code phone 375 × 667} with a name: as the User Steps and reports say it. */
    @Override
    public String toString() {
        return (name.isEmpty() ? "" : name + " ") + width + " × " + height;
    }
}
