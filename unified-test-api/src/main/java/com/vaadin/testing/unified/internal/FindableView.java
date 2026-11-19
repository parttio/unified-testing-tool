package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDateTimePicker;
import com.vaadin.testing.unified.component.UnifiedDetails;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedNumberField;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedTabSheet;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextArea;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.component.UnifiedUpload;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import com.vaadin.testing.unified.query.TextFilter;
import com.vaadin.testing.unified.spi.ElementFacts;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.GridAccess;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Findable View (spec chapter 12, "Findable View"): the components a Unified UI Test can find in a scope, one
 * line each, with its kind, name and state in User Step words and the lookup that singles it out.
 *
 * <pre>
 * text field “Name” = “Alan Turing”     page.find(UnifiedTextField.class).withLabel("Name")
 * button “Save” (disabled)              page.find(UnifiedButton.class).withText("Save")
 * grid, 12 rows: Name, Email            page.find(UnifiedGrid.class)
 * button (no name; test id “close”)     page.find(UnifiedButton.class).withTestId("close")
 * </pre>
 *
 * For each Wrapper type, {@code find(Type.class)} runs once in the scope, and the naming values of its matches are
 * read at once, as for Near Misses ({@link com.vaadin.testing.unified.spi.AdapterSession#facts}). A lookup is
 * printed when its filter's value is that of exactly one match, compared as the exact filters compare. A component
 * no filter of its own tells apart is printed with the container that does, and that lookup is run before it is
 * printed. Reads only: nothing is recorded as a User Step, a read that fails leaves its part out, and the test's
 * secrets are masked, as in the reports.
 */
public final class FindableView {
    private FindableView() {}

    /** The Wrapper types listed, in this order, before the custom Wrappers the test has looked up. */
    static final List<Class<?>> BUILT_IN = List.of(UnifiedDialog.class, UnifiedNotification.class,
            UnifiedTextField.class, UnifiedTextArea.class, UnifiedNumberField.class, UnifiedIntegerField.class,
            UnifiedBigDecimalField.class, UnifiedComboBox.class, UnifiedMultiSelectComboBox.class,
            UnifiedDatePicker.class, UnifiedDateTimePicker.class, UnifiedRadioButtonGroup.class,
            UnifiedCheckbox.class, UnifiedButton.class, UnifiedGrid.class, UnifiedTabs.class, UnifiedTabSheet.class,
            UnifiedMenuBar.class, UnifiedDetails.class, UnifiedUpload.class);

    /**
     * The fields whose {@code value} property is what a user sees. A combo box's or a radio button group's is an
     * item key in a browser, so they show none.
     */
    private static final Set<Class<?>> SHOWS_VALUE = Set.of(UnifiedTextField.class, UnifiedTextArea.class,
            UnifiedNumberField.class, UnifiedIntegerField.class, UnifiedBigDecimalField.class,
            UnifiedDatePicker.class, UnifiedDateTimePicker.class);

    /** Last words of a custom filter's name that make it a name, as in the User Steps: {@code withHeaderTitle}. */
    private static final Set<String> NAMING_WORDS = Set.of("title", "text", "name", "label", "caption");

    /** The id Flow gives the element it renders the UI in: a new one on every page load, so no container. */
    private static final java.util.regex.Pattern FLOW_ROOT = java.util.regex.Pattern.compile("ROOT-\\d+");

    /** At most this many components are listed; the rest are counted. */
    static final int MAX_LINES = 100;
    /** At most this many components of one Wrapper type are read. */
    private static final int MAX_PER_TYPE = 200;
    /** At most this many column headers are named for a grid. */
    private static final int MAX_HEADERS = 6;
    /** The lookup column starts here, unless a description is longer. */
    private static final int COLUMN = 38;
    /** Values longer than this are cut. */
    private static final int MAX_VALUE = 40;

    // ---------------------------------------------------------------- entry points

    /** The view of the whole page, lookups starting with {@code page.}. */
    public static String ofPage(TestRun run) {
        String where;
        try {
            String path = run.session().currentPath();
            where = "“/" + (path == null ? "" : path) + "”";
        } catch (RuntimeException e) {
            where = "the page";
        }
        return run.steps().mask(render("Findable view of " + where + " on " + run.toolName(),
                components(run, null, "page.")));
    }

    /**
     * The view inside a container, lookups relative to it ({@code find(..)} on the container). Waits, as any use
     * of the container does, until it can be singled out.
     */
    public static String ofScope(LazyElement container) {
        UnifiedElement scope = container.concrete();
        return container.run().steps().mask(render("Findable view inside " + StepTarget.scopeOf(container) + " on "
                + container.run().toolName() + "; lookups are on it", components(container.run(), scope, "")));
    }

    /**
     * Writes the page's view as {@code <name>.view.txt} into {@code directory}, for the failure evidence.
     *
     * @return the file written
     */
    static Path write(TestRun run, Path directory, String name) throws IOException {
        String view = ofPage(run);
        Files.createDirectories(directory);
        Path file = directory.resolve(name + ".view.txt");
        Files.writeString(file, view, StandardCharsets.UTF_8);
        return file;
    }

    // ---------------------------------------------------------------- model

    /**
     * One line of the view.
     *
     * @param description kind, name, state: {@code text field “Name” = “Alan”}
     * @param lookup the code that singles it out, or what tells it apart when nothing does
     */
    record Line(String description, String lookup) {}

    /**
     * A filter a lookup of one Wrapper type can name a component by: one the Testing Tools match ({@code kind}),
     * or one of the Wrapper's own, read with its getter ({@code withHeaderTitle} and {@code getHeaderTitle()}).
     */
    private record Namer(Filter.Kind kind, String method, Method getter) {
        String code(String value) {
            if (kind != null) {
                String code = new Filter(kind, null, value, true).toString();
                return code.substring(0, code.indexOf('(') + 1) + javaString(value) + ")";
            }
            return method + "(" + javaString(value) + ")";
        }

        /** Shown as the component's name: not placeholders and test ids, which say "(no name)". */
        boolean names() {
            return kind != Filter.Kind.PLACEHOLDER && kind != Filter.Kind.TEST_ID;
        }
    }

    /** One component as read. {@code values} are its namers' values, normalized, null when it has none. */
    private record Read(UnifiedElement element, ElementFacts facts, Map<Namer, String> values, String value,
            boolean hidden, boolean disabled, String grid) {
        String valueOf(Namer namer) {
            return values.get(namer);
        }
    }

    /** The components of one Wrapper type in the scope. */
    private record OfType(Class<?> type, UnifiedComponent annotation, String tag, List<Filter> selector,
            List<Namer> namers, List<Read> reads) {}

    /** A container listed with a unique lookup, through which an ambiguous component may be singled out. */
    private record Container(String words, String lookup, UnifiedElement element) {}

    // ---------------------------------------------------------------- reading

    static List<Line> components(TestRun run, UnifiedElement scope, String prefix) {
        return run.steps().unrecorded(() -> {
            List<OfType> all = new ArrayList<>();
            Set<String> listed = new HashSet<>();
            Set<Class<?>> types = new LinkedHashSet<>(BUILT_IN);
            types.addAll(run.steps().wrapperTypes());
            for (Class<?> type : types) {
                UnifiedComponent annotation = type.getAnnotation(UnifiedComponent.class);
                if (annotation == null || !listed.add(Wrappers.tagOf(annotation.value(), annotation.tag()) + " "
                        + annotation.selector().strip())) {
                    continue; // a custom Wrapper of the same components as one listed already
                }
                try {
                    OfType found = ofType(run, type, annotation, scope);
                    if (found != null) {
                        all.add(found);
                    }
                } catch (RuntimeException | AssertionError e) {
                    // this kind can't be listed on this Testing Tool
                }
            }
            List<Container> containers = new ArrayList<>();
            Map<Read, String> lookups = new IdentityHashMap<>();
            for (OfType t : all) {
                for (Read r : t.reads()) {
                    String lookup = uniqueLookup(t, r, prefix);
                    if (lookup != null) {
                        lookups.put(r, lookup);
                        if (Scope.class.isAssignableFrom(t.type())) {
                            containers.add(new Container(words(t, r), lookup, r.element()));
                        }
                    }
                }
            }
            List<Line> lines = new ArrayList<>();
            for (OfType t : all) {
                for (Read r : t.reads()) {
                    String lookup = lookups.get(r);
                    lines.add(new Line(description(t, r), lookup != null ? lookup
                            : apart(run, t, r, scope, prefix, containers)));
                }
            }
            return lines;
        });
    }

    private static OfType ofType(TestRun run, Class<?> type, UnifiedComponent annotation, UnifiedElement scope) {
        String tag = Wrappers.tagOf(annotation.value(), annotation.tag());
        if (tag == null || tag.equals("*")) {
            return null;
        }
        List<Filter> selector = annotation.selector().isBlank() ? List.of()
                : WrapperSelector.filters(annotation.selector(), type.getName());
        List<UnifiedElement> found = run.session().find(new QuerySpec(annotation.value(), tag, selector, scope));
        if (found.isEmpty()) {
            return null;
        }
        found = found.subList(0, Math.min(found.size(), MAX_PER_TYPE));
        List<ElementFacts> facts = LookupFailures.facts(run, found);
        List<Namer> namers = namers(type);
        TestRun quick = run.withTimeout(Duration.ZERO);
        List<Read> reads = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            reads.add(read(quick, type, namers, found.get(i), facts.get(i)));
        }
        return new OfType(type, annotation, tag, selector, namers, reads);
    }

    private static Read read(TestRun quick, Class<?> type, List<Namer> namers, UnifiedElement e,
            ElementFacts facts) {
        ElementFacts f = facts != null ? facts : new ElementFacts(null, null, null, null, null, null, null);
        Map<Namer, String> values = new HashMap<>();
        Object wrapper = null;
        for (Namer namer : namers) {
            String value;
            if (namer.kind() == null) {
                if (wrapper == null) {
                    wrapper = Wrappers.newWrapper(type, new LazyElement(fixed(e), -1, quick));
                }
                Object w = wrapper;
                value = safely(() -> (String) namer.getter().invoke(w), null);
            } else if (namer.kind() == Filter.Kind.ACCESSIBLE_NAME) {
                // a name of its own: not the text that a missing name falls back to
                String name = normalize(f.accessibleName());
                value = name != null && !name.equals(normalize(f.text())) ? name : null;
            } else {
                value = f.value(namer.kind());
            }
            values.put(namer, normalize(value));
        }
        boolean hidden = safely(() -> !e.isVisible(), false);
        boolean disabled = safely(() -> !e.isEnabled(), false);
        String value = null;
        if (UnifiedCheckbox.class.isAssignableFrom(type)) {
            value = safely(() -> Boolean.TRUE.equals(e.getPropertyBoolean("checked")) ? "checked" : null, null);
        } else if (SHOWS_VALUE.contains(type)) {
            value = safely(() -> e.getProperty("value"), null);
        }
        String grid = UnifiedGrid.class.isAssignableFrom(type) ? safely(() -> grid(e), null) : null;
        return new Read(e, f, values, value, hidden, disabled, grid);
    }

    /** {@code 12 rows: Name, Email}, from the Testing Tool's grid access. */
    private static String grid(UnifiedElement e) {
        Optional<GridAccess> access = e.tryUnwrap(GridAccess.class);
        if (access.isEmpty()) {
            return null;
        }
        int rows = access.get().rowCount();
        List<String> headers = access.get().columnHeaders().stream().filter(h -> h != null && !h.isBlank())
                .toList();
        String text = rows + (rows == 1 ? " row" : " rows");
        if (!headers.isEmpty()) {
            text += ": " + String.join(", ", headers.subList(0, Math.min(MAX_HEADERS, headers.size())))
                    + (headers.size() > MAX_HEADERS ? ", …" : "");
        }
        return text;
    }

    /**
     * The filters a lookup of this type can single a component out by, best first: the Wrapper's own naming
     * filters, then label, text, accessible name, placeholder and test id, as far as the type has them.
     */
    private static List<Namer> namers(Class<?> type) {
        List<Namer> namers = new ArrayList<>();
        for (Method filter : type.getMethods()) {
            Method getter = namingGetter(type, filter);
            if (getter != null) {
                namers.add(new Namer(null, filter.getName(), getter));
            }
        }
        namers.sort(java.util.Comparator.comparing(Namer::method));
        if (LabelFilter.class.isAssignableFrom(type)) {
            namers.add(new Namer(Filter.Kind.LABEL, null, null));
        }
        if (TextFilter.class.isAssignableFrom(type)) {
            namers.add(new Namer(Filter.Kind.TEXT, null, null));
        }
        namers.add(new Namer(Filter.Kind.ACCESSIBLE_NAME, null, null));
        if (PlaceholderFilter.class.isAssignableFrom(type)) {
            namers.add(new Namer(Filter.Kind.PLACEHOLDER, null, null));
        }
        namers.add(new Namer(Filter.Kind.TEST_ID, null, null));
        return namers;
    }

    /** {@code withHeaderTitle(String)} returning the Wrapper → its {@code String getHeaderTitle()}, or null. */
    private static Method namingGetter(Class<?> type, Method filter) {
        String name = filter.getName();
        if (!filter.isDefault() || !name.startsWith("with") || name.length() <= 4 || filter.getParameterCount() != 1
                || filter.getParameterTypes()[0] != String.class || !type.isAssignableFrom(filter.getReturnType())) {
            return null;
        }
        List<String> words = StepTarget.splitWords(name.substring(4));
        if (words.isEmpty() || !NAMING_WORDS.contains(words.get(words.size() - 1).toLowerCase(Locale.ROOT))) {
            return null;
        }
        try {
            Method getter = type.getMethod("get" + name.substring(4));
            return getter.getReturnType() == String.class && getter.isDefault() ? getter : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- one line

    /** {@code text field “Name” = “Alan”}, {@code button (no name; test id “close”)}, {@code grid, 3 rows}. */
    private static String description(OfType t, Read r) {
        StringBuilder sb = new StringBuilder(words(t, r));
        List<String> state = new ArrayList<>();
        if (name(t, r) == null && (LabelFilter.class.isAssignableFrom(t.type())
                || TextFilter.class.isAssignableFrom(t.type()))) {
            String placeholder = normalize(r.facts().placeholder());
            String testId = normalize(r.facts().testId());
            state.add(placeholder != null ? "no name; placeholder " + quote(placeholder)
                    : testId != null ? "no name; test id " + quote(testId) : "no name");
        }
        if (r.value() != null && !r.value().isEmpty()) {
            sb.append(UnifiedCheckbox.class.isAssignableFrom(t.type()) ? " (checked)" : " = " + quote(r.value()));
        }
        if (r.grid() != null) {
            sb.append(", ").append(r.grid());
        }
        if (r.disabled()) {
            state.add("disabled");
        }
        if (r.hidden()) {
            state.add("hidden");
        }
        if (!state.isEmpty()) {
            sb.append(" (").append(String.join("; ", state)).append(')');
        }
        return sb.toString();
    }

    /** The kind and name: {@code dialog “Edit person”}. */
    private static String words(OfType t, Read r) {
        String kind = t.annotation().kind().isBlank() ? StepTarget.kindOf(t.type()) : t.annotation().kind();
        String name = name(t, r);
        return name == null ? kind : kind + " " + quote(name);
    }

    /** The name a reader knows the component by: the first naming value it has. */
    private static String name(OfType t, Read r) {
        for (Namer namer : t.namers()) {
            if (namer.names() && r.valueOf(namer) != null) {
                return r.valueOf(namer);
            }
        }
        return null;
    }

    /**
     * The best filter whose value only this component has, or the bare lookup when it is the only one of its
     * type and has no such value; null when nothing of its own singles it out.
     */
    private static String uniqueLookup(OfType t, Read r, String prefix) {
        String find = prefix + "find(" + t.type().getSimpleName() + ".class)";
        for (Namer namer : t.namers()) {
            String value = r.valueOf(namer);
            if (value != null && count(t.reads(), namer, value) == 1) {
                return find + "." + namer.code(value);
            }
        }
        return t.reads().size() == 1 ? find : null;
    }

    /**
     * For a component nothing of its own singles out: the lookup through its nearest container with a test id or
     * an id, or through a listed container (a dialog), once run and found to match exactly one; else how many
     * are alike.
     */
    private static String apart(TestRun run, OfType t, Read r, UnifiedElement scope, String prefix,
            List<Container> containers) {
        Namer best = null;
        for (Namer namer : t.namers()) {
            if (r.valueOf(namer) != null) {
                best = namer;
                break;
            }
        }
        long alike = best == null ? t.reads().size() : count(t.reads(), best, r.valueOf(best));
        String notUnique = "not unique: " + alike + " alike";
        if (best == null) {
            return notUnique;
        }
        String inner = ".find(" + t.type().getSimpleName() + ".class)." + best.code(r.valueOf(best));
        Filter.Kind kind = r.facts().ancestorTestId() != null ? Filter.Kind.TEST_ID
                : r.facts().ancestorId() != null && !FLOW_ROOT.matcher(r.facts().ancestorId()).matches()
                ? Filter.Kind.ID : null;
        if (kind != null) {
            String value = kind == Filter.Kind.TEST_ID ? r.facts().ancestorTestId() : r.facts().ancestorId();
            Filter filter = new Filter(kind, null, value, true);
            Optional<UnifiedElement> container = safely(() -> single(run.session().find(
                    new QuerySpec(Component.class, "*", List.of(filter), scope))), Optional.empty());
            if (container.isPresent() && singlesOut(run, t, best, r, container.get())) {
                return prefix + "find(" + UnifiedContainer.class.getSimpleName() + ".class)."
                        + new Namer(kind, null, null).code(value) + inner;
            }
            return notUnique + "; this one in container (" + StepTarget.words(filter) + ")";
        }
        for (Container container : containers) {
            if (container.element() != r.element() && singlesOut(run, t, best, r, container.element())) {
                return notUnique + "; one in " + container.words() + ": " + container.lookup() + inner;
            }
        }
        return notUnique;
    }

    /** Whether the lookup by {@code namer} finds exactly one component inside {@code container}. */
    private static boolean singlesOut(TestRun run, OfType t, Namer namer, Read r, UnifiedElement container) {
        if (namer.kind() == null) {
            return false; // a Wrapper's own filter runs in the core: not checked here
        }
        List<Filter> filters = new ArrayList<>(t.selector());
        filters.add(new Filter(namer.kind(), null, r.valueOf(namer), true));
        return safely(() -> run.session().find(new QuerySpec(t.annotation().value(), t.tag(), List.copyOf(filters),
                container)).size() == 1, false);
    }

    private static Optional<UnifiedElement> single(List<UnifiedElement> found) {
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    private static long count(List<Read> reads, Namer namer, String value) {
        return reads.stream().filter(o -> value.equals(o.valueOf(namer))).count();
    }

    // ---------------------------------------------------------------- rendering

    static String render(String header, List<Line> lines) {
        StringBuilder sb = new StringBuilder(header).append(": ");
        if (lines.isEmpty()) {
            return sb.append("no component a Unified UI Test can find\n").toString();
        }
        sb.append(lines.size()).append(lines.size() == 1 ? " component\n" : " components\n");
        List<Line> shown = lines.subList(0, Math.min(MAX_LINES, lines.size()));
        int width = Math.min(COLUMN, shown.stream().mapToInt(l -> l.description().length()).max().orElse(0));
        for (Line line : shown) {
            sb.append(line.description());
            sb.append(" ".repeat(Math.max(2, width + 2 - line.description().length())));
            sb.append(line.lookup()).append('\n');
        }
        if (lines.size() > shown.size()) {
            sb.append("… and ").append(lines.size() - shown.size())
                    .append(" more: describe a container to see them\n");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers

    /** Whitespace collapsed and trimmed, as the exact filters compare; null when blank. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private static String quote(String value) {
        return "“" + (value.length() > MAX_VALUE ? value.substring(0, MAX_VALUE) + "…" : value) + "”";
    }

    private static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static ElementResolver fixed(UnifiedElement e) {
        return new ElementResolver() {
            @Override
            public List<UnifiedElement> resolve() {
                return List.of(e);
            }

            @Override
            public String describe() {
                return "a component of the Findable View";
            }
        };
    }

    private interface Reader<T> {
        T read() throws Exception;
    }

    private static <T> T safely(Reader<T> reader, T fallback) {
        try {
            return reader.read();
        } catch (Exception | AssertionError e) {
            return fallback;
        }
    }
}
