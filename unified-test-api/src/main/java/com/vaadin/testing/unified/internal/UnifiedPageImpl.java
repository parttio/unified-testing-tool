package com.vaadin.testing.unified.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.component.UnifiedGenericComponent;
import com.vaadin.testing.unified.query.CommonFilters;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.List;
import com.vaadin.testing.unified.internal.steps.Phrase;
import com.vaadin.testing.unified.internal.steps.Phrases;

/** The injected {@link UnifiedPage}: delegates to the run's adapter session. */
public final class UnifiedPageImpl implements UnifiedPage {

    /** The gap reason for {@link #waitUntil}. */
    static final String WAIT_UNTIL = "waitUntil";

    private final TestRun run;

    public UnifiedPageImpl(TestRun run) {
        this.run = run;
    }

    public TestRun run() {
        return run;
    }

    @Override
    public <W extends UnifiedWrapper & CommonFilters<W>> W find(Class<W> wrapperType) {
        return Wrappers.newWrapper(run, wrapperType, null);
    }

    @Override
    public <C extends Component> UnifiedGenericComponent findComponent(Class<C> componentType) {
        return Wrappers.newComponent(run, componentType, null);
    }

    @Override
    public void navigate(Class<? extends Component> view) {
        record(() -> Phrases.open(view, null), () -> run.session().navigate(view));
    }

    @Override
    public <T, C extends Component & HasUrlParameter<T>> void navigate(Class<C> view, T parameter) {
        record(() -> Phrases.open(view, parameter), () -> run.session().navigate(view, parameter));
    }

    @Override
    public void navigate(Class<? extends Component> view, RouteParameters parameters) {
        record(() -> Phrases.open(view, parameters.getParameterNames().stream()
                .map(name -> name + "=" + parameters.get(name).orElse(""))
                .collect(Collectors.joining(", "))), () -> run.session().navigate(view, parameters));
    }

    @Override
    public void navigate(String path) {
        record(() -> Phrases.open(path), () -> {
            if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                run.requireBrowser("Navigating to an absolute URL");
            }
            run.session().navigate(path);
        });
    }

    @Override
    public void reload() {
        record(() -> Phrases.page("reload", "reload the page"), () -> run.session().reload());
    }

    /** A condition the User Steps can't read: recorded as a gap, whatever it calls while polling. */
    @Override
    public void waitUntil(BooleanSupplier condition) {
        run.steps().opaque(WAIT_UNTIL, run.window(),
                () -> run.poll(() -> condition.getAsBoolean() ? Boolean.TRUE : null, () -> "Condition not met"));
    }

    @Override
    public void pressKey(String key) {
        record(() -> Phrases.pressKey(key), () -> run.session().pressKey(key));
    }

    @Override
    public UnifiedPage openWindow() {
        String window = run.steps().nextWindow();
        return run.steps().action(() -> Phrases.page("open", "open " + window), run.window(),
                () -> new UnifiedPageImpl(run.inWindow(run.session().openWindow(), window)));
    }

    @Override
    public UnifiedPage openAsUser(String username, String... roles) {
        String window = "user " + username;
        return run.steps().action(() -> Phrase.template("open", "open a window as user {value}", null,
                        List.of(username)), run.window(),
                () -> new UnifiedPageImpl(run.inWindow(run.session().openAsUser(username, roles), window)));
    }

    @Override
    public void step(String name, Runnable userSteps) {
        run.steps().group(name, run.window(), userSteps);
    }

    @Override
    public String secret(String value) {
        return run.steps().secret(value);
    }

    private void record(Supplier<Phrase> phrase, Runnable action) {
        run.steps().action(phrase, run.window(), action);
    }

    @Override
    public boolean hasRealBrowser() {
        return run.tool().hasRealBrowser();
    }

    @Override
    public void screenshot(Path file) {
        run.requireBrowser("screenshot");
        run.session().screenshot(file);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return tryUnwrap(type).orElseThrow(() -> new UnsupportedOperationException(run.tool()
                + " can't provide " + type.getName() + ". If the test needs another Testing Tool, annotate it "
                + "with @RequiresTestingTool so it is skipped on " + run.tool() + "."));
    }

    @Override
    public <T> Optional<T> tryUnwrap(Class<T> type) {
        run.steps().gap(LazyElement.ESCAPE_HATCH, run.window());
        return run.session().unwrap(type);
    }

    @Override
    public String toString() {
        return "UnifiedPage[" + run.tool() + "]";
    }
}
