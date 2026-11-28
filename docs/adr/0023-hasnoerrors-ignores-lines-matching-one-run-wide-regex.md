---
status: accepted
---

# `hasNoErrors()` ignores the lines that match one run-wide regex; no HTTP status check yet

`assertThat(page).hasNoErrors()` (#289) fails on every error line the test has so far: the application's `ERROR` lines and the browsers' console errors. The only exception is the browser's own request for `/favicon.ico`, which is hard-coded. A suite run in parallel against copies of the application in Docker (#682) gets Chromium's `Failed to load resource: net::ERR_NETWORK_CHANGED` whenever another copy's network is created, and failed at random steps. It had no way to say "this line is not my application's fault", so it kept its own console listeners behind the Escape Hatch. We decide that `vaadin.testing.user-steps.ignored-errors` holds one Java regex, and `hasNoErrors()` doesn't count a line that the regex finds anywhere in the line's text as the reports print it (`console ERROR: Failed to load resource: net::ERR_NETWORK_CHANGED`, `server ERROR PoolMonitor: …`).

## Decisions

- **One setting for the whole run, no per-call form.** Noise like `ERR_NETWORK_CHANGED` comes from where the suite runs, not from one test: the reporter would otherwise repeat the list in 62 workflows. A test that expects an error of its own has no need for `hasNoErrors()`.
- **A regex, found anywhere in the printed text.** The text starts with the source and the level, so `^console ERROR` or `^server ERROR \w+Monitor:` limits a pattern to one source, and `|` joins alternatives. A comma-separated list of substrings would break on a message that holds a comma. The same text is in the failure and in `user-steps.txt`, so what the reader sees is what they match. The regex is checked when the test starts: an invalid one fails every test and names the setting.
- **Server lines and console lines alike.** A known server line, such as a pool's warning logged at `ERROR` on every start, is as much noise as a console line.
- **An ignored line still shows under its User Step.** Only the assertion skips it. The reports stay a record of what was logged, and a line ignored by mistake can still explain a failure. `vaadin.testing.user-steps.server-log` stays the only setting that keeps a line out of the reports.
- **No HTTP status check in this change.** Chromium already logs most failed loads to the console (`Failed to load resource: the server responded with a status of 500`). Vaadin Flow's UIDL requests answer `200` even when a listener throws, so the server log covers that case. An HTTP check needs `onResponse` on Playwright and CDP or the performance log on Selenium, and Browserless has no HTTP. It is left to its own issue.

## Considered Options

- **A per-call `hasNoErrors().ignoring(..)`**: `hasNoErrors()` checks at once, so a trailing call comes too late. It would have to be a new method, such as `hasNoErrors(Pattern...)`. It can be added later if a test needs it; the setting is what #682 needs.
- **Dropping ignored lines when they are recorded**, the way `/favicon.ico` is: quieter reports, but the evidence loses lines, and the setting would act on more than `hasNoErrors()`.
- **Matching the message only**, without the source and level: a pattern could not tell a console line from a server line.
