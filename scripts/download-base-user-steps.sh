#!/usr/bin/env bash
# Downloads the evidence a Testing Tool's job uploaded in the base branch's last finished run of this workflow,
# so the run summary can compare a changed scenario's User Steps with it (#264). For pull requests in GitHub
# Actions; needs `actions: read` and GH_TOKEN. Never fails the job: without a base run the summary says so.
#
#   scripts/download-base-user-steps.sh <artifact name> <directory>
#
# Tries the base branch's last 20 finished push runs, newest first: a cancelled run, one whose job didn't get as far
# as uploading, and one that changed only documentation (Playwright and Selenium skipped) have no artifact.
set -uo pipefail

artifact="$1"
dir="$2"
workflow="${GITHUB_WORKFLOW_REF:?not in GitHub Actions}"
workflow="${workflow%@*}"   # owner/repo/.github/workflows/tests.yml
workflow="${workflow##*/}"  # tests.yml

runs=$(gh run list --repo "$GITHUB_REPOSITORY" --workflow "$workflow" --branch "${GITHUB_BASE_REF:?not a pull request}" \
    --event push --status completed --limit 20 --json databaseId --jq '.[].databaseId') || {
    echo "No base run: the base branch's runs could not be listed"
    exit 0
}
for run in $runs; do
    if gh run download "$run" --repo "$GITHUB_REPOSITORY" --name "$artifact" --dir "$dir" 2>/dev/null; then
        echo "Base run: $artifact from run $run of $GITHUB_BASE_REF, in $dir"
        exit 0
    fi
    rm -rf "$dir"
done
echo "No base run: no $artifact in the last runs of $GITHUB_BASE_REF"
