package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An agent knows only the settings the bundled skill names, and a person only those the configuration guide names:
 * every {@code vaadin.testing.*} setting the jars read is in both.
 */
class SkillListsEverySettingTest {

    private static final Pattern SETTING = Pattern.compile("\"(vaadin\\.testing\\.[a-z0-9.-]+)\"");
    private static final Path ROOT = Path.of("..");

    @Test
    @DisplayName("The bundled skill lists every vaadin.testing.* setting the jars read")
    void skillListsEverySetting() throws IOException {
        assertThat(AddUnifiedTestingSkill.skill()).contains(rows(settingsReadByTheJars()));
    }

    @Test
    @DisplayName("The configuration guide lists every vaadin.testing.* setting the jars read")
    void configurationGuideListsEverySetting() throws IOException {
        assertThat(Files.readString(ROOT.resolve("docs/guide/configuration.md")))
                .contains(rows(settingsReadByTheJars()));
    }

    private static String[] rows(Set<String> settings) {
        return settings.stream().map(s -> "| `" + s + "` |").toArray(String[]::new);
    }

    /** The setting names in the main sources of the shipped modules: unified-test-api and the adapters. */
    static Set<String> settingsReadByTheJars() throws IOException {
        Set<String> settings = new TreeSet<>();
        for (String module : new String[] {"unified-test-api", "unified-test-browserless", "unified-test-playwright",
                "unified-test-selenium", "unified-test-spring"}) {
            Path sources = ROOT.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sources)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(sources)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    Matcher m = SETTING.matcher(Files.readString(file));
                    while (m.find()) {
                        settings.add(m.group(1));
                    }
                }
            }
        }
        assertThat(settings).as("settings found in the main sources").contains("vaadin.testing.tool");
        return settings;
    }
}
