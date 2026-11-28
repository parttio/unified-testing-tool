package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * An app's own text field with a Vaadin 8 style null representation (#689): it reads null until a value comes
 * from the client, which the field learns in {@code setModelValue(.., fromClient)}.
 */
@AnonymousAllowed
@Route("null-representation")
public class NullRepresentationView extends VerticalLayout {

    public NullRepresentationView() {
        NullableTextField firstName = new NullableTextField("First name");
        Paragraph saved = new Paragraph();
        saved.setId("saved");
        Button save = new Button("Save", e -> saved.setText("Saved: " + firstName.getValue()));
        add(firstName, save, saved);
    }

    /** Null until the user enters a value. */
    static class NullableTextField extends TextField {
        private boolean isNull = true;

        NullableTextField(String label) {
            super(label);
        }

        @Override
        protected void setModelValue(String value, boolean fromClient) {
            if (fromClient) {
                isNull = false;
            }
            super.setModelValue(value, fromClient);
        }

        @Override
        public String getValue() {
            return isNull ? null : super.getValue();
        }
    }
}
