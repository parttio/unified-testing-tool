package demo.template;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.littemplate.LitTemplate;
import com.vaadin.flow.component.template.Id;

/**
 * A Template holding another ({@link SearchBar}), a button with the same id as the search bar's, a status line,
 * and a slot for what the view adds to it.
 */
@Tag("demo-orders-panel")
public class OrdersPanel extends LitTemplate {

    @Id("search")
    SearchBar search;

    @Id("new")
    Button newCustomer;

    @Id("status")
    Span status;

    public OrdersPanel() {
        newCustomer.setText("New customer");
        newCustomer.addClickListener(e -> status.setText("New customer"));
        search.filter.addValueChangeListener(e -> status.setText("Filtered by " + e.getValue()));
        search.newOrder.addClickListener(e -> {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("New order");
            dialog.add(new Button("Create", c -> {
                status.setText("Order created");
                dialog.close();
            }));
            dialog.open();
        });
    }
}
