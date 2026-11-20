package com.vaadin.testing.unified.internal.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code report.html}: {@code summary.md}'s sections in a browser, from the same {@code user-steps.json}. */
class RunReportTest {

    private static ScenarioResult scenario(String json, String evidence) {
        return ScenarioResult.parse(json, "target/surefire-reports/unified-evidence/" + evidence + "/");
    }

    private static List<ScenarioResult> run() {
        return List.of(scenario(RunSummaryTest.ORDER_FAILED, "OrderTest/totals"),
                scenario(RunSummaryTest.EDIT_PERSON, "EditPersonTest/editsAPerson"),
                scenario(RunSummaryTest.CHECKOUT_FAILED, "CheckoutTest$AsGuest/paysByCard"),
                scenario(RunSummaryTest.ABORTED, "OrderTest/onlyOnFriday"));
    }

    private static final ChangedFiles CHANGES = ChangedFiles.of("origin/main",
            List.of("unified-test-demo/src/test/java/demo/tasks/PeopleTasks.java",
                    "unified-test-demo/src/test/java/demo/CheckoutTest.java"),
            List.of(), Map.of("unified-test-demo/src/test/java/demo/CheckoutTest.java",
                    "class CheckoutTest { void other() {} }"));

    private static String report(ChangedFiles changes) {
        return new RunReport(new RunSummary("Playwright", run(), 2, changes),
                s -> s.testMethod().equals("paysByCard") ? List.of("demo.CheckoutTest$AsGuest.paysByCard.png")
                        : List.of())
                .html();
    }

    @Test
    void sectionsComeInSummaryOrderWithPassingHidden() {
        String html = report(CHANGES);

        int header = html.indexOf("<h1>Unified UI Tests <span class=\"tool\">Playwright</span></h1>");
        int changed = html.indexOf("<section id=\"changed\">");
        int failed = html.indexOf("<section id=\"failed\">");
        int passing = html.indexOf("<section id=\"passing\" hidden>");
        assertTrue(header >= 0 && header < changed && changed < failed && failed < passing, html);
        assertTrue(html.contains("1 passed</span><span class=\"count failed\">2 failed</span>"
                + "<span class=\"count skipped\">3 skipped</span>"), html);
        assertTrue(html.contains("Show passing (1)"), html);
        // the passing scenario is in the hidden section, folded
        String passingSection = html.substring(passing, html.indexOf("</section>", passing));
        assertTrue(passingSection.contains("Editing a person updates the grid"), passingSection);
        assertFalse(passingSection.matches("(?s).*<details class=\"scenario[^>]* open>.*"), passingSection);
        // aborted scenarios count as skipped only
        assertFalse(html.contains("onlyOnFriday\""), html);
    }

    @Test
    void passingScenariosWithoutACheckHaveASectionAndABadge() {
        String endsOnAnAction = RunSummaryTest.EDIT_PERSON.replace("\"verb\": \"see\"", "\"verb\": \"click\"");
        String html = new RunReport(new RunSummary("Playwright",
                List.of(scenario(endsOnAnAction, "EditPersonTest/editsAPerson")), 0, null), s -> List.of()).html();

        int failed = html.indexOf("<section id=\"failed\">");
        int unchecked = html.indexOf("<section id=\"unchecked\">");
        int passing = html.indexOf("<section id=\"passing\" hidden>");
        assertTrue(failed < unchecked && unchecked < passing, html);
        String section = html.substring(unchecked, passing);
        assertTrue(section.contains("1 passing scenario ends on User Steps that nothing checks"), section);
        assertTrue(section.contains("<span class=\"badge unchecked\">no check</span>"), section);
        assertTrue(section.contains("<p class=\"why\">no check after: “see notification “Saved””</p>"), section);
        // a run whose scenarios all end on a check has no such section
        assertFalse(report(null).contains("id=\"unchecked\""));
    }

    @Test
    void aChangedScenarioSaysWhyAndShowsItsDiffWithASwitchToTheFullSteps() {
        BaseRun base = BaseRun.of(List.of(scenario(RunSummaryTest.EDIT_PERSON_BASE, "EditPersonTest/editsAPerson")),
                "playwright");
        String html = new RunReport(new RunSummary("Playwright", run(), 0, CHANGES, base), s -> List.of()).html();

        assertTrue(html.contains("<input type=\"checkbox\" id=\"show-diff\" checked> Diff against base run"), html);
        String changed = html.substring(html.indexOf("<section id=\"changed\">"), html.indexOf("<section id=\"failed\">"));
        assertTrue(changed.contains("<details class=\"scenario passed has-diff\""), changed);
        assertTrue(changed.contains("<span class=\"diffstat\"><span class=\"add\">+2</span> <span class=\"del\">−3</span>"
                + " against the base run</span>"), changed);
        assertTrue(changed.contains("<p class=\"why\">Called from changed files: <code>PeopleTasks.java</code> (editPerson)</p>"),
                changed);
        assertTrue(changed.contains("<span class=\"del\">-    fill text field “Name” with “Alan Turing”</span>"
                + "<span class=\"add\">+    fill text field “Name” with “Alan M. Turing”</span>"), changed);
        // the full steps stay in the page, for the switch; a new scenario has no why and no diff
        assertTrue(changed.contains("<ol class=\"steps\">"), changed);
        String checkout = changed.substring(changed.indexOf("A guest pays by card"),
                changed.indexOf("Editing a person updates the grid"));
        assertFalse(checkout.contains("class=\"why\""), checkout);
        // the failed and passing sections show the steps only
        assertEquals(1, html.split("<pre class=\"diff\"", -1).length - 1, html);
    }

