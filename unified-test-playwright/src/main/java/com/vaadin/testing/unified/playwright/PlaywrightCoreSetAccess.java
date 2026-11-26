package com.vaadin.testing.unified.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.spi.ConfirmDialogAccess;
import com.vaadin.testing.unified.spi.DateTimePickerAccess;
import com.vaadin.testing.unified.spi.DetailsAccess;
import com.vaadin.testing.unified.spi.LoginAccess;
import com.vaadin.testing.unified.component.UnifiedMessageList;
import com.vaadin.testing.unified.internal.BrowserQueries;
import com.vaadin.testing.unified.spi.MenuBarAccess;
import com.vaadin.testing.unified.spi.MessageInputAccess;
import com.vaadin.testing.unified.spi.MessageListAccess;
import com.vaadin.testing.unified.spi.StaleElementException;
import com.vaadin.testing.unified.spi.TabSheetAccess;
import com.vaadin.testing.unified.spi.UploadAccess;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.vaadin.addons.dramafinder.AutoWait;
import org.vaadin.addons.dramafinder.element.ConfirmDialogElement;
import org.vaadin.addons.dramafinder.element.DateTimePickerElement;
import org.vaadin.addons.dramafinder.element.DetailsElement;
import org.vaadin.addons.dramafinder.element.MenuBarElement;
import org.vaadin.addons.dramafinder.element.MenuElement;
import org.vaadin.addons.dramafinder.element.MenuItemElement;
import org.vaadin.addons.dramafinder.element.MessageInputElement;
import org.vaadin.addons.dramafinder.element.TabElement;
import org.vaadin.addons.dramafinder.element.TabsElement;
import org.vaadin.addons.dramafinder.element.UploadElement;

/**
 * Access to LoginOverlay and LoginForm, ConfirmDialog, Tabs, Details, MenuBar, Upload, DateTimePicker, MessageInput and MessageList on Playwright, through
 * Dramafinder's elements.
 * Dramafinder's element actions wait for the server round-trip themselves; only actions on raw locators
 * wait here.
 */
final class PlaywrightCoreSetAccess {
    private PlaywrightCoreSetAccess() {}

    /** Fails at once, listing the names, when no item has this one: a locator matching nothing would wait. */
    static void requireName(List<String> names, String name, String what) {
        if (!names.contains(name)) {
            throw new IllegalArgumentException("No " + what + " \"" + name + "\"; there are " + names);
        }
    }

    /**
     * Tabs of a Tabs bar or a TabSheet by their Item Name: Dramafinder's tab label is the tab's own text, or
     * its {@code aria-label} when it shows no text.
     */
    static final class Tabs {
        private Tabs() {}

        /**
         * Dramafinder's read waits Playwright's timeout (10 s) for the bar: tabs gone from the page, e.g. after a tab
         * that sent the user to another page, are stale at once (#554), as a replaced selection field (#377).
         */
        static String selected(TabsElement bar) {
            if (bar.getLocator().count() == 0) {
                throw new StaleElementException("The tabs are gone (the page changed?)", null);
            }
            int index = bar.getSelectedIndex();
            return index < 0 ? null : bar.getTab(index).getLabel();
        }

        static TabElement tab(TabsElement bar, String label) {
            requireName(bar.getTabLabels(), label, "tab");
            return bar.getTab(label);
        }

        static void select(PlaywrightSession session, TabsElement bar, String label) {
            tab(bar, label).select();
            AutoWait.waitForVaadinIdle(session.page());
        }

        /** The tab's only {@code <a>}, which a user's click on the tab lands on (#531); the core clicks it. */
        static Optional<UnifiedElement> link(PlaywrightSession session, TabsElement bar, String label) {
            Locator links = tab(bar, label).getLocator().locator("a");
            return links.count() == 1
                    ? Optional.of(new PlaywrightElement(session, links, null, PlaywrightComponents.DEFAULT))
                    : Optional.empty();
        }
    }

    /** A bare Tabs bar through Dramafinder's TabsElement: TabSheetAccess without content. */
    record TabsAccess(PlaywrightSession session, Locator locator) implements TabSheetAccess {
        private TabsElement bar() {
            return new TabsElement(locator);
        }

