package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.TemplateScope;
import com.vaadin.testing.unified.component.UnifiedTemplate;
import com.vaadin.flow.component.littemplate.LitTemplate;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.internal.steps.Phrases;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import com.vaadin.testing.unified.query.TextFilter;
import com.vaadin.testing.unified.spi.Filter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Implements Wrapper interfaces at runtime (ADR 0003): their default methods, and the filters their mixins
 * declare (ADR 0007). A filter returns a new Wrapper over the lookup with one filter more.
 */
public final class Wrappers {
    private Wrappers() {}

    /** The filter mixins: their abstract methods are implemented here, by name. */
    /** The gap reason for a test's own {@code where(..)} condition. */
    static final String WHERE = "where(..) condition";

    private static final Set<Class<?>> FILTER_MIXINS = Set.of(CommonFilters.class, LabelFilter.class,
            TextFilter.class, PlaceholderFilter.class);

    // ---------------------------------------------------------------- entry points

    /** {@code find(wrapperType)}: the Wrapper over a lookup without user filters. */
    public static <W> W newWrapper(TestRun run, Class<W> wrapperType, LazyElement parent) {
        UnifiedComponent annotation = wrapperType.getAnnotation(UnifiedComponent.class);
        if (annotation == null) {
            throw new IllegalArgumentException(wrapperType.getName() + " has no @UnifiedComponent annotation");
        }
        if (!CommonFilters.class.isAssignableFrom(wrapperType)) {
            throw new IllegalArgumentException(wrapperType.getName() + " must extend CommonFilters<"
                    + wrapperType.getSimpleName() + "> to be found");
        }
        for (Class<?> c = wrapperType; c != null; c = c.getEnclosingClass()) {
            if (!java.lang.reflect.Modifier.isPublic(c.getModifiers())) {
                throw new IllegalArgumentException(wrapperType.getName() + " must be public, and so must the classes"
                        + " it is nested in: the Unified API implements it at runtime");
            }
        }
        String tag = requireTag(annotation.value(), annotation.tag(), wrapperType.getSimpleName());
        requireBrowserInside(run, parent);
        run.steps().wrapperUsed(wrapperType);
        List<Filter> filters = annotation.selector().isBlank() ? List.of()
                : WrapperSelector.filters(annotation.selector(), wrapperType.getName());
        ElementSource source = new ElementSource(run, wrapperType.getSimpleName(), wrapperType, annotation.value(),
                tag, filters, filters.size(), List.of(), List.of(), parent,
                TemplateScope.class.isAssignableFrom(wrapperType));
        return wrapperType.cast(newWrapper(wrapperType, new LazyElement(source, -1, run)));
    }

    /** {@code findComponent(componentType)}: a lazy {@link UnifiedGenericComponent} with the shared filters. */
    public static UnifiedGenericComponent newComponent(TestRun run, Class<? extends Component> componentType,
            LazyElement parent) {
        String name = "findComponent(" + componentType.getSimpleName() + ")";
        requireBrowserInside(run, parent);
        ElementSource source = new ElementSource(run, name, null, componentType,
                requireTag(componentType, "", name), List.of(), 0, List.of(), List.of(), parent, false);
        return (UnifiedGenericComponent) newWrapper(UnifiedGenericComponent.class, new LazyElement(source, -1, run));
    }

    /** {@code findTemplate(templateType)}: a lazy {@link UnifiedTemplate}, whose lookups search inside it. */
    public static UnifiedTemplate newTemplate(TestRun run, Class<? extends Component> templateType,
            LazyElement parent) {
        String name = "findTemplate(" + templateType.getSimpleName() + ")";
        String tag = requireTag(templateType, "", name);
        requireBrowserInside(run, parent);
        ElementSource source = new ElementSource(run, name, null, templateType, tag, List.of(), 0, List.of(),
                List.of(), parent, true);
        return (UnifiedTemplate) newWrapper(UnifiedTemplate.class, new LazyElement(source, -1, run));
    }

    /**
     * A lookup inside a Template that is not a {@code LitTemplate}: that Template renders its content in the browser
     * only, so Browserless has nothing to find inside it, and the lookup needs a real browser (spec 09).
     */
    private static void requireBrowserInside(TestRun run, LazyElement parent) {
        if (ElementSource.entersTemplate(parent) && parent.source() instanceof ElementSource template
                && !LitTemplate.class.isAssignableFrom(template.componentType())) {
            run.requireBrowser("A lookup inside " + template.describe() + ", a Template that renders its content in"
                    + " the browser,");
        }
    }

    /** The lazy element behind a wrapper created by this class. */
    public static LazyElement lazyElementOf(UnifiedWrapper wrapper) {
        if (Proxy.isProxyClass(wrapper.getClass())
                && Proxy.getInvocationHandler(wrapper) instanceof WrapperHandler handler) {
            return handler.element;
        }
        if (wrapper.element() instanceof LazyElement lazy) {
            return lazy;
        }
        throw new IllegalArgumentException("Not a Unified API wrapper: " + wrapper);
    }

