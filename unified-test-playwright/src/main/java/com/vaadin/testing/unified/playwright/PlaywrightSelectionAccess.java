package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.Locator;
import com.vaadin.testing.unified.spi.SelectionAccess;
import com.vaadin.testing.unified.spi.StaleElementException;
import java.util.List;
import org.vaadin.addons.dramafinder.AutoWait;
import org.vaadin.addons.dramafinder.element.ComboBoxElement;
import org.vaadin.addons.dramafinder.element.MultiSelectComboBoxElement;
import org.vaadin.addons.dramafinder.element.RadioButtonGroupElement;

/** Selection on Playwright through Dramafinder's elements: labels are the ones the user sees. */
final class PlaywrightSelectionAccess implements SelectionAccess {

    enum Kind { COMBO_BOX, MULTI_SELECT_COMBO_BOX, RADIO_BUTTON_GROUP }

    private final PlaywrightSession session;
    private final Locator locator;
    private final Kind kind;

    PlaywrightSelectionAccess(PlaywrightSession session, Locator locator, Kind kind) {
        this.session = session;
        this.locator = locator;
        this.kind = kind;
    }

    /**
     * Read in one call that doesn't wait for the field (Dramafinder's waits Playwright's timeout, 10 s): a field
     * the app replaced as it took a value is stale at once (#377). Radio button groups count first, so don't wait.
     */
    @Override
    public List<String> selectedItems() {
        return switch (kind) {
            case COMBO_BOX -> optional((String) readNow(SELECTED_LABEL));
            case MULTI_SELECT_COMBO_BOX -> ((List<?>) readNow(SELECTED_LABELS)).stream().map(String::valueOf).toList();
            case RADIO_BUTTON_GROUP -> optional(new RadioButtonGroupElement(locator).getSelectedLabel());
        };
    }

    /** Dramafinder's {@code ComboBoxElement.getSelectedItemLabel()} script. */
    private static final String SELECTED_LABEL = """
            el => { const i = el.selectedItem;
              if (i === undefined || i === null) return null;
              return typeof i === 'string' ? i : String(i[el.itemLabelPath || 'label'] ?? ''); }""";

    /** Dramafinder's {@code MultiSelectComboBoxElement.getSelectedItems()} script. */
    private static final String SELECTED_LABELS = """
            el => (el.selectedItems || []).map(i => typeof i === 'string' ? i : i[el.itemLabelPath || 'label'] || String(i))""";

    private Object readNow(String script) {
        List<?> result = (List<?>) locator.evaluateAll(
                "els => { const read = " + script + "; return els.length === 0 ? [] : [read(els[0])]; }");
        if (result.isEmpty()) {
            throw new StaleElementException("The field is gone (replaced by the app?)", null);
        }
        return result.get(0);
    }

    private static List<String> optional(String label) {
        return label == null ? List.of() : List.of(label);
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
        ComboBoxElement combo = new ComboBoxElement(locator);
        combo.setFilter(label);
        if (combo.getItemLabels().contains(label)) {
            combo.filterAndSelectItem(label, label);
            AutoWait.waitForVaadinIdle(session.page());
            return true;
        }
        combo.setFilter("");
        combo.close();
        AutoWait.waitForVaadinIdle(session.page());
        return false;
    }

    /** Every item, a combo box's lazily loaded pages included. */
    @Override
    public List<String> items() {
        return switch (kind) {
            case COMBO_BOX -> new ComboBoxElement(locator).getItemLabels();
            case MULTI_SELECT_COMBO_BOX -> new MultiSelectComboBoxElement(locator).getItemLabels();
            case RADIO_BUTTON_GROUP -> new RadioButtonGroupElement(locator).getItemLabels();
        };
    }

    @Override
    public void select(String label) {
        switch (kind) {
            case COMBO_BOX -> new ComboBoxElement(locator).selectItem(label);
            case MULTI_SELECT_COMBO_BOX -> {
                MultiSelectComboBoxElement multi = new MultiSelectComboBoxElement(locator);
                multi.selectItem(label);
                closeIfStillThere();
            }
            case RADIO_BUTTON_GROUP -> new RadioButtonGroupElement(locator).selectByLabel(label);
        }
        AutoWait.waitForVaadinIdle(session.page());
    }

    @Override
    public void deselect(String label) {
        MultiSelectComboBoxElement multi = new MultiSelectComboBoxElement(locator);
        multi.deselectItem(label);
        closeIfStillThere();
    }

    /**
     * Closes the drop-down as Dramafinder's {@code close()} does, in one call that doesn't wait for the field: the
     * app may have replaced it as it took the value (#377), leaving nothing to close.
     */
    private void closeIfStillThere() {
        AutoWait.waitForVaadinIdle(session.page());
        locator.evaluateAll("els => els.slice(0, 1).forEach(el => el.opened = false)");
        AutoWait.waitForVaadinIdle(session.page());
    }

    /** What a user does: the clear button when shown, otherwise empty the input or deselect every item. */
    @Override
    public void clear() {
        switch (kind) {
            case COMBO_BOX -> new ComboBoxElement(locator).clear();
            case MULTI_SELECT_COMBO_BOX -> new MultiSelectComboBoxElement(locator).clear();
            case RADIO_BUTTON_GROUP -> throw new UnsupportedOperationException("A user can't clear a radio button group");
        }
    }
}
