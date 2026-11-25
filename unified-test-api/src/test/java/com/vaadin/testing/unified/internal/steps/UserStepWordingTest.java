package com.vaadin.testing.unified.internal.steps;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.testing.unified.component.UnifiedEmailField;
import com.vaadin.testing.unified.component.UnifiedPasswordField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.UserStepVerb;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedCheckbox;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedDateTimePicker;
import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedLink;
import com.vaadin.testing.unified.component.UnifiedLogin;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedMenuBar;
import com.vaadin.testing.unified.component.UnifiedMessageInput;
import com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.component.UnifiedUpload;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Gap;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Group;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Status;
import com.vaadin.testing.unified.internal.steps.StepRecorder.Step;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/** User Steps in words (spec chapter 12, "User Steps"): kinds, verbs, names, brackets, scope, masking, gaps. */
public class UserStepWordingTest {

    private final FakePage fake = new FakePage();

    /** An add-on Wrapper with its own kind and verb. */
    @UnifiedComponent(value = Button.class, kind = "star rating")
    public interface UnifiedStars extends UnifiedWrapper, CommonFilters<UnifiedStars>, LabelFilter<UnifiedStars> {
        @UserStepVerb("give {value} stars to")
        default void rate(int stars) { element().click(); }

        @UserStepVerb("reset")
        default void clearRating() { element().click(); }

        /** No act(): a void non-getter, so still a User Step, with the verb from its name. */
        default void setFavourite(boolean favourite) { }
    }

    /** An add-on Wrapper without overrides: kind and verb from its names. */
    @UnifiedComponent(Button.class)
    public interface UnifiedThumbsUp extends UnifiedWrapper, CommonFilters<UnifiedThumbsUp> {
        default void giveThumb() { element().click(); }
    }

    /** A Wrapper for a password field: what is typed into it never reaches the reports. */
    @UnifiedComponent(PasswordField.class)
    public interface UnifiedSecretField extends UnifiedWrapper, CommonFilters<UnifiedSecretField>,
            LabelFilter<UnifiedSecretField> {
        default void setValue(String value) { element().setValueAsUser(value); }
    }

    private List<String> texts() {
        return fake.steps.entries().stream().map(e -> switch (e) {
            case Step s -> s.phrase().render();
            case Group g -> "group " + g.name();
            case Gap g -> g.text();
        }).toList();
    }

    @Test
    void wrapperActionsReadAsVerbKindNameValue() {
        fake.add("vaadin-text-field", "label", "Name")
                .add("vaadin-button", "text", "Save")
                .add("vaadin-checkbox", "label", "Terms");

        fake.page.find(UnifiedTextField.class).withLabel("Name").setValue("Alan M. Turing");
        fake.page.find(UnifiedTextField.class).withLabel("Name").clear();
        fake.page.find(UnifiedButton.class).withText("Save").click();
        fake.page.find(UnifiedCheckbox.class).withLabel("Terms").check();
        fake.page.find(UnifiedCheckbox.class).withLabel("Terms").setChecked(false);
        fake.page.find(UnifiedButton.class).withText("Save").element().click();

        assertEquals(List.of(
                "fill text field “Name” with “Alan M. Turing”",
                "clear text field “Name”",
                "click button “Save”",
                "check checkbox “Terms”",
                "uncheck checkbox “Terms”",
                "click button “Save”"), texts());
    }

    @Test
    void aLinkClickThatNavigatesIsFollowedByTheOpenStep() {
        fake.add("a", "text", "Top", "href", "#top").add("a", "text", "Users", "href", "users");

        fake.page.find(UnifiedLink.class).withText("Top").click(); // stays on the page
        fake.onClick = () -> fake.path = "users";
        fake.page.find(UnifiedLink.class).withHref("users").click();

        assertEquals(List.of(
                "click link “Top”",
                "click link (href “users”)",
                "open “users”"), texts());
        assertEquals(List.of("click a", "click a"), fake.actions);
    }

