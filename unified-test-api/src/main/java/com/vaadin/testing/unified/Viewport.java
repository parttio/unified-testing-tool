package com.vaadin.testing.unified;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Viewport the test runs at, exactly, on every browser Testing Tool: set when each of its pages opens, before
 * the first navigation (ADR 0011). On a method it wins over one on the class; without either, the test runs at the
 * run's {@code vaadin.testing.viewport}, else 1280 × 720. Implies {@link RequiresBrowser}: skipped on Testing Tools
 * without one. Inherited: on a base class, it applies to the test classes that extend it.
 *
 * <pre>{@code
 * @Viewport(width = 375, height = 667, name = "phone")
 * void theDrawerIsAnOverlay(UnifiedPage page) { ... }
 * }</pre>
 *
 * For a size that changes during the test, use {@code UnifiedPage.setViewport(width, height)}.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@RequiresBrowser
public @interface Viewport {

    /** In CSS pixels. */
    int width();

    /** In CSS pixels. */
    int height();

    /** What the reports call this size, such as {@code "phone"}; empty for none. */
    String name() default "";
}
