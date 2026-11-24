package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.internal.dialogs.ConfirmDialogSupport;
import com.vaadin.testing.unified.spi.ConfirmDialogAccess.Role;

/**
 * A {@link ConfirmDialog}: a question with Confirm, and optionally Reject and Cancel, buttons. Its actions press a
 * button by its role, whatever text the application or its i18n gave it:
 *
 * <pre>{@code
 * UnifiedConfirmDialog dialog = page.find(UnifiedConfirmDialog.class).withHeaderTitle("Delete order?");
 * assertThat(dialog).hasText("The order can't be restored.");
 * dialog.confirm();
 * assertThat(dialog).isClosed();
 * }</pre>
 *
 * {@code withHeaderTitle(..)}, {@code isOpen()} and {@code close()} as every {@link UnifiedOverlayDialog}. Pressing
 * Escape ({@code close()}) fires the dialog's cancel event, as a user's Escape does in a browser.
 */
@UnifiedComponent(ConfirmDialog.class)
public interface UnifiedConfirmDialog extends UnifiedOverlayDialog<UnifiedConfirmDialog> {

    /** Dialogs whose message ({@link #getText()}) contains this. A custom filter built from {@code where(..)}. */
    default UnifiedConfirmDialog withTextContaining(String part) {
        return where(e -> {
            String message = e.getProperty("message");
            return message != null && message.contains(part);
        });
    }

    // actions (Auto-Wait): press the button of the role, and wait for the server round-trip
    /** Presses the Confirm button. */
    default void confirm() { ConfirmDialogSupport.press(this, Role.CONFIRM); }
    /** Presses the Reject button; fails, listing the dialog's buttons, when the dialog has none. */
    default void reject() { ConfirmDialogSupport.press(this, Role.REJECT); }
    /** Presses the Cancel button; fails, listing the dialog's buttons, when the dialog has none. */
    default void cancel() { ConfirmDialogSupport.press(this, Role.CANCEL); }

    // getters (immediate)
    /** The message, as the application set it with {@code setText(String)}; null if none. */
    default String getText() {
        String message = element().getProperty("message");
        return message == null || message.isEmpty() ? null : message;
    }
    /** The Confirm button's text as the user sees it. */
    default String getConfirmText() { return ConfirmDialogSupport.buttonText(this, Role.CONFIRM); }
    /** The Reject button's text as the user sees it, or null when the dialog shows no Reject button. */
    default String getRejectText() { return ConfirmDialogSupport.buttonText(this, Role.REJECT); }
    /** The Cancel button's text as the user sees it, or null when the dialog shows no Cancel button. */
    default String getCancelText() { return ConfirmDialogSupport.buttonText(this, Role.CANCEL); }
}
