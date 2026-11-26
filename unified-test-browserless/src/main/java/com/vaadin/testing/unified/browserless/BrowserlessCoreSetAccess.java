package com.vaadin.testing.unified.browserless;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.confirmdialog.ConfirmDialogTester;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.login.AbstractLogin;
import com.vaadin.flow.component.login.AbstractLoginTester;
import com.vaadin.flow.component.details.DetailsTester;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageInputTester;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.menubar.MenuBarTester;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsTester;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadTester;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.spi.ConfirmDialogAccess;
import com.vaadin.testing.unified.spi.DateTimePickerAccess;
import com.vaadin.testing.unified.spi.DetailsAccess;
import com.vaadin.testing.unified.spi.LoginAccess;
import com.vaadin.testing.unified.spi.MenuBarAccess;
import com.vaadin.testing.unified.spi.MessageInputAccess;
import com.vaadin.testing.unified.spi.MessageListAccess;
import com.vaadin.testing.unified.spi.TabSheetAccess;
import com.vaadin.testing.unified.spi.UploadAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Access to LoginOverlay and LoginForm, ConfirmDialog, Tabs, Details, MenuBar, Upload, DateTimePicker, MessageInput and MessageList on Browserless, through
 * their testers.
 */
final class BrowserlessCoreSetAccess {
    private BrowserlessCoreSetAccess() {}

    /** A bare Tabs bar: TabSheetAccess without content. Tabs are found by their Item Name. */
    record TabsAccess(BrowserlessSession session, Tabs bar) implements TabSheetAccess {
        private List<Tab> all() {
            return bar.getChildren().filter(Tab.class::isInstance).map(Tab.class::cast).toList();
        }

        @Override
        public List<String> tabs() {
            return all().stream().map(ItemNames::of).toList();
        }

        @Override
        public String selectedTab() {
            Tab selected = bar.getSelectedTab();
            return selected == null ? null : ItemNames.of(selected);
        }

        @Override
        public boolean isEnabled(String label) {
            return ItemNames.find(all(), label, "tab \"" + label + "\"").isEnabled();
        }

        /** By position: TabsTester.select(label) compares the label, which a tab holding a component lacks. */
        @Override
        public void select(String label) {
            session.act(bar, () -> {
                List<Tab> all = all();
                int position = ItemNames.visiblePosition(all, ItemNames.find(all, label, "tab \"" + label + "\""), "Tab");
                ((TabsTester<?>) session.window().test(bar)).select(position);
                session.window().roundTrip();
            });
        }

        @Override
        public Optional<UnifiedElement> link(String label) {
            return BrowserlessLinks.heldBy(session, ItemNames.find(all(), label, "tab \"" + label + "\""));
        }

        @Override
        public UnifiedElement content() {
            throw new UnsupportedOperationException("A Tabs bar has no content; use UnifiedTabSheet for a TabSheet");
        }
    }

    /**
     * Through ConfirmDialogTester, by role. A button the application put in a role's slot is clicked first, as a
     * browser's click runs its listeners, then the dialog fires the role's event and closes, as the web component
     * does on that click.
     */
    record ConfirmDialogAccessImpl(BrowserlessSession session, ConfirmDialog dialog) implements ConfirmDialogAccess {

        @Override
        public Map<Role, String> buttons() {
            Map<Role, String> buttons = new EnumMap<>(Role.class);
            for (Role role : Role.values()) {
                if (role == Role.CONFIRM || dialog.getElement().getProperty(key(role) + "ButtonVisible", false)) {
                    buttons.put(role, own(role).map(c -> new BrowserlessElement(session, c).getText())
                            .orElseGet(() -> dialog.getElement().getProperty(key(role) + "Text", role.title())));
                }
            }
            return buttons;
        }

        @Override
        public void press(Role role) {
            session.act(dialog, () -> {
                Optional<Component> own = own(role);
                if (own.isPresent()) {
                    new BrowserlessElement(session, own.get()).click();
                    fire(dialog, role);
                } else {
                    ConfirmDialogTester tester = (ConfirmDialogTester) session.window().test(dialog);
                    switch (role) {
                        case CONFIRM -> tester.confirm();
                        case REJECT -> tester.reject();
                        case CANCEL -> tester.cancel();
                    }
                }
                session.window().roundTrip();
            });
        }

        /** What the web component does on Escape, unless the application turned it off: it cancels. */
        static void escape(ConfirmDialog dialog) {
            if (dialog.isCloseOnEsc()) {
                fire(dialog, Role.CANCEL);
            }
        }

