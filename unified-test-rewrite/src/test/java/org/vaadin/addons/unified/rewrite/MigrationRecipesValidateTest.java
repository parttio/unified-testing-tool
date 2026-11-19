package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.Validated;

/**
 * Every recipe a Migration Recipe runs resolves: the composites build their recipe lists from this module's
 * {@code META-INF/rewrite} files alone, so a declarative recipe from another jar would never load (#386).
 */
class MigrationRecipesValidateTest {

    @Test
    void everyMigrationRecipeValidatesWithEveryOptionOn() {
        for (Recipe recipe : List.of(new MigrateBrowserlessToUnified(true, true, true, true),
                new MigrateDramafinderToUnified(true, true, true), new MigrateTestBenchToUnified(true, true, true))) {
            List<String> failures = recipe.validateAll().stream()
                    .flatMap(validated -> validated.failures().stream())
                    .map(Validated.Invalid::getMessage)
                    .toList();
            assertThat(failures).as(recipe.getName()).isEmpty();
        }
    }
}
