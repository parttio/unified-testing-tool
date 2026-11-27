package demo.addon.testing;

import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import demo.addon.Rating;
import java.time.Duration;

/**
 * The add-on's wrapper, shipped in its own jar (ADR 0003): only the Unified Element, no per-tool code.
 * Tag from {@link Rating}'s {@code @Tag}.
 */
// --8<-- [start:wrapper]
@UnifiedComponent(Rating.class)
public interface UnifiedRating extends UnifiedWrapper, CommonFilters<UnifiedRating>, LabelFilter<UnifiedRating> {

    /** Only to chain after it: {@code rating.withTimeout(..).rate(4)}. The Unified API implements it. */
    @Override
    UnifiedRating withTimeout(Duration timeout);

    /** A custom filter built from {@code where(..)}. */
    default UnifiedRating withValue(int stars) {
        return where(e -> Integer.valueOf(stars).equals(e.getPropertyInteger("value")));
    }

    /** Clicks the n-th star, as a user would. */
    default void rate(int stars) {
        element().findChildren("span").get(stars - 1).click();
    }

    default int getValue() {
        Integer value = element().getPropertyInteger("value");
        return value == null ? 0 : value;
    }
}
// --8<-- [end:wrapper]
