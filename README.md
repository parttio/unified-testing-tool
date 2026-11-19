# Unified API for Vaadin UI tests

Write a Vaadin Flow UI test **once**, run it on **Browserless** (in the JVM, no browser), on
**Playwright** through [Dramafinder](https://github.com/parttio/dramafinder) 2.0, or on **Selenium**
through TestBench, chosen per run.

```java
@UnifiedSpringBootTest
class ProfileViewTest {

    @Test
    @DisplayName("The user saves a name and an email and sees a confirmation")
    void savesName(UnifiedPage page) {
        page.navigate(ProfileView.class);

        UnifiedTextField name = page.find(UnifiedTextField.class).withLabel("Name");
        name.setValue("Alice");
        page.find(UnifiedTextField.class).withLabel("Email").setValue("alice@example.com");
        page.find(UnifiedButton.class).withText("Save").click();

        assertThat(page.find(UnifiedNotification.class)).isOpen().hasText("Saved Alice");
        assertThat(name).hasValue("Alice").isValid();
    }
}
```

- **Documentation site:** https://parttio.github.io/unified-testing-tool/
- **Migrating from TestBench:** [docs/migrating-from-testbench.md](docs/migrating-from-testbench.md), OpenRewrite Migration Recipes.
- **Migrating from Browserless:** [docs/migrating-from-browserless.md](docs/migrating-from-browserless.md), the same for Browserless Test and TestBench UI Unit tests.
- **Migrating from Dramafinder:** [docs/migrating-from-dramafinder.md](docs/migrating-from-dramafinder.md), the same for Dramafinder (Playwright) tests.
- **Getting started:** [docs/guide/getting-started.md](docs/guide/getting-started.md): from the dependencies to a first test that passes on Browserless and in a browser.
- **Developer guide:** [docs/guide](docs/guide/index.md): how to write tests, structure scenarios and read their reports, with examples from the demo.
- **API reference *(unreleased)*:** [every Wrapper](unified-test-api/src/main/resources/META-INF/unified/api-reference.md) with its filters, actions, reads and checks, generated from the code and shipped in the `unified-test-api` jar (`META-INF/unified/api-reference.md`).
- **For agents:** the site has an [`llms.txt`](https://parttio.github.io/unified-testing-tool/llms.txt), every page as Markdown, and the whole guide in [`llms-full.txt`](https://parttio.github.io/unified-testing-tool/llms-full.txt); the `write-unified-ui-tests` skill is installed by the `AddUnifiedTestingSkill` recipe.
- **Configuration:** [docs/guide/configuration.md](docs/guide/configuration.md): every setting, such as the Testing Tool, the timeout and the reports.
- **Specification:** [docs/spec](docs/spec/README.md). Decisions: the map [Unified testing API for Vaadin](https://github.com/parttio/unified-testing-tool/issues/2), ADRs in [docs/adr](docs/adr), terms in [GLOSSARY.md](GLOSSARY.md).
- **Release notes:** [docs/releases](docs/releases/unreleased.md). This page and the site describe `main`: what is newer than the latest release is marked *(unreleased)* and listed under [Unreleased](docs/releases/unreleased.md).
- **Status:** first version. Spec draft, not yet `spec-v1`.

## Modules

| Module | Contains |
|---|---|
| `unified-test-api` | `@UnifiedUITest`, `UnifiedPage`, Wrappers (`UnifiedTextField`, `UnifiedNumberField`, `UnifiedDatePicker`, `UnifiedCheckbox`, `UnifiedComboBox`, `UnifiedRadioButtonGroup`, `UnifiedMultiSelectComboBox`, `UnifiedButton`, `UnifiedNotification`, `UnifiedDialog`, `UnifiedGrid`, `UnifiedTabSheet`, `UnifiedContainer`, `findComponent`), users and windows (`@WithMockUser`, `openWindow`, `openAsUser`; browsers log in through a test-only endpoint), retrying `UnifiedAssertions`, User Step reports (`page.step(..)`, `user-steps.txt`, `summary.md`, `report.html` and, *unreleased*, `summary.json`, the application's log lines and the console's errors under each step, scenarios without a check, the comparison with another Testing Tool's run) and `UnifiedAssertionError`, Capabilities, Escape Hatch, adapter SPI, the API reference (`META-INF/unified/api-reference.md`, *unreleased*) |
| `unified-test-browserless` | Browserless Test adapter |
| `unified-test-playwright` | Playwright adapter on Dramafinder 2.0 |
| `unified-test-selenium` | Selenium adapter on TestBench 25.3 (needs a TestBench license) |
| `unified-test-spring` | `@UnifiedSpringBootTest`, test-only login endpoint for browsers |
| `unified-test-archunit` | `UnifiedStyleRules`: the Unified UI Test style as ArchUnit rules, opt-in at test scope ([Checking the style](docs/guide/style-rules.md)) |
| `unified-test-bom` | versions of the above and of the Testing Tools |
| `unified-test-demo` | Spring Boot + Vaadin demo app and its Unified UI Tests |
| `unified-test-rewrite` | OpenRewrite Migration Recipes from TestBench, Browserless and Dramafinder to Unified UI Tests ([ADR 0004](docs/adr/0004-test-migration-openrewrite-recipes-then-agent-skill.md), [ADR 0005](docs/adr/0005-browserless-migration-recipes.md)); `ApplyDidYouMean` *(unreleased)*, which writes a failed lookup's `Did you mean` into the test ([Reading reports and failures](docs/guide/reports-and-failures.md#applying-it)) |

## Try it

Alpha releases are on Maven Central as `org.vaadin.addons`, version `0.1.0-alpha3`. The API can still change between alphas. Import the BOM and add the adapters you run on as test dependencies (here for a Spring Boot application; [Getting started](docs/guide/getting-started.md) has the other cases and a first test):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.vaadin.addons</groupId>
            <artifactId>unified-test-bom</artifactId>
            <version>0.1.0-alpha3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.vaadin.addons</groupId>
        <artifactId>unified-test-spring</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.vaadin</groupId>
        <artifactId>browserless-test-spring</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.vaadin.addons</groupId>
        <artifactId>unified-test-browserless</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.vaadin.addons</groupId>
        <artifactId>unified-test-playwright</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Run the tests on Browserless with `mvn test`, and on another Testing Tool with `-Dvaadin.testing.tool=playwright` or `selenium`. The Selenium adapter runs TestBench, which needs a TestBench license; Browserless and Playwright runs never load TestBench, even with the Selenium adapter on the classpath. Every setting: [Configuration](docs/guide/configuration.md).

`unified-test-demo` shows a complete setup. To run the tests in CI, with the run summary on GitHub's job page, see [Running in CI](docs/guide/ci-setup.md).

## Not in this first version

| Area | State |
|---|---|
| Browserless: searching inside Grid row details (`row.details()`) | throws; toggling and checking details works |
| Browserless: `page.reload()`, `pressKey`, `dispatchEvent` | simulated: events bubble from the target to the UI; `page.pressKey` goes to the focused component or the open dialog; client-side behaviour (e.g. typing text, Tab moving focus) doesn't happen ([#66](https://github.com/parttio/unified-testing-tool/issues/66)) |
| Browserless: deselecting one item of a MultiSelectComboBox | done on the server, not as from the client ([#61](https://github.com/parttio/unified-testing-tool/issues/61)) |
| Selenium: a combo box's `getItems()` | the drop-down's first page only (50 items by default; [#127](https://github.com/parttio/unified-testing-tool/issues/127)) |
| The browser's console errors *(unreleased)* (under each User Step, and in `hasNoErrors()`) | Playwright, and Selenium with Chrome; Firefox on Selenium has no browser log ([#316](https://github.com/parttio/unified-testing-tool/issues/316)) |

## Contributing

Building this repository, previewing the documentation site and cutting a release: [CONTRIBUTING.md](CONTRIBUTING.md).
