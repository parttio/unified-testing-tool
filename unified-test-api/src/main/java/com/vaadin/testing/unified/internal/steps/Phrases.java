package com.vaadin.testing.unified.internal.steps;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UserStepVerb;
import com.vaadin.testing.unified.internal.LazyElement;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** The verb table of spec chapter 12 ("User Steps"): Wrapper calls and assertions in user words. */
public final class Phrases {
    private Phrases() {}

    // ---------------------------------------------------------------- Wrapper actions

    /**
     * A Wrapper method call: the verb from {@link UserStepVerb}, else from the table, else the method name in
     * words.
     */
    public static Phrase forWrapperCall(Class<?> wrapperType, LazyElement element, Method method, Object[] args) {
        StepTarget target = StepTarget.of(element);
        String name = method.getName();
        UserStepVerb annotation = verbAnnotation(wrapperType, method);
        if (annotation != null) {
            return custom(annotation.value(), target, values(args));
        }
        return switch (name) {
            case "click" -> Phrase.of("click", target);
            case "doubleClick" -> Phrase.of("double-click", target);
            case "setValue", "typeText" -> Phrase.of("fill", target, values(args));
            case "clear" -> Phrase.of("clear", target);
            case "selectItem" -> Phrase.template("choose", "choose {value} in", target, values(args));
            case "selectItems" -> Phrase.template("select", "select {value} in", target, values(args));
            case "deselectItems" -> Phrase.template("deselect", "deselect {value} in", target, values(args));
            case "check" -> Phrase.of("check", target);
            case "uncheck" -> Phrase.of("uncheck", target);
            case "setChecked" -> Phrase.of(Boolean.TRUE.equals(args[0]) ? "check" : "uncheck", target);
            case "selectTab" -> Phrase.template("select", "select tab {value} in", target, values(args));
            case "clickItem" -> Phrase.template("click", "click {value} in", target,
                    List.of(String.join(" › ", values(args))));
            case "selectAll" -> Phrase.template("select", "select all rows in", target, List.of());
            case "deselectAll" -> Phrase.template("deselect", "deselect all rows in", target, List.of());
            case "open" -> Phrase.of(target.kind().equals("details") ? "expand" : "open", target);
            case "close" -> Phrase.of(target.kind().equals("details") ? "collapse" : "close", target);
            case "upload" -> Phrase.template("upload", "upload {value} to", target, uploadName(args));
            case "send" -> Phrase.template("send", "send {value} in", target, values(args));
            case "pressKey" -> Phrase.template("press", "press {value} on", target, values(args));
            default -> {
                String verb = String.join(" ", StepTarget.splitWords(name)).toLowerCase(Locale.ROOT);
                yield Phrase.of(verb, target, values(args));
            }
        };
    }

    /**
     * An action of a Unified Element without a Wrapper method around it, e.g. {@code element().click()}:
     * from the words its Auto-Wait failure uses ("click", "set the value of", "press Enter on").
     */
    public static Phrase forElementAction(String action, LazyElement element, Object value) {
        StepTarget target = StepTarget.of(element);
        if (action.equals("set the value of")) {
            return Phrase.of("fill", target, value == null ? List.of() : values(new Object[] { value }));
        }
        if (action.startsWith("press ") && action.endsWith(" on") && value != null) {
            return Phrase.template("press", "press {value} on", target, List.of(String.valueOf(value)));
        }
        return Phrase.of(action, target);
    }

    /** {@code @UserStepVerb("rate {value} stars on")}: the verb, with the values where {@code {value}} is. */
    static Phrase custom(String verbTemplate, StepTarget target, List<String> values) {
        if (verbTemplate.contains("{value}")) {
            String verb = verbTemplate.substring(0, verbTemplate.indexOf("{value}")).strip();
            return Phrase.template(verb.isEmpty() ? verbTemplate : verb, verbTemplate, target, values);
        }
        return Phrase.of(verbTemplate, target, values);
    }

