# How stable is test timing on GitHub's shared runners?

Research for #301, part of map #294 ("Performance: show a Unified UI Test is not slower than the same Direct Test").

**Question.** How much does the time of the same JUnit test vary on GitHub-hosted `ubuntu-latest` runners, within one job and between jobs? So, how should a benchmark repeat and summarise its runs for a ratio of two tests' times (Unified UI Test over Direct Test) to be trustworthy? And is a ratio measured in one job stable enough to compare across pull requests?

Researched 2026-11-18 against `main` at `6c15e62`. External sources were read that day. This session's network allowed only GitHub (`raw.githubusercontent.com` and the GitHub API), so:

- GitHub's runner documentation was read **from its source**, `github/docs` on `main` (the rendered `docs.github.com` was blocked);
- JMH, JUnit and gradle-profiler were read from their source repositories on GitHub;
- Kalibera & Jones and the Quansight study were read in full from copies on GitHub;
- every other paper (`arxiv.org`, `dl.acm.org`, `link.springer.com` and the blogs were blocked) is cited from its **abstract or a search-engine excerpt**, marked *(abstract)*. Treat those numbers as less checked than the rest.

Labels: a **fact** has a source. **[opinion]** marks this note's reasoning. **[local only]** marks numbers measured in this session's sandbox, which is **not** a GitHub runner (4 vCPUs, other sessions' builds running at the same time, load average 2 to 6).

## Answer in short

1. **Absolute times are not comparable between jobs.** Each job gets a new VM; for public repositories `ubuntu-latest` is 4 vCPUs and 16 GB, and since multi-cloud compute it may run on **AWS or Azure** (G1, G3). Others found different CPU models under the same label (G6) and a regression caused by the runner environment, not by code (P7). The one study of identical code on GitHub Actions saw per-benchmark time ratios from **0.51 to 1.36** even when both sides ran in the same job (P1).
2. **Within one JVM in one job, a paired, interleaved ratio is stable.** In this sandbox [local only], per-JVM median times of the same Browserless test varied by **23–26 %** between 8 JVMs, but the median of the per-round Unified/Direct ratio varied only **1.21–1.27**, and its 95 % interval narrowed to **±5 % with 20 pairs** and **±3.5 % with 35 pairs** (L4, L5). Playwright tests of 1–2 s were relatively quieter: per-JVM medians within 4–12 %, paired ratio 0.96–1.00 per JVM, **±3 % from 12 pairs** (L6).
3. **Recommended protocol** [opinion, built on the facts cited in §3 and §4]:
   - **Same job, same JVM, interleaved.** Run the Direct Test and the Unified UI Test of a pair alternately in one JVM, in **ABBA order** (A B, then B A, …), never all of A then all of B (P1, P2, P3). `@RepeatedTest` and the default JUnit method order do not interleave (J1, J2).
   - **Warm-up: discard at least the first 5 rounds.** The first invocation in a JVM carries Spring context start or browser launch (3.8 s against 15 ms per test locally, L1); Browserless rounds 1–4 are still 1.5–2× slower (L2); Playwright rounds drift down about 10 % until round 8 (L6). Do not wait for a "steady state": the Browserless times were still falling at round 30 (L2), and Kalibera & Jones found many JVM benchmarks never reach one in reasonable time (K2). Interleaving makes both sides share the drift.
   - **Repetitions: 30 measured rounds per pair**, in **at least 2 JVM forks** if the budget allows. 30 rounds of a 1–2 s browser pair is about 1.5–2 minutes [opinion].
   - **Statistic: the median of the per-round paired ratios** (Unified ÷ Direct, same round), reported with its **interquartile range** and a **bootstrap 95 % confidence interval**. Kalibera & Jones ask for a ratio with a confidence interval, not a bare mean or a significance test (K3, K4).
   - **Verdict:** "not slower within budget b" when the upper bound of the 95 % interval is ≤ 1 + b. With about ±5 % of in-job noise, **b = 10 %** is the smallest budget this note would trust on shared runners (P2: slowdowns of 10 % or less detectable on the same instance; P5: 4.41 % detectable on GitHub Actions for sequential work).
4. **Across pull requests, compare only the ratio, only as a trend, and with a wide tolerance.** A ratio cancels a uniformly slower machine but not a CPU or image difference that hits the two tests differently (G6, P7). To decide whether a pull request changed the ratio, measure the base branch in the same job (as `asv continuous` does, P1), or rely on the round-trip count from the map, which does not depend on the machine [opinion].

Confidence: **high** that absolute times must not cross jobs and that same-job interleaving is the accepted practice (several independent sources). **Medium** on the numbers (5 warm-up rounds, 30 rounds, 10 % budget): they come from this sandbox and from studies of other workloads, and should be checked once on a real `ubuntu-latest` runner with the first benchmark job (see [What to check on a real runner](#what-to-check-on-a-real-runner)).

---

## 1. What GitHub says about its hosted runners

| # | Fact | Source |
|---|---|---|
| G1 | For **public** repositories, `ubuntu-latest` (= `ubuntu-24.04`) runs with **4 CPUs, 16 GB RAM, 14 GB SSD**, x64. For **private** repositories the same label gets **2 CPUs, 8 GB**. | `github/docs` `data/reusables/actions/supported-github-runners.md` (main, read 2026-11-18) |
| G2 | "With the exception of single-CPU runners, each GitHub-hosted runner is a **new virtual machine (VM)** hosted by GitHub. Single-CPU runners are hosted in a container on a shared VM." | `github/docs` `data/reusables/actions/single-cpu-runners.md` |
| G3 | "GitHub Actions uses a **multi-cloud compute pool** … These jobs can run on either **Amazon Web Services (AWS) or Microsoft Azure**." It applies to standard 2-core and 4-core Linux runners and is automatic on Free, Pro and Team plans; "Jobs are automatically scheduled across available cloud providers." | `github/docs` `content/actions/concepts/runners/github-hosted-runners.md`, "Cloud hosts used by GitHub-hosted runners" |
| G4 | Runner VM images are maintained in `actions/runner-images` and the docs list three Ubuntu images (22.04, 24.04, 26.04); `ubuntu-latest` points to one of them and moves when GitHub changes it. | `github/docs` `content/actions/concepts/runners/github-hosted-runners.md`; G1's table |
| G5 | The docs give a vCPU count, never a CPU model, clock or any performance guarantee. The only stability statement found is about nested virtualisation: "we offer no guarantees regarding stability, performance, or compatibility." | same two files (searched for "performance", "dedicated", "shared", "hardware") |
| G6 | Others report different CPUs under the same label: CodSpeed (a benchmarking vendor) wrote in March 2026 that "even if you use a pinned runner image on GitHub Actions, they assign you different CPUs", and traced a benchmark difference to glibc's `malloc` on two CPU types. RunsOn lists Intel Xeon Platinum 8370C, AMD EPYC 7763 and EPYC 9V74 in GitHub's standard fleet. | *(excerpt)* <https://codspeed.io/blog/unrelated-benchmark-regression>; *(excerpt)* <https://runs-on.com/benchmarks/github-actions-cpu-performance/>. Both are vendors. |

**Inference** [opinion]: the repo's CI jobs (`browserless`, `playwright`, … in `.github/workflows/tests.yml`) each start on a fresh VM whose cloud, CPU model and neighbours are unknown. Two jobs of the same workflow run, let alone of two pull requests, are two different machines. G2 says the VM is not shared with another *job of ours*; nothing in the docs says the physical host is not shared with other tenants.

## 2. How much timing varies on hosted CI, as measured by others

| # | Finding | Source |
|---|---|---|
| P1 | **Quansight, 2021, scikit-image on GitHub Actions.** Two *identical* commits benchmarked in the **same job** with `asv continuous` (75 benchmarks, every 6 h for 16 days). With asv's default — two **interleaved** passes, A→B→A→B — the ratio ranged **0.51 to 1.36**, mean 1.00, sd 0.05, and 3.7 % of comparisons were false positives at asv's 1.2 threshold. **Without interleaving** the false positives rose to 9.99 % (range 0.43–1.50); with a **single pass** to 12.5 % (range 0.51–2.76). Their conclusion: same-job relative benchmarking can detect "regressions of 50% or more". | `Quansight/Quansight-website` `apps/labs/posts/github-actions-benchmarks.md` (published at <https://labs.quansight.org/blog/github-actions-benchmarks>) |
| P2 | **Laaber, Scheuner, Leitner, EMSE 2019**, Java and Go microbenchmarks on AWS, GCE and Azure: coefficient of variation from **0.03 % to over 100 %** depending on benchmark and instance type; "executing test and control experiments on the **same instances (in randomized order)** allows us to detect slowdowns of **10% or less** with high confidence", using Wilcoxon rank-sum and overlapping bootstrapped confidence intervals. | *(abstract)* Empirical Software Engineering 24(4), doi:10.1007/s10664-019-09681-1 |
| P3 | **Abedi & Brecht, ICPE 2017**: proposes **randomized multiple interleaved trials** (RMIT) for repeatable experiments in highly variable clouds; later work calls it best practice. | *(abstract/citations)* doi:10.1145/3030207.3030229 |
| P4 | **Bulej et al., "Duet benchmarking", ICPE 2020**: running the two compared artifacts **in parallel** on the same cloud VM and comparing them pairwise improved accuracy by 2.3–12.5× (avg 5×) for DaCapo/ScalaBench and 24–82× for SPEC CPU 2017. | *(abstract)* doi:10.1145/3358960.3379132, arXiv:2001.05811 |
| P5 | **Reichelt, Jung, van Hoorn, SSP 2024**, MooBench on GitHub Actions vs bare metal: "performance changes up to **4.41 %** are detectable by GitHub actions, as long as only **sequential** workloads are examined"; bare metal had lower standard deviation for multi-threaded runs. | *(abstract)* arXiv:2411.05491 |
| P6 | **Leitner & Cito, ACM TOIT 2016**: in four public IaaS clouds, the effect of multi-tenancy on predictability differs by provider; hardware heterogeneity under one instance type was seen on Azure and some EC2 types; no clear time-of-day or day-of-week effect. | *(abstract)* doi:10.1145/2885497, arXiv:1411.2429 |
| P7 | **Yang, Reichelt, Ingo, Hasselbring, 2025**: MooBench has run on GitHub-hosted VMs since May 2024; they traced a major regression to the environment rather than the code under test (excerpts differ on the detail: a Linux kernel change, or the move from the Ubuntu 22.04 to the 24.04 image), and note that "the performance is not only impacted by software changes, but also by the VMs". | *(abstract/excerpt)* arXiv:2510.11310 |
| P8 | **CodSpeed** (vendor, undated excerpt): 100 runs per suite on GitHub-hosted runners gave a **2.66 %** coefficient of variation; they estimate a 2 % gate would give false positives about 45 % of the time. | *(excerpt)* <https://codspeed.io/blog/benchmarks-in-ci-without-noise> |
| P9 | **Akinshin**, "Performance stability of GitHub Actions" (c. 2023; data in `AndreyAkinshin/githubactionsperfstability`): CPU-bound benchmarks far more stable than memory- or disk-bound; mean performance differed up to **3×** between builds; advises against comparing default runners across builds. | *(excerpt)* <https://aakinshin.net/posts/github-actions-perf-stability/> (blocked; repo not attachable) |
| P10 | **Mytkowicz et al., ASPLOS 2009**: innocuous setup details (environment size, link order) shift measured performance, by ~33 % frequently and once ~300 %, and the effect differs between machines; they propose setup randomisation. | *(abstract)* doi:10.1145/1508244.1508275 |

**Inference** [opinion]: none of these measured a *whole UI test* of 0.1–2 s. They agree on direction, though: (a) cross-job and cross-day spread is large (P1, P8, P9); (b) the same machine, interleaved or randomised, is what brings the detectable change down to 5–10 % (P1, P2, P3, P5); (c) the environment itself shifts over time (P7). A UI test mixes CPU work (the Unified layer, Vaadin, JIT) with I/O and inter-process waits (browser, HTTP), which P9 and P5 put on the noisier side.

## 3. What published methodology recommends

| # | Recommendation | Source |
|---|---|---|
| K1 | Repeat at the level where the variance is: "experimenter time is much better spent repeating whole executions rather than iterations" when execution-to-execution variation is high. The confidence interval uses the variance at the highest level, so at least **5 executions** are needed to estimate it. | Kalibera & Jones, "Rigorous Benchmarking in Reasonable Time", ISMM 2013, §9 and §11 (Kent repository copy, read in full via `petertsehsun/petertsehsun.github.io`) |
| K2 | Many benchmarks **do not reach an independent (steady) state in reasonable time**; then take the same iteration index from each execution, after the "initialised" warm-up, rather than letting a heuristic pick a different cut per run. | same, §6 |
| K3 | "Report performance changes with **effect size confidence intervals**", e.g. "we are 95 % confident that system A is faster than system B by 5.5 % ± 2.5 %"; visual overlap of two intervals "lacks any rigorous semantics". | same, §2 (the RECOMMENDATION boxes) |
| K4 | For the **ratio** of two systems' times use a confidence interval for the ratio (Fieller's). If each system's interval is ±ε, the ratio's is about **±ε·√2**; and choosing repetitions well for a single system also does so for the ratio. | same, §10 |
| R1 | Georges, Buytaert & Eeckhout, OOPSLA 2007: JVM measurements need multiple VM invocations, a warm-up policy, and confidence intervals rather than best-of or a single run. | *(abstract)* doi:10.1145/1297027.1297033 |
| R2 | Interleave or randomise A and B on the same machine (P1, P2, P3), or run them in parallel (P4). | §2 above |

**Mapping to the ticket's terms** [opinion]:

- **Median and IQR** are what gradle-profiler reports (§4, B3) and are robust to the occasional 5–10× outlier seen locally (L3, max 156 ms against a 16 ms median). Kalibera & Jones's own method is ANOVA on means with a non-parametric alternative (K3); for a CI job with outliers the median of paired ratios with a bootstrap interval is the simpler non-parametric choice, and it is what P2 used.
- **Paired** ratios (the same round's B over A) beat the ratio of two separate medians because the round shares the machine state: locally the paired median spread across JVMs was 1.21–1.27, the unpaired 1.13–1.32 (L4).

## 4. What JMH, JUnit, Maven and Gradle say about timing whole tests

| # | Fact | Source |
|---|---|---|
| H1 | JMH is "for building, running, and analysing nano/micro/milli/**macro** benchmarks". | `openjdk/jmh` `README.md` |
| H2 | For operations with no steady state, "the only acceptable benchmark mode is a **single shot**"; `Mode.SingleShotTime` "is useful to estimate the 'cold' performance", and "more warmup/measurement iterations are generally required". | `openjdk/jmh` `JMHSample_26_BatchSize.java`; `jmh-core/.../annotations/Mode.java` |
| H3 | JMH **forks** each benchmark into a fresh JVM by default, because profiles of different tests mix ("uniformly bad" code), and "forking also allows to estimate **run-to-run variance**". Defaults: **5 forks**, 5 warm-up × 10 s and 5 measurement × 10 s iterations; for single-shot mode **0 warm-up and 1 measurement** iteration per fork. | `JMHSample_12_Forking.java`, `JMHSample_13_RunToRun.java`; `jmh-core/.../runner/Defaults.java` |
| H4 | `Level.Invocation` (per-call setup/teardown, which a whole UI test would need) is "only usable for benchmarks taking more than a millisecond" and is flagged "HERE BE DRAGONS". | `jmh-core/.../annotations/Level.java` |
| J1 | `@RepeatedTest` runs each repetition "like the execution of a regular `@Test` method with full support for the same lifecycle callbacks" — so repetitions of one method run back-to-back, not interleaved with another class. | `junit-team/junit-framework` `junit-jupiter-api/.../RepeatedTest.java` |
| J2 | JUnit's default method order is "deterministic but intentionally nonobvious", so within a class the order is fixed between runs. | same file |
| B1 | gradle-profiler (Gradle's tool for timing *whole builds*) defaults to **6 warm-up and 10 measured builds** in benchmark mode; `--single-shot` is "a single measured build with zero warm-ups" with a cold daemon. | `gradle/gradle-profiler` `README.md` |
| B2 | It compares scenarios against a baseline with a **Mann-Whitney U test** and keeps raw per-iteration values. | same |
| B3 | Its per-scenario statistics are "mean, min, P25, median, P75, max and standard deviation". | same |
| S1 | Surefire writes each test's time in `TEST-*.xml` as `time="0.024"` (seconds, millisecond resolution). In a plain `mvn test` each test runs **once**, so one CI build gives one sample per test. | `unified-test-demo/target/surefire-reports/TEST-demo.ProfileBrowserlessLeftoversMigratedTest.xml` (local build) |

**Inference** [opinion]: no source gives a recipe for timing whole UI tests. JMH's closest fit is single-shot mode with many forks, which pays JVM start-up per sample and is too slow for 1–2 s browser tests. gradle-profiler is the nearest analogue (whole, multi-second operations): a handful of warm-ups, about 10 measured runs, median and quartiles, a rank test against a baseline. The Surefire XML of the normal CI build is **not** a benchmark: one sample per test, a fixed order, and the first test of a JVM carries the Spring context (L1). A benchmark needs its own runner that repeats and interleaves, like the JUnit Platform launcher harness used for L1–L6.

## 5. Local measurements [local only]

Sandbox: Linux, 4 vCPUs, OpenJDK 21, other sessions building at the same time (load average 1.9 → 4.1 during the Browserless run, 2.7 → 5.8 during the Playwright run). **Not a GitHub runner**; use only for the shape of the variance, not its size.

Harness (L1–L5): a JUnit Platform `Launcher` in one JVM runs `ProfileBrowserlessTest` (Direct) and `ProfileBrowserlessMigratedTest` (Unified), each class once per round, alternating which goes first (ABBA…), and records each test method's wall time between `executionStarted` and `executionFinished`. 8 separate JVMs × 40 rounds × 2 classes × 2 methods = 1,280 samples. "Round time" = the two methods of one class added.

| # | Finding |
|---|---|
| L1 | **The first test in a JVM** took **3.7–4.1 s** (Direct's `opensTheEmailDialog`, which ran first: it carries the Spring context start); the Unified class's first test took 0.22–0.39 s more. Warm tests take 10–20 ms. |
| L2 | **No steady state within 40 rounds.** Median (over 8 JVMs) Direct / Unified round time: round 1: 48 / 63 ms; round 5: 36 / 48 ms; round 10: 31 / 41 ms; round 20: 28 / 34 ms; round 30: 21 / 26 ms. |
| L3 | **Within a JVM** (rounds 5–39), per-test IQR was 21–45 % of the median and the coefficient of variation 17–55 %, with single outliers up to ~10× the median (156 ms against 16 ms). |
| L4 | **Between JVMs**, each test's median varied by **23–26 %** (range ÷ median). The **paired** ratio (same round, Unified ÷ Direct) had a median of **1.21–1.27** in every JVM; the ratio of the two separate medians spread wider, **1.13–1.32**. Order did not matter once interleaved: median paired ratio 1.25 with Direct first, 1.23 with Unified first. |
| L5 | **How many pairs** (bootstrap 95 % CI half-width of the median paired ratio, averaged over 8 JVMs; spread of the per-JVM medians): 5 pairs ±24 % (1.11–1.35); 10 pairs ±10 % (1.18–1.31); 20 pairs ±4.9 % (1.20–1.29); 35 pairs ±3.5 % (1.21–1.27). |
| L6 | **Playwright, 1–2 s tests.** Same harness with `-Dvaadin.testing.tool=playwright` and the installed Chromium, `ProfileDramafinderTest` (Direct) against `ProfileDramafinderMigratedTest` (Unified), 4 JVMs × 15 rounds (load average 2.7 → 5.8). Warm tests take **1.0–1.7 s**. Round 0 of the class that ran first carried **+1.2–1.3 s** (median round time 4.1 s against 2.9 s); later rounds drifted down by about 10 % until round 8. Rounds 3–14: per-test IQR **4–32 %** of the median, coefficient of variation **3–20 %**, max 1.5× the median; per-JVM class medians varied **4–12 %** between JVMs. The median paired ratio per JVM was **0.96–1.00**, each with a 95 % interval of about **±3 % from 12 pairs**; pooled over the 48 pairs, 0.98 [0.976, 1.002], IQR 0.96–1.01. |

The ratios (1.2–1.3 for the Browserless pair, about 0.98 for the Playwright pair) are by-products, not a finding of this ticket: the Browserless pair is a 10–20 ms test where Unified-layer overhead (User Step recording, lookups) weighs relatively more, everything ran on a loaded machine, and the Dramafinder pair's two methods are not line-for-line the same scenario (the migrated one keeps an Escape Hatch). Measuring the ratios properly is the map's job.

## 6. Is a ratio comparable across pull requests?

- **Absolute times: no.** Different VM per job (G2), possibly a different cloud (G3) and CPU (G6), an image that moves (G4, P7), and cross-build spread up to 3× (P9).
- **The ratio: only as a trend, with a tolerance.** It cancels whatever slows both tests alike — locally the paired ratio stayed within ±3 % across JVMs whose absolute medians differed 23–26 % (L4). It does not cancel what hits them differently: a CPU with a different `malloc` cost (G6), a different browser build in a new image (G4), more or fewer cores changing GC and JIT threads (G1: 4 vCPUs public, 2 private). Even same-job ratios of *identical* code reached 0.51 and 1.36 for some benchmarks on GitHub Actions (P1). [opinion]
- **What would make a cross-PR claim sound** [opinion]:
  1. measure base and head **in the same job**, interleaved, like `asv continuous` (P1) — doubles the cost, and is only worth it on demand (a label) or nightly;
  2. or keep a history of the per-job ratio and flag only a **change-point** sustained over several runs, as MooBench does with Nyrkiö's E-Divisive means (P7), rather than comparing two single runs;
  3. and use the **round-trip count** per user action from the map as the cross-PR signal: it is a count, so it does not depend on the runner.

## What to check on a real runner

The local numbers stand in for a measurement that this session could not make. When the first benchmark job exists, run it 5–10 times on `ubuntu-latest` (e.g. via `workflow_dispatch`) and check [opinion]:

- that 5 discarded rounds are enough (plot round time against round index, as K2 suggests);
- the CI half-width of the median paired ratio at 30 rounds; if it is above ±5 %, add rounds or forks before setting the budget;
- how far the per-job ratio moves between those runs: that spread is the tolerance for any cross-PR trend.

## Sources not reachable from this session

`docs.github.com`, `arxiv.org`, `dl.acm.org`, `link.springer.com`, `aakinshin.net`, `codspeed.io`, `openjdk.org`, `docs.gradle.org`, `maven.apache.org` and `junit.org` were blocked by the session's network policy. GitHub's docs and the JMH, JUnit and gradle-profiler material were read from their GitHub sources instead; the papers marked *(abstract)* or *(excerpt)* were not read in full.

## Method

- GitHub docs: `raw.githubusercontent.com/github/docs/main/…` for the three files named in §1, plus the reusables they include.
- JMH: `openjdk/jmh` master, files named in §4. JUnit: `junit-team/junit-framework` main. gradle-profiler: master `README.md`.
- Kalibera & Jones: the Kent Academic Repository's updated version, from `petertsehsun/petertsehsun.github.io` (`soen691/current/papers/reasonable_benchmarking.pdf`).
- Quansight: `Quansight/Quansight-website` main, `apps/labs/posts/github-actions-benchmarks.md`.
- Local runs (Playwright: `-Dvaadin.testing.tool=playwright -Dvaadin.testing.playwright.executable=/opt/pw-browsers/chromium`, Chromium from `/opt/pw-browsers`): `mvn -B -o -q install -DskipTests` on `6c15e62`; test classpath from `mvn dependency:build-classpath -pl unified-test-demo`; a 40-line JUnit Platform `Launcher` harness (`Bench.java`, not committed) run from `unified-test-demo/` with the configuration parameters from its `pom.xml` (`vaadin.testing.browserless.route-errors-to-session=true`, parallel execution off). Analysis: medians, IQR, and a 2,000-resample bootstrap of the median paired ratio, in Python's `statistics` module.
