#!/usr/bin/env bash
# Prepares everything the live demo of docs/talks/live-demo-runbook.md shows from a file rather than live, and warms
# the build so that nothing downloads on stage. Run it the day before, and again an hour before the talk.
#
#   scripts/prepare-talk.sh [--quick] [--selenium] [--skip-bench] [--skip-viewports] [--skip-migration]
#
#   --quick           run only the demo's talk subset (TALK_TESTS) instead of all of unified-test-demo's tests
#   --selenium        also run on Selenium (needs the TestBench licence: VAADIN_OFFLINE_KEY or a local key)
#   --skip-bench      don't time Browserless against Playwright (the slowest step)
#   --skip-viewports  don't run the Viewport Comparison
#   --skip-migration  don't set up the one-class migration worktree
#
# Environment:
#   PLAYWRIGHT_EXECUTABLE   a Chrome or Chromium binary for Playwright, instead of Playwright's own download
#   TALK_TESTS              Surefire -Dtest filter for --quick (default below)
#   VIEWPORT_TESTS          Surefire -Dtest filter for the Viewport runs (default below)
#   TALK_MIGRATION_DIR      where the migration worktree goes (default: ../unified-testing-tool-talk-migration)
#
# Everything lands in target/talk/. The script prints what to open at the end. A step that fails is reported and the
# script goes on: a missing report is better found the day before than on stage. Exit code 1 when a step failed.
set -uo pipefail
cd "$(dirname "$0")/.."
root=$(pwd)
out="$root/target/talk"
logs="$out/logs"

quick=false selenium=false bench=true viewports=true migration=true
for arg in "$@"; do
    case "$arg" in
        --quick) quick=true ;;
        --selenium) selenium=true ;;
        --skip-bench) bench=false ;;
        --skip-viewports) viewports=false ;;
        --skip-migration) migration=false ;;
        -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

TALK_TESTS=${TALK_TESTS:-ProfileViewTest,EditPersonUnifiedTest,GridTest,DialogTest,MenuTest,LoginTest}
VIEWPORT_TESTS=${VIEWPORT_TESTS:-ProfileViewTest,EditPersonUnifiedTest,MenuBarOverflowTest,MenuTest,DashboardTest}
migration_dir=${TALK_MIGRATION_DIR:-"$root/../unified-testing-tool-talk-migration"}

extra=()
if [ -n "${PLAYWRIGHT_EXECUTABLE:-}" ]; then
    extra+=("-Dvaadin.testing.playwright.executable=$PLAYWRIGHT_EXECUTABLE")
fi

rm -rf "$out"
mkdir -p "$logs"
failed=()

# step NAME COMMAND...: runs the command with its output in target/talk/logs/NAME.log
step() {
    local name=$1; shift
    printf '%-34s' "$name"
    local start=$SECONDS
    if "$@" >"$logs/$name.log" 2>&1; then
        echo "ok ($((SECONDS - start)) s)"
    else
        echo "FAILED ($((SECONDS - start)) s), see target/talk/logs/$name.log"
        failed+=("$name")
    fi
}

# demo_test ARGS...: runs unified-test-demo's tests; --quick narrows them to TALK_TESTS
demo_test() {
    local filter=()
    if $quick; then filter=("-Dtest=$TALK_TESTS" -Dsurefire.failIfNoSpecifiedTests=false); fi
    mvn -B -ntp -pl unified-test-demo test "${filter[@]}" "${extra[@]}" "$@"
}

version=$(mvn -B -q -ntp help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null)
echo "Preparing the talk on unified-testing-tool $version, into target/talk/"
echo

# 1. Every module installed, so the demo, CompareRuns and the migration worktree find this version
step build mvn -B -ntp install -DskipTests

# 2. The same tests on each Testing Tool; each later run is compared with Browserless
step run-browserless demo_test -Dvaadin.testing.evidence-dir="$out/evidence-browserless"
step run-playwright demo_test -Dvaadin.testing.tool=playwright \
    -Dvaadin.testing.evidence-dir="$out/evidence-playwright" \
    -Dvaadin.testing.user-steps.compare-dir="$out/evidence-browserless"
if $selenium; then
    step run-selenium demo_test -Dvaadin.testing.tool=selenium \
        -Dvaadin.testing.evidence-dir="$out/evidence-selenium" \
        -Dvaadin.testing.user-steps.compare-dir="$out/evidence-browserless"
fi

# CompareRuns needs unified-test-api and its dependencies
api_cp() {
    mvn -B -q -ntp -pl unified-test-api dependency:build-classpath -Dmdep.outputFile="$out/api.cp" &&
        echo "$root/unified-test-api/target/classes:$(cat "$out/api.cp")"
}
compare_tools() {
    local runs=("$out/evidence-browserless" "$out/evidence-playwright")
    if $selenium; then runs+=("$out/evidence-selenium"); fi
    java -cp "$(api_cp)" com.vaadin.testing.unified.report.CompareRuns --out "$out/tool-comparison" "${runs[@]}" \
        | tee "$out/tool-comparison.md"
}
step compare-tools compare_tools

