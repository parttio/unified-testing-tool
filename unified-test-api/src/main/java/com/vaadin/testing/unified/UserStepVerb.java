package com.vaadin.testing.unified;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How a Wrapper action reads as a User Step in the reports, for an add-on Wrapper whose method name doesn't
 * say it. Without it the verb comes from the core verb table ({@code click}, {@code setValue} → "fill", …) or,
 * for other methods, from the method name in words ({@code setRating(4)} → {@code set rating rating with “4”}).
 * <p>
 * The value is the verb, followed by the component kind, name and arguments:
 * {@code @UserStepVerb("rate")} on {@code rate(int stars)} reads {@code rate rating “Quality” with “4”}. To place
 * the arguments yourself, write {@code {value}} where they go:
 * {@code @UserStepVerb("give {value} stars to")} reads {@code give “4” stars to rating “Quality”}.
 * <p>
 * The component kind comes from {@link UnifiedComponent#kind()}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserStepVerb {
    String value();
}
