package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.time.LocalDate;

/**
 * An app's own text field with a Vaadin 8 style null representation (#689): it reads null until a value comes
 * from the client, which the field learns in {@code setModelValue(.., fromClient)}.
 */
@AnonymousAllowed
@Route("null-representation")
public class NullRepresentationView extends VerticalLayout {

    public NullRepresentationView() {
        NullableTextField firstName = new NullableTextField("First name");
        NullableNumberField height = new NullableNumberField("Height");
        NullableDatePicker birthday = new NullableDatePicker("Birthday");
        Paragraph savedName = saved("saved-name");
        Paragraph savedHeight = saved("saved-height");
        Paragraph savedBirthday = saved("saved-birthday");
        Button save = new Button("Save", e -> {
            savedName.setText("Saved: " + firstName.getValue());
            savedHeight.setText("Saved: " + height.getValue());
            savedBirthday.setText("Saved: " + birthday.getValue());
        });
        add(firstName, height, birthday, save, savedName, savedHeight, savedBirthday);
    }

    private static Paragraph saved(String id) {
        Paragraph saved = new Paragraph();
        saved.setId(id);
        return saved;
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

    /** Null until the user enters a number. */
    static class NullableNumberField extends NumberField {
        private boolean isNull = true;

        NullableNumberField(String label) {
            super(label);
        }

        @Override
        protected void setModelValue(Double value, boolean fromClient) {
            if (fromClient) {
                isNull = false;
            }
            super.setModelValue(value, fromClient);
        }

        @Override
        public Double getValue() {
            return isNull ? null : super.getValue();
        }
    }

    /** Null until the user enters a date. */
    static class NullableDatePicker extends DatePicker {
        private boolean isNull = true;

        NullableDatePicker(String label) {
            super(label);
        }

        @Override
        protected void setModelValue(LocalDate value, boolean fromClient) {
            if (fromClient) {
                isNull = false;
            }
            super.setModelValue(value, fromClient);
        }

        @Override
        public LocalDate getValue() {
            return isNull ? null : super.getValue();
        }
    }
}