    @Test
    void withoutABaseRunThereIsNoDiffSwitch() {
        assertFalse(report(CHANGES).contains("id=\"show-diff\""));
        assertFalse(new RunReport(new RunSummary("Playwright", run(), 0, CHANGES, BaseRun.NONE), s -> List.of()).html()
                .contains("id=\"show-diff\""));
    }

    @Test
    void changedInThisPrMarksNewAndChangedScenarios() {
        String html = report(CHANGES);
        String changed = html.substring(html.indexOf("<section id=\"changed\">"),
                html.indexOf("<section id=\"failed\">"));

        assertTrue(changed.contains("2 scenarios call code changed since <code>origin/main</code>."), changed);
        assertTrue(changed.contains("A guest pays by card</span> <span class=\"class\">CheckoutTest.AsGuest</span>"
                + " <span class=\"badge new\">new</span>"), changed);
        assertTrue(changed.contains("<span class=\"badge changed\">changed</span>"), changed);
        assertTrue(html.contains("<input type=\"checkbox\" id=\"changed-only\">"), html);
    }

    @Test
    void leavesOutChangedInThisPrWithoutABaseRef() {
        String html = report(null);

        assertFalse(html.contains("id=\"changed\""), html);
        assertFalse(html.contains("id=\"changed-only\""), html);
        assertTrue(html.indexOf("<section id=\"failed\">") < html.indexOf("<section id=\"passing\" hidden>"));
    }

    @Test
    void showsTheFailureWithItsNearMissesAndSuggestion() {
        String html = report(null);
        String checkout = html.substring(html.indexOf("A guest pays by card"));

        assertTrue(checkout.contains("<dt>Failed at</dt><dd>User Step 2: click button “Pay `now`”</dd>"), checkout);
        assertTrue(checkout.contains("<dt>Expected</dt><dd>exactly one button with text “Pay now”</dd>"), checkout);
        assertTrue(checkout.contains("<dt>Found</dt><dd>none</dd>"), checkout);
        assertTrue(checkout.contains("<dt>Nearby</dt><dd>button “Pay_now”, button “Cancel”</dd>"), checkout);
        assertTrue(checkout.contains("<dt>Did you mean</dt><dd><code>find(UnifiedButton.class).withText("
                + "&quot;Pay_now&quot;)</code></dd>"), checkout);
        assertTrue(checkout.contains("<li class=\"step failed\"><span class=\"idx\">1.2</span>"), checkout);
        // a failure outside any User Step shows its error line
        assertTrue(html.contains("<dt>Failed at</dt><dd>outside any User Step</dd>"), html);
        assertTrue(html.contains("<dt>Error</dt><dd><code>AssertionFailedError: expected: &lt;3&gt; but was: "
                + "&lt;4&gt;</code></dd>"), html);
    }

    @Test
    void linksEvidenceRelativeToTheReport() {
        String html = report(null);

        assertTrue(html.contains("<a href=\"CheckoutTest$AsGuest/paysByCard/user-steps.txt\">user-steps.txt</a>"),
                html);
        assertTrue(html.contains("<img src=\"demo.CheckoutTest$AsGuest.paysByCard.png\""), html);
        assertTrue(html.contains("<a href=\"EditPersonTest/editsAPerson/user-steps.json\">"), html);
        assertEquals("UsersTest/savesAll%5B2%5D/a%20b%C3%A9", RunReport.href("UsersTest/savesAll[2]/a bé"));
    }

    @Test
    void escapesEveryValue() {
        String json = RunSummaryTest.EDIT_PERSON
                .replace("Editing a person updates the grid", "<b>bold</b> & \\\"quoted\\\"")
                .replace("open People", "fill text field “Name” with “<script>alert(1)</script>”");
        String html = new RunReport(new RunSummary("Browserless",
                List.of(scenario(json, "EditPersonTest/editsAPerson")), 0, null), s -> List.of()).html();

        assertFalse(html.contains("<script>alert"), html);
        assertFalse(html.contains("<b>bold"), html);
        assertTrue(html.contains("“&lt;script&gt;alert(1)&lt;/script&gt;”"), html);
        assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt; &amp; &quot;quoted&quot;"), html);
        // one script element: the report's own
        assertEquals(1, html.split("<script>", -1).length - 1, html);
    }

