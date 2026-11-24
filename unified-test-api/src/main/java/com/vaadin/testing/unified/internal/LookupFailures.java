package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDateTimePicker;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
import com.vaadin.testing.unified.component.UnifiedNumberField;
import com.vaadin.testing.unified.component.UnifiedRadioButtonGroup;
import com.vaadin.testing.unified.component.UnifiedTextArea;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.internal.ElementSource.FilterLabel;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.failures.NearMisses;
import com.vaadin.testing.unified.internal.failures.NearMisses.Candidate;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.ElementFacts;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The failure parts of a lookup that could not single out its component, or of an action on one that stayed
 * unusable (spec chapter 12, "Failures"). Everything here runs on failure only; it reads what the page holds right
 * then, and a read that fails leaves its part out rather than hiding the failure.
 * <p>
 * Near Misses: the failed lookup again, without its user-facing filters (its {@code @UnifiedComponent} selector
 * kept), in the same scope, reading for each candidate the value its naming filter matched on; then the other
 * field kinds in the same scope with exactly the wanted name. When the scope itself is missing, the scope's own
 * lookup fails first, with Near Misses for the scope.
 */
final class LookupFailures {
    private LookupFailures() {}

    /** The Wrapper types a lookup by name may have meant instead ("another kind"), with their naming filter. */
    private static final List<Class<?>> OTHER_KINDS = List.of(UnifiedTextField.class, UnifiedTextArea.class,
            UnifiedNumberField.class, UnifiedIntegerField.class, UnifiedBigDecimalField.class, UnifiedComboBox.class,
            UnifiedMultiSelectComboBox.class, UnifiedDatePicker.class, UnifiedDateTimePicker.class,
            UnifiedCheckbox.class, UnifiedRadioButtonGroup.class, UnifiedButton.class);

    /** The naming filters, in the order a lookup's main one is picked. */
    private static final List<Filter.Kind> NAMING = List.of(Filter.Kind.LABEL, Filter.Kind.TEXT,
            Filter.Kind.ACCESSIBLE_NAME, Filter.Kind.PLACEHOLDER, Filter.Kind.TEST_ID);

    /** At most this many candidates are read for Near Misses. */
    private static final int MAX_CANDIDATES = 200;
    /** At most this many matches are listed when several match. */
    private static final int MAX_MATCHES = 5;

    // ---------------------------------------------------------------- entry points

    /** {@code concrete()} found none, or several, or fewer than its index needs. */
    static FailureParts notSingledOut(LazyElement element, int lastCount) {
        String target = target(element);
        int index = element.index();
        if (index >= 0) {
            FailureParts parts = FailureParts.of("a " + StepTarget.ordinal(index + 1) + " " + target,
                    lastCount == 0 ? "none" : "only " + lastCount);
            return lastCount == 0 ? withNearMisses(parts, element) : parts;
        }
        FailureParts parts = FailureParts.of("exactly one " + target, lastCount == 0 ? "none" : String.valueOf(lastCount));
        if (lastCount == 0) {
            return withNearMisses(parts, element);
        }
        if (element.source() instanceof ElementSource source) {
            safely(() -> parts.found(several(source, lastCount)));
        }
        return parts;
    }

    /** An action's Auto-Wait timed out: the component is there, but hidden or disabled. */
    static FailureParts notUsable(LazyElement element) {
        List<String> reasons = new ArrayList<>();
        safely(() -> element.tryConcrete().ifPresent(e -> {
            if (!e.isVisible()) {
                reasons.add("hidden");
            }
            if (!e.isEnabled()) {
                reasons.add("disabled");
            }
        }));
        // the action is the failed User Step ("Failed at"), not part of the expectation
        return FailureParts.of("a visible and enabled " + target(element),
                reasons.isEmpty() ? null : "1, but " + String.join(" and ", reasons));
    }

    // ---------------------------------------------------------------- what was expected

