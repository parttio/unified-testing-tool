package demo;

import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.UserStepVerb;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;
import demo.addon.Rating;

/**
 * The add-on's {@link Rating} again, with its own words in the User Step reports: the kind "star rating" and the
 * verb of {@link #rate(int)}.
 */
// --8<-- [start:wrapper]
@UnifiedComponent(value = Rating.class, kind = "star rating")
public interface UnifiedStarRating extends UnifiedWrapper,
        CommonFilters<UnifiedStarRating>, LabelFilter<UnifiedStarRating> {

    /** A custom filter: reads as {@code stars “3”} in the reports. */
    default UnifiedStarRating withStars(int stars) {
        return where(e -> Integer.valueOf(stars).equals(e.getPropertyInteger("value")));
    }

    /** Reads {@code give “3” stars to star rating “Food”}. */
    @UserStepVerb("give {value} stars to")
    default void rate(int stars) {
        element().findChildren("span").get(stars - 1).click();
    }
}
// --8<-- [end:wrapper]