# 3. The failure of the getting-started guide ("Break it"): the Email label mistyped as E-mail
profile_test=unified-test-demo/src/test/java/demo/ProfileViewTest.java
break_it() {
    cp "$profile_test" "$out/ProfileViewTest.java.orig"
    sed -i 's/withLabel("Email").setValue("alice@example.com")/withLabel("E-mail").setValue("alice@example.com")/' \
        "$profile_test"
    if grep -q 'withLabel("E-mail")' "$profile_test"; then
        # Both runs are meant to fail; what counts is that they left their evidence
        mvn -B -ntp -pl unified-test-demo test -Dtest=ProfileViewTest "${extra[@]}" \
            -Dvaadin.testing.evidence-dir="$out/evidence-failure-browserless" >"$out/failure-browserless.txt" 2>&1
        mvn -B -ntp -pl unified-test-demo test -Dtest=ProfileViewTest "${extra[@]}" -Dvaadin.testing.tool=playwright \
            -Dvaadin.testing.evidence-dir="$out/evidence-failure-playwright" >"$out/failure-playwright.txt" 2>&1
    else
        echo "The Email label wasn't found in $profile_test"
    fi
    cp "$out/ProfileViewTest.java.orig" "$profile_test"
    grep -q "Did you mean" "$out/failure-browserless.txt" && test -f "$out/evidence-failure-playwright/report.html"
}
step failure break_it
git diff --quiet -- "$profile_test" || { echo "ProfileViewTest.java is still changed: git checkout -- $profile_test"; failed+=(restore); }

# 4. Viewport Comparison: the same tests at three Viewports, with the layout checks on
viewport_runs() {
    local vp name
    for vp in phone:375x667 tablet:768x1024 desktop:1280x720; do
        name=${vp%%:*}
        mvn -B -ntp -pl unified-test-demo test -Dtest="$VIEWPORT_TESTS" -Dsurefire.failIfNoSpecifiedTests=false \
            "${extra[@]}" -Dvaadin.testing.tool=playwright -Dvaadin.testing.viewport="$vp" \
            -Dvaadin.testing.layout-checks=on -Dvaadin.testing.layout-checks.screenshots=on \
            -Dvaadin.testing.evidence-dir="$out/viewports/$name" || true
    done
    java -cp "$(api_cp)" com.vaadin.testing.unified.report.CompareRuns --out "$out/viewport-comparison" \
        "$out"/viewports/* || [ $? -eq 2 ]
    test -f "$out/viewport-comparison/viewport-comparison.html"
}
if $viewports; then step viewports viewport_runs; fi

# 5. The headline number: the same test on Browserless and on Playwright, in one JVM
benchmark() {
    local browser=()
    if [ -n "${PLAYWRIGHT_EXECUTABLE:-}" ]; then browser=(--browser "$PLAYWRIGHT_EXECUTABLE"); fi
    scripts/bench-unified.sh --compare browserless,playwright --scenario longForm --warmup 3 --rounds 10 \
        --skip-build "${browser[@]}" &&
        mkdir -p "$out/benchmark" &&
        cp unified-test-demo/target/benchmark/compare-browserless-playwright.* "$out/benchmark/"
}
if $bench; then step benchmark benchmark; fi

# 6. Migration: a worktree whose demo has one TestBench test class, committed as "before"
recipe() {
    mvn -B -ntp -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run -pl unified-test-demo \
        -Drewrite.recipeArtifactCoordinates="org.vaadin.addons:unified-test-rewrite:$version" \
        -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.MigrateTestBenchToUnified
}
migration_worktree() {
    git worktree remove --force "$migration_dir" 2>/dev/null || rm -rf "$migration_dir"
    git worktree prune
    git branch -D talk/migration-before 2>/dev/null || true
    git worktree add -b talk/migration-before "$migration_dir" HEAD || return 1
    (
        cd "$migration_dir" || exit 1
        find unified-test-demo/src/test/java -type f ! -path '*/demo/ProfileTestBenchTest.java' -delete
        find unified-test-demo/src/test/java -type d -empty -delete
        # The Performance Pairs' listener and extension are registered as services; their classes are gone now
        rm -rf unified-test-demo/src/test/resources/META-INF/services
        git add -A && git commit -q -m "Talk: the demo with one TestBench test class, before the migration" || exit 1
        # A rehearsal: downloads the rewrite plugin now, and keeps the diff as the fallback
        recipe || exit 1
        mkdir -p "$out/migration"
        git diff >"$out/migration/ProfileTestBenchTest.diff"
        git status --short >"$out/migration/changed-files.txt"
        # The migrated test runs on Browserless, with no TestBench licence and no browser
        rm -rf unified-test-demo/target
        mvn -B -ntp -pl unified-test-demo test >"$out/migration/migrated-test-browserless.txt" 2>&1 || exit 1
        git reset -q --hard && git clean -qfd
    )
}
find_classes() {
    rm -rf target/rewrite/datatables
    mvn -B -ntp -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun -pl unified-test-demo \
        -Drewrite.recipeArtifactCoordinates="org.vaadin.addons:unified-test-rewrite:$version" \
        -Drewrite.activeRecipes=org.vaadin.addons.unified.rewrite.FindTestClassesToMigrate \
        -Drewrite.exportDatatables=true || return 1
    mkdir -p "$out/migration"
    find target/rewrite/datatables -name '*FindTestClassesToMigrate*.csv' -exec cp {} "$out/migration/" \;
    ls "$out"/migration/*FindTestClassesToMigrate*.csv >/dev/null
}
if $migration; then
    step migration-worktree migration_worktree
    step find-classes-to-migrate find_classes
fi

echo
echo "To open during the talk:"
for f in \
    evidence-playwright/report.html evidence-playwright/summary.md tool-comparison.md \
    failure-browserless.txt evidence-failure-playwright/report.html \
    viewport-comparison/viewport-comparison.html benchmark/compare-browserless-playwright.md \
    migration/ProfileTestBenchTest.diff migration/*FindTestClassesToMigrate*.csv; do
    for path in "$out"/$f; do
        if [ -e "$path" ]; then echo "  ${path#"$root"/}"; else echo "  (missing) target/talk/$f"; fi
    done
done
if $migration && [ -d "$migration_dir" ]; then echo "  migration worktree: $(cd "$migration_dir" && pwd)"; fi

if [ ${#failed[@]} -gt 0 ]; then
    echo
    echo "Failed: ${failed[*]}"
    exit 1
fi
