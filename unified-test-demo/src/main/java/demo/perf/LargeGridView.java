package demo.perf;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Performance scenario "large grid": 500 rows; find one by its text, select it, check the editor.
 * Shows how the Unified layer's cost grows with lookups in a large component.
 */
@AnonymousAllowed
@Route("perf/large-grid")
public class LargeGridView extends VerticalLayout {

    public static final int ROW_COUNT = 500;

    /** One row: the same data on every run. */
    public record Person(String name, String email, String city) {}

    private static final List<String> CITIES = List.of("Turku", "Helsinki", "Tampere", "Oulu", "Espoo");

    /** The rows, in grid order: "Person 001" to "Person 500". */
    public static final List<Person> PEOPLE = IntStream.rangeClosed(1, ROW_COUNT)
            .mapToObj(i -> new Person(name(i), "person%03d@example.com".formatted(i), CITIES.get(i % CITIES.size())))
            .toList();

    /** The name of row {@code i}, counting from 1. */
    public static String name(int i) {
        return "Person %03d".formatted(i);
    }

    public LargeGridView() {
        Grid<Person> grid = new Grid<>();
        grid.addColumn(Person::name).setHeader("Name");
        grid.addColumn(Person::email).setHeader("Email");
        grid.addColumn(Person::city).setHeader("City");
        grid.setItems(PEOPLE);

        TextField name = new TextField("Name");
        TextField email = new TextField("Email");
        TextField city = new TextField("City");
        for (TextField field : List.of(name, email, city)) {
            field.setReadOnly(true);
        }
        grid.asSingleSelect().addValueChangeListener(e -> {
            Person person = e.getValue();
            name.setValue(person == null ? "" : person.name());
            email.setValue(person == null ? "" : person.email());
            city.setValue(person == null ? "" : person.city());
        });

        HorizontalLayout content = new HorizontalLayout(grid, new VerticalLayout(name, email, city));
        content.setSizeFull();
        content.setFlexGrow(1, grid);
        add(new H1("Large grid"), content);
        setSizeFull();
    }
}
