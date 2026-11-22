# Benchmarking the Unified layer

For maintainers and contributors of the Unified API: how to check that a change doesn't make Unified UI Tests slower than the Direct Tests they replace. Spec: [13. Performance](../spec/13-performance.md), which has the scenarios, the method and the budget.

Two checks compare each **Performance Pair**, a Unified UI Test and a Direct Test of the same scenario, in `unified-test-demo`, package `demo.perf`:

- **Round trips per user action**, checked on every pull request that touches the API or an adapter. They are exact, so any increase fails.
- **Time**, against a budget, run on request and once a week. Time on shared runners is noisy, so this check is never required.

Both run through one script, `scripts/bench-unified.sh`, on one Testing Tool at a time. A third, informational run compares two Testing Tools with each other: see [Comparing two Testing Tools](#comparing-two-testing-tools).

## Running the checks locally

```
scripts/bench-unified.sh --tool browserless --counts     # round trips against the committed counts
scripts/bench-unified.sh --tool browserless --time       # time against the budget
```

| Option | What it does |
|---|---|
| `--tool browserless\|playwright\|selenium` | the Testing Tool; required |
| `--counts` | count the round trips per user action and compare them with the committed counts (the default) |
| `--update-counts` | count them 3 times (`--runs N`) and write them as the committed counts |
| `--time` | time each pair: 5 warm-up rounds (`--warmup N`), then 30 measured rounds (`--rounds N`) |
| `--compare FIRST,SECOND` | instead of `--tool`: time both tools in one JVM, see [below](#comparing-two-testing-tools) |
| `--browser PATH` | with `--compare`: the browser binary every side runs |
| `--scenario NAME` | only `smallForm`, `longForm`, `largeGrid` or `comboBox` |
| `--skip-build` | don't install the modules first; use it when they are installed |
| `-- ARGS` | extra Maven arguments, such as `-- -Dvaadin.testing.playwright.executable=/path/to/chromium` |

- **Exit code:** 0 when everything matches or passes, 1 when a count differs or a scenario fails the time budget, 2 when a test failed.
- **How long:**
  - Browserless takes seconds.
  - Playwright takes a few minutes for the counts, and 10 to 15 for the time.
  - Selenium needs the TestBench licence (`VAADIN_OFFLINE_KEY` or a local key).

The Performance Pairs themselves run like other tests: `mvn verify -Pperformance -Dvaadin.testing.tool=…` in `unified-test-demo`. The normal run leaves them out.

## Reading the round-trip check

The check prints a table. Only the counts that differ are shown first, and the full table is folded below them. In CI the table also goes to the job summary of the **Round trips** workflow.

```
| Test | User action | Counter | Committed | Measured | Result |
| `UnifiedPerfTest.smallForm:33` | `page.find(UnifiedButton.class).withText("Save").click();` | roundTrips | 2 | 3 | **more** (+1) |
```

- **Test:** the test class, method and source line that made the calls. A line in a loop adds up every pass.
- **Counter:** per Testing Tool.
  - Playwright: `calls`.
  - Selenium: `commands`.
  - Browserless: `lookups`, `walks` and `roundTrips`.
  - `waitPolls` rows, the waits whose number depends on timing, are listed but never checked.
- **Result:**
  - **more**: the change adds round trips to that user action, and the check fails.
  - **fewer**: the change removes some. It fails too, so that the committed counts follow the improvement.
  - **new**: a user action that isn't in the committed counts, for example after a Performance Pair was edited. It fails until the counts are updated.

### When a count changes

| The change… | Do |
|---|---|
| removes round trips | run `--update-counts` on that tool and commit `unified-test-demo/perf/round-trips/<tool>.txt` in the same pull request |
| edits a Performance Pair (lines move) | the same: the counts are keyed by source line |
| adds round trips on purpose | edit the committed count by hand, and say why in the pull request; never let `--update-counts` raise it unnoticed |
| adds round trips by accident | fix the change |

Selenium's counts can only be measured where the TestBench licence is. In a pull request, the failing **Round trips (selenium)** job's table gives the new count: copy it into `selenium.txt`.

## Reading the time benchmark

```
| Scenario | Direct (median) | Unified (median) | Ratio | 95 % interval | Extra per user action | Budget | Result |
| largeGrid | 5.1 ms | 9.5 ms | 1.839 | 1.676 to 1.918 | 4.4 ms | 1.389 | **fail** |
```

- **Ratio:** the median, over the measured rounds, of the Unified UI Test's time divided by the Direct Test's in the same round. Only the test body is timed.
- **95 % interval:** where the median ratio would land on another run, from a bootstrap.
- **Budget:** 1.10, or 1 + 2 ms × the scenario's user actions ÷ the Direct Test's median, whichever is larger. Browserless's fast tests get the 2 ms rule; the browsers' slower ones get the 10 %.
- **Result:**
  - **pass**: the median is within the budget.
  - **warn**: the median is over, but the interval still reaches below the budget. Run it again, or with more rounds.
  - **fail**: the whole interval is over the budget.

The times are written to `unified-test-demo/target/benchmark/<tool>.csv`, one line per timed test body, and the table to `<tool>.md`. In CI both are in the run's `benchmark-<tool>` artifact.

### Running it in CI

| How | When to use it |
|---|---|
| Add the label `benchmark` to a pull request | a change that could be slower. The benchmark runs when the label is added and on each push while it's there. |
| Actions → **Benchmark** → Run workflow | any branch, with a number of measured rounds |
| Nothing | it runs every Monday on `main`: the record over time |

Each Testing Tool has its own job, in parallel, of at most 30 minutes. A failing scenario fails that job, which is not a required check. Look at its table, and when the result holds on a second run, open an issue for the overhead.

## Comparing two Testing Tools

The time benchmark compares a Unified UI Test with the Direct Test on **one** Testing Tool. Its jobs run on different runners, so their times can't be compared across tools. To compare the tools themselves, for example Playwright with Selenium ([#497](https://github.com/parttio/unified-testing-tool/issues/497)), run both in one JVM:

```
scripts/bench-unified.sh --compare playwright,selenium --browser "$(command -v google-chrome)"
```

- **What runs:** each round runs four test classes: the Unified UI Test and the Direct Test on each tool. They share one application server and one machine. The order of the tools alternates round by round (ABBA), and so does the order of the two tests on a tool.
- **`--browser`:** every side runs that binary. That covers both Unified adapters (`vaadin.testing.playwright.executable`, `vaadin.testing.selenium.executable`), `DramafinderPerfTest` and `TestBenchPerfTest`. Without it, each tool runs its own default browser, and the table says so.
- **What is timed:** the test body only, as in the time benchmark. The browser's launch is not included.
- **Result:** per scenario, two rows:
  - the Unified UI Test on both tools;
  - the Direct Tests, for example `DramafinderPerfTest` vs `TestBenchPerfTest`.

  Each row gives both medians, the median of the paired ratios SECOND ÷ FIRST and its 95 % interval. Above 1 means the second tool is slower. There is no budget and no verdict.
- **Output:** `unified-test-demo/target/benchmark/compare-FIRST-SECOND.csv` and `.md`. In CI the job summary has the table, and the `benchmark-compare` artifact has both files.

For example, from a short local run of `--compare browserless,playwright`:

```
| Scenario | Tests | browserless (median) | playwright (median) | playwright ÷ browserless | 95 % interval |
| longForm | Unified UI Test | 24.3 ms | 2400.5 ms | 99.675 | 91.603 to 145.563 |
| longForm | BrowserlessPerfTest vs DramafinderPerfTest | 23.8 ms | 2748.6 ms | 111.712 | 109.276 to 176.044 |
```

**Reading it:**
- The **Direct Tests** row compares the tools as their own users write tests.
- The **Unified UI Test** row also includes this repo's adapters, so it changes when an adapter gets faster.
- Selenium needs the TestBench licence.

**In CI:** the **Benchmark** workflow's job *Compare Playwright and Selenium* runs it on the runner's Google Chrome:
- every Monday;
- on request;
- on a pull request labelled `benchmark`.

It takes up to 60 minutes, and it is never a required check.

