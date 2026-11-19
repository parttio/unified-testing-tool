package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.UnifiedPageImpl;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A page of fake components for tests of the User Step recording: each has a tag and properties ({@code label},
 * {@code text}, {@code testId}, ...). Lookups match the tag and the filters, and ignore the parent scope.
 */
final class FakePage {

    final List<Map<String, String>> components = new CopyOnWriteArrayList<>();
    final List<String> actions = new CopyOnWriteArrayList<>();
    /** What the application does when a component is clicked, e.g. log an error. */
    Runnable onClick = () -> { };
    /** The errors the fake browser's console holds until they are read. */
    final List<String> console = new CopyOnWriteArrayList<>();
    final StepRecorder steps = new StepRecorder();
    final UnifiedPageImpl page;

    FakePage() {
        AdapterSession session = (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "find" -> find((QuerySpec) args[0]);
                    case "isAt" -> true;
                    case "currentPath" -> "people";
                    case "title" -> "People";
                    case "unwrap", "queryParameter" -> Optional.empty();
                    case "navigate" -> {
                        actions.add("navigate " + args[0]);
                        yield null;
                    }
                    case "openWindow", "openAsUser" -> proxy;
                    case "consoleErrors" -> {
                        List<String> errors = List.copyOf(console);
                        console.clear();
                        yield errors;
                    }
                    case "hashCode" -> 1;
                    default -> null;
                });
        steps.session(null, session);
        page = new UnifiedPageImpl(new TestRun(TestingTool.BROWSERLESS, session, Duration.ofMillis(200), steps,
                null));
    }

    /** Adds a component: {@code add("vaadin-text-field", "label", "Name")}. */
    FakePage add(String tag, String... properties) {
        Map<String, String> component = new HashMap<>();
        component.put("tag", tag);
        for (int i = 0; i < properties.length; i += 2) {
            component.put(properties[i], properties[i + 1]);
        }
        components.add(component);
        return this;
    }

    private List<UnifiedElement> find(QuerySpec spec) {
        return components.stream()
                .filter(c -> spec.tag().equals(c.get("tag")))
                .filter(c -> spec.filters().stream().allMatch(f -> f.matches(c.get(property(f)))))
                .map(this::element)
                .toList();
    }

    private static String property(Filter f) {
        return switch (f.kind()) {
            case LABEL, ACCESSIBLE_NAME -> "label";
            case TEXT -> "text";
            case TEST_ID -> "testId";
            case PLACEHOLDER -> "placeholder";
            case ID -> "id";
            case CLASS_NAME -> "className";
            case THEME -> "theme";
            case ATTRIBUTE -> f.name();
        };
    }

    private UnifiedElement element(Map<String, String> component) {
        return (UnifiedElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { UnifiedElement.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "isVisible" -> !"false".equals(component.get("visible"));
                    case "isEnabled" -> !"false".equals(component.get("enabled"));
                    case "getText" -> component.getOrDefault("text", "");
                    case "getTag" -> component.get("tag");
                    case "getProperty", "getAttribute" -> component.get((String) args[0]);
                    case "getPropertyBoolean" -> Boolean.valueOf(component.get((String) args[0]));
                    case "tryUnwrap" -> Optional.empty();
                    case "setValueAsUser" -> {
                        component.put("value", String.valueOf(args[0]));
                        component.put("checked", String.valueOf(args[0]));
                        actions.add("set " + args[0]);
                        yield null;
                    }
                    case "click", "focus", "pressKey" -> {
                        actions.add(method.getName() + " " + component.get("tag"));
                        if (method.getName().equals("click")) {
                            onClick.run();
                        }
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** The recorded steps as text, one line each, without the header line. */
    String lines() {
        String text = new UserStepsReport("demo.FakeTest", "test", "test", "browserless", "passed", null, steps)
                .text();
        return text.substring(text.indexOf('\n') + 1);
    }
}
