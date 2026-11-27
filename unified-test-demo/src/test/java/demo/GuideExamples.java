package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.archunit.UnifiedStyleRules;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDateTimePicker;
import com.vaadin.testing.unified.component.UnifiedDetails;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTabSheet;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextField;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Examples for the developer guide (docs/guide) that compile but don't run: they show the API on buttons, fields
 * and views the demo doesn't have. CI compiles them, so a change to the API breaks the build instead of the guide.
 * An example that can run against the demo belongs in a test of its own instead.
 */
@Disabled("Examples for docs/guide: compiled, not run")
class GuideExamples {

    // ---- assertions-and-waiting.md

    void assertions(UnifiedPage page, UnifiedTextField name) {
        // --8<-- [start:assertions]
        assertThat(name).hasValue("Alice").isValid();                         // retries until it holds
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alice");
        assertThat(page).isAt(ProfileView.class);
        // --8<-- [end:assertions]
    }

    void assertThatNotGetters(UnifiedTextField name) {
        // --8<-- [start:assertThatNotGetters]
        assertThat(name).hasValue("Alice");                // good: retries
        assertEquals("Alice", name.getValue());            // flaky in a browser after an async update
        // --8<-- [end:assertThatNotGetters]
    }

    void ownTimeout(UnifiedPage page) {
        // --8<-- [start:ownTimeout]
        UnifiedButton slow = page.find(UnifiedButton.class).withText("Export")
                .withTimeout(Duration.ofSeconds(30));
        slow.click();
        // --8<-- [end:ownTimeout]
    }

    void counting(UnifiedPage page) {
        // --8<-- [start:counting]
        assertThat(page.find(UnifiedButton.class).withText("Approve")).exists();                // at least one
        assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist(); // none shown
        assertThat(page.find(UnifiedMetricCard.class)).hasCount(2);                               // exactly two
        // --8<-- [end:counting]
    }

    void gone(UnifiedPage page, UnifiedButton approve, UnifiedDialog dialog) {
        // --8<-- [start:gone]
        assertThat(approve).isHidden();                                          // still in the page, hidden
        assertThat(dialog).isClosed();                                           // dialogs and notifications
        assertThat(page.find(UnifiedButton.class).withText("Approve").visible()).doesNotExist();
        // --8<-- [end:gone]
    }

    // ---- components.md

    void textFields(UnifiedPage page) {
        // --8<-- [start:textFields]
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
        name.setValue("Alice");     // types as a user: fires input and change
        name.clear();
        String value = name.getValue();

        assertThat(name).hasValue("Alice").isValid().hasLabel("Name");
        assertThat(page.find(UnifiedTextField.class).withLabel("Email"))
                .isInvalid()
                .hasErrorMessage("Email is required");
        // --8<-- [end:textFields]
    }

    void unparsableText(UnifiedPage page, UnifiedDateTimePicker departure) {
        // --8<-- [start:unparsableText]
        UnifiedDatePicker returnDate = page.find(UnifiedDatePicker.class).withLabel("Return date");
        returnDate.typeText("dsdds");
        assertThat(returnDate).isInvalid();

        departure.typeText("dsdds", "10:00");   // UnifiedDateTimePicker: date text, time text
        // --8<-- [end:unparsableText]
    }

    void buttons(UnifiedPage page) {
        // --8<-- [start:buttons]
        page.find(UnifiedButton.class).withText("Save").click();
        assertThat(page.find(UnifiedButton.class).withText("Archive")).isDisabled();
        // --8<-- [end:buttons]
    }

    void tabsAndDetails(UnifiedPage page) {
        // --8<-- [start:tabsAndDetails]
        UnifiedTabSheet tabs = page.find(UnifiedTabSheet.class);
        tabs.selectTab("Security");
        assertThat(tabs).hasTabs("Profile", "Security", "Billing").hasSelectedTab("Security");
        tabs.content().find(UnifiedButton.class).withText("Change password").click();

        UnifiedTabs filter = page.find(UnifiedTabs.class).withTab("Approved");   // Tabs without content
        filter.selectTab("Rejected");

        UnifiedDetails advanced = page.find(UnifiedDetails.class).withSummaryText("Advanced");
        advanced.open();
        advanced.content().find(UnifiedTextField.class).withLabel("Cost center").setValue("CC-7");
        // --8<-- [end:tabsAndDetails]
    }

