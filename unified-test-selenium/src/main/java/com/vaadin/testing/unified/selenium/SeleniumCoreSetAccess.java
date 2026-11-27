package com.vaadin.testing.unified.selenium;

import com.vaadin.flow.component.datetimepicker.testbench.DateTimePickerElement;
import com.vaadin.flow.component.details.testbench.DetailsElement;
import com.vaadin.flow.component.menubar.testbench.MenuBarButtonElement;
import com.vaadin.flow.component.menubar.testbench.MenuBarElement;
import com.vaadin.flow.component.menubar.testbench.MenuBarItemElement;
import com.vaadin.flow.component.menubar.testbench.MenuBarSubMenuElement;
import com.vaadin.flow.component.messages.testbench.MessageInputElement;
import com.vaadin.flow.component.timepicker.testbench.TimePickerElement;
import com.vaadin.flow.component.upload.testbench.UploadElement;
import com.vaadin.testbench.TestBenchElement;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.spi.ConfirmDialogAccess;
import com.vaadin.testing.unified.spi.DateTimePickerAccess;
import com.vaadin.testing.unified.spi.DetailsAccess;
import com.vaadin.testing.unified.spi.LoginAccess;
import com.vaadin.testing.unified.spi.MenuBarAccess;
import com.vaadin.testing.unified.spi.MessageInputAccess;
import com.vaadin.testing.unified.spi.MessageListAccess;
import com.vaadin.testing.unified.spi.UploadAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Access to LoginOverlay and LoginForm, ConfirmDialog, Details, MenuBar, Upload, DateTimePicker, MessageInput and MessageList on Selenium, through TestBench's element classes; a bare Tabs bar
 * uses {@link SeleniumTabSheetAccess}.
 */
final class SeleniumCoreSetAccess {
    private SeleniumCoreSetAccess() {}

    /** Each button by its slot in the host, never by its text: the application's own or the web component's. */
    record ConfirmDialogAccessImpl(SeleniumSession session, WebElement element) implements ConfirmDialogAccess {
        @Override
        public Map<Role, String> buttons() {
            return BrowserQueries.confirmDialogButtons(
                    session.js("return (" + BrowserQueries.CONFIRM_DIALOG_BUTTONS + ")(arguments[0]);", element));
        }

        @Override
        public void press(Role role) {
            SeleniumSession.stale(() -> element.findElement(By.cssSelector(BrowserQueries.confirmDialogButton(role))))
                    .click();
            session.waitForVaadin();
        }
    }

    /**
     * Through TestBench's LoginOverlayElement and LoginFormElement: the form's fields and buttons, inside the
     * overlay for a LoginOverlay. A form post loads another page, which waiting for Vaadin waits for.
     */
    record LoginAccessImpl(SeleniumSession session, WebElement element) implements LoginAccess {
        @Override
        public boolean postsForms() {
            return true;
        }

        @Override
        public void login(String username, String password) {
            TestBenchElement host = session.testBench(element);
            if (isOverlay(host)) {
                var login = host.wrap(com.vaadin.flow.component.login.testbench.LoginOverlayElement.class);
                login.getUsernameField().setValue(username);
                login.getPasswordField().setValue(password);
                login.getSubmitButton().click();
            } else {
                var login = host.wrap(com.vaadin.flow.component.login.testbench.LoginFormElement.class);
                login.getUsernameField().setValue(username);
                login.getPasswordField().setValue(password);
                login.getSubmitButton().click();
            }
            session.waitForVaadin();
        }

        @Override
        public void forgotPassword() {
            TestBenchElement host = session.testBench(element);
            (isOverlay(host) ? host.wrap(com.vaadin.flow.component.login.testbench.LoginOverlayElement.class)
                    .getForgotPasswordButton()
                    : host.wrap(com.vaadin.flow.component.login.testbench.LoginFormElement.class)
                            .getForgotPasswordButton()).click();
            session.waitForVaadin();
        }

        private static boolean isOverlay(TestBenchElement host) {
            return "vaadin-login-overlay".equalsIgnoreCase(host.getTagName());
        }
    }