        @Override
        public List<String> tabs() {
            return bar().getTabLabels();
        }

        @Override
        public String selectedTab() {
            return Tabs.selected(bar());
        }

        @Override
        public boolean isEnabled(String label) {
            return Tabs.tab(bar(), label).isEnabled();
        }

        @Override
        public void select(String label) {
            Tabs.select(session, bar(), label);
        }

        @Override
        public Optional<UnifiedElement> link(String label) {
            return Tabs.link(session, bar(), label);
        }

        @Override
        public UnifiedElement content() {
            throw new UnsupportedOperationException("A Tabs bar has no content; use UnifiedTabSheet for a TabSheet");
        }
    }

    /** Through Dramafinder's ConfirmDialogElement: each button by its slot, never by its text. */
    record ConfirmDialogAccessImpl(PlaywrightSession session, Locator locator) implements ConfirmDialogAccess {
        @Override
        public Map<Role, String> buttons() {
            return BrowserQueries.confirmDialogButtons(locator.evaluate(BrowserQueries.CONFIRM_DIALOG_BUTTONS));
        }

        @Override
        public void press(Role role) {
            ConfirmDialogElement dialog = new ConfirmDialogElement(locator);
            switch (role) {
                case CONFIRM -> dialog.getConfirmButton().click();
                case REJECT -> dialog.getRejectButton().click();
                case CANCEL -> dialog.getCancelButton().click();
            }
        }
    }

    /**
     * Through Dramafinder's login elements: the form's fields and buttons, inside the overlay for a LoginOverlay.
     * A form that posts is submitted with a plain click, without Dramafinder's wait after it: the post loads
     * another page, which the session's settle after the action waits for, as for any other click.
     */
    record LoginAccessImpl(PlaywrightSession session, Locator locator) implements LoginAccess {
        private org.vaadin.addons.dramafinder.element.AbstractLoginElement login() {
            return "vaadin-login-overlay".equals(locator.evaluate("e => e.localName"))
                    ? new org.vaadin.addons.dramafinder.element.LoginOverlayElement(locator)
                    : new org.vaadin.addons.dramafinder.element.LoginFormElement(locator);
        }

        @Override
        public boolean postsForms() {
            return true;
        }

        @Override
        public void login(String username, String password) {
            org.vaadin.addons.dramafinder.element.AbstractLoginElement login = login();
            if (!Boolean.TRUE.equals(locator.evaluate("e => !!e.action"))) {
                // the LoginEvent: Dramafinder types, presses submit and waits for the round trip it starts
                login.login(username, password);
                return;
            }
            login.getUsernameField().setValue(username);
            login.getPasswordField().setValue(password);
            login.getSubmitButton().getLocator().click();
        }

        @Override
        public void forgotPassword() {
            login().getForgotPasswordButton().getLocator().click();
        }
    }

    record DetailsAccessImpl(PlaywrightSession session, Locator locator) implements DetailsAccess {
        private DetailsElement details() {
            return new DetailsElement(locator);
        }

        @Override
        public String summaryText() {
            return details().getSummaryText().strip();
        }

        @Override
        public boolean isOpen() {
            return details().isOpen();
        }

        @Override
        public void toggle() {
            details().toggle(); // waits for the round-trip
        }

        /** Everything but the summary, open or closed. */
        @Override
        public UnifiedElement content() {
            return new PlaywrightElement(session, details().getContentLocator(), null, PlaywrightComponents.DEFAULT);
        }
    }

    /** Through Dramafinder's MenuBarElement: items by their Item Name, each sub-menu scoped to its level. */
    record MenuBarAccessImpl(PlaywrightSession session, Locator locator) implements MenuBarAccess {
        private MenuBarElement bar() {
            return new MenuBarElement(locator);
        }

        @Override
        public List<String> items(List<String> path) {
            if (path.isEmpty()) {
                return strings(topLevel().get("items"));
            }
            MenuElement menu = open(path);
            try {
                return menu.getItemLabels();
            } finally {
                bar().close();
            }
        }

        @Override
        public void click(List<String> path) {
            MenuElement menu = holding(path);
            (menu == null ? topLevel(path.get(0)) : item(menu, path)).click(); // Dramafinder's click waits for the round-trip
        }

