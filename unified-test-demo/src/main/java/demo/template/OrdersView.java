package demo.template;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** An app built from Templates (#347): their components are in shadow roots, which no lookup enters by itself. */
@AnonymousAllowed
@Route("orders")
public class OrdersView extends VerticalLayout {

    public OrdersView() {
        OrdersPanel panel = new OrdersPanel();
        // in the panel's light DOM, shown through its slot
        Button help = new Button("Help", e -> panel.status.setText("Help shown"));
        panel.getElement().appendChild(help.getElement());
        add(panel);
    }
}
