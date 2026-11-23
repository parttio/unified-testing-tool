package com.vaadin.testing.unified.browserless;

import com.vaadin.browserless.ComponentTester;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Unified Element on Browserless: reads the server-side element and component; user actions go through
 * the component's tester (events fire as from the browser), then a round-trip.
 */
final class BrowserlessElement implements UnifiedElement {

    private final BrowserlessSession session;
    private final Component component;
    private final BrowserlessComponents.Support support;
    private final List<Component> scopeRoots;
    private final boolean rootsIncluded;

    /**
     * A part whose component is its own content, such as a grid cell's renderer component or a tab sheet's
     * selected content: a search inside it can find the component itself, as a browser finds it inside the
     * cell or the panel that holds it.
     */
    BrowserlessElement(BrowserlessSession session, Component component) {
        this(session, component, List.of(component), true);
    }

    /** A part whose content is several components (a grid column's header cells, a Details' content). */
    BrowserlessElement(BrowserlessSession session, Component component, List<Component> scopeRoots) {
        this(session, component, scopeRoots, true);
    }

    private BrowserlessElement(BrowserlessSession session, Component component, List<Component> scopeRoots,
            boolean rootsIncluded) {
        this.session = session;
        this.component = component;
        this.support = BrowserlessComponents.of(component.getClass());
        this.scopeRoots = scopeRoots;
        this.rootsIncluded = rootsIncluded;
    }

    /**
     * A component a lookup found: a search inside it finds its descendants only, not the component itself, as
     * in a browser ({@code dialog.find(UnifiedDialog.class)} finds no dialog).
     */
    static BrowserlessElement found(BrowserlessSession session, Component component) {
        return new BrowserlessElement(session, component, List.of(component), false);
    }

    /** Where queries scoped to this element search: these components and their descendants. */
    List<Component> scopeRoots() {
        return scopeRoots;
    }

    /** Whether a query scoped to this element can match its {@link #scopeRoots()} themselves. */
    boolean rootsIncluded() {
        return rootsIncluded;
    }

    Component component() {
        return component;
    }

    private Element element() {
        return component.getElement();
    }

    private ComponentTester<?> tester() {
        return session.window().test(component);
    }

    // ---------------------------------------------------------------- reading

    @Override
    public String getTag() {
        return element().getTag();
    }

    /** The text of the server-side elements, whitespace collapsed: what a browser's textContent holds. */
    @Override
    public String getText() {
        return BrowserQueries.normalize(
                support.text(component, session.window()).orElseGet(() -> element().getTextRecursively()));
    }

    @Override
    public String getVisibleText() {
        throw new UnsupportedOperationException("Browserless has no rendering, so no visible text; use getText()"
                + " or @RequiresBrowser");
    }

