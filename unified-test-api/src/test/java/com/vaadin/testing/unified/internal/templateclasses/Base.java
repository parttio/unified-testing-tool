package com.vaadin.testing.unified.internal.templateclasses;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;

/** A class with a tag, and a subclass that only inherits it ({@link Sub}). */
@Tag("fixture-base")
public class Base extends Component {
    /** Inherits {@code fixture-base}: not another class of that tag. */
    public static class Sub extends Base {}
}
