package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.internal.CurrentView;
import com.vaadin.testing.unified.spi.AdapterSession;
import com.vaadin.testing.unified.spi.Filter;
import com.vaadin.testing.unified.spi.QuerySpec;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.vaadin.addons.dramafinder.AutoWait;
import org.vaadin.addons.dramafinder.RouteNavigation;
import org.vaadin.addons.dramafinder.VaadinClient;
import org.vaadin.addons.dramafinder.element.GenericElement;
import org.vaadin.addons.dramafinder.element.VaadinElement;
import org.vaadin.addons.dramafinder.element.query.ElementQueries;
import org.vaadin.addons.dramafinder.element.query.TagQuery;

/** One test on Playwright: one browser context and page. */
final class PlaywrightSession implements AdapterSession {

    private final BrowserContext context;
    private final Page page;
    private final String baseUrl;

    private final boolean ownsContext;
    private final java.util.List<PlaywrightSession> children = new ArrayList<>();

    private final double timeoutMillis;
    /** Whether the context records a trace ({@code vaadin.testing.playwright.trace}), and when to keep it. */
    private final TraceMode trace;

    /** {@code vaadin.testing.playwright.trace}: {@code off} (default), {@code on}, or {@code on-failure}. */
    enum TraceMode {
        OFF, ON, ON_FAILURE;