    /**
     * The lookup in words, for {@code Expected}: {@code text field labelled “Email” in dialog “Edit person”},
     * {@code button with text “Save” (test id “form”)}.
     */
    static String target(LazyElement element) {
        if (!(element.source() instanceof ElementSource source)) {
            return StepTarget.of(element).renderWithScope();
        }
        StringBuilder sb = new StringBuilder(StepTarget.kindOf(source));
        Probe probe = probe(source);
        List<String> others = new ArrayList<>();
        List<Filter> user = userFilters(source);
        for (int i = 0; i < user.size(); i++) {
            if (probe != null && probe.filterIndex() == i) {
                sb.append(' ').append(nameWords(user.get(i)));
            } else {
                others.add(StepTarget.words(user.get(i)));
            }
        }
        List<FilterLabel> labels = source.coreFilterNames();
        for (int i = 0; i < labels.size(); i++) {
            if (probe != null && probe.coreIndex() == i) {
                sb.append(" with ").append(StepTarget.words(labels.get(i)));
            } else {
                others.add(StepTarget.words(labels.get(i)));
            }
        }
        if (!others.isEmpty()) {
            sb.append(" (").append(String.join(", ", others)).append(')');
        }
        if (source.parent() != null) {
            sb.append(" in ").append(StepTarget.scopeOf(source.parent()));
        }
        return sb.toString();
    }

    private static String nameWords(Filter f) {
        String value = quote(f.value());
        return switch (f.kind()) {
            case LABEL -> f.exact() ? "labelled " + value : "with a label containing " + value;
            case TEXT -> f.exact() ? "with text " + value : "with text containing " + value;
            case ACCESSIBLE_NAME -> f.exact() ? "named " + value : "with an accessible name containing " + value;
            default -> "with " + StepTarget.words(f);
        };
    }

    // ---------------------------------------------------------------- Near Misses

    /**
     * How to read a candidate's name: an adapter filter's kind ({@code filterIndex} among the user's filters), or
     * a custom filter's getter ({@code withHeaderTitle} → {@code getHeaderTitle()}, {@code coreIndex} among the
     * core filters).
     */
    record Probe(Filter.Kind kind, Method getter, String wanted, int filterIndex, int coreIndex) {}

    static Probe probe(ElementSource source) {
        List<Filter> user = userFilters(source);
        for (Filter.Kind kind : NAMING) {
            for (int i = 0; i < user.size(); i++) {
                if (user.get(i).kind() == kind) {
                    return new Probe(kind, null, user.get(i).value(), i, -1);
                }
            }
        }
        List<FilterLabel> labels = source.coreFilterNames();
        for (int i = 0; i < labels.size(); i++) {
            Method getter = getter(source.wrapperType(), labels.get(i));
            if (getter != null) {
                return new Probe(null, getter, labels.get(i).args().get(0), -1, i);
            }
        }
        return null;
    }

