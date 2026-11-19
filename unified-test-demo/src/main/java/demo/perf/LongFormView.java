package demo.perf;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Performance scenario "long form": 30 fields filled one after the other, then saved. Shows how the
 * Unified layer's cost grows with the number of user actions.
 */
@AnonymousAllowed
@Route("perf/long-form")
public class LongFormView extends VerticalLayout {

    /** The 24 text fields' labels, in form order. */
    public static final List<String> TEXT_FIELDS = List.of("First name", "Last name", "Email", "Phone", "Mobile",
            "Company", "Department", "Job title", "Street", "House number", "Apartment", "Postal code", "City",
            "Region", "Country", "Billing street", "Billing postal code", "Billing city", "Billing country",
            "Website", "Referral code", "Emergency contact", "Emergency phone", "Notes");
    /** The 2 integer fields' labels. */
    public static final List<String> INTEGER_FIELDS = List.of("Age", "Household size");
    /** The 2 date pickers' labels. */
    public static final List<String> DATE_FIELDS = List.of("Birthday", "Start date");
    /** The 2 checkboxes' labels. */
    public static final List<String> CHECKBOXES = List.of("Subscribe to newsletter", "Accept terms");
    /** All 30 fields. */
    public static final int FIELD_COUNT = TEXT_FIELDS.size() + INTEGER_FIELDS.size() + DATE_FIELDS.size()
            + CHECKBOXES.size();

    private final List<HasValue<?, ?>> fields = new ArrayList<>();
    private final Paragraph result = new Paragraph();

    public LongFormView() {
        FormLayout form = new FormLayout();
        TEXT_FIELDS.forEach(label -> form.add(field(new TextField(label))));
        INTEGER_FIELDS.forEach(label -> form.add(field(new IntegerField(label))));
        DATE_FIELDS.forEach(label -> form.add(field(new DatePicker(label))));
        CHECKBOXES.forEach(label -> form.add(field(new Checkbox(label))));

        result.setId("result");
        add(new H1("Long form"), form, new Button("Save", e -> save()), result);
    }

    private <C extends AbstractField<?, ?>> C field(C field) {
        fields.add(field);
        return field;
    }

    private void save() {
        long filled = fields.stream().filter(f -> !Objects.equals(f.getValue(), f.getEmptyValue())).count();
        result.setText("Saved " + filled + " of " + fields.size() + " fields");
    }
}
