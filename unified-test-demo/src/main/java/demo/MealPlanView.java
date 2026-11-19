package demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Plain HTML with click listeners, a common Flow pattern: navigation tabs that are {@code Div}s, a wordmark
 * {@code Span}, and a card that is a layout.
 */
@AnonymousAllowed
@Route("meal-plan")
public class MealPlanView extends VerticalLayout {

    public MealPlanView() {
        Paragraph showing = new Paragraph("Showing: Plan");
        showing.setId("showing");

        Span wordmark = new Span("Mise");
        wordmark.addClickListener(e -> showing.setText("Showing: Home"));

        Div plan = new Div("Plan");
        plan.addClickListener(e -> showing.setText("Showing: Plan"));
        Div shop = new Div("Shop");
        shop.addClickListener(e -> showing.setText("Showing: Shop"));
        Nav tabs = new Nav(plan, shop);

        HorizontalLayout card = new HorizontalLayout(new Span("Lentil soup"), new Span("4 servings"));
        card.setTestId("recipe-card");
        card.addClickListener(e -> showing.setText("Showing: Lentil soup"));

        add(wordmark, tabs, card, showing);
    }
}
