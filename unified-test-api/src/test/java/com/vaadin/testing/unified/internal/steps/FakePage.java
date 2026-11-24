package com.vaadin.testing.unified.internal.steps;

import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.UnifiedPageImpl;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.ElementFacts;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.spi.GridAccess;
import com.vaadin.testing.unified.spi.MenuBarAccess;
import com.vaadin.testing.unified.spi.MessageListAccess;
import com.vaadin.testing.unified.spi.QuerySpec;
import com.vaadin.testing.unified.spi.TabSheetAccess;
import com.vaadin.testing.unified.spi.ConfirmDialogAccess;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A page of fake components for tests of the User Step recording: each has a tag and properties ({@code label},
 * {@code text}, {@code testId}, ...). Lookups match the tag ({@code *} any) and the filters. A component with
 * {@code in} lies in the container whose {@code testId} that is: a lookup inside that container finds it, and its
 * facts name that test id as its nearest ancestor's; any other lookup ignores the scope.
 */
final class FakePage {

    final List<Map<String, String>> components = new CopyOnWriteArrayList<>();
    final List<String> actions = new CopyOnWriteArrayList<>();
    /** What the application does when a component is clicked, e.g. log an error. */
    Runnable onClick = () -> { };
    /** The page's path, which a click may change ({@link #onClick}). */
    volatile String path = "people";
    /** The errors the fake browser's console holds until they are read. */
    final List<String> console = new CopyOnWriteArrayList<>();
    final StepRecorder steps = new StepRecorder();
    final UnifiedPageImpl page;

