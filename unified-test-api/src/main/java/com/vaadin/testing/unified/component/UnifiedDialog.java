package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;

/**
 * A {@link Dialog}: {@code withHeaderTitle(..)}, {@code isOpen()} and {@code close()} as every
 * {@link UnifiedOverlayDialog}. A Scope: {@code dialog.find(UnifiedButton.class)} searches inside the dialog.
 */
@UnifiedComponent(Dialog.class)
public interface UnifiedDialog extends UnifiedOverlayDialog<UnifiedDialog>, Scope {
}
