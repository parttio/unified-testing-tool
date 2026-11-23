package org.vaadin.addons.unified.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** {@link ApplyDidYouMean}: the suggestion of a failed lookup, written into the test. */
class ApplyDidYouMeanTest implements RewriteTest {

    @TempDir
    Path evidence;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ApplyDidYouMean(evidence.toString()))
                .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()));
    }

    /** A failed test's user-steps.json, as the Unified API writes it, with the parts the recipe reads. */
    private void failed(String testClass, String method, String lookup, String suggestion, String... codePathClasses)
            throws IOException {
        StringBuilder codePath = new StringBuilder();
        for (String c : codePathClasses) {
            String simple = c.substring(c.lastIndexOf('.') + 1).replaceFirst("\\$.*", "");
            codePath.append(codePath.isEmpty() ? "" : ", ").append("{\"file\": \"").append(simple).append(".java\", ")
                    .append("\"line\": 12, \"className\": \"").append(c).append("\", \"method\": \"m\"}");
        }
        String simple = testClass.substring(testClass.lastIndexOf('.') + 1);
        Path file = evidence.resolve("app/target/surefire-reports/unified-evidence").resolve(simple).resolve(method)
                .resolve("user-steps.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"schemaVersion": 1, "testClass": "%s", "testMethod": "%s", "displayName": "The user saves an email",
                 "tool": "browserless", "status": "failed",
                 "failure": {"type": "com.vaadin.testing.unified.UnifiedAssertionError",
                   "lookup": %s, "suggestion": %s, "codePath": [%s]},
                 "steps": []}
                """.formatted(testClass, method, json(lookup), json(suggestion), codePath));
    }

    private static String json(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final String TEST = """
            package demo;

            import com.vaadin.testing.unified.UnifiedPage;
            import com.vaadin.testing.unified.component.UnifiedButton;
            import com.vaadin.testing.unified.component.UnifiedTextField;

            class ProfileTest {
                void savesTheEmail(UnifiedPage page) {
                    page.find(UnifiedTextField.class).withLabel(%s).setValue("alan@example.com");
                    // a button with the same text is another lookup
                    page.find(UnifiedButton.class).withText("Email").click();
                }

                void anotherScenario(UnifiedPage page) {
                    UnifiedTextField email = page.find(UnifiedTextField.class).withTestId("email").withLabel(%s);
                    email.setValue("Email");
                }
            }
            """;

    @Test
    void theSuggestedValueReplacesTheOneTheTestHadWhereverThatLookupIsInTheFile() throws IOException {
        failed("demo.ProfileTest", "savesTheEmail", "find(UnifiedTextField.class).withLabel(\"Email\")",
                "find(UnifiedTextField.class).withLabel(\"E-mail\")", "demo.ProfileTest");

        rewriteRun(spec -> spec.dataTable(ApplyDidYouMean.Applied.Row.class, rows -> {
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).isEqualTo(new ApplyDidYouMean.Applied.Row("src/test/java/demo/ProfileTest.java",
                    "withLabel", "Email", "E-mail", "The user saves an email"));
        }), java(TEST.formatted("\"Email\"", "\"Email\""), TEST.formatted("\"E-mail\"", "\"E-mail\""),
                source -> source.path("src/test/java/demo/ProfileTest.java")));
    }

    @Test
    void aFailureFailsafeWroteIsFoundToo() throws IOException {
        failed("demo.ProfileTest", "savesTheEmail", "find(UnifiedTextField.class).withLabel(\"Email\")",
                "find(UnifiedTextField.class).withLabel(\"E-mail\")", "demo.ProfileTest");
        // where the build recipes put Failsafe's evidence (#512): every user-steps.json under the directory is read
        Path surefire = evidence.resolve("app/target/surefire-reports");
        Path failsafe = evidence.resolve("app/target/failsafe-reports");
        Files.move(surefire, failsafe);

        rewriteRun(java(TEST.formatted("\"Email\"", "\"Email\""), TEST.formatted("\"E-mail\"", "\"E-mail\""),
                source -> source.path("src/test/java/demo/ProfileTest.java")));
    }

    @Test
    void onlyFilesOnTheFailuresCodePathAreChanged() throws IOException {
        failed("demo.ProfileTest", "savesTheEmail", "find(UnifiedTextField.class).withLabel(\"Email\")",
                "find(UnifiedTextField.class).withLabel(\"E-mail\")", "demo.tasks.ProfileTasks");
        String tasks = """
                package demo.tasks;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedTextField;

                class ProfileTasks {
                    static void fillEmail(UnifiedPage page) {
                        page.find(UnifiedTextField.class).withLabel(%s).setValue("a@b.c");
                    }
                }
                """;

        rewriteRun(
                // the Tasks class the failed step was called from
                java(tasks.formatted("\"Email\""), tasks.formatted("\"E-mail\""),
                        source -> source.path("app/src/test/java/demo/tasks/ProfileTasks.java")),
                // the same lookup in a test that didn't fail: another view may still say Email
                java(tasks.formatted("\"Email\"").replace("demo.tasks", "other").replace("ProfileTasks", "SignUpTasks"),
                        source -> source.path("app/src/test/java/other/SignUpTasks.java")));
    }

    @Test
    void anItemIsReplacedInItsCall() throws IOException {
        failed("demo.OrderTest", "orders", "selectItem(\"Span\")", "selectItem(\"Spain\")", "demo.OrderTest");
        String test = """
                package demo;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedComboBox;

                class OrderTest {
                    void orders(UnifiedPage page) {
                        UnifiedComboBox country = page.find(UnifiedComboBox.class).withLabel("Span");
                        country.selectItem(%s);
                    }
                }
                """;

        rewriteRun(java(test.formatted("\"Span\""), test.formatted("\"Spain\""),
                source -> source.path("src/test/java/demo/OrderTest.java")));
    }

    @Test
    void aValueThatIsNotALiteralAndASuggestionOfAnotherWrapperAreLeftAlone() throws IOException {
        failed("demo.ProfileTest", "savesTheEmail", "find(UnifiedTextField.class).withLabel(\"Email\")",
                "find(UnifiedTextField.class).withLabel(\"E-mail\")", "demo.ProfileTest");
        failed("demo.ProfileTest", "clicksSave", "find(UnifiedButton.class).withText(\"Save\")",
                "find(UnifiedTextField.class).withLabel(\"Save\")", "demo.ProfileTest");

        rewriteRun(java("""
                package demo;

                import com.vaadin.testing.unified.UnifiedPage;
                import com.vaadin.testing.unified.component.UnifiedButton;
                import com.vaadin.testing.unified.component.UnifiedTextField;

                class ProfileTest {
                    static final String EMAIL = "Email";

                    void savesTheEmail(UnifiedPage page) {
                        page.find(UnifiedTextField.class).withLabel(EMAIL).setValue("alan@example.com");
                    }

                    void clicksSave(UnifiedPage page) {
                        page.find(UnifiedButton.class).withText("Save").click();
                    }
                }
                """, source -> source.path("src/test/java/demo/ProfileTest.java")));
    }

    @Test
    void whatToChangeIsTheOneStringTheTwoLookupsDifferIn() {
        assertThat(ApplyDidYouMean.difference("find(UnifiedTextField.class).withLabel(\"Email\")",
                "find(UnifiedTextField.class).withLabel(\"E-mail\")")).isEqualTo(Optional.of(
                        new ApplyDidYouMean.Change("UnifiedTextField", "withLabel", 0, "Email", "E-mail", Set.of(), "")));
        // the second argument of a call, after other filters, with quotes and commas in the values
        assertThat(ApplyDidYouMean.difference(
                "findComponent(Div.class).withTestId(\"a, b\").withAttribute(\"title\", \"Say \\\"hi\\\"\")",
                "findComponent(Div.class).withTestId(\"a, b\").withAttribute(\"title\", \"Say \\\"hello\\\"\")"))
                .isEqualTo(Optional.of(new ApplyDidYouMean.Change("Div", "withAttribute", 1, "Say \"hi\"",
                        "Say \"hello\"", Set.of(), "")));
        assertThat(ApplyDidYouMean.difference("selectTab(\"Securty\")", "selectTab(\"Security\")")).isEqualTo(
                Optional.of(new ApplyDidYouMean.Change(null, "selectTab", 0, "Securty", "Security", Set.of(), "")));
        // another Wrapper, another filter, two values, or nothing to change: not applied
        assertThat(List.of(
                ApplyDidYouMean.difference("find(UnifiedButton.class).withText(\"Save\")",
                        "find(UnifiedTextField.class).withLabel(\"Save\")"),
                ApplyDidYouMean.difference("find(A.class).withText(\"a\").withLabel(\"b\")",
                        "find(A.class).withText(\"x\").withLabel(\"y\")"),
                ApplyDidYouMean.difference("find(A.class).withText(\"a\")", "find(A.class).withText(\"a\")")))
                .containsOnly(Optional.empty());
    }

    @Test
    void failuresOfSeveralTestsWithTheSameSuggestionAreOneChangeOverTheirFiles() throws IOException {
        failed("demo.ProfileTest", "savesTheEmail", "find(UnifiedTextField.class).withLabel(\"Email\")",
                "find(UnifiedTextField.class).withLabel(\"E-mail\")", "demo.ProfileTest$Nested");
        failed("demo.SignUpTest", "signsUp", "find(UnifiedTextField.class).withLabel(\"Email\")",
                "find(UnifiedTextField.class).withLabel(\"E-mail\")", "demo.tasks.SignUpTasks");
        Files.writeString(evidence.resolve("user-steps.json"), "not JSON");

        List<ApplyDidYouMean.Change> changes = ApplyDidYouMean.read(evidence);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).files()).containsExactlyInAnyOrder("demo/ProfileTest.java", "demo/SignUpTest.java",
                "demo/tasks/SignUpTasks.java");
        assertThat(changes.get(0).appliesTo(Path.of("app/src/test/java/demo/tasks/SignUpTasks.java"))).isTrue();
        assertThat(changes.get(0).appliesTo(Path.of("app/src/test/java/other/demo/tasks/XSignUpTasks.java"))).isFalse();
        assertThat(ApplyDidYouMean.read(evidence.resolve("missing"))).isEmpty();
    }
}
