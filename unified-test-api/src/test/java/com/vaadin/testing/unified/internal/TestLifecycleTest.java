package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.internal.steps.StepRecorder;
import com.vaadin.testing.unified.spi.AdapterContext;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.TestingToolAdapter;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@link TestLifecycle} the way any Test Runner integration would, with no JUnit context: plain
 * classes and methods, a hand-made {@link AdapterContext} and a fake adapter.
 */
class TestLifecycleTest {

    // ---------------------------------------------------------------- a fake Testing Tool

    /** Records what the lifecycle asks of the session. */
    static final class FakeSession {
        final List<String> calls = new ArrayList<>();
        List<Path> evidence = List.of(Path.of("evidence", "page.png"));
        IOException evidenceFailure;

        AdapterSession proxy() {
            return (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> {
                        calls.add(method.getName() + (args == null ? "" : List.of(args)));
                        return switch (method.getName()) {
                            case "writeFailureEvidence" -> {
                                if (evidenceFailure != null) {
                                    throw evidenceFailure;
                                }
                                yield evidence;
                            }
                            case "unwrap", "queryParameter" -> Optional.empty();
                            case "find" -> List.of();
                            case "isAt" -> false;
                            default -> null;
                        };
                    });
        }
    }

    final FakeSession session = new FakeSession();
    final List<AdapterContext> opened = new ArrayList<>();

    @TempDir
    Path evidenceDir;

    static final TestLifecycle.TestId SAVES = new TestLifecycle.TestId("demo.ProfileTest", "saves", "saves", null);

    private TestLifecycle lifecycle(String tool) {
        Settings settings = Settings.from(ConfigurationSource.of(Map.of(Settings.TOOL, tool,
                Settings.EVIDENCE_DIR, evidenceDir.toString(), Settings.USER_STEPS_LOG, "none")));
        return new TestLifecycle(settings, t -> new TestingToolAdapter() {
            @Override
            public TestingTool tool() {
                return t;
            }

            @Override
            public AdapterSession open(AdapterContext context) {
                opened.add(context);
                return session.proxy();
            }
        });
    }

    /** What a Test Runner without JUnit would hand the adapter. */
    record PlainContext(Class<?> test, Method method) implements AdapterContext {
        @Override public Optional<String> configuration(String key) { return Optional.empty(); }
        @Override public Duration timeout() { return Duration.ofSeconds(1); }
        @Override public Optional<Class<?>> testClass() { return Optional.of(test); }
        @Override public Optional<Method> testMethod() { return Optional.ofNullable(method); }
        @Override public <T extends AutoCloseable> T runScoped(Class<T> key, Supplier<T> create) { return create.get(); }
        @Override public Optional<Object> springContext() { return Optional.empty(); }
    }

    // ---------------------------------------------------------------- sample tests (plain classes, never run)

    @Retention(RetentionPolicy.RUNTIME)
    @RequiresBrowser
    @interface VisualCheck {}

    @RequiresBrowser
    interface BrowserOnly {}

    static class Plain {
        void anyTool() {}

        @RequiresBrowser
        void inBrowser() {}

        @RequiresTestingTool(TestingTool.SELENIUM)
        void onSelenium() {}

        @VisualCheck
        void throughMetaAnnotation() {}
    }

    @RequiresBrowser
    static class BrowserClass {
        void anything() {}
    }

    static class ImplementsBrowserOnly implements BrowserOnly {
        void anything() {}
    }

