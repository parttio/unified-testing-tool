package com.vaadin.testing.unified.internal.junit;

import com.vaadin.testing.unified.BaseUrl;
import com.vaadin.testing.unified.BaseUrlProvider;
import com.vaadin.testing.unified.internal.Settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** What the JUnit integration gives an adapter, checked by running sample tests through the JUnit test kit. */
class JUnitAdapterContextTest {

    /** What an adapter saw in one sample test, keyed by the sample test's method name. */
    record Seen(Optional<Class<?>> testClass, Optional<Method> testMethod, Shared shared, Optional<Object> spring,
            String baseUrl) {}

    static final Map<String, Seen> SEEN = new ConcurrentHashMap<>();

    /** Stands in for an adapter: builds the test's AdapterContext and records what it gives. */
    static final class Probe implements BeforeEachCallback {
        @Override
        public void beforeEach(ExtensionContext context) {
            JUnitAdapterContext adapter = new JUnitAdapterContext(context, Settings.from(context::getConfigurationParameter));
            SEEN.put(context.getRequiredTestMethod().getName(), new Seen(adapter.testClass(), adapter.testMethod(),
                    adapter.runScoped(Shared.class, Shared::new), adapter.springContext(), adapter.baseUrl()));
        }
    }

    /** A run-scoped instance, such as a shared browser. */
    static final class Shared implements AutoCloseable {
        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger CLOSED = new AtomicInteger();

        Shared() {
            CREATED.incrementAndGet();
        }

        @Override
        public void close() {
            CLOSED.incrementAndGet();
        }
    }

    @BeforeEach
    void reset() {
        SEEN.clear();
        Shared.CREATED.set(0);
        Shared.CLOSED.set(0);
    }

    private static void run(Class<?>... classes) {
        runWithBaseUrlSetting("", classes);
    }

    private static void runWithBaseUrlSetting(String baseUrl, Class<?>... classes) {
        EngineTestKit.engine("junit-jupiter").configurationParameter(Settings.BASE_URL, baseUrl)
                .selectors(Arrays.stream(classes).map(c -> selectClass(c)).toArray(DiscoverySelector[]::new))
                .execute().testEvents().assertStatistics(stats -> stats.failed(0).aborted(0));
    }

    @Test
    void givesTheRunningTestClassAndMethod() {
        run(PlainSample.class);
        Seen seen = SEEN.get("first");
        assertEquals(Optional.of(PlainSample.class), seen.testClass());
        assertEquals("first", seen.testMethod().orElseThrow().getName());
    }

    @Test
    void sharesOneRunScopedInstanceAcrossTheRunAndClosesItWhenTheRunEnds() {
        run(PlainSample.class, OtherSample.class);
        assertEquals(3, SEEN.size());
        Shared shared = SEEN.get("first").shared();
        assertSame(shared, SEEN.get("second").shared());
        assertSame(shared, SEEN.get("inAnotherClass").shared());
        assertEquals(1, Shared.CREATED.get());
        assertEquals(1, Shared.CLOSED.get());
    }

    @Test
    void hasNoSpringContextOutsideASpringTest() {
        run(PlainSample.class);
        assertTrue(SEEN.get("first").spring().isEmpty());
    }

    @Test
    void givesTheSpringContextOfASpringTest() {
        run(SpringSample.class);
        Object spring = SEEN.get("inSpringTest").spring().orElseThrow();
        assertEquals("hello", ((ApplicationContext) spring).getBean("greeting"));
    }

    @Test
    void givesTheEnclosingSpringTestsContextToANestedTest() {
        run(SpringSample.class);
        Seen nested = SEEN.get("inNestedTest");
        assertEquals(Optional.of(SpringSample.Inner.class), nested.testClass());
        assertSame(SEEN.get("inSpringTest").spring().orElseThrow(), nested.spring().orElseThrow());
    }

    @Test
    void givesTheFixedBaseUrlOfTheTestClass() {
        run(FixedBaseUrlSample.class);
        assertEquals("http://localhost:18721/app", SEEN.get("onFixedUrl").baseUrl());
    }

    @Test
    void givesTheBaseUrlTheProviderReturnsForTheRunningTestClass() {
        run(ProvidedBaseUrlSample.class);
        assertEquals("http://localhost:18800/ProvidedBaseUrlSample", SEEN.get("onProvidedUrl").baseUrl());
    }

    @Test
    void givesANestedTestTheBaseUrlOfItsEnclosingClassAndCallsTheProviderWithTheNestedClass() {
        run(NestedBaseUrlSample.class);
        assertEquals("http://localhost:18800/Inner", SEEN.get("inNestedClass").baseUrl());
    }

    @Test
    void givesASubclassTheBaseUrlOfItsBaseClass() {
        run(InheritedBaseUrlSample.class);
        assertEquals("http://localhost:18721/app", SEEN.get("inSubclass").baseUrl());
    }