    /** {@code withHeaderTitle(..)} → {@code String getHeaderTitle()} on the Wrapper, if it has one. */
    private static Method getter(Class<?> wrapperType, FilterLabel label) {
        if (wrapperType == null || label.args().size() != 1 || !label.method().startsWith("with")
                || label.method().length() <= 4) {
            return null;
        }
        try {
            Method getter = wrapperType.getMethod("get" + label.method().substring(4));
            return getter.getReturnType() == String.class && getter.isDefault() ? getter : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static List<Filter> userFilters(ElementSource source) {
        return source.filters().subList(source.selectorFilters(), source.filters().size());
    }

    /** A candidate's Wrapper type (null for the failed lookup's own), and its element. */
    private record Ref(Class<?> otherType, UnifiedElement element) {}

    private static FailureParts withNearMisses(FailureParts parts, LazyElement element) {
        if (element.source() instanceof ElementSource source) {
            safely(() -> addNearMisses(parts, source));
            safely(() -> addTemplateMatches(parts, element, source));
        }
        return parts;
    }

    private static void addNearMisses(FailureParts parts, ElementSource source) {
        Probe probe = probe(source);
        if (probe == null) {
            return;
        }
        Optional<UnifiedElement> scope = scope(source);
        if (scope == null) {
            return; // the scope is missing right now: its own lookup reports that
        }
        TestRun run = source.run();
        UnifiedElement parent = scope.orElse(null);
        boolean inTemplate = parent != null && ElementSource.entersTemplate(source.parent());
        List<UnifiedElement> found = run.session().find(new QuerySpec(source.componentType(), source.tag(),
                source.filters().subList(0, source.selectorFilters()), parent, inTemplate));
        found = found.subList(0, Math.min(found.size(), MAX_CANDIDATES));
        List<String> values = values(source, probe, found);
        String kind = StepTarget.kindOf(source);
        List<Candidate<Ref>> same = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            same.add(new Candidate<>(kind, values.get(i), false, new Ref(null, found.get(i))));
        }
        List<Candidate<Ref>> other = probe.kind() == Filter.Kind.LABEL || probe.kind() == Filter.Kind.TEXT
                || probe.kind() == Filter.Kind.ACCESSIBLE_NAME ? otherKinds(source, probe.wanted(), parent, inTemplate)
                : List.of();
        NearMisses.Result<Ref> result = NearMisses.rank(probe.wanted(), same, other);
        parts.nearMisses(result.shown().stream()
                .map(c -> new Candidate<>(c.kind(), c.value(), hidden(c.payload().element()), c.payload()).render())
                .toList());
        if (result.suggestion() != null) {
            parts.suggestion(suggestion(source, probe, result.suggestion()));
            parts.lookup(lookup(source, probe, probe.wanted()));
        }
    }

    /**
     * For a lookup that found none: the components it finds inside the Templates in its scope, as Near Misses with the
     * lookup that enters their Template (spec 12, "Failures"). The suggestion, when there is no other and only one
     * Template matches, is that lookup.
     */
    private static void addTemplateMatches(FailureParts parts, LazyElement element, ElementSource source) {
        Optional<UnifiedElement> scope = scope(source);
        if (scope == null) {
            return;
        }
        UnifiedElement parent = scope.orElse(null);
        boolean inTemplate = parent != null && ElementSource.entersTemplate(source.parent());
        List<Templates.Match> matches = Templates.matching(source, parent, inTemplate);
        if (matches.isEmpty()) {
            return;
        }
        String wanted = target(element.withSource(withoutParent(source)));
        String code = asCode(source);
        List<String> lines = new ArrayList<>(parts.nearMisses());
        for (Templates.Match m : matches) {
            lines.add((m.count() > 1 ? m.count() + " × " : "") + wanted + " in " + m.where() + ": " + m.chain() + "."
                    + code);
        }
        parts.nearMisses(lines);
        if (parts.suggestion() == null && matches.size() == 1 && matches.get(0).count() == 1
                && matches.get(0).known()) {
            parts.suggestion(matches.get(0).chain() + "." + code);
            parts.lookup(code);
        }
    }

    private static ElementSource withoutParent(ElementSource s) {
        return new ElementSource(s.run(), s.wrapperName(), s.wrapperType(), s.componentType(), s.tag(), s.filters(),
                s.selectorFilters(), s.coreFilters(), s.coreFilterNames(), null, s.template());
    }

    /** The lookup as the test wrote it, from {@code find(..)} on. */
    private static String asCode(ElementSource source) {
        StringBuilder sb = new StringBuilder(source.wrapperType() != null
                ? "find(" + source.wrapperType().getSimpleName() + ".class)"
                : source.wrapperName().replace(")", ".class)"));
        userFilters(source).forEach(f -> sb.append('.').append(f));
        source.coreFilterNames().forEach(n -> sb.append('.').append(n.display()));
        return sb.toString();
    }

    /** The scope's element: empty for the page, null when the scope can't be singled out right now. */
    private static Optional<UnifiedElement> scope(ElementSource source) {
        if (source.parent() == null) {
            return Optional.empty();
        }
        Optional<UnifiedElement> parent = source.parent().tryConcrete();
        return parent.isPresent() ? parent : null;
    }

    private static List<String> values(ElementSource source, Probe probe, List<UnifiedElement> elements) {
        List<String> values = new ArrayList<>();
        if (probe.kind() != null) {
            List<ElementFacts> facts = facts(source.run(), elements);
            facts.forEach(f -> values.add(f == null ? null : f.value(probe.kind())));
            return values;
        }
        TestRun quick = source.run().withTimeout(Duration.ZERO);
        for (UnifiedElement e : elements) {
            String value = null;
            try {
                Object wrapper = Wrappers.newWrapper(source.wrapperType(),
                        new LazyElement(fixed(e), -1, quick));
                value = (String) probe.getter().invoke(wrapper);
            } catch (InvocationTargetException | IllegalAccessException | RuntimeException | AssertionError ignored) {
                // left out
            }
            values.add(value);
        }
        return values;
    }

    /** The id Flow gives the element it renders the UI in: a new one on every page load, and above every view. */
    private static final java.util.regex.Pattern FLOW_ROOT = java.util.regex.Pattern.compile("ROOT-\\d+");

    /**
     * The adapter's facts for the elements, one each; read one by one when the adapter gives none. An ancestor
     * that is Flow's root ({@code id="ROOT-2521314"}, in a browser) counts as none: it holds every component, so
     * it tells none apart, and Browserless has no such element.
     */
    static List<ElementFacts> facts(TestRun run, List<UnifiedElement> elements) {
        List<ElementFacts> facts = null;
        try {
            facts = run.session().facts(elements);
        } catch (RuntimeException e) {
            // read one by one below
        }
        if (facts != null && facts.size() == elements.size()) {
            return facts.stream().map(LookupFailures::withoutFlowRoot).toList();
        }
        List<ElementFacts> each = new ArrayList<>();
        for (UnifiedElement e : elements) {
            ElementFacts f = null;
            try {
                f = ElementFacts.read(e);
            } catch (RuntimeException ignored) {
                // left out
            }
            each.add(f);
        }
        return each;
    }

    private static ElementFacts withoutFlowRoot(ElementFacts f) {
        if (f == null || f.ancestorId() == null || !FLOW_ROOT.matcher(f.ancestorId()).matches()) {
            return f;
        }
        return new ElementFacts(f.label(), f.text(), f.placeholder(), f.testId(), f.accessibleName(),
                f.ancestorTestId(), null, f.internalPart());
    }

    private static List<Candidate<Ref>> otherKinds(ElementSource source, String wanted, UnifiedElement parent,
            boolean inTemplate) {
        List<Candidate<Ref>> found = new ArrayList<>();
        for (Class<?> type : OTHER_KINDS) {
            UnifiedComponent annotation = type.getAnnotation(UnifiedComponent.class);
            String tag = Wrappers.tagOf(annotation.value(), annotation.tag());
            if (type == source.wrapperType() || tag == null || tag.equals(source.tag())) {
                continue;
            }
            List<Filter> filters = new ArrayList<>(Wrappers.typeFilters(type, annotation, tag));
            filters.add(new Filter(namingKind(type), null, wanted, true));
            try {
                for (UnifiedElement e : source.run().session().find(new QuerySpec(annotation.value(), tag,
                        List.copyOf(filters), parent, inTemplate))) {
                    found.add(new Candidate<>(StepTarget.kindOf(type), wanted, false, new Ref(type, e)));
                }
            } catch (RuntimeException e) {
                // this kind can't be searched by name on this Testing Tool
            }
            if (found.size() >= NearMisses.OTHER_KIND) {
                break;
            }
        }
        return found;
    }

    private static Filter.Kind namingKind(Class<?> type) {
        return type == UnifiedButton.class ? Filter.Kind.TEXT : Filter.Kind.LABEL;
    }

    private static boolean hidden(UnifiedElement e) {
        try {
            return !e.isVisible();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ---------------------------------------------------------------- suggestion

    /** {@code find(UnifiedTextField.class).withLabel("E-mail")}: the failed lookup with the Near Miss's name. */
    static String suggestion(ElementSource source, Probe probe, Candidate<Ref> best) {
        if (best.payload().otherType() != null) {
            Class<?> type = best.payload().otherType();
            return "find(" + type.getSimpleName() + ".class)." + (namingKind(type) == Filter.Kind.TEXT ? "withText"
                    : "withLabel") + "(" + javaString(best.value()) + ")";
        }
        return lookup(source, probe, best.value());
    }

    /** The lookup as code, from {@code find(..)} on, with {@code name} as the value of the probed filter. */
    static String lookup(ElementSource source, Probe probe, String name) {
        StringBuilder sb = new StringBuilder(source.wrapperType() != null
                ? "find(" + source.wrapperType().getSimpleName() + ".class)"
                : "findComponent(" + source.componentType().getSimpleName() + ".class)");
        List<Filter> user = userFilters(source);
        for (int i = 0; i < user.size(); i++) {
            Filter f = user.get(i);
            sb.append('.').append(i == probe.filterIndex()
                    ? new Filter(f.kind(), f.name(), name, f.exact()).toString().replace(
                            "\"" + name + "\"", javaString(name))
                    : f.toString());
        }
        List<FilterLabel> labels = source.coreFilterNames();
        for (int i = 0; i < labels.size(); i++) {
            sb.append('.').append(i == probe.coreIndex()
                    ? labels.get(i).method() + "(" + javaString(name) + ")"
                    : labels.get(i).display());
        }
        return sb.toString();
    }

    private static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------------------------------------------------------------- several matches

    /** {@code 2: text field “City” in container (test id “billing”), text field “City” in container (…)}. */
    private static String several(ElementSource source, int lastCount) {
        List<UnifiedElement> matches = source.resolveNow();
        if (matches.size() < 2) {
            return String.valueOf(lastCount);
        }
        Probe probe = probe(source);
        List<UnifiedElement> listed = matches.subList(0, Math.min(MAX_MATCHES, matches.size()));
        List<ElementFacts> facts = facts(source.run(), listed);
        String kind = StepTarget.kindOf(source);
        List<String> described = new ArrayList<>();
        for (ElementFacts f : facts) {
            StringBuilder sb = new StringBuilder(kind);
            String name = f == null ? null : probe != null && probe.kind() != null ? f.value(probe.kind())
                    : probe != null ? probe.wanted() : f.label() != null && !f.label().isBlank() ? f.label() : null;
            if (name != null && !name.isBlank()) {
                sb.append(' ').append(quote(name));
            }
            if (f != null && f.ancestorTestId() != null) {
                sb.append(" in container (test id ").append(quote(f.ancestorTestId())).append(')');
            } else if (f != null && f.ancestorId() != null) {
                sb.append(" in container (id ").append(quote(f.ancestorId())).append(')');
            }
            described.add(sb.toString());
        }
        return matches.size() + ": " + String.join(", ", described) + (matches.size() > listed.size() ? ", …" : "");
    }

    // ---------------------------------------------------------------- helpers

    private static ElementResolver fixed(UnifiedElement e) {
        return new ElementResolver() {
            @Override
            public List<UnifiedElement> resolve() {
                return List.of(e);
            }

            @Override
            public String describe() {
                return "a Near Miss";
            }
        };
    }

    private static String quote(String value) {
        return "“" + value + "”";
    }

    /** Runs a read for a failure message: a failing read leaves its part out. */
    private static void safely(Runnable read) {
        try {
            read.run();
        } catch (RuntimeException | AssertionError e) {
            // the failure is reported without this part
        }
    }
}