    @Test
    void aTabHoldingALinkIsFollowedAndTheOpenStepFollows() {
        fake.add("vaadin-tabs", "tabs", "Orders,Users", "links", "Users", "selected", "Orders");

        fake.page.find(UnifiedTabs.class).selectTab("Orders"); // a plain tab: selected, stays on the page
        fake.onClick = () -> fake.path = "users";
        fake.page.find(UnifiedTabs.class).selectTab("Users"); // the browser clicks the tab's link

        assertEquals(List.of(
                "select tab “Orders” in tabs",
                "select tab “Users” in tabs",
                "open “users”"), texts());
        assertEquals(List.of("select Orders", "click a"), fake.actions);
    }

    @Test
    void nestedWrapperCallsAndGettersAreNotSteps() {
        fake.add("vaadin-checkbox", "label", "Terms", "checked", "false");
        UnifiedCheckbox terms = fake.page.find(UnifiedCheckbox.class).withLabel("Terms");

        terms.check(); // calls setChecked(true), then element().setValueAsUser(true)
        assertTrue(terms.isChecked()); // a getter: not a step
        terms.getLabel();

        assertEquals(List.of("check checkbox “Terms”"), texts());
        assertEquals(1, fake.steps.stepCount());
    }

    @Test
    void otherFiltersGoInBracketsAndTheScopeNamesTheContainer() {
        fake.add("vaadin-button", "text", "Save", "testId", "billing")
                .add("vaadin-dialog", "headerTitle", "Edit person")
                .add("vaadin-text-field", "label", "Email", "placeholder", "you@example.com");

        fake.page.find(UnifiedButton.class).withText("Save").withTestId("billing").click();
        fake.page.find(UnifiedButton.class).withTestId("billing").visible().click();
        UnifiedDialog dialog = fake.page.find(UnifiedDialog.class).withHeaderTitle("Edit person");
        dialog.find(UnifiedButton.class).withText("Save").click();
        dialog.find(UnifiedTextField.class).withPlaceholder("you@example.com").setValue("a@b.c");
        fake.page.find(UnifiedButton.class).withTextContaining("Sav").click();
        fake.page.find(UnifiedButton.class).all().get(0).click();

        assertEquals(List.of(
                "click button “Save” (test id “billing”)",
                "click button (test id “billing”, visible)",
                "click button “Save” in dialog “Edit person”",
                "fill text field (placeholder “you@example.com”) with “a@b.c” in dialog “Edit person”",
                "click button (text containing “Sav”)",
                "click the 1st button"), texts());
    }

    @Test
    void aConfirmDialogsAnswerNamesTheDialogByItsHeaderOrItsText() {
        fake.add("vaadin-confirm-dialog", "header", "Delete order?", "message", "It can't be restored.",
                "opened", "true", "buttons", "CONFIRM=Delete,REJECT=Keep,CANCEL=Back");
        fake.add("vaadin-confirm-dialog", "message", "Leave the page?", "opened", "true", "buttons", "CONFIRM=Leave",
                "testId", "leave");

        fake.page.find(UnifiedConfirmDialog.class).withHeaderTitle("Delete order?").confirm();
        fake.page.find(UnifiedConfirmDialog.class).withTextContaining("restored").reject();
        fake.page.find(UnifiedConfirmDialog.class).withHeaderTitle("Delete order?").cancel();
        fake.page.find(UnifiedConfirmDialog.class).withTestId("leave").confirm();

        assertEquals(List.of(
                "confirm “Delete order?”",
                "reject “Delete order?” (text containing “restored”)",
                "cancel “Delete order?”",
                "confirm “Leave the page?” (test id “leave”)"), texts());
        assertEquals(List.of("press CONFIRM", "press REJECT", "press CANCEL", "press CONFIRM"), fake.actions);
    }

