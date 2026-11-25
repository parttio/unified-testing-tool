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
        /** The Testing Tool's evidence files; null for a screenshot named as asked, in the directory asked. */
        List<Path> evidence;
        IOException evidenceFailure;
        boolean traces;
        /** What measureLayout returns, as the layout script's JSON; null when the Testing Tool can't measure. */
        String layout;
        /** Why this Testing Tool can't set a Viewport; null when it can. */
        String cannotSetViewport;

        AdapterSession proxy() {
            return (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> {
                        calls.add(method.getName() + (args == null ? "" : java.util.Arrays.asList(args)));
                        return switch (method.getName()) {
                            case "writeFailureEvidence" -> {
                                if (evidenceFailure != null) {
                                    throw evidenceFailure;
                                }
                                yield com.vaadin.testing.unified.spi.Evidence.of(evidence != null ? evidence
                                        : List.of(((Path) args[0]).resolve(args[1] + ".png")));
                            }
                            case "writeStepSnapshot" -> com.vaadin.testing.unified.spi.Evidence.of(
                                    List.of(((Path) args[0]).resolve(args[1] + ".png")));
                            case "testEnded" -> traces ? List.of(((Path) args[0]).resolve("trace.zip")) : List.of();
                            case "tracesSteps" -> traces;
                            case "unwrap", "queryParameter" -> Optional.empty();
                            case "measureLayout" -> {
                                if (layout == null) {
                                    throw new UnsupportedOperationException("this Testing Tool can't measure the layout");
                                }
                                yield layout;
                            }
                            case "setViewport" -> {
                                if (cannotSetViewport != null) {
                                    throw new UnsupportedOperationException(cannotSetViewport);
                                }
                                yield null;
                            }
                            case "find" -> List.of();
                            // another window records into the same list
                            case "openWindow", "openAsUser" -> proxy;
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
        // a base class's Capabilities apply to the test classes that extend it
        assertFalse(browserless.decide(ExtendsBrowserClass.class, ExtendsBrowserClass.class).runs());
        assertFalse(browserless.decide(method(BrowserClass.class, "anything"), ExtendsBrowserClass.class).runs());
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
        assertEquals(List.of("setViewport[1280 × 720]", "tracesSteps", "navigate[profile]", "consoleErrors", "consoleErrors",
                "testEnded[" + evidenceDir.resolve("ProfileTest").resolve("saves") + ", false]", "close"), session.calls);
        // a passing test still gets its User Steps report
        assertTrue(Files.exists(evidenceDir.resolve("ProfileTest").resolve("saves").resolve("user-steps.txt")));
        assertTrue(Files.exists(evidenceDir.resolve("ProfileTest").resolve("saves").resolve("user-steps.json")));
    }

    @Test
    void writesTheFailureEvidenceOnAFailedTestAndNamesItOnTheFailure() throws Exception {
        TestLifecycle.OpenTest test = lifecycle("playwright").open(new PlainContext(Plain.class, null), new StepRecorder());
        AssertionError failure = new AssertionError("expected 'Saved'");

        Path saves = evidenceDir.resolve("ProfileTest").resolve("saves");
        Files.createDirectories(saves);
        Files.writeString(saves.resolve("failure.tree.txt"), "an earlier run's, on Browserless");

        List<Path> evidence = test.close(failure, SAVES);

        // all of it in the test's own evidence directory, next to its User Steps
        Path view = saves.resolve("failure.view.txt");
        assertEquals(List.of(saves.resolve("failure.png"), view), evidence);
        assertFalse(Files.exists(saves.resolve("failure.tree.txt")), "an earlier run's evidence is gone");
        // the Findable View, on every Testing Tool; this fake page has nothing to find
        assertEquals("Findable view of “/” on playwright: no component a Unified UI Test can find\n",
                Files.readString(view));
        assertTrue(Files.readString(saves.resolve("user-steps.json"))
                .contains("\"evidence\": [\n      \"failure.png\",\n      \"failure.view.txt\"\n    ]"),
                Files.readString(saves.resolve("user-steps.json")));
        assertEquals(List.of("setViewport[1280 × 720]", "consoleErrors", "writeFailureEvidence[" + saves + ", failure, null]",
                "testEnded[" + evidenceDir.resolve("ProfileTest").resolve("saves") + ", true]", "close"),
                session.calls.stream().filter(call -> !call.startsWith("find[") && !call.startsWith("findTemplates[")
                        && !call.equals("currentPath")
                        && !call.equals("browserName")).toList()); // the Findable View's reads
        Throwable[] suppressed = failure.getSuppressed();
        assertEquals(2, suppressed.length);
        assertEquals("Failure evidence: " + saves.resolve("failure.png").toAbsolutePath() + ", "
                + view.toAbsolutePath(), suppressed[0].getMessage());
        assertEquals(0, suppressed[0].getStackTrace().length);
        assertTrue(suppressed[1].getMessage().startsWith("User Steps (" + saves.toAbsolutePath() + "):\n"),
                suppressed[1].getMessage());
    }

    @Test
    void aUnifiedFailureNamesItsEvidenceDirectoryAndFiles() {
        TestLifecycle.OpenTest test = lifecycle("playwright").open(new PlainContext(Plain.class, null),
                new StepRecorder(), SAVES);
        com.vaadin.testing.unified.UnifiedAssertionError failure = new com.vaadin.testing.unified.UnifiedAssertionError(
                com.vaadin.testing.unified.internal.failures.FailureParts.of("exactly one button “Save”", "none"),
                null);

        test.close(failure, SAVES);

        String directory = evidenceDir.resolve("ProfileTest").resolve("saves").toString().replace('\\', '/') + "/";
        assertEquals(List.of("failure.png", "failure.view.txt"), failure.getEvidenceFiles());
        assertTrue(failure.getMessage().endsWith("  Evidence      " + directory + "  (failure.png, failure.view.txt)"),
                failure.getMessage());
    }

    @Test
    void theDisplayNameTheAuthorWroteIsNotMaskedButAnInvocationsIs() throws Exception {
        TestLifecycle.TestId authored = new TestLifecycle.TestId("demo.LoginTest", "logout",
                "A signed-in barista logs out", null);
        TestLifecycle.TestId invocation = new TestLifecycle.TestId("demo.LoginTest", "logout", "[1] barista", 1);
        for (TestLifecycle.TestId id : List.of(authored, invocation)) {
            TestLifecycle.OpenTest test = lifecycle("browserless").open(new PlainContext(Plain.class, null),
                    new StepRecorder(), id);
            test.page().secret("barista");
            com.vaadin.testing.unified.UnifiedAssertionError failure = new com.vaadin.testing.unified.UnifiedAssertionError(
                    com.vaadin.testing.unified.internal.failures.FailureParts.of("“barista” signed in", "none"),
                    null);

            test.close(failure, id);

            String shown = id == authored ? "A signed-in barista logs out" : "[1] ••••";
            Path dir = lifecycle("browserless").testEvidenceDir(id);
            assertTrue(Files.readString(dir.resolve("user-steps.txt")).startsWith("✘ LoginTest › " + shown + "   "),
                    Files.readString(dir.resolve("user-steps.txt")));
            assertTrue(Files.readString(dir.resolve("user-steps.json")).contains("\"displayName\": \"" + shown + "\""),
                    Files.readString(dir.resolve("user-steps.json")));
            assertTrue(failure.getMessage().contains(shown), failure.getMessage());
            assertTrue(failure.getMessage().contains("“••••” signed in"), failure.getMessage());
        }
    }

    @Test
    void keepsTheTestsOwnFailureWhenTheEvidenceCannotBeWritten() {
        session.evidenceFailure = new IOException("disk full");
        TestLifecycle.OpenTest test = lifecycle("selenium").open(new PlainContext(Plain.class, null), new StepRecorder());
        AssertionError failure = new AssertionError("expected 'Saved'");

        // the Findable View is written all the same
        assertEquals(List.of(evidenceDir.resolve("ProfileTest").resolve("saves").resolve("failure.view.txt")),
                test.close(failure, SAVES));

        assertEquals("expected 'Saved'", failure.getMessage());
        assertEquals("Failure evidence could not be written: java.io.IOException: disk full",
                failure.getSuppressed()[0].getMessage());
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

        assertTrue(session.calls.contains("writeStepSnapshot[" + steps + ", 001, null]"), session.calls.toString());
        assertTrue(session.calls.contains("writeStepSnapshot[" + steps + ", 002, null]"), session.calls.toString());
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
