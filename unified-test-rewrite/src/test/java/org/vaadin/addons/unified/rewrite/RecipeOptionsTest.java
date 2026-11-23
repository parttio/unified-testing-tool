package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.YamlResourceLoader;
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

    @Test
    void springBootTestFromARewriteYmlReachesTheSetupRecipe() {
        for (String value : List.of("false", "true")) {
            String yaml = """
                    type: specs.openrewrite.org/v1beta/recipe
                    name: com.example.Migrate
                    recipeList:
                      - org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified:
                          springBootTest: %s
                    """.formatted(value);
            Recipe recipe = Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite")
                    .load(new YamlResourceLoader(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
                            URI.create("rewrite.yml"), new Properties()))
                    .build().activateRecipes("com.example.Migrate");
            List<TestBenchSetupToUnified> setups = new ArrayList<>();
            collect(recipe, TestBenchSetupToUnified.class, setups);
            assertThat(setups).as(value).isNotEmpty()
                    .allMatch(setup -> Boolean.valueOf(value).equals(setup.getSpringBootTest() == null
                            || setup.getSpringBootTest()));
        }
    }

    private static <T> void collect(Recipe recipe, Class<T> type, List<T> found) {
        if (recipe instanceof Recipe.DelegatingRecipe delegating) { // behind a declared recipe's precondition
            collect(delegating.getDelegate(), type, found);
        }
        if (type.isInstance(recipe)) {
            found.add(type.cast(recipe));
        }
        for (Recipe child : recipe.getRecipeList()) {
            collect(child, type, found);
        }
    }
}
