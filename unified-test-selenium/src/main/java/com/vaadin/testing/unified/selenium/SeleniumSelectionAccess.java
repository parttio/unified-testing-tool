package com.vaadin.testing.unified.selenium;

import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.combobox.testbench.MultiSelectComboBoxElement;
import com.vaadin.flow.component.radiobutton.testbench.RadioButtonGroupElement;
import com.vaadin.testbench.HasClearButton;
import com.vaadin.testing.unified.spi.SelectionAccess;
import com.vaadin.testing.unified.spi.StaleElementException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

/**
 * Selection on Selenium: actions through TestBench's element classes, labels read from the web component
 * (the label Flow sends with each item).
 */
final class SeleniumSelectionAccess implements SelectionAccess {

    enum Kind { COMBO_BOX, MULTI_SELECT_COMBO_BOX, RADIO_BUTTON_GROUP }

    private final SeleniumSession session;
    private final WebElement element;
    private final Kind kind;

    SeleniumSelectionAccess(SeleniumSession session, WebElement element, Kind kind) {
        this.session = session;
        this.element = element;
        this.kind = kind;
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(String script) {
        return (List<String>) session.js(script, element);
    }

    @Override
    public List<String> selectedItems() {
        return SeleniumSession.stale(this::readSelected);
    }

    private List<String> readSelected() {
        return switch (kind) {
            case COMBO_BOX -> {
                String text = session.testBench(element).wrap(ComboBoxElement.class).getSelectedText();
                yield text == null || text.isEmpty() ? List.of() : List.of(text); // "" when nothing is selected
            }
            case MULTI_SELECT_COMBO_BOX ->
                    session.testBench(element).wrap(MultiSelectComboBoxElement.class).getSelectedTexts();
            case RADIO_BUTTON_GROUP -> {
                String text = session.testBench(element).wrap(RadioButtonGroupElement.class).getSelectedText();
                yield text == null ? List.of() : List.of(text.strip());
            }
        };
    }

    /**
     * The drop-down's item count and its loaded items as {@code [index, label]} pairs; a label is null for
     * an item without one. A lazy data provider's items not loaded yet are placeholders: empty objects.
     */
    private static final String LOADED_ITEMS = "const c = arguments[0]; const items = c.filteredItems || [];"
            + " const out = []; items.forEach((i, n) => {"
            + " if (i === undefined || i === null || (typeof i === 'object' && Object.keys(i).length === 0)) return;"
            + " const l = typeof i === 'object' ? i[c.itemLabelPath] : i;"
            + " out.push([n, l === undefined || l === null ? null : String(l)]); });"
            + " return [items.length, out];";

    /**
     * Combo boxes: opens the drop-down, reads its items page by page as a lazy data provider loads them (as
     * Dramafinder does), closes it.
     */
    @Override
    public List<String> items() {
        if (kind == Kind.RADIO_BUTTON_GROUP) {
            return session.testBench(element).wrap(RadioButtonGroupElement.class).getOptions().stream()
                    .map(String::strip).toList();
        }
        // ComboBoxElement.getOptions() doesn't wait for loading, skip placeholders or restore the popup: #163
        boolean wasOpen = Boolean.TRUE.equals(session.js("return !!arguments[0].opened;", element));
        if (!wasOpen) {
            session.js("arguments[0].opened = true;", element);
        }
        try {
            waitUntil("const c = arguments[0]; return c.opened && !c.loading;");
            return readEveryPage();
        } finally {
            if (!wasOpen) {
                session.js("arguments[0].opened = false;", element);
            }
        }
    }

    /**
     * A lazy combo box loads a page at a time as its drop-down scrolls, and drops pages far from the view:
     * scroll to the first item not read yet, collect what is loaded, until every item has been read.
     */
    @SuppressWarnings("unchecked")
    private List<String> readEveryPage() {
        Map<Integer, String> labels = new TreeMap<>();
        Set<Integer> read = new HashSet<>();
        long deadline = System.nanoTime() + session.timeout().toNanos();
        int next = 0;
        while (true) {
            List<Object> state = (List<Object>) session.js(LOADED_ITEMS, element);
            int size = ((Number) state.get(0)).intValue();
            for (Object entry : (List<Object>) state.get(1)) {
                List<Object> pair = (List<Object>) entry;
                int index = ((Number) pair.get(0)).intValue();
                read.add(index);
                if (pair.get(1) != null) {
                    labels.put(index, (String) pair.get(1));
                }
            }
            while (next < size && read.contains(next)) {
                next++;
            }
            if (next >= size) {
                return List.copyOf(labels.values());
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Timed out waiting for the drop-down's items");
            }
            session.js("arguments[0]._scroller.scrollIntoView(arguments[1]);", element, next);
            session.waitForVaadin();
            waitUntil("return !arguments[0].loading;");
        }
    }

    private void waitUntil(String script) {
        long deadline = System.nanoTime() + session.timeout().toNanos();
        while (!Boolean.TRUE.equals(session.js(script, element))) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Timed out waiting for the drop-down's items");
            }
            session.waitForVaadin();
        }
    }

    /**
     * A combo box: filters it by the label, so the server sends only the items that match, and picks the item
     * among them; else clears the filter and closes it, and the core reads every item (#403).
     */
    @Override
    public boolean trySelect(String label) {
        if (kind != Kind.COMBO_BOX) {
            return false;
        }
        ComboBoxElement combo = session.testBench(element).wrap(ComboBoxElement.class);
        combo.setFilter(label);
        session.waitForVaadin();
        if (items().contains(label)) {
            replaceable(() -> combo.selectByText(label));
            session.waitForVaadin();
            return true;
        }
        combo.setFilter("");
        combo.closePopup();
        session.waitForVaadin();
        return false;
    }

    @Override
    public void select(String label) {
        replaceable(() -> {
            switch (kind) {
                case COMBO_BOX -> session.testBench(element).wrap(ComboBoxElement.class).selectByText(label);
                case MULTI_SELECT_COMBO_BOX -> {
                    MultiSelectComboBoxElement multi = session.testBench(element).wrap(MultiSelectComboBoxElement.class);
                    multi.selectByText(label);
                    multi.closePopup();
                }
                case RADIO_BUTTON_GROUP -> session.testBench(element).wrap(RadioButtonGroupElement.class).selectByText(label);
            }
        });
        session.waitForVaadin();
    }

    @Override
    public void deselect(String label) {
        replaceable(() -> {
            MultiSelectComboBoxElement multi = session.testBench(element).wrap(MultiSelectComboBoxElement.class);
            multi.deselectByText(label);
            multi.closePopup();
        });
        session.waitForVaadin();
    }

    /**
     * A change after which the element may be stale: the app may replace the field as it takes the value (#377),
     * and TestBench closes the drop-down after it. The core's check after the change reads the field again and
     * tells a replaced field (nothing to check) from a re-rendered one (the change is retried).
     */
    private static void replaceable(Runnable change) {
        try {
            change.run();
        } catch (StaleElementReferenceException | StaleElementException e) {
            // left to the core's check
        }
    }

    /** The clear button when shown, as a user would; otherwise TestBench's clear. */
    @Override
    public void clear() {
        if (kind == Kind.RADIO_BUTTON_GROUP) {
            throw new UnsupportedOperationException("A user can't clear a radio button group");
        }
        HasClearButton field = kind == Kind.COMBO_BOX
                ? session.testBench(element).wrap(ComboBoxElement.class)
                : session.testBench(element).wrap(MultiSelectComboBoxElement.class);
        if (field.isClearButtonVisible()) {
            field.clickClearButton();
        } else if (field instanceof ComboBoxElement comboBox) {
            comboBox.clear();
        } else {
            ((MultiSelectComboBoxElement) field).deselectAll();
        }
        session.waitForVaadin();
    }
}
