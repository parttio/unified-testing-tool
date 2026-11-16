package com.vaadin.testing.unified.internal;

import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Spring-only code of the JUnit integration. {@link #isAvailable()} guards {@link #of}: it runs only when
 * Spring's JUnit test support is on the classpath.
 */
final class SpringContexts {
    private SpringContexts() {}

    static boolean isAvailable() {
        try {
            Class.forName("org.springframework.test.context.junit.jupiter.SpringExtension");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** The test's Spring context, if the test class or, for a {@code @Nested} test, an enclosing one is a Spring test. */
    static Optional<Object> of(ExtensionContext context) {
        Optional<Class<?>> testClass = context.getTestClass();
        if (testClass.isEmpty() || !isSpringTest(testClass.get())) {
            return Optional.empty();
        }
        return Optional.of(SpringExtension.getApplicationContext(context));
    }

    private static boolean isSpringTest(Class<?> testClass) {
        for (Class<?> c = testClass; c != null; c = c.getEnclosingClass()) {
            if (AnnotatedElementUtils.hasAnnotation(c, BootstrapWith.class)
                    || AnnotatedElementUtils.hasAnnotation(c, ContextConfiguration.class)) {
                return true;
            }
        }
        return false;
    }
}
