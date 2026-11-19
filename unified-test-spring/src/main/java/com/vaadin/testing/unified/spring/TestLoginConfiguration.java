package com.vaadin.testing.unified.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/** Imported by {@link UnifiedSpringBootTest}: the test login endpoint, when Spring Security is present. */
@Configuration(proxyBeanMethods = false)
@Conditional(TestLoginConfiguration.SpringSecurityPresent.class)
class TestLoginConfiguration {

    @Bean
    TestLoginFilter unifiedTestLoginFilter() {
        return new TestLoginFilter();
    }

    /**
     * Checks by class name only: {@link TestLogin} has Spring Security types in its signatures, so calling it
     * without Spring Security fails with {@code NoClassDefFoundError} before it could answer.
     */
    static final class SpringSecurityPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            ClassLoader classLoader = context.getClassLoader();
            return ClassUtils.isPresent("org.springframework.security.core.context.SecurityContextHolder", classLoader)
                    && ClassUtils.isPresent("jakarta.servlet.Filter", classLoader);
        }
    }
}
