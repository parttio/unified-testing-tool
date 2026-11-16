package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
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
        List<Filter> filters = annotation.selector().isBlank() ? List.of()
                : WrapperSelector.filters(annotation.selector(), wrapperType.getName());
        ElementSource source = new ElementSource(run, wrapperType.getSimpleName(), annotation.value(), tag,
                filters, List.of(), List.of(), parent);
        return wrapperType.cast(newWrapper(wrapperType, new LazyElement(source, -1, run)));
    }

    /** {@code findComponent(componentType)}: a lazy {@link UnifiedGenericComponent} with the shared filters. */
    public static UnifiedGenericComponent newComponent(TestRun run, Class<? extends Component> componentType,
            LazyElement parent) {
        String name = "findComponent(" + componentType.getSimpleName() + ")";
        ElementSource source = new ElementSource(run, name, componentType,
                requireTag(componentType, "", name), List.of(), List.of(), List.of(), parent);
        return (UnifiedGenericComponent) newWrapper(UnifiedGenericComponent.class, new LazyElement(source, -1, run));
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
                try {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            if (FILTER_MIXINS.contains(method.getDeclaringClass())) {
                return filter(method, args);
            }
            throw new UnsupportedOperationException(wrapperType.getSimpleName() + "." + method.getName()
                    + " has no default implementation (the wrapper must implement it on element())");
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
                case "withClassName" -> source.with(new Filter(Filter.Kind.CLASS_NAME, null, (String) args[0], true));
                case "withTheme" -> source.with(new Filter(Filter.Kind.THEME, null, (String) args[0], true));
                case "withAttribute" -> source.with(new Filter(Filter.Kind.ATTRIBUTE, (String) args[0], (String) args[1], true));
                case "withProperty" -> {
                    String property = (String) args[0];
                    Object expected = args[1];
                    yield source.with(e -> Objects.equals(String.valueOf(expected), e.getProperty(property)),
                            "withProperty(\"" + property + "\", " + expected + ")");
                }
                case "visible" -> source.with(UnifiedElement::isVisible, "visible()");
                case "where" -> {
                    @SuppressWarnings("unchecked")
                    Predicate<UnifiedElement> condition = (Predicate<UnifiedElement>) args[0];
                    yield source.with(condition, "where(..)");
                }
                default -> throw new UnsupportedOperationException(
                        wrapperType.getSimpleName() + "." + name + " is not implemented");
            };
            return newWrapper(wrapperType, element.withSource(next));
        }
    }
}