    private static UserStepVerb verbAnnotation(Class<?> wrapperType, Method method) {
        UserStepVerb own = method.getAnnotation(UserStepVerb.class);
        if (own != null) {
            return own;
        }
        try {
            return wrapperType.getMethod(method.getName(), method.getParameterTypes()).getAnnotation(UserStepVerb.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** A Wrapper call that only read and failed, e.g. {@code getValue()} on a field that is not there. */
    public static Phrase read(LazyElement element) {
        return Phrase.of("read", StepTarget.of(element));
    }

    /** Named like a getter: {@code get…}, {@code is…}, {@code has…}. */
    public static boolean isGetterName(String name) {
        return name.startsWith("get") || name.startsWith("is") || name.startsWith("has");
    }

    private static List<String> uploadName(Object[] args) {
        if (args != null && args.length > 0) {
            if (args[0] instanceof Path path) {
                return List.of(path.getFileName().toString());
            }
            if (args[0] instanceof String fileName) {
                return List.of(fileName);
            }
        }
        return List.of();
    }

    /** Arguments as values: arrays and collections expanded, byte arrays and nulls left out. */
    public static List<String> values(Object[] args) {
        List<String> values = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                addValue(values, arg);
            }
        }
        return values;
    }

    private static void addValue(List<String> values, Object arg) {
        if (arg == null || arg instanceof byte[]) {
            return;
        }
        if (arg.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(arg); i++) {
                addValue(values, Array.get(arg, i));
            }
        } else if (arg instanceof Collection<?> collection) {
            collection.forEach(v -> addValue(values, v));
        } else if (arg instanceof Path path) {
            values.add(String.valueOf(path.getFileName()));
        } else if (arg instanceof Enum<?> e) {
            values.add(e.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        } else {
            values.add(String.valueOf(arg));
        }
    }

    // ---------------------------------------------------------------- page

    /** {@code open People}: the view's simple name in words, without "View". */
    public static Phrase open(Class<? extends Component> view, Object parameter) {
        Phrase open = Phrase.template("open", "open " + viewName(view), null, List.of());
        return parameter == null ? open : Phrase.template("open", "open " + viewName(view) + " with {value}", null,
                List.of(String.valueOf(parameter)));
    }

    public static Phrase open(String path) {
        return Phrase.template("open", "open {value}", null, List.of(path));
    }

    /** {@code PeopleView} → "People", {@code EditPersonView} → "Edit Person". */
    public static String viewName(Class<?> view) {
        String simple = view.getSimpleName();
        if (simple.endsWith("View") && simple.length() > 4) {
            simple = simple.substring(0, simple.length() - 4);
        }
        return String.join(" ", StepTarget.splitWords(simple));
    }

    public static Phrase pressKey(String key) {
        return Phrase.template("press", "press {value}", null, List.of(key));
    }

    public static Phrase page(String verb, String words) {
        return Phrase.template(verb, words, null, List.of());
    }

    // ---------------------------------------------------------------- assertions

    /**
     * A retrying assertion: "see …", or "see no …" for absence. {@code expectation} is the assertion's own
     * wording ("to have value", "to be checked"); {@code expected} its expected value, or null.
     */
    public static Phrase forCheck(StepTarget target, String expectation, Object expected) {
        List<String> values = expectedValues(expected);
        switch (expectation) {
            case "to exist", "to be visible":
                return Phrase.of("see", target);
            case "not to exist", "to be hidden":
                return Phrase.of("see no", target);
            case "to have count": {
                int n = expected instanceof Number number ? number.intValue() : -1;
                Phrase see = Phrase.template("see", "see " + expected, target, List.of());
                return n == 1 ? see : see.inPlural();
            }
            case "to have text":
                if (target.name() == null && values.size() == 1) {
                    return Phrase.of("see", target.withName(values.get(0)));
                }
                return Phrase.of("see", target, values).withValueLabel("with text");
            case "to be sorted":
                return Phrase.of("see", target).withTrailing("sorted " + String.join(" ", values).toLowerCase(Locale.ROOT));
            default:
                break;
        }
        if (expectation.startsWith("to have ")) {
            String what = expectation.substring("to have ".length());
            if (values.isEmpty()) {
                return Phrase.of("see", target).withTrailing("with " + what);
            }
            return Phrase.of("see", target, values).withValueLabel("with " + what);
        }
        if (expectation.startsWith("to be ")) {
            return Phrase.of("see", target, values).withTrailing(expectation.substring("to be ".length()));
        }
        if (expectation.startsWith("not to be ")) {
            return Phrase.of("see", target, values).withTrailing("not " + expectation.substring("not to be ".length()));
        }
        return Phrase.of("see", target, values).withTrailing(expectation);
    }

    private static List<String> expectedValues(Object expected) {
        List<String> values = new ArrayList<>();
        addValue(values, expected);
        return values;
    }
}
