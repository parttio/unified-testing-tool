package demo;

import com.vaadin.flow.component.html.Article;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Plain HTML the user clicks, as a planning app builds it: a badge with a role, a tab, a focusable card, and a
 * span with only a click listener, which says nothing about being clickable (#523).
 */
@AnonymousAllowed
@Route("clickables")
public class ClickablesView extends VerticalLayout {

    public ClickablesView() {
        Span status = new Span();
        status.setId("status");

        Span badge = new Span("Week of 2026-11-16");
        badge.getElement().setAttribute("role", "button");
        badge.addClickListener(e -> status.setText("badge"));

        Div tab = new Div("Mon");
        tab.getElement().setAttribute("role", "tab");
        tab.addClickListener(e -> status.setText("tab"));

        Article card = new Article();
        card.setText("Pancakes");
        card.getElement().setAttribute("tabindex", "0");
        card.addClickListener(e -> status.setText("card"));

        Span listenerOnly = new Span("Details");
        listenerOnly.addClickListener(e -> status.setText("details"));

        add(badge, tab, card, listenerOnly, status);
    }
}