    /**
     * The tag, or a failure on every Testing Tool when there is none: Browserless could find the component by
     * class, but a browser can't, and the test must not pass on one and fail on the other.
     */
    static String requireTag(Class<? extends Component> componentType, String explicitTag, String where) {
        String tag = tagOf(componentType, explicitTag);
        if (tag == null) {
            throw new IllegalArgumentException(where + ": " + componentType.getName() + " has no @Tag, so a browser"
                    + " can't find it. Write a wrapper with @UnifiedComponent(value = " + componentType.getSimpleName()
                    + ".class, tag = \"...\") naming the element it renders (for a Composite: its content's tag), or"
                    + " search with UnifiedContainer.");
        }
        return tag;
    }

    static String tagOf(Class<? extends Component> componentType, String explicitTag) {
        if (!explicitTag.isEmpty()) {
            return explicitTag;
        }
        Tag tag = componentType.getAnnotation(Tag.class);
        return tag == null ? null : tag.value();
    }

    // ---------------------------------------------------------------- wrappers

    static Object newWrapper(Class<?> wrapperType, LazyElement element) {
        Class<?> implemented = implementationFor(wrapperType, element.run().tool());
        return Proxy.newProxyInstance(implemented.getClassLoader(), new Class<?>[] { implemented },
                new WrapperHandler(wrapperType, element));
    }

    /**
     * The wrapper interface itself, or its {@code @ToolImplementation} override for this Testing Tool: an
     * interface extending the wrapper that redefines the methods needing the tool's own API.
     */
    static Class<?> implementationFor(Class<?> wrapperType, TestingTool tool) {
        UnifiedComponent annotation = wrapperType.getAnnotation(UnifiedComponent.class);
        if (annotation == null) {
            return wrapperType;
        }
        for (ToolImplementation override : annotation.overrides()) {
            if (override.tool() != tool) {
                continue;
            }
            Class<?> type = override.value();
            if (!type.isInterface() || !wrapperType.isAssignableFrom(type)) {
                throw new IllegalArgumentException("@ToolImplementation(" + type.getName() + ") on "
                        + wrapperType.getName() + ": must be an interface extending " + wrapperType.getSimpleName());
            }
            return type;
        }
        return wrapperType;
    }

    private record WrapperHandler(Class<?> wrapperType, LazyElement element) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "element":
                    return element;
                case "unwrap":
                    return element.unwrap((Class<?>) args[0]);
                case "tryUnwrap":
                    return element.tryUnwrap((Class<?>) args[0]);
                case "withTimeout":
                    return newWrapper(wrapperType, element.withRun(element.run().withTimeout((Duration) args[0])));
                case "find":
                    if (args != null && args.length == 1 && args[0] instanceof Class<?> type) {
                        return newWrapper(element.run(), type, element);
                    }
                    break;
                case "findComponent":
                    if (args != null && args.length == 1 && args[0] instanceof Class<?> type) {
                        @SuppressWarnings("unchecked")
                        Class<? extends Component> componentType = (Class<? extends Component>) type;
                        return newComponent(element.run(), componentType, element);
                    }
                    break;
                case "findTemplate":
                    if (args != null && args.length == 1 && args[0] instanceof Class<?> type) {
                        @SuppressWarnings("unchecked")
                        Class<? extends Component> templateType = (Class<? extends Component>) type;
                        return newTemplate(element.run(), templateType, element);
                    }
                    break;
                case "describe":
                    if (args == null && method.getDeclaringClass() == Scope.class) {
                        return FindableView.ofScope(element);
                    }
                    break;
                case "toString":
                    return wrapperType.getSimpleName() + "[" + element.describe() + "]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            if (method.isDefault()) {
                TestRun run = element.run();
                boolean voidAction = method.getReturnType() == void.class && !Phrases.isGetterName(method.getName());
                Object result = run.steps().wrapperCall(
                        () -> Phrases.forWrapperCall(wrapperType, element, method, args),
                        () -> Phrases.read(element), voidAction, run.window(), () -> {
                            try {
                                return InvocationHandler.invokeDefault(proxy, method, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        });
                return named(method, args, result);
            }
            if (FILTER_MIXINS.contains(method.getDeclaringClass())) {
                return filter(method, args);
            }
            throw new UnsupportedOperationException(wrapperType.getSimpleName() + "." + method.getName()
                    + " has no default implementation (the wrapper must implement it on element())");
        }

        /**
         * A custom filter, i.e. a default method returning a Wrapper with one {@code where(..)} more, reads in
         * failures and reports as itself: {@code withHeaderTitle("Edit person")}, not {@code where(..)}.
         */
        private Object named(Method method, Object[] args, Object result) {
            if (result == null || !Proxy.isProxyClass(result.getClass())
                    || !(Proxy.getInvocationHandler(result) instanceof WrapperHandler next)
                    || !(element.source() instanceof ElementSource mine)
                    || !(next.element.source() instanceof ElementSource theirs)
                    || theirs.coreFilterNames().size() != mine.coreFilterNames().size() + 1
                    || !theirs.coreFilterNames().get(theirs.coreFilterNames().size() - 1).isOpaque()) {
                return result;
            }
            List<String> values = Phrases.values(args);
            String display = method.getName() + "(" + String.join(", ", values.stream()
                    .map(v -> "\"" + v + "\"").toList()) + ")";
            ElementSource renamed = theirs.withLastCoreFilterNamed(
                    new ElementSource.FilterLabel(display, method.getName(), values));
            return newWrapper(next.wrapperType, next.element.withSource(renamed));
        }

