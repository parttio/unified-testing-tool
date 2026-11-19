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
        boolean traces;

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
                            case "writeStepSnapshot" -> List.of(((Path) args[0]).resolve(args[1] + ".png"));
                            case "testEnded" -> traces ? List.of(((Path) args[0]).resolve("trace.zip")) : List.of();
                            case "tracesSteps" -> traces;
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
        return lifecycle(tool, Map.of());
    }

    private TestLifecycle lifecycle(String tool, Map<String, String> more) {
        Map<String, String> all = new java.util.HashMap<>(more);
        all.putAll(Map.of(Settings.TOOL, tool, Settings.EVIDENCE_DIR, evidenceDir.toString(),
                Settings.USER_STEPS_LOG, "none"));
        Settings settings = Settings.from(ConfigurationSource.of(all));
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
        // the console is read after each User Step, and once more when the test ends
        assertEquals(List.of("tracesSteps", "navigate[profile]", "consoleErrors", "consoleErrors",
                "testEnded[" + evidenceDir.resolve("ProfileTest").resolve("saves") + ", false]", "close"), session.calls);
        // a passing test still gets its User Steps report
        assertTrue(Files.exists(evidenceDir.resolve("ProfileTest").resolve("saves").resolve("user-steps.txt")));
        assertTrue(Files.exists(evidenceDir.resolve("ProfileTest").resolve("saves").resolve("user-steps.json")));
    }

    @Test
    void writesTheFailureEvidenceOnAFailedTestAndNamesItOnTheFailure() throws Exception {
        TestLifecycle.OpenTest test = lifecycle("playwright").open(new PlainContext(Plain.class, null), new StepRecorder());
        AssertionError failure = new AssertionError("expected 'Saved'");

        List<Path> evidence = test.close(failure, SAVES);

        Path view = evidenceDir.resolve("demo.ProfileTest.saves.view.txt");
        assertEquals(List.of(Path.of("evidence", "page.png"), view), evidence);
        // the Findable View, on every Testing Tool; this fake page has nothing to find
        assertEquals("Findable view of “/” on playwright: no component a Unified UI Test can find\n",
                Files.readString(view));
        assertEquals(List.of("consoleErrors", "writeFailureEvidence[" + evidenceDir + ", demo.ProfileTest.saves]",
                "testEnded[" + evidenceDir.resolve("ProfileTest").resolve("saves") + ", true]", "close"),
                session.calls.stream().filter(call -> !call.startsWith("find[") && !call.equals("currentPath")
                        && !call.equals("browserName")).toList()); // the Findable View's reads
        Throwable[] suppressed = failure.getSuppressed();
        assertEquals(2, suppressed.length);
        assertTrue(suppressed[0].getMessage().startsWith("User Steps ("
                + evidenceDir.resolve("ProfileTest").resolve("saves").toAbsolutePath() + "):\n"), suppressed[0].getMessage());
        assertEquals("Failure evidence: " + Path.of("evidence", "page.png").toAbsolutePath() + ", "
                + view.toAbsolutePath(), suppressed[1].getMessage());
        assertEquals(0, suppressed[1].getStackTrace().length);
    }

    @Test
    void keepsTheTestsOwnFailureWhenTheEvidenceCannotBeWritten() {
        session.evidenceFailure = new IOException("disk full");
        TestLifecycle.OpenTest test = lifecycle("selenium").open(new PlainContext(Plain.class, null), new StepRecorder());
        AssertionError failure = new AssertionError("expected 'Saved'");

        // the Findable View is written all the same
        assertEquals(List.of(evidenceDir.resolve("demo.ProfileTest.saves.view.txt")), test.close(failure, SAVES));

        assertEquals("expected 'Saved'", failure.getMessage());
        assertEquals("Failure evidence could not be written: java.io.IOException: disk full",
                failure.getSuppressed()[1].getMessage());
        assertTrue(session.calls.contains("close"));
    }

    @Test
    void takesASnapshotAfterEveryUserStepWhenAskedAndListsItInTheJson() throws Exception {
        Path steps = evidenceDir.resolve("ProfileTest").resolve("saves").resolve("steps");
        Files.createDirectories(steps);
        Files.writeString(steps.resolve("009.png"), "an earlier run's");
        TestLifecycle.OpenTest test = lifecycle("playwright", Map.of(Settings.USER_STEPS_SNAPSHOTS, "true"))
                .open(new PlainContext(Plain.class, null), new StepRecorder(), SAVES);

        test.page().navigate("profile");
        test.page().navigate("people");
        test.close(null, SAVES);

        assertTrue(session.calls.contains("writeStepSnapshot[" + steps + ", 001]"), session.calls.toString());
        assertTrue(session.calls.contains("writeStepSnapshot[" + steps + ", 002]"), session.calls.toString());
        assertFalse(Files.exists(steps.resolve("009.png")));
        String json = Files.readString(steps.getParent().resolve("user-steps.json"));
        assertTrue(json.contains("\"snapshot\": \"steps/001.png\""), json);
        assertTrue(json.contains("\"snapshot\": \"steps/002.png\""), json);
    }

    @Test
    void takesNoSnapshotsByDefault() {
        TestLifecycle.OpenTest test = lifecycle("playwright").open(new PlainContext(Plain.class, null),
                new StepRecorder(), SAVES);

        test.page().navigate("profile");
        test.close(null, SAVES);

        assertFalse(session.calls.stream().anyMatch(c -> c.startsWith("writeStepSnapshot")), session.calls.toString());
    }

    @Test
    void groupsATracingSessionsActionsByUserStepAndUserTask() {
        session.traces = true;
        TestLifecycle.OpenTest test = lifecycle("playwright").open(new PlainContext(Plain.class, null),
                new StepRecorder(), SAVES);
        AssertionError failure = new AssertionError("expected 'Saved'");

        test.page().step("Open the profile", () -> test.page().navigate("profile"));
        test.close(failure, SAVES);

        List<String> trace = session.calls.stream().filter(c -> c.startsWith("step")).toList();
        assertEquals(List.of("stepStarted[Open the profile]", "stepStarted[open “profile”]", "stepEnded", "stepEnded"),
                trace);
        // the kept trace is named on the failure
        assertTrue(java.util.Arrays.stream(failure.getSuppressed()).anyMatch(s -> s.getMessage().equals(
                "Recording: " + evidenceDir.resolve("ProfileTest").resolve("saves").resolve("trace.zip")
                        .toAbsolutePath())), java.util.Arrays.toString(failure.getSuppressed()));
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
                + " org.vaadin.addons:unified-test-selenium (its version is in unified-test-bom).", e.getMessage());
    }
}
