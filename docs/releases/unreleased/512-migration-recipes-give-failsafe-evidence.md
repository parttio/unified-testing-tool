---
section: New
---
- **The migration recipes give Failsafe its own evidence directory**
  ([#512](https://github.com/parttio/unified-testing-tool/issues/512)): every `maven-failsafe-plugin` in
  `<build><plugins>` and in each profile gets `vaadin.testing.evidence-dir` =
  `${project.build.directory}/failsafe-reports/unified-evidence`, unless it sets one already, so a build with
  Surefire and Failsafe no longer mixes both runs in one `summary.md`. The guide's CI examples upload
  `**/target/*-reports/unified-evidence/`.
