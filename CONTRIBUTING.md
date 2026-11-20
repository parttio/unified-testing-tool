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

The Playwright run downloads the one browser it launches, the first time: Chromium's headless shell, about 280 MB. To use an installed Chromium instead, which skips the download:

```
mvn test -pl unified-test-demo \
    -Dvaadin.testing.tool=playwright \
    -Dvaadin.testing.playwright.executable=/path/to/chrome
```

## Documentation site

The `Docs` workflow publishes `docs/`, `README.md` and `GLOSSARY.md` to GitHub Pages on every push to `main`. To preview it locally:

```
pip install 'mkdocs-material==9.*'
scripts/build-docs.sh        # builds target/site
```

The `Links` workflow checks the links to other sites, on pull requests that change the documentation and weekly. The build fails on a broken link between pages, a missing snippet, or pages that name different release versions ([Cutting a release](#cutting-a-release)).

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

The guide's rules of thumb are in `docs/snippets/rules-of-thumb.md`, included by the guide overview and written at the top of `llms.txt` by `scripts/llms_txt.py`. Change them there, and keep the `write-unified-ui-tests` skill in line with them.

Write an example inline only when it can't compile: a signature, a class skeleton, or a mistake to avoid.

### What is unreleased

The site documents `main`, so mark what the latest release doesn't have, where a reader of that release meets it. Mark the item, not its surroundings: a row, a bullet or a sentence gets `*(unreleased)*` next to its name. Only a section whose every line is new gets the banner instead:

```markdown
> **Unreleased:** on `main`, not in `0.1.0-alpha4`. See [what is unreleased](../releases/unreleased.md).
```

A section that keeps released content under a new heading, or a sentence that names a released and an unreleased item, is marked per item, so a reader of the release can tell which half they have.

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
