package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.TemplateScope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Templates in failure messages and the Findable View (spec 04, "Templates"): which are in a scope, and the lookup
 * that enters one. A browser knows a Template by its tag only, so the lookup names the Wrapper or class the test
 * has looked up for that tag, the same on every Testing Tool, and a placeholder naming the tag otherwise.
 */
final class Templates {
    private Templates() {}

    /** At most this many Templates are searched in one scope, and this many levels of Templates in Templates. */
    private static final int MAX_PER_SCOPE = 20;
    private static final int MAX_DEPTH = 3;

    /** A Template in a scope, and how a lookup in that scope enters it. */
    record Found(UnifiedElement element, String tag, String enter, boolean known) {}

    /** The Templates in the scope, not entering them; none when the Testing Tool can't list them. */
    static List<Found> in(TestRun run, UnifiedElement scope, boolean inTemplate) {
        List<Found> found = new ArrayList<>();
        try {
            for (UnifiedElement e : run.session().findTemplates(scope, inTemplate)) {
                if (found.size() >= MAX_PER_SCOPE) {
                    break;
                }
                String tag = e.getTag();
                String known = enter(run, tag);
                found.add(new Found(e, tag, known != null ? known
                        : "findTemplate(<the class with @Tag(\"" + tag + "\")>.class)", known != null));
            }
        } catch (RuntimeException e) {
            // this Testing Tool can't list Templates right now
        }
        return found;
    }

    /** The lookup that enters a Template of this tag, from the Wrappers and classes the test used; or null. */
    static String enter(TestRun run, String tag) {
        for (Class<?> type : run.steps().wrapperTypes()) {
            UnifiedComponent annotation = type.getAnnotation(UnifiedComponent.class);
            if (TemplateScope.class.isAssignableFrom(type) && annotation != null
                    && tag.equals(Wrappers.tagOf(annotation.value(), annotation.tag()))) {
                return "find(" + type.getSimpleName() + ".class)";
            }
        }
        for (Class<?> type : run.steps().templateTypes()) {
            @SuppressWarnings("unchecked")
            Class<? extends Component> component = (Class<? extends Component>) type;
            if (tag.equals(Wrappers.tagOf(component, ""))) {
                return "findTemplate(" + type.getSimpleName() + ".class)";
            }
        }
        return null;
    }

    /** A Template, inside the Templates {@code path} names, in which a lookup found components. */
    record Match(List<Found> path, int count) {
        String chain() {
            return String.join(".", path.stream().map(Found::enter).toList());
        }

        boolean known() {
            return path.stream().allMatch(Found::known);
        }

        String where() {
            List<String> tags = new ArrayList<>();
            for (int i = path.size() - 1; i >= 0; i--) {
                tags.add("Template <" + path.get(i).tag() + ">");
            }
            return String.join(" in ", tags);
        }
    }

    /**
     * The Templates in the scope, and in Templates inside them, in which the lookup finds components: for a lookup
     * that found none, which a test enters a Template to find.
     */
    static List<Match> matching(ElementSource source, UnifiedElement scope, boolean inTemplate) {
        List<Match> matches = new ArrayList<>();
        search(source, scope, inTemplate, List.of(), matches);
        return matches;
    }

    private static void search(ElementSource source, UnifiedElement scope, boolean inTemplate, List<Found> path,
            List<Match> matches) {
        if (path.size() >= MAX_DEPTH) {
            return;
        }
        for (Found template : in(source.run(), scope, inTemplate)) {
            List<Found> inner = new ArrayList<>(path);
            inner.add(template);
            List<UnifiedElement> found = source.run().session().find(new QuerySpec(source.componentType(),
                    source.tag(), source.filters(), template.element(), true));
            long count = found.stream().filter(e -> source.coreFilters().stream().allMatch(f -> f.test(e))).count();
            if (count > 0) {
                matches.add(new Match(List.copyOf(inner), (int) count));
            }
            search(source, template.element(), true, inner, matches);
        }
    }
}
