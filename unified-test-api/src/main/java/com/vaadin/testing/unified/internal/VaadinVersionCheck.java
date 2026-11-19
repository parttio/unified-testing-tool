package com.vaadin.testing.unified.internal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Vaadin version the Unified API needs, checked once per JVM before the first test. Below it, Browserless Test
 * and the Unified API's own calls into Flow fail in ways that don't name the cause, such as a
 * {@code NoSuchMethodError}, so the test fails first with a message that does (#387). Flow's version is Vaadin's:
 * Vaadin 25.3 runs on Flow 25.3. Without Flow on the test's classpath (a browser test of an application running
 * elsewhere) there is nothing to check.
 */
final class VaadinVersionCheck {

    /** The oldest Vaadin the Unified API runs on; keep in step with the Migration Recipes' {@code CheckVaadinVersion}. */
    static final int MIN_MAJOR = 25;
    static final int MIN_MINOR = 3;

    private static final Pattern MAJOR_MINOR = Pattern.compile("^(\\d+)\\.(\\d+)");
    private static final Optional<String> PROBLEM = problem(flowVersion());

    private VaadinVersionCheck() {
    }

    /** Throws when this JVM's Flow is older than the Unified API needs. */
    static void check() {
        PROBLEM.ifPresent(message -> {
            throw new IllegalStateException(message);
        });
    }

    /** What is wrong with {@code flowVersion}, or empty when it is new enough or unknown. */
    static Optional<String> problem(String flowVersion) {
        if (flowVersion == null) {
            return Optional.empty();
        }
        Matcher m = MAJOR_MINOR.matcher(flowVersion);
        if (!m.find()) {
            return Optional.empty();
        }
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        if (major > MIN_MAJOR || major == MIN_MAJOR && minor >= MIN_MINOR) {
            return Optional.empty();
        }
        return Optional.of("The Unified API needs Vaadin " + MIN_MAJOR + "." + MIN_MINOR + " or newer, and this test"
                + " runs on Vaadin " + major + "." + minor + " (Flow " + flowVersion + "). Move the application to"
                + " Vaadin " + MIN_MAJOR + "." + MIN_MINOR + ": the Browserless Test and TestBench versions come"
                + " from the project's Vaadin BOM.");
    }

    /** {@code com.vaadin.flow.server.Version.getFullVersion()}, or null without Flow on the classpath. */
    private static String flowVersion() {
        try {
            Class<?> version = Class.forName("com.vaadin.flow.server.Version", true,
                    VaadinVersionCheck.class.getClassLoader());
            return (String) version.getMethod("getFullVersion").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
