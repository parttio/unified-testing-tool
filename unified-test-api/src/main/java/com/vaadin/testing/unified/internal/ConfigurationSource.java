package com.vaadin.testing.unified.internal;

import java.util.Map;
import java.util.Optional;

/**
 * Where the Unified API's settings come from. Each Test Runner integration supplies one: the JUnit
 * integration reads JUnit configuration parameters, which fall back to system properties.
 */
@FunctionalInterface
public interface ConfigurationSource {

    /** The raw value of {@code key}, if set. */
    Optional<String> get(String key);

    /** Reads system properties only. */
    static ConfigurationSource systemProperties() {
        return key -> Optional.ofNullable(System.getProperty(key));
    }

    /** Reads a fixed map, for tests. */
    static ConfigurationSource of(Map<String, String> values) {
        return key -> Optional.ofNullable(values.get(key));
    }
}
