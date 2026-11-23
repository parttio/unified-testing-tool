#!/usr/bin/env bash
# Runs the Performance Pairs (unified-test-demo, package demo.perf) on one Testing Tool: checks their round trips
# against the committed round-trip counts (#341), or times them against the budget (#342). Part of the
# performance plan #294.
#
#   scripts/bench-unified.sh --tool browserless|playwright|selenium [--scenario NAME]
#                            [--counts|--update-counts|--time] [--runs N] [--warmup N] [--rounds N]
#                            [--skip-build] [-- extra Maven arguments]
#   scripts/bench-unified.sh --compare FIRST,SECOND [--browser PATH] [--scenario NAME] [--warmup N] [--rounds N]
#                            [--skip-build] [-- extra Maven arguments]
#
#   --counts         count the round trips per user action and compare them with the committed counts (default)
#   --update-counts  count them --runs times (default 3) and write them as the committed counts; a user action
#                    whose count differs between runs is written as a range, min-max
#   --time           time each pair in ABBA rounds (--warmup, default 5, then --rounds, default 30) and judge the
#                    median ratio against the budget: 10 % or 2 ms per user action, whichever is larger. Writes
#                    unified-test-demo/target/benchmark/<tool>.csv and .md; exit code 1 when a scenario fails
#   --compare        time the Performance Pairs on two Testing Tools in one JVM, for example playwright,selenium
#                    (#497): ABBA rounds, as --time; per scenario, the median ratio SECOND ÷ FIRST for the Unified UI
#                    Test and for the Direct Tests, with its 95 % interval. Informational: no budget. Writes
#                    unified-test-demo/target/benchmark/compare-FIRST-SECOND.csv and .md
#   --browser        with --compare: the browser binary every side runs (a Chrome or Chromium), so that the tools
#                    differ only in how they drive it
#   --scenario       only this scenario: smallForm, longForm, largeGrid or comboBox
#   --skip-build     don't install the modules first
#
# Committed counts: unified-test-demo/perf/round-trips/<tool>.txt, one line per user action (test source line) and
# counter. The check fails when a count is above the committed one (a change added round trips), and when it is
# below (lower the committed count in the same PR with --update-counts). A count goes up only by an explicit edit of
# the file, with the reason in the PR. Selenium needs the TestBench licence (VAADIN_OFFLINE_KEY or a local key).
# Playwright downloads its browser, or uses -- -Dvaadin.testing.playwright.executable=PATH.
#
# Exit code 0 when the counts match (or no scenario fails the time budget), 1 when they don't, 2 when the tests
# failed.
set -euo pipefail
cd "$(dirname "$0")/.."
root=$(pwd)

tool=""
compare=""
browser=""
scenario=""
mode=counts
runs=3
warmup=5
rounds=30
build=true
maven_args=()
while [ $# -gt 0 ]; do
    case "$1" in
        --tool) tool="$2"; shift 2 ;;
        --scenario) scenario="$2"; shift 2 ;;
        --compare) compare="$2"; mode=compare; shift 2 ;;
        --browser) browser="$2"; shift 2 ;;
        --counts) mode=counts; shift ;;
        --update-counts) mode=update; shift ;;
        --time) mode=time; shift ;;
        --warmup) warmup="$2"; shift 2 ;;
        --rounds) rounds="$2"; shift 2 ;;
        --runs) runs="$2"; shift 2 ;;
        --skip-build) build=false; shift ;;
        --) shift; maven_args=("$@"); break ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done
if [ "$mode" = compare ]; then
    case "$compare" in
        *,*) ;;
        *) echo "--compare takes two Testing Tools, for example playwright,selenium" >&2; exit 2 ;;
    esac
else
    case "$tool" in
        browserless|playwright|selenium) ;;
        *) echo "--tool browserless|playwright|selenium is required" >&2; exit 2 ;;
    esac
    [ -z "$browser" ] || { echo "--browser goes with --compare" >&2; exit 2; }
fi
[ "$mode" = update ] || runs=1

if $build; then
    mvn -B -q -ntp install -DskipTests ${maven_args[@]+"${maven_args[@]}"}
