---
status: accepted
---

# The lookup that enters a Template names its class from the application's packages

The Findable View and the Near Misses name a Template by the lookup that enters it. A browser knows the Template only by its tag, so that lookup named a class only once the test had used it (`findTemplate(SearchBar.class)` or a `TemplateScope` Wrapper). Until then they printed `findTemplate(<the class with @Tag("search-bar")>.class)`: the test had to look the class up itself, on the first failure, when the view was meant to say (#616, from the Bakery migration, #602). Decided in #616:

- **The class comes from the application's own packages.** A Template's tag is looked up among the classes in those packages that extend `Component`, aren't abstract, and declare `@Tag` themselves. A class that only inherits its tag from another doesn't count.
- **The scan is lazy.** It runs only when a Template's tag matches no Wrapper and no class the test has used, so a passing test never pays for it. It runs once per JVM for each set of packages, and its result is kept. ClassGraph reads the class files without loading or initialising the classes.
- **The application's packages** are the test class's package, the packages of the `@SpringBootApplication` (Spring Boot's auto-configuration packages, in a Spring test), the packages named by `vaadin.testing.browserless.view-packages`, and the packages of the routes the test opens with `navigate(X.class)`, each with its subpackages. Every Testing Tool reads them from the test, not from the page, so the lookup is the same on every Testing Tool.
- **Otherwise the placeholder stays.** That covers a tag that two classes in those packages declare, a tag that none of them declares, and an application whose classes aren't on the test classpath. A Wrapper or class the test has used still comes first.

## Considered Options

- **Scan at startup.** Same answer, but every run pays for the scan, even one that never prints a Template.
- **The route registry, or the fields of the route class.** No scan, but it finds only route targets and the Templates they keep in fields. The demo's `OrdersView` creates its `OrdersPanel` as a local variable.
- **Flow's own tag lookup.** Flow has no tag-to-class index at runtime. Its frontend scanner runs at build time or in the dev server, which Browserless doesn't run.
- **The component's own class on Browserless.** Browserless holds the component, but the lookup would then differ between Testing Tools.
- **The whole class path.** It finds Templates anywhere, including in add-ons, at the cost of a slower scan.
- **Naming every candidate for a shared tag.** More helpful, but `findTemplate(A.class or B.class)` isn't a lookup that runs.

## Consequences

- `unified-test-api` depends on `io.github.classgraph:classgraph`, which `browserless-test-shared` already brings.
- The first `describe()` or Near Miss that meets an unknown Template's tag scans the application's packages, which takes a fraction of a second.
- A Template class outside those packages, in an add-on or a library, still gets the placeholder until the test uses it.
