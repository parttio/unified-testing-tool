package org.vaadin.addons.unified.rewrite;

import java.util.Map;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;

/**
 * A confirm dialog's buttons, which TestBench and Dramafinder reach by their role and the Unified API presses by
 * it: {@code getConfirmButton().click()} becomes {@code confirm()}, and so on. A button used otherwise (its text,
 * a check on it) stays behind the Escape Hatch.
 */
final class ConfirmDialogs {

    static final String CONFIRM_DIALOG = "com.vaadin.testing.unified.component.UnifiedConfirmDialog";
    static final String TESTBENCH_ELEMENT = "com.vaadin.flow.component.confirmdialog.testbench.ConfirmDialogElement";

    /** The element's button getter, and the Wrapper action that presses that button. */
    static final Map<String, String> PRESSES = Map.of("getConfirmButton", "confirm", "getRejectButton", "reject",
            "getCancelButton", "cancel");

    private ConfirmDialogs() {
    }

    /** {@code getConfirmButton()} converts when the chain clicks it: {@code getConfirmButton().click()}. */
    static boolean converts(J.MethodInvocation call, Cursor callCursor) {
        return PRESSES.containsKey(call.getSimpleName()) && call.getArguments().stream().allMatch(J.Empty.class::isInstance)
                && callCursor.getParentTreeCursor().getValue() instanceof J.MethodInvocation next
                && next.getSelect() == call && next.getSimpleName().equals("click");
    }

    /** {@code d.getConfirmButton().click()} → {@code confirm}, else {@code null}. */
    static String press(J.MethodInvocation click) {
        return click.getSimpleName().equals("click") && click.getSelect() instanceof J.MethodInvocation button
                ? PRESSES.get(button.getSimpleName()) : null;
    }
}
