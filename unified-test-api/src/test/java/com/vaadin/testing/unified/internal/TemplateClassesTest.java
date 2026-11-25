package com.vaadin.testing.unified.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vaadin.testing.unified.internal.templateclasses.Base;
import com.vaadin.testing.unified.internal.templateclasses.Unique;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The class the application declares for a Template's tag, from its packages (#616). */
class TemplateClassesTest {

    private static final List<String> PACKAGES = List.of("com.vaadin.testing.unified.internal.templateclasses");

    @Test
    void aTagOnlyOneClassDeclaresGivesThatClass() {
        assertEquals(Optional.of(Unique.class), TemplateClasses.forTag("fixture-unique", PACKAGES));
    }

    @Test
    void aTagTwoClassesDeclareGivesNone() {
        assertEquals(Optional.empty(), TemplateClasses.forTag("fixture-shared", PACKAGES));
    }

    @Test
    void aSubclassThatOnlyInheritsTheTagIsNotAnotherClassOfIt() {
        assertEquals(Optional.of(Base.class), TemplateClasses.forTag("fixture-base", PACKAGES));
    }

    @Test
    void anAbstractClassIsNotFound() {
        assertEquals(Optional.empty(), TemplateClasses.forTag("fixture-abstract", PACKAGES));
    }

    @Test
    void aTagNoClassDeclaresGivesNone() {
        assertEquals(Optional.empty(), TemplateClasses.forTag("fixture-nowhere", PACKAGES));
    }

    @Test
    void aClassOutsideThePackagesIsNotFound() {
        assertEquals(Optional.empty(), TemplateClasses.forTag("fixture-unique", List.of("demo.nowhere")));
    }
}
