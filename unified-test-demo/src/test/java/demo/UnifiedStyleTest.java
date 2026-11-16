package demo;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.vaadin.testing.unified.archunit.UnifiedStyleRules;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The demo's Unified UI Tests follow the style rules (spec 12, "Style rules"). */
class UnifiedStyleTest {

    // --8<-- [start:excludingClasses]
    /**
     * The golden migration outputs ({@code *Migrated*}) are left out: {@code GoldenMigrationTest} in
     * {@code unified-test-rewrite} compares them with what the Migration Recipes write, which has no scenario
     * sentences yet and keeps Migration Markers' Escape Hatches. Their inputs ({@code *TestBenchTest},
     * {@code *BrowserlessTest}, {@code *DramafinderTest}, ...) are no Unified UI Tests, so the rules skip them anyway.
     */
    private static final Pattern GOLDEN_MIGRATION_OUTPUT = Pattern.compile(".*/demo/[^/]*Migrated[^/]*\\.class");
    private static final ImportOption NOT_GOLDEN_MIGRATION_OUTPUT =
            location -> !location.matches(GOLDEN_MIGRATION_OUTPUT);

    @Test
    @DisplayName("Every Unified UI Test in the demo follows the style rules")
    void followsTheUnifiedStyle() {
        UnifiedStyleRules.ALL.check(new ClassFileImporter()
                .withImportOption(NOT_GOLDEN_MIGRATION_OUTPUT)
                .importPackages("demo"));
    }
    // --8<-- [end:excludingClasses]
}
