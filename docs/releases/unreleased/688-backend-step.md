---
section: New
---
- **Backend Steps: checks outside the UI that the run summary counts**
  ([#688](https://github.com/parttio/unified-testing-tool/issues/688)): `page.backendStep("order is stored", () -> ..)`
  runs the test's own work outside the UI (the database, a file, a mail) once. The reports show it among the User
  Steps as `backend: order is stored`, with any User Steps it calls under it, and `user-steps.json` gains the entry
  type `backend`. If the work throws, the Backend Step and the test fail. The run summary counts a Backend Step as a
  check, so a scenario that ends by checking the database is no longer listed under *Scenarios without a check*. The
  line about scenarios that used the Escape Hatch now reads `<n> passing scenarios used the Escape Hatch and end on
  User Steps the report sees no check for`. It points to `assertThat(..)` and `page.backendStep(..)`, and no longer to
  Migration Markers, which the run can't see
  ([ADR 0023](../adr/0023-a-check-outside-the-ui-is-a-backend-step.md),
  [guide](../guide/assertions-and-waiting.md#checking-outside-the-ui)).
