#!/usr/bin/env bash
# Prints the test classes that failed in a Surefire run, comma-separated for -Dtest, or nothing when none did.
# A nested class counts as its top-level class, which runs it. tests.yml re-runs them with Playwright's trace (#601).
#
#   scripts/failed-test-classes.sh <surefire reports dir>
set -euo pipefail

dir="${1:?usage: $0 <surefire reports dir>}"
[ -d "$dir" ] || exit 0

shopt -s nullglob
reports=("$dir"/TEST-*.xml)
[ ${#reports[@]} -gt 0 ] || exit 0

# the <testsuite> element of each report, then its name when it counted a failure or an error
for report in "${reports[@]}"; do
    suite=$(grep -m 1 -o '<testsuite [^>]*>' "$report" || true)
    if grep -qE ' (failures|errors)="[1-9][0-9]*"' <<<"$suite"; then
        sed -E 's/.* name="([^"]*)".*/\1/; s/\$.*//' <<<"$suite"
    fi
done | sort -u | paste -sd, -
