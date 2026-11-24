package com.vaadin.testing.unified.browserless;

import com.vaadin.browserless.BrowserlessApplicationContext;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.browserless.BrowserlessUserContext;
import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import java.util.ArrayList;
import com.vaadin.flow.component.Component;
import com.vaadin.browserless.internal.PrettyPrintTree;
import com.vaadin.browserless.internal.PrettyPrintTreeKt;
import com.vaadin.browserless.internal.TestingLifecycleHookKt;
import com.vaadin.testing.unified.internal.steps.TreePage;
import com.vaadin.flow.component.UI;
import java.nio.file.Files;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * One window of one user on Browserless. The page injected into the test owns the application; windows
 * and users opened from it are its children and close with it.
 */
final class BrowserlessSession implements AdapterSession {

    /** The window Browserless's thread-locals (current UI, session, security context) point to. */
    private static final ThreadLocal<BrowserlessSession> ACTIVE = new ThreadLocal<>();

    private final BrowserlessApplicationContext app;
    private final BrowserlessUserContext user;
    private final BrowserlessUIContext window;
    private final boolean ownsUser;
    private final boolean ownsApp;
    private final List<BrowserlessSession> children = new ArrayList<>();
    private boolean routeErrorsToSession;
    /** The component the current User Step acts on: set by an action, cleared by the next lookup or snapshot. */
    private Component target;

    /** The test's own page: owns the app and the user. */
    BrowserlessSession(BrowserlessApplicationContext app, BrowserlessUserContext user) {
        this(app, user, true, true);
    }

    private BrowserlessSession(BrowserlessApplicationContext app, BrowserlessUserContext user, boolean ownsUser,
            boolean ownsApp) {
        this.app = app;
        this.user = user;
        this.window = user.newWindow();
        this.ownsUser = ownsUser;
        this.ownsApp = ownsApp;
        ACTIVE.set(this);
    }

    /** This window, made the current one first: several windows and users can live in one test. */
    BrowserlessUIContext window() {
        if (ACTIVE.get() != this) {
            window.activate();
            ACTIVE.set(this);
        }
        return window;
    }

    @Override
    public AdapterSession openWindow() {
        return child(new BrowserlessSession(app, user, false, false));
    }

    @Override
    public AdapterSession openAsUser(String username, String... roles) {
        if (!(app instanceof SecuredBrowserlessApplicationContext<?> secured)) {
            throw new IllegalStateException("openAsUser needs Spring Security in the application"
                    + " (spring-security on the test classpath and a SecurityFilterChain bean)");
        }
        return child(new BrowserlessSession(app, secured.newUser(username, roles), true, false));
    }

    private BrowserlessSession child(BrowserlessSession session) {
        session.routeErrorsToSession = routeErrorsToSession;
        children.add(session);
        return session;
    }

    /** {@code vaadin.testing.browserless.route-errors-to-session}: see {@link #act}. */
    BrowserlessSession routeErrorsToSession(boolean route) {
        this.routeErrorsToSession = route;
        return this;
    }

    /**
     * Runs a user action. In a browser, an exception thrown by an app listener goes to the session's
     * ErrorHandler (the app's error UI); Browserless Test throws it into the test. With
     * {@code route-errors-to-session}, it goes to the ErrorHandler as in a browser. Exceptions of Browserless
     * Test itself (a component that isn't usable) are always thrown.
     */
    void act(Runnable action) {
        act(null, action);
    }

    /** {@link #act(Runnable)} on a component, which the step's snapshot and failure evidence then mark. */
    void act(Component on, Runnable action) {
        target = on;
        try {
            action.run();
        } catch (RuntimeException e) {
            if (!routeErrorsToSession || thrownByBrowserless(e)) {
                throw e;
            }
            com.vaadin.flow.server.VaadinSession session = window().getUI().getSession();
            session.getErrorHandler().error(new com.vaadin.flow.server.ErrorEvent(e));
            window().roundTrip();
        }
    }

