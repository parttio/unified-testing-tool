package com.vaadin.testing.unified.spring;

import java.util.List;
import java.util.Map;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * The settings a {@link UnifiedSpringBootTest}'s application starts with unless something sets them: a property
 * source after all others, so the test's {@code properties}, {@code @TestPropertySource}, the application's
 * configuration files, system properties and environment variables all win over it.
 * <p>
 * {@code vaadin.devmode.devTools.enabled=false}: browser Testing Tools serve the application in development mode,
 * where Vaadin Copilot starts file watchers for every browser session and stops them only when the application
 * stops. A run's sessions add up to the machine's limit of file watchers (about two per test, 128 on a usual
 * Linux), after which every session logs an error. A test needs none of the development tools.
 */
public final class TestApplicationDefaults implements ContextCustomizerFactory {

    static final String NAME = "unified-ui-test-defaults";
    static final Map<String, Object> DEFAULTS = Map.of("vaadin.devmode.devTools.enabled", "false");

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        // on the class, a superclass or, for a @Nested test, an enclosing class
        return TestContextAnnotationUtils.hasAnnotation(testClass, UnifiedSpringBootTest.class) ? new Defaults() : null;
    }

    /** Equal to every other one: the defaults don't make two tests' application contexts differ. */
    private static final class Defaults implements ContextCustomizer {
        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            context.getEnvironment().getPropertySources().addLast(new MapPropertySource(NAME, DEFAULTS));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Defaults;
        }

        @Override
        public int hashCode() {
            return Defaults.class.hashCode();
        }
    }
}
