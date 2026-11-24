---
section: Changed behaviour
---
- **Failure evidence is in the test's own evidence directory** ([#514](https://github.com/parttio/unified-testing-tool/issues/514)):
  `<evidence dir>/<test class>/<test method>/failure.png` (browsers) or `failure.tree.txt` (Browserless), and
  `failure.view.txt`, next to `user-steps.txt`, instead of `<fully qualified class>.<method>.*` at the evidence
  directory's root. The failure's `Evidence` line names the directory and the files,
  `…/EditPersonTest/editsAPerson/  (failure.png, failure.view.txt)`, `user-steps.json`'s `failure` lists them as
  `evidence`, and `summary.json`'s per-scenario `evidence` paths become `EditPersonTest/editsAPerson/failure.png`.
  `UnifiedAssertionError.getEvidenceFiles()` returns the names. A script that read the root files reads the test's
  directory instead; a passing test leaves no `failure.*` there.
