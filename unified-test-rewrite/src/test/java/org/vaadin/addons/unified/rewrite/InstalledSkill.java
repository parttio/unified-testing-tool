package org.vaadin.addons.unified.rewrite;

import static org.openrewrite.test.SourceSpecs.text;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.SourceSpecs;

/**
 * The files {@link AddUnifiedTestingSkill} writes into a project: every Migration Recipe runs it, so a recipe test
 * with a build file and Java sources expects them.
 */
final class InstalledSkill {

    private InstalledSkill() {
    }

    /**
     * {@code .claude/skills/write-unified-ui-tests/SKILL.md}, {@code AGENTS.md} and the {@code CLAUDE.md} that imports
     * it, all new.
     */
    static SourceSpecs files() {
        List<SourceSpec<?>> specs = new ArrayList<>();
        text(null, AddUnifiedTestingSkill.skill(), s -> s.path(AddUnifiedTestingSkill.PATH)).forEach(specs::add);
        text(null, AddUnifiedTestingSkill.SECTION, s -> s.path(AddUnifiedTestingSkill.AGENTS)).forEach(specs::add);
        text(null, AddUnifiedTestingSkill.CLAUDE_IMPORT, s -> s.path(AddUnifiedTestingSkill.CLAUDE).noTrim())
                .forEach(specs::add);
        return specs::iterator;
    }
}
