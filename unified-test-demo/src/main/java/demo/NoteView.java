package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * A note with Save and Cancel, under a "Restore the draft?" bar with Restore and a Cancel of its own. Either of the
 * bar's buttons hides the bar ({@code setVisible(false)}); hidden after it was shown, it stays in the page, so a
 * lookup of Cancel by text finds both buttons, one of them hidden.
 */
@AnonymousAllowed
@Route("note")
public class NoteView extends VerticalLayout {

    public NoteView() {
        TextArea note = new TextArea("Note");
        HorizontalLayout draft = new HorizontalLayout(new Span("Restore the draft?"));
        draft.add(new Button("Restore", e -> {
            note.setValue("Buy milk");
            draft.setVisible(false);
        }), new Button("Cancel", e -> draft.setVisible(false)));

        add(draft, note, new HorizontalLayout(new Button("Save"), new Button("Cancel", e -> note.clear())));
    }
}
