package org.vaadin.addons.unified.rewrite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.UnaryOperator;
import org.openrewrite.Recipe;
import org.openrewrite.config.YamlResourceLoader;

/** The declarative recipes in this module's {@code META-INF/rewrite} files, for the composite recipes to run. */
final class DeclaredRecipes {

    private DeclaredRecipes() {
    }

    static YamlResourceLoader load(String resource) {
        return load(resource, yaml -> yaml);
    }

    /** The recipes of the resource, its YAML edited first: how a composite's option reaches a recipe declared there. */
    static YamlResourceLoader load(String resource, UnaryOperator<String> edit) {
        URL url = DeclaredRecipes.class.getResource(resource);
        try (InputStream in = url.openStream()) {
            byte[] yaml = edit.apply(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            return new YamlResourceLoader(new ByteArrayInputStream(yaml), url.toURI(), new Properties(),
                    DeclaredRecipes.class.getClassLoader());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The YAML with the option set on the recipe, which it lists without options. */
    static String withOption(String yaml, Class<? extends Recipe> recipe, String option, String value) {
        String entry = "  - " + recipe.getName() + "\n";
        if (!yaml.contains(entry)) {
            throw new IllegalStateException(recipe.getName() + " is not listed without options");
        }
        return yaml.replace(entry, "  - " + recipe.getName() + ":\n      " + option + ": " + value + "\n");
    }
}
