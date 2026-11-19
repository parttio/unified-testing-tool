package demo;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A combo box that replaces itself on each pick with a new one holding it, as the order item editors of
 * Vaadin's Bakery do (#377). The pick goes through though the field the test picked in is gone.
 */
@AnonymousAllowed
@Route("replacing-field")
public class ReplacingFieldView extends Div {
    private int generation;

    public ReplacingFieldView() {
        add(product(null));
    }

    private ComboBox<String> product(String value) {
        ComboBox<String> box = new ComboBox<>("Product", "Bun", "Cake");
        box.getElement().setAttribute("data-generation", String.valueOf(generation++));
        box.setValue(value);
        box.addValueChangeListener(e -> {
            removeAll();
            add(product(e.getValue()));
        });
        return box;
    }
}
