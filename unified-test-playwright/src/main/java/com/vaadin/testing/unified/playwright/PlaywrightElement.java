package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.internal.BrowserQueries;
import java.util.List;
import java.util.Optional;
import org.vaadin.addons.dramafinder.AutoWait;
import org.vaadin.addons.dramafinder.element.GenericElement;
import org.vaadin.addons.dramafinder.element.VaadinElement;
import org.vaadin.addons.dramafinder.element.shared.FocusableElement;
import org.vaadin.addons.dramafinder.element.shared.HasValueElement;

/**
 * Unified Element on Playwright: a locator for one component, with its Dramafinder element when the
 * component has one. Reads and actions go through Dramafinder: the component's own element where it exists,
 * else a generic Dramafinder element on the locator. Dramafinder's actions wait for the server round-trip;
 * actions on a raw locator wait here.
 */
final class PlaywrightElement implements UnifiedElement, com.vaadin.testing.unified.spi.UsabilityCheck {

    private final PlaywrightSession session;
    private final Locator locator;
    private final VaadinElement dramafinder;
    /** The generic Dramafinder element on the same locator: what every component has. */
    private final GenericElement generic;
    private final PlaywrightComponents.Support support;

    /** The query's candidates this element is the {@link #index}-th of, or null: one read covers them all. */
    private final Locator candidates;
    private final int index;

    PlaywrightElement(PlaywrightSession session, Locator locator, VaadinElement dramafinder,
            PlaywrightComponents.Support support) {
        this(session, locator, dramafinder, support, null, -1);
    }

    PlaywrightElement(PlaywrightSession session, Locator locator, VaadinElement dramafinder,
            PlaywrightComponents.Support support, Locator candidates, int index) {
        this.session = session;
        this.locator = locator;
        this.dramafinder = dramafinder;
        this.generic = new GenericElement(locator);
        this.support = support;
        this.candidates = candidates;
        this.index = index;
    }

    Locator candidates() {
        return candidates;
    }

    int index() {
        return index;
    }

    Locator locator() {
        return locator;
    }

    private Page page() {
        return session.page();
    }

    // ---------------------------------------------------------------- reading

    @Override
    public String getTag() {
        return generic.getTagName();
    }

    /**
     * The DOM text, as {@link BrowserQueries#TEXT} reads it: what Browserless reads on the server. Not
     * Dramafinder's {@code getText()}, whose {@code textContent} holds a tooltip's text.
     */
    @Override
    public String getText() {
        Optional<String> special = support.text(locator);
        return BrowserQueries.normalize(special.orElseGet(() -> (String) locator.evaluate(BrowserQueries.TEXT)));
    }

    @Override
    public String getVisibleText() {
        return locator.innerText().strip();
    }

    @Override
    public String getInlineStyle(String name) {
        String value = generic.getInlineStyle(BrowserQueries.cssName(name));
        return value == null ? null : value.strip();
    }

    /** The same script as the {@code withAccessibleName(..)} filter. Dramafinder has no read of it yet. */
    @Override
    public String getAccessibleName() {
        return (String) locator.evaluate(BrowserQueries.ACCESSIBLE_NAME);
    }

    @Override
    public String getProperty(String name) {
        Object value = generic.getProperty(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return String.valueOf(n.longValue());
        }
        return String.valueOf(value);
    }

    @Override
    public Integer getPropertyInteger(String name) {
        String value = getProperty(name);
        return value == null ? null : (int) Double.parseDouble(value);
    }