    FakePage() {
        AdapterSession session = (AdapterSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { AdapterSession.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "find" -> find((QuerySpec) args[0]);
                    case "facts" -> facts((List<?>) args[0]);
                    case "isAt" -> true;
                    case "currentPath" -> path;
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
                .filter(c -> spec.tag().equals("*")
                        || com.vaadin.testing.unified.internal.BrowserQueries.tags(spec.tag()).contains(c.get("tag")))
                .filter(c -> spec.parent() == null || spec.parent().getProperty("testId") == null
                        || spec.parent().getProperty("testId").equals(c.get("in")))
                .filter(c -> spec.filters().stream().allMatch(f -> f.kind() == Filter.Kind.WITHOUT_ATTRIBUTE
                        ? !c.containsKey(f.name()) : f.kind() == Filter.Kind.ATTRIBUTE && f.value() == null
                        ? c.containsKey(f.name()) : f.matches(c.get(property(f)))))
                .map(this::element)
                .toList();
    }

    /** Each element's facts as read through it, and whether it is an internal part ({@code "internalPart"}). */
    private List<ElementFacts> facts(List<?> elements) {
        return elements.stream().map(o -> {
            UnifiedElement e = (UnifiedElement) o;
            ElementFacts f = ElementFacts.read(e);
            // its data-* attributes but the test id, which the view reads as a name of its own
            Map<String, String> data = new java.util.TreeMap<>();
            componentOf.getOrDefault(e, Map.of()).forEach((k, v) -> {
                if (k.startsWith("data-") && !k.equals("data-testid")) {
                    data.put(k, v);
                }
            });
            return new ElementFacts(f.label(), f.text(), f.placeholder(), f.testId(), f.accessibleName(),
                    e.getAttribute("in"), e.getAttribute("inId"), Boolean.parseBoolean(e.getAttribute("internalPart")),
                    data);
        }).toList();
    }

    /** Each element handed out, with the component it stands for. */
    private final Map<Object, Map<String, String>> componentOf = java.util.Collections.synchronizedMap(
            new java.util.IdentityHashMap<>());

    private static String property(Filter f) {
        return switch (f.kind()) {
            case LABEL, ACCESSIBLE_NAME -> "label";
            case TEXT -> "text";
            case TEST_ID -> "testId";
            case PLACEHOLDER -> "placeholder";
            case ID -> "id";
            case CLASS_NAME -> "className";
            case THEME -> "theme";
            case ATTRIBUTE, WITHOUT_ATTRIBUTE -> f.name();
        };
    }

    private UnifiedElement element(Map<String, String> component) {
        UnifiedElement e = elementProxy(component);
        componentOf.put(e, component);
        return e;
    }

    private UnifiedElement elementProxy(Map<String, String> component) {
        return (UnifiedElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { UnifiedElement.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "isVisible" -> {
                        if ("throws".equals(component.get("visible"))) {
                            throw new IllegalStateException("the element went stale");
                        }
                        yield !"false".equals(component.get("visible"));
                    }
                    case "isEnabled" -> !"false".equals(component.get("enabled"));
                    case "getText" -> component.getOrDefault("text", "");
                    case "getTag" -> component.get("tag");
                    case "getProperty", "getAttribute" -> component.get((String) args[0]);
                    case "getPropertyBoolean" -> Boolean.valueOf(component.get((String) args[0]));
                    case "tryUnwrap" -> args[0] == MenuBarAccess.class ? Optional.of(menuBar(component))
                            : args[0] == MessageListAccess.class ? Optional.of(messageList(component))
                            : args[0] == GridAccess.class && isGrid(component) ? Optional.of(grid(component))
                            : Optional.empty();
                    case "unwrap" -> args[0] == MessageListAccess.class ? messageList(component)
                            : args[0] == GridAccess.class && isGrid(component) ? grid(component)
                            : args[0] == TabSheetAccess.class ? tabs(component)
                            : args[0] == ConfirmDialogAccess.class ? confirmDialog(component)
                            : args[0] == com.vaadin.testing.unified.spi.LoginAccess.class ? login(component) : null;
                    case "setValueAsUser" -> {
                        component.put("value", String.valueOf(args[0]));
                        component.put("checked", String.valueOf(args[0]));
                        actions.add("set " + args[0]);
                        yield null;
                    }
                    case "setProperty" -> {
                        component.put((String) args[0], String.valueOf(args[1]));
                        actions.add("property " + args[0] + "=" + args[1]);
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

    /**
     * A login form or overlay: it posts forms unless {@code "postsForms"} is {@code "false"}; signing in records
     * the username and the password, as the application receives them; a password {@code "boom"} fails with it.
     */
    private com.vaadin.testing.unified.spi.LoginAccess login(Map<String, String> component) {
        return new com.vaadin.testing.unified.spi.LoginAccess() {
            @Override
            public boolean postsForms() {
                return !"false".equals(component.get("postsForms"));
            }

            @Override
            public void login(String username, String password) {
                actions.add("login " + username + " " + password);
                if ("boom".equals(password)) {
                    throw new IllegalStateException("the form rejected " + username + "/" + password);
                }
            }

            @Override
            public void forgotPassword() {
                actions.add("forgot password");
            }
        };
    }

    /**
     * A confirm dialog's buttons from its {@code "buttons"} property, by role and text
     * ({@code "CONFIRM=Delete,CANCEL=Keep editing"}); pressing one closes the dialog.
     */
    private ConfirmDialogAccess confirmDialog(Map<String, String> component) {
        return new ConfirmDialogAccess() {
            @Override
            public Map<Role, String> buttons() {
                Map<Role, String> buttons = new java.util.EnumMap<>(Role.class);
                for (String button : component.getOrDefault("buttons", "").split(",")) {
                    String[] roleAndText = button.split("=", 2);
                    buttons.put(Role.valueOf(roleAndText[0]), roleAndText[1]);
                }
                return buttons;
            }

            @Override
            public void press(Role role) {
                component.put("opened", "false");
                actions.add("press " + role);
            }
        };
    }

    /**
     * A menu bar's items from its properties: {@code "items"} for the top level, {@code "items File/Export"}
     * under a path, and {@code "disabled"} listing the paths of disabled items ({@code "File/Print"}).
     */
    private static MenuBarAccess menuBar(Map<String, String> component) {
        return new MenuBarAccess() {
            @Override
            public List<String> items(List<String> path) {
                String key = path.isEmpty() ? "items" : "items " + String.join("/", path);
                return List.of(component.getOrDefault(key, "").split(","));
            }

            @Override
            public void click(List<String> path) { }

            @Override
            public boolean isEnabled(List<String> path) {
                return !List.of(component.getOrDefault("disabled", "").split(",")).contains(String.join("/", path));
            }
        };
    }

    /**
     * A tab bar's tabs from its {@code "tabs"} property ({@code "Orders,Users"}), the selected one in
     * {@code "selected"}, and the tabs holding a link in {@code "links"}: a link a browser follows by clicking it.
     */
    private TabSheetAccess tabs(Map<String, String> component) {
        return new TabSheetAccess() {
            @Override
            public List<String> tabs() {
                return List.of(component.getOrDefault("tabs", "").split(","));
            }

            @Override
            public String selectedTab() {
                return component.get("selected");
            }

            @Override
            public boolean isEnabled(String label) {
                return true;
            }

            @Override
            public void select(String label) {
                component.put("selected", label);
                actions.add("select " + label);
            }

            @Override
            public Optional<UnifiedElement> link(String label) {
                return List.of(component.getOrDefault("links", "").split(",")).contains(label)
                        ? Optional.of(element(new HashMap<>(Map.of("tag", "a", "text", label)))) : Optional.empty();
            }

            @Override
            public UnifiedElement content() {
                return null;
            }
        };
    }

    /**
     * A message list's messages from its {@code "messages"} property, read again on every call so that a test can
     * add a reply later: {@code "Assistant:Hi!|You:Soup"}.
     */
    private static MessageListAccess messageList(Map<String, String> component) {
        return () -> {
            String messages = component.getOrDefault("messages", "");
            return messages.isEmpty() ? List.of() : java.util.Arrays.stream(messages.split("\\|"))
                    .map(m -> new UnifiedMessageList.Message(m.substring(0, m.indexOf(':')),
                            m.substring(m.indexOf(':') + 1)))
                    .toList();
        };
    }

    /**
     * A grid's rows from its {@code "headers"} ({@code "Name,Email"}) and {@code "rows"} ({@code "Alan|alan@x;Ada|ada@x"})
     * properties; it reads cells only, as the Findable View does.
     */
    private static boolean isGrid(Map<String, String> component) {
        return component.containsKey("headers") || component.containsKey("rows");
    }

    private static GridAccess grid(Map<String, String> component) {
        List<String> headers = List.of(component.getOrDefault("headers", "").split(","));
        String rows = component.getOrDefault("rows", "");
        List<String[]> cells = rows.isEmpty() ? List.of()
                : java.util.Arrays.stream(rows.split(";")).map(r -> r.split("\\|", -1)).toList();
        return (GridAccess) Proxy.newProxyInstance(FakePage.class.getClassLoader(), new Class<?>[] { GridAccess.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "rowCount" -> cells.size();
                    case "columnHeaders" -> headers;
                    case "cellText" -> cells.get((Integer) args[0])[(Integer) args[1]];
                    case "columnTexts" -> cells.stream().map(r -> r[(Integer) args[0]]).toList();
                    default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
                });
    }

    /** The recorded steps as text, one line each, without the header line. */
    String lines() {
        String text = new UserStepsReport("demo.FakeTest", "test", "test", "browserless", "passed", null, steps)
                .text();
        return text.substring(text.indexOf('\n') + 1);
    }
}
