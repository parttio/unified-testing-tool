package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An immutable description of what to find: component type, tag, adapter filters, core filters (property,
 * predicate) and the parent scope. {@link #resolve} returns the matching adapter elements right now.
 * {@code wrapperType} is null for {@code findComponent(..)}; the first {@code selectorFilters} filters come from
 * {@code @UnifiedComponent(selector)} and are not the user's.
 */
public record ElementSource(TestRun run, String wrapperName, Class<?> wrapperType,
        Class<? extends Component> componentType, String tag, List<Filter> filters, int selectorFilters,
        List<Predicate<UnifiedElement>> coreFilters, List<FilterLabel> coreFilterNames, LazyElement parent)
        implements ElementResolver {

    /**
     * How a core filter reads: {@code display} in API terms ({@code withHeaderTitle("Edit person")}), and the
     * method and arguments for the User Step reports. {@code where(..)} has no arguments: it is opaque.
     */
    public record FilterLabel(String display, String method, List<String> args) {
        public static FilterLabel opaque() {
            return new FilterLabel("where(..)", "where", List.of());
        }

        public boolean isOpaque() {
            return method.equals("where");
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public ElementSource with(Filter filter) {
        List<Filter> copy = new ArrayList<>(filters);
        copy.add(filter);
        return new ElementSource(run, wrapperName, wrapperType, componentType, tag, List.copyOf(copy),
                selectorFilters, coreFilters, coreFilterNames, parent);
    }

    public ElementSource with(Predicate<UnifiedElement> filter, FilterLabel label) {
        List<Predicate<UnifiedElement>> copy = new ArrayList<>(coreFilters);
        copy.add(filter);
        List<FilterLabel> names = new ArrayList<>(coreFilterNames);
        names.add(label);
        return new ElementSource(run, wrapperName, wrapperType, componentType, tag, filters, selectorFilters,
                List.copyOf(copy), List.copyOf(names), parent);
    }

    /** The same lookup with its last core filter named, e.g. a custom filter built from {@code where(..)}. */
    public ElementSource withLastCoreFilterNamed(FilterLabel label) {
        List<FilterLabel> names = new ArrayList<>(coreFilterNames);
        names.set(names.size() - 1, label);
        return new ElementSource(run, wrapperName, wrapperType, componentType, tag, filters, selectorFilters,
                coreFilters, List.copyOf(names), parent);
    }

    /** Matching adapter elements now. Waits for the parent scope (if any) to exist. */
    public List<UnifiedElement> resolve() {
        return StaleRetry.call(run, () -> {
            UnifiedElement parentElement = parent == null ? null : parent.concrete();
            return filter(run.session().find(new QuerySpec(componentType, tag, filters, parentElement)));
        });
    }

    /**
     * Matching adapter elements now, without waiting for the parent scope: none when the parent can't be
     * singled out right now. For the retrying assertions on counts and absence.
     */
    public List<UnifiedElement> resolveNow() {
        return StaleRetry.call(run, () -> {
            UnifiedElement parentElement = null;
            if (parent != null) {
                Optional<UnifiedElement> found = parent.tryConcrete();
                if (found.isEmpty()) {
                    return List.of();
                }
                parentElement = found.get();
            }
            return filter(run.session().find(new QuerySpec(componentType, tag, filters, parentElement)));
        });
    }

    private List<UnifiedElement> filter(List<UnifiedElement> found) {
        if (coreFilters.isEmpty()) {
            return found;
        }
        return found.stream().filter(e -> coreFilters.stream().allMatch(f -> f.test(e))).toList();
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(wrapperName);
        filters.forEach(f -> sb.append('.').append(f));
        coreFilterNames.forEach(n -> sb.append('.').append(n));
        if (parent != null) {
            sb.append(" inside ").append(parent.describe());
        }
        return sb.toString();
    }
}