    /** Through TestBench's DetailsElement. */
    record DetailsAccessImpl(SeleniumSession session, WebElement element) implements DetailsAccess {
        private DetailsElement details() {
            return session.testBench(element).wrap(DetailsElement.class);
        }

        @Override
        public String summaryText() {
            return details().getSummaryText().replaceAll("\\s+", " ").strip();
        }

        @Override
        public boolean isOpen() {
            return details().isOpened();
        }

        @Override
        public void toggle() {
            details().toggle();
            session.waitForVaadin();
        }

        @Override
        public UnifiedElement content() {
            return new SeleniumElement(session, details().getContent(), SeleniumComponents.DEFAULT);
        }
    }

    /**
     * Through TestBench's MenuBarElement: a top-level button opens its sub-menu when clicked, a nested item
     * when hovered (TestBench's {@code openSubMenu()}). Items are found by their Item Name.
     */
    record MenuBarAccessImpl(SeleniumSession session, WebElement element) implements MenuBarAccess {
        private MenuBarElement menuBar() {
            return session.testBench(element).wrap(MenuBarElement.class);
        }

        @Override
        public List<String> items(List<String> path) {
            if (path.isEmpty()) {
                return strings(topLevel().get("items"));
            }
            MenuBarSubMenuElement subMenu = open(path);
            try {
                return names(subMenu.getMenuItems());
            } finally {
                close();
            }
        }

        @Override
        public void click(List<String> path, boolean throughOverflow) {
            MenuBarSubMenuElement subMenu = holding(path, throughOverflow, "clickItem");
            if (subMenu == null) {
                button(path.get(0)).click();
            } else {
                item(subMenu, path, path.size() - 1).click();
            }
            session.waitForVaadin();
        }

        @Override
        public boolean isEnabled(List<String> path, boolean throughOverflow) {
            MenuBarSubMenuElement subMenu = holding(path, throughOverflow, "isItemEnabled");
            if (subMenu == null) {
                return enabled(button(path.get(0)));
            }
            try {
                return enabled(item(subMenu, path, path.size() - 1));
            } finally {
                close();
            }
        }

        @Override
        public List<String> overflowItems() {
            return strings(topLevel().get("overflow"));
        }

        /**
         * The top-level items, all and in the overflow menu, and the overflow button's name, in one round trip:
         * TestBench's {@code getButtons()} leaves the overflowed ones out.
         */
        private java.util.Map<?, ?> topLevel() {
            return (java.util.Map<?, ?>) session.js("return (" + BrowserQueries.MENU_BAR_ITEMS + ")(arguments[0]);",
                    element);
        }

        @SuppressWarnings("unchecked")
        private static List<String> strings(Object list) {
            return (List<String>) list;
        }

        /** A disabled item keeps focus with aria-disabled, which TestBench's isEnabled() misses: #162. */
        private static boolean enabled(TestBenchElement item) {
            return !item.hasAttribute("disabled") && !"true".equals(item.getDomAttribute("aria-disabled"));
        }

        private void close() {
            session.pressKey("Escape");
            if (!menuBar().$("vaadin-menu-bar-submenu").withAttribute("opened").all().isEmpty()) {
                session.pressKey("Escape");
            }
        }

        /** The Item Names of these items, in one round trip. */
        @SuppressWarnings("unchecked")
        private List<String> names(List<? extends WebElement> items) {
            return (List<String>) session.js("return arguments[0].map(" + BrowserQueries.ITEM_NAME + ");", items); // #161
        }

        /** Opens the sub-menus along the path; returns the last one. */
        private MenuBarSubMenuElement open(List<String> path) {
            List<String> below = new java.util.ArrayList<>(path);
            below.add(null);
            return holding(below, true, null); // reading a sub-menu's items opens the menus along its path
        }