        /** The role's event, as from the browser, then the dialog closes, unless a listener closed it. */
        private static void fire(ConfirmDialog dialog, Role role) {
            ComponentUtil.fireEvent(dialog, switch (role) {
                case CONFIRM -> new ConfirmDialog.ConfirmEvent(dialog, true);
                case REJECT -> new ConfirmDialog.RejectEvent(dialog, true);
                case CANCEL -> new ConfirmDialog.CancelEvent(dialog, true);
            });
            if (dialog.isOpened()) {
                dialog.close();
            }
        }

        /** The button the application put in the role's slot ({@code setConfirmButton(Component)}), if any. */
        private Optional<Component> own(Role role) {
            String slot = key(role) + "-button";
            return dialog.getChildren().filter(c -> slot.equals(c.getElement().getAttribute("slot"))).findFirst();
        }

        private static String key(Role role) {
            return role.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Through Browserless Test's login tester, which fires the component's LoginEvent or ForgotPasswordEvent as the
     * web component does on submit. The form's {@code action} is not followed: there is no browser to post it.
     */
    record LoginAccessImpl(BrowserlessSession session, AbstractLogin login) implements LoginAccess {
        @Override
        public boolean postsForms() {
            return false;
        }

        @Override
        public void login(String username, String password) {
            session.act(login, () -> {
                tester().login(username, password);
                session.window().roundTrip();
            });
        }

        @Override
        public void forgotPassword() {
            session.act(login, () -> {
                tester().forgotPassword();
                session.window().roundTrip();
            });
        }

        private AbstractLoginTester<?> tester() {
            return (AbstractLoginTester<?>) session.window().test(login);
        }
    }

    record DetailsAccessImpl(BrowserlessSession session, Details details) implements DetailsAccess {
        @Override
        public String summaryText() {
            return details.getSummaryText();
        }

        @Override
        public boolean isOpen() {
            return details.isOpened();
        }

        @Override
        public void toggle() {
            session.act(details, () -> {
                ((DetailsTester<?>) session.window().test(details)).toggleDetails();
                session.window().roundTrip();
            });
        }

        @Override
        public UnifiedElement content() {
            List<Component> content = details.getContent().toList();
            return new BrowserlessElement(session, details, content);
        }
    }

    /**
     * Items found by their Item Name, then clicked by position: MenuBarTester matches an item's own text,
     * which an icon-only item lacks.
     */
    record MenuBarAccessImpl(BrowserlessSession session, MenuBar menuBar) implements MenuBarAccess {

        @Override
        public List<String> items(List<String> path) {
            List<MenuItem> items = path.isEmpty() ? menuBar.getItems()
                    : resolve(path).get(path.size() - 1).getSubMenu().getItems();
            return items.stream().filter(Component::isVisible).map(ItemNames::of).toList();
        }

        @Override
        public void click(List<String> path, boolean throughOverflow) { // no layout: nothing is folded
            session.act(menuBar, () -> {
                List<MenuItem> chosen = resolve(path);
                int[] positions = new int[path.size()];
                List<MenuItem> level = menuBar.getItems();
                for (int i = 0; i < path.size(); i++) {
                    positions[i] = ItemNames.visiblePosition(level, chosen.get(i), "Menu item");
                    level = chosen.get(i).getSubMenu().getItems();
                }
                ((MenuBarTester<?>) session.window().test(menuBar)).clickItem(positions[0],
                        java.util.Arrays.copyOfRange(positions, 1, positions.length));
                session.window().roundTrip();
            });
        }

        @Override
        public boolean isEnabled(List<String> path, boolean throughOverflow) {
            return resolve(path).get(path.size() - 1).isEnabled();
        }

        /** The item of each level of the path. */
        private List<MenuItem> resolve(List<String> path) {
            List<MenuItem> chosen = new java.util.ArrayList<>();
            List<MenuItem> level = menuBar.getItems();
            for (int i = 0; i < path.size(); i++) {
                MenuItem item = ItemNames.find(level, path.get(i), "menu item \"" + path.get(i) + "\" at " + path.subList(0, i + 1));
                chosen.add(item);
                level = item.getSubMenu().getItems();
            }
            return chosen;
        }
    }

    /**
     * Typing into a DateTimePicker's halves, as a browser reports it to the server: each half is a date or
     * time picker child of the field, set from the client when its text parses (ISO only), else given the
     * text as input and an {@code unparsable-change}; then the field gets the value, or an
     * {@code unparsable-change} that makes it validate the halves (bad input, incomplete). Builds on Flow's
     * internals: Browserless Test can't type into a DateTimePicker (#100).
     */
    record DateTimePickerAccessImpl(BrowserlessSession session, DateTimePicker picker) implements DateTimePickerAccess {
        @Override
        public void typeText(String date, String time) {
            session.act(picker, () -> {
                BrowserlessUIContext window = session.window();
                Optional<LocalDate> parsedDate = parse(date, LocalDate::parse);
                Optional<LocalTime> parsedTime = parse(time, LocalTime::parse);
                type(window, half(picker, DatePicker.class), date, parsedDate, LocalDate.class);
                type(window, half(picker, TimePicker.class), time, parsedTime, LocalTime.class);
                if (parsedDate.isPresent() && parsedTime.isPresent()) {
                    tester(window, picker, "setValue", LocalDateTime.class,
                            LocalDateTime.of(parsedDate.get(), parsedTime.get()));
                } else {
                    if (!picker.isEmpty()) {
                        tester(window, picker, "setValue", LocalDateTime.class, null);
                    }
                    if (!date.isBlank() || !time.isBlank()) {
                        com.vaadin.browserless.internal.BasicUtilsKt._fireDomEvent(picker, "unparsable-change");
                    }
                }
                window.roundTrip();
            });
        }

        /** ISO: the only date text {@link #typeText} parses. */
        @Override
        public String formatDate(LocalDate date) {
            return date.toString();
        }

        /**
         * Sets the value as a user entering it does: both halves from the client, then the field. Browserless
         * Test's DateTimePickerTester sets the field only, so a half kept text it couldn't parse.
         */
        static void setValueAsUser(BrowserlessUIContext window, DateTimePicker picker, LocalDateTime value) {
            setHalf(window, half(picker, DatePicker.class), value == null ? null : value.toLocalDate(), LocalDate.class);
            setHalf(window, half(picker, TimePicker.class), value == null ? null : value.toLocalTime(), LocalTime.class);
            tester(window, picker, "setValue", LocalDateTime.class, value);
        }

        private static void type(BrowserlessUIContext window, Component half, String text, Optional<?> parsed,
                Class<?> type) {
            if (parsed.isPresent() || text.isBlank()) {
                setHalf(window, half, parsed.orElse(null), type);
            } else {
                half.getElement().setProperty("_inputElementValue", text);
                com.vaadin.browserless.internal.BasicUtilsKt._fireDomEvent(half, "unparsable-change");
            }
        }

        /** A half's value from the client; its input text is no longer what it couldn't parse. */
        private static void setHalf(BrowserlessUIContext window, Component half, Object value, Class<?> type) {
            half.getElement().setProperty("_inputElementValue", "");
            if (value == null) {
                tester(window, half, "clear", null, null);
            } else {
                tester(window, half, "setValue", type, value);
            }
        }

        private static Component half(DateTimePicker picker, Class<? extends Component> type) {
            return picker.getChildren().filter(type::isInstance).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No " + type.getSimpleName() + " in the DateTimePicker"));
        }

        private static <T> Optional<T> parse(String text, java.util.function.Function<String, T> parser) {
            try {
                return text.isBlank() ? Optional.empty() : Optional.of(parser.apply(text.strip()));
            } catch (java.time.format.DateTimeParseException e) {
                return Optional.empty();
            }
        }

        /** Calls the tester's method: with no argument when {@code type} is null. */
        private static void tester(BrowserlessUIContext window, Component component, String method, Class<?> type,
                Object value) {
            Object tester = window.test(component);
            try {
                if (type == null) {
                    tester.getClass().getMethod(method).invoke(tester);
                } else {
                    tester.getClass().getMethod(method, type).invoke(tester, value);
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    record UploadAccessImpl(BrowserlessSession session, Upload upload) implements UploadAccess {
        @Override
        public void upload(String fileName, String mimeType, byte[] content) {
            session.act(upload, () -> {
                ((UploadTester<?>) session.window().test(upload)).upload(fileName, mimeType, content);
                session.window().roundTrip();
            });
        }
    }

    record MessageInputAccessImpl(BrowserlessSession session, MessageInput input) implements MessageInputAccess {
        @Override
        public void send(String text) {
            session.act(input, () -> {
                ((MessageInputTester<?>) session.window().test(input)).send(text);
                session.window().roundTrip();
            });
        }
    }

    record MessageListAccessImpl(MessageList list) implements MessageListAccess {
        @Override
        public List<UnifiedMessageList.Message> messages() {
            return list.getItems().stream()
                    .map(item -> new UnifiedMessageList.Message(item.getUserName(), item.getText())).toList();
        }
    }
}
