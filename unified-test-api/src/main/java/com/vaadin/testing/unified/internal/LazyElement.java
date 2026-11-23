package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.UnifiedElement;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.vaadin.testing.unified.internal.steps.Phrase;
import com.vaadin.testing.unified.internal.steps.Phrases;

/**
 * The Unified Element behind every wrapper. Finds its component again on every call (lazy), waiting until
 * exactly one matches ({@code index < 0}) or the indexed one exists. Actions also wait until the component
 * is visible and enabled; the adapter waits for the server round-trip after the action.
 */
public final class LazyElement implements UnifiedElement {

    /** The gap reason for calls into the Testing Tool's own API. */
    public static final String ESCAPE_HATCH = "Escape Hatch";

    private final ElementResolver source;
    private final int index;
    private final TestRun run;

    public LazyElement(ElementResolver source, int index, TestRun run) {
        this.source = source;
        this.index = index;
        this.run = run;
    }

    public LazyElement withRun(TestRun newRun) {
        return new LazyElement(source, index, newRun);
    }

    public TestRun run() {
        return run;
    }

    /** What this element finds: a lookup, or a part of a component such as a grid cell. */
    public ElementResolver source() {
        return source;
    }

    /** The position among the matches, or {@code -1} for "exactly one". */
    public int index() {
        return index;
    }

    /** The same position in another lookup, e.g. one with a filter more. */
    public LazyElement withSource(ElementResolver newSource) {
        return new LazyElement(newSource, index, run);
    }

    public String describe() {
        if (!(source instanceof ElementSource)) {
            return source.describe();
        }
        return source.describe() + (index < 0 ? "" : ".all().get(" + index + ")");
    }

    /**
     * How many components the lookup matches right now, ignoring {@link #index()}; no waiting. A parent scope
     * that can't be singled out right now counts as no match.
     */
    public int countNow() {
        run.steps().read();
        if (source instanceof ElementSource s) {
            return s.resolveNow().size();
        }
        return StaleRetry.call(run, () -> source.resolve().size());
    }

    /**
     * Whether the lookup still finds this element, without waiting: after an action whose check couldn't read the
     * result, an element the app replaced or navigated away from has nothing to check (#377, #554).
     */
    public boolean stillFound() {
        return countNow() > Math.max(index, 0);
    }

    /** The adapter's element, waiting until it can be singled out. */
    public UnifiedElement concrete() {
        int[] lastCount = {0};
        return run.pollFor(() -> {
            List<UnifiedElement> found = source.resolve();
            lastCount[0] = found.size();
            UnifiedElement one = index < 0 ? (found.size() == 1 ? found.get(0) : null)
                    : index < found.size() ? found.get(index) : null;
            if (one != null) {
                run.steps().singledOut(one);
            }
            return one;
        }, () -> LookupFailures.notSingledOut(this, lastCount[0]));
    }

    /** The adapter's element if it can be singled out right now; no waiting. */
    public Optional<UnifiedElement> tryConcrete() {
        List<UnifiedElement> found = source.resolve();
        if (index < 0) {
            return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
        }
        return index < found.size() ? Optional.of(found.get(index)) : Optional.empty();
    }

    /**
     * Runs a user action once the element is visible and enabled (Auto-Wait). Called from a Wrapper method, it
     * makes that call a User Step; otherwise it records one of its own, from {@code action}.
     */
    public void act(String action, Consumer<UnifiedElement> body) {
        act(() -> Phrases.forElementAction(action, this, null), action, body);
    }

    private void act(Supplier<Phrase> phrase, String action, Consumer<UnifiedElement> body) {
        act(phrase, action, LazyElement::usable, body);
    }

    /**
     * {@link #act(String, Consumer)} for a component the user can also act on in another state than visible and
     * enabled, such as a date picker whose calendar is open while its field is hidden: Auto-Wait waits for
     * {@code ready} instead. The User Step reads as {@code phrase}.
     */
    public void act(Supplier<Phrase> phrase, String action, java.util.function.Predicate<UnifiedElement> ready,
            Consumer<UnifiedElement> body) {
        run.steps().action(phrase, run.window(), () -> StaleRetry.call(run, () -> {
            UnifiedElement element = run.pollFor(() -> {
                UnifiedElement e = concrete();
                return ready.test(e) ? e : null;
            }, () -> LookupFailures.notUsable(this));
            body.accept(element);
            run.session().settle();
            return null;
        }));
    }

