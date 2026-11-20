package com.vaadin.testing.unified.internal.selection;

import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.spi.SelectionAccess;
import com.vaadin.testing.unified.internal.failures.ItemFailures;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.spi.StaleElementException;
import java.util.List;
import java.util.stream.Collectors;

/** Selection field behaviour shared by the selection wrappers: Auto-Wait, then verify the result. */
public final class SelectionSupport {
    private SelectionSupport() {}

    public static SelectionAccess access(UnifiedWrapper field) {
        return field.element().unwrap(SelectionAccess.class);
    }

    public static void select(UnifiedWrapper field, String label) {
        LazyElement element = Wrappers.lazyElementOf(field);
        element.act("select \"" + label + "\" in", e -> {
            SelectionAccess access = e.unwrap(SelectionAccess.class);
            // the quick way first (a combo box filtered by the label); every item only when it doesn't find it
            if (!access.trySelect(label)) {
                List<String> items = access.items();
                if (!items.contains(label)) {
                    throw ItemFailures.missing(element.run(), "item", label, StepTarget.of(element).renderWithScope(),
                            items, "selectItem");
                }
                access.select(label);
            }
            // the app may replace the field as it takes the value (#377): a field gone has nothing to check
            if (!stillFound(element)) {
                return;
            }
            List<String> selected;
            try {
                selected = access.selectedItems();
            } catch (StaleElementException stale) {
                throw stale;
            } catch (RuntimeException readFailed) {
                // e.g. Playwright's own timeout: the Unified failure, with it as the cause
                throw notSelected(element, label, null, readFailed);
            }
            if (!selected.contains(label)) {
                throw notSelected(element, label, selected, null);
            }
        });
    }

    public static void deselect(UnifiedWrapper field, String label) {
        LazyElement element = Wrappers.lazyElementOf(field);
        element.act("deselect \"" + label + "\" in", e -> {
            SelectionAccess access = e.unwrap(SelectionAccess.class);
            if (!access.selectedItems().contains(label)) {
                return;
            }
            access.deselect(label);
            if (!stillFound(element)) {
                return;
            }
            if (access.selectedItems().contains(label)) {
                throw new IllegalStateException("\"" + label + "\" could not be deselected in " + element.describe());
            }
        });
    }

    public static void clear(UnifiedWrapper field) {
        LazyElement element = Wrappers.lazyElementOf(field);
        element.act("clear", e -> {
            SelectionAccess access = e.unwrap(SelectionAccess.class);
            access.clear();
            if (!access.selectedItems().isEmpty()) {
                throw new IllegalStateException("Selection could not be cleared in " + element.describe());
            }
        });
    }

    /** {@code Expected  item “Large” selected in combo box “Size”}, {@code Found  “Small”}. */
    private static UnifiedAssertionError notSelected(LazyElement element, String label, List<String> selected,
            Throwable cause) {
        String found = selected == null ? null : selected.isEmpty() ? "none"
                : selected.stream().map(s -> "“" + s + "”").collect(Collectors.joining(", "));
        return element.run().failure(FailureParts.of("item “" + label + "” selected in "
                + StepTarget.of(element).renderWithScope(), found), cause);
    }

    /** Whether the lookup still finds the field, without waiting. */
    private static boolean stillFound(LazyElement element) {
        return element.countNow() > Math.max(element.index(), 0);
    }

    /** The single selected label, or null. */
    public static String selected(UnifiedWrapper field) {
        List<String> selected = access(field).selectedItems();
        return selected.isEmpty() ? null : selected.get(0);
    }
}
