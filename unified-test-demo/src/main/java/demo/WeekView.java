package demo;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** A week's plan: whose, from an optional route parameter, and which week, from a query parameter. */
@AnonymousAllowed
@Route("week/:owner?")
public class WeekView extends VerticalLayout implements BeforeEnterObserver {

    private final Paragraph showing = new Paragraph();

    public WeekView() {
        showing.setId("showing");
        add(showing);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String owner = event.getRouteParameters().get("owner").orElse("everyone");
        String week = event.getLocation().getQueryParameters().getSingleParameter("week").orElse("this week");
        showing.setText("Plan of " + owner + " for " + week);
    }
}
