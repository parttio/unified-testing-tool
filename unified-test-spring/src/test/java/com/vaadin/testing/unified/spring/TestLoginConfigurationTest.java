package com.vaadin.testing.unified.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

/** With Spring Security on the classpath, {@code @UnifiedSpringBootTest} serves the test login endpoint. */
class TestLoginConfigurationTest {

    @Test
    void registersTestLoginFilterWithSpringSecurity() {
        try (var context = new AnnotationConfigApplicationContext(unifiedSpringBootTestImports())) {
            assertEquals(1, context.getBeansOfType(TestLoginFilter.class).size());
        }
    }

    /** The configurations {@code @UnifiedSpringBootTest} imports into every test's application. */
    static Class<?>[] unifiedSpringBootTestImports() {
        return UnifiedSpringBootTest.class.getAnnotation(Import.class).value();
    }
}