        @Override
        public boolean isEnabled(List<String> path) {
            MenuElement menu = holding(path);
            if (menu == null) {
                return topLevel(path.get(0)).isEnabled();
            }
            try {
                return item(menu, path).isEnabled();
            } finally {
                bar().close();
            }
        }

        @Override
        public List<String> overflowItems() {
            return strings(topLevel().get("overflow"));
        }

        /**
         * The top-level items, all and in the overflow menu, and the overflow button's name, in one round trip:
         * Dramafinder's labels include the overflow button and can't tell the overflowed items apart
         * (parttio/dramafinder#334).
         */
        private java.util.Map<?, ?> topLevel() {
            return (java.util.Map<?, ?>) locator.evaluate(BrowserQueries.MENU_BAR_ITEMS);
        }

        /** Opens the menus of every item of the path; returns the last one opened. */
        private MenuElement open(List<String> path) {
            List<String> below = new java.util.ArrayList<>(path);
            below.add(null);
            return holding(below);
        }

        /**
         * Opens the menus down to the one that holds the path's last item, and returns it; null for a top-level item
         * on the menu bar itself. A top-level item moved into the overflow menu is in the overflow button's menu,
         * which a user opens first.
         */
        private MenuElement holding(List<String> path) {
            java.util.Map<?, ?> top = topLevel();
            MenuElement menu = null;
            int from = 1;
            if (strings(top.get("overflow")).contains(path.get(0))) {
                menu = bar().openSubMenu((String) top.get("overflowButton"));
                from = 0;
            } else if (path.size() > 1) {
                topLevel(path.get(0));
                menu = bar().openSubMenu(path.get(0));
            }
            for (int i = from; i < path.size() - 1; i++) {
                item(menu, path.subList(0, i + 1));
                menu = menu.openSubMenu(path.get(i));
            }
            return menu;
        }

        @SuppressWarnings("unchecked")
        private static List<String> strings(Object list) {
            return (List<String>) list;
        }

        private MenuItemElement topLevel(String name) {
            requireName(bar().getItemLabels(), name, "menu item");
            return bar().getMenuItemElement(name);
        }

        /** The last item of {@code path}, in {@code menu}. */
        private static MenuItemElement item(MenuElement menu, List<String> path) {
            String name = path.get(path.size() - 1);
            requireName(menu.getItemLabels(), name, "menu item at " + path);
            return menu.getMenuItemElement(name);
        }
    }

    /** Types into both halves, each committed with Enter, then leaves the field, as a user does. */
    record DateTimePickerAccessImpl(PlaywrightSession session, Locator locator) implements DateTimePickerAccess {
        @Override
        public void typeText(String date, String time) {
            new DateTimePickerElement(locator).typeText(date, time); // waits for the round-trip
        }

        @Override
        public String formatDate(LocalDate date) {
            return (String) locator.evaluate(BrowserQueries.FORMAT_DATE, BrowserQueries.formatDateArgument(date));
        }
    }

    /** Returns once the upload request has finished and the round trip after it is done. */
    record UploadAccessImpl(PlaywrightSession session, Locator locator) implements UploadAccess {
        @Override
        public void upload(String fileName, String mimeType, byte[] content) {
            new UploadElement(locator).uploadFiles(new FilePayload(fileName, mimeType, content));
            AutoWait.waitForVaadinIdle(session.page());
        }
    }

    /** Types into the input's text area and clicks its send button, through Dramafinder's MessageInputElement. */
    record MessageInputAccessImpl(PlaywrightSession session, Locator locator) implements MessageInputAccess {
        @Override
        public void send(String text) {
            new MessageInputElement(locator).typeAndSubmit(text);
            AutoWait.waitForVaadinIdle(session.page());
        }
    }

    /** The list's {@code items}, as the server set them. */
    record MessageListAccessImpl(PlaywrightSession session, Locator locator) implements MessageListAccess {
        @Override
        public List<UnifiedMessageList.Message> messages() {
            return BrowserQueries.messages(locator.evaluate(BrowserQueries.MESSAGES));
        }
    }
}
