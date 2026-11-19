package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.RecipeIntrospectionUtils;

/** The options a project sets on this module's recipes in its {@code rewrite.yml} reach them (issue #353). */
class RecipeOptionsTest {

    @Test
    void everyRecipeConstructorKeepsItsParameterNames() {
        // OpenRewrite matches the options to the constructor's parameters by name, so it needs javac -parameters
        List<String> unnamed = Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                .listRecipes().stream()
                .map(Recipe::getClass)
                .filter(type -> type.getPackageName().equals(RecipeOptionsTest.class.getPackageName()))
                .distinct()
                .flatMap(type -> Arrays.stream(type.getConstructors()))
                .filter(constructor -> Arrays.stream(constructor.getParameters()).anyMatch(p -> !p.isNamePresent()))
                .map(Constructor::toString)
                .toList();
        assertThat(unnamed).isEmpty();
    }

    @Test
    void addAgentSkillReachesEachMigrationRecipe() {
        for (Class<?> type : List.of(MigrateBrowserlessToUnified.class, MigrateDramafinderToUnified.class,
                MigrateTestBenchToUnified.class)) {
            Recipe recipe = RecipeIntrospectionUtils.constructRecipe(type, Map.of("addAgentSkill", true));
            assertThat(recipe.getRecipeList()).as(type.getSimpleName())
                    .anyMatch(AddMigrationMarkersSkill.class::isInstance);
        }
    }
}
