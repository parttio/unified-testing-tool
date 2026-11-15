package proto.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** PROTOTYPE stand-ins for the demo's views and the test annotation, so every style compiles alone. */
public final class Common {
    private Common() {}

    public static final class PeopleView {}
    public static final class CheckoutView {}
    public static final class UsersView {}

    @Retention(RetentionPolicy.RUNTIME)
    public @interface UnifiedSpringBootTest {}
}
