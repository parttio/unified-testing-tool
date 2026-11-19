package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.YamlResourceLoader;

/**
 * The rewrite-maven-plugin reads the declarative recipes of the jar named in {@code recipeArtifactCoordinates}, not
 * those of its dependencies: a recipe of ours that names a declarative recipe of another jar fails validation there
 * and is skipped (#355). Java recipes resolve through the class loader, so only our own YAML is loaded here.
 */
class RecipesResolveOnTheirOwnTest {

    @Test
    void everyRecipeOursNameExistsWithoutTheYamlOfOtherJars() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        Environment.Builder builder = Environment.builder();
        for (String file : List.of("browserless.yml", "dramafinder.yml", "testbench.yml")) {
            URL url = classLoader.getResource("META-INF/rewrite/" + file);
            try (InputStream in = url.openStream()) {
                builder.load(new YamlResourceLoader(in, URI.create(url.toString()), new Properties(), classLoader));
            }
        }
        Environment environment = builder.build();

        List<String> errors = new ArrayList<>();
        for (Recipe recipe : environment.listRecipes()) {
            if (!recipe.getName().startsWith("org.vaadin.addons.unified.rewrite.")) {
                continue;
            }
            recipe.validate().failures().forEach(failure ->
                    errors.add(recipe.getName() + ": " + failure.getMessage()));
        }
        assertThat(errors).as("validation errors").isEmpty();
    }
}