    void overlays(UnifiedPage page) {
        // --8<-- [start:overlays]
        page.find(UnifiedButton.class).withText("Change email").click();
        UnifiedDialog dialog = page.find(UnifiedDialog.class).withHeaderTitle("Change email");
        dialog.find(UnifiedTextField.class).withLabel("Email").setValue("new@example.com");
        dialog.find(UnifiedButton.class).withText("Save").click();
        assertThat(dialog).isClosed();

        dialog.close();                  // as pressing Escape

        assertThat(page.find(UnifiedNotification.class)).isOpen().hasText("Saved Alice");
        assertThat(page.find(UnifiedNotification.class).withText("Account deleted")).isOpen();
        // --8<-- [end:overlays]
    }

    void keysAndEvents(UnifiedPage page, UnifiedTextField search, UnifiedWrapper dropZone) {
        // --8<-- [start:keysAndEvents]
        search.element().pressKey("Enter");                          // to a component
        page.find(UnifiedTextField.class).withLabel("Search").element().pressKey("Control+s");
        page.pressKey("Escape");                                     // to the focused component or the open dialog
        dropZone.element().dispatchEvent("item-dropped");            // a custom DOM event, for a Flow listener
        // --8<-- [end:keysAndEvents]
    }

    void withoutWrapper(UnifiedPage page) {
        // --8<-- [start:withoutWrapper]
        assertThat(page.findComponent(Span.class).withId("status")).hasText("Approved");
        String total = page.find(UnifiedDiv.class).withClassName("summary").getText();
        page.findComponent(Input.class).withTestId("code").element().setValueAsUser("ABC");
        // --8<-- [end:withoutWrapper]
    }

    // ---- finding-components.md

    void anatomy(UnifiedPage page) {
        // --8<-- [start:anatomy]
        page.find(UnifiedTextField.class)   // which kind of component: the Wrapper
            .withLabel("Name")              // filters: zero or more, all must match
            .setValue("Alan M. Turing");    // use it: an action, a read or assertThat(..)
        // --8<-- [end:anatomy]
    }

    void containing(UnifiedPage page) {
        // --8<-- [start:containing]
        page.find(UnifiedButton.class).withText("Save");                      // "Save", not "Save draft"
        page.find(UnifiedButton.class).withTextContaining("save");            // "Save", "Save draft", "Autosave"
        page.find(UnifiedTextField.class).withLabelContaining("phone");       // "Phone", "Mobile phone"
        page.find(UnifiedButton.class).withAccessibleNameContaining("Edit");  // "Edit User 3", "Edit User 4", …
        // --8<-- [end:containing]
    }

    void narrowing(UnifiedPage page) {
        // --8<-- [start:narrowing]
        UnifiedButton delete = page.find(UnifiedButton.class).withText("Delete");
        delete.withTheme("error").click();              // the red one
        assertThat(delete).hasCount(2);                 // `delete` itself is unchanged
        // --8<-- [end:narrowing]
    }

    void lazy(UnifiedPage page) {
        // --8<-- [start:lazy]
        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");  // no search yet
        name.setValue("Alice");                  // searches, waits until it is there and enabled, types
        assertThat(name).hasValue("Alice");      // searches again
        // --8<-- [end:lazy]
    }

    void flowComponents(UnifiedPage page) {
        // --8<-- [start:flowComponents]
        assertThat(page.findComponent(Span.class).withId("status")).hasText("Approved");
        assertThat(page.findComponent(H2.class).withText("Admin area of alice")).isVisible();
        // --8<-- [end:flowComponents]
    }

    // ---- grid.md

    void gridRow(UnifiedPage page) {
        // --8<-- [start:gridRow]
        UnifiedGrid users = page.find(UnifiedGrid.class).withTestId("users");
        UnifiedGrid.UnifiedGridRow alice = users.row().withCellText("Name", "Alice");
        alice.click();
        // --8<-- [end:gridRow]
    }

    void gridCells(UnifiedGrid users, UnifiedGrid.UnifiedGridRow alice, UnifiedGrid archived) {
        // --8<-- [start:gridCells]
        assertThat(alice).hasCellText("Email", "alice@example.com").hasCellText("Role", "Admin");
        String email = alice.getCellText("Email");
        String first = users.row(0).getCellText(0);     // by column index, for columns without a header
        int index = alice.getIndex();
        assertThat(users).hasRowCount(120);             // all rows, not only the rendered ones
        assertThat(archived).isEmpty();
        // --8<-- [end:gridCells]
    }

    void gridColumns(UnifiedGrid users) {
        // --8<-- [start:gridColumns]
        users.column("Name").sort(SortDirection.ASCENDING);
        assertThat(users.column("Name")).isSortedAscending().hasCellTexts("Alice", "Bob", "Carol");
        List<String> headers = users.getColumnHeaders();
        // --8<-- [end:gridColumns]
    }

