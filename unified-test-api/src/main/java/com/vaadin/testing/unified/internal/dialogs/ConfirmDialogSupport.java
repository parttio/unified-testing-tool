package com.vaadin.testing.unified.internal.dialogs;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.internal.LazyElement;
import com.vaadin.testing.unified.internal.TestRun;
import com.vaadin.testing.unified.internal.Wrappers;
import com.vaadin.testing.unified.internal.failures.FailureParts;
import com.vaadin.testing.unified.internal.steps.StepTarget;
import com.vaadin.testing.unified.spi.ConfirmDialogAccess;
import com.vaadin.testing.unified.spi.ConfirmDialogAccess.Role;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A ConfirmDialog's buttons by their role ({@link ConfirmDialogAccess}), on every Testing Tool: pressing one, with
 * a failure that lists the buttons the dialog has when it lacks that role, and reading their texts.
 */
public final class ConfirmDialogSupport {
    private ConfirmDialogSupport() {}

    /**
     * Presses the button of {@code role} once the dialog is open (Auto-Wait). The User Step names the dialog as
     * the user read it, by its header, else its message, read before the dialog closes.
     */
    public static void press(UnifiedWrapper dialog, Role role) {
        LazyElement element = Wrappers.lazyElementOf(dialog);
        TestRun run = element.run();
        element.act(role.name().toLowerCase(java.util.Locale.ROOT), e -> {
            ConfirmDialogAccess access = e.unwrap(ConfirmDialogAccess.class);
            Map<Role, String> buttons = access.buttons();
            if (!buttons.containsKey(role)) {
                throw run.failure(FailureParts.of("to press the " + role.title() + " button of "
                        + StepTarget.of(element).renderWithScope(),
                        "no " + role.title() + " button; its buttons: " + list(buttons))
                        .suggestion("enable the button in the application (" + enabler(role)
                                + "), or press one the dialog has"), null);
            }
            run.steps().nameTarget(nameOf(e));
            access.press(role);
        });
    }

    /** The text of the button of {@code role} as the user sees it, or null when the dialog doesn't show it. */
    public static String buttonText(UnifiedWrapper dialog, Role role) {
        return dialog.element().unwrap(ConfirmDialogAccess.class).buttons().get(role);
    }

    /** {@code Confirm “Delete”, Cancel “Keep editing”}. */
    static String list(Map<Role, String> buttons) {
        return buttons.entrySet().stream().map(b -> b.getKey().title() + " “" + b.getValue() + "”")
                .collect(Collectors.joining(", "));
    }

    private static String enabler(Role role) {
        return switch (role) {
            case CONFIRM -> "setConfirmButton(..)";
            case REJECT -> "setRejectable(true) or setRejectButton(..)";
            case CANCEL -> "setCancelable(true) or setCancelButton(..)";
        };
    }

    /** The dialog's header, else its message: what the user reads it by. Null when it shows neither as text. */
    private static String nameOf(UnifiedElement e) {
        String header = e.getProperty("header");
        if (header != null && !header.isBlank()) {
            return header;
        }
        String message = e.getProperty("message");
        return message == null || message.isBlank() ? null : message;
    }
}
