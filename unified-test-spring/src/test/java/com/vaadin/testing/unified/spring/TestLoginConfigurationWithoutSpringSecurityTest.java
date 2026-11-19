package com.vaadin.testing.unified.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.util.ClassUtils;

/**
 * An application without Spring Security still starts under {@code @UnifiedSpringBootTest}, without the test
 * login endpoint (issue 348). Surefire runs this class in its own execution, with spring-security-core taken
 * off the classpath (see this module's pom).
 */
class TestLoginConfigurationWithoutSpringSecurityTest {

    @Test
    void startsWithoutTestLoginFilterWithoutSpringSecurity() {
        assertFalse(ClassUtils.isPresent("org.springframework.security.core.Authentication", null),
                "this test must run without Spring Security on the classpath");
        try (var context = new AnnotationConfigApplicationContext(
                TestLoginConfigurationTest.unifiedSpringBootTestImports())) {
            assertEquals(0, context.getBeanNamesForType(TestLoginFilter.class, true, false).length);
        }
    }
}
