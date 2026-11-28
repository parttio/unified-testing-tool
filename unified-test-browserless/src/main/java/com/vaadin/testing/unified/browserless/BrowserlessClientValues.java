package com.vaadin.testing.unified.browserless;

import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.ComponentTester;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.ElementPropertyMap;
import com.vaadin.flow.internal.nodefeature.PropertyChangeDeniedException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Sets a field's value the way a browser does (#689): as the element's property update from the client, so
 * the field's {@code setModelValue(.., fromClient)}, which an application may override, runs. Browserless
 * Test's testers set it through {@code AbstractFieldSupport} instead, which skips that override.
 *
 * <p>The tester's checks still come first (usable, the text it can take) and its focus tracking around the
 * update, through Browserless Test's package-private helpers; if those move, only the public usability check
 * is kept. Whether the update reaches the server is Flow's call, as for a browser: a field that doesn't
 * synchronize its value (no {@code ValueChangeMode}) only shows it.
 */
final class BrowserlessClientValues {
    private static final String PROPERTY = "value";

    private BrowserlessClientValues() {}

    /** Text typed into a text input; {@code text} is what the browser sends. */
    static void typeText(Component field, String text, BrowserlessUIContext window) {
        ComponentTester<?> tester = (ComponentTester<?>) window.test(field);
        tester.ensureComponentIsUsable();
        textConstraint("ensureValueCanBeTyped", Component.class, field, text);
        update(field, text);
    }

    /** A number entered in a number field, or {@code null} cleared: sent as the browser's text of it. */
    static void enterNumber(Component field, Number value, BrowserlessUIContext window) {
        ((ComponentTester<?>) window.test(field)).ensureComponentIsUsable();
        update(field, numberText(value));
    }

    /** A date picked in a date picker, or {@code null} cleared: sent as its ISO value, as the browser does. */
    static void enterDate(Component field, java.time.LocalDate date, BrowserlessUIContext window) {
        ((ComponentTester<?>) window.test(field)).ensureComponentIsUsable();
        update(field, date == null ? "" : date.toString());
    }

    /** {@code 42}, not {@code 42.0}, as a number input holds it; a BigDecimal keeps its scale. */
    private static String numberText(Number value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return java.math.BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    /** The presentation value as the browser sends it, focus moved to the field first and flushed after. */
    private static void update(Component field, Serializable presentation) {
        Object ui = focusTracker("moveFocusTo", Component.class, field);
        Element element = field.getElement();
        try {
            element.getNode().getFeature(ElementPropertyMap.class).deferredUpdateFromClient(PROPERTY, presentation)
                    .run();
        } catch (PropertyChangeDeniedException e) {
            // not synchronized: the browser shows the value, the server never hears of it
            element.setProperty(PROPERTY, presentation == null ? null : presentation.toString());
        }
        if (ui instanceof UI) {
            focusTracker("flush", UI.class, ui);
        }
    }

    private static Object focusTracker(String method, Class<?> type, Object arg) {
        return callHidden("com.vaadin.browserless.FocusTracker", method, new Class<?>[] {type}, arg);
    }

    private static void textConstraint(String method, Class<?> type, Object field, Object value) {
        callHidden("com.vaadin.flow.component.textfield.TextInputConstraints", method,
                new Class<?>[] {type, String.class}, field, value);
    }

    /** A package-private static helper of Browserless Test; null, doing nothing, if it has moved. */
    private static Object callHidden(String className, String name, Class<?>[] types, Object... args) {
        Method method;
        try {
            method = Class.forName(className, true, BrowserlessClientValues.class.getClassLoader())
                    .getDeclaredMethod(name, types);
            method.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