        /**
         * Opens the sub-menus down to the one that holds the path's last item, and returns it; null for a top-level
         * item on the menu bar itself. A top-level item folded into the overflow menu is in the overflow button's
         * sub-menu, which a user opens first: only {@code throughOverflow}, else refused for {@code call}.
         */
        private MenuBarSubMenuElement holding(List<String> path, boolean throughOverflow, String call) {
            MenuBarSubMenuElement subMenu = null;
            int from = 1;
            if (strings(topLevel().get("overflow")).contains(path.get(0))) {
                if (!throughOverflow) {
                    throw MenuBarAccess.folded(path, call);
                }
                subMenu = menuBar().getOverflowButton().openSubMenu();
                from = 0;
            } else if (path.size() > 1) {
                subMenu = button(path.get(0)).openSubMenu();
            }
            for (int i = from; i < path.size() - 1; i++) {
                subMenu = item(subMenu, path, i).openSubMenu();
            }
            return subMenu;
        }

        private MenuBarButtonElement button(String name) {
            return named(menuBar().getButtons(), List.of(name), 0);
        }

        private MenuBarItemElement item(MenuBarSubMenuElement subMenu, List<String> path, int level) {
            return named(subMenu.getMenuItems(), path, level);
        }

        private <T extends WebElement> T named(List<T> items, List<String> path, int level) {
            List<String> names = names(items);
            int index = names.indexOf(path.get(level));
            if (index < 0) {
                throw new IllegalArgumentException("No menu item \"" + path.get(level) + "\" at "
                        + path.subList(0, level + 1) + "; there are " + names);
            }
            return items.get(index);
        }
    }

    /**
     * Types into the date and time halves through TestBench's DateTimePickerElement, each committed with
     * Enter, then leaves the field as a user does: the field checks its halves (bad input, incomplete) when
     * focus leaves it.
     */
    record DateTimePickerAccessImpl(SeleniumSession session, WebElement element) implements DateTimePickerAccess {
        @Override
        public void typeText(String date, String time) {
            DateTimePickerElement picker = session.testBench(element).wrap(DateTimePickerElement.class);
            picker.getDatePicker().setInputValue(date);
            TimePickerElement timePicker = picker.getTimePicker();
            timePicker.selectByText(time);
            timePicker.getTimePickerInputElement().callFunction("blur"); // no TestBenchElement.blur(): #164
            session.waitForVaadin();
        }

        @Override
        public String formatDate(LocalDate date) {
            return (String) session.js("return (" + BrowserQueries.FORMAT_DATE + ")(arguments[0], arguments[1]);",
                    element, BrowserQueries.formatDateArgument(date));
        }
    }

    /**
     * Through TestBench's UploadElement, which feeds the file input and waits until the upload finished.
     * WebDriver needs a file on disk: the content goes to a temporary file with the given name.
     */
    record UploadAccessImpl(SeleniumSession session, WebElement element) implements UploadAccess {
        @Override
        public void upload(String fileName, String mimeType, byte[] content) {
            Path file;
            try {
                file = Files.createTempDirectory("unified-upload").resolve(fileName);
                Files.write(file, content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            try {
                session.testBench(element).wrap(UploadElement.class)
                        .upload(file.toFile(), (int) Math.max(1, session.timeout().toSeconds()));
                session.waitForVaadin();
            } finally {
                try {
                    Files.deleteIfExists(file);
                    Files.deleteIfExists(file.getParent());
                } catch (IOException ignored) {
                    // a temporary file
                }
            }
        }
    }

    /** Through TestBench's MessageInputElement, which types the text and clicks the send button. */
    record MessageInputAccessImpl(SeleniumSession session, WebElement element) implements MessageInputAccess {
        @Override
        public void send(String text) {
            session.testBench(element).wrap(MessageInputElement.class).submit(text);
            session.waitForVaadin();
        }
    }

    /** The list's {@code items}, as the server set them. */
    record MessageListAccessImpl(SeleniumSession session, WebElement element) implements MessageListAccess {
        @Override
        public List<UnifiedMessageList.Message> messages() {
            return BrowserQueries.messages(session.js("return (" + BrowserQueries.MESSAGES + ")(arguments[0]);", element));
        }
    }
}
