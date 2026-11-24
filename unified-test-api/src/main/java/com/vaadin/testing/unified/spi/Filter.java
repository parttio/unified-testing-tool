package com.vaadin.testing.unified.spi;

import java.util.regex.Pattern;

/**
 * One filter of a query, as the adapter receives it. {@code exact} is false for the {@code ...Containing}
 * variants, which match case-insensitively (as in Dramafinder). {@code name} is used by {@link Kind#ATTRIBUTE}; an
 * {@code ATTRIBUTE} filter whose {@code value} is null matches an element that has the attribute, whatever its value.
 * {@link Kind#WITHOUT_ATTRIBUTE} matches an element that doesn't have the attribute {@code name}: the core adds it to
 * tell apart component classes that share a tag (an {@code Anchor} is an {@code <a>} without {@code router-link}).
 */
public record Filter(Kind kind, String name, String value, boolean exact) {

    public enum Kind { LABEL, ACCESSIBLE_NAME, TEXT, PLACEHOLDER, TEST_ID, ID, CLASS_NAME, THEME, ATTRIBUTE, WITHOUT_ATTRIBUTE }

    public boolean matches(String actual) {
        if (actual == null) {
            return false;
        }
        String normalized = normalize(actual);
        return exact ? normalized.equals(value)
                : normalized.toLowerCase(java.util.Locale.ROOT).contains(value.toLowerCase(java.util.Locale.ROOT));
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Stripped, each run of whitespace one space. A grid row lookup matches every cell of a column, so the
     * pattern is compiled once, and a text with only single spaces is not copied.
     */
    static String normalize(String text) {
        String stripped = text.strip();
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == ' ' ? i + 1 < stripped.length() && isRegexWhitespace(stripped.charAt(i + 1))
                    : isRegexWhitespace(c)) {
                return WHITESPACE.matcher(stripped).replaceAll(" ");
            }
        }
        return stripped;
    }

    /** Whitespace as {@link Pattern}'s {@code \s} matches it: {@code [ \t\n\x0B\f\r]}. */
    private static boolean isRegexWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }

    @Override
    public String toString() {
        String method = switch (kind) {
            case LABEL -> "withLabel";
            case ACCESSIBLE_NAME -> "withAccessibleName";
            case TEXT -> "withText";
            case PLACEHOLDER -> "withPlaceholder";
            case TEST_ID -> "withTestId";
            case ID -> "withId";
            case CLASS_NAME -> "withClassName";
            case THEME -> "withTheme";
            case ATTRIBUTE -> "withAttribute";
            case WITHOUT_ATTRIBUTE -> "withoutAttribute";
        };
        if (kind == Kind.WITHOUT_ATTRIBUTE) {
            return method + "(\"" + name + "\")";
        }
        String arg = kind == Kind.ATTRIBUTE ? "\"" + name + "\", \"" + value + "\"" : "\"" + value + "\"";
        return method + (exact ? "" : "Containing") + "(" + arg + ")";
    }
}
