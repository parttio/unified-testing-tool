package demo.perf;

import java.time.LocalDate;

/** The values every Performance Pair enters, so all four tests of a pair do exactly the same. */
final class PerfData {
    private PerfData() {}

    static final String NAME = "Alice";
    static final String EMAIL = "alice@example.com";
    static final String SAVED = "Saved Alice";

    /** The value typed into the long form's text field {@code label}. */
    static String text(String label) {
        return label + " value";
    }

    static final int INTEGER = 42;
    static final LocalDate DATE = LocalDate.of(2026, 5, 17);
    static final String LONG_FORM_SAVED = "Saved 30 of 30 fields";

    static final String GRID_NAME = "Person 250";
    static final String GRID_EMAIL = "person250@example.com";

    static final String ITEM = "Item 0742";
    static final String PICKED = "Picked: Item 0742";
}
