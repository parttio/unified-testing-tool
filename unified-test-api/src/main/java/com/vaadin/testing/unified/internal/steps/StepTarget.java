package com.vaadin.testing.unified.internal.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.internal.ElementResolver;
import com.vaadin.testing.unified.internal.ElementSource;
import com.vaadin.testing.unified.internal.ElementSource.FilterLabel;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.spi.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a User Step acts on or checks, in user words: {@code text field “Name” (test id “billing”)}, with its
 * scope {@code dialog “Edit person”}.
 *
 * @param kind the component kind: the Wrapper type in plain words ("combo box")
 * @param name the main filter's value (label, text, accessible name, or a naming custom filter), or null
 * @param filters the other filters, each in words ({@code test id “billing”})
 * @param where true when {@code filters} read as a condition ({@code row where Name is “Alan Turing”}) rather
 *        than in brackets
 * @param qualifier words after the filters, e.g. {@code of row 2}, or null
 * @param position the 1-based position from {@code all()}, or 0
 * @param scope the containers, innermost first, rendered ({@code dialog “Edit person”}), or null for the page
 * @param secret whether values typed into it are hidden (a password field)
 */
public record StepTarget(String kind, String name, List<String> filters, boolean where, String qualifier,
        int position, String scope, boolean secret) {

    /** Kinds that read better than the Wrapper's split name. */
    private static final Map<String, String> KINDS = Map.of(
            "UnifiedMultiSelectComboBox", "multi-select combo box",
            "UnifiedDateTimePicker", "date-time picker",
            "UnifiedBigDecimalField", "decimal field");

    /** Last words of a custom filter's name that make it the main filter, e.g. {@code withHeaderTitle}. */
    private static final Set<String> NAMING_WORDS = Set.of("title", "text", "name", "label", "caption");

    private static final String PASSWORD_FIELD = "com.vaadin.flow.component.textfield.PasswordField";

    public static StepTarget of(String kind) {
        return new StepTarget(kind, null, List.of(), false, null, 0, null, false);
    }

    public StepTarget withName(String newName) {
        return new StepTarget(kind, newName, filters, where, qualifier, position, scope, secret);
    }

    public StepTarget withQualifier(String newQualifier) {
        return new StepTarget(kind, name, filters, where, newQualifier, position, scope, secret);
    }

    public StepTarget withScope(String newScope) {
        return new StepTarget(kind, name, filters, where, qualifier, position, newScope, secret);
    }

    public StepTarget withFilters(List<String> newFilters, boolean asCondition) {
        return new StepTarget(kind, name, List.copyOf(newFilters), asCondition, qualifier, position, scope, secret);
    }

    // ---------------------------------------------------------------- from a lookup

    /** The target behind a Wrapper's lazy element. */
    public static StepTarget of(LazyElement element) {
        ElementResolver resolver = element.source();
        if (resolver instanceof ElementSource source) {
            StepTarget target = of(source);
            return element.index() < 0 ? target
                    : new StepTarget(target.kind, target.name, target.filters, target.where, target.qualifier,
                            element.index() + 1, target.scope, target.secret);
        }
        StepTarget own = resolver.userTarget();
        return own != null ? own : of(resolver.describe());
    }

    static StepTarget of(ElementSource source) {
        String name = null;
        List<String> filters = new ArrayList<>();
        List<Filter> userFilters = source.filters().subList(source.selectorFilters(), source.filters().size());
        Filter main = mainFilter(userFilters);
        if (main != null) {
            name = main.value();
        }
        for (Filter f : userFilters) {
            if (f != main) {
                filters.add(words(f));
            }
        }
        for (FilterLabel label : source.coreFilterNames()) {
            if (name == null && main == null && isNaming(label)) {
                name = label.args().get(0);
                continue;
            }
            filters.add(words(label));
        }
        String scope = source.parent() == null ? null : scopeOf(source.parent());
        return new StepTarget(kindOf(source), name, List.copyOf(filters), false, null, 0, scope, isSecret(source));
    }

    /** A container, as the scope of what is inside it: {@code dialog “Edit person” in details “Billing”}. */
    public static String scopeOf(LazyElement container) {
        return of(container).renderWithScope();
    }

    private static Filter mainFilter(List<Filter> filters) {
        for (Filter.Kind kind : List.of(Filter.Kind.LABEL, Filter.Kind.TEXT, Filter.Kind.ACCESSIBLE_NAME)) {
            for (Filter f : filters) {
                if (f.kind() == kind && f.exact()) {
                    return f;
                }
            }
        }
        return null;
    }

    private static boolean isNaming(FilterLabel label) {
        if (label.args().size() != 1 || !label.method().startsWith("with")) {
            return false;
        }
        List<String> words = splitWords(label.method().substring(4));
        return !words.isEmpty() && NAMING_WORDS.contains(words.get(words.size() - 1).toLowerCase(Locale.ROOT));
    }

    public static String words(Filter f) {
        String what = switch (f.kind()) {
            case LABEL -> "label";
            case ACCESSIBLE_NAME -> "accessible name";
            case TEXT -> "text";
            case PLACEHOLDER -> "placeholder";
            case TEST_ID -> "test id";
            case ID -> "id";
            case CLASS_NAME -> "class name";
            case THEME -> "theme";
            // UnifiedLink.withHref(..): where the link goes reads as such
            case ATTRIBUTE -> "href".equals(f.name()) ? "href" : "attribute " + f.name();
        };
        return what + (f.exact() ? " " : " containing ") + Phrase.quote(f.value());
    }

    public static String words(FilterLabel label) {
        return switch (label.method()) {
            case "where" -> "matching a condition";
            case "visible" -> "visible";
            case "withProperty" -> "property " + label.args().get(0) + " " + Phrase.quote(label.args().get(1));
            default -> {
                String method = label.method().startsWith("with") && label.method().length() > 4
                        ? label.method().substring(4) : label.method();
                StringBuilder sb = new StringBuilder(String.join(" ", splitWords(method)).toLowerCase(Locale.ROOT));
                label.args().forEach(a -> sb.append(' ').append(Phrase.quote(a)));
                yield sb.toString();
            }
        };
    }

    // ---------------------------------------------------------------- kind

    public static String kindOf(ElementSource source) {
        Class<?> wrapperType = source.wrapperType();
        if (wrapperType == null) {
            return kindOf(source.componentType());
        }
        UnifiedComponent annotation = wrapperType.getAnnotation(UnifiedComponent.class);
        if (annotation != null && !annotation.kind().isBlank()) {
            return annotation.kind();
        }
        return kindOf(wrapperType);
    }

    /** {@code UnifiedComboBox} → "combo box", {@code FormLayout} → "form layout". */
    public static String kindOf(Class<?> type) {
        String known = KINDS.get(type.getSimpleName());
        if (known != null) {
            return known;
        }
        String simple = type.getSimpleName();
        if (simple.startsWith("Unified") && simple.length() > "Unified".length()) {
            simple = simple.substring("Unified".length());
        }
        return String.join(" ", splitWords(simple).stream().map(StepTarget::lowerUnlessAcronym).toList());
    }

    private static String lowerUnlessAcronym(String word) {
        return word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT)) ? word : word.toLowerCase(Locale.ROOT);
    }

    /** {@code MultiSelectComboBox} → [Multi, Select, Combo, Box]; {@code HTMLView} → [HTML, View]. */
    public static List<String> splitWords(String camel) {
        List<String> words = new ArrayList<>();
        for (String w : camel.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_\\s]+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        return words;
    }

    private static boolean isSecret(ElementSource source) {
        if ("vaadin-password-field".equals(source.tag())) {
            return true;
        }
        for (Class<?> c = source.componentType(); c != null && c != Component.class; c = c.getSuperclass()) {
            if (c.getName().equals(PASSWORD_FIELD)) {
                return true;
            }
        }
        return source.wrapperType() != null
                && source.wrapperType().getSimpleName().toLowerCase(Locale.ROOT).contains("password");
    }

    // ---------------------------------------------------------------- rendering

    /** {@code the 2nd text field “Name” (test id “billing”)}, without the scope. */
    public String render(boolean plural) {
        StringBuilder sb = new StringBuilder();
        if (position > 0) {
            sb.append("the ").append(ordinal(position)).append(' ');
        }
        sb.append(plural ? plural(kind) : kind);
        if (name != null) {
            sb.append(' ').append(Phrase.quote(name));
        }
        if (!filters.isEmpty()) {
            sb.append(where ? " where " + String.join(" and ", filters) : " (" + String.join(", ", filters) + ")");
        }
        if (qualifier != null) {
            sb.append(' ').append(qualifier);
        }
        return sb.toString();
    }

    /** {@link #render} followed by {@code in <scope>}. */
    public String renderWithScope() {
        return render(false) + (scope == null ? "" : " in " + scope);
    }

    public static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    static String plural(String kind) {
        if (kind.endsWith("s")) {
            return kind; // "details", "tabs"
        }
        if (kind.endsWith("x") || kind.endsWith("ch") || kind.endsWith("sh")) {
            return kind + "es";
        }
        if (kind.endsWith("y") && kind.length() > 1 && "aeiou".indexOf(kind.charAt(kind.length() - 2)) < 0) {
            return kind.substring(0, kind.length() - 1) + "ies";
        }
        return kind + "s";
    }
}