    @Test
    void givesTheRunsBaseUrlSettingToATestWithoutBaseUrlOrSpringPort() {
        runWithBaseUrlSetting("http://localhost:9090/", PlainSample.class);
        assertEquals("http://localhost:9090", SEEN.get("first").baseUrl());
    }

    @Test
    void givesHttpLocalhost8080WithoutAnyBaseUrl() {
        run(PlainSample.class);
        assertEquals("http://localhost:8080", SEEN.get("first").baseUrl());
    }

    @Test
    void givesTheSpringWebServersPortOverTheRunsSetting() {
        runWithBaseUrlSetting("http://localhost:9090", SpringPortSample.class);
        assertEquals("http://localhost:18900/shop", SEEN.get("onSpringPort").baseUrl());
    }

    @Test
    void givesTheTestClassesBaseUrlOverTheSpringPortAndTheRunsSetting() {
        runWithBaseUrlSetting("http://localhost:9090", SpringPortSample.class);
        assertEquals("http://localhost:18721/app", SEEN.get("onAnnotatedSpringTest").baseUrl());
    }

    @Test
    void failsTheTestWhenItsBaseUrlHasBothAUrlAndAProvider() {
        assertInvalidBaseUrl(BothBaseUrlSample.class);
    }

    @Test
    void failsTheTestWhenItsBaseUrlHasNeitherAUrlNorAProvider() {
        assertInvalidBaseUrl(EmptyBaseUrlSample.class);
    }

    private static void assertInvalidBaseUrl(Class<?> sample) {
        EngineTestKit.engine("junit-jupiter").selectors(selectClass(sample)).execute().testEvents()
                .assertThatEvents().haveExactly(1, org.junit.platform.testkit.engine.EventConditions.finishedWithFailure(
                        org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf(
                                IllegalStateException.class),
                        org.junit.platform.testkit.engine.TestExecutionResultConditions.message(m -> m.contains(
                                sample.getName()) && m.contains("either a URL or a provider"))));
    }

    // ---------------------------------------------------------------- samples, run only by the tests above

    @Tag("launched-only")
    @ExtendWith(Probe.class)
    static class PlainSample {
        @Test
        void first() {}

        @Test
        void second() {}
    }

    @Tag("launched-only")
    @ExtendWith(Probe.class)
    static class OtherSample {
        @Test
        void inAnotherClass() {}
    }

    @Tag("launched-only")
    @SpringJUnitConfig(SpringSample.Config.class)
    @ExtendWith(Probe.class)
    static class SpringSample {

        @Configuration
        static class Config {
            @Bean
            String greeting() {
                return "hello";
            }
        }

        @Test
        void inSpringTest() {}

        @Nested
        class Inner {
            @Test
            void inNestedTest() {}
        }
    }

    @Tag("launched-only")
    @ExtendWith(Probe.class)
    @BaseUrl("http://localhost:18721/app/")
    static class FixedBaseUrlSample {
        @Test
        void onFixedUrl() {}
    }

    /** Stands in for a pool of application copies: a URL per test class. */
    public static final class PerClassPool implements BaseUrlProvider {
        @Override
        public String baseUrl(Class<?> testClass) {
            return "http://localhost:18800/" + testClass.getSimpleName();
        }
    }

    @Tag("launched-only")
    @ExtendWith(Probe.class)
    @BaseUrl(provider = PerClassPool.class)
    static class ProvidedBaseUrlSample {
        @Test
        void onProvidedUrl() {}
    }

    @Tag("launched-only")
    @ExtendWith(Probe.class)
    @BaseUrl(provider = PerClassPool.class)
    static class NestedBaseUrlSample {
        @Nested
        class Inner {
            @Test
            void inNestedClass() {}
        }
    }

    @Tag("launched-only")
    static class InheritedBaseUrlSample extends FixedBaseUrlSample {
        @Test
        void inSubclass() {}
    }

    @Tag("launched-only")
    @SpringJUnitConfig(SpringSample.Config.class)
    @TestPropertySource(properties = { "local.server.port=18900", "server.servlet.context-path=/shop" })
    @ExtendWith(Probe.class)
    static class SpringPortSample {
        @Test
        void onSpringPort() {}

        @Nested
        @BaseUrl("http://localhost:18721/app")
        class Annotated {
            @Test
            void onAnnotatedSpringTest() {}
        }
    }

    @Tag("launched-only")
    @ExtendWith(Probe.class)
    @BaseUrl(value = "http://localhost:18721", provider = PerClassPool.class)
    static class BothBaseUrlSample {
        @Test
        void withBoth() {}
    }

    @Tag("launched-only")
    @ExtendWith(Probe.class)
    @BaseUrl
    static class EmptyBaseUrlSample {
        @Test
        void withNeither() {}
    }
}
