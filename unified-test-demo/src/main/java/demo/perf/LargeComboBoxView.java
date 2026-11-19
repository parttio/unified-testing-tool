package demo.perf;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Performance scenario "combo box": 1,000 items loaded lazily; type to filter, pick one. Shows the
 * cost of an overlay and of items fetched page by page.
 */
@AnonymousAllowed
@Route("perf/combo-box")
public class LargeComboBoxView extends VerticalLayout {

    public static final int ITEM_COUNT = 1_000;

    /** The items, in order: "Item 0001" to "Item 1000". */
    public static final List<String> ITEMS = IntStream.rangeClosed(1, ITEM_COUNT)
            .mapToObj(i -> "Item %04d".formatted(i))
            .toList();

    public LargeComboBoxView() {
        ComboBox<String> product = new ComboBox<>("Product");
        // a lazy data provider: the browser fetches the items page by page, filtered on the server
        product.setItems(query -> matching(query.getFilter().orElse(""))
                .skip(query.getOffset()).limit(query.getLimit()),
                query -> (int) matching(query.getFilter().orElse("")).count());

        Paragraph picked = new Paragraph("Picked: none");
        picked.setId("picked");
        product.addValueChangeListener(e -> picked.setText("Picked: " + (e.getValue() == null ? "none" : e.getValue())));

        add(new H1("Combo box"), product, picked);
    }

    private static Stream<String> matching(String filter) {
        String lower = filter.toLowerCase();
        return ITEMS.stream().filter(item -> item.toLowerCase().contains(lower));
    }
}
