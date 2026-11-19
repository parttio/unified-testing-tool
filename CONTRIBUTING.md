# Contributing

How to build this repository, preview its documentation and cut a release. To use the Unified API in your own project, start from the [README](README.md).

## Build and run

Java 21, Maven.

```
mvn install                                              # everything; demo tests on Browserless
mvn test -pl unified-test-demo -Dvaadin.testing.tool=playwright
VAADIN_OFFLINE_KEY=... mvn test -pl unified-test-demo -Dvaadin.testing.tool=selenium   # installed Chrome
```

TestBench checks its license when it loads: on CI, the `selenium` job reads the `VAADIN_OFFLINE_KEY` secret. Browserless and Playwright runs never load TestBench, even with the Selenium adapter on the classpath.

The Playwright run uses Playwright's own browser download. To use an installed Chromium instead:

```
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 mvn test -pl unified-test-demo \
    -Dvaadin.testing.tool=playwright \
    -Dvaadin.testing.playwright.executable=/path/to/chrome
```

## Documentation site

The `Docs` workflow publishes `docs/`, `README.md` and `GLOSSARY.md` to GitHub Pages on every push to `main`. To preview it locally:

```
pip install 'mkdocs-material==9.*'
scripts/build-docs.sh        # builds target/site
```

The build fails on a broken link, a missing snippet, or pages that name different release versions ([Cutting a release](#cutting-a-release)).

### Code examples

Put a Java example in a test of `unified-test-demo` and include it with `--8<--`, so that CI compiles it and runs it on every Testing Tool:

```java
// in unified-test-demo/src/test/java/demo/ProfileViewTest.java
// --8<-- [start:savesName]
@Test
void savesName(UnifiedPage page) { ... }
// --8<-- [end:savesName]
```

````markdown
```java
--8<-- "unified-test-demo/src/test/java/demo/ProfileViewTest.java:savesName"
```
````

A short example that the demo app can't run, because it shows buttons or views the demo doesn't have, goes in [`GuideExamples`](unified-test-demo/src/test/java/demo/GuideExamples.java) instead: CI compiles it but doesn't run it, so an API change still breaks the build rather than the guide.

Write an example inline only when it can't compile: a signature, a class skeleton, or a mistake to avoid.

## Cutting a release

1. Make sure the build passes locally and in the Tests workflow on `main`.
2. Turn `docs/releases/unreleased.md` into the release's notes (`docs/releases/<version>.md`, and its entry in `mkdocs.yml`), start a new `unreleased.md`, and take out the marks of what the release now has: `grep -rni "unreleased" README.md docs/guide docs/spec scripts`.
3. From an up-to-date `main` with push rights:

   ```
   mvn release:prepare release:clean -DreleaseVersion=<version> -DdevelopmentVersion=0.1.0-SNAPSHOT
   ```

   This runs the tests, sets the version in every module, commits, tags `unified-test-<version>`, moves to the next snapshot and pushes. `release:perform` is not needed: the tag starts the Release workflow, which publishes every module except the demo to Maven Central (`mvn -Prelease deploy`, the same secrets as Dramafinder). Releases appear on Central within about half an hour.

4. Once the release is on Central, point the documentation at it, and push:

   ```
   scripts/doc_version.py --update
   ```

   The version the README, the migration guides and the developer guide name comes from the newest file in `docs/releases/`. Until you run this, the Docs workflow warns that the documentation names the previous release.
