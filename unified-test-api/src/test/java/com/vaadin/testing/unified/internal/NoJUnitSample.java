package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;

/** A Unified UI Test as another Test Runner would see it: loaded by {@link NoJUnitClasspathTest} without JUnit. */
@UnifiedUITest
class NoJUnitSample {

    @RequiresBrowser
    void checksLayout(UnifiedPage page) {}

    void anyTool(UnifiedPage page) {}
}
