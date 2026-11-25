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
import com.vaadin.testing.unified.component.UnifiedLink;
import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
import com.vaadin.testing.unified.component.UnifiedLogin;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNav;
import com.vaadin.testing.unified.component.UnifiedMain;
import com.vaadin.testing.unified.component.UnifiedFooter;
import com.vaadin.testing.unified.component.UnifiedHeader;
import com.vaadin.testing.unified.component.UnifiedAside;
import com.vaadin.testing.unified.component.UnifiedArticle;
import com.vaadin.testing.unified.component.UnifiedSection;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.component.UnifiedSpan;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedNumberField;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedTabSheet;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextArea;
import com.vaadin.testing.unified.component.UnifiedEmailField;
import com.vaadin.testing.unified.component.UnifiedPasswordField;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.component.UnifiedUpload;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import com.vaadin.testing.unified.query.TextFilter;
import com.vaadin.testing.unified.spi.ElementFacts;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.GridAccess;
import com.vaadin.testing.unified.spi.MessageListAccess;
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
 * message list, 3 messages              page.find(UnifiedMessageList.class)
 * button (no name; test id “close”)     page.find(UnifiedButton.class).withTestId("close")
 * </pre>
 *
 * For each Wrapper type, {@code find(Type.class)} runs once in the scope, and the naming values of its matches are
 * read at once, as for Near Misses ({@link com.vaadin.testing.unified.spi.AdapterSession#facts}). A lookup is
 * printed when its filter's value is that of exactly one match, compared as the exact filters compare. A match that
 * is an internal part of another component ({@link ElementFacts#internalPart()}), such as the text area inside a
 * message input in a browser, is left out, so the view lists the same components on every Testing Tool. A component
 * no filter of its own tells apart is printed with the container that does, and that lookup is run before it is
 * printed. Reads only: nothing is recorded as a User Step, a read that fails leaves its part out, and the test's
 * secrets are masked, as in the reports.
 */
public final class FindableView {
    private FindableView() {}

    /** The Wrapper types listed, in this order, before the custom Wrappers the test has looked up. */
    static final List<Class<?>> BUILT_IN = List.of(UnifiedDialog.class, UnifiedConfirmDialog.class, UnifiedLogin.class, UnifiedNotification.class,
            UnifiedTextField.class, UnifiedTextArea.class, UnifiedEmailField.class, UnifiedPasswordField.class,
            UnifiedNumberField.class, UnifiedIntegerField.class, UnifiedBigDecimalField.class, UnifiedComboBox.class, UnifiedMultiSelectComboBox.class,
            UnifiedDatePicker.class, UnifiedDateTimePicker.class, UnifiedRadioButtonGroup.class,
            UnifiedCheckbox.class, UnifiedButton.class, UnifiedLink.class, UnifiedGrid.class, UnifiedTabs.class,
            UnifiedTabSheet.class, UnifiedMenuBar.class, UnifiedDetails.class, UnifiedUpload.class,
            UnifiedMessageList.class, UnifiedMessageInput.class);

    /**
     * Plain HTML Wrappers listed only for the components that say they are clickable (#523): a role a user
     * activates, or a {@code tabindex} of 0 or more. A click listener alone can't be seen the same way on every
     * Testing Tool, so it doesn't count; a type the test has looked up is listed in full.
     */
    static final List<Class<?>> CLICKABLE_PLAIN = List.of(UnifiedDiv.class, UnifiedSpan.class,
            UnifiedParagraph.class, UnifiedSection.class, UnifiedArticle.class, UnifiedAside.class,
            UnifiedHeader.class, UnifiedFooter.class, UnifiedMain.class, UnifiedNav.class);

    /** The ARIA roles a user activates by clicking. */
    static final Set<String> CLICKABLE_ROLES = Set.of("button", "link", "tab", "menuitem", "menuitemcheckbox",
            "menuitemradio", "checkbox", "radio", "switch", "option");

    /**
     * The fields whose {@code value} property is what a user sees. A combo box's or a radio button group's is an
     * item key in a browser, so they show none. A password field shows none: its value is a secret.
     */
    private static final Set<Class<?>> SHOWS_VALUE = Set.of(UnifiedTextField.class, UnifiedTextArea.class,
            UnifiedEmailField.class, UnifiedNumberField.class, UnifiedIntegerField.class, UnifiedBigDecimalField.class,
            UnifiedDatePicker.class, UnifiedDateTimePicker.class);

    /** Last words of a custom filter's name that make it a name, as in the User Steps: {@code withHeaderTitle}. */
    private static final Set<String> NAMING_WORDS = Set.of("title", "text", "name", "label", "caption");

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
                components(run, null, false, "page.")));
    }

    /**
     * The view inside a container, lookups relative to it ({@code find(..)} on the container). Waits, as any use
     * of the container does, until it can be singled out.
     */
    public static String ofScope(LazyElement container) {
        UnifiedElement scope = container.concrete();
        return container.run().steps().mask(render("Findable view inside " + StepTarget.scopeOf(container) + " on "
                + container.run().toolName() + "; lookups are on it", components(container.run(), scope, ElementSource.entersTemplate(container), "")));
    }

    /**
     * Writes the page's view as {@code <name>.view.txt} into {@code directory}, for the failure evidence.
     *
     * @return the file written
     */
    static Path write(TestRun run, Path directory, String name) throws IOException {
        return write(run, directory, name, null);
    }

    /** {@link #write(TestRun, Path, String)}, followed by {@code more} (a view of items) when not null. */
    static Path write(TestRun run, Path directory, String name, String more) throws IOException {
        String view = ofPage(run) + (more == null ? "" : "\n" + more);
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

        /** Shown as the component's name: not placeholders, test ids and ids, which say "(no name)". */
        boolean names() {
            return kind != Filter.Kind.PLACEHOLDER && kind != Filter.Kind.TEST_ID && kind != Filter.Kind.ID;
        }
    }

    /** One component as read. {@code values} are its namers' values, normalized, null when it has none. */
    private record Read(UnifiedElement element, ElementFacts facts, Map<Namer, String> values, String value,
            boolean hidden, boolean disabled, String summary, String clickable) {
        String valueOf(Namer namer) {
            return values.get(namer);
        }

        /** This read, listed because it says it is clickable: {@code role button}, {@code focusable}. */
        Read clickable(String why) {
            return new Read(element, facts, values, value, hidden, disabled, summary, why);
        }
    }

    /** The components of one Wrapper type in the scope. */
    /**
     * The components of one Wrapper type in the scope; {@code partial} when only the clickable ones of a plain
     * HTML type were read, so that a value unique among them may not be unique in the scope.
     */
    private record OfType(Class<?> type, UnifiedComponent annotation, String tag, List<Filter> selector,
            List<Namer> namers, List<Read> reads, boolean partial) {}

    /** A container listed with a unique lookup, through which an ambiguous component may be singled out. */
    private record Container(String words, String lookup, UnifiedElement element) {}

    // ---------------------------------------------------------------- reading

    static List<Line> components(TestRun run, UnifiedElement scope, boolean inTemplate, String prefix) {
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
                    OfType found = ofType(run, type, annotation, scope, inTemplate);
                    if (found != null) {
                        all.add(found);
                    }
                } catch (RuntimeException | AssertionError e) {
                    // this kind can't be listed on this Testing Tool
                }
            }
            for (Class<?> type : CLICKABLE_PLAIN) {
                UnifiedComponent annotation = type.getAnnotation(UnifiedComponent.class);
                if (types.contains(type) || !listed.add(Wrappers.tagOf(annotation.value(), annotation.tag()) + " "
                        + annotation.selector().strip())) {
                    continue; // the test looked this type up: listed in full above
                }
                try {
                    OfType found = clickable(run, type, annotation, scope, inTemplate);
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
                    String lookup = t.partial() ? checkedLookup(run, t, r, scope, inTemplate, prefix)
                            : uniqueLookup(t, r, prefix);
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
                            : apart(run, t, r, scope, inTemplate, prefix, containers)));
                }
            }
            lines.addAll(templates(run, scope, inTemplate, prefix, listed));
            return lines;
        });
    }

    /**
     * The Templates in the scope, one line each with the lookup that enters it (spec 12, "Findable View"); what is
     * inside them is listed by {@code describe()} on the Template. A Template the test's own Wrapper lists already
     * is left out.
     */
    private static List<Line> templates(TestRun run, UnifiedElement scope, boolean inTemplate, String prefix,
            Set<String> listed) {
        List<Templates.Found> found = Templates.in(run, scope, inTemplate).stream()
                .filter(t -> listed.stream().noneMatch(key -> key.startsWith(t.tag() + " "))).toList();
        List<Line> lines = new ArrayList<>();
        for (Templates.Found t : found) {
            long alike = found.stream().filter(o -> o.tag().equals(t.tag())).count();
            lines.add(new Line("Template <" + t.tag() + ">", alike == 1 ? prefix + t.enter()
                    : "not unique: " + alike + " alike"));
        }
        return lines;
    }

    private static OfType ofType(TestRun run, Class<?> type, UnifiedComponent annotation, UnifiedElement scope,
            boolean inTemplate) {
        String tag = Wrappers.tagOf(annotation.value(), annotation.tag());
        if (tag == null || tag.equals("*")) {
            return null;
        }
        List<Filter> selector = Wrappers.typeFilters(type, annotation, tag);
        List<UnifiedElement> found = run.session().find(new QuerySpec(annotation.value(), tag, selector, scope,
                inTemplate));
        if (found.isEmpty()) {
            return null;
        }
        found = found.subList(0, Math.min(found.size(), MAX_PER_TYPE));
        List<ElementFacts> facts = LookupFailures.facts(run, found);
        List<Namer> namers = namers(type, inTemplate);
        TestRun quick = run.withTimeout(Duration.ZERO);
        List<Read> reads = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            if (facts.get(i) != null && facts.get(i).internalPart()) {
                continue; // another component's own part, which the application didn't create: not listed (#517)
            }
            reads.add(read(quick, type, namers, found.get(i), facts.get(i)));
        }
        if (reads.isEmpty()) {
            return null;
        }
        return new OfType(type, annotation, tag, selector, namers, reads, false);
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
                // a name of its own: not the text a component named by its content is named by (#641)
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
        String summary = UnifiedGrid.class.isAssignableFrom(type) ? safely(() -> grid(e), null)
                : UnifiedMessageList.class.isAssignableFrom(type) ? safely(() -> messageList(e), null) : null;
        return new Read(e, f, values, value, hidden, disabled, summary, null);
    }

    /** {@code 3 messages}, from the Testing Tool's message list access. */
    private static String messageList(UnifiedElement e) {
        Optional<MessageListAccess> access = e.tryUnwrap(MessageListAccess.class);
        if (access.isEmpty()) {
            return null;
        }
        int messages = access.get().messages().size();
        return messages + (messages == 1 ? " message" : " messages");
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
     * filters, then label, text, accessible name, placeholder, test id and id, as far as the type has them. Inside a
     * Template the id comes first: text written in the template's HTML is empty on Browserless, so {@code withId}
     * is the lookup that works on every Testing Tool there (ADR 0012, #609).
     */
    private static List<Namer> namers(Class<?> type, boolean inTemplate) {
        List<Namer> namers = new ArrayList<>();
        Namer id = new Namer(Filter.Kind.ID, null, null);
        if (inTemplate) {
            namers.add(id);
        }
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
        if (!inTemplate) {
            namers.add(id);
        }
        return namers;
    }

    /** {@code withHeaderTitle(String)} returning the Wrapper → its {@code String getHeaderTitle()}, or null. */
    private static Method namingGetter(Class<?> type, Method filter) {
        String name = filter.getName();
        if (!filter.isDefault() || !name.startsWith("with") || name.length() <= 4 || filter.getParameterCount() != 1
                || filter.getParameterTypes()[0] != String.class || !returnsWrapper(type, filter)) {
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

    /** The filter returns the Wrapper: its own type, or {@code SELF} of a shared mixin ({@code UnifiedOverlayDialog}). */
    private static boolean returnsWrapper(Class<?> type, Method filter) {
        return type.isAssignableFrom(filter.getReturnType())
                || filter.getGenericReturnType() instanceof java.lang.reflect.TypeVariable<?> self
                        && self.getGenericDeclaration() instanceof Class<?>;
    }

    // ---------------------------------------------------------------- one line

    /**
     * {@code text field “Name” = “Alan”}, {@code button (no name; test id “close”)},
     * {@code button (no name; id “save”)}, {@code grid, 3 rows},
     * {@code message list, 2 messages}.
     */
    private static String description(OfType t, Read r) {
        StringBuilder sb = new StringBuilder(words(t, r));
        List<String> state = new ArrayList<>();
        if (name(t, r) == null && (LabelFilter.class.isAssignableFrom(t.type())
                || TextFilter.class.isAssignableFrom(t.type()))) {
            String placeholder = normalize(r.facts().placeholder());
            String testId = normalize(r.facts().testId());
            String id = normalize(r.facts().id());
            state.add(placeholder != null ? "no name; placeholder " + quote(placeholder)
                    : testId != null ? "no name; test id " + quote(testId)
                    : id != null ? "no name; id " + quote(id) : "no name");
        }
        if (r.value() != null && !r.value().isEmpty()) {
            sb.append(UnifiedCheckbox.class.isAssignableFrom(t.type()) ? " (checked)" : " = " + quote(r.value()));
        }
        if (r.summary() != null) {
            sb.append(", ").append(r.summary());
        }
        if (r.disabled()) {
            state.add("disabled");
        }
        if (r.clickable() != null) {
            state.add(r.clickable());
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
     * The components of a plain HTML type that say they are clickable: those with a role ({@code [role]}) a user
     * activates, then those with a {@code tabindex} ({@code [tabindex]}) of 0 or more and no such role.
     */
    private static OfType clickable(TestRun run, Class<?> type, UnifiedComponent annotation, UnifiedElement scope,
            boolean inTemplate) {
        String tag = Wrappers.tagOf(annotation.value(), annotation.tag());
        List<UnifiedElement> found = new ArrayList<>();
        List<String> why = new ArrayList<>();
        for (UnifiedElement e : run.session().find(new QuerySpec(annotation.value(), tag,
                List.of(new Filter(Filter.Kind.ATTRIBUTE, "role", null, true)), scope, inTemplate))) {
            String role = role(e);
            if (isClickable(role)) {
                found.add(e);
                why.add("role " + role);
            }
        }
        for (UnifiedElement e : run.session().find(new QuerySpec(annotation.value(), tag,
                List.of(new Filter(Filter.Kind.ATTRIBUTE, "tabindex", null, true)), scope, inTemplate))) {
            if (!isClickable(role(e)) && tabIndex(e) >= 0) {
                found.add(e);
                why.add("focusable");
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        found = found.subList(0, Math.min(found.size(), MAX_PER_TYPE));
        List<ElementFacts> facts = LookupFailures.facts(run, found);
        List<Namer> namers = namers(type, inTemplate);
        TestRun quick = run.withTimeout(Duration.ZERO);
        List<Read> reads = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            if (facts.get(i) != null && facts.get(i).internalPart()) {
                continue;
            }
            reads.add(read(quick, type, namers, found.get(i), facts.get(i)).clickable(why.get(i)));
        }
        return reads.isEmpty() ? null : new OfType(type, annotation, tag, List.of(), namers, reads, true);
    }

    private static boolean isClickable(String role) {
        return role != null && CLICKABLE_ROLES.contains(role);
    }

    /** The element's first role, lower case, or null. */
    private static String role(UnifiedElement e) {
        String role = safely(() -> e.getAttribute("role"), null);
        return role == null || role.isBlank() ? null : role.strip().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    private static int tabIndex(UnifiedElement e) {
        String value = safely(() -> e.getAttribute("tabindex"), null);
        try {
            return value == null ? -1 : Integer.parseInt(value.strip());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * For a clickable plain HTML component, whose type was read only in part: the best filter whose value only it
     * has among the clickable ones and that, run, finds exactly one component of its type in the scope.
     */
    private static String checkedLookup(TestRun run, OfType t, Read r, UnifiedElement scope, boolean inTemplate,
            String prefix) {
        for (Namer namer : t.namers()) {
            String value = r.valueOf(namer);
            if (value == null || namer.kind() == null || count(t.reads(), namer, value) != 1) {
                continue;
            }
            List<Filter> filters = List.of(new Filter(namer.kind(), null, value, true));
            int matches = safely(() -> run.session().find(new QuerySpec(t.annotation().value(), t.tag(), filters,
                    scope, inTemplate)).size(), 0);
            if (matches == 1) {
                return prefix + "find(" + t.type().getSimpleName() + ".class)." + namer.code(value);
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
     * are alike, and how many of them its container holds when that container is the only one with its test id
     * or id (a container every row shares tells nothing apart, #522).
     */
    private static String apart(TestRun run, OfType t, Read r, UnifiedElement scope, boolean inTemplate, String prefix,
            List<Container> containers) {
        Namer best = null;
        for (Namer namer : t.namers()) {
            if (r.valueOf(namer) != null) {
                best = namer;
                break;
            }
        }
        long alike = best == null ? t.reads().size() : count(t.reads(), best, r.valueOf(best));
        if (t.partial() && best != null && best.kind() != null) {
            // only the clickable ones were read: count every component of the type with this value (#523)
            List<Filter> same = List.of(new Filter(best.kind(), null, r.valueOf(best), true));
            alike = Math.max(alike, safely(() -> run.session().find(new QuerySpec(t.annotation().value(), t.tag(),
                    same, scope, inTemplate)).size(), 0));
        }
        String notUnique = "not unique: " + alike + " alike";
        if (best == null) {
            return notUnique;
        }
        String inner = ".find(" + t.type().getSimpleName() + ".class)." + best.code(r.valueOf(best));
        Filter.Kind kind = r.facts().ancestorTestId() != null ? Filter.Kind.TEST_ID
                : r.facts().ancestorId() != null ? Filter.Kind.ID : null;
        if (kind != null) {
            String value = kind == Filter.Kind.TEST_ID ? r.facts().ancestorTestId() : r.facts().ancestorId();
            Filter filter = new Filter(kind, null, value, true);
            Optional<UnifiedElement> container = safely(() -> single(run.session().find(
                    new QuerySpec(Component.class, "*", List.of(filter), scope, inTemplate))), Optional.empty());
            int inside = container.isPresent() ? countIn(run, t, best, r, container.get()) : 0;
            if (inside == 1) {
                return prefix + "find(" + UnifiedContainer.class.getSimpleName() + ".class)."
                        + new Namer(kind, null, null).code(value) + inner;
            }
            if (inside > 1) {
                String byData = byDataAttribute(run, t, best, r, scope, inTemplate, prefix);
                return byData != null ? byData
                        : notUnique + "; " + inside + " in container (" + StepTarget.words(filter) + ")";
            }
        }
        for (Container container : containers) {
            if (container.element() != r.element() && countIn(run, t, best, r, container.element()) == 1) {
                return notUnique + "; one in " + container.words() + ": " + container.lookup() + inner;
            }
        }
        String byData = byDataAttribute(run, t, best, r, scope, inTemplate, prefix);
        return byData != null ? byData : notUnique;
    }

    /**
     * The last resort for alike components (#547): the name, then the first of the component's {@code data-*}
     * attributes, by name, whose value none of the alike has, once run and found to match exactly one.
     */
    private static String byDataAttribute(TestRun run, OfType t, Namer best, Read r, UnifiedElement scope,
            boolean inTemplate, String prefix) {
        String name = r.valueOf(best);
        if (best.kind() == null || name == null) {
            return null;
        }
        List<Read> alike = t.reads().stream().filter(o -> name.equals(o.valueOf(best))).toList();
        for (Map.Entry<String, String> attribute : new java.util.TreeMap<>(r.facts().data()).entrySet()) {
            String value = attribute.getValue();
            if (value == null || alike.stream().filter(o -> value.equals(o.facts().data().get(attribute.getKey())))
                    .count() != 1) {
                continue;
            }
            List<Filter> filters = new ArrayList<>(t.selector());
            filters.add(new Filter(best.kind(), null, name, true));
            filters.add(new Filter(Filter.Kind.ATTRIBUTE, attribute.getKey(), value, true));
            int found = safely(() -> run.session().find(new QuerySpec(t.annotation().value(), t.tag(),
                    List.copyOf(filters), scope, inTemplate)).size(), 0);
            if (found == 1) {
                return prefix + "find(" + t.type().getSimpleName() + ".class)." + best.code(name)
                        + ".withAttribute(\"" + attribute.getKey() + "\", \"" + value + "\")";
            }
        }
        return null;
    }

    /** How many components the lookup by {@code namer} finds inside {@code container}; 0 when it can't be run. */
    private static int countIn(TestRun run, OfType t, Namer namer, Read r, UnifiedElement container) {
        if (namer.kind() == null) {
            return 0; // a Wrapper's own filter runs in the core: not checked here
        }
        List<Filter> filters = new ArrayList<>(t.selector());
        filters.add(new Filter(namer.kind(), null, r.valueOf(namer), true));
        return safely(() -> run.session().find(new QuerySpec(t.annotation().value(), t.tag(), List.copyOf(filters),
                container)).size(), 0);
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
        appendLines(sb, shown);
        if (lines.size() > shown.size()) {
            sb.append("… and ").append(lines.size() - shown.size())
                    .append(" more: describe a container to see them\n");
        }
        return sb.toString();
    }

    /** The lines, each description padded so the lookups line up. */
    static void appendLines(StringBuilder sb, List<Line> lines) {
        int width = Math.min(COLUMN, lines.stream().mapToInt(l -> l.description().length()).max().orElse(0));
        for (Line line : lines) {
            sb.append(line.description());
            sb.append(" ".repeat(Math.max(2, width + 2 - line.description().length())));
            sb.append(line.lookup()).append('\n');
        }
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
