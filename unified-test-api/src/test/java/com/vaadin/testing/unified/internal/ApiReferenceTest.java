package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.testing.unified.HasEscapeHatch;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.assertion.UnifiedAssertions;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.query.CommonFilters;
import java.io.File;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The API reference shipped in the jar ({@code META-INF/unified/api-reference.md}) is generated from the Wrapper
 * interfaces and {@link UnifiedAssertions}, and this test keeps it so: it fails when the file is not what the code
 * says. Run it with {@code -Dunified.api-reference.update=true} to write the file again.
 */
class ApiReferenceTest {

    static final String RESOURCE = "META-INF/unified/api-reference.md";
    private static final Path FILE = Path.of("src/main/resources").resolve(RESOURCE);

    @Test
    void theShippedReferenceIsWhatTheCodeSays() throws Exception {
        String reference = reference();
        if (Boolean.getBoolean("unified.api-reference.update")) {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, reference, StandardCharsets.UTF_8);
        }
        assertTrue(Files.exists(FILE), FILE + " is missing: run this test with -Dunified.api-reference.update=true");
        assertEquals(Files.readString(FILE, StandardCharsets.UTF_8), reference, "The API changed: run "
                + "mvn test -pl unified-test-api -Dtest=ApiReferenceTest -Dunified.api-reference.update=true "
                + "and commit " + FILE);
    }

    @Test
    void theReferenceIsOnTheClasspathOfAProjectThatUsesTheApi() {
        assertTrue(UnifiedWrapper.class.getClassLoader().getResource(RESOURCE) != null);
    }

    // ---------------------------------------------------------------- the reference

    static String reference() throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("""
                # Unified API reference

                Every Wrapper of the Unified API with its filters, actions, reads and checks. Generated from the \
                code; shipped in the `unified-test-api` jar as `META-INF/unified/api-reference.md`.

                - **Find** a component with `page.find(UnifiedX.class)` and filters: \
                `page.find(UnifiedTextField.class).withLabel("Name")`. A Wrapper is lazy: it looks its component up \
                again on every use, so keep it in a variable and reuse it.
                - **Actions** wait until the component is visible and enabled, act, then wait for the server \
                (Auto-Wait). Each is a User Step in the reports.
                - **Reads** return what is there right now, without waiting. Assert with a check instead when you can.
                - **Checks** are `assertThat(wrapper).…` from `UnifiedAssertions`; they retry until the timeout and \
                are User Steps (`see …`).
                - A component without a Wrapper: `page.findComponent(FlowClass.class)`, with the filters of every \
                Wrapper.

                """);
        section(md, "## The page: `UnifiedPage`", "", methods(UnifiedPage.class, Set.of(Object.class)),
                UnifiedPage.class);
        md.append("## Every Wrapper\n\n");
        list(md, "Filters", signatures(methods(CommonFilters.class, Set.of(Object.class)), false));
        list(md, "Methods", signatures(methods(UnifiedWrapper.class, Set.of(Object.class)), true));
        checks(md, UnifiedWrapper.class);
        md.append("\n## Wrappers\n\n");
        for (Class<?> wrapper : wrappers()) {
            UnifiedComponent component = wrapper.getAnnotation(UnifiedComponent.class);
            String title = "### `" + wrapper.getSimpleName() + "`";
            String about = StepTarget.kindOf(wrapper) + (component == null ? "" : ", Flow's `"
                    + component.value().getSimpleName() + "`");
            // a Wrapper that is a Scope lists find(..) and findComponent(..): components are searched inside it
            section(md, title, about, methods(wrapper, Set.of(Object.class, CommonFilters.class, UnifiedWrapper.class,
                    HasEscapeHatch.class)), wrapper);
            for (Class<?> nested : Arrays.stream(wrapper.getClasses()).filter(Class::isInterface)
                    .sorted(Comparator.comparing(Class::getSimpleName)).toList()) {
                section(md, "#### `" + wrapper.getSimpleName() + "." + nested.getSimpleName() + "`", "",
                        methods(nested, Set.of(Object.class)), nested);
            }
        }
        return md.toString();
    }

    /** One type: its filters, actions, reads, other methods and checks. */
    private static void section(StringBuilder md, String title, String about, List<Method> methods, Class<?> type) {
        md.append(title).append("\n\n");
        if (!about.isEmpty()) {
            md.append(Character.toUpperCase(about.charAt(0))).append(about.substring(1)).append(".\n\n");
        }
        Map<String, List<Method>> kinds = new LinkedHashMap<>();
        for (String kind : List.of("Filters", "Actions", "Reads", "Also")) {
            kinds.put(kind, new ArrayList<>());
        }
        for (Method m : methods) {
            kinds.get(kind(m, type)).add(m);
        }
        kinds.forEach((kind, list) -> list(md, kind, signatures(list, !kind.equals("Filters") && !kind.equals("Actions"))));
        checks(md, type);
        md.append('\n');
    }

    private static String kind(Method m, Class<?> type) {
        // a filter returns its own type (a Wrapper, a grid row); the page's methods that return a page open another
        if (m.getDeclaringClass().getPackageName().endsWith(".query")
                || (m.getReturnType() == type && type != UnifiedPage.class)) {
            return "Filters";
        }
        if (m.getReturnType() == void.class) {
            return "Actions";
        }
        String name = m.getName();
        return name.startsWith("get") || name.startsWith("is") || name.startsWith("has") ? "Reads" : "Also";
    }

    private static void list(StringBuilder md, String label, List<String> signatures) {
        if (!signatures.isEmpty()) {
            md.append("- **").append(label).append(":** ").append(String.join(", ", signatures)).append('\n');
        }
    }

    /** The checks of {@code assertThat(type)}: the most specific overload of {@link UnifiedAssertions#assertThat}. */
    private static void checks(StringBuilder md, Class<?> type) {
        Method best = null;
        for (Method m : UnifiedAssertions.class.getMethods()) {
            if (m.getName().equals("assertThat") && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(type)
                    && (best == null || best.getParameterTypes()[0].isAssignableFrom(m.getParameterTypes()[0]))) {
                best = m;
            }
        }
        if (best == null) {
            return;
        }
        List<String> checks = signatures(methods(best.getReturnType(), Set.of(Object.class)), false);
        if (type != UnifiedWrapper.class && UnifiedWrapper.class.isAssignableFrom(type)) {
            // every Wrapper has the checks of "Every Wrapper": say them once, and here what this one adds
            List<String> shared = signatures(methods(sharedChecks(), Set.of(Object.class)), false);
            List<String> own = checks.stream().filter(c -> !shared.contains(c)).toList();
            md.append("- **Checks:** those of every Wrapper").append(own.isEmpty() ? "" : ", and "
                    + String.join(", ", own)).append('\n');
            return;
        }
        list(md, "Checks, `assertThat(" + best.getParameterTypes()[0].getSimpleName() + ")`", checks);
    }

    /** What {@code assertThat(UnifiedWrapper)} returns: the checks every Wrapper has. */
    private static Class<?> sharedChecks() {
        try {
            return UnifiedAssertions.class.getMethod("assertThat", UnifiedWrapper.class).getReturnType();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The type's public instance methods, without those declared by {@code excluded}, by name then parameters. */
    private static List<Method> methods(Class<?> type, Set<Class<?>> excluded) {
        Map<String, Method> bySignature = new TreeMap<>();
        for (Method m : type.getMethods()) {
            if (m.isBridge() || m.isSynthetic() || Modifier.isStatic(m.getModifiers())
                    || excluded.contains(m.getDeclaringClass())) {
                continue;
            }
            // an override with a narrower return type wins over the one it overrides
            bySignature.merge(m.getName() + parameters(m), m, (a, b) -> a.getReturnType()
                    .isAssignableFrom(b.getReturnType()) ? b : a);
        }
        return new ArrayList<>(bySignature.values());
    }

    private static List<String> signatures(List<Method> methods, boolean withReturnType) {
        return methods.stream().map(m -> "`" + m.getName() + parameters(m) + (withReturnType
                && m.getReturnType() != void.class ? " → " + name(m.getGenericReturnType()) : "") + "`").toList();
    }

    private static String parameters(Method m) {
        Type[] types = m.getGenericParameterTypes();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            String name = name(types[i]);
            names.add(m.isVarArgs() && i == types.length - 1 && name.endsWith("[]")
                    ? name.substring(0, name.length() - 2) + "..." : name);
        }
        return "(" + String.join(", ", names) + ")";
    }

    private static String name(Type type) {
        if (type instanceof Class<?> c) {
            return c.isArray() ? name(c.getComponentType()) + "[]" : c.getSimpleName();
        }
        if (type instanceof ParameterizedType p) {
            return name(p.getRawType()) + "<" + Arrays.stream(p.getActualTypeArguments()).map(ApiReferenceTest::name)
                    .collect(Collectors.joining(", ")) + ">";
        }
        if (type instanceof GenericArrayType a) {
            return name(a.getGenericComponentType()) + "[]";
        }
        if (type instanceof WildcardType w) {
            return w.getUpperBounds().length == 1 && w.getUpperBounds()[0] != Object.class
                    ? "? extends " + name(w.getUpperBounds()[0]) : "?";
        }
        if (type instanceof TypeVariable<?> v) {
            return v.getName();
        }
        return type.getTypeName();
    }

    /** The Wrapper interfaces of the API's {@code component} package, by name. */
    private static List<Class<?>> wrappers() throws Exception {
        String pkg = "com.vaadin.testing.unified.component";
        URL dir = UnifiedWrapper.class.getClassLoader().getResource(pkg.replace('.', '/'));
        List<Class<?>> wrappers = new ArrayList<>();
        for (File file : new File(dir.toURI()).listFiles((d, n) -> n.endsWith(".class") && !n.contains("$"))) {
            Class<?> type = Class.forName(pkg + "." + file.getName().replace(".class", ""));
            if (type.isInterface() && UnifiedWrapper.class.isAssignableFrom(type)
                    && type.isAnnotationPresent(UnifiedComponent.class)) {
                wrappers.add(type);
            }
        }
        wrappers.sort(Comparator.comparing(Class::getSimpleName));
        return wrappers;
    }
}
