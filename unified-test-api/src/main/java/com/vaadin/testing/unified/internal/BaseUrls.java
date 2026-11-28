package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.BaseUrl;
import com.vaadin.testing.unified.BaseUrlProvider;
import com.vaadin.testing.unified.spi.AdapterContext;
import java.util.Optional;

/** The Base URL of a test (ADR 0021), the same for every browser Testing Tool. */
public final class BaseUrls {
    private BaseUrls() {}

    public static String of(AdapterContext context) {
        String url = context.testClass().flatMap(BaseUrls::annotated)
                .or(() -> context.springContext().flatMap(BaseUrls::springWebServer))
                .or(() -> context.configuration(Settings.BASE_URL))
                .orElse("http://localhost:8080");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** The {@code @BaseUrl} of the test class or, for a {@code @Nested} test, of the nearest enclosing class. */
    private static Optional<String> annotated(Class<?> testClass) {
        BaseUrl baseUrl = null;
        for (Class<?> c = testClass; c != null && baseUrl == null; c = c.getEnclosingClass()) {
            baseUrl = c.getAnnotation(BaseUrl.class);
        }
        if (baseUrl == null) {
            return Optional.empty();
        }
        boolean hasProvider = baseUrl.provider() != BaseUrlProvider.class;
        if (hasProvider == !baseUrl.value().isBlank()) {
            throw new IllegalStateException("@BaseUrl on " + testClass.getName()
                    + " (or a class it extends or is nested in) needs either a URL or a provider, not "
                    + (hasProvider ? "both" : "neither"));
        }
        if (!hasProvider) {
            return Optional.of(baseUrl.value());
        }
        return Optional.of(provider(baseUrl.provider()).baseUrl(testClass));
    }

    /**
     * The URL of the Spring context's web server, when it runs on a port, such as {@code @UnifiedSpringBootTest}'s
     * random port. Read reflectively: the Unified API does not depend on Spring.
     */
    private static Optional<String> springWebServer(Object spring) {
        try {
            ClassLoader loader = spring.getClass().getClassLoader();
            Object env = Class.forName("org.springframework.core.env.EnvironmentCapable", false, loader)
                    .getMethod("getEnvironment").invoke(spring);
            java.lang.reflect.Method property = Class.forName("org.springframework.core.env.PropertyResolver", false,
                    loader).getMethod("getProperty", String.class, String.class);
            String port = (String) property.invoke(env, "local.server.port", null);
            if (port == null) {
                return Optional.empty();
            }
            return Optional.of("http://localhost:" + port + property.invoke(env, "server.servlet.context-path", ""));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read the Spring web server's port", e);
        }
    }

    private static BaseUrlProvider provider(Class<? extends BaseUrlProvider> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("@BaseUrl: cannot create " + type.getName()
                    + ", which needs a no-argument constructor", e);
        }
    }
}
