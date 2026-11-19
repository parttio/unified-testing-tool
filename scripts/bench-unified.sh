#!/usr/bin/env bash
# Runs the Performance Pairs (unified-test-demo, package demo.perf) on one Testing Tool and checks their round trips
# against the committed round-trip counts (#341, part of the performance plan #294).
#
#   scripts/bench-unified.sh --tool browserless|playwright|selenium [--scenario NAME] [--counts|--update-counts]
#                            [--runs N] [--skip-build] [-- extra Maven arguments]
#
#   --counts         count the round trips per user action and compare them with the committed counts (default)
#   --update-counts  count them --runs times (default 3) and write them as the committed counts; a user action
#                    whose count differs between runs is written as a range, min-max
#   --scenario       only this scenario: smallForm, longForm, largeGrid or comboBox
#   --skip-build     don't install the modules first
#
# Committed counts: unified-test-demo/perf/round-trips/<tool>.txt, one line per user action (test source line) and
# counter. The check fails when a count is above the committed one (a change added round trips), and when it is
# below (lower the committed count in the same PR with --update-counts). A count goes up only by an explicit edit of
# the file, with the reason in the PR. Selenium needs the TestBench licence (VAADIN_OFFLINE_KEY or a local key).
# Playwright downloads its browser, or uses -- -Dvaadin.testing.playwright.executable=PATH.
#
# Exit code 0 when the counts match, 1 when they don't, 2 when the tests failed.
set -euo pipefail
cd "$(dirname "$0")/.."
root=$(pwd)

tool=""
scenario=""
mode=counts
runs=3
build=true
maven_args=()
while [ $# -gt 0 ]; do
    case "$1" in
        --tool) tool="$2"; shift 2 ;;
        --scenario) scenario="$2"; shift 2 ;;
        --counts) mode=counts; shift ;;
        --update-counts) mode=update; shift ;;
        --runs) runs="$2"; shift 2 ;;
        --skip-build) build=false; shift ;;
        --) shift; maven_args=("$@"); break ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done
case "$tool" in
    browserless|playwright|selenium) ;;
    *) echo "--tool browserless|playwright|selenium is required" >&2; exit 2 ;;
esac
[ "$mode" = update ] || runs=1

if $build; then
    mvn -B -q -ntp install -DskipTests "${maven_args[@]}"
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
            -Dvaadin.testing.perf.round-trips="$out/run-$i" "${tests[@]}" "${maven_args[@]}" > "$out.log" 2>&1; then
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
