package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.spi.AdapterContext;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.ExtensionContext;

/** The JUnit integration's {@link AdapterContext}, for one test. */
final class JUnitAdapterContext implements AdapterContext {

    /** Run-scoped instances live in the root context's store, which JUnit closes at the end of the run. */
    private static final ExtensionContext.Namespace RUN = ExtensionContext.Namespace.create(JUnitAdapterContext.class, "run");

    private final ExtensionContext context;
    private final Settings settings;
    private final Duration timeout;

    JUnitAdapterContext(ExtensionContext context, Settings settings) {
        this.context = context;
        this.settings = settings;
        this.timeout = settings.timeout();
    }

    @Override
    public Optional<String> configuration(String key) {
        return settings.get(key);
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public Optional<Class<?>> testClass() {
        return context.getTestClass();
    }

    @Override
    public Optional<Method> testMethod() {
        return context.getTestMethod();
    }

    @Override
    public <T extends AutoCloseable> T runScoped(Class<T> key, Supplier<T> create) {
        return context.getRoot().getStore(RUN).computeIfAbsent(key, k -> create.get(), key);
    }

    @Override
    public Optional<Object> springContext() {
        return SpringContexts.isAvailable() ? SpringContexts.of(context) : Optional.empty();
    }
}
