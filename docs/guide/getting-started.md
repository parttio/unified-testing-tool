# Getting started

From a Vaadin Flow application to a Unified UI Test that passes on Browserless and in a browser. You need Java 21, Maven, Vaadin 25.3 or later, and JUnit 6 (Jupiter).

## 1. Add the dependencies

Import the BOM and add the adapters of the Testing Tools you run on, as test dependencies:

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
    <!-- Spring Boot applications: @UnifiedSpringBootTest -->
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
    <!-- the Testing Tools -->
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

| Dependency | When |
|---|---|
| `unified-test-spring`, `browserless-test-spring` | Spring Boot applications. Without `browserless-test-spring`, a Spring test fails at once on Browserless. |
| `unified-test-browserless` | Runs in the test JVM, no browser. The default Testing Tool. |
| `unified-test-playwright` | Runs in a real browser through Dramafinder. Brings `vaadin-dev`, which browser runs need. |
| `unified-test-selenium` | Runs in a real browser through TestBench. Needs a TestBench license. |
| `unified-test-archunit` | Optional: [checks the style](style-rules.md) of your tests. |

The BOM sets the versions of all of them, and of Browserless Test and Dramafinder. Alphas can change the API between them: use one version for every `org.vaadin.addons` artifact.

## 2. Write a test

A test takes a `UnifiedPage`, opens a view, finds components the way a user does, acts on them and checks what the user sees:

```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:savesName"
```

in a class like this:

```java
import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@UnifiedSpringBootTest          // @UnifiedUITest for an app without Spring Boot
class ProfileViewTest {
    // tests take a UnifiedPage parameter
}
```

!!! tip "Import `assertThat` from `UnifiedAssertions`"
    `UnifiedAssertions.assertThat(wrapper)` retries until the timeout; AssertJ's or JUnit's assertions don't. Import it statically next to your other assertion libraries: it only accepts Unified API types.

## 3. Run it

```
mvn test                                         # Browserless, the default
mvn test -Dvaadin.testing.tool=playwright        # the same test in a real browser
mvn test -Dvaadin.testing.tool=selenium          # the same, on TestBench
```

- **Playwright** downloads its browser on the first run. To use an installed one, set `vaadin.testing.playwright.executable` ([Configuration](configuration.md)).
- **Selenium** drives the installed Chrome or Firefox, and runs TestBench, which checks its license when it loads. Browserless and Playwright runs never load TestBench, even with the Selenium adapter on the classpath.
- **A failing test** says which User Step failed and what was there instead ([Reading reports and failures](reports-and-failures.md)).

## Without Spring Boot

Mark the class `@UnifiedUITest` instead of `@UnifiedSpringBootTest`, and leave out the two Spring dependencies. The Unified API never starts a server itself:

| Testing Tool | Finds the application |
|---|---|
| Browserless | Builds it in the test JVM from the routes in the test's package, or in the packages of `vaadin.testing.browserless.view-packages` ([Configuration](configuration.md)). |
| Playwright, Selenium | At `vaadin.testing.base-url` (default `http://localhost:8080`), on a server your build starts first, for example with Failsafe and `jetty:start`. |

More in [2. Test setup](../spec/02-test-setup.md#reaching-the-application).

## Next

- [Developer guide](index.md): the four steps of every test, and a page for each.
- [Running in CI](ci-setup.md): one job per Testing Tool, with the run summary on the job's page.
- Existing tests: migrate them [from TestBench](../migrating-from-testbench.md), [from Browserless](../migrating-from-browserless.md) or [from Dramafinder](../migrating-from-dramafinder.md).
