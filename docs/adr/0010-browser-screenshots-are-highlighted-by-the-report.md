---
status: accepted
---

# Browser screenshots are highlighted by the report, not in the page or the PNG

On Browserless, a step's snapshot is the component tree, with the component the step acted on marked `  ← this step`, and the report's tree page highlights it. A browser's snapshot was a plain screenshot: the reader had to guess which field the step filled. #472 brings the same mark to Playwright and Selenium screenshots. We decide that the adapter measures the Step Target's box and hands it back with the screenshot; the core writes it into `user-steps.json` as fractions of the image; `report.html` draws it. Neither the page nor the PNG is changed.

## Decisions

- **The Step Target is chosen once, in the core.** It is the element the User Step last singled out (`LazyElement.concrete()`), whether to act on it or to check it. The core passes it to `writeStepSnapshot` and, for the failed step, to `writeFailureEvidence`. Every Testing Tool marks the same component, Browserless included, so a check now marks what it checked there too.
- **The adapter returns `Evidence`: its files and a `Highlight`.** It is one call, not a second `targetBox()` afterwards, because the page may change between two round trips. The box is measured when the screenshot is taken, without waiting: an element that is gone or hidden is `gone`; one wholly outside the picture is `outside`. A highlight that can't be measured is left out and never costs the screenshot.
- **Fractions of the image, clipped to it.** `Highlight.of(..)` turns a box in pixels into fractions of the viewport, or of the whole document for Playwright's full-page failure shot. The report draws them in `%` at any size and any device pixel ratio.
- **The report shows a viewer with a spotlight.** A step's screenshot opens over the report with the scenario's other screenshots (filmstrip, ← →, Esc), and everything outside the box is dimmed, with an accent outline and a white halo so it shows on the application's own colours. The failure's screenshot shows the spotlight inline. One switch turns the spotlights off. The look was chosen from a prototype of three variants: inline under the step, a viewer, and a side pane with a spotlight. We took the viewer, with the side pane's spotlight.

## Considered Options

- **Inject an outline into the page before the screenshot, and remove it after.** That is the simplest, and the PNG shows the mark anywhere. We rejected it: taking evidence would change the application's DOM, and an outline clashes with shadow DOM, overlays and focus styles.
- **Draw the box onto the PNG in Java.** Every viewer shows it, but the original picture is lost, and the report could not turn the mark off.
- **A covered-target note** ("covered by another element", for a button under a dialog's backdrop). Deferred: detecting it means hit-testing in the page, extra round trips for a rare case. The spotlight still shows where the target is.

## Consequences

- `AdapterSession.writeStepSnapshot` and `writeFailureEvidence` take the Step Target and return `Evidence`. That changes the SPI of [ADR 0008](0008-adapter-spi-is-test-runner-neutral.md); all its implementations live in this repository.
- The lifecycle writes the Testing Tool's failure evidence before `user-steps.json`, so the failure's `highlight` is in it. The suppressed messages on the failure keep their order.
- A PNG opened on its own shows no mark. The report, and the `highlight` in `user-steps.json` for agents, carry it.
- Browserless's component tree keeps opening under its step: a run uses one Testing Tool, so a report never mixes trees and screenshots.