    @Override
    public String getInlineStyle(String name) {
        String value = element().getStyle().get(BrowserQueries.cssName(name));
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** The same computation as the {@code withAccessibleName(..)} filter: {@link #accessibleName(Component)}. */
    @Override
    public String getAccessibleName() {
        return accessibleName(component);
    }

    @Override
    public String getProperty(String name) {
        if ("value".equals(name) && component instanceof HasValue<?, ?> hasValue) {
            return text(hasValue.getValue());
        }
        if (element().hasProperty(name)) {
            return text(element().getPropertyRaw(name));
        }
        return element().getAttribute(name);
    }

    /**
     * A property value as the browsers write it: Flow keeps a numeric property as a double, so a whole number
     * is written without its ".0" ({@code 3}, not {@code 3.0}), as Playwright and Selenium do. A BigDecimal
     * keeps its scale ({@code 54.20}).
     */
    static String text(Object value) {
        if (value == null) {
            return null;
        }
        if ((value instanceof Double || value instanceof Float) && isWhole(((Number) value).doubleValue())) {
            return String.valueOf(((Number) value).longValue());
        }
        return String.valueOf(value);
    }

    private static boolean isWhole(double d) {
        return !Double.isInfinite(d) && d == Math.rint(d) && Math.abs(d) < 0x1p53;
    }

    @Override
    public Integer getPropertyInteger(String name) {
        String value = getProperty(name);
        return value == null ? null : (int) Double.parseDouble(value);
    }

    @Override
    public Boolean getPropertyBoolean(String name) {
        Optional<Boolean> special = support.booleanProperty(component, name);
        if (special.isPresent()) {
            return special.get();
        }
        if (element().hasProperty(name)) {
            return element().getProperty(name, false);
        }
        if (element().hasAttribute(name)) {
            return true;
        }
        return Boolean.FALSE;
    }

    @Override
    public String getAttribute(String name) {
        // an @Id component's id is in its binding, not an attribute on the server; a browser reads it as one
        return "id".equals(name) ? BrowserlessSession.idOf(element()) : element().getAttribute(name);
    }

    @Override
    public String getCssValue(String property) {
        throw new UnsupportedOperationException("Browserless has no CSS; use @RequiresBrowser");
    }

    @Override
    public boolean isVisible() {
        Optional<Boolean> special = support.visible(component);
        if (special.isPresent()) {
            return special.get();
        }
        if (!component.isAttached()) {
            return false;
        }
        for (Component c = component; c != null && !(c instanceof UI); c = c.getParent().orElse(null)) {
            if (!c.isVisible()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEnabled() {
        return element().isEnabled();
    }

    @Override
    /**
     * Child elements with this tag, components or not: plain {@link Element}s (built with the Element API,
     * e.g. an add-on's inner {@code <span>}s) are mapped to a {@link PlainElement} component to be read and
     * clicked like any other.
     */
    public List<UnifiedElement> findChildren(String tag) {
        return element().getChildren().filter(e -> hasTag(e, tag)).map(this::toUnified).toList();
    }

    @Override
    public List<UnifiedElement> findDescendants(String tag) {
        return element().getChildren().flatMap(BrowserlessElement::descendants).filter(e -> hasTag(e, tag))
                .map(this::toUnified).toList();
    }

    /** An element, not a text node, with this tag ({@code *}: any). */
    private static boolean hasTag(Element e, String tag) {
        return !e.isTextNode() && ("*".equals(tag) || tag.equalsIgnoreCase(e.getTag()));
    }

    private UnifiedElement toUnified(Element e) {
        return found(session, e.getComponent().orElseGet(() -> new PlainElement(e)));
    }

    /** A component for an element that has none; no tag of its own, it takes the element's. */
    static final class PlainElement extends Component {
        PlainElement(Element element) {
            super(element);
        }
    }

    // ---------------------------------------------------------------- acting

    @Override
    public void setProperty(String name, Object value) {
        session.act(component, () -> {
            if (value instanceof Boolean b) {
                element().setProperty(name, b);
            } else if (value instanceof Number n) {
                element().setProperty(name, n.doubleValue());
            } else {
                element().setProperty(name, value == null ? null : String.valueOf(value));
            }
            session.window().roundTrip();
        });
    }

    @Override
    public void click() {
        session.act(component, () -> {
            if (tester().getClass() != ComponentTester.class && hasTesterMethod("click", 0)) {
                invokeTester("click"); // a component's own tester (Button, ...): its usability checks
            } else {
                // a DOM click: element listeners and @DomEvent("click") component listeners both run
                com.vaadin.browserless.internal.BasicUtilsKt._fireDomEvent(component, "click");
            }
            session.window().roundTrip();
        });
    }

    @Override
    public void setValueAsUser(Object value) {
        session.act(component, () -> {
            if (support.setValue(component, value, session.window())) {
                session.window().roundTrip();
                return;
            }
            if (value == null && hasTesterMethod("clear", 0)) {
                invokeTester("clear");
                session.window().roundTrip();
                return;
            }
            Optional<Method> setValue = testerMethod("setValue", 1);
            if (setValue.isPresent()) {
                call(setValue.get(), value);
            } else if (List.of("input", "textarea").contains(getTag())) {
                // a native input with no tester (a bare Element): what the browser does when a user types
                element().setProperty("value", value == null ? "" : String.valueOf(value));
                com.vaadin.browserless.internal.BasicUtilsKt._fireDomEvent(component, "input");
                com.vaadin.browserless.internal.BasicUtilsKt._fireDomEvent(component, "change");
            } else {
                throw new UnsupportedOperationException(component.getClass().getSimpleName()
                        + " has no value to set on Browserless");
            }
            session.window().roundTrip();
        });
    }

    @Override
    public void focus() {
        session.act(component, () -> {
            if (component instanceof Focusable<?> focusable) {
                focusable.focus();
            }
            session.window().roundTrip();
        });
    }

    @Override
    public void blur() {
        session.act(component, () -> {
            if (component instanceof Focusable<?> focusable) {
                focusable.blur();
            }
            session.window().roundTrip();
        });
    }

    @Override
    public void pressKey(String key) {
        session.act(component, () -> {
            pressKey(session, component, key);
        });
    }

    /** The component's own key handling (e.g. Escape closes a dialog), else a bubbling keydown. */
    static void pressKey(BrowserlessSession session, Component target, String key) {
        if (!BrowserlessComponents.of(target.getClass()).pressKey(target, key, session.window())) {
            BrowserlessKeys.press(target, key);
        }
        session.window().roundTrip();
    }

    @Override
    public void dispatchEvent(String eventName) {
        session.act(component, () -> {
            BrowserlessKeys.dispatch(component, eventName);
            session.window().roundTrip();
        });
    }

    // ---------------------------------------------------------------- Escape Hatch

    @Override
    public <T> T unwrap(Class<T> type) {
        return tryUnwrap(type).orElseThrow();
    }

    @Override
    public <T> Optional<T> tryUnwrap(Class<T> type) {
        Optional<Object> special = support.unwrap(type, component, session);
        if (special.isPresent()) {
            return special.map(type::cast);
        }
        if (type.isInstance(component)) {
            return Optional.of(type.cast(component));
        }
        if (ComponentTester.class.isAssignableFrom(type)) {
            ComponentTester<?> tester = tester();
            return type.isInstance(tester) ? Optional.of(type.cast(tester)) : Optional.empty();
        }
        if (type.isInstance(element())) {
            return Optional.of(type.cast(element()));
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- helpers

    /** What a screen reader announces, approximated: aria-label, accessibleName, aria-labelledby target, label, text. */
    static String accessibleName(Component c) {
        Element e = c.getElement();
        String ariaLabel = e.getAttribute("aria-label");
        if (ariaLabel != null && !ariaLabel.isBlank()) {
            return ariaLabel;
        }
        // a field's setAriaLabel(..): the web component forwards it to its input as aria-label
        String accessibleName = e.getProperty("accessibleName");
        if (accessibleName != null && !accessibleName.isBlank()) {
            return accessibleName;
        }
        String labelledBy = e.getAttribute("aria-labelledby");
        if (labelledBy != null && !labelledBy.isBlank()) {
            Optional<UI> ui = c.getUI();
            if (ui.isPresent()) {
                Optional<String> text = ui.get().getElement().getChildren()
                        .flatMap(BrowserlessElement::descendants)
                        .filter(el -> labelledBy.equals(el.getAttribute("id")))
                        .map(Element::getTextRecursively).findFirst();
                if (text.isPresent()) {
                    return text.get();
                }
            }
        }
        String label = e.getProperty("label");
        if (label != null && !label.isBlank()) {
            return label;
        }
        return e.getTextRecursively();
    }

    private static java.util.stream.Stream<Element> descendants(Element e) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(e), e.getChildren().flatMap(BrowserlessElement::descendants));
    }

    private boolean hasTesterMethod(String name, int params) {
        return testerMethod(name, params).isPresent();
    }

    private Optional<Method> testerMethod(String name, int params) {
        for (Method m : tester().getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    private Object invokeTester(String name) {
        return call(testerMethod(name, 0).orElseThrow(
                () -> new UnsupportedOperationException(tester().getClass().getSimpleName() + " has no " + name + "()")));
    }

    private Object call(Method method, Object... args) {
        try {
            return method.invoke(tester(), args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "BrowserlessElement[" + component.getClass().getSimpleName() + "]";
    }
}
