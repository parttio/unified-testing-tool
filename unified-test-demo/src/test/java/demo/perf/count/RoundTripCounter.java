package demo.perf.count;

import com.vaadin.browserless.internal.TestingLifecycleHook;
import com.vaadin.browserless.internal.TestingLifecycleHookKt;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Counts the round trips each user action of a Performance Pair makes (#341, method from #300), without
 * changing the tests: a user action is the test's source line that made the call.
 *
 * <p>Off unless {@value #OUTPUT_PROPERTY} names a directory; {@code scripts/bench-unified.sh --counts} sets it.
 * At the end of the run it writes {@code <tool>.txt} there, one line per user action and counter, which the
 * script compares with the committed round-trip counts. What is counted, per Testing Tool:
 *
 * <ul>
 * <li>Playwright: {@code calls}, every Java-to-driver call, from Playwright's channel log ({@code DEBUG=pw:channel},
 *     which the script sets); calls repeated until something is ready ({@code waitForSelector} and the
 *     {@code dispose} of its handle, reads of a lazy drop-down's {@code loading} state) are counted apart as
 *     {@code waitPolls}, because their number depends on timing;
 * <li>Selenium: {@code commands}, every WebDriver command, from {@code RemoteWebDriver}'s command log; TestBench's
 *     {@code waitForVaadin} polls and reads of a lazy drop-down's {@code loading} state are counted apart as
 *     {@code waitPolls}, because their number depends on timing;
 * <li>Browserless: {@code lookups}, {@code walks} (component-tree walks from the UI) and {@code roundTrips}
 *     (simulated server round trips), from Browserless Test's lifecycle hook and its round-trip methods.
 * </ul>
 *
 * <p>Only tests in {@code demo.perf} are counted. Calls made outside the test method (browser launch, the
 * {@code @BeforeEach} and {@code @AfterEach} methods, setup in base classes) are left out.
 */
public class RoundTripCounter implements TestExecutionListener {

    public static final String OUTPUT_PROPERTY = "vaadin.testing.perf.round-trips";
    private static final String PACKAGE = "demo.perf.";

    /** The running test's class name, or null between tests. */
    private static volatile String testClass;
    /** The running test's method name, or null between tests. */
    private static volatile String testMethod;
    /** "UnifiedPerfTest.smallForm:31" -> counter -> count, for the whole run. */
    private static final Map<String, Map<String, Integer>> COUNTS = new TreeMap<>();

    private Path output;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        String dir = System.getProperty(OUTPUT_PROPERTY, "");
        if (dir.isBlank()) {
            return;
        }
        output = Path.of(dir);
        installBrowserless();
        installSelenium();
        installPlaywright();
    }

    @Override
    public void executionStarted(TestIdentifier test) {
        if (test.getSource().orElse(null) instanceof MethodSource method
                && method.getClassName().startsWith(PACKAGE)) {
            testMethod = method.getMethodName();
            testClass = method.getClassName();
        }
    }

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (test.getSource().orElse(null) instanceof MethodSource) {
            testClass = null;
            testMethod = null;
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (output == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        synchronized (COUNTS) {
            COUNTS.forEach((action, counters) -> counters.forEach(
                    (counter, count) -> lines.add(action + " " + counter + " " + count)));
        }
        try {
            Files.createDirectories(output);
            Files.write(output.resolve(System.getProperty("vaadin.testing.tool", "browserless") + ".txt"), lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Adds one to {@code counter} for the test line on the current stack. Public: Byte Buddy advice inlines calls to it. */
    public static void count(String counter) {
        String current = testClass;
        String method = testMethod;
        if (current == null || method == null) {
            return;
        }
        // the test class's innermost line, as long as the test method is on the stack: a helper it calls is
        // credited by its own line, while @BeforeEach and @AfterEach (a base class's setup too) are left out
        String action = StackWalker.getInstance().walk(frames -> {
            List<StackWalker.StackFrame> inClass = frames
                    .filter(f -> f.getClassName().equals(current) || f.getClassName().startsWith(current + "$"))
                    .toList();
            boolean inTest = inClass.stream().anyMatch(f -> f.getClassName().equals(current)
                    && (f.getMethodName().equals(method) || f.getMethodName().startsWith("lambda$" + method + "$")));
            return inTest ? current.substring(current.lastIndexOf('.') + 1) + "." + inClass.get(0).getMethodName()
                    + ":" + inClass.get(0).getLineNumber() : null;
        });
        if (action == null) {
            return;
        }
        synchronized (COUNTS) {
            COUNTS.computeIfAbsent(action, a -> new TreeMap<>()).merge(counter, 1, Integer::sum);
        }
    }

    // --- Browserless Test: the lifecycle hook for lookups and walks, bytecode advice for round trips ---

    private static void installBrowserless() {
        TestingLifecycleHook standard = TestingLifecycleHook.Companion.getDefault();
        TestingLifecycleHookKt.setTestingLifecycleHook(new TestingLifecycleHook() {
            @Override
            public void awaitBeforeLookup() {
                count("lookups");
                standard.awaitBeforeLookup();
            }

            @Override
            public void awaitAfterLookup() {
                standard.awaitAfterLookup();
            }

            @Override
            public List<Component> getAllChildren(Component component) {
                if (component instanceof UI) {
                    count("walks");
                }
                return standard.getAllChildren(component);
            }
        });
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(named("com.vaadin.browserless.BrowserlessDSL")
                        .or(named("com.vaadin.browserless.internal.MockVaadin")))
                .transform((builder, type, loader, module, domain) -> builder.visit(
                        Advice.to(RoundTripAdvice.class).on(named("roundTrip").or(named("clientRoundtrip")))))
                .installOn(ByteBuddyAgent.install());
    }

    /** Inlined at the start of Browserless Test's two round-trip methods. */
    public static class RoundTripAdvice {
        @Advice.OnMethodEnter
        public static void enter() {
            RoundTripCounter.count("roundTrips");
        }
    }

    // --- Selenium: RemoteWebDriver logs every W3C command at FINE ---

    /** Kept, so the logger and its level are not garbage-collected. */
    private static Logger seleniumLog;

    private static void installSelenium() {
        seleniumLog = Logger.getLogger("org.openqa.selenium.remote.RemoteWebDriver");
        seleniumLog.setLevel(Level.FINE);
        seleniumLog.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                Object[] params = record.getParameters();
                if (!"Executing: {0} {1}".equals(record.getMessage()) || params == null || params.length == 0) {
                    return;
                }
                String command = params.length > 1 ? String.valueOf(params[1]) : "";
                boolean waitPoll = "executeScript".equals(String.valueOf(params[0]))
                        // TestBench's WAIT_FOR_VAADIN_SCRIPT, or a read of a lazy drop-down's loading state
                        && (command.contains("devServerIsNotLoaded") || command.contains(".loading"));
                count(waitPoll ? "waitPolls" : "commands");
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    // --- Playwright: with DEBUG=pw:channel, Connection prints "SEND ►" for every call through System.err ---

    private static final Pattern SEND = Pattern.compile("SEND ► \\{\"id\":\\d+");

    private static void installPlaywright() {
        String debug = System.getenv("DEBUG");
        if (debug == null || !debug.contains("pw:channel")) {
            return;
        }
        PrintStream original = System.err;
        System.setErr(new PrintStream(new LineFilter(original), true, StandardCharsets.UTF_8));
    }

    /**
     * Calls repeated until something is ready, so their number depends on timing: waiting for a selector and
     * disposing of the handle it returns, and reading a lazy drop-down's loading state while it fetches a page.
     */
    private static boolean isWait(String line) {
        return line.contains("\"method\":\"waitForSelector\"") || line.contains("\"method\":\"dispose\"")
                || line.contains("el.loading") || line.contains("c.loading");
    }

    /** Counts Playwright's channel lines on the thread that sends them, and keeps them out of the log. */
    private static final class LineFilter extends OutputStream {
        private final PrintStream target;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        LineFilter(PrintStream target) {
            this.target = target;
        }

        @Override
        public synchronized void write(int b) {
            line.write(b);
            if (b == '\n') {
                endLine();
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) {
                write(b[i]);
            }
        }

        private void endLine() {
            String text = line.toString(StandardCharsets.UTF_8);
            line.reset();
            Matcher send = SEND.matcher(text);
            if (send.find()) {
                count(isWait(text) ? "waitPolls" : "calls");
            } else if (!text.contains("◀ RECV ")) {
                target.print(text);
            }
        }

        @Override
        public synchronized void flush() {
            target.flush();
        }
    }
}
