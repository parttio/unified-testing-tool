#!/usr/bin/env bash
# Evaluates the write-unified-ui-tests agent skill (#241): for each prose scenario in
# unified-test-rewrite/skill-eval/writing/fixtures/, asks Claude Code, with the skill installed, to write the test
# class the fixture is named after in the demo, in a scratch worktree; then scores it: compiles, passes on Browserless
# and on Playwright, UnifiedStyleTest passes, intent share (scripts/score_intent.py), no Escape Hatch, nothing changed
# outside the demo's test sources. Compares the score with the committed baseline. Needs Java 21, Maven, Python 3
# and, without --reference, Claude Code (`claude`) logged in.
#
#   scripts/eval-writing-skill.sh [--reference] [--only A,B] [--accept] [--keep] [--playwright-executable PATH]
#
#   --reference  score the hand-written reference tests (skill-eval/writing/reference/) instead of running an agent:
#                checks the fixtures and the scoring, and is what the agent is measured against
#   --only       only these fixtures (test class names, comma-separated)
#   --accept     when the run is acceptable, write it as the new baseline
#   --keep       keep the scratch worktree
#
# Each fixture runs on its own: the worktree's changes are scored, kept in the run directory and then reverted, so
# one fixture's test can't break another's. A run is acceptable when no compared metric is worse than the baseline,
# and, when SKILL.md changed since the baseline, at least one is better, unless the baseline has nothing left to
# improve. Exit code 0 when acceptable, 1 when not.
set -euo pipefail
cd "$(dirname "$0")/.."
root=$(pwd)

reference=""
only=""
accept=""
keep=""
playwright_executable=""
while [ $# -gt 0 ]; do
  case "$1" in
    --reference) reference=1; shift ;;
    --only) only="$2"; shift 2 ;;
    --accept) accept="--accept"; shift ;;
    --keep) keep=1; shift ;;
    --playwright-executable) playwright_executable="$2"; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

suite="$root/unified-test-rewrite/skill-eval/writing"
skill="$root/unified-test-rewrite/src/main/resources/META-INF/unified/skills/write-unified-ui-tests/SKILL.md"
tests_dir=unified-test-demo/src/test/java/demo
out="$root/target/skill-eval-writing/$(date +%Y%m%d-%H%M%S)"
work="$out/worktree"
mkdir -p "$out/fixtures"

git worktree add --detach "$work" HEAD >/dev/null
cleanup() { [ -n "$keep" ] || git worktree remove --force "$work" >/dev/null 2>&1 || true; }
trap cleanup EXIT

# the skill, where Claude Code finds it, as AddUnifiedTestingSkill installs it
mkdir -p "$work/.claude/skills/write-unified-ui-tests"
cp "$skill" "$work/.claude/skills/write-unified-ui-tests/SKILL.md"

echo "Building the worktree..."
(cd "$work" && mvn -B -q -ntp install -DskipTests) >"$out/build.log" 2>&1

playwright_args=()
if [ -n "$playwright_executable" ]; then
  playwright_args=("-Dvaadin.testing.playwright.executable=$playwright_executable")
fi

for fixture in "$suite"/fixtures/*.md; do
  name=$(basename "$fixture" .md)
  if [ -n "$only" ] && ! [[ ",$only," == *",$name,"* ]]; then
    continue
  fi
  dir="$out/fixtures/$name"
  mkdir -p "$dir/files" "$dir/browserless" "$dir/playwright"
  echo "== $name"

  if [ -n "$reference" ]; then
    cp "$suite/reference/$name.java" "$work/$tests_dir/$name.java"
  else
    prompt="Write a Unified UI Test for the scenario below, as the test class demo.$name in $tests_dir/$name.java.
The application under test is the demo in unified-test-demo (its views are in unified-test-demo/src/main/java/demo);
run Maven for it with -pl unified-test-demo. Follow the write-unified-ui-tests skill. Change only test sources.

Scenario: $(cat "$fixture")"
    echo "  asking Claude Code to write it..."
    (cd "$work" && claude -p "$prompt" \
        --permission-mode acceptEdits \
        --allowedTools "Read" "Edit" "Write" "Glob" "Grep" "Skill" "Bash(mvn:*)" "Bash(ls:*)" "Bash(cat:*)") \
        >"$dir/agent.log" 2>&1 || echo "  Claude Code exited with an error, see $dir/agent.log"
  fi

  # what the run wrote: test sources kept for scoring, anything else counted
  git -C "$work" status --porcelain --untracked-files=all -- "$tests_dir" | awk '{print $2}' | while read -r f; do
    [ -f "$work/$f" ] && cp "$work/$f" "$dir/files/"
  done
  git -C "$work" status --porcelain --untracked-files=all -- . ":!$tests_dir" ":!.claude" \
    | awk '{print $2}' >"$dir/other-changes.txt"

  if (cd "$work" && mvn -B -q -ntp test-compile -pl unified-test-demo) >"$dir/compile.log" 2>&1; then
    touch "$dir/compiled"
    echo "  running on browserless, with the style rules..."
    (cd "$work" && mvn -B -q -ntp test -pl unified-test-demo -Dtest="$name,UnifiedStyleTest" \
        -Dsurefire.failIfNoSpecifiedTests=false -Dvaadin.testing.tool=browserless) >"$dir/browserless.log" 2>&1 || true
    cp "$work"/unified-test-demo/target/surefire-reports/TEST-*.xml "$dir/browserless/" 2>/dev/null || true
    cp -r "$work/unified-test-demo/target/surefire-reports/unified-evidence/$name" "$dir/browserless/evidence" 2>/dev/null || true
    echo "  running on playwright..."
    rm -rf "$work/unified-test-demo/target/surefire-reports"
    (cd "$work" && mvn -B -q -ntp test -pl unified-test-demo -Dtest="$name" \
        -Dsurefire.failIfNoSpecifiedTests=false -Dvaadin.testing.tool=playwright "${playwright_args[@]}") \
        >"$dir/playwright.log" 2>&1 || true
    cp "$work"/unified-test-demo/target/surefire-reports/TEST-*.xml "$dir/playwright/" 2>/dev/null || true
    cp -r "$work/unified-test-demo/target/surefire-reports/unified-evidence/$name" "$dir/playwright/evidence" 2>/dev/null || true
  fi
  if [ -f "$dir/files/$name.java" ]; then
    python3 -I "$root/scripts/score_intent.py" "$dir/files/$name.java" >"$dir/intent.json"
  fi

  # back to HEAD for the next fixture (the installed skill and the build output stay)
  git -C "$work" checkout -q -- .
  git -C "$work" clean -fdq -e .claude -- .
  rm -rf "$work/unified-test-demo/target/surefire-reports"
done

source=agent
[ -n "$reference" ] && source=reference
java "$root/scripts/EvalWritingSkill.java" --runs "$out/fixtures" --skill "$skill" \
  --baseline "$suite/baseline.json" --source "$source" --out "$out/report.md" $accept
