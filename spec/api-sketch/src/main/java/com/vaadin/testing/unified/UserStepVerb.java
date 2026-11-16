package com.vaadin.testing.unified;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How an add-on Wrapper's action reads as a User Step (spec chapter 12): {@code @UserStepVerb("rate")} on
 * {@code rate(int stars)} reads {@code rate rating “Quality” with “4”}; {@code {value}} places the arguments,
 * {@code @UserStepVerb("give {value} stars to")} reads {@code give “4” stars to rating “Quality”}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserStepVerb {
    String value();
}
