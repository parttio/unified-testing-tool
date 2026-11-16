package com.vaadin.testing.unified;

import com.vaadin.flow.component.Component;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the Flow component a wrapper interface wraps (ADR 0003). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UnifiedComponent {
    /** The Flow component class. Browserless finds components by it. */
    Class<? extends Component> value();

    /** Web component tag for browser Testing Tools. Empty: read from the component's {@code @Tag}. */
    String tag() default "";

    /** What tells this component apart from others with the same tag, e.g. {@code ".metric-card"}. */
    String selector() default "";

    /**
     * The component kind in the User Step reports, e.g. {@code "star rating"}. Empty: the Wrapper's name in
     * words, without the {@code Unified} prefix. For the verbs, see {@link UserStepVerb}.
     */
    String kind() default "";

    /** Per-Testing-Tool implementations, for what the Unified Element can't express. */
    ToolImplementation[] overrides() default {};
}
