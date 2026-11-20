package demo;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedBigDecimalField;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDatePicker;
import com.vaadin.testing.unified.component.UnifiedDateTimePicker;
import com.vaadin.testing.unified.component.UnifiedDetails;
import com.vaadin.testing.unified.component.UnifiedDiv;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedSection;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextArea;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.component.UnifiedUpload;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Core Set wrappers added for a typical Flow app, on every Testing Tool. */
@UnifiedSpringBootTest
class ExpenseTest {

    private static void assertStatus(UnifiedPage page, String text) {
        assertThat(page.findComponent(Span.class).withId("status")).hasText(text);
    }

    // --8<-- [start:fillsTheValueFields]
    @Test
    @DisplayName("The user fills notes, nights, amount and departure, and saves the expense")
    void fillsTheValueFields(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        LocalDateTime departure = LocalDateTime.of(2026, 11, 8, 10, 30);

        page.find(UnifiedTextArea.class).withLabel("Notes").setValue("Taxi to the airport");
        page.find(UnifiedIntegerField.class).withLabel("Nights").setValue(3);
        page.find(UnifiedBigDecimalField.class).withLabel("Amount").setValue(new BigDecimal("54.20"));
        page.find(UnifiedDateTimePicker.class).withLabel("Departure").setValue(departure);
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class))
                .hasText("Saved: Taxi to the airport / 3 nights / 54.20 / 2026-11-08T10:30 / null /");
        assertThat(page.find(UnifiedTextArea.class).withLabel("Notes")).hasValue("Taxi to the airport");
        assertThat(page.find(UnifiedIntegerField.class).withLabel("Nights")).hasValue(3);
        assertThat(page.find(UnifiedBigDecimalField.class).withLabel("Amount")).hasValue(new BigDecimal("54.2"));
        assertThat(page.find(UnifiedDateTimePicker.class).withLabel("Departure")).hasValue(departure);
    }
    // --8<-- [end:fillsTheValueFields]

    @Test
    @DisplayName("Typing text that isn't a date leaves the return date invalid and empty")
    void typesTextADatePickerCantParse(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedDatePicker returnDate = page.find(UnifiedDatePicker.class).withLabel("Return date");

        returnDate.typeText("dsdds");

        assertThat(returnDate).isInvalid();
        assertNull(returnDate.getValue());
    }

    @Test
    @DisplayName("Typing text that isn't a date marks the departure invalid until a valid one is set")
    void typesTextADateTimePickerCantParse(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedDateTimePicker departure = page.find(UnifiedDateTimePicker.class).withLabel("Departure");

        departure.typeText("dsdds", "10:00");

        assertThat(departure).isInvalid();
        assertNull(departure.getValue());

        LocalDateTime value = LocalDateTime.of(2026, 11, 8, 10, 30);
        departure.setValue(value);

        assertThat(departure).isValid().hasValue(value);
    }

    @Test
    @DisplayName("A departure with a time but no date is invalid")
    void typesAnIncompleteDateTime(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedDateTimePicker departure = page.find(UnifiedDateTimePicker.class).withLabel("Departure");
        departure.setValue(LocalDateTime.of(2026, 11, 8, 10, 30));

        departure.typeText("", "10:00");

        assertThat(departure).isInvalid();
        assertNull(departure.getValue());
    }

    // --8<-- [start:typesADateWithoutATime]
    @Test
    @DisplayName("A departure with a date but no time asks for both, on every Testing Tool and locale")
    void typesADateWithoutATime(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedDateTimePicker departure = page.find(UnifiedDateTimePicker.class).withLabel("Departure");

        departure.typeText(LocalDate.of(2026, 7, 1), "");

        assertThat(departure).isInvalid().hasErrorMessage("Enter both a date and a time");
        assertNull(departure.getValue());
    }
    // --8<-- [end:typesADateWithoutATime]

    @Test
    @DisplayName("A Finnish-locale arrival with a date but no time asks for both")
    void typesADateInTheFieldsLocale(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedDateTimePicker arrival = page.find(UnifiedDateTimePicker.class).withLabel("Arrival");

        arrival.typeText(LocalDate.of(2026, 7, 1), "");

        assertThat(arrival).isInvalid().hasErrorMessage("Enter both a date and a time");
        assertNull(arrival.getValue());
    }

    @Test
    @DisplayName("The user filters the expenses to the rejected ones")
    void selectsATab(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedTabs filter = page.find(UnifiedTabs.class).withTab("Approved");

        filter.selectTab("Rejected");

        assertThat(filter).hasTabs("Open", "Approved", "Rejected").hasSelectedTab("Rejected");
        assertStatus(page, "Showing Rejected");
    }

    // --8<-- [start:opensDetails]
    @Test
    @DisplayName("The user opens the Advanced section and saves a cost center")
    void opensDetails(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedDetails advanced = page.find(UnifiedDetails.class).withSummaryText("Advanced");

        advanced.open();
        advanced.content().find(UnifiedTextField.class).withLabel("Cost center").setValue("CC-7");

        assertThat(advanced).isOpen();
        page.find(UnifiedButton.class).withText("Save").click();
        assertThat(page.find(UnifiedNotification.class)).hasTextContaining("/ CC-7");
    }
    // --8<-- [end:opensDetails]

    // --8<-- [start:clicksNestedMenuItems]
    @Test
    @DisplayName("The user exports the expenses as CSV from the File menu, then opens Help")
    void clicksNestedMenuItems(UnifiedPage page) {
        page.navigate(ExpenseView.class);
        UnifiedMenuBar menu = page.find(UnifiedMenuBar.class).withItem("File");

        assertThat(menu).hasItems("File", "Help")
                .hasItems(List.of("File", "Export"), "PDF", "CSV");
        menu.clickItem("File", "Export", "CSV");
        assertStatus(page, "Exported CSV");

        menu.clickItem("Help");
        assertStatus(page, "Help opened");
    }
    // --8<-- [end:clicksNestedMenuItems]

    // --8<-- [start:uploadsAFile]
    @Test
    @DisplayName("The user uploads a receipt")
    void uploadsAFile(UnifiedPage page) {
        page.navigate(ExpenseView.class);

        page.find(UnifiedUpload.class).withId("receipt").upload("receipt.txt", "text/plain",
                "Taxi 54.20".getBytes(StandardCharsets.UTF_8));

        assertStatus(page, "Received receipt.txt: Taxi 54.20");
    }
    // --8<-- [end:uploadsAFile]

    /** Returns once the upload is handled, even when the app clears the file list right after (#122). */
    @Test
    @DisplayName("The user uploads three attachments one after another")
    void uploadsRepeatedlyWhenTheAppClearsTheFileList(UnifiedPage page) {
        page.navigate(ExpenseView.class);

        for (int i = 1; i <= 3; i++) {
            page.find(UnifiedUpload.class).withId("attachment").upload("receipt-" + i + ".txt", "text/plain",
                    "Taxi".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(page.findComponent(Span.class).withId("attachments"))
                .hasText("receipt-1.txt, receipt-2.txt, receipt-3.txt");
    }

    // --8<-- [start:searchesInPlainHtmlContainers]
    @Test
    @DisplayName("The summary and the payment note are found in plain HTML containers")
    void searchesInPlainHtmlContainers(UnifiedPage page) {
        page.navigate(ExpenseView.class);

        assertEquals("Total120.50", page.find(UnifiedDiv.class).withClassName("summary").getText());
        assertEquals("120.50", page.find(UnifiedDiv.class).withClassName("summary")
                .findComponent(Span.class).withText("120.50").getText());
        assertThat(page.find(UnifiedSection.class)).hasText("Expenses are paid monthly.");
        // a component found with findComponent is a Scope too
        assertThat(page.findComponent(VerticalLayout.class)
                .find(UnifiedSection.class)).hasCount(1);
    }
    // --8<-- [end:searchesInPlainHtmlContainers]
}
