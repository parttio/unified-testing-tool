package com.vaadin.testing.unified.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Text matching: the text is stripped and its whitespace runs are one space, as before #443's fast path. */
class FilterTest {

    @Test
    void normalizesAsTheRegexDid() {
        for (String text : List.of("", " ", "Person 250", "  Person 250  ", "Person  250", "Person\t250",
                "Person \n 250", "a\u000Bb", "a\fb", "a\rb", "a   b", "a b", " \t a  b \r\n")) {
            assertEquals(text.strip().replaceAll("\\s+", " "), Filter.normalize(text), "\"" + text + "\"");
        }
    }

    @Test
    void matchesExactlyOrContainingIgnoringCase() {
        Filter exact = new Filter(Filter.Kind.TEXT, null, "Person 250", true);
        assertTrue(exact.matches(" Person\n  250 "));
        assertFalse(exact.matches("Person 2500"));
        assertFalse(exact.matches(null));

        Filter containing = new Filter(Filter.Kind.TEXT, null, "SON 25", false);
        assertTrue(containing.matches("Person  250"));
        assertFalse(containing.matches("Person 260"));
    }
}