    /** The property's JavaScript truthiness. */
    @Override
    public Boolean getPropertyBoolean(String name) {
        Optional<Boolean> special = support.booleanProperty(locator, name);
        if (special.isPresent()) {
            return special.get();
        }
        Object value = generic.getProperty(name);
        if (value == null || value instanceof Boolean b && !b) {
            return false;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        return !(value instanceof String s && s.isEmpty());
    }

    @Override
    public String getAttribute(String name) {
        return locator.getAttribute(name);
    }

    @Override
    public String getCssValue(String property) {
        return generic.getComputedStyle(property);
    }

    @Override
    public boolean isVisible() {
        return support.visible(locator).orElseGet(locator::isVisible);
    }

    /**
     * Auto-Wait's check in one call for an element found with its query's candidates: Playwright's own
     * visibility rule (non-empty box, visibility visible, display: contents by its children) and no
     * {@code disabled} attribute, read in the page together. Otherwise, or when the component has its own
     * visibility rule, {@link #isVisible()} and {@link #isEnabled()} (#402).
     */
    @Override
    public boolean isVisibleAndEnabled() {
        java.util.Optional<Boolean> own = support.visible(locator);
        if (own.isPresent() || candidates == null) {
            return own.orElseGet(locator::isVisible) && isEnabled();
        }
        return Boolean.TRUE.equals(candidates.evaluateAll(VISIBLE_AND_ENABLED, index));
    }

    /** As Playwright's isVisible() and Dramafinder's isEnabled() (the disabled attribute) decide them. */
    private static final String VISIBLE_AND_ENABLED = """
            (els, i) => {
              const el = els[i];
              if (!el) return false;
              const textVisible = (node) => {
                const range = document.createRange();
                range.selectNode(node);
                const r = range.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
              };
              const visible = (e) => {
                const style = getComputedStyle(e);
                if (!style) return true;
                if (style.display === 'contents') {
                  for (let c = e.firstChild; c; c = c.nextSibling) {
                    if (c.nodeType === 1 && visible(c)) return true;
                    if (c.nodeType === 3 && textVisible(c)) return true;
                  }
                  return false;
                }
                if (e.checkVisibility && !e.checkVisibility()) return false;
                if (style.visibility !== 'visible') return false;
                const r = e.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
              };
              return visible(el) && !el.hasAttribute('disabled');
            }""";

    /** No {@code disabled} attribute: Vaadin components reflect the property to it. */
    @Override
    public boolean isEnabled() {
        return generic.isEnabled();
    }

    @Override
    public List<UnifiedElement> findChildren(String tag) {
        return wrap(generic.getLightDomChildren(tag));
    }

    @Override
    public List<UnifiedElement> findDescendants(String tag) {
        return wrap(generic.getLightDomDescendants(tag));
    }

    private List<UnifiedElement> wrap(List<VaadinElement> elements) {
        return elements.stream().map(e -> (UnifiedElement) new PlaywrightElement(session, e.getLocator(), null,
                PlaywrightComponents.DEFAULT)).toList();
    }

    // ---------------------------------------------------------------- acting

    @Override
    public void setProperty(String name, Object value) {
        generic.setProperty(name, value);
    }

    @Override
    public void click() {
        (dramafinder != null ? dramafinder : generic).click();
    }

    @Override
    public void setValueAsUser(Object value) {
        if (support.setValue(locator, value)) {
            return; // Dramafinder's element set it and waited
        }
        String text = BrowserQueries.typedText(value);
        if (dramafinder instanceof HasValueElement field) {
            field.setValue(text);
            return;
        }
        // the element itself when it is the native input, else the input inside it
        boolean isInput = List.of("input", "textarea").contains(generic.getTagName());
        Locator input = isInput ? locator : locator.locator("input, textarea").first();
        input.fill(text); // fires input
        input.dispatchEvent("change");
        AutoWait.afterAction(page());
    }

    @Override
    public void focus() {
        focusable().focus();
    }

    @Override
    public void blur() {
        focusable().blur();
    }

    @Override
    public void pressKey(String key) {
        focusable().getFocusLocator().press(key);
        AutoWait.afterAction(page());
    }

    @Override
    public void dispatchEvent(String eventName) {
        locator.dispatchEvent(eventName);
        AutoWait.afterAction(page());
    }

    /**
     * Dramafinder's focus handling: a field's input, not its host element, which can't be focused (keys
     * pressed on it would go to the page body).
     */
    private FocusableElement focusable() {
        return dramafinder instanceof FocusableElement focusable ? focusable : generic;
    }

    // ---------------------------------------------------------------- Escape Hatch

    @Override
    public <T> T unwrap(Class<T> type) {
        return tryUnwrap(type).orElseThrow();
    }

    @Override
    public <T> Optional<T> tryUnwrap(Class<T> type) {
        Optional<Object> special = support.unwrap(type, locator, session);
        if (special.isPresent()) {
            return special.map(type::cast);
        }
        if (type.isInstance(locator)) {
            return Optional.of(type.cast(locator));
        }
        if (dramafinder != null && type.isInstance(dramafinder)) {
            return Optional.of(type.cast(dramafinder));
        }
        if (VaadinElement.class.isAssignableFrom(type)) {
            try {
                return Optional.of(type.getConstructor(Locator.class).newInstance(locator));
            } catch (ReflectiveOperationException e) {
                return Optional.empty();
            }
        }
        if (type.isInstance(page())) {
            return Optional.of(type.cast(page()));
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "PlaywrightElement[" + locator + "]";
    }
}
