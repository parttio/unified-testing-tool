#!/usr/bin/env bash
# Prints `code=true` when a change touches more than documentation, else `code=false`, for tests.yml's `changes` job.
# Documentation: docs/**, .out-of-scope/**, mkdocs.yml, and Markdown files outside any src/ directory (a skill's
# SKILL.md under src/ ships in a jar, so it counts as code). Unsure (a manual run, a new branch, an unknown base):
# code=true.
#
#   scripts/changes-beyond-docs.sh <event name> <base commit>
set -uo pipefail

event="$1"
base="${2:-}"

if [[ "$event" != "pull_request" && "$event" != "push" ]] || [[ -z "$base" || "$base" =~ ^0+$ ]] \
    || ! files=$(git diff --name-only "$base" HEAD 2>/dev/null); then
    echo "code=true"
    exit 0
fi

beyond=$(printf '%s\n' "$files" | grep -v '^$' \
    | grep -vE '^docs/|^\.out-of-scope/|^mkdocs\.yml$' \
    | grep -vE '(^|/)[^/]+\.md$' ; \
    printf '%s\n' "$files" | grep -E '(^|/)src/.*\.md$')

if [[ -n "$beyond" ]]; then
    echo "code=true"
else
    echo "code=false"
fi
