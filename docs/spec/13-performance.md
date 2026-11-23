# 13. Performance

Decided on the map [Performance: show a Unified UI Test is not slower than the same Direct Test](https://github.com/parttio/unified-testing-tool/issues/294). Terms: **Direct Test** and **Performance Pair** in [GLOSSARY.md](../../GLOSSARY.md). How to run and read the benchmark: [Benchmarking the Unified layer](../guide/performance-benchmark.md).

A Unified UI Test runs on a Testing Tool, through the Unified layer: Wrappers that find their component again on every use, Auto-Wait, User Step recording. This chapter says how that layer's cost is measured, how much it may cost, and what happens when a change goes over.

## What is compared

The benchmark compares **Performance Pairs**: a Unified UI Test and a Direct Test of the same scenario on one Testing Tool ([#303](https://github.com/parttio/unified-testing-tool/issues/303)). Each scenario has one Unified UI Test, which runs on every Testing Tool, and a Direct Test on each Testing Tool: Browserless Test, Dramafinder and TestBench.

| Scenario | View | User actions | Check | What it shows |
|---|---|---|---|---|
| `smallForm` | Profile | fill Name, fill Email, click Save | the notification | the fixed cost per action |
| `longForm` | `perf/long-form` | fill 24 text fields, 2 integer fields, 2 date pickers, check 2 checkboxes, click Save | "Saved 30 of 30 fields" | how the cost grows with the number of actions |
| `largeGrid` | `perf/large-grid`, 500 rows | find the row "Person 250" and click it | the editor's Name and Email | how it grows with lookups in a large component |
| `comboBox` | `perf/combo-box`, 1,000 lazy items | pick "Item 0742" | "Picked: Item 0742" | overlays and lazily loaded items |

The pairs are in `unified-test-demo`, package `demo.perf`: `UnifiedPerfTest`, `BrowserlessPerfTest`, `DramafinderPerfTest` and `TestBenchPerfTest`, with one method per scenario. They are tagged `performance`, so the normal test run leaves them out.

The demo's golden migration pairs are not Performance Pairs. They are fixtures for the Migration Recipes, and most of them keep Migration Markers on purpose.

### The fairness rule

1. The same user actions and checks, in the same order.
2. Each Direct Test is written the way a skilled user of its tool would write it, with the waits that tool needs. It is not stripped down.
3. The Unified UI Test uses no Escape Hatch and has no Migration Markers.
4. The browser starts and stops the same way on both sides. Only the test body is timed. On a browser, the body starts in a new browser context with nothing of the application loaded yet: the first page load in a context is the slowest, so it belongs inside the body on both sides. Dramafinder's `SpringPlaywrightIT` loads the application before each test, so its Direct Test opens a new page after that setup ([#444](https://github.com/parttio/unified-testing-tool/issues/444)).
5. Settings stay at their defaults.

## What is measured

Two measures, kept apart because they behave differently on shared CI runners ([#300](https://github.com/parttio/unified-testing-tool/issues/300), [#301](https://github.com/parttio/unified-testing-tool/issues/301)):

| Measure | What | Varies between runs |
|---|---|---|
| **Round trips per user action** | Playwright: Java-to-driver calls. Selenium: WebDriver commands. Browserless: lookups, tree walks from the UI, and simulated server round trips. Each one is credited to the test's source line that made it. | no, except waits (below) |
| **Time** | the test body's time, as the ratio of the Unified UI Test's to the Direct Test's | yes: on a shared runner, only the ratio within one run compares |

**Waits are counted apart** (`waitPolls`): calls repeated until something is ready, whose number depends on timing. They are TestBench's `waitForVaadin` polls, Playwright's `waitForSelector` and the `dispose` of its handle, and reads of a lazy drop-down's `loading` state. They are reported but not checked.

### How time is measured

- **Setup:** one JVM and one job per Testing Tool, the Unified UI Test and the Direct Test of each scenario run by turns.
- **Order:** ABBA, the pair's order alternating round by round, so a drift during the run hits both sides.
- **Rounds:** 5 warm-up rounds, then 30 measured.
- **What is timed:** only the test method's body, not the extensions' setup, the browser's launch or the reports written after it.
- **Result:** per scenario, the median of the paired ratios (Unified ÷ Direct, round by round) and its bootstrap 95 % interval, from 4,000 resamples with a fixed seed.

## The budget

One budget for all Testing Tools ([#304](https://github.com/parttio/unified-testing-tool/issues/304)).

### Time

Per Performance Pair, the Unified UI Test may take **at most 10 % longer than the Direct Test, or at most 2 ms more per user action, whichever is larger**. A ratio alone would fail Browserless's millisecond tests on a few milliseconds. A limit in milliseconds alone would mean something different on each tool.

| Result | When |
|---|---|
| **pass** | the median ratio is within the budget |
| **warn** | the median is over the budget, but the 95 % interval reaches below it |
| **fail** | the whole 95 % interval is over the budget |

### Round trips: a ratchet

The round trips per user action of every Performance Pair, on every Testing Tool, are committed in `unified-test-demo/perf/round-trips/<tool>.txt` (the "committed round-trip counts").

- A count above the committed one fails.
- A count below it fails too, so the file is lowered in the same pull request (`--update-counts`).
- A count goes up only by an explicit edit of the file, with the reason in the pull request.
- A count that varied between the runs that wrote it is written as a range, `min-max`, and checked against its max only.

## Where and when it runs

Two checks, and one informational comparison, with different costs and different reliability ([#305](https://github.com/parttio/unified-testing-tool/issues/305)):

| Check | When | Where | Fails |
|---|---|---|---|
| **Round trips** | every pull request and push to `main` that touches the API, an adapter module, the Performance Pairs, their counts or the scripts | workflow `round-trips.yml`, one job per Testing Tool, about 1 to 4 minutes each | the pull request, when a count differs from the committed one |
| **Time** | by hand; on a pull request labelled `benchmark`; Mondays on `main` | workflow `benchmark.yml`, one job per Testing Tool in parallel, at most 30 minutes; a job that hits the limit fails as unfinished, with no verdict | its own check, which is never required |
| **Tool comparison** (informational, [#497](https://github.com/parttio/unified-testing-tool/issues/497)) | with the time check | workflow `benchmark.yml`, job *Compare Playwright and Selenium*: both tools in one JVM, on the runner's Chrome, at most 60 minutes | never: no budget, only the ratio Selenium ÷ Playwright and its interval |

- **Results:** both put their table in the job summary. The time benchmark also uploads every timed body as a CSV artifact. There is no PR comment and no trend store: the weekly `main` runs are the record.
- **Forks:** on a pull request from a fork there is no TestBench licence, so the Selenium jobs are skipped with a line in the job summary.
- **Locally:** `scripts/bench-unified.sh --tool browserless|playwright|selenium` with `--counts`, `--update-counts` or `--time`.

## Out of scope

- **Fixing the overheads found:** each one is its own issue. The round-trip check and the benchmark show the result.
- **Memory, CPU and start-up time** (Spring context, browser launch): shared by both sides, and cached.
- **Comparing the Testing Tools with each other:** the question is the Unified layer's cost on each tool, not which tool is fastest.