    void gridRowActions(UnifiedGrid users, UnifiedGrid.UnifiedGridRow row) {
        // --8<-- [start:gridRowActions]
        users.row(1).click();            // as a user clicking the row (not a button in it)
        users.row(4).doubleClick();

        row.toggleDetails();             // clicks the row, as a user does
        assertThat(row).hasDetailsOpen();
        row.details().find(UnifiedButton.class).withText("Reset password").click();   // browser only for now
        // --8<-- [end:gridRowActions]
    }

    // ---- navigation-and-users.md

    /** A view with a URL parameter, as {@code OrderView} in the guide. */
    static class OrderView extends Div implements HasUrlParameter<Long> {
        @Override
        public void setParameter(BeforeEvent event, Long parameter) {
        }
    }

    void navigate(UnifiedPage page) {
        // --8<-- [start:navigate]
        page.navigate(ProfileView.class);                                // by route class (recommended)
        page.navigate(OrderView.class, 42L);                             // HasUrlParameter
        page.navigate(OrderView.class, new RouteParameters("id", "42")); // route template parameters
        page.navigate("profile?tab=2");                                  // by path, relative to the base URL
        page.reload();
        // --8<-- [end:navigate]
    }

    void redirected(UnifiedPage page) {
        // --8<-- [start:redirected]
        page.navigate(AdminView.class);
        assertThat(page).isAt(LoginView.class);     // anonymous user sent to login
        // --8<-- [end:redirected]
    }

    void anotherUser(UnifiedPage alice) {
        // --8<-- [start:anotherUser]
        UnifiedPage bob = alice.openAsUser("bob", "USER");   // starts on no view: navigate first
        bob.navigate(WhoAmIView.class);
        assertThat(bob.findComponent(Span.class).withText("Signed in as bob")).isVisible();
        // --8<-- [end:anotherUser]
    }

    // ---- reports-and-failures.md

    // --8<-- [start:changesTheEmail]
    @Test
    @DisplayName("Changing a person's email updates the grid")
    void changesTheEmail(UnifiedPage page) {
        // given
        page.navigate(PeopleView.class);

        // when
        changeEmail(page, "Alan Turing", "alan@bletchley.uk");

        // then
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan Turing");
    }

    private static void changeEmail(UnifiedPage page, String name, String email) {
        page.step("Change the email of " + name, () -> {
            page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
            page.find(UnifiedTextField.class).withLabel("E-mail").setValue(email);
            page.find(UnifiedButton.class).withText("Save").click();
        });
    }
    // --8<-- [end:changesTheEmail]

    // ---- structuring-a-scenario.md

    // --8<-- [start:peopleTasks]
    final class PeopleTasks {

        private PeopleTasks() {
        }

        static void editPerson(UnifiedPage page, String name, String newName) {
            page.step("Edit " + name, () -> {
                page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
                page.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
                page.find(UnifiedButton.class).withText("Save").click();
            });
        }
    }
    // --8<-- [end:peopleTasks]

    void wrappersBeforeAStep(UnifiedPage page) {
        // --8<-- [start:wrappersBeforeAStep]
        // given
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);
        UnifiedNotification notification = page.find(UnifiedNotification.class);

        // when
        page.step("Edit Alan Turing", () -> {
            people.row().withCellText("Name", "Alan Turing").click();
            page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
            page.find(UnifiedButton.class).withText("Save").click();
        });

        // then
        assertThat(notification).hasText("Saved Alan M. Turing");
        assertThat(people.row().withCellText("Name", "Alan M. Turing")).exists();
        // --8<-- [end:wrappersBeforeAStep]
    }

    void secret(UnifiedPage page, String apiKey) {
        // --8<-- [start:secret]
        page.find(UnifiedTextField.class).withLabel("API key").setValue(page.secret(apiKey));
        // --8<-- [end:secret]
    }

    // --8<-- [start:peopleViewLookups]
    final class PeopleViewLookups {

        private final UnifiedPage page;

        PeopleViewLookups(UnifiedPage page) {
            this.page = page;
        }

        UnifiedGrid people() {
            return page.find(UnifiedGrid.class);
        }

        UnifiedTextField name() {
            return page.find(UnifiedTextField.class).withLabel("Name");
        }

        UnifiedButton save() {
            return page.find(UnifiedButton.class).withText("Save");
        }
    }
    // --8<-- [end:peopleViewLookups]

    // ---- style-rules.md

    void freeze() {
        // --8<-- [start:freeze]
        FreezingArchRule.freeze(UnifiedStyleRules.SCENARIO_SENTENCE)
                .check(new ClassFileImporter().importPackages("com.example"));
        // --8<-- [end:freeze]
    }
}
