package com.vaadin.testing.unified.internal.steps;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One User Step in words: {@code <verb> <component kind> “<name>” [with “<value>”] [in <scope>]}.
 *
 * @param verb the verb from the fixed table ("fill", "see", "see no", ...), for the JSON report
 * @param template how the verb reads when it is not simply followed by the target, with {@code {value}} where
 *        the values go: {@code choose {value} in}; null for the plain verb
 * @param target what the step acts on or checks; null for page-level steps ("open People")
 * @param values the values typed, chosen or expected, already masked and cut
 * @param valueLabel the words before the values when the template doesn't place them: "with", "with value"
 * @param trailing words after the target, e.g. "checked", or null
 * @param plural whether the target's kind reads in the plural ("see 3 buttons")
 * @param nameOnly whether a named target reads by its name alone, without its kind ({@code confirm “Delete order?”})
 */
public record Phrase(String verb, String template, StepTarget target, List<String> values, String valueLabel,
        String trailing, boolean plural, boolean nameOnly) {

    /** Values longer than this are cut with "…". */
    public static final int MAX_VALUE_LENGTH = 80;

    /** How a hidden value reads. */
    public static final String MASK = "••••";

    public Phrase {
        values = values == null ? List.of() : values.stream().map(Phrase::cut).toList();
        if (target != null && target.secret()) {
            values = values.stream().map(v -> MASK).toList();
        }
        valueLabel = valueLabel == null ? "with" : valueLabel;
    }

    public static Phrase of(String verb, StepTarget target, List<String> values) {
        return new Phrase(verb, null, target, values, null, null, false, false);
    }

    public static Phrase of(String verb, StepTarget target) {
        return of(verb, target, List.of());
    }

    /** {@code template} with {@code {value}} where the values go: {@code press {value} on}. */
    public static Phrase template(String verb, String template, StepTarget target, List<String> values) {
        return new Phrase(verb, template, target, values, null, null, false, false);
    }

    public Phrase withTrailing(String words) {
        return new Phrase(verb, template, target, values, valueLabel, words, plural, nameOnly);
    }

    public Phrase withValueLabel(String label) {
        return new Phrase(verb, template, target, values, label, trailing, plural, nameOnly);
    }

    public Phrase inPlural() {
        return new Phrase(verb, template, target, values, valueLabel, trailing, true, nameOnly);
    }

    /** A named target reads by its name alone: {@code confirm “Delete order?”}, not {@code confirm confirm dialog …}. */
    public Phrase byNameOnly() {
        return new Phrase(verb, template, target, values, valueLabel, trailing, plural, true);
    }

    public Phrase withTarget(StepTarget newTarget) {
        return new Phrase(verb, template, newTarget, values, valueLabel, trailing, plural, nameOnly);
    }

    /** The step as one line, before secrets are masked. */
    public String render() {
        String quoted = values.stream().map(Phrase::quote).collect(Collectors.joining(", "));
        boolean placed = template != null && template.contains("{value}");
        StringBuilder sb = new StringBuilder(template == null ? verb : template.replace("{value}", quoted));
        if (target != null) {
            sb.append(' ').append(nameOnly && target.name() != null ? target.renderWithoutKind() : target.render(plural));
        }
        if (!placed && !values.isEmpty()) {
            sb.append(' ');
            if (!valueLabel.isEmpty()) {
                sb.append(valueLabel).append(' ');
            }
            sb.append(quoted);
        }
        if (trailing != null && !trailing.isEmpty()) {
            sb.append(' ').append(trailing);
        }
        if (target != null && target.scope() != null) {
            sb.append(" in ").append(target.scope());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return render();
    }

    static String quote(String value) {
        return "“" + cut(value) + "”";
    }

    /** Cuts a value longer than {@link #MAX_VALUE_LENGTH} characters with "…". */
    public static String cut(String value) {
        if (value == null) {
            return "";
        }
        if (value.codePointCount(0, value.length()) <= MAX_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_VALUE_LENGTH - 1)) + "…";
    }
}