        private ElementSource lookup(String method) {
            if (element.source() instanceof ElementSource source) {
                return source;
            }
            throw new UnsupportedOperationException(method + " needs a Wrapper from find(..) or findComponent(..);"
                    + " this one is " + element.describe());
        }

        /** A filter returns a new Wrapper; {@code all()} the matches by position. */
        private Object filter(Method method, Object[] args) {
            String name = method.getName();
            ElementSource source = lookup(name + "(..)");
            if (name.equals("all")) {
                int count = source.resolve().size();
                List<Object> wrappers = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    wrappers.add(newWrapper(wrapperType, new LazyElement(source, i, element.run())));
                }
                return List.copyOf(wrappers);
            }
            ElementSource next = switch (name) {
                case "withLabel" -> source.with(new Filter(Filter.Kind.LABEL, null, (String) args[0], true));
                case "withLabelContaining" -> source.with(new Filter(Filter.Kind.LABEL, null, (String) args[0], false));
                case "withAccessibleName" -> source.with(new Filter(Filter.Kind.ACCESSIBLE_NAME, null, (String) args[0], true));
                case "withAccessibleNameContaining" -> source.with(new Filter(Filter.Kind.ACCESSIBLE_NAME, null, (String) args[0], false));
                case "withText" -> source.with(new Filter(Filter.Kind.TEXT, null, (String) args[0], true));
                case "withTextContaining" -> source.with(new Filter(Filter.Kind.TEXT, null, (String) args[0], false));
                case "withPlaceholder" -> source.with(new Filter(Filter.Kind.PLACEHOLDER, null, (String) args[0], true));
                case "withTestId" -> source.with(new Filter(Filter.Kind.TEST_ID, null, (String) args[0], true));
                case "withId" -> source.with(new Filter(Filter.Kind.ID, null, (String) args[0], true));
                case "withClassName" -> source.with(classNameFilter((String) args[0]));
                case "withClassNames" -> {
                    ElementSource narrowed = source;
                    for (String className : classNames((String[]) args[0])) {
                        narrowed = narrowed.with(classNameFilter(className));
                    }
                    yield narrowed;
                }
                case "withTheme" -> source.with(new Filter(Filter.Kind.THEME, null, (String) args[0], true));
                case "withAttribute" -> source.with(new Filter(Filter.Kind.ATTRIBUTE, (String) args[0], (String) args[1], true));
                case "withProperty" -> {
                    String property = (String) args[0];
                    Object expected = args[1];
                    yield source.with(e -> Objects.equals(String.valueOf(expected), e.getProperty(property)),
                            new ElementSource.FilterLabel("withProperty(\"" + property + "\", " + expected + ")",
                                    "withProperty", List.of(property, String.valueOf(expected))));
                }
                case "visible" -> source.with(UnifiedElement::isVisible,
                        new ElementSource.FilterLabel("visible()", "visible", List.of()));
                case "where" -> {
                    @SuppressWarnings("unchecked")
                    Predicate<UnifiedElement> condition = (Predicate<UnifiedElement>) args[0];
                    // called by the test itself, not by a custom filter: a condition the reports can't read
                    element.run().steps().gap(WHERE, element.run().window());
                    yield source.with(condition, ElementSource.FilterLabel.opaque());
                }
                default -> throw new UnsupportedOperationException(
                        wrapperType.getSimpleName() + "." + name + " is not implemented");
            };
            return newWrapper(wrapperType, element.withSource(next));
        }

        /** One class name, as Flow's {@code addClassName(..)} takes: none of the tools can match "a b" as one. */
        private static Filter classNameFilter(String className) {
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("Class name cannot be empty");
            }
            if (className.strip().matches(".*\\s.*")) {
                throw new IllegalArgumentException("Class name cannot contain spaces: \"" + className
                        + "\"; use withClassNames(..) to require several");
            }
            return new Filter(Filter.Kind.CLASS_NAME, null, className.strip(), true);
        }

        /** The names in each argument, split on whitespace, as Flow's {@code addClassNames(..)} does. */
        private static List<String> classNames(String[] classNames) {
            List<String> names = new ArrayList<>();
            for (String arg : classNames) {
                if (arg != null) {
                    for (String name : arg.strip().split("\\s+")) {
                        if (!name.isEmpty()) {
                            names.add(name);
                        }
                    }
                }
            }
            if (names.isEmpty()) {
                throw new IllegalArgumentException("withClassNames(..) needs at least one class name");
            }
            return names;
        }
    }
}