    private static boolean thrownByBrowserless(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            String type = frame.getClassName();
            if (type.startsWith("java.") || type.startsWith("jdk.") || type.startsWith("kotlin.")) {
                continue;
            }
            return type.startsWith("com.vaadin.browserless.") || type.startsWith("com.vaadin.testing.unified.");
        }
        return false;
    }

    // ---------------------------------------------------------------- finding

    /**
     * Matching components in document order, hidden ones included (as in a browser, where a component hidden
     * after it was shown stays in the page): Browserless Test's own queries skip them, so the adapter walks
     * the component tree itself.
     */
    @Override
    public List<UnifiedElement> find(QuerySpec spec) {
        target = null; // a new lookup: a new step, or one that has not acted yet
        BrowserlessElement parent = (BrowserlessElement) spec.parent();
        List<Component> roots = parent == null ? List.of(window().getUI())
                : spec.inTemplate() ? templateRoots(parent.component()) : parent.scopeRoots();
        boolean rootsIncluded = spec.inTemplate() || parent != null && parent.rootsIncluded();
        // as Browserless Test does before a lookup: run the UI's pending access() tasks (one round trip)
        com.vaadin.browserless.internal.TestingLifecycleHook hook =
                com.vaadin.browserless.internal.TestingLifecycleHookKt.getTestingLifecycleHook();
        hook.awaitBeforeLookup();
        java.util.Set<Component> found = new java.util.LinkedHashSet<>();
        java.util.Set<com.vaadin.flow.component.grid.Grid<?>> grids = new java.util.LinkedHashSet<>();
        // one walk per root finds the matches and the Grids under it (#401)
        for (Component root : roots) {
            found.addAll(findIn(root, spec, rootsIncluded, grids));
        }
        // a Grid's cell components are not in the component tree: add those the grid rendered for the client,
        // as a browser has them in the page, and those of Grids inside them
        java.util.Set<com.vaadin.flow.component.grid.Grid<?>> seen = new java.util.HashSet<>(grids);
        for (com.vaadin.flow.component.grid.Grid<?> grid : grids) {
            addRenderedCells(grid, spec, found, seen);
        }
        hook.awaitAfterLookup();
        return found.stream().map(c -> (UnifiedElement) BrowserlessElement.found(this, c)).toList();
    }

    /** The {@code LitTemplate}s in the scope, with Browserless Test's walk, which doesn't enter them. */
    @Override
    public List<UnifiedElement> findTemplates(UnifiedElement parent, boolean inTemplate) {
        BrowserlessElement scope = (BrowserlessElement) parent;
        List<Component> roots = scope == null ? List.of(window().getUI())
                : inTemplate ? templateRoots(scope.component()) : scope.scopeRoots();
        boolean rootsIncluded = inTemplate || scope != null && scope.rootsIncluded();
        java.util.Set<Component> found = new java.util.LinkedHashSet<>();
        for (Component root : roots) {
            for (Component c : com.vaadin.browserless.internal.LocatorKt._walkAll(root)) {
                if ((c != root || rootsIncluded) && c instanceof com.vaadin.flow.component.littemplate.LitTemplate) {
                    found.add(c);
                }
            }
        }
        return found.stream().map(c -> (UnifiedElement) BrowserlessElement.found(this, c)).toList();
    }

    /**
     * Where a lookup inside a Template searches (spec 04, "Templates"): the components bound to its template with
     * {@code @Id}, which Flow keeps as virtual children and Browserless Test's own walk leaves out, then its light
     * DOM children. Each is searched with what is under it, but not inside a Template nested in it.
     */
    static List<Component> templateRoots(Component template) {
        List<Component> roots = new ArrayList<>();
        com.vaadin.flow.internal.StateNode node = template.getElement().getNode();
        if (node.hasFeature(com.vaadin.flow.internal.nodefeature.VirtualChildrenList.class)) {
            var virtual = node.getFeature(com.vaadin.flow.internal.nodefeature.VirtualChildrenList.class);
            for (int i = 0; i < virtual.size(); i++) {
                com.vaadin.flow.dom.Element child = com.vaadin.flow.dom.Element.get(virtual.get(i));
                if (injectedId(child) != null) {
                    child.getComponent().ifPresent(roots::add);
                }
            }
        }
        template.getChildren().forEach(roots::add);
        return roots;
    }

    /**
     * The id a browser reads on the element: its {@code id} attribute, or the id of the template element it is bound
     * to with {@code @Id}, which Flow keeps in the binding and doesn't set as an attribute on the server.
     */
    static String idOf(com.vaadin.flow.dom.Element element) {
        String id = element.getAttribute("id");
        return id != null ? id : injectedId(element);
    }

    /** The id an {@code @Id} binding gave the element in its template, or null when it has none. */
    private static String injectedId(com.vaadin.flow.dom.Element element) {
        if (!element.isVirtualChild()) {
            return null;
        }
        com.vaadin.flow.internal.StateNode node = element.getNode();
        var payload = node.hasFeature(com.vaadin.flow.internal.nodefeature.ElementData.class)
                ? node.getFeature(com.vaadin.flow.internal.nodefeature.ElementData.class).getPayload() : null;
        return payload != null && com.vaadin.flow.internal.nodefeature.NodeProperties.INJECT_BY_ID.equals(
                payload.path(com.vaadin.flow.internal.nodefeature.NodeProperties.TYPE).asString(null))
                ? payload.path(com.vaadin.flow.internal.nodefeature.NodeProperties.PAYLOAD).asString(null) : null;
    }

    /**
     * In the JVM, so no round trips to save: the element's values, and its nearest ancestor with a test id or id.
     * Every element found is a component of the application's, so none is an internal part.
     */
    @Override
    public List<com.vaadin.testing.unified.spi.ElementFacts> facts(List<UnifiedElement> elements) {
        return elements.stream().map(e -> {
            com.vaadin.testing.unified.spi.ElementFacts own = com.vaadin.testing.unified.spi.ElementFacts.read(e);
            String testId = null;
            String id = null;
            if (e instanceof BrowserlessElement b) {
                for (Component c = b.component().getParent().orElse(null); c != null; c = c.getParent().orElse(null)) {
                    testId = c.getElement().getAttribute("data-testid");
                    id = idOf(c.getElement());
                    if (testId != null || id != null) {
                        break;
                    }
                }
            }
            String label = own.label() != null && !own.label().isBlank() || !(e instanceof BrowserlessElement b2)
                    ? own.label() : formItemLabel(b2.component());
            java.util.Map<String, String> data = new java.util.HashMap<>();
            if (e instanceof BrowserlessElement b3) {
                com.vaadin.flow.dom.Element el = b3.component().getElement();
                el.getAttributeNames().filter(n -> n.startsWith("data-") && !n.equals("data-testid"))
                        .forEach(n -> data.put(n, el.getAttribute(n)));
            }
            return new com.vaadin.testing.unified.spi.ElementFacts(label, own.text(), own.placeholder(),
                    own.testId(), own.accessibleName(), testId, testId == null ? id : null, false, data);
        }).toList();
    }

    /**
     * The label of the FormLayout item holding the component ({@code formLayout.addFormItem(field, "Name")}): the
     * text of the {@code <vaadin-form-item>}'s {@code slot="label"} child, as a browser reads it; null outside a
     * form item.
     */
    static String formItemLabel(Component c) {
        return c.getElement().getParent() == null || !"vaadin-form-item".equals(c.getElement().getParent().getTag())
                ? null
                : c.getElement().getParent().getChildren().filter(l -> "label".equals(l.getAttribute("slot")))
                        .findFirst().map(l -> com.vaadin.testing.unified.internal.BrowserQueries.normalize(l.getTextRecursively())).orElse(null);
    }

    /**
     * Matching components under {@code root}, and {@code root} itself when {@code includeRoot}, in one walk that
     * also adds to {@code grids} the Grids it passes: {@code root} if it is one, and the visible ones under it, as
     * Browserless Test's own query finds them.
     */
    private List<Component> findIn(Component root, QuerySpec spec, boolean includeRoot,
            java.util.Set<com.vaadin.flow.component.grid.Grid<?>> grids) {
        BrowserlessComponents.Support support = BrowserlessComponents.of(spec.componentType());
        List<Component> found = new ArrayList<>();
        for (Component c : com.vaadin.browserless.internal.LocatorKt._walkAll(root)) {
            if ((c != root || includeRoot) && spec.componentType().isInstance(c) && hasQueriedTag(c, spec)
                    && support.include(c)
                    && spec.filters().stream().allMatch(f -> matches(c, f))) {
                found.add(c);
            }
            if (c instanceof com.vaadin.flow.component.grid.Grid<?> grid
                    && (c == root || com.vaadin.browserless.internal.BasicUtilsKt.isEffectivelyVisible(c))) {
                grids.add(grid);
            }
        }
        return found;
    }

    /**
     * A tag the component class doesn't declare narrows the class, as it does in a browser: {@code UnifiedLink} is
     * {@code @UnifiedComponent(value = Component.class, tag = "a")}, so a RouterLink, an Anchor or any other
     * {@code <a>}. A class with a {@code @Tag} of its own is found by class alone, as before. A tag list matches any
     * of its tags: {@code UnifiedLogin} is a {@code LoginOverlay} or a {@code LoginForm}. A {@code Text} component
     * is a text node, with no tag, so it never matches a tag (#603).
     */
    private static boolean hasQueriedTag(Component c, QuerySpec spec) {
        String tag = spec.tag();
        if (tag == null || "*".equals(tag) || spec.componentType().isAnnotationPresent(com.vaadin.flow.component.Tag.class)) {
            return true;
        }
        return !c.getElement().isTextNode() && com.vaadin.testing.unified.internal.BrowserQueries.tags(tag).stream()
                .anyMatch(t -> t.equalsIgnoreCase(c.getElement().getTag()));
    }

    private boolean matches(Component c, Filter f) {
        com.vaadin.flow.dom.Element e = c.getElement();
        return switch (f.kind()) {
            // its own label, or the label of the FormLayout item holding it
            case LABEL -> (f.exact() ? com.vaadin.browserless.ElementConditions.hasLabel(f.value()).test(c)
                    : f.matches(e.getProperty("label"))) || f.matches(formItemLabel(c));
            // getText(), as in a browser: Browserless's own withText reads the element's own text only
            case TEXT -> f.matches(new BrowserlessElement(this, c).getText());
            case PLACEHOLDER -> f.matches(e.getProperty("placeholder"));
            case TEST_ID -> f.value().equals(e.getAttribute("data-testid"));
            case ID -> f.value().equals(idOf(e));
            case CLASS_NAME -> e.getClassList().contains(f.value());
            case THEME -> {
                String theme = e.getAttribute("theme");
                yield theme != null && java.util.Arrays.asList(theme.split("\\s+")).contains(f.value());
            }
            case ATTRIBUTE -> f.value() == null ? e.hasAttribute(f.name()) : f.value().equals(e.getAttribute(f.name()));
            case WITHOUT_ATTRIBUTE -> !e.hasAttribute(f.name());
            case ACCESSIBLE_NAME -> f.matches(BrowserlessElement.accessibleName(c));
        };
    }

    /** How many rows of a grid are checked for rendered cell components, at most. */
    private static final int GRID_ROWS_CHECKED = 500;

    /**
     * Adds to {@code found} the matches among the components {@code grid} has rendered for the rows the client
     * has, in its component columns (what a browser has in the page), then those of Grids inside them.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void addRenderedCells(com.vaadin.flow.component.grid.Grid<?> grid, QuerySpec spec,
            java.util.Set<Component> found, java.util.Set<com.vaadin.flow.component.grid.Grid<?>> seen) {
        List<com.vaadin.flow.component.grid.Grid.Column<?>> columns = (List) grid.getColumns().stream()
                .filter(c -> c.isVisible() && c.getRenderer() instanceof com.vaadin.flow.data.renderer.ComponentRenderer)
                .toList();
        if (columns.isEmpty()) {
            return;
        }
        com.vaadin.flow.data.provider.DataCommunicator<Object> communicator =
                (com.vaadin.flow.data.provider.DataCommunicator<Object>) grid.getDataCommunicator();
        com.vaadin.flow.component.grid.GridTester<?, Object> tester =
                (com.vaadin.flow.component.grid.GridTester<?, Object>) (Object) window().test(grid);
        int size = Math.min(tester.size(), GRID_ROWS_CHECKED);
        for (int row = 0; row < size; row++) {
            Object item = tester.getRow(row);
            if (!communicator.isItemActive(item)) {
                continue;
            }
            String key = communicator.getKeyMapper().key(item);
            for (com.vaadin.flow.component.grid.Grid.Column<?> column : columns) {
                Component cell = com.vaadin.browserless.internal.RenderedComponentSupport.getRenderedComponent(column, key);
                if (cell != null) {
                    java.util.Set<com.vaadin.flow.component.grid.Grid<?>> inner = new java.util.LinkedHashSet<>();
                    found.addAll(findIn(cell, spec, true, inner));
                    for (com.vaadin.flow.component.grid.Grid<?> nested : inner) {
                        if (seen.add(nested)) {
                            addRenderedCells(nested, spec, found, seen);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- navigation

    @Override
    public void navigate(Class<? extends Component> view) {
        // UI.navigate, not BrowserlessUIContext.navigate: that one throws when the router lands elsewhere,
        // but a redirect (login, access denied) is a result the test asserts on
        window().getUI().navigate(view);
        window().roundTrip();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void navigate(Class<? extends Component> view, Object urlParameter) {
        window().getUI().navigate((Class) view, urlParameter);
        window().roundTrip();
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters) {
        window().getUI().navigate(view, parameters);
        window().roundTrip();
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters, QueryParameters query) {
        window().getUI().navigate(view, parameters, query);
        window().roundTrip();
    }

    @Override
    public void navigate(String path) {
        window().getUI().navigate(path.startsWith("/") ? path.substring(1) : path);
        window().roundTrip();
    }

    @Override
    public void reload() {
        UI ui = window().getUI();
        Location location = activeLocation();
        if (location == null) {
            return;
        }
        boolean preserved = ui.getInternals().getActiveRouterTargetsChain().stream()
                .anyMatch(target -> target.getClass().isAnnotationPresent(PreserveOnRefresh.class));
        if (!preserved) {
            // a browser creates the views again; with @PreserveOnRefresh it keeps them, as here
            ui.getInternals().getRouter().navigate(ui, location, NavigationTrigger.REFRESH, null, true, true);
        }
        window().roundTrip();
    }

    @Override
    public boolean isAt(Class<? extends Component> view) {
        return view.isInstance(window().getCurrentView());
    }

    @Override
    public String currentPath() {
        Location location = activeLocation();
        return location == null ? "" : location.getPath();
    }

    @Override
    public Optional<String> queryParameter(String name) {
        Location location = activeLocation();
        if (location == null) {
            return Optional.empty();
        }
        List<String> values = location.getQueryParameters().getParameters().get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /** The title the UI last set; Flow keeps it on the server as it sends it to the browser. */
    @Override
    public String title() {
        String title = window().getUI().getInternals().getTitle();
        return title == null ? "" : title;
    }

    private Location activeLocation() {
        return window().getUI().getInternals().getActiveViewLocation();
    }

    // ---------------------------------------------------------------- the rest

    @Override
    public void sync() {
        window().roundTrip();
        window().runPendingSignalsTasks(1, TimeUnit.MILLISECONDS);
    }

    @Override
    public void pressKey(String key) {
        act(() -> pressPageKey(key));
    }

    private void pressPageKey(String key) {
        List<Dialog> open = window().find(Dialog.class).all().stream().filter(Dialog::isOpened).toList();
        BrowserlessElement.pressKey(this, BrowserlessKeys.pageTarget(window().getUI(), open), key);
    }

    /** The UI's component tree as text, with the location: what Browserless's TreeOnFailureExtension prints. */
    @Override
    public com.vaadin.testing.unified.spi.Evidence writeFailureEvidence(Path directory, String name, com.vaadin.testing.unified.UnifiedElement stepTarget)
            throws java.io.IOException {
        return com.vaadin.testing.unified.spi.Evidence.of(writeTree(directory, name, marked(stepTarget)));
    }

    /** The component a tree marks: the Step Target the API names, else the one this session last acted on. */
    private Component marked(com.vaadin.testing.unified.UnifiedElement stepTarget) {
        return stepTarget instanceof BrowserlessElement element ? element.component() : target;
    }

    /** The component tree after the step: Browserless has no screenshot, and the tree is cheap. */
    @Override
    public com.vaadin.testing.unified.spi.Evidence writeStepSnapshot(Path directory, String name,
            com.vaadin.testing.unified.UnifiedElement stepTarget) throws java.io.IOException {
        try {
            return com.vaadin.testing.unified.spi.Evidence.of(writeTree(directory, name, marked(stepTarget)));
        } finally {
            target = null;
        }
    }

    private List<Path> writeTree(Path directory, String name, Component marked) throws java.io.IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(name + ".tree.txt");
        Location location = activeLocation();
        String tree = maskPasswords(tree(window().getUI(), marked).print());
        Files.writeString(file, "Location: /" + (location == null ? "" : location.getPathWithQueryParameters())
                + "\n\n" + tree);
        return List.of(file);
    }

    /**
     * Karibu's pretty tree ({@code PrettyPrintTreeKt.toPrettyTree}), built the same way, with the component the
     * step acted on marked at the end of its line ({@link TreePage#TARGET_MARK}).
     */
    static PrettyPrintTree tree(Component component, Component target) {
        List<PrettyPrintTree> children = new ArrayList<>();
        for (Component child : TestingLifecycleHookKt.getTestingLifecycleHook().getAllChildren(component)) {
            children.add(tree(child, target));
        }
        String line = PrettyPrintTreeKt.toPrettyString(component);
        return new PrettyPrintTree(component == target ? line + TreePage.TARGET_MARK : line, children);
    }

    /** A password field's value shows as the reports show it: {@code ••••}. */
    static String maskPasswords(String tree) {
        return tree.replaceAll("(PasswordField\\[[^\\n]*?value=')[^'\\n]*(')", "$1••••$2");
    }

    @Override
    public void screenshot(Path file) {
        throw new UnsupportedOperationException("Browserless has no screenshots");
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type.isInstance(window)) {
            // made current: the caller (BrowserlessEscapeHatch) works on the thread-locals of this window
            return Optional.of(type.cast(window()));
        }
        UI ui = window().getUI();
        if (type.isInstance(ui)) {
            return Optional.of(type.cast(ui));
        }
        if (type.isInstance(app)) {
            return Optional.of(type.cast(app));
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        try {
            for (int i = children.size() - 1; i >= 0; i--) {
                children.get(i).close();
            }
        } finally {
            if (ACTIVE.get() == this) {
                ACTIVE.remove();
            }
            try {
                window().close();
            } finally {
                if (ownsUser) {
                    user.close();
                }
                if (ownsApp) {
                    app.close();
                    ACTIVE.remove();
                }
            }
        }
    }
}
