package demo;

import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.time.LocalDate;

/**
 * A week picker as a planning app has it: the date picker's field is hidden with CSS (no size, so a browser sees
 * it hidden, yet it still anchors its calendar: {@code display: none} would keep the calendar from opening), a
 * click on the week badge opens its calendar, and the user picks a day there (#516).
 */
@AnonymousAllowed
@Route("week-picker")
public class WeekPickerView extends VerticalLayout {

    public WeekPickerView() {
        DatePicker picker = new DatePicker("Week");
        picker.setId("week-picker");
        picker.setValue(LocalDate.of(2026, 11, 16));
        picker.getStyle().set("width", "0").set("height", "0").set("overflow", "hidden").set("position", "absolute");

        Span badge = new Span();
        badge.setId("week-badge");
        badge.setText("Week of " + picker.getValue());
        badge.addClickListener(e -> picker.open());
        picker.addValueChangeListener(e -> badge.setText("Week of " + e.getValue()));

        add(badge, picker);
    }
}
