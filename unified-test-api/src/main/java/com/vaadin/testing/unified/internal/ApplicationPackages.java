package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.spi.AdapterContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The application's own packages, read from the test, the same on every Testing Tool (ADR 0017): the test
 * class's package, those of the {@code @SpringBootApplication} in a Spring test, and the packages set as
 * {@code vaadin.testing.browserless.view-packages}. The packages of the routes the test opens are added as it
 * opens them ({@link com.vaadin.testing.unified.internal.steps.StepRecorder#routeUsed}).
 */
final class ApplicationPackages {
    private ApplicationPackages() {}

    static final String VIEW_PACKAGES = "vaadin.testing.browserless.view-packages";

    static Set<String> of(AdapterContext context) {
        Set<String> packages = new LinkedHashSet<>();
        context.testClass().ifPresent(type -> packages.add(type.getPackageName()));
        context.springContext().ifPresent(spring -> packages.addAll(autoConfigurationPackages(spring)));
        context.configuration(VIEW_PACKAGES).ifPresent(value -> Arrays.stream(value.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).forEach(packages::add));
        packages.remove("");
        return packages;
    }

    /** Spring Boot's auto-configuration packages, those of the {@code @SpringBootApplication}; none without Boot. */
    @SuppressWarnings("unchecked")
    private static List<String> autoConfigurationPackages(Object spring) {
        try {
            Object beanFactory = spring.getClass().getMethod("getAutowireCapableBeanFactory").invoke(spring);
            Class<?> factoryType = Class.forName("org.springframework.beans.factory.BeanFactory", false,
                    spring.getClass().getClassLoader());
            Class<?> packages = Class.forName("org.springframework.boot.autoconfigure.AutoConfigurationPackages",
                    false, spring.getClass().getClassLoader());
            Method has = packages.getMethod("has", factoryType);
            if (!(Boolean) has.invoke(null, beanFactory)) {
                return List.of();
            }
            return (List<String>) packages.getMethod("get", factoryType).invoke(null, beanFactory);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return List.of(); // not a Spring Boot application
        }
    }
}