    @Test
    void aConfirmDialogWithoutTheButtonFailsListingTheButtonsItHas() {
        fake.add("vaadin-confirm-dialog", "header", "Discard draft?", "opened", "true",
                "buttons", "CONFIRM=Discard,CANCEL=Keep editing");

        AssertionError error = assertThrows(AssertionError.class,
                () -> fake.page.find(UnifiedConfirmDialog.class).withHeaderTitle("Discard draft?").reject());

        assertTrue(error.getMessage().contains("to press the Reject button of confirm dialog “Discard draft?”"),
                error.getMessage());
        assertTrue(error.getMessage().contains("no Reject button; its buttons: Confirm “Discard”, Cancel “Keep editing”"),
                error.getMessage());
        assertEquals(List.of(), fake.actions);
    }

    @Test
    void signingInNamesTheUserNeverThePassword() {
        fake.add("vaadin-login-overlay", "title", "Bakery", "opened", "true");

        fake.page.find(UnifiedLogin.class).login("barista@vaadin.com", "coffee-beans");
        fake.page.find(UnifiedLogin.class).withTitle("Bakery").forgotPassword();

        assertEquals(List.of(
                "sign in as “barista@vaadin.com” in login",
                "press “Forgot password” in login “Bakery”"), texts());
        assertEquals(List.of("login barista@vaadin.com coffee-beans", "forgot password"), fake.actions);
        assertEquals("a log line with ••••", fake.steps.mask("a log line with coffee-beans"));
    }

    @Test
    void aPasswordInsideItsUserNameIsNotMaskedThereButEverywhereElse() {
        fake.add("vaadin-login-overlay", "opened", "true");

        fake.page.find(UnifiedLogin.class).login("barista@vaadin.com", "barista");

        assertEquals("sign in as “barista@vaadin.com” in login", fake.steps.mask(texts().get(0)));
        assertEquals("Sign in as barista@vaadin.com", fake.steps.mask("Sign in as barista@vaadin.com"));
        assertEquals("password •••• rejected for barista@vaadin.com",
                fake.steps.mask("password barista rejected for barista@vaadin.com"));
        assertEquals("expected “••••”, found “••••2”",
                fake.steps.mask("expected “barista”, found “barista2”"));
    }

    @Test
    void aPasswordEqualToItsUserNameIsMaskedEverywhere() {
        fake.add("vaadin-login-overlay", "opened", "true");

        fake.page.find(UnifiedLogin.class).login("admin", "admin");

        assertEquals("sign in as “••••” in login", fake.steps.mask(texts().get(0)));
    }

    @Test
    void anotherSecretInsideAUserNameIsStillMaskedThere() {
        fake.add("vaadin-login-overlay", "opened", "true");
        fake.page.secret("vaadin");

        fake.page.find(UnifiedLogin.class).login("barista@vaadin.com", "barista");

        assertEquals("sign in as “barista@••••.com” in login", fake.steps.mask(texts().get(0)));
    }

    @Test
    void anAdaptersFailureWhileSigningInIsMaskedInTheReports() {
        fake.add("vaadin-login-form");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> fake.page.find(UnifiedLogin.class).login("barista@vaadin.com", "boom"));

