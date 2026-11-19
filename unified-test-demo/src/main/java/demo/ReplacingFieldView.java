package demo;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.Set;

/**
 * Fields that replace themselves on each change with a new one holding the value, as the order item editors of
 * Vaadin's Bakery do (#377). The change goes through though the field the test changed is gone.
 */
@AnonymousAllowed
@Route("replacing-field")
public class ReplacingFieldView extends Div {
    private final Div productSlot = new Div();
    private final Div extrasSlot = new Div();
    private int generation;

    public ReplacingFieldView() {
        productSlot.add(product(null));
        extrasSlot.add(extras(Set.of("Bun", "Cake")));
        add(productSlot, extrasSlot);
    }

    private ComboBox<String> product(String value) {
        ComboBox<String> box = new ComboBox<>("Product", "Bun", "Cake");
        box.getElement().setAttribute("data-generation", String.valueOf(generation++));
        box.setValue(value);
        box.addValueChangeListener(e -> {
            productSlot.removeAll();
            productSlot.add(product(e.getValue()));
        });
        return box;
    }

    private MultiSelectComboBox<String> extras(Set<String> value) {
        MultiSelectComboBox<String> box = new MultiSelectComboBox<>("Extras", "Bun", "Cake");
        box.getElement().setAttribute("data-generation", String.valueOf(generation++));
        box.setValue(value);
        box.addValueChangeListener(e -> {
            extrasSlot.removeAll();
            extrasSlot.add(extras(e.getValue()));
        });
        return box;
    }
}
