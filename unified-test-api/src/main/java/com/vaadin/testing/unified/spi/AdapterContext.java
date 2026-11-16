package com.vaadin.testing.unified.spi;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What an adapter gets when a test starts. Supplied by the Test Runner integration, and tied to none
 * (ADR 0008): the build fails on a JUnit import in this package or in an adapter.
 */
public interface AdapterContext {

    /** A setting of the Unified API, from the Test Runner's configuration (JUnit: configuration parameters or system properties). */
    Optional<String> configuration(String key);

    Duration timeout();

    /** The running test's class; for a {@code @Nested} test, the nested class. Empty for a Test Runner without test classes. */
    Optional<Class<?>> testClass();

    /** The running test's method. Empty for a Test Runner without test methods, such as a Cucumber scenario. */
    Optional<Method> testMethod();

    /**
     * One instance per test run and key, such as a browser shared by all tests: created by {@code create} on
     * first use, the same instance for every later test of the run, and closed when the run ends.
     */
    <T extends AutoCloseable> T runScoped(Class<T> key, Supplier<T> create);

    /**
     * The Spring {@code ApplicationContext} of the running test, when Spring's test support is on the
     * classpath and the test (or, for a {@code @Nested} test, an enclosing class) is a Spring test. Typed
     * {@code Object} so that the Unified API does not depend on Spring.
     */
    Optional<Object> springContext();
}