    static class ExtendsBrowserClass extends BrowserClass {}

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
    }

    // ---------------------------------------------------------------- Capabilities

    @Test
    void decidesFromTheMethodsCapabilities() throws Exception {
        TestLifecycle browserless = lifecycle("browserless");
        assertTrue(browserless.decide(method(Plain.class, "anyTool"), Plain.class).runs());
        assertEquals(new TestLifecycle.Decision(false, "Requires a real browser, this run uses BROWSERLESS"),
                browserless.decide(method(Plain.class, "inBrowser"), Plain.class));
        assertEquals(new TestLifecycle.Decision(false, "Requires [SELENIUM], this run uses BROWSERLESS"),
                browserless.decide(method(Plain.class, "onSelenium"), Plain.class));
        assertFalse(browserless.decide(method(Plain.class, "throughMetaAnnotation"), Plain.class).runs());

        TestLifecycle playwright = lifecycle("playwright");
        assertEquals(new TestLifecycle.Decision(true, "Runs on PLAYWRIGHT"),
                playwright.decide(method(Plain.class, "inBrowser"), Plain.class));
        assertFalse(playwright.decide(method(Plain.class, "onSelenium"), Plain.class).runs());
    }

    @Test
    void decidesFromTheClassesCapabilities() throws Exception {
        TestLifecycle browserless = lifecycle("browserless");
        assertFalse(browserless.decide(BrowserClass.class, BrowserClass.class).runs());
        assertFalse(browserless.decide(method(BrowserClass.class, "anything"), BrowserClass.class).runs());
        assertFalse(browserless.decide(method(ImplementsBrowserOnly.class, "anything"), ImplementsBrowserOnly.class).runs());
        // @RequiresBrowser is not @Inherited, as with JUnit's annotation search
        assertTrue(browserless.decide(ExtendsBrowserClass.class, ExtendsBrowserClass.class).runs());
    }

    // ---------------------------------------------------------------- open and close

    @Test
    void opensATestAndClosesItWithoutEvidenceWhenItPasses() throws Exception {
        PlainContext context = new PlainContext(Plain.class, method(Plain.class, "anyTool"));
        TestLifecycle.OpenTest test = lifecycle("playwright").open(context, new StepRecorder());

        assertSame(context, opened.get(0));
        UnifiedPage page = test.page();
        assertNotNull(page);
        page.navigate("profile");

        assertEquals(List.of(), test.close(null, SAVES));
        assertEquals(List.of("navigate[profile]", "close"), session.calls);
        // a passing test still gets its User Steps report
        assertTrue(Files.exists(evidenceDir.resolve("ProfileTest").resolve("saves").resolve("user-steps.txt")));
        assertTrue(Files.exists(evidenceDir.resolve("ProfileTest").resolve("saves").resolve("user-steps.json")));
    }

    @Test
    void writesTheFailureEvidenceOnAFailedTestAndNamesItOnTheFailure() throws Exception {
        TestLifecycle.OpenTest test = lifecycle("playwright").open(new PlainContext(Plain.class, null), new StepRecorder());
        AssertionError failure = new AssertionError("expected 'Saved'");

        List<Path> evidence = test.close(failure, SAVES);

        assertEquals(List.of(Path.of("evidence", "page.png")), evidence);
        assertEquals(List.of("writeFailureEvidence[" + evidenceDir + ", demo.ProfileTest.saves]", "close"),
                session.calls);
        Throwable[] suppressed = failure.getSuppressed();
        assertEquals(2, suppressed.length);
        assertTrue(suppressed[0].getMessage().startsWith("User Steps ("
                + evidenceDir.resolve("ProfileTest").resolve("saves").toAbsolutePath() + "):\n"), suppressed[0].getMessage());
        assertEquals("Failure evidence: " + Path.of("evidence", "page.png").toAbsolutePath(), suppressed[1].getMessage());
        assertEquals(0, suppressed[1].getStackTrace().length);
    }

    @Test
    void keepsTheTestsOwnFailureWhenTheEvidenceCannotBeWritten() {
        session.evidenceFailure = new IOException("disk full");
        TestLifecycle.OpenTest test = lifecycle("selenium").open(new PlainContext(Plain.class, null), new StepRecorder());
        AssertionError failure = new AssertionError("expected 'Saved'");

        assertEquals(List.of(), test.close(failure, SAVES));

        assertEquals("expected 'Saved'", failure.getMessage());
        assertEquals("Failure evidence could not be written: java.io.IOException: disk full",
                failure.getSuppressed()[1].getMessage());
        assertTrue(session.calls.contains("close"));
    }

    @Test
    void namesTheEvidenceDirectoryOfARepeatedTest() {
        TestLifecycle.TestId third = new TestLifecycle.TestId("demo.ProfileTest$Nested", "saves", "saves", 3);
        assertEquals(evidenceDir.resolve("ProfileTest$Nested").resolve("saves[3]"),
                lifecycle("browserless").testEvidenceDir(third));
    }

    @Test
    void namesTheMissingAdapterDependency() {
        TestLifecycle lifecycle = TestLifecycle.create(Settings.from(ConfigurationSource.of(Map.of(Settings.TOOL, "selenium"))));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> lifecycle.open(new PlainContext(Plain.class, null), new StepRecorder()));
        assertEquals("No adapter for vaadin.testing.tool=selenium. Add the test dependency"
                + " com.vaadin:unified-test-selenium.", e.getMessage());
    }
}
