package com.vaadin.testing.unified.internal.failures;

import com.vaadin.testing.unified.UnifiedAssertionError;
import com.vaadin.testing.unified.internal.TestRun;
import java.util.List;

/** A Unified failure for an item, tab or menu entry a component doesn't have, with its own items as Near Misses. */
public final class ItemFailures {
    private ItemFailures() {}

    /**
     * {@code Expected  item “Spain” in combo box “Country”}, {@code Found  none}, the closest items as Near Misses,
     * and {@code selectItem("France")} when one is clearly best.
     *
     * @param what the item kind in words: "item", "tab"
     * @param wanted the item the test asked for
     * @param scope the component, in words with its scope
     * @param items the component's items
     * @param method the Wrapper method the suggestion calls
     */
    public static UnifiedAssertionError missing(TestRun run, String what, String wanted, String scope,
            List<String> items, String method) {
        List<NearMisses.Candidate<String>> candidates = items.stream()
                .map(item -> new NearMisses.Candidate<>(what, item, false, item)).toList();
        NearMisses.Result<String> result = NearMisses.rank(wanted, candidates, List.of());
        FailureParts parts = FailureParts.of(what + " “" + wanted + "” in " + scope, "none")
                .nearMisses(result.rendered());
        if (result.suggestion() != null) {
            parts.suggestion(method + "(" + javaString(result.suggestion().value()) + ")");
            parts.lookup(method + "(" + javaString(wanted) + ")");
        }
        return run.failure(parts, null);
    }

    private static String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
