package com.vaadin.testing.unified;

/**
 * Gives a test class its Base URL at run time, such as a free copy of the application from a pool (ADR 0021):
 * {@code @BaseUrl(provider = MyPool.class)}. It needs a no-argument constructor, and is called each time a test's
 * session opens on a browser Testing Tool. Its exception fails the test before the test body runs.
 */
@FunctionalInterface
public interface BaseUrlProvider {

    /**
     * @param testClass the running test's class; for a {@code @Nested} test, the nested class
     * @return the application's URL, such as {@code "http://localhost:18721/app"}
     */
    String baseUrl(Class<?> testClass);
}
