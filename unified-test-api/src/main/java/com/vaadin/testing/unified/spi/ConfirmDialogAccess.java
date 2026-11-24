package com.vaadin.testing.unified.spi;

import java.util.Map;

/**
 * Adapter access to a ConfirmDialog, reached by the core with {@code element.unwrap(ConfirmDialogAccess.class)}.
 * Its buttons are known by their role, never by their text: the application, its i18n or the web component's
 * defaults choose the text.
 */
public interface ConfirmDialogAccess {

    /** A button of a ConfirmDialog, by what pressing it means. */
    enum Role {
        CONFIRM, REJECT, CANCEL;

        /** {@code "Reject"}: how a failure names the role. */
        public String title() {
            String name = name();
            return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * The buttons the dialog shows, in the order of {@link Role}, each with the text the user sees on it: the
     * application's own button's text, else the text it set for the role, else the web component's default
     * ({@code "Confirm"}, {@code "Reject"}, {@code "Cancel"}). A hidden button (reject or cancel not enabled) is
     * not listed.
     */
    Map<Role, String> buttons();

    /**
     * Presses the button of this role, as a user's click does: the dialog fires its event of that role and
     * closes. The core checks that the dialog shows it first.
     */
    void press(Role role);
}
