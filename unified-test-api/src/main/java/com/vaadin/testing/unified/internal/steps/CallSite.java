package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.UnifiedWrapper;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * Where a User Step or step group was called from: the first stack frame outside the Unified API, its adapters,
 * Wrapper interfaces (add-on Wrappers included) and the JDK. That is the line in the test, its User Task or its
 * page object.
 *
 * @param className the class of that frame, e.g. {@code demo.EditPersonTest}
 * @param method the method, with a lambda's synthetic name reduced to the method that declares the lambda
 * @param file the source file name, or null when the class has no debug information
 * @param line the line number, or -1 when unknown
 */
public record CallSite(String className, String method, String file, int line) {

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** The caller of the Unified API on the current thread, or null if every frame belongs to the API. */
    public static CallSite here() {
        return WALKER.walk(frames -> frames
                .filter(f -> !skipped(f.getDeclaringClass()))
                .findFirst()
                .map(f -> new CallSite(f.getClassName(), methodName(f.getMethodName()), f.getFileName(),
                        f.getLineNumber()))
                .orElse(null));
    }

    /** The first frame of the throwable's stack trace outside the Unified API, if any. */
    public static Optional<CallSite> of(Throwable throwable) {
        for (StackTraceElement e : throwable.getStackTrace()) {
            if (!skipped(e.getClassName(), null)) {
                return Optional.of(new CallSite(e.getClassName(), methodName(e.getMethodName()), e.getFileName(),
                        e.getLineNumber()));
            }
        }
        return Optional.empty();
    }

    /** {@code EditPersonTest.java:24 (editPerson)}. */
    @Override
    public String toString() {
        String where = file == null ? simpleName(className) : file;
        return where + (line < 0 ? "" : ":" + line) + " (" + method + ")";
    }

    private static boolean skipped(Class<?> type) {
        if (Proxy.isProxyClass(type)) {
            return true;
        }
        // a Wrapper's default methods (Core Set, add-on, @ToolImplementation) are the API, not the test
        if (type.isInterface() && UnifiedWrapper.class.isAssignableFrom(type)) {
            return true;
        }
        return skipped(type.getName(), type);
    }

    static boolean skipped(String className, Class<?> type) {
        if (className.startsWith("java.") || className.startsWith("jdk.") || className.startsWith("sun.")
                || className.startsWith("com.sun.") || className.startsWith("javax.")) {
            return true;
        }
        if (className.startsWith("com.vaadin.testing.unified.")) {
            // the API's and the adapters' own tests are callers too
            String outer = simpleName(className);
            int dollar = outer.indexOf('$');
            if (dollar >= 0) {
                outer = outer.substring(0, dollar);
            }
            return !(outer.endsWith("Test") || outer.endsWith("Tests"));
        }
        return false;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /** {@code lambda$editPerson$0} → {@code editPerson}. */
    private static String methodName(String name) {
        if (name.startsWith("lambda$")) {
            String rest = name.substring("lambda$".length());
            int end = rest.indexOf('$');
            return end > 0 ? rest.substring(0, end) : rest;
        }
        return name;
    }
}
