package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.marker.Markup;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.text.PlainText;

/** {@link AddUnifiedTestingSkill}: installs the write-unified-ui-tests skill and its AGENTS.md (or CLAUDE.md) pointer. */
class AddUnifiedTestingSkillTest implements RewriteTest {

    private static final String SKILL = AddUnifiedTestingSkill.skill();
    private static final String SKILL_PATH = ".claude/skills/write-unified-ui-tests/SKILL.md";

    @Override
    public void defaults(RecipeSpec spec) {
        // found by name on the classpath, as the rewrite Maven and Gradle plugins find it when it runs alone
        spec.recipe(Environment.builder().scanRuntimeClasspath("org.vaadin.addons.unified.rewrite").build()
                .activateRecipes("org.vaadin.addons.unified.rewrite.AddUnifiedTestingSkill"));
    }

    /** A Maven project with one Java source. */
    private static SourceSpecs[] project() {
        return new SourceSpecs[] {
            text("<project/>", source -> source.path("pom.xml")),
            java("class ViewTest {}")
        };
    }

    private static SourceSpecs[] with(SourceSpecs... specs) {
        SourceSpecs[] project = project();
        SourceSpecs[] all = new SourceSpecs[project.length + specs.length];
        System.arraycopy(project, 0, all, 0, project.length);
        System.arraycopy(specs, 0, all, project.length, specs.length);
        return all;
    }

    @Test
    void theShippedSkillCarriesThisReleasesStamp() {
        assertThat(AddUnifiedTestingSkill.stamp(SKILL)).isEqualTo(System.getProperty("unified.version"));
        assertThat(SKILL).startsWith("---\nname: write-unified-ui-tests\n");
    }

    @Test
    void freshProjectGetsTheSkillAndAgentsMd() {
        rewriteRun(with(
                text(null, SKILL, source -> source.path(SKILL_PATH)),
                text(null, """
                        <!-- unified-test: write-unified-ui-tests -->
                        ## Unified UI Tests

                        For Unified UI Tests, follow `.claude/skills/write-unified-ui-tests/SKILL.md`.
                        <!-- /unified-test -->
                        """, source -> source.path("AGENTS.md"))));
    }

    @Test
    void writesNothingOutsideAProject() {
        rewriteRun(java("class ViewTest {}"));
    }

    @Test
    void replacesACopyWithAnOlderStamp() {
        rewriteRun(with(
                text("""
                        ---
                        name: write-unified-ui-tests
                        ---
                        <!-- unified-test-skill: write-unified-ui-tests 0.0.1 -->
                        the old skill
                        """, SKILL, source -> source.path(SKILL_PATH).noTrim()),
                text(AddUnifiedTestingSkill.SECTION, source -> source.path("AGENTS.md"))));
    }

    @Test
    void keepsACopyWithThisReleasesStamp() {
        rewriteRun(with(
                text(SKILL, source -> source.path(SKILL_PATH)),
                text(AddUnifiedTestingSkill.SECTION, source -> source.path("AGENTS.md"))));
    }

    @Test
    void keepsACopyWithANewerStamp() {
        rewriteRun(with(
                text("<!-- unified-test-skill: write-unified-ui-tests 99.0.0 -->\na newer skill\n",
                        source -> source.path(SKILL_PATH)),
                text(AddUnifiedTestingSkill.SECTION, source -> source.path("AGENTS.md"))));
    }

    @Test
    void keepsAnUnstampedCopyAndWarns() {
        String handEdited = """
                ---
                name: write-unified-ui-tests
                ---
                our own rules
                """;
        rewriteRun(
                spec -> spec.afterRecipe(run -> assertThat(run.getChangeset().getAllResults())
                        .filteredOn(r -> r.getAfter() != null && r.getAfter().getSourcePath().toString().equals(SKILL_PATH))
                        .singleElement()
                        .satisfies(r -> {
                            assertThat(((PlainText) r.getAfter()).getText()).isEqualTo(handEdited.strip());
                            assertThat(r.getAfter().getMarkers().findFirst(Markup.Warn.class))
                                    .hasValueSatisfying(warn -> assertThat(warn.getMessage())
                                            .isEqualTo(AddUnifiedTestingSkill.UNSTAMPED_WARNING));
                        })),
                with(
                        text(handEdited, "~~(" + AddUnifiedTestingSkill.UNSTAMPED_WARNING + ")~~>" + handEdited,
                                source -> source.path(SKILL_PATH)),
                        text(AddUnifiedTestingSkill.SECTION, source -> source.path("AGENTS.md"))));
    }

