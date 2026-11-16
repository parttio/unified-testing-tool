package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR 0008: a test written for another Test Runner can carry {@code @UnifiedUITest} without JUnit on its
 * classpath. The Unified API's classes are loaded again by a class loader that hides JUnit; the JVM then
 * ignores {@code @UnifiedUITest}'s {@code @ExtendWith}, and the Test Runner-neutral lifecycle reads the test's
 * Capabilities.
 */
class NoJUnitClasspathTest {

    /** Loads the Unified API's classes itself, so that they link against this loader, and hides JUnit. */
    static final class NoJUnitClassLoader extends ClassLoader {
        NoJUnitClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.startsWith("org.junit.") || name.startsWith("org.opentest4j.")) {
                    throw new ClassNotFoundException(name + " (hidden: no JUnit on this classpath)");
                }
                if (!name.startsWith("com.vaadin.testing.unified.")) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = define(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private Class<?> define(String name) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    private final ClassLoader noJUnit = new NoJUnitClassLoader(getClass().getClassLoader());

    @Test
    void hidesJUnit() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.junit.jupiter.api.extension.ExtendWith", false, noJUnit));
        // the JUnit integration cannot even be loaded there
        assertThrows(NoClassDefFoundError.class,
                () -> Class.forName("com.vaadin.testing.unified.internal.junit.UnifiedExtension", true, noJUnit));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsAUnifiedUITestAndReadsItsAnnotations() throws Exception {
        Class<?> sample = Class.forName(NoJUnitSample.class.getName(), true, noJUnit);
        assertEquals(noJUnit, sample.getClassLoader());

        Class<? extends Annotation> unifiedUITest = (Class<? extends Annotation>) noJUnit.loadClass(UnifiedUITest.class.getName());
        assertNotNull(sample.getAnnotation(unifiedUITest));
        // @ExtendWith is silently dropped: its class is missing
        assertTrue(Arrays.stream(unifiedUITest.getAnnotations())
                .noneMatch(a -> a.annotationType().getName().startsWith("org.junit.")));
    }

    @Test
    void decidesFromItsCapabilitiesThroughTheNeutralLifecycle() throws Exception {
        Class<?> sample = Class.forName(NoJUnitSample.class.getName(), true, noJUnit);
        Class<?> page = noJUnit.loadClass(UnifiedPage.class.getName());

        assertEquals("false Requires a real browser, this run uses BROWSERLESS",
                decide("browserless", sample.getDeclaredMethod("checksLayout", page), sample));
        assertEquals("true Runs on BROWSERLESS", decide("browserless", sample.getDeclaredMethod("anyTool", page), sample));
        assertEquals("true Runs on PLAYWRIGHT", decide("playwright", sample.getDeclaredMethod("checksLayout", page), sample));
    }

    /** {@code TestLifecycle.create(Settings.from(ConfigurationSource.of(tool))).decide(...)}, all loaded without JUnit. */
    private String decide(String tool, Method method, Class<?> testClass) throws Exception {
        Class<?> sourceType = noJUnit.loadClass(ConfigurationSource.class.getName());
        Class<?> settingsType = noJUnit.loadClass(Settings.class.getName());
        Class<?> lifecycleType = noJUnit.loadClass(TestLifecycle.class.getName());
        assertEquals(noJUnit, lifecycleType.getClassLoader());

        Object source = sourceType.getMethod("of", Map.class).invoke(null, Map.of(Settings.TOOL, tool));
        Object settings = settingsType.getMethod("from", sourceType).invoke(null, source);
        Object lifecycle = lifecycleType.getMethod("create", settingsType).invoke(null, settings);
        Object decision = lifecycleType.getMethod("decide", AnnotatedElement.class, Class.class)
                .invoke(lifecycle, method, testClass);
        return decision.getClass().getMethod("runs").invoke(decision) + " "
                + decision.getClass().getMethod("reason").invoke(decision);
    }
}
