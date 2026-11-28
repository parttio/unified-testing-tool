package com.vaadin.testing.unified;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Base URL of the test class's tests on browser Testing Tools (ADR 0021): the running application that relative
 * {@code navigate(..)}, the path checks and {@code isAt(..)} resolve against. It wins over the port of a Spring
 * test's web server and over the run's {@code vaadin.testing.base-url}. Browserless has no URL and ignores it.
 * Inherited, and found on an enclosing class for a {@code @Nested} test.
 *
 * <pre>{@code
 * @UnifiedUITest
 * @BaseUrl("http://localhost:18721/app")
 * class LoginViewIT { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface BaseUrl {

    /** A fixed URL, such as {@code "http://localhost:18721/app"}. */
    String value() default "";

    /** Gives the URL at run time, when the test's session opens; for a port handed out by a pool. */
    Class<? extends BaseUrlProvider> provider() default BaseUrlProvider.class;
}
