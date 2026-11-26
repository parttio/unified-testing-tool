package org.vaadin.addons.unified.rewrite;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;

/**
 * An AppLayout's drawer (#669), which TestBench and Dramafinder open by setting its property and the Unified API
 * opens as a user does: {@code setDrawerOpened(true)} becomes {@code openDrawer()}, {@code false}
 * {@code closeDrawer()}, with a Migration Marker. A non-literal argument stays behind the Escape Hatch.
 */
final class AppLayouts {

    static final String APP_LAYOUT = "com.vaadin.testing.unified.component.UnifiedAppLayout";

    private AppLayouts() {
    }

    /** {@code setDrawerOpened(true)} or {@code (false)}: the Unified action, {@code openDrawer} or {@code closeDrawer}; else {@code null}. */
    static String drawerAction(J.MethodInvocation call) {
        if (!call.getSimpleName().equals("setDrawerOpened") || call.getArguments().size() != 1
                || !(call.getArguments().get(0) instanceof J.Literal literal) || !(literal.getValue() instanceof Boolean open)) {
            return null;
        }
        return open ? "openDrawer" : "closeDrawer";
    }

    /** The Migration Marker of a {@code setDrawerOpened(..)} that became {@code action}. */
    static String drawerNote(J.MethodInvocation call, String action) {
        return "was setDrawerOpened(" + call.getArguments().get(0).printTrimmed() + "), which set the property; "
                + action + "() clicks the DrawerToggle"
                + (action.equals("closeDrawer") ? ", or presses Escape on an opened overlay," : "") + " as a user does";
    }

    /**
     * A TestBench AppLayoutElement call a recipe converts: {@code setDrawerOpened} with a literal, and
     * {@code getDrawerToggle()} when the chain clicks it, {@code getDrawerToggle().click()}.
     */
    static boolean converts(J.MethodInvocation call, Cursor callCursor) {
        return drawerAction(call) != null
                || call.getSimpleName().equals("getDrawerToggle") && call.getArguments().stream().allMatch(J.Empty.class::isInstance)
                        && callCursor.getParentTreeCursor().getValue() instanceof J.MethodInvocation next
                        && next.getSelect() == call && next.getSimpleName().equals("click");
    }

    /** {@code l.getDrawerToggle().click()}: the {@code getDrawerToggle()} call, else {@code null}. */
    static J.MethodInvocation toggleClicked(J.MethodInvocation click) {
        return click.getSimpleName().equals("click") && click.getSelect() instanceof J.MethodInvocation toggle
                && toggle.getSimpleName().equals("getDrawerToggle") ? toggle : null;
    }
}