        assertEquals(List.of("sign in as “barista@vaadin.com” in login"), texts());
        assertEquals("the form rejected barista@vaadin.com/••••", fake.steps.mask(error.getMessage()));
    }

    @Test
    void aFormThatPostsFailsWithoutABrowserBeforeSigningIn() {
        fake.add("vaadin-login-form", "action", "login", "postsForms", "false");

        AssertionError error = assertThrows(AssertionError.class,
                () -> fake.page.find(UnifiedLogin.class).login("barista@vaadin.com", "coffee-beans"));

        assertTrue(error.getMessage().contains("to sign in through login"), error.getMessage());
        assertTrue(error.getMessage().contains("a login form that posts to “login”, which BROWSERLESS can't submit"),
                error.getMessage());
        assertTrue(error.getMessage().contains("@RequiresBrowser"), error.getMessage());
        assertTrue(error.getMessage().contains("@WithMockUser"), error.getMessage());
        assertFalse(error.getMessage().contains("coffee-beans"), error.getMessage());
        assertEquals(List.of(), fake.actions);
    }

    @Test
    void forgotPasswordFailsWhenTheButtonIsHidden() {
        fake.add("vaadin-login-form", "noForgotPassword", "true");

        AssertionError error = assertThrows(AssertionError.class,
                () -> fake.page.find(UnifiedLogin.class).forgotPassword());

        assertTrue(error.getMessage().contains("no “Forgot password” button"), error.getMessage());
        assertEquals(List.of(), fake.actions);
    }

    @Test
    void customFiltersReadAsThemselvesInFailuresToo() {
        fake.add("vaadin-dialog", "headerTitle", "Edit person");
        UnifiedDialog dialog = fake.page.find(UnifiedDialog.class).withHeaderTitle("Edit person");

        assertEquals("UnifiedDialog[UnifiedDialog.withHeaderTitle(\"Edit person\")]", dialog.toString());
        assertEquals("dialog “Edit person”", StepTarget.of(Wrappers.lazyElementOf(dialog)).render(false));
    }

    @Test
    void kindsAreTheWrapperTypeInPlainWords() {
        assertEquals("combo box", StepTarget.kindOf(UnifiedComboBox.class));
        assertEquals("multi-select combo box", StepTarget.kindOf(UnifiedMultiSelectComboBox.class));
        assertEquals("date-time picker", StepTarget.kindOf(UnifiedDateTimePicker.class));
        assertEquals("text field", StepTarget.kindOf(UnifiedTextField.class));
        assertEquals("menu bar", StepTarget.kindOf(UnifiedMenuBar.class));
        assertEquals("thumbs up", StepTarget.kindOf(UnifiedThumbsUp.class));
        assertEquals("HTML view", StepTarget.kindOf(HTMLView.class));
        assertEquals(List.of("Multi", "Select", "Combo", "Box"), StepTarget.splitWords("MultiSelectComboBox"));
    }

    interface HTMLView {}

    @Test
    void findComponentReadsAsTheComponentClass() {
        fake.add("vaadin-button", "text", "Cancel");

        fake.page.findComponent(Button.class).withText("Cancel").element().click();

        assertEquals(List.of("click button “Cancel”"), texts());
    }

    @Test
    void addOnWrappersTakeKindAndVerbFromTheirNamesOrAnnotations() {
        fake.add("vaadin-button", "label", "Quality");

        fake.page.find(UnifiedStars.class).withLabel("Quality").rate(4);
        fake.page.find(UnifiedStars.class).withLabel("Quality").clearRating();
        fake.page.find(UnifiedStars.class).withLabel("Quality").setFavourite(true);
        fake.page.find(UnifiedThumbsUp.class).giveThumb();

        assertEquals(List.of(
                "give “4” stars to star rating “Quality”",
                "reset star rating “Quality”",
                "set favourite star rating “Quality” with “true”",
                "give thumb thumbs up"), texts());
    }

    @Test
    void theVerbTableCoversTheCoreSet() throws Exception {
        fake.add("vaadin-combo-box", "label", "Role")
                .add("vaadin-menu-bar", "testId", "menu")
                .add("vaadin-upload", "testId", "receipt")
                .add("vaadin-multi-select-combo-box", "label", "Tags")
                .add("vaadin-message-input", "testId", "chat");

        assertEquals("choose “Admin” in combo box “Role”",
                phrase(fake.page.find(UnifiedComboBox.class).withLabel("Role"), "selectItem", "Admin"));
        assertEquals("select “a”, “b” in multi-select combo box “Tags”",
                phrase(fake.page.find(UnifiedMultiSelectComboBox.class).withLabel("Tags"), "selectItems",
                        (Object) new String[] { "a", "b" }));
        assertEquals("click “File › Export” in menu bar (test id “menu”)",
                phrase(fake.page.find(UnifiedMenuBar.class).withTestId("menu"), "clickItem", "File",
                        new String[] { "Export" }));
        assertEquals("upload “receipt.pdf” to upload (test id “receipt”)",
                phrase(fake.page.find(UnifiedUpload.class).withTestId("receipt"), "upload", "receipt.pdf",
                        "application/pdf", new byte[1]));
        assertEquals("send “Hello” in message input (test id “chat”)",
                phrase(fake.page.find(UnifiedMessageInput.class).withTestId("chat"), "send", "Hello"));
    }

    private static String phrase(UnifiedWrapper wrapper, String method, Object... args) throws Exception {
        Class<?> type = wrapper.getClass().getInterfaces()[0];
        Method m = java.util.Arrays.stream(type.getMethods())
                .filter(x -> x.getName().equals(method) && x.getParameterCount() == args.length)
                .findFirst().orElseThrow();
        return Phrases.forWrapperCall(type, Wrappers.lazyElementOf(wrapper), m, args).render();
    }

    @Test
    void assertionsReadSeeOrSeeNo() {
        fake.add("vaadin-text-field", "label", "Name", "value", "Alan")
                .add("vaadin-notification", "text", "Saved Alan")
                .add("vaadin-button", "text", "Delete")
                .add("vaadin-button", "text", "Delete")
                .add("vaadin-checkbox", "label", "Terms", "checked", "true");

        assertThat(fake.page.find(UnifiedTextField.class).withLabel("Name")).hasValue("Alan");
        assertThat(fake.page.find(UnifiedNotification.class)).hasText("Saved Alan");
        assertThat(fake.page.find(UnifiedButton.class).withText("Archive")).doesNotExist();
        assertThat(fake.page.find(UnifiedButton.class).withText("Delete")).hasCount(2);
        assertThat(fake.page.find(UnifiedCheckbox.class).withLabel("Terms")).isChecked();
        assertThat(fake.page).isAt(com.vaadin.flow.component.html.Div.class);
        assertThat(fake.page).hasTitle("People");

        assertEquals(List.of(
                "see text field “Name” with value “Alan”",
                "see notification “Saved Alan”",
                "see no button “Archive”",
                "see 2 buttons “Delete”",
                "see checkbox “Terms” checked",
                "see Div",
                "see title “People”"), texts());
    }

    @Test
    void menuBarAssertionsReadAsSeeWithItems() {
        fake.add("vaadin-menu-bar", "testId", "menu", "items", "File,Help", "items File", "Export,Print",
                "items File/Export", "PDF,CSV", "disabled", "File/Print");
        UnifiedMenuBar menu = fake.page.find(UnifiedMenuBar.class).withTestId("menu");

        assertThat(menu).hasItems("File", "Help").hasItems(List.of("File", "Export"), "PDF", "CSV")
                .hasItem("File", "Export", "CSV").hasItemEnabled("File", "Export").hasItemDisabled("File", "Print");

        assertEquals(List.of(
                "see menu bar (test id “menu”) with items “File”, “Help”",
                "see menu bar (test id “menu”) with items under “File › Export” “PDF”, “CSV”",
                "see menu bar (test id “menu”) with item “File › Export › CSV”",
                "see menu bar (test id “menu”) with enabled item “File › Export”",
                "see menu bar (test id “menu”) with disabled item “File › Print”"), texts());
    }

    @Test
    void aMissingColumnReadsAsWithout() {
        fake.add("vaadin-grid", "testId", "archived");
        StepTarget grid = StepTarget.of(Wrappers.lazyElementOf(
                fake.page.find(com.vaadin.testing.unified.component.UnifiedGrid.class).withTestId("archived")));

        assertEquals("see grid (test id “archived”) with column “Archived on”",
                Phrases.forCheck(grid, "to have column", "Archived on").render());
        assertEquals("see grid (test id “archived”) without column “Archived on”",
                Phrases.forCheck(grid, "not to have column", "Archived on").render());
    }

    @Test
    void pageStepsReadAsTheUserWouldSayThem() {
        fake.page.navigate(PeopleView.class);
        fake.page.navigate("profile?tab=2");
        fake.page.pressKey("Escape");

        assertEquals(List.of("open People", "open “profile?tab=2”", "press “Escape”"), texts());
    }

    @Test
    void openingTheRootRouteReadsSlashHoweverItIsSpelled() {
        fake.page.navigate("");
        fake.page.navigate("/");
        fake.page.navigate("users");

        assertEquals(List.of("open “/”", "open “/”", "open “users”"), texts());
    }

    @Test
    void aLinkClickBackToTheRootRouteReadsOpenSlash() {
        fake.add("a", "text", "Home", "href", "");
        fake.path = "users";
        fake.onClick = () -> fake.path = "";
        fake.page.find(UnifiedLink.class).withText("Home").click();

        assertEquals(List.of("click link “Home”", "open “/”"), texts());
    }

    static class PeopleView extends com.vaadin.flow.component.html.Div {}

    @Test
    void openingAViewWithQueryParametersNamesThem() {
        // issue #370
        fake.page.navigate(PeopleView.class, com.vaadin.flow.router.QueryParameters.of("week", "2026-11-02"));
        fake.page.navigate(PeopleView.class, new com.vaadin.flow.router.RouteParameters("owner", "ada"),
                com.vaadin.flow.router.QueryParameters.of("week", "2026-11-02"));
        fake.page.navigate(PeopleView.class, com.vaadin.flow.router.QueryParameters.empty());

        assertEquals(List.of("open People with “week=2026-11-02”", "open People with “owner=ada, week=2026-11-02”",
                "open People"), texts());
    }

    @Test
    void passwordValuesAndSecretsAreMasked() {
        fake.add("vaadin-password-field", "label", "Password")
                .add("vaadin-text-field", "label", "API key", "value", "k-123");

        fake.page.find(UnifiedSecretField.class).withLabel("Password").setValue("hunter2");
        fake.page.find(UnifiedTextField.class).withLabel("API key").setValue(fake.page.secret("k-123"));
        assertThat(fake.page.find(UnifiedTextField.class).withLabel("API key")).hasValue("k-123");

        String lines = fake.lines();
        assertTrue(lines.contains("fill secret field “Password” with “••••”"), lines);
        assertTrue(lines.contains("fill text field “API key” with “••••”"), lines);
        assertTrue(lines.contains("see text field “API key” with value “••••”"), lines);
        assertFalse(lines.contains("hunter2") || lines.contains("k-123"), lines);
        String json = new UserStepsReport("demo.FakeTest", "test", "test", "browserless", "passed", null,
                fake.steps).json();
        assertFalse(json.contains("hunter2") || json.contains("k-123"), json);
    }

    /** The Core Set's password field hides what is typed into it, and its values in a failed check (#555). */
    @Test
    void corePasswordFieldIsMaskedInStepsAndFailures() {
        fake.add("vaadin-password-field", "label", "Password", "value", "hunter")
                .add("vaadin-email-field", "label", "Email");

        fake.page.find(UnifiedPasswordField.class).withLabel("Password").setValue("hunter2");
        fake.page.find(UnifiedEmailField.class).withLabel("Email").setValue("ada@example.com");
        AssertionError error = assertThrows(AssertionError.class,
                () -> assertThat(fake.page.find(UnifiedPasswordField.class).withLabel("Password")).hasValue("hunter3"));

        String lines = fake.lines();
        assertTrue(lines.contains("fill password field “Password” with “••••”"), lines);
        assertTrue(lines.contains("fill email field “Email” with “ada@example.com”"), lines);
        assertTrue(lines.contains("see password field “Password” with value “••••”"), lines);
        assertFalse(lines.contains("hunter"), lines);
        assertTrue(error.getMessage().contains("password field “Password” to have value “••••”"), error.getMessage());
        assertFalse(error.getMessage().contains("hunter"), error.getMessage());
    }

    @Test
    void longValuesAreCut() {
        fake.add("vaadin-text-field", "label", "Notes");
        String value = "x".repeat(100);

        fake.page.find(UnifiedTextField.class).withLabel("Notes").setValue(value);

        assertEquals(List.of("fill text field “Notes” with “" + "x".repeat(79) + "…”"), texts());
        assertEquals(80, Phrase.cut(value).length());
        assertEquals("short", Phrase.cut("short"));
    }

    @Test
    void callsTheRecorderCantSeeAreOneGapLinePerRun() {
        fake.add("vaadin-button", "text", "Save");
        UnifiedButton save = fake.page.find(UnifiedButton.class).withText("Save");

        save.tryUnwrap(Button.class);
        fake.page.tryUnwrap(Object.class);
        save.element().tryUnwrap(Button.class);
        save.click();
        fake.page.waitUntil(() -> save.isVisible());
        fake.page.waitUntil(() -> true);
        fake.page.find(UnifiedButton.class).where(e -> true).click();

        assertEquals(List.of(
                "… 3 calls outside the Unified API (Escape Hatch)",
                "click button “Save”",
                "… 2 calls outside the Unified API (waitUntil)",
                "… 1 call outside the Unified API (where(..) condition)",
                "click button (matching a condition)"), texts());
    }

    @Test
    void theApiReachingItsOwnTypesIsNoGap() {
        fake.add("vaadin-button", "text", "Save");

        fake.page.find(UnifiedButton.class).withText("Save").tryUnwrap(com.vaadin.testing.unified.spi.GridAccess.class);

        assertEquals(List.of(), texts());
    }

    @Test
    void stepsNestAndNumberTheirUserSteps() {
        fake.add("vaadin-button", "text", "Save");
        UnifiedButton save = fake.page.find(UnifiedButton.class).withText("Save");

        save.click();
        fake.page.step("Edit Alan Turing", () -> {
            save.click();
            fake.page.step("Save the form", save::click);
            save.click();
        });
        save.click();

        assertEquals("""
                   1  click button “Save”
                   2  Edit Alan Turing
                      2.1  click button “Save”
                      2.2  Save the form
                         2.2.1  click button “Save”
                      2.3  click button “Save”
                   3  click button “Save”
                """, fake.lines());
        Group group = (Group) fake.steps.entries().get(1);
        assertEquals("UserStepWordingTest.java", group.callSite().file());
        assertEquals("stepsNestAndNumberTheirUserSteps", group.callSite().method());
        Step inner = (Step) ((Group) group.children().get(1)).children().get(0);
        assertEquals("2.2.1", inner.index());
        assertEquals(3, inner.number());
    }

    @Test
    void aFailureInsideAStepPointsAtTheStepFirstThenTheInnerLine() {
        UnifiedButton missing = fake.page.find(UnifiedButton.class).withText("Missing");

        AssertionError error = assertThrows(AssertionError.class,
                () -> fake.page.step("Outer", () -> fake.page.step("Inner", missing::click)));

        Step failed = fake.steps.stepThatThrew(error);
        assertEquals("1.1.1", failed.index());
        assertEquals(Status.FAILED, failed.status());
        assertSame(error, failed.error());
        List<CallSite> path = failed.codePath();
        assertEquals(3, path.size(), path.toString());
        assertTrue(path.get(0).line() < path.get(1).line() || path.get(0).line() == path.get(1).line(),
                path.toString());
        path.forEach(site -> assertEquals("UserStepWordingTest.java", site.file()));
        assertEquals(2, fake.steps.groupsThatThrew(error).size());
        assertTrue(fake.lines().contains("1.1.1  click button “Missing”   ✘"), fake.lines());
    }

    /** The expected items in the test's order, so two runs of a scenario word the step alike. */
    @Test
    void selectedItemsAreNamedInTheTestsOrder() {
        FakePage fake = new FakePage().add("vaadin-multi-select-combo-box", "label", "Toppings");
        var toppings = fake.page.find(com.vaadin.testing.unified.component.UnifiedMultiSelectComboBox.class)
                .withLabel("Toppings");

        // the fake page has no selection to read, so the check fails; its step is recorded all the same
        org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> com.vaadin.testing.unified.assertion
                .UnifiedAssertions.assertThat(toppings).hasSelectedItems("Olives", "Ham", "Cheese", "Basil", "Corn", "Egg"));

        org.junit.jupiter.api.Assertions.assertTrue(fake.lines().contains("see multi-select combo box “Toppings” with "
                + "selected items “Olives”, “Ham”, “Cheese”, “Basil”, “Corn”, “Egg”"), fake.lines());
    }
}
