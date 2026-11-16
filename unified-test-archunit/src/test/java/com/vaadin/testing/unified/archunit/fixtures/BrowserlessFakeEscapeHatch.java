package com.vaadin.testing.unified.archunit.fixtures;

import com.vaadin.testing.unified.UnifiedPage;

/** Stands for BrowserlessEscapeHatch, which this module doesn't depend on: the rule knows it by its name. */
public final class BrowserlessFakeEscapeHatch {

    private BrowserlessFakeEscapeHatch() {}

    public static BrowserlessFakeEscapeHatch of(UnifiedPage page) {
        return new BrowserlessFakeEscapeHatch();
    }
}
