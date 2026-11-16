package com.vaadin.testing.unified.spi;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * What an adapter gets when a test starts. Supplied by the Test Runner integration; nothing here but
 * {@link #extensionContext()} is tied to a Test Runner (ADR 0008).
 */
public interface AdapterContext {

    /**
     * The JUnit context of the running test.
     *
     * @deprecated ties the adapter to JUnit. Use {@link #testClass()}, {@link #testMethod()},
     *             {@link #runScoped(Class, Supplier)} and {@link #springContext()}; this method is removed once
     *             no adapter calls it.
     */
    @Deprecated(forRemoval = true)
    ExtensionContext extensionContext();

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
