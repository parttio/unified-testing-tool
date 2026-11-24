package com.vaadin.testing.unified.component;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import java.time.Duration;

/**
 * A {@link Dialog}: {@code withHeaderTitle(..)}, {@code isOpen()} and {@code close()} as every
 * {@link UnifiedOverlayDialog}. A Scope: {@code dialog.find(UnifiedButton.class)} searches inside the dialog.
 */
@UnifiedComponent(Dialog.class)
public interface UnifiedDialog extends UnifiedOverlayDialog<UnifiedDialog>, Scope {

    /** Returns this Wrapper with another timeout for Auto-Wait and assertions. */
    @Override
    UnifiedDialog withTimeout(Duration timeout);
}
