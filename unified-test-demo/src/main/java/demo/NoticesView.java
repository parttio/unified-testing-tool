package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** Notifications and texts as an application builds them beyond plain text: from components (#690). */
@AnonymousAllowed
@Route("notices")
public class NoticesView extends VerticalLayout {

    public NoticesView() {
        Button save = new Button("Save", e -> {
            Notification saved = new Notification(new Span("Saved"), new Button("Undo"));
            saved.setDuration(0);
            saved.open();
        });
        add(save);
    }
}