fi

if [ "$mode" = compare ]; then
    bench_args="--tools $compare --warmup $warmup --rounds $rounds --out $root/unified-test-demo/target/benchmark"
    [ -n "$scenario" ] && bench_args="$bench_args --scenario $scenario"
    # TestBench's own setting, as the demo's Surefire configuration passes it
    extra=(-Dcom.vaadin.testbench.Parameters.headless=true)
    if [ -n "$browser" ]; then
        # the Unified adapters and the Direct Tests (DramafinderPerfTest, TestBenchPerfTest) all read these
        extra+=(-Dvaadin.testing.perf.browser="$browser" -Dvaadin.testing.playwright.executable="$browser"
                -Dvaadin.testing.selenium.executable="$browser")
    fi
    echo "Timing the Performance Pairs on $compare in one JVM: $warmup warm-up and $rounds measured rounds"
    status=0
    (cd unified-test-demo && mvn -B -ntp -q test-compile exec:java -Dexec.mainClass=demo.perf.bench.ToolComparison \
        -Dexec.classpathScope=test -Dexec.args="$bench_args" \
        ${extra[@]+"${extra[@]}"} ${maven_args[@]+"${maven_args[@]}"}) || status=$?
    exit $status
fi

if [ "$mode" = time ]; then
    bench_args="--warmup $warmup --rounds $rounds --out $root/unified-test-demo/target/benchmark"
    [ -n "$scenario" ] && bench_args="$bench_args --scenario $scenario"
    extra=()
    # TestBench's own setting, as the demo's Surefire configuration passes it
    [ "$tool" = selenium ] && extra=(-Dcom.vaadin.testbench.Parameters.headless=true)
    echo "Timing the Performance Pairs on $tool: $warmup warm-up and $rounds measured rounds"
    status=0
    # from the demo module, as Surefire runs its tests: the application's dev mode writes its front-end files there
    (cd unified-test-demo && mvn -B -ntp -q test-compile exec:java -Dexec.mainClass=demo.perf.bench.TimeBenchmark \
        -Dexec.classpathScope=test -Dexec.args="$bench_args" -Dvaadin.testing.tool="$tool" \
        ${extra[@]+"${extra[@]}"} ${maven_args[@]+"${maven_args[@]}"}) || status=$?
    exit $status
fi

tests=()
if [ -n "$scenario" ]; then
    tests=("-Dtest=*PerfTest#$scenario" -Dsurefire.failIfNoSpecifiedTests=false)
fi
if [ "$tool" = playwright ]; then
    # Playwright's channel log, which the counter reads; it must be set before Playwright loads
    export DEBUG=pw:channel
fi

out="$root/unified-test-demo/target/round-trip-counts"
rm -rf "$out"
measured=()
for i in $(seq 1 "$runs"); do
    echo "Counting round trips on $tool, run $i of $runs"
    if ! mvn -B -ntp -pl unified-test-demo verify -Pperformance -Dvaadin.testing.tool="$tool" \
            -Dvaadin.testing.perf.round-trips="$out/run-$i" ${tests[@]+"${tests[@]}"} ${maven_args[@]+"${maven_args[@]}"} > "$out.log" 2>&1; then
        grep -E "Tests run:|FAIL|ERROR" "$out.log" | tail -30
        echo "The Performance Pairs failed on $tool: see $out.log" >&2
        exit 2
    fi
    measured+=("$out/run-$i/$tool.txt")
done

args=(--tool "$tool" --committed "unified-test-demo/perf/round-trips/$tool.txt"
      --sources unified-test-demo/src/test/java/demo/perf)
[ -n "$scenario" ] && args+=(--scenario "$scenario")
if [ "$mode" = update ]; then
    python3 scripts/round_trip_counts.py update "${args[@]}" "${measured[@]}"
else
    python3 scripts/round_trip_counts.py compare "${args[@]}" ${GITHUB_STEP_SUMMARY:+--summary "$GITHUB_STEP_SUMMARY"} \
        "${measured[@]}"
fi
