package com.vaadin.testing.unified.spi;

import java.util.List;

/** Adapter access to a SideNav, reached by the core with {@code element.unwrap(SideNavAccess.class)}. */
public interface SideNavAccess {

    /** The label shown above the items ({@code new SideNav("Main")}), or null when it has none. */
    String label();

    /** The labels of its top-level items, in order ({@link SideNavItemAccess#label()}). */
    List<String> items();
}
