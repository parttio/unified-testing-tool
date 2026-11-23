package demo;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Shipments, or one shipment by its number: a route with an optional parameter, an alias with a required one, and
 * a root-level alias (#530). The number is parsed as a long, so a URL that keeps {@code :shipmentID} in fails.
 */
@AnonymousAllowed
@Route("shipment/:shipmentID?")
@RouteAlias("shipment/:shipmentID/edit")
@RouteAlias("parcels")
public class ShipmentView extends VerticalLayout implements BeforeEnterObserver {

    private final Paragraph showing = new Paragraph();

    public ShipmentView() {
        showing.setId("showing");
        add(showing);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        showing.setText(event.getRouteParameters().get("shipmentID").map(Long::parseLong)
                .map(id -> "Shipment " + id).orElse("All shipments"));
    }
}
