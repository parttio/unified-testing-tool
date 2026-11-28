package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Notifications and texts as an application builds them beyond plain text: from components (#690), and as markup
 * set through {@code innerHTML} (#691).
 */
@AnonymousAllowed
@Route("notices")
public class NoticesView extends VerticalLayout {

    public NoticesView() {
        Button save = new Button("Save", e -> {
            Notification saved = new Notification(new Span("Saved"), new Button("Undo"));
            saved.setDuration(0);
            saved.open();
        });
        Span warning = new Span();
        warning.getElement().setProperty("innerHTML", "Please <em>check</em> the input data");

        Button importRows = new Button("Import", e -> {
            Div message = new Div();
            message.getElement().setProperty("innerHTML", "<b>3</b> rows imported");
            Notification imported = new Notification(message);
            imported.setDuration(0);
            imported.open();
        });

        Button export = new Button();
        export.getElement().setProperty("innerHTML", "<b>Export</b> all");

        Div greeting = new Div();
        greeting.getElement().setProperty("innerHTML", "<p>Hello</p>");
        HorizontalLayout welcome = new HorizontalLayout(new Span("Welcome"), greeting);
        welcome.setTestId("welcome");

        add(save, importRows, export, welcome, new Html("<div data-testid=\"total\"><b>Total</b> 42</div>"),
                new Html("<p>Prices include <em>VAT</em></p>"), warning);
    }
}