        static TraceMode parse(String value) {
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "", "off", "false" -> OFF;
                case "on", "true" -> ON;
                case "on-failure", "retain-on-failure" -> ON_FAILURE;
                default -> throw new IllegalArgumentException(PlaywrightAdapter.TRACE
                        + ": expected off, on or on-failure, got " + value);
            };
        }
    }

    PlaywrightSession(BrowserContext context, Page page, String baseUrl, double timeoutMillis, TraceMode trace) {
        this(context, page, baseUrl, timeoutMillis, true, trace);
    }

    private PlaywrightSession(BrowserContext context, Page page, String baseUrl, double timeoutMillis,
            boolean ownsContext, TraceMode trace) {
        this.context = context;
        this.page = page;
        this.baseUrl = baseUrl;
        this.timeoutMillis = timeoutMillis;
        this.ownsContext = ownsContext;
        this.trace = trace;
        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                // "Failed to load resource: … 404" doesn't say which one: the location does
                String location = message.location() == null ? "" : message.location().replaceFirst("(:\\d+){1,2}$", "");
                // every browser asks for /favicon.ico by itself; an application without one did nothing wrong
                if (!location.endsWith("/favicon.ico")) {
                    consoleErrors.add(message.text() + (location.isBlank() || message.text().contains(location) ? ""
                            : " (" + location + ")"));
                }
            }
        });
        page.onPageError(error -> consoleErrors.add(error));
    }


    /** What the page logged with console.error, and the exceptions no script caught, since they were last read. */
    private final List<String> consoleErrors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @Override
    public List<String> consoleErrors() {
        synchronized (consoleErrors) {
            List<String> errors = List.copyOf(consoleErrors);
            consoleErrors.clear();
            return errors;
        }
    }

    /** A new page in the same browser context: same cookies, so the same HTTP and Vaadin session. */
    @Override
    public AdapterSession openWindow() {
        Page second = context.newPage();
        // the same context, so the same trace
        return new PlaywrightSession(context, second, baseUrl, timeoutMillis, false, trace);
    }

    /** A new browser context (own cookies, own session), logged in through the test-only endpoint. */
    @Override
    public AdapterSession openAsUser(String username, String... roles) {
        String loginUrl = LoginSupport.userUrl(baseUrl, username, roles);
        PlaywrightSession session = newContext();
        session.logIn(loginUrl);
        return session;
    }

    /** A new browser context (own cookies, own session) with nobody signed in. */
    @Override
    public AdapterSession openNewSession() {
        return newContext();
    }

    private PlaywrightSession newContext() {
        BrowserContext other = context.browser().newContext();
        other.setDefaultTimeout(timeoutMillis);
        // a context of its own: not traced
        PlaywrightSession session = new PlaywrightSession(other, other.newPage(), baseUrl, timeoutMillis, true,
                TraceMode.OFF);
        children.add(session);
        return session;
    }

    /** Opens the test-only login URL: the browser context's session belongs to that user afterwards. */
    void logIn(String loginUrl) {
        com.microsoft.playwright.Response response = page.navigate(loginUrl);
        if (response == null || !response.ok()) {
            throw new IllegalStateException("Test login failed: " + (response == null ? "no response" : response.status()
                    + " " + response.text()));
        }
    }

    Page page() {
        return page;
    }

    // ---------------------------------------------------------------- finding

    @Override
    public List<UnifiedElement> find(QuerySpec spec) {
        PlaywrightComponents.Support support = PlaywrightComponents.of(spec.componentType());
        Locator scope = spec.parent() == null ? null : ((PlaywrightElement) spec.parent()).locator();
        if (spec.inTemplate()) {
            return findInTemplate(support, scope, spec);
        }
        if (support.dramafinderElement() != null) {
            return findWithDramafinder(support, scope, spec.filters());
        }
        if (spec.tag() == null) {
            throw new IllegalArgumentException(spec.componentType().getName()
                    + " has no @Tag; set @UnifiedComponent(tag = \"...\")");
        }
        // Dramafinder's tag query takes the filters CSS can express; the rest are checked in the page with the
        // Unified API's matching, for every candidate in one round trip: no browser call per element, and no
        // element detaching between two calls.
        if (BrowserQueries.isTagList(spec.tag())) {
            // several tags (UnifiedLogin): Dramafinder's tag query takes one, so a plain CSS locator, with the
            // filters CSS can express, as findInTemplate builds it
            String selector = BrowserQueries.selector(spec.tag(), spec.filters());
            return matching(support, scope == null ? page.locator(selector) : scope.locator(selector), spec);
        }
        // "*" (VaadinElement.ANY_TAG) queries every light-DOM element, whatever its tag
        TagQuery<GenericElement> query = scope == null ? ElementQueries.find(page, spec.tag())
                : ElementQueries.find(scope, spec.tag());
        for (Filter f : spec.filters()) {
            if (BrowserQueries.selectorPart(f) != null) {
                query = switch (f.kind()) {
                    case ID -> query.withId(f.value());
                    case TEST_ID -> query.withTestId(f.value());
                    case CLASS_NAME -> query.withClassName(f.value());
                    case THEME -> query.withTheme(f.value());
                    case ATTRIBUTE -> query.withAttribute(f.name(), f.value());
                    default -> throw new IllegalStateException("Not a CSS filter: " + f);
                };
            }
        }
        return matching(support, query.getLocator(), spec);
    }

    /**
     * The {@code candidates} that pass the filters CSS can't express, checked in the page with the Unified API's
     * matching, and that {@code support} includes.
     */
    private List<UnifiedElement> matching(PlaywrightComponents.Support support, Locator candidates, QuerySpec spec) {
        List<Map<String, Object>> remaining = BrowserQueries.remaining(spec.filters());
        List<Integer> indices = new ArrayList<>();
        for (Object i : (List<?>) candidates.evaluateAll(BrowserQueries.MATCH, remaining)) {
            indices.add(((Number) i).intValue());
        }
        List<UnifiedElement> result = new ArrayList<>();
        for (int i : indices) {
            Locator locator = candidates.nth(i);
            if (support.include(locator)) {
                result.add(new PlaywrightElement(this, locator, null, support, candidates, i));
            }
        }
        return result;
    }

    /**
     * A lookup inside a Template the test entered (spec 04, "Templates"). Playwright's CSS enters every shadow root
     * under the Template, so the candidates are filtered in the page, in one round trip with the other filters
     * ({@link BrowserQueries#MATCH_IN_TEMPLATE}), down to what Selenium's query finds there.
     */
    private List<UnifiedElement> findInTemplate(PlaywrightComponents.Support support, Locator template,
            QuerySpec spec) {
        if (spec.tag() == null) {
            throw new IllegalArgumentException(spec.componentType().getName()
                    + " has no @Tag; set @UnifiedComponent(tag = \"...\")");
        }
        Locator candidates = template.locator(BrowserQueries.selector(spec.tag(), spec.filters()));
        com.microsoft.playwright.ElementHandle host = template.elementHandle();
        List<UnifiedElement> result = new ArrayList<>();
        try {
            for (Object i : (List<?>) candidates.evaluateAll(BrowserQueries.MATCH_IN_TEMPLATE,
                    Map.of("host", host, "filters", BrowserQueries.remaining(spec.filters())))) {
                int index = ((Number) i).intValue();
                Locator locator = candidates.nth(index);
                if (support.include(locator)) {
                    result.add(new PlaywrightElement(this, locator, dramafinderElement(support, locator), support,
                            candidates, index));
                }
            }
        } finally {
            host.dispose();
        }
        return result;
    }

    /** One round trip: every element under the scope, kept in the page as {@link BrowserQueries#TEMPLATES} would. */
    @Override
    public List<UnifiedElement> findTemplates(UnifiedElement parent, boolean inTemplate) {
        Locator scope = parent == null ? null : ((PlaywrightElement) parent).locator();
        Locator candidates = scope == null ? page.locator("*") : scope.locator("*");
        com.microsoft.playwright.ElementHandle host = scope == null ? null : scope.elementHandle();
        PlaywrightComponents.Support support = PlaywrightComponents.of(Component.class);
        Map<String, Object> arg = new java.util.HashMap<>();
        arg.put("scope", host);
        arg.put("inTemplate", inTemplate);
        try {
            List<UnifiedElement> result = new ArrayList<>();
            for (Object i : (List<?>) candidates.evaluateAll(BrowserQueries.TEMPLATES_AMONG, arg)) {
                int index = ((Number) i).intValue();
                result.add(new PlaywrightElement(this, candidates.nth(index), null, support, candidates, index));
            }
            return result;
        } finally {
            if (host != null) {
                host.dispose();
            }
        }
    }

    /** Dramafinder's element of the component's type over {@code locator}, for the actions it offers; or null. */
    private static VaadinElement dramafinderElement(PlaywrightComponents.Support support, Locator locator) {
        Class<? extends VaadinElement> type = support.dramafinderElement();
        if (type == null) {
            return null;
        }
        try {
            return type.getConstructor(Locator.class).newInstance(locator);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getName() + " has no constructor taking a Locator", e);
        }
    }

    /**
     * One round trip per query's candidates: elements found together are read by their index in one
     * {@code evaluateAll}; any other element is read on its own.
     */
    @Override
    public List<com.vaadin.testing.unified.spi.ElementFacts> facts(List<UnifiedElement> elements) {
        com.vaadin.testing.unified.spi.ElementFacts[] facts = new com.vaadin.testing.unified.spi.ElementFacts[elements.size()];
        java.util.Map<Locator, List<Integer>> groups = new java.util.IdentityHashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof PlaywrightElement e && e.candidates() != null) {
                groups.computeIfAbsent(e.candidates(), k -> new ArrayList<>()).add(i);
            } else if (elements.get(i) instanceof PlaywrightElement e) {
                facts[i] = BrowserQueries.facts(e.locator().evaluate(BrowserQueries.FACTS));
            } else {
                facts[i] = com.vaadin.testing.unified.spi.ElementFacts.read(elements.get(i));
            }
        }
        groups.forEach((candidates, positions) -> {
            List<Integer> indices = positions.stream().map(p -> ((PlaywrightElement) elements.get(p)).index()).toList();
            List<?> read = (List<?>) candidates.evaluateAll(
                    "(els, idx) => idx.map(i => els[i] ? (" + BrowserQueries.FACTS + ")(els[i]) : null)", indices);
            for (int j = 0; j < positions.size(); j++) {
                Object one = j < read.size() ? read.get(j) : null;
                facts[positions.get(j)] = one == null ? null : BrowserQueries.facts(one);
            }
        });
        return java.util.Arrays.asList(facts);
    }

    @Override
    public String browserName() {
        return context.browser() == null ? null : context.browser().browserType().name();
    }

    private List<UnifiedElement> findWithDramafinder(PlaywrightComponents.Support support, Locator scope,
            List<Filter> filters) {
        Class<? extends VaadinElement> type = support.dramafinderElement();
        Object query = scope == null
                ? invokeStatic("find", new Class<?>[] { Page.class, Class.class }, page, type)
                : invokeStatic("find", new Class<?>[] { Locator.class, Class.class }, scope, type);
        List<Filter> inPage = new ArrayList<>();
        for (Filter f : filters) {
            if (f.kind() == Filter.Kind.LABEL || f.kind() == Filter.Kind.ACCESSIBLE_NAME
                    || f.kind() == Filter.Kind.ATTRIBUTE && f.value() == null
                    || f.kind() == Filter.Kind.WITHOUT_ATTRIBUTE) {
                // Dramafinder's withLabel reads the field's own label slot only; the label property or a FormLayout
                // item's label is checked in the page, with the Unified API's matching, as on Selenium (#93).
                // Dramafinder's withAccessibleName matches each aria-labelledby target on its own: the name is
                // checked in the page as getAccessibleName() reads it, all targets joined (#641)
                inPage.add(f);
                continue;
            }
            String method = switch (f.kind()) {
                case LABEL -> "withLabel";
                case ACCESSIBLE_NAME -> "withAccessibleName";
                case TEXT -> "withText";
                case PLACEHOLDER -> "withPlaceholder";
                case TEST_ID -> "withTestId";
                case ID -> "withId";
                case CLASS_NAME -> "withClassName";
                case THEME, ATTRIBUTE, WITHOUT_ATTRIBUTE -> null;
            };
            if (f.kind() == Filter.Kind.THEME) {
                query = invoke(query, "withTheme", new Class<?>[] { String.class }, f.value());
            } else if (f.kind() == Filter.Kind.ATTRIBUTE) {
                query = invoke(query, "withAttribute", new Class<?>[] { String.class, String.class }, f.name(), f.value());
            } else {
                query = invoke(query, f.exact() ? method : method + "Containing", new Class<?>[] { String.class },
                        f.value());
            }
        }
        Locator candidates = (Locator) invoke(query, "getLocator", new Class<?>[0]);
        List<UnifiedElement> result = new ArrayList<>();
        if (!inPage.isEmpty()) {
            // the in-page check returns the matching candidates' indices: element i is the query's get(i), with
            // no count of its own (#402)
            for (Object i : (List<?>) candidates.evaluateAll(BrowserQueries.MATCH, BrowserQueries.remaining(inPage))) {
                int index = ((Number) i).intValue();
                VaadinElement e = (VaadinElement) invoke(query, "get", new Class<?>[] { int.class }, index);
                if (support.include(e.getLocator())) {
                    result.add(new PlaywrightElement(this, e.getLocator(), e, support, candidates, index));
                }
            }
            return result;
        }
        // all() returns the query locator's matches in order: element i is candidates.nth(i)
        @SuppressWarnings("unchecked")
        List<VaadinElement> found = (List<VaadinElement>) invoke(query, "all", new Class<?>[0]);
        for (int i = 0; i < found.size(); i++) {
            VaadinElement e = found.get(i);
            if (support.include(e.getLocator())) {
                result.add(new PlaywrightElement(this, e.getLocator(), e, support, candidates, i));
            }
        }
        return result;
    }

    private static Object invokeStatic(String name, Class<?>[] types, Object... args) {
        try {
            return ElementQueries.class.getMethod(name, types).invoke(null, args);
        } catch (InvocationTargetException e) {
            throw unwrapped(e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, types);
            return method.invoke(target, args);
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException(target.getClass().getSimpleName() + " has no " + name
                    + " filter in Dramafinder", e);
        } catch (InvocationTargetException e) {
            throw unwrapped(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RuntimeException unwrapped(InvocationTargetException e) {
        return e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
    }

    // ---------------------------------------------------------------- navigation

    @Override
    public void navigate(Class<? extends Component> view) {
        RouteNavigation.navigate(page, baseUrl, view);
    }

    @Override
    public void navigate(Class<? extends Component> view, Object urlParameter) {
        RouteNavigation.navigate(page, baseUrl, view, urlParameter);
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters) {
        RouteNavigation.open(page, baseUrl, RouteNavigation.pathOf(view, parameters));
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters, QueryParameters query) {
        String queryString = query.getQueryString();
        RouteNavigation.open(page, baseUrl, RouteNavigation.pathOf(view, parameters)
                + (queryString.isEmpty() ? "" : "?" + queryString));
    }

    @Override
    public void navigate(String path) {
        if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            page.navigate(path);
            AutoWait.waitForVaadinIdle(page);
        } else {
            RouteNavigation.open(page, baseUrl, path.startsWith("/") ? path.substring(1) : path);
        }
    }

    @Override
    public void reload() {
        page.reload();
        AutoWait.waitForVaadinIdle(page);
    }


    @Override
    public boolean isAt(Class<? extends Component> view) {
        if (!CurrentView.hasRoute(view)) {
            return CurrentView.shows(currentViews(), view);
        }
        // the route's template and each @RouteAlias of the view (Dramafinder 2.2.2+, #530)
        return RouteNavigation.isAt(page, baseUrl, view);
    }

    /** The current-view endpoint's answer for the page's UI, or null (no Vaadin UI, or no endpoint). */
    private String currentViews() {
        Optional<Integer> ui = VaadinClient.uiId(page);
        if (ui.isEmpty()) {
            return null;
        }
        APIResponse response = page.request().get(baseUrl + CurrentView.PATH + "?ui=" + ui.get());
        try {
            return response.ok() ? response.text() : null;
        } finally {
            response.dispose();
        }
    }

    /** The path in the page's location: it already sees a client-side navigation. Null outside the app. */
    @Override
    public String currentPath() {
        return RouteNavigation.currentPath(page, baseUrl);
    }

    @Override
    public Optional<String> queryParameter(String name) {
        return RouteNavigation.queryParameter(page, name);
    }

    @Override
    public String title() {
        return page.title();
    }

    // ---------------------------------------------------------------- the rest

    @Override
    public void sync() {
        // nothing to flush: the browser runs on its own
    }

    /**
     * Waits after an action, in one call to the page when nothing is pending (#402): for the round trip it started,
     * for a server-side navigation to reach the address bar (#233), and for a page load it started, until Flow has
     * started the new page's UI (#397). All of it is Dramafinder's wait since 2.2.3 (parttio/dramafinder#304, #305,
     * #320). It sees a page load through the Navigation API listener it installs in each page on its first wait
     * there, and the session waits on every page it opens.
     */
    @Override
    public void settle() {
        AutoWait.waitForVaadinIdle(page);
    }

    /**
     * How long an action waits, before acting, for a Flow client that is still busy (#604, ADR 0014): then it acts
     * anyway, so an app that stays busy (a short {@code @Poll} interval, a long request) can still be driven.
     */
    static final int FLOW_IDLE_CAP_MS = 5000;

    /** What {@link #awaitFlowIdle()} found. */
    enum FlowIdle {
        /** No Flow client was busy: nothing waited. */
        IDLE,
        /** A Flow client was busy: waited until it was idle, or for the cap. */
        WAITED,
        /** The page navigated meanwhile. */
        GONE
    }

    /** Waits until no Flow client is busy, at most {@link #FLOW_IDLE_CAP_MS}; one call to the page. */
    FlowIdle awaitFlowIdle() {
        try {
            return Boolean.TRUE.equals(page.evaluate(AWAIT_FLOW_IDLE, FLOW_IDLE_CAP_MS)) ? FlowIdle.WAITED
                    : FlowIdle.IDLE;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return FlowIdle.GONE;
        }
    }

    private static final String AWAIT_FLOW_IDLE = """
            async (idleMs) => {
              const busy = () => {
                const clients = window.Vaadin?.Flow?.clients;
                return !!clients && Object.values(clients).some(c => c.isActive && c.isActive());
              };
              if (!busy()) return false;
              for (let t = 0; t < idleMs && busy(); t += 10) {
                await new Promise(r => setTimeout(r, 10));
              }
              return true;
            }""";


    /** A full-page screenshot, with where it shows the failed step's Step Target. */
    @Override
    public com.vaadin.testing.unified.spi.Evidence writeFailureEvidence(Path directory, String name, com.vaadin.testing.unified.UnifiedElement target)
            throws java.io.IOException {
        java.nio.file.Files.createDirectories(directory);
        Path file = directory.resolve(name + ".png");
        page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true));
        return new com.vaadin.testing.unified.spi.Evidence(List.of(file), highlight(target, true));
    }

    /** The viewport after the step: what the user saw, with where it shows the Step Target. */
    @Override
    public com.vaadin.testing.unified.spi.Evidence writeStepSnapshot(Path directory, String name,
            com.vaadin.testing.unified.UnifiedElement target) throws java.io.IOException {
        java.nio.file.Files.createDirectories(directory);
        Path file = directory.resolve(name + ".png");
        page.screenshot(new Page.ScreenshotOptions().setPath(file));
        return new com.vaadin.testing.unified.spi.Evidence(List.of(file), highlight(target, false));
    }

    /**
     * The Step Target's box in fractions of the screenshot: the viewport, or the whole document for a full-page
     * one; null without a target. Read with {@code evaluateAll}, which doesn't wait for an element that is gone.
     */
    private com.vaadin.testing.unified.spi.Highlight highlight(com.vaadin.testing.unified.UnifiedElement target,
            boolean fullPage) {
        if (!(target instanceof PlaywrightElement element)) {
            return null;
        }
        Object result;
        try {
            result = element.locator().evaluateAll(TARGET_BOX, fullPage);
        } catch (RuntimeException e) {
            return null; // the screenshot is kept without a box: a highlight never costs the snapshot
        }
        if (!(result instanceof List<?> box) || box.size() != 6) {
            return com.vaadin.testing.unified.spi.Highlight.gone();
        }
        return com.vaadin.testing.unified.spi.Highlight.of(number(box.get(0)), number(box.get(1)), number(box.get(2)),
                number(box.get(3)), number(box.get(4)), number(box.get(5)));
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    /**
     * The first element's box in pixels, and the size of what the screenshot shows: [left, top, width, height,
     * image width, image height]. The viewport, or with {@code fullPage} the whole document, in which the box
     * moves by the scroll position. Null when it is no longer on the page or not shown.
     */
    private static final String TARGET_BOX = """
            (els, fullPage) => {
              if (!els.length || !els[0].isConnected || !els[0].checkVisibility()) return null;
              const r = els[0].getBoundingClientRect(), d = document.documentElement;
              return fullPage
                  ? [r.left + scrollX, r.top + scrollY, r.width, r.height,
                     Math.max(d.scrollWidth, innerWidth), Math.max(d.scrollHeight, innerHeight)]
                  : [r.left, r.top, r.width, r.height, innerWidth, innerHeight];
            }""";

    @Override
    public boolean tracesSteps() {
        return trace != TraceMode.OFF;
    }

    /** A group in the trace: Playwright's trace viewer lists the step's actions under its words. */
    @Override
    public void stepStarted(String text) {
        context.tracing().group(text);
    }

    @Override
    public void stepEnded() {
        context.tracing().groupEnd();
    }

    /** Stops the trace: kept as {@code trace.zip} in the test's evidence directory when its mode says so. */
    @Override
    public List<Path> testEnded(Path testEvidenceDirectory, boolean failed) throws java.io.IOException {
        if (trace == TraceMode.OFF || !ownsContext) {
            return List.of();
        }
        Path file = testEvidenceDirectory.resolve(TRACE_FILE);
        java.nio.file.Files.deleteIfExists(file);
        if (trace == TraceMode.ON || failed) {
            java.nio.file.Files.createDirectories(testEvidenceDirectory);
            context.tracing().stop(new com.microsoft.playwright.Tracing.StopOptions().setPath(file));
            return List.of(file);
        }
        context.tracing().stop();
        return List.of();
    }

    static final String TRACE_FILE = "trace.zip";

    @Override
    public void pressKey(String key) {
        page.keyboard().press(key);
        AutoWait.afterAction(page);
    }

    /** Exact, for this page only, any size: Playwright emulates the Viewport (ADR 0011). */
    @Override
    public void setViewport(com.vaadin.testing.unified.ViewportSize size) {
        page.setViewportSize(size.width(), size.height());
    }

    /** The core's layout script, unchanged, with the subject's element (ADR 0011). One round trip. */
    @Override
    public String measureLayout(java.util.List<com.vaadin.testing.unified.spi.LayoutScript.Target> targets,
            com.vaadin.testing.unified.UnifiedElement subject) {
        String argument = com.vaadin.testing.unified.spi.LayoutScript.argument(targets);
        if (subject instanceof PlaywrightElement element) {
            return (String) element.locator().evaluate("(element, argument) => ("
                    + com.vaadin.testing.unified.spi.LayoutScript.SOURCE + ")([argument, element])", argument);
        }
        return (String) page.evaluate(com.vaadin.testing.unified.spi.LayoutScript.SOURCE,
                java.util.Arrays.asList(argument, null));
    }

    @Override
    public void screenshot(Path file) {
        page.screenshot(new Page.ScreenshotOptions().setPath(file));
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (type.isInstance(page)) {
            return Optional.of(type.cast(page));
        }
        if (type.isInstance(context)) {
            return Optional.of(type.cast(context));
        }
        if (type.isInstance(context.browser())) {
            return Optional.of(type.cast(context.browser()));
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        for (PlaywrightSession child : children) {
            child.close();
        }
        if (ownsContext) {
            context.close(); // closes the windows opened from this one too
        } else {
            page.close();
        }
    }

    /** Exactly this text, surrounding whitespace aside. Escaped by hand: Playwright runs it as a JS regex. */
    static Pattern exactText(String text) {
        return Pattern.compile("^\\s*" + text.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0") + "\\s*$");
    }
}
