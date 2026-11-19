package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class VaadinVersionCheckTest {

    @Test
    void olderVaadinIsNamedWithTheMinimum() {
        assertEquals(Optional.of("The Unified API needs Vaadin 25.3 or newer, and this test runs on Vaadin 25.2"
                + " (Flow 25.2.4). Move the application to Vaadin 25.3: the Browserless Test and TestBench versions"
                + " come from the project's Vaadin BOM."), VaadinVersionCheck.problem("25.2.4"));
        assertTrue(VaadinVersionCheck.problem("24.9.0").isPresent());
    }

    @Test
    void theMinimumAndNewerPass() {
        assertEquals(Optional.empty(), VaadinVersionCheck.problem("25.3.0"));
        assertEquals(Optional.empty(), VaadinVersionCheck.problem("25.4.0.beta1"));
        assertEquals(Optional.empty(), VaadinVersionCheck.problem("26.0.0"));
    }

    @Test
    void unknownVersionPasses() {
        assertEquals(Optional.empty(), VaadinVersionCheck.problem(null));
        assertEquals(Optional.empty(), VaadinVersionCheck.problem("snapshot"));
    }

    @Test
    void thisBuildsVaadinPasses() {
        VaadinVersionCheck.check();
    }
}
