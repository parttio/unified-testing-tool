package demo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.List;
import java.util.stream.IntStream;

@AnonymousAllowed
@Route("users")
public class UsersView extends VerticalLayout {

    public record User(String name, String email, String role) {}

    static final int USER_COUNT = 120;

    private String nameFilter = "";

    public UsersView() {
        // lazy: more rows than one page, so reading far rows needs scrolling and loading
        Grid<User> users = new Grid<>();
        users.setTestId("users");
        Grid.Column<User> name = users.addColumn(User::name).setHeader("Name").setKey("name");
        users.addColumn(User::email).setHeader("Email").setKey("email");
        users.addColumn(User::role).setHeader("Role").setKey("role");
        users.addComponentColumn(u -> {
            Button edit = new Button("Edit", e -> Notification.show("Editing " + u.name()));
            edit.setAriaLabel("Edit " + u.name());
            return edit;
        }).setHeader("Actions");
        users.setItems(DataProvider.fromCallbacks(
                query -> filtered().skip(query.getOffset()).limit(query.getLimit()),
                query -> (int) filtered().count()));

        // a filter field in a second header row
        TextField filter = new TextField();
        filter.setPlaceholder("Filter by name");
        filter.setValueChangeMode(ValueChangeMode.EAGER);
        filter.addValueChangeListener(e -> {
            nameFilter = e.getValue();
            users.getDataProvider().refreshAll();
        });
        users.appendHeaderRow().getCell(name).setComponent(filter);

        // row details, opened by clicking the row (Flow's default)
        users.setItemDetailsRenderer(new ComponentRenderer<>(u -> new HorizontalLayout(
                new Span("Details of " + u.name()),
                new Button("Reset password", e -> Notification.show("Password reset for " + u.name())))));

        users.addItemClickListener(e -> Notification.show("Clicked " + e.getItem().name()));
        users.addItemDoubleClickListener(e -> Notification.show("Opened " + e.getItem().name()));

        // small, multi-select, sortable; the last column has no header text, only an id
        Grid<User> roles = new Grid<>();
        roles.setTestId("roles");
        roles.setSelectionMode(Grid.SelectionMode.MULTI);
        roles.addColumn(User::role).setHeader("Role").setKey("role").setSortable(true).setComparator(User::role);
        roles.addColumn(User::name).setId("owner");
        roles.setItems(List.of(new User("Ada", "", "Admin"), new User("Bob", "", "Editor"), new User("Cy", "", "Viewer")));
        roles.setAllRowsVisible(true);

        Grid<User> archived = new Grid<>();
        archived.setTestId("archived");
        archived.addColumn(User::name).setHeader("Name");
        archived.setItems(List.of());
        // reshaping a grid from the server: a column comes and goes after a round trip
        Button archivedOn = new Button("Show archive date");
        archivedOn.addClickListener(e -> {
            if (archived.getColumnByKey("archivedOn") == null) {
                archived.addColumn(User::role).setHeader("Archived on").setKey("archivedOn");
                archivedOn.setText("Hide archive date");
            } else {
                archived.removeColumnByKey("archivedOn");
                archivedOn.setText("Show archive date");
            }
        });

        // the first column is a button: clicking the row must not click it
        Grid<User> tasks = new Grid<>();
        tasks.setTestId("tasks");
        tasks.addComponentColumn(u -> new Button("Done", e -> Notification.show("Done " + u.name())))
                .setHeader("Action");
        tasks.addColumn(User::name).setHeader("Task");
        tasks.setItems(List.of(new User("Task 1", "", ""), new User("Task 2", "", "")));
        tasks.setAllRowsVisible(true);
        tasks.addItemClickListener(e -> Notification.show("Clicked " + e.getItem().name()));

        // a header group row above the captions: the captions are in the second header row (#338)
        Grid<User> staff = new Grid<>();
        staff.setTestId("staff");
        Grid.Column<User> staffName = staff.addColumn(User::name).setHeader("Name").setSortable(true)
                .setComparator(User::name);
        Grid.Column<User> staffRole = staff.addColumn(User::role).setHeader("Role").setSortable(true)
                .setComparator(User::role);
        staff.addColumn(User::email).setHeader("Email").setSortable(true).setComparator(User::email);
        staff.setItems(List.of(new User("Ada", "b@example.com", "Editor"), new User("Bob", "c@example.com", "Admin"),
                new User("Cy", "a@example.com", "Viewer")));
        staff.prependHeaderRow().join(staffName, staffRole).setText("Person");
        staff.setAllRowsVisible(true);

        // a hidden column first, and an action column without header text: reached by index (#614)
        Grid<User> accounts = new Grid<>();
        accounts.setTestId("accounts");
        accounts.addColumn(User::email).setHeader("Email").setVisible(false);
        accounts.addColumn(User::name).setHeader("Name");
        accounts.addComponentColumn(u -> new Button("Remove", e -> Notification.show("Removing " + u.name())));
        accounts.setItems(List.of(new User("Ada", "ada@example.com", ""), new User("Bob", "bob@example.com", "")));
        accounts.setAllRowsVisible(true);

        // markup a LitRenderer draws, which only exists in a browser (#608): a pencil in a column without
        // header text, as Crud.addEditColumn draws, and a clickable card that fills its row's only cell
        Grid<User> editable = new Grid<>();
        editable.setTestId("editable");
        editable.addColumn(User::name).setHeader("Name");
        editable.addColumn(LitRenderer.<User>of("<demo-edit role=\"button\" @click=${edit}>✎</demo-edit>")
                .withFunction("edit", u -> Notification.show("Editing " + u.name())));
        editable.setItems(List.of(new User("Ada", "ada@example.com", ""), new User("Bob", "bob@example.com", "")));
        editable.setAllRowsVisible(true);

        Grid<User> cards = new Grid<>();
        cards.setTestId("cards");
        // the negative margin spreads the card over the cell's padding, so no spot of the row is free of it
        cards.addColumn(LitRenderer.<User>of("<demo-card style=\"display: block; margin: -50px; padding: 50px\""
                        + " @click=${open}>${item.name}</demo-card>")
                .withProperty("name", User::name)
                .withFunction("open", u -> Notification.show("Opening " + u.name())))
                .setHeader("Order");
        cards.setItems(List.of(new User("Ada", "ada@example.com", ""), new User("Bob", "bob@example.com", "")));
        cards.setAllRowsVisible(true);

        add(users, roles, archived, archivedOn, tasks, staff, accounts, editable, cards);
    }

    private java.util.stream.Stream<User> filtered() {
        return IntStream.range(0, USER_COUNT).mapToObj(UsersView::user)
                .filter(u -> u.name().toLowerCase().contains(nameFilter.toLowerCase()));
    }

    static User user(int i) {
        int n = i + 1;
        return new User("User " + n, "user" + n + "@example.com", n % 10 == 0 ? "Admin" : "Member");
    }
}
