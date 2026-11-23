package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RoutePrefix;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Route class to URL path, resolved statically from the annotations (spec 03-navigation): {@code @Route}
 * value or Flow's naming convention, prefixes of the parent layouts ({@code @RoutePrefix}), absolute routes.
 * For browser Testing Tools that have no route navigation of their own.
 */
public final class RoutePaths {
    private RoutePaths() {}

    private static final String NAMING_CONVENTION = "___NAMING_CONVENTION___";

    /** The path without leading slash, e.g. {@code "admin/users"}; {@code ""} for the root. */
    public static String pathOf(Class<? extends Component> view) {
        Route route = view.getAnnotation(Route.class);
        if (route == null) {
            throw new IllegalArgumentException(view.getName() + " has no @Route");
        }
        List<String> parts = new ArrayList<>();
        if (!route.absolute()) {
            parts.addAll(prefixes(route.layout()));
        }
        parts.add(NAMING_CONVENTION.equals(route.value()) ? conventionName(view) : route.value());
        return join(parts);
    }

    /**
     * The names of the route template's required parameters ({@code :id}, not {@code :id?} or {@code :id*}):
     * the view can't be opened without them.
     */
    public static List<String> requiredParameters(Class<? extends Component> view) {
        List<String> required = new ArrayList<>();
        for (String segment : segments(pathOf(view))) {
            if (segment.startsWith(":") && kind(segment) == ':') {
                required.add(parameterName(segment));
            }
        }
        return required;
    }

    /**
     * Whether {@code path} (without leading slash or query) is one the view's route or one of its
     * {@code @RouteAlias}es opens, with the parent layouts' prefixes: a required {@code :x} matches one segment, an
     * optional {@code :x?} zero or one, a wildcard {@code :x*} the remaining ones; a regex constraint is not checked.
     */
    public static boolean matches(Class<? extends Component> view, String path) {
        List<String> actual = segments(strip(path));
        for (String template : templates(view)) {
            if (matches(segments(template), 0, actual, 0)) {
                return true;
            }
        }
        return false;
    }

    /** The route's path template and its aliases', with the layouts' prefixes. */
    private static List<String> templates(Class<? extends Component> view) {
        List<String> templates = new ArrayList<>();
        templates.add(pathOf(view));
        for (RouteAlias alias : view.getAnnotationsByType(RouteAlias.class)) {
            List<String> parts = new ArrayList<>();
            if (!alias.absolute()) {
                parts.addAll(prefixes(alias.layout()));
            }
            parts.add(alias.value());
            templates.add(join(parts));
        }
        return templates;
    }

    private static boolean matches(List<String> template, int t, List<String> actual, int a) {
        if (t == template.size()) {
            return a == actual.size();
        }
        String segment = template.get(t);
        if (!segment.startsWith(":")) {
            return a < actual.size() && segment.equals(actual.get(a)) && matches(template, t + 1, actual, a + 1);
        }
        return switch (kind(segment)) {
            case '?' -> matches(template, t + 1, actual, a)
                    || a < actual.size() && matches(template, t + 1, actual, a + 1);
            case '*' -> {
                for (int rest = a; rest <= actual.size(); rest++) {
                    if (matches(template, t + 1, actual, rest)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> a < actual.size() && matches(template, t + 1, actual, a + 1);
        };
    }

    /** {@code '?'} for an optional parameter segment, {@code '*'} for a wildcard, {@code ':'} for a required one. */
    private static char kind(String segment) {
        String head = segment.replaceAll("\\(.*\\)$", "");
        if (head.endsWith("?")) {
            return '?';
        }
        return head.endsWith("*") ? '*' : ':';
    }

    private static String parameterName(String segment) {
        return segment.substring(1).replaceAll("[?*]?(\\(.*\\))?$", "").replaceAll("[?*]$", "");
    }

    private static List<String> segments(String path) {
        return path.isEmpty() ? List.of() : List.of(path.split("/"));
    }

    public static String pathOf(Class<? extends Component> view, Object urlParameter) {
        String path = pathOf(view);
        return urlParameter == null ? path : join(List.of(path, String.valueOf(urlParameter)));
    }

    /** Fills {@code :name} segments of the route template; missing optional ones are dropped. */
    public static String pathOf(Class<? extends Component> view, RouteParameters parameters) {
        List<String> segments = new ArrayList<>();
        for (String segment : pathOf(view).split("/")) {
            if (!segment.startsWith(":")) {
                segments.add(segment);
                continue;
            }
            parameters.get(parameterName(segment)).ifPresent(segments::add);
        }
        return join(segments);
    }

    /** {@link #pathOf(Class, RouteParameters)} with the query string, if any: {@code "plan?week=2026-11-02"}. */
    public static String pathOf(Class<? extends Component> view, RouteParameters parameters, QueryParameters query) {
        String path = pathOf(view, parameters);
        String queryString = query.getQueryString();
        return queryString.isEmpty() ? path : path + "?" + queryString;
    }

    private static List<String> prefixes(Class<?> layout) {
        List<String> prefixes = new ArrayList<>();
        for (Class<?> l = layout; l != null && l != com.vaadin.flow.component.UI.class; ) {
            RoutePrefix prefix = l.getAnnotation(RoutePrefix.class);
            if (prefix != null) {
                prefixes.add(0, prefix.value());
                if (prefix.absolute()) {
                    break;
                }
            }
            ParentLayout parent = l.getAnnotation(ParentLayout.class);
            l = parent == null ? null : parent.value();
        }
        return prefixes;
    }

    /** Flow's naming convention: the class name without a "View" suffix, lower case; "Main" is the root. */
    private static String conventionName(Class<?> view) {
        String name = view.getSimpleName();
        if (name.endsWith("View") && name.length() > 4) {
            name = name.substring(0, name.length() - 4);
        }
        return "Main".equals(name) ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String join(List<String> parts) {
        return String.join("/", parts.stream().map(RoutePaths::strip).filter(p -> !p.isEmpty()).toList());
    }

    private static String strip(String path) {
        String p = path == null ? "" : path;
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
