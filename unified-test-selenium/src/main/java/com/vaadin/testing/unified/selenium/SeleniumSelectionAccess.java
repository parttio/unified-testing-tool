package com.vaadin.testing.unified.selenium;

import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.combobox.testbench.MultiSelectComboBoxElement;
import com.vaadin.flow.component.radiobutton.testbench.RadioButtonGroupElement;
import com.vaadin.testbench.HasClearButton;
import com.vaadin.testing.unified.spi.SelectionAccess;
import java.util.List;
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

    /** The index of the first item a lazy combo box hasn't loaded yet (a placeholder), or -1. */
    private static final String FIRST_PLACEHOLDER = "const items = arguments[0].filteredItems || [];"
            + " return items.findIndex(i => i === undefined || i === null"
            + " || (typeof i === 'object' && Object.keys(i).length === 0));";

    /**
     * Combo boxes: opens the drop-down, scrolls it until a lazy data provider has loaded every page (as
     * Dramafinder does), reads the items, closes it.
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
            loadEveryPage();
            return strings("const c = arguments[0]; return (c.filteredItems || [])"
                    + ".filter(i => i && i[c.itemLabelPath] !== undefined).map(i => String(i[c.itemLabelPath]));");
        } finally {
            if (!wasOpen) {
                session.js("arguments[0].opened = false;", element);
            }
        }
    }

    /** A lazy combo box loads items a page at a time as its drop-down scrolls: scroll to each missing page. */
    private void loadEveryPage() {
        long deadline = System.nanoTime() + session.timeout().toNanos();
        for (Object index = session.js(FIRST_PLACEHOLDER, element); ((Number) index).intValue() >= 0;
                index = session.js(FIRST_PLACEHOLDER, element)) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Timed out waiting for the drop-down's items");
            }
            session.js("arguments[0]._scroller.scrollIntoView(arguments[1]);", element, index);
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

    @Override
    public void select(String label) {
        switch (kind) {
            case COMBO_BOX -> session.testBench(element).wrap(ComboBoxElement.class).selectByText(label);
            case MULTI_SELECT_COMBO_BOX -> {
                MultiSelectComboBoxElement multi = session.testBench(element).wrap(MultiSelectComboBoxElement.class);
                multi.selectByText(label);
                multi.closePopup();
            }
            case RADIO_BUTTON_GROUP -> session.testBench(element).wrap(RadioButtonGroupElement.class).selectByText(label);
        }
        session.waitForVaadin();
    }

    @Override
    public void deselect(String label) {
        MultiSelectComboBoxElement multi = session.testBench(element).wrap(MultiSelectComboBoxElement.class);
        multi.deselectByText(label);
        multi.closePopup();
        session.waitForVaadin();
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
