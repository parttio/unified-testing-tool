package demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** Components whose host can't take focus: focus goes to an input inside them. Each says when it gets focus. */
@AnonymousAllowed
@Route("focus")
public class FocusView extends VerticalLayout {

    public FocusView() {
        TextArea notes = new TextArea("Notes");
        notes.getElement().addEventListener("focusin", e -> Notification.show("Notes focused"));
        Div notesBox = new Div(notes);
        notesBox.setTestId("notes-box");

        MessageInput message = new MessageInput();
        message.getElement().addEventListener("focusin", e -> Notification.show("Message input focused"));

        add(notesBox, message);
    }
}
