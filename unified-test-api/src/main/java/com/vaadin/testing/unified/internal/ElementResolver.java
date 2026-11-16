package com.vaadin.testing.unified.internal;

import com.vaadin.testing.unified.UnifiedElement;
import java.util.List;

/** Finds adapter elements right now: a query, or a part of a component such as a grid cell. */
public interface ElementResolver {
    List<UnifiedElement> resolve();

    String describe();

    /** What it finds in user words, for the User Step reports; null to use {@link #describe()}. */
    default com.vaadin.testing.unified.internal.steps.StepTarget userTarget() {
        return null;
    }
}