    @Test
    void addsTheSectionToAnExistingAgentsMd() {
        rewriteRun(with(
                text(SKILL, source -> source.path(SKILL_PATH)),
                text("""
                        # Agents

                        Run `mvn verify` before committing.
                        """, """
                        # Agents

                        Run `mvn verify` before committing.

                        <!-- unified-test: write-unified-ui-tests -->
                        ## Unified UI Tests

                        For Unified UI Tests, follow `.claude/skills/write-unified-ui-tests/SKILL.md`.
                        <!-- /unified-test -->
                        """, source -> source.path("AGENTS.md"))));
    }

    @Test
    void updatesTheSectionInPlace() {
        rewriteRun(with(
                text(SKILL, source -> source.path(SKILL_PATH)),
                text("""
                        # Agents

                        <!-- unified-test: write-unified-ui-tests -->
                        Old pointer.
                        <!-- /unified-test -->

                        ## Releases
                        """, """
                        # Agents

                        <!-- unified-test: write-unified-ui-tests -->
                        ## Unified UI Tests

                        For Unified UI Tests, follow `.claude/skills/write-unified-ui-tests/SKILL.md`.
                        <!-- /unified-test -->

                        ## Releases
                        """, source -> source.path("AGENTS.md"))));
    }

    @Test
    void leavesAnUpToDateAgentsMdAlone() {
        rewriteRun(with(
                text(SKILL, source -> source.path(SKILL_PATH)),
                text("# Agents\n\n" + AddUnifiedTestingSkill.SECTION + "\n## Releases\n",
                        source -> source.path("AGENTS.md"))));
    }

    @Test
    void aProjectWithClaudeMdAndNoAgentsMdGetsTheSectionInClaudeMd() {
        // Claude Code reads CLAUDE.md, not AGENTS.md (#365): no AGENTS.md is created
        rewriteRun(with(
                text(null, SKILL, source -> source.path(SKILL_PATH)),
                text("""
                        # Mise

                        Run `mvn verify` before committing.
                        """, """
                        # Mise

                        Run `mvn verify` before committing.

                        <!-- unified-test: write-unified-ui-tests -->
                        ## Unified UI Tests

                        For Unified UI Tests, follow `.claude/skills/write-unified-ui-tests/SKILL.md`.
                        <!-- /unified-test -->
                        """, source -> source.path("CLAUDE.md"))));
    }

    @Test
    void updatesTheSectionInClaudeMdInPlace() {
        rewriteRun(with(
                text(SKILL, source -> source.path(SKILL_PATH)),
                text("""
                        # Mise

                        <!-- unified-test: write-unified-ui-tests -->
                        Old pointer.
                        <!-- /unified-test -->

                        ## Releases
                        """, """
                        # Mise

                        <!-- unified-test: write-unified-ui-tests -->
                        ## Unified UI Tests

                        For Unified UI Tests, follow `.claude/skills/write-unified-ui-tests/SKILL.md`.
                        <!-- /unified-test -->

                        ## Releases
                        """, source -> source.path("CLAUDE.md"))));
    }

    @Test
    void withBothFilesOnlyAgentsMdGetsTheSection() {
        rewriteRun(with(
                text(SKILL, source -> source.path(SKILL_PATH)),
                text("# Mise\n", source -> source.path("CLAUDE.md")),
                text("# Agents\n", "# Agents\n\n" + AddUnifiedTestingSkill.SECTION, source -> source.path("AGENTS.md"))));
    }

    @Test
    void everyMigrationRecipeInstallsIt() {
        for (var recipe : java.util.List.of(new MigrateTestBenchToUnified(), new MigrateBrowserlessToUnified(),
                new MigrateDramafinderToUnified())) {
            assertThat(recipe.getRecipeList()).anyMatch(AddUnifiedTestingSkill.class::isInstance);
        }
    }
}
