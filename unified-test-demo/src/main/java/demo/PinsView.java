package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** A week's meals, each with a "Pin" button that only its data-pin-date tells apart (#547). */
@AnonymousAllowed
@Route("pins")
public class PinsView extends VerticalLayout {

    public PinsView() {
        for (String date : new String[] { "2026-11-23", "2026-11-24", "2026-11-25" }) {
            Button pin = new Button("Pin");
            pin.getElement().setAttribute("data-pin-date", date);
            pin.getElement().setAttribute("data-kind", "meal");
            add(pin);
        }
    }
}
