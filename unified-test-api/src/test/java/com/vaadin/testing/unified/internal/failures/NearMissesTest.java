package com.vaadin.testing.unified.internal.failures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vaadin.testing.unified.internal.failures.NearMisses.Candidate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The ranking of Near Misses and when one is clearly best (spec chapter 12, "Failures"). */
class NearMissesTest {

    private static List<Candidate<String>> fields(String... labels) {
        return List.of(labels).stream().map(l -> new Candidate<>("text field", l, false, l)).toList();
    }

    private static List<Candidate<String>> comboBoxes(String... labels) {
        return List.of(labels).stream().map(l -> new Candidate<>("combo box", l, false, "combo " + l)).toList();
    }

    @Test
    void ranksSameTextIgnoringCaseThenContainingThenByEditDistance() {
        NearMisses.Result<String> result = NearMisses.rank("Email",
                fields("Phone", "Work email", "EMAIL ", "E-mail", "", "Name"), List.of());

        assertEquals(List.of("text field “EMAIL ”", "text field “Work email”", "text field “E-mail”",
                "text field “Name”", "text field “Phone”"), result.rendered());
    }

    @Test
    void showsAtMostFiveOfTheSameKindThenThreeOfAnother() {
        NearMisses.Result<String> result = NearMisses.rank("Role", fields("A", "B", "C", "D", "E", "F"),
                comboBoxes("Role", "Role", "Role", "Role"));

        assertEquals(5 + 3, result.shown().size());
        assertEquals("combo box “Role”", result.rendered().get(5));
    }

    @Test
    void suggestsTheOnlyOneThatDiffersInCaseSpacingOrPunctuation() {
        assertEquals("E-mail", NearMisses.rank("Email", fields("Name", "E-mail"), List.of()).suggestion().value());
        assertEquals("first name", NearMisses.rank("First Name", fields("first name"), List.of()).suggestion().value());
        assertNull(NearMisses.rank("Email", fields("E-mail", "E mail"), List.of()).suggestion());
    }

    @Test
    void suggestsTheOnlyOneWithinASmallEditDistance() {
        assertEquals("Address", NearMisses.rank("Adress", fields("Name", "Address"), List.of()).suggestion().value());
        // a tie is not clearly best
        assertNull(NearMisses.rank("Cat", fields("Bat", "Hat"), List.of()).suggestion());
        // too far: more than max(2, length / 4)
        assertNull(NearMisses.rank("Email", fields("Phone"), List.of()).suggestion());
        assertEquals("Shipping street", NearMisses.rank("Shiping stret", fields("Shipping street", "Billing street"),
                List.of()).suggestion().value());
    }

    @Test
    void suggestsTheOnlyOneOfAnotherKindWithTheExactName() {
        Candidate<String> best = NearMisses.rank("Role", fields("Name"), comboBoxes("Role")).suggestion();
        assertEquals("combo Role", best.payload());

        assertNull(NearMisses.rank("Role", fields("Name"), comboBoxes("Role", "Role")).suggestion());
        // a close one of the same kind and an exact one of another kind: neither is clearly best
        assertNull(NearMisses.rank("Rol", fields("Role"), comboBoxes("Rol")).suggestion());
    }

    @Test
    void suggestsNothingWhenTheNameIsThere() {
        // another filter or the scope ruled it out: renaming wouldn't help
        assertNull(NearMisses.rank("Email", fields("Email", "E-mail"), List.of()).suggestion());
    }

    @Test
    void marksHiddenOnes() {
        assertEquals("text field “E-mail” (hidden)", new Candidate<>("text field", "E-mail", true, null).render());
    }

    @Test
    void measuresEditDistanceIgnoringCase() {
        assertEquals(0, NearMisses.distance("Email", "EMAIL"));
        assertEquals(1, NearMisses.distance("Email", "E-mail"));
        assertEquals(3, NearMisses.distance("kitten", "sitting"));
    }
}