    /** Auto-Wait's check: visible and enabled, in one read where the adapter offers it (#402). */
    public static boolean usable(UnifiedElement e) {
        return e instanceof com.vaadin.testing.unified.spi.UsabilityCheck check ? check.isVisibleAndEnabled()
                : e.isVisible() && e.isEnabled();
    }

    /** Reads from the element, finding it again if it went stale meanwhile; counted as a read by the reports. */
    private <T> T read(java.util.function.Function<UnifiedElement, T> reader) {
        run.steps().read();
        return on(reader);
    }

    /** Calls the element, finding it again if it went stale meanwhile. */
    private <T> T on(java.util.function.Function<UnifiedElement, T> call) {
        return StaleRetry.call(run, () -> call.apply(concrete()));
    }

    @Override public String getTag() { return read(UnifiedElement::getTag); }
    @Override public String getText() { return read(UnifiedElement::getText); }
    @Override public String getVisibleText() {
        run.requireBrowser("getVisibleText");
        return read(UnifiedElement::getVisibleText);
    }
    @Override public String getInlineStyle(String name) { return read(e -> e.getInlineStyle(name)); }
    @Override public String getAccessibleName() { return read(UnifiedElement::getAccessibleName); }
    @Override public String getProperty(String name) { return read(e -> e.getProperty(name)); }
    @Override public Integer getPropertyInteger(String name) { return read(e -> e.getPropertyInteger(name)); }
    @Override public Boolean getPropertyBoolean(String name) { return read(e -> e.getPropertyBoolean(name)); }
    @Override public String getAttribute(String name) { return read(e -> e.getAttribute(name)); }
    @Override public String getCssValue(String property) { return read(e -> e.getCssValue(property)); }
    @Override public boolean isVisible() { return read(UnifiedElement::isVisible); }
    @Override public boolean isEnabled() { return read(UnifiedElement::isEnabled); }
    @Override public List<UnifiedElement> findChildren(String tag) { return read(e -> e.findChildren(tag)); }
    @Override public List<UnifiedElement> findDescendants(String tag) { return read(e -> e.findDescendants(tag)); }

    @Override public void setProperty(String name, Object value) { on(e -> { e.setProperty(name, value); return null; }); }
    @Override public void click() { act("click", UnifiedElement::click); }
    @Override public void setValueAsUser(Object value) {
        act(() -> Phrases.forElementAction("set the value of", this, value), "set the value of",
                e -> e.setValueAsUser(value));
    }
    @Override public void focus() { act("focus", UnifiedElement::focus); }
    @Override public void blur() { on(e -> { e.blur(); return null; }); }
    @Override public void pressKey(String key) {
        act(() -> Phrases.forElementAction("press " + key + " on", this, key), "press " + key + " on",
                e -> e.pressKey(key));
    }
    @Override public void dispatchEvent(String eventName) { on(e -> { e.dispatchEvent(eventName); return null; }); }

    @Override
    public <T> T unwrap(Class<T> type) {
        return tryUnwrap(type).orElseThrow(() -> new UnsupportedOperationException(
                run.tool() + " can't provide " + type.getName() + " for " + describe()
                        + ". If the test needs another Testing Tool, annotate it with "
                        + "@RequiresTestingTool so it is skipped on " + run.tool() + "."));
    }

    @Override
    public <T> Optional<T> tryUnwrap(Class<T> type) {
        if (!type.getName().startsWith("com.vaadin.testing.unified.")) {
            // the Testing Tool's own type: the Escape Hatch, which the User Steps can't see into
            run.steps().gap(ESCAPE_HATCH, run.window());
        } else {
            // a Testing Tool's access (grid, selection, tabs): what Wrapper getters read through
            run.steps().read();
        }
        return concrete().tryUnwrap(type);
    }

    @Override
    public String toString() {
        return describe();
    }
}
