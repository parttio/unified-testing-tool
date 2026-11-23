package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Map.entry;

import com.vaadin.testing.unified.HasEscapeHatch;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.assertion.UnifiedAssertions;
import com.vaadin.testing.unified.internal.steps.Phrases;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.query.CommonFilters;
import java.io.File;
import java.lang.reflect.Array;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /** The documentation site, where the guide pages the reference links to are. */
    private static final String SITE = "https://parttio.github.io/unified-testing-tool/";

    /** Where the developer guide shows each Wrapper: a page of docs/guide, and its section. */
    private static final Map<String, String> GUIDE = Map.ofEntries(
            entry("UnifiedPage", "navigation-and-users.md"),
            entry("UnifiedTextField", "components.md#text-fields"),
            entry("UnifiedTextArea", "components.md#text-fields"),
            entry("UnifiedNumberField", "components.md#typed-values"),
            entry("UnifiedIntegerField", "components.md#typed-values"),
            entry("UnifiedBigDecimalField", "components.md#typed-values"),
            entry("UnifiedDatePicker", "components.md#typed-values"),
            entry("UnifiedDateTimePicker", "components.md#typed-values"),
            entry("UnifiedCheckbox", "components.md#checkbox"),
            entry("UnifiedComboBox", "components.md#selection-combobox-radiobuttongroup-multiselectcombobox"),
            entry("UnifiedRadioButtonGroup", "components.md#selection-combobox-radiobuttongroup-multiselectcombobox"),
            entry("UnifiedMultiSelectComboBox",
                    "components.md#selection-combobox-radiobuttongroup-multiselectcombobox"),
            entry("UnifiedButton", "components.md#buttons-and-menus"),
            entry("UnifiedMenuBar", "components.md#buttons-and-menus"),
            entry("UnifiedTabSheet", "components.md#tabs-and-details"),
            entry("UnifiedTabs", "components.md#tabs-and-details"),
            entry("UnifiedDetails", "components.md#tabs-and-details"),
            entry("UnifiedDialog", "components.md#dialogs-and-notifications"),
            entry("UnifiedNotification", "components.md#dialogs-and-notifications"),
            entry("UnifiedUpload", "components.md#upload"),
            entry("UnifiedMessageInput", "components.md#chat"),
            entry("UnifiedMessageList", "components.md#chat"),
            entry("UnifiedGrid", "grid.md"),
            entry("UnifiedContainer", "finding-components.md#scopes"),
            entry("UnifiedGenericComponent", "finding-components.md#components-without-a-wrapper"),
            entry("UnifiedArticle", "components.md#plain-html-and-text"),
            entry("UnifiedAside", "components.md#plain-html-and-text"),
            entry("UnifiedDiv", "components.md#plain-html-and-text"),
            entry("UnifiedFooter", "components.md#plain-html-and-text"),
            entry("UnifiedHeader", "components.md#plain-html-and-text"),
            entry("UnifiedMain", "components.md#plain-html-and-text"),
            entry("UnifiedNav", "components.md#plain-html-and-text"),
            entry("UnifiedParagraph", "components.md#plain-html-and-text"),
            entry("UnifiedSection", "components.md#plain-html-and-text"),
            entry("UnifiedSpan", "components.md#plain-html-and-text"));

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

    @Test
    void everyWrapperLinksToTheGuide() throws Exception {
        for (Class<?> wrapper : wrappers()) {
            assertTrue(GUIDE.containsKey(wrapper.getSimpleName()), wrapper.getSimpleName() + " has no guide section:"
                    + " add the docs/guide page that shows it to ApiReferenceTest.GUIDE");
        }
    }

    @Test
    void theGuideLinksPointAtSectionsThatExist() throws Exception {
        for (Map.Entry<String, String> link : GUIDE.entrySet()) {
            String[] parts = link.getValue().split("#");
            Path page = Path.of("../docs/guide").resolve(parts[0]);
            assertTrue(Files.exists(page), link.getKey() + ": no " + page);
            if (parts.length > 1) {
                List<String> anchors = Files.readAllLines(page).stream().filter(l -> l.startsWith("#"))
                        .map(l -> anchor(l.replaceFirst("^#+ ", ""))).toList();
                assertTrue(anchors.contains(parts[1]), link.getKey() + ": no section #" + parts[1] + " in " + page
                        + "; its sections are " + anchors);
            }
        }
    }

    // ---------------------------------------------------------------- the reference

    static String reference() throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("""
                # Unified API reference

                Every Wrapper of the Unified API with its filters, actions, reads and checks, and the section of \
                the developer guide that shows it. Generated from the code; shipped in the `unified-test-api` jar as \
                `META-INF/unified/api-reference.md`.

                - **Find** a component with `page.find(UnifiedX.class)` and filters: \
                `page.find(UnifiedTextField.class).withLabel("Name")`. A Wrapper is lazy: it looks its component up \
                again on every use, so keep it in a variable and reuse it.
                - **Actions** wait until the component is visible and enabled, act, then wait for the server \
                (Auto-Wait). Each is a User Step in the reports, worded as listed here, with the component's name \
                and the values where the "…" are.
                - **Reads** return what is there right now, without waiting. Assert with a check instead when you can.
                - **Checks** are `assertThat(wrapper).…` from `UnifiedAssertions`; they retry until the timeout and \
                are User Steps (`see …`).
                - A component without a Wrapper: `page.findComponent(FlowClass.class)`, with the filters of every \
                Wrapper.
                - **Evidence**: a failed test leaves a screenshot (browser) or the component tree (Browserless) \
                under `target/surefire-reports/unified-evidence/`. For every User Step, failed or not, set \
                `vaadin.testing.user-steps.snapshots=true`; for Playwright's trace (`trace.zip`), set \
                `vaadin.testing.playwright.trace=on` or `on-failure`. Every `vaadin.testing.*` setting, with its \
                default: the [configuration guide](https://parttio.github.io/unified-testing-tool/guide/configuration/).

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
        String guide = GUIDE.get(type.getSimpleName());
        if (!about.isEmpty() || guide != null) {
            String text = about.isEmpty() ? "" : Character.toUpperCase(about.charAt(0)) + about.substring(1) + ". ";
            md.append(text).append(guide == null ? "" : "Guide: [" + guideTitle(guide) + "](" + guideUrl(guide) + ").")
                    .append("\n\n");
        }
        Map<String, List<Method>> kinds = new LinkedHashMap<>();
        for (String kind : List.of("Filters", "Actions", "Reads", "Also")) {
            kinds.put(kind, new ArrayList<>());
        }
        for (Method m : methods) {
            kinds.get(kind(m, type)).add(m);
        }
        kinds.forEach((kind, list) -> {
            if (kind.equals("Actions") && UnifiedWrapper.class.isAssignableFrom(type) && !list.isEmpty()) {
                actions(md, list, type);
            } else {
                list(md, kind, signatures(list, !kind.equals("Filters") && !kind.equals("Actions")));
            }
        });
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

    /** A Wrapper's actions, one a line, each with the User Step it reports: the words of the reports' verb table. */
    private static void actions(StringBuilder md, List<Method> actions, Class<?> wrapper) {
        md.append("- **Actions**, each with the User Step it reports:\n");
        StepTarget target = StepTarget.of(StepTarget.kindOf(wrapper)).withName("…");
        for (Method m : actions) {
            Object[] args = Arrays.stream(m.getParameterTypes()).map(ApiReferenceTest::sample).toArray();
            md.append("  - `").append(m.getName()).append(parameters(m)).append("`: ")
                    .append(Phrases.forWrapperCall(wrapper, target, m, args).render()).append('\n');
        }
    }

    /** A stand-in argument, shown as "…" in a User Step. */
    private static Object sample(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == Path.class) {
            return Path.of("file.txt");
        }
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive()) {
                return Array.newInstance(type.getComponentType(), 0);
            }
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, sample(type.getComponentType()));
            return array;
        }
        if (Collection.class.isAssignableFrom(type)) {
            return List.of("…");
        }
        return "…";
    }

    private static String guideUrl(String guide) {
        String[] parts = guide.split("#");
        return SITE + "guide/" + parts[0].replace(".md", "/") + (parts.length > 1 ? "#" + parts[1] : "");
    }

    /** The guide page's title, from its first heading, and the section's. */
    private static String guideTitle(String guide) {
        String[] parts = guide.split("#");
        try {
            List<String> lines = Files.readAllLines(Path.of("../docs/guide").resolve(parts[0]));
            String page = lines.stream().filter(l -> l.startsWith("# ")).findFirst().orElseThrow().substring(2);
            if (parts.length == 1) {
                return page;
            }
            String section = lines.stream().filter(l -> l.startsWith("#") && anchor(l.replaceFirst("^#+ ", ""))
                    .equals(parts[1])).findFirst().orElseThrow().replaceFirst("^#+ ", "");
            return page + ", " + section;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A heading's anchor, as MkDocs makes it: lower case, punctuation dropped, spaces as hyphens. */
    static String anchor(String heading) {
        String plain = heading.replaceAll("[`*]", "").strip().toLowerCase(Locale.ROOT);
        return plain.replaceAll("[^\\w\\s-]", "").replaceAll("\\s+", "-");
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