    @Test
    void linksEachStepsSnapshotAndTheTrace() {
        String json = RunSummaryTest.EDIT_PERSON
                .replace("\"text\": \"open People\", \"verb\": \"open\",",
                        "\"text\": \"open People\", \"verb\": \"open\", \"snapshot\": \"steps/001.png\",")
                .replace("\"verb\": \"fill\",", "\"verb\": \"fill\", \"snapshot\": \"steps/002.tree.txt\",");
        String html = new RunReport(new RunSummary("Playwright", List.of(scenario(json, "EditPersonTest/editsAPerson")),
                0, null), s -> List.of("EditPersonTest/editsAPerson/trace.zip")).html();

        assertTrue(html.contains("<a class=\"snap\" href=\"EditPersonTest/editsAPerson/steps/001.png\" data-image>"
                + "screenshot</a>"), html);
        // a component tree links its readable page, which the script opens under the step
        assertTrue(html.contains("<a class=\"snap\" href=\"EditPersonTest/editsAPerson/steps/002.tree.html\" "
                + "data-tree>tree</a>"), html);
        assertTrue(html.contains("a.snap[data-image],a.snap[data-tree]"), html);
        // a passing scenario links its trace too, and the trace is not shown as failure evidence
        assertTrue(html.contains(" · <a href=\"EditPersonTest/editsAPerson/trace.zip\""), html);
        assertFalse(html.contains("class=\"evidence\""), html);
    }

    /** The checkout scenario, its steps with screenshots: one boxed, one whose target is gone; its failure boxed. */
    private static String highlighted() {
        String json = RunSummaryTest.CHECKOUT_FAILED
                .replace("\"stepNumber\": 2,", "\"stepNumber\": 2, \"highlight\": {\"x\": 0.1, \"y\": 0.25, "
                        + "\"width\": 0.3, \"height\": 0.05},")
                .replace("\"text\": \"open Checkout\", \"verb\": \"open\",", "\"text\": \"open Checkout\", \"verb\": "
                        + "\"open\", \"snapshot\": \"steps/001.png\", \"highlight\": {\"x\": 0.5, \"y\": 0.125, "
                        + "\"width\": 0.25, \"height\": 0.0625},")
                .replace("\"verb\": \"click\",", "\"verb\": \"click\", \"snapshot\": \"steps/002.png\", "
                        + "\"highlight\": {\"missing\": \"gone\"},");
        return new RunReport(new RunSummary("Playwright", List.of(scenario(json, "CheckoutTest$AsGuest/paysByCard")),
                0, null), s -> List.of("demo.CheckoutTest$AsGuest.paysByCard.png")).html();
    }

    @Test
    void aStepsScreenshotLinkCarriesItsStepTargetsBoxOrWhyThereIsNone() {
        String html = highlighted();

        assertTrue(html.contains("<a class=\"snap\" href=\"CheckoutTest$AsGuest/paysByCard/steps/001.png\" "
                + "data-image data-highlight=\"0.5 0.125 0.25 0.0625\">screenshot</a>"), html);
        assertTrue(html.contains("<a class=\"snap\" href=\"CheckoutTest$AsGuest/paysByCard/steps/002.png\" "
                + "data-image data-missing=\"gone\">screenshot</a>"), html);
    }

    @Test
    void theFailuresScreenshotShowsTheSpotlightInline() {
        String html = highlighted();
        String evidence = html.substring(html.indexOf("<div class=\"evidence\">"));

        assertTrue(evidence.startsWith("<div class=\"evidence\">\n<a class=\"shot\" "
                + "href=\"demo.CheckoutTest$AsGuest.paysByCard.png\" data-image data-highlight=\"0.1 0.25 0.3 0.05\">"
                + "<img src=\"demo.CheckoutTest$AsGuest.paysByCard.png\""), evidence);
        assertTrue(evidence.contains("<span class=\"spot\" style=\"left:10%;top:25%;width:30%;height:5%\"></span>"
                + "</a>"), evidence);
    }

    @Test
    void aReportWithHighlightsCanTurnThemOff() {
        assertTrue(highlighted().contains("<label><input type=\"checkbox\" id=\"highlight\" checked> "
                + "highlight step targets</label>"));
        assertFalse(report(null).contains("id=\"highlight\""), "no highlights, no switch");
    }

    @Test
    void showsAFailuresComponentTreeInThePageWithItsTextLinked() {
        String html = new RunReport(new RunSummary("Browserless", run(), 0, null),
                s -> s.testMethod().equals("paysByCard") ? List.of("demo.CheckoutTest$AsGuest.paysByCard.tree.txt")
                        : List.of()).html();

        assertTrue(html.contains("<iframe class=\"tree-frame\" src=\"demo.CheckoutTest$AsGuest.paysByCard.tree.html\""),
                html);
        assertTrue(html.contains("<a href=\"demo.CheckoutTest$AsGuest.paysByCard.tree.txt\">"), html);
    }

    @Test
    void writesDurationsAsPeopleRead() {
        assertEquals("850 ms", RunReport.duration(850));
        assertEquals("1.2 s", RunReport.duration(1200));
        assertEquals("2 min 5 s", RunReport.duration(125_000));
    }
}
