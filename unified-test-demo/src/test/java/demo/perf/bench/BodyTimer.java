package demo.perf.bench;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * Times a test method's own body: not the extensions' setup, the browser's launch or the reports written after
 * it. Registered through extension auto-detection, which only {@link TimeBenchmark} switches on.
 */
public class BodyTimer implements InvocationInterceptor {

    /** One timed test body: the test class's simple name, the method and its time in ms. */
    public record Timing(String testClass, String method, double millis) {}

    private static final List<Timing> TIMINGS = new ArrayList<>();

    /** The timings since the last call, in the order the tests ran. */
    public static synchronized List<Timing> drain() {
        List<Timing> taken = List.copyOf(TIMINGS);
        TIMINGS.clear();
        return taken;
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        time(invocation, invocationContext);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext, ExtensionContext extensionContext) throws Throwable {
        time(invocation, invocationContext); // TestBench's @BrowserTest
    }

    private static void time(Invocation<Void> invocation, ReflectiveInvocationContext<Method> context) throws Throwable {
        long start = System.nanoTime();
        invocation.proceed();
        double millis = (System.nanoTime() - start) / 1e6;
        synchronized (BodyTimer.class) {
            TIMINGS.add(new Timing(context.getTargetClass().getSimpleName(), context.getExecutable().getName(), millis));
        }
    }
}
